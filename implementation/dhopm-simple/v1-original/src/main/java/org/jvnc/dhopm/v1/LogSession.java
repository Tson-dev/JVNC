package org.jvnc.dhopm.v1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class LogSession {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DEFAULT_LOG_DIRECTORY = Path.of("..", "logs");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String schemaVersion = "1.0";
    private final String engineVersion = "v1-original";
    private final String sessionId = java.util.UUID.randomUUID().toString();
    private final String startedAt = LocalDateTime.now().toString();
    private final String javaVersion = System.getProperty("java.version");
    private final String operatingSystem = System.getProperty("os.name") + " " + System.getProperty("os.arch");
    private final List<Event> events = new ArrayList<>();

    public List<Event> events() {
        return events;
    }

    public void add(Event event) {
        events.add(event);
    }

    public boolean hasUnprocessedLogs() {
        return events.stream().anyMatch(event -> !event.handled);
    }

    public boolean hasUnprocessedMiningPhases() {
        return events.stream().anyMatch(event -> !event.handled
                && ("RECONSTRUCTION".equals(event.phase) || "MINING".equals(event.phase)));
    }

    public void removeUnprocessedMiningPhases() {
        events.removeIf(event -> !event.handled
                && ("RECONSTRUCTION".equals(event.phase) || "MINING".equals(event.phase)));
        events.forEach(event -> event.handled = true);
    }

    public void markHandled() {
        events.forEach(event -> event.handled = true);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public Path export() throws IOException {
        Files.createDirectories(DEFAULT_LOG_DIRECTORY);
        String day = LocalDate.now().format(FILE_DATE);
        for (int id = 1; id < 1_000_000; id++) {
            Path candidate = DEFAULT_LOG_DIRECTORY.resolve(
                    "v1-original_" + day + "_" + String.format("%03d", id) + ".txt");
            try {
                Files.writeString(candidate, toJson(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW);
                return candidate;
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another export used this sequence number; try the next one.
            }
        }
        throw new IOException("Could not allocate a unique log file name.");
    }

    public static Event event(DhopmEngine.PhaseResult result, DhopmEngine.Parameters parameters,
                              String datasetName, ComputeWorkerClient.Metrics metrics) {
        WindowsRssSampler.Metrics rss = metrics.rss();
        return new Event(result.phase(), LocalDateTime.now().toString(), "completed",
                datasetName, parameters, result.minSup(), result.elapsedNanos(),
                metrics.heapUsedEndBytes(), metrics.heapPeakBytes(), metrics.workerCpuNanos(),
                rss.rssPeakSampledBytes(), rss.rssStatus(), metrics.heapUsedStartBytes(),
                metrics.heapUsedEndBytes(), metrics.gcCountDelta(), metrics.workerPid(),
                metrics.workerStartupNanos(), rss.rssStartBytes(), rss.rssEndBytes(),
                rss.rssPeakSampledBytes(), rss.rssSampleCount(), rss.rssSamplingIntervalMs(),
                rss.rssMetric(), result.snapshot(), result.patterns(), result.dubo());
    }

    public static final class Event {
        private final String phase;
        private final String completedAt;
        private final String status;
        private final String datasetName;
        private final DhopmEngine.Parameters parameters;
        private final double minSup;
        private final long computeWallNanos;
        private final long heapUsedBytes;
        private final long heapPeakBytes;
        private final long workerCpuNanos;
        private final Long rssBytes;
        private final String rssStatus;
        private final long heapUsedStartBytes;
        private final long heapUsedEndBytes;
        private final long gcCountDelta;
        private final long workerPid;
        private final long workerStartupNanos;
        private final Long rssStartBytes;
        private final Long rssEndBytes;
        private final Long rssPeakSampledBytes;
        private final long rssSampleCount;
        private final long rssSamplingIntervalMs;
        private final String rssMetric;
        private final DhopmEngine.Snapshot snapshot;
        private final List<DhopmEngine.PatternResult> patterns;
        private final List<DhopmEngine.DuboObservation> duboCompared;
        private boolean handled;

        private Event(String phase, String completedAt, String status, String datasetName,
                      DhopmEngine.Parameters parameters, double minSup, long computeWallNanos,
                      long heapUsedBytes, long heapPeakBytes, long workerCpuNanos,
                      Long rssBytes, String rssStatus, long heapUsedStartBytes,
                      long heapUsedEndBytes, long gcCountDelta, long workerPid,
                      long workerStartupNanos, Long rssStartBytes, Long rssEndBytes,
                      Long rssPeakSampledBytes, long rssSampleCount, long rssSamplingIntervalMs,
                      String rssMetric, DhopmEngine.Snapshot snapshot,
                      List<DhopmEngine.PatternResult> patterns,
                      List<DhopmEngine.DuboObservation> duboCompared) {
            this.phase = phase;
            this.completedAt = completedAt;
            this.status = status;
            this.datasetName = datasetName;
            this.parameters = parameters;
            this.minSup = minSup;
            this.computeWallNanos = computeWallNanos;
            this.heapUsedBytes = heapUsedBytes;
            this.heapPeakBytes = heapPeakBytes;
            this.workerCpuNanos = workerCpuNanos;
            this.rssBytes = rssBytes;
            this.rssStatus = rssStatus;
            this.heapUsedStartBytes = heapUsedStartBytes;
            this.heapUsedEndBytes = heapUsedEndBytes;
            this.gcCountDelta = gcCountDelta;
            this.workerPid = workerPid;
            this.workerStartupNanos = workerStartupNanos;
            this.rssStartBytes = rssStartBytes;
            this.rssEndBytes = rssEndBytes;
            this.rssPeakSampledBytes = rssPeakSampledBytes;
            this.rssSampleCount = rssSampleCount;
            this.rssSamplingIntervalMs = rssSamplingIntervalMs;
            this.rssMetric = rssMetric;
            this.snapshot = snapshot;
            this.patterns = List.copyOf(patterns);
            this.duboCompared = List.copyOf(duboCompared);
        }
    }

    public static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public static long threadCpuTime() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1;
    }

}
