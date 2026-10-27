package org.jvnc.dhopm.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record LogDocument(String engineVersion, String sessionId, String startedAt, List<PhaseEvent> events) {
    static LogDocument read(Path path) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("Log root must be a JSON object.");
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Could not parse log JSON: " + ex.getMessage(), ex);
        }

        if (text(root, "schemaVersion", "").isBlank()
                || text(root, "engineVersion", "").isBlank()) {
            throw new IOException("Log is missing schemaVersion or engineVersion metadata.");
        }
        JsonElement eventsElement = root.get("events");
        if (eventsElement == null || !eventsElement.isJsonArray()) {
            throw new IOException("Log does not contain an events array.");
        }
        List<PhaseEvent> events = new ArrayList<>();
        for (JsonElement element : eventsElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IOException("Each log event must be a JSON object.");
            }
            try {
                PhaseEvent event = PhaseEvent.from(element.getAsJsonObject());
                if (event.phase().isBlank()) {
                    throw new IOException("A log event is missing its phase name.");
                }
                events.add(event);
            } catch (RuntimeException ex) {
                throw new IOException("A log event contains an invalid field: " + ex.getMessage(), ex);
            }
        }
        if (events.isEmpty()) {
            throw new IOException("Log contains no phase events.");
        }
        return new LogDocument(text(root, "engineVersion", "unknown"),
                text(root, "sessionId", ""), text(root, "startedAt", ""),
                List.copyOf(events));
    }

    private static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    record PhaseEvent(String phase, String completedAt, String datasetName, double minSup,
                      long computeWallNanos, long heapUsedBytes, long heapPeakBytes,
                      long workerCpuNanos, Long rssBytes, String rssStatus,
                      long heapUsedStartBytes, long heapUsedEndBytes, long gcCountDelta,
                      long workerPid, long workerStartupNanos, Long rssStartBytes,
                      Long rssEndBytes, Long rssPeakSampledBytes, long rssSampleCount,
                      long rssSamplingIntervalMs, String rssMetric,
                      JsonObject parameters, JsonObject snapshot,
                      JsonArray duboCompared, JsonArray patterns) {
        PhaseEvent(String phase, String completedAt, String datasetName, double minSup,
                   long computeWallNanos, long heapUsedBytes, long heapPeakBytes,
                   long workerCpuNanos, Long rssBytes, String rssStatus,
                   JsonObject parameters, JsonObject snapshot, JsonArray duboCompared) {
            this(phase, completedAt, datasetName, minSup, computeWallNanos, heapUsedBytes,
                    heapPeakBytes, workerCpuNanos, rssBytes, rssStatus, parameters, snapshot,
                    duboCompared, new JsonArray());
        }

        PhaseEvent(String phase, String completedAt, String datasetName, double minSup,
                   long computeWallNanos, long heapUsedBytes, long heapPeakBytes,
                   long workerCpuNanos, Long rssBytes, String rssStatus,
                   JsonObject parameters, JsonObject snapshot, JsonArray duboCompared,
                   JsonArray patterns) {
            this(phase, completedAt, datasetName, minSup, computeWallNanos, heapUsedBytes,
                    heapPeakBytes, workerCpuNanos, rssBytes, rssStatus,
                    heapUsedBytes, heapUsedBytes, -1, -1, -1, null, null, rssBytes,
                    -1, -1, "unavailable", parameters, snapshot, duboCompared, patterns);
        }

        private static PhaseEvent from(JsonObject event) {
            JsonObject snapshot = object(event, "snapshot");
            Long legacyRss = nullableInteger(event, "rssBytes");
            Long rssPeak = nullableInteger(event, "rssPeakSampledBytes");
            if (rssPeak == null) {
                rssPeak = legacyRss;
            }
            return new PhaseEvent(text(event, "phase", "unknown"),
                    text(event, "completedAt", ""), text(event, "datasetName", ""),
                    number(event, "minSup"), integer(event, "computeWallNanos"),
                    integer(event, "heapUsedBytes"), integer(event, "heapPeakBytes"),
                    integer(event, "workerCpuNanos"), legacyRss,
                    text(event, "rssStatus", "unavailable"),
                    integer(event, "heapUsedStartBytes", integer(event, "heapUsedBytes")),
                    integer(event, "heapUsedEndBytes", integer(event, "heapUsedBytes")),
                    integer(event, "gcCountDelta"), integer(event, "workerPid"),
                    integer(event, "workerStartupNanos"),
                    nullableInteger(event, "rssStartBytes"), nullableInteger(event, "rssEndBytes"),
                    rssPeak, integer(event, "rssSampleCount"),
                    integer(event, "rssSamplingIntervalMs"),
                    text(event, "rssMetric", "unavailable"),
                    object(event, "parameters"), snapshot, array(event, "duboCompared"),
                    array(event, "patterns"));
        }

        private static JsonObject object(JsonObject object, String key) {
            JsonElement value = object.get(key);
            return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
        }

        private static JsonArray array(JsonObject object, String key) {
            JsonElement value = object.get(key);
            return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
        }

        private static double number(JsonObject object, String key) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
        }

        private static long integer(JsonObject object, String key) {
            return integer(object, key, -1);
        }

        private static long integer(JsonObject object, String key, long fallback) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
        }

        private static Long nullableInteger(JsonObject object, String key) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? null : value.getAsLong();
        }
    }
}
