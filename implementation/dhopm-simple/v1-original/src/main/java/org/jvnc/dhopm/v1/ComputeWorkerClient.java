package org.jvnc.dhopm.v1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ComputeWorkerClient implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(30);
    private final Process process;
    private final BufferedWriter input;
    private final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
    private final List<String> pendingTransactions = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private WindowsRssSampler rssSampler;
    private volatile String stderrTail = "";
    private boolean healthy = true;
    private long requestSequence;
    private final long startupNanos;

    ComputeWorkerClient() throws IOException {
        long startupStarted = System.nanoTime();
        ProcessBuilder builder = new ProcessBuilder(javaCommand(),
                "--enable-native-access=ALL-UNNAMED", "-cp", workerClassPath(),
                ComputeWorkerMain.class.getName());
        process = builder.start();
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread stdoutReader = new Thread(() -> readLines(process), "compute-worker-stdout");
        stdoutReader.setDaemon(true);
        stdoutReader.start();
        Thread stderrReader = new Thread(() -> readStderr(process), "compute-worker-stderr");
        stderrReader.setDaemon(true);
        stderrReader.start();
        JsonObject ready = readResponse(Duration.ofSeconds(30));
        if (!"READY".equals(string(ready, "type"))) {
            close();
            throw new IOException("Compute worker did not report READY.");
        }
        long pid = number(ready, "pid");
        if (pid != process.pid()) {
            close();
            throw new IOException("Compute worker PID mismatch.");
        }
        rssSampler = new WindowsRssSampler(pid);
        startupNanos = System.nanoTime() - startupStarted;
    }

    synchronized void addPendingTransaction(String transaction) {
        ensureHealthy();
        String trimmed = transaction == null ? "" : transaction.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Transaction cannot be empty.");
        }
        List<String> items = List.of(trimmed.split("\\s+"));
        if (items.stream().distinct().count() != items.size()) {
            throw new IllegalArgumentException("A transaction cannot contain duplicate items.");
        }
        pendingTransactions.add(trimmed);
    }

    synchronized int pendingCount() {
        return pendingTransactions.size();
    }

    synchronized void discardPendingTransactions() {
        pendingTransactions.clear();
    }

    synchronized void resetEpoch() throws IOException {
        JsonObject request = request("RESET_EPOCH");
        awaitResult(request);
    }

    synchronized WorkerResult construct(Path dataset, int limit,
                                        DhopmEngine.Parameters parameters) throws IOException {
        JsonObject request = request("CONSTRUCT");
        if (dataset != null) {
            request.addProperty("datasetPath", dataset.toAbsolutePath().toString());
        } else {
            request.add("datasetPath", null);
        }
        request.addProperty("limit", limit);
        request.add("parameters", GSON.toJsonTree(parameters));
        JsonArray queued = new JsonArray();
        pendingTransactions.forEach(queued::add);
        request.add("queuedTransactions", queued);
        WorkerResult result = awaitPhase(request);
        pendingTransactions.clear();
        return result;
    }

    synchronized WorkerResult reconstruct(DhopmEngine.Parameters parameters) throws IOException {
        JsonObject request = request("RECONSTRUCT");
        request.add("parameters", GSON.toJsonTree(parameters));
        return awaitPhase(request);
    }

    synchronized WorkerResult mine() throws IOException {
        return awaitPhase(request("MINE"));
    }

    private JsonObject request(String operation) throws IOException {
        ensureHealthy();
        String id = "v1-" + (++requestSequence) + "-" + UUID.randomUUID();
        JsonObject request = new JsonObject();
        request.addProperty("requestId", id);
        request.addProperty("operation", operation);
        return request;
    }

    private void send(JsonObject request) throws IOException {
        try {
            input.write(GSON.toJson(request));
            input.newLine();
            input.flush();
        } catch (IOException ex) {
            healthy = false;
            throw workerFailure("Could not send request to compute worker.", ex);
        }
    }

    private WorkerResult awaitPhase(JsonObject request) throws IOException {
        send(request);
        String id = string(request, "requestId");
        boolean phaseStarted = false;
        WindowsRssSampler.Metrics rssMetrics = null;
        long deadline = System.nanoTime() + REQUEST_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            JsonObject response = readResponseUntil(deadline);
            if (!id.equals(string(response, "requestId"))) {
                healthy = false;
                throw workerFailure("Compute worker returned a response for a different request.", null);
            }
            String type = string(response, "type");
            if ("PHASE_STARTED".equals(type)) {
                if (phaseStarted) {
                    healthy = false;
                    throw workerFailure("Compute worker reported a duplicate phase start.", null);
                }
                phaseStarted = true;
                rssSampler.startPhase();
            } else if ("PHASE_COMPUTE_DONE".equals(type)) {
                if (!phaseStarted) {
                    healthy = false;
                    throw workerFailure("Compute worker ended a phase before it started.", null);
                }
                rssMetrics = rssSampler.finishPhase();
            } else if ("ERROR".equals(type)) {
                healthy = false;
                throw workerFailure(string(response, "message"), null);
            } else if ("RESULT".equals(type)) {
                if (!phaseStarted || rssMetrics == null) {
                    healthy = false;
                    throw workerFailure("Compute worker returned an incomplete phase result.", null);
                }
                DhopmEngine.PhaseResult result = GSON.fromJson(
                        response.get("result"), DhopmEngine.PhaseResult.class);
                Metrics metrics = new Metrics(number(response, "heapUsedStartBytes"),
                        number(response, "heapUsedEndBytes"), number(response, "heapPeakBytes"),
                        number(response, "workerCpuNanos"), number(response, "gcCountDelta"),
                        number(response, "workerPid"), startupNanos, rssMetrics);
                return new WorkerResult(result, metrics);
            }
        }
        healthy = false;
        throw workerFailure("Compute worker request timed out.", null);
    }

    private void awaitResult(JsonObject request) throws IOException {
        send(request);
        JsonObject response = readResponse(REQUEST_TIMEOUT);
        if (!string(request, "requestId").equals(string(response, "requestId"))
                || !"RESULT".equals(string(response, "type"))
                || !response.has("success") || !response.get("success").getAsBoolean()) {
            healthy = false;
            throw workerFailure(string(response, "message"), null);
        }
    }

    private JsonObject readResponseUntil(long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw workerFailure("Timed out waiting for compute worker.", null);
        }
        return readResponse(Duration.ofNanos(remaining));
    }

    private JsonObject readResponse(Duration timeout) throws IOException {
        try {
            String line = outputLines.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                healthy = false;
                if (!process.isAlive()) {
                    throw workerFailure("Compute worker exited with code " + process.exitValue() + ".", null);
                }
                throw workerFailure("Timed out waiting for compute worker response.", null);
            }
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            healthy = false;
            throw workerFailure("Interrupted while waiting for compute worker.", ex);
        } catch (RuntimeException ex) {
            healthy = false;
            throw workerFailure("Compute worker emitted invalid protocol JSON.", ex);
        }
    }

    private void readLines(Process worker) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.offer(line);
            }
        } catch (IOException ex) {
            if (!closed.get()) {
                JsonObject error = new JsonObject();
                error.addProperty("type", "ERROR");
                error.addProperty("message", "Worker stdout failed: " + ex.getMessage());
                outputLines.offer(GSON.toJson(error));
            }
        }
    }

    private void readStderr(Process worker) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(worker.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (this) {
                    stderrTail = (stderrTail + System.lineSeparator() + line);
                    if (stderrTail.length() > 8000) {
                        stderrTail = stderrTail.substring(stderrTail.length() - 8000);
                    }
                }
            }
        } catch (IOException ex) {
            synchronized (this) {
                stderrTail = stderrTail + System.lineSeparator() + "stderr reader: " + ex.getMessage();
            }
        }
    }

    private IOException workerFailure(String message, Throwable cause) {
        String detail = message == null || message.isBlank() ? "No worker error detail." : message;
        String stderr;
        synchronized (this) {
            stderr = stderrTail;
        }
        String full = "Compute worker failure: " + detail
                + (stderr.isBlank() ? "" : System.lineSeparator() + stderr);
        return cause == null ? new IOException(full) : new IOException(full, cause);
    }

    private void ensureHealthy() {
        if (!healthy || closed.get() || !process.isAlive()) {
            throw new IllegalStateException("Compute worker is unavailable; restart the engine application.");
        }
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String workerClassPath() throws IOException {
        try {
            String engine = Path.of(DhopmEngine.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString();
            String gson = Path.of(Gson.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString();
            String jna = Path.of(Native.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString();
            String jnaPlatform = Path.of(WinNT.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString();
            return String.join(System.getProperty("path.separator"), engine, gson, jna, jnaPlatform);
        } catch (URISyntaxException | NullPointerException ex) {
            throw new IOException("Could not determine the compute worker classpath.", ex);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static long number(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong() : -1;
    }

    long processId() {
        return process.pid();
    }

    boolean available() {
        return healthy && !closed.get() && process.isAlive();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (rssSampler != null) {
            rssSampler.close();
        }
        try {
            input.write("{\"requestId\":\"shutdown-" + UUID.randomUUID()
                    + "\",\"operation\":\"SHUTDOWN\"}");
            input.newLine();
            input.flush();
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (IOException ex) {
            // The process may already have exited; termination below remains the fallback.
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The worker process is already being shut down.
        }
    }

    record WorkerResult(DhopmEngine.PhaseResult result, Metrics metrics) {
    }

    record Metrics(long heapUsedStartBytes, long heapUsedEndBytes, long heapPeakBytes,
                   long workerCpuNanos, long gcCountDelta, long workerPid,
                   long workerStartupNanos, WindowsRssSampler.Metrics rss) {
    }
}
