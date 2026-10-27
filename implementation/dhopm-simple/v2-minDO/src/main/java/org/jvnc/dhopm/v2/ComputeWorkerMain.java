package org.jvnc.dhopm.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class ComputeWorkerMain {
    private static final Gson GSON = new GsonBuilder().create();

    private ComputeWorkerMain() {
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<DhopmEngine> engine = new AtomicReference<>(new DhopmEngine());
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            JsonObject ready = new JsonObject();
            ready.addProperty("type", "READY");
            ready.addProperty("pid", ProcessHandle.current().pid());
            output.println(GSON.toJson(ready));
            String line;
            while ((line = input.readLine()) != null) {
                JsonObject request;
                try {
                    request = JsonParser.parseString(line).getAsJsonObject();
                } catch (RuntimeException ex) {
                    output.println(error(null, "Invalid request JSON: " + ex.getMessage()));
                    continue;
                }
                String id = string(request, "requestId");
                String operation = string(request, "operation");
                if ("SHUTDOWN".equals(operation)) {
                    respond(output, id, "SHUTDOWN", true, null, null);
                    return;
                }
                try {
                    if ("RESET_EPOCH".equals(operation)) {
                        engine.set(new DhopmEngine());
                        respond(output, id, "RESULT", true, null, null);
                        continue;
                    }
                    DhopmEngine.PhaseResult result;
                    DhopmEngine.Parameters parameters = parameters(request);
                    if ("CONSTRUCT".equals(operation)) {
                        JsonArray queued = array(request, "queuedTransactions");
                        for (JsonElement transaction : queued) {
                            engine.get().addPendingTransaction(transaction.getAsString());
                        }
                        JsonElement dataset = request.get("datasetPath");
                        DhopmEngine.PathInput pathInput = dataset == null || dataset.isJsonNull()
                                ? null : new DhopmEngine.PathInput(Path.of(dataset.getAsString()),
                                integer(request, "limit"));
                        result = runPhase(output, id, "CONSTRUCTION",
                                () -> engine.get().construct(pathInput));
                    } else if ("RECONSTRUCT".equals(operation)) {
                        result = runPhase(output, id, "RECONSTRUCTION",
                                () -> engine.get().reconstruct(parameters.partial(), parameters.factor(),
                                        parameters.minDo(), parameters.epsilon()));
                    } else if ("MINE".equals(operation)) {
                        result = runPhase(output, id, "MINING", () -> engine.get().mine());
                    } else {
                        throw new IllegalArgumentException("Unknown operation: " + operation);
                    }
                    JsonObject response = new JsonObject();
                    response.addProperty("type", "RESULT");
                    response.addProperty("requestId", id);
                    response.add("result", GSON.toJsonTree(result));
                    addWorkerMetrics(response);
                    output.println(GSON.toJson(response));
                } catch (Throwable ex) {
                    respond(output, id, "ERROR", false, null,
                            ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private static DhopmEngine.PhaseResult runPhase(PrintWriter output, String requestId,
                                                     String phase, PhaseAction action) throws Exception {
        output.println(message("PHASE_STARTED", requestId, phase));
        long cpuStart = LogSession.threadCpuTime();
        long heapUsedStart = LogSession.usedHeapBytes();
        long gcCountStart = gcCount();
        for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                try {
                    pool.resetPeakUsage();
                } catch (UnsupportedOperationException ignored) {
                    // This JVM memory pool does not expose a resettable peak.
                }
            }
        }
        DhopmEngine.PhaseResult result = action.run();
        long cpuEnd = LogSession.threadCpuTime();
        long heapUsedEnd = LogSession.usedHeapBytes();
        long heapPeak = heapPeakBytes();
        long gcCountEnd = gcCount();
        output.println(message("PHASE_COMPUTE_DONE", requestId, phase));
        WorkerMetrics.set(new WorkerMetrics(heapUsedStart, heapUsedEnd,
                heapPeak, cpuStart >= 0 && cpuEnd >= cpuStart ? cpuEnd - cpuStart : -1,
                gcCountStart < 0 || gcCountEnd < gcCountStart ? -1 : gcCountEnd - gcCountStart));
        return result;
    }

    private static void addWorkerMetrics(JsonObject response) {
        WorkerMetrics metrics = WorkerMetrics.get();
        response.addProperty("heapUsedStartBytes", metrics.heapUsedStartBytes());
        response.addProperty("heapUsedEndBytes", metrics.heapUsedEndBytes());
        response.addProperty("heapPeakBytes", metrics.heapPeakBytes());
        response.addProperty("workerCpuNanos", metrics.workerCpuNanos());
        response.addProperty("gcCountDelta", metrics.gcCountDelta());
        response.addProperty("workerPid", ProcessHandle.current().pid());
    }

    private static long heapPeakBytes() {
        long peak = 0;
        boolean available = false;
        for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                var usage = pool.getPeakUsage();
                if (usage != null && usage.getUsed() >= 0) {
                    peak += usage.getUsed();
                    available = true;
                }
            }
        }
        return available ? peak : -1;
    }

    private static long gcCount() {
        long count = 0;
        boolean available = false;
        for (var bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionCount() >= 0) {
                count += bean.getCollectionCount();
                available = true;
            }
        }
        return available ? count : -1;
    }

    private static String message(String type, String requestId, String phase) {
        JsonObject response = new JsonObject();
        response.addProperty("type", type);
        response.addProperty("requestId", requestId);
        response.addProperty("phase", phase);
        return GSON.toJson(response);
    }

    private static String error(String requestId, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "ERROR");
        if (requestId != null) {
            response.addProperty("requestId", requestId);
        }
        response.addProperty("success", false);
        response.addProperty("message", message);
        return GSON.toJson(response);
    }

    private static void respond(PrintWriter output, String id, String type, boolean success,
                                DhopmEngine.PhaseResult result, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("type", type);
        response.addProperty("requestId", id);
        response.addProperty("success", success);
        if (result != null) {
            response.add("result", GSON.toJsonTree(result));
        }
        if (message != null) {
            response.addProperty("message", message);
        }
        output.println(GSON.toJson(response));
    }

    private static DhopmEngine.Parameters parameters(JsonObject request) {
        JsonObject parameters = request.has("parameters")
                ? request.getAsJsonObject("parameters") : new JsonObject();
        return new DhopmEngine.Parameters(number(parameters, "partial"),
                number(parameters, "factor"), number(parameters, "minDo"),
                number(parameters, "epsilon"), integer(parameters, "limit"));
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? 0 : element.getAsInt();
    }

    private static double number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? 0 : element.getAsDouble();
    }

    @FunctionalInterface
    private interface PhaseAction {
        DhopmEngine.PhaseResult run() throws Exception;
    }

    private record WorkerMetrics(long heapUsedStartBytes, long heapUsedEndBytes,
                                 long heapPeakBytes, long workerCpuNanos,
                                 long gcCountDelta) {
        private static final ThreadLocal<WorkerMetrics> CURRENT =
                ThreadLocal.withInitial(() -> new WorkerMetrics(-1, -1, -1, -1, -1));

        private static void set(WorkerMetrics metrics) {
            CURRENT.set(metrics);
        }

        private static WorkerMetrics get() {
            return CURRENT.get();
        }
    }
}
