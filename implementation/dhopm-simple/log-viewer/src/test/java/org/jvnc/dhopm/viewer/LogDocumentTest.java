package org.jvnc.dhopm.viewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogDocumentTest {
    @TempDir
    Path tempDir;

    @Test
    void readsPhaseEventsAndOptionalMetrics() throws Exception {
        Path log = tempDir.resolve("run.txt");
        Files.writeString(log, """
                {
                  "schemaVersion": "1.0",
                  "engineVersion": "v1-original",
                  "sessionId": "run-1",
                  "startedAt": "2026-01-01T10:00:00",
                  "events": [{
                    "phase": "MINING",
                    "computeWallNanos": 12,
                    "heapUsedBytes": 100,
                    "snapshot": {"lastTid": 2}
                  }]
                }
                """);

        LogDocument document = LogDocument.read(log);

        assertEquals("v1-original", document.engineVersion());
        assertEquals(1, document.events().size());
        assertEquals("MINING", document.events().getFirst().phase());
        assertEquals("unavailable", document.events().getFirst().rssStatus());
        assertNull(document.events().getFirst().rssStartBytes());
        assertNull(document.events().getFirst().rssEndBytes());
        assertEquals(12, document.events().getFirst().computeWallNanos());
        assertEquals(0, document.events().getFirst().duboCompared().size());
        assertEquals(0, document.events().getFirst().patterns().size());
    }

    @Test
    void rejectsLogsWithoutPhaseEvents() throws Exception {
        Path log = tempDir.resolve("empty.txt");
        Files.writeString(log, "{\"schemaVersion\":\"1.0\",\"engineVersion\":\"v1\",\"events\":[]}");

        assertThrows(IOException.class, () -> LogDocument.read(log));
    }

    @Test
    void rejectsLogsMissingVersionMetadata() throws Exception {
        Path log = tempDir.resolve("missing-version.txt");
        Files.writeString(log, "{\"events\":[{\"phase\":\"MINING\"}]}");

        assertThrows(IOException.class, () -> LogDocument.read(log));
    }

    @Test
    void readsWorkerRssMetricsAndKeepsMissingValuesUnavailable() throws Exception {
        Path log = tempDir.resolve("worker-rss.txt");
        Files.writeString(log, """
                {
                  "schemaVersion": "1.0",
                  "engineVersion": "v1-original",
                  "events": [{
                    "phase": "MINING",
                    "heapUsedBytes": 100,
                    "heapUsedStartBytes": 80,
                    "heapUsedEndBytes": 100,
                    "workerPid": 1234,
                    "rssStartBytes": 200,
                    "rssEndBytes": 250,
                    "rssPeakSampledBytes": 300,
                    "rssSampleCount": 4,
                    "rssSamplingIntervalMs": 20,
                    "rssMetric": "windows-working-set",
                    "rssStatus": "available",
                    "snapshot": {}
                  }]
                }
                """);

        LogDocument.PhaseEvent event = LogDocument.read(log).events().getFirst();

        assertEquals(80, event.heapUsedStartBytes());
        assertEquals(100, event.heapUsedEndBytes());
        assertEquals(1234, event.workerPid());
        assertEquals(200L, event.rssStartBytes());
        assertEquals(250L, event.rssEndBytes());
        assertEquals(300L, event.rssPeakSampledBytes());
        assertEquals(4, event.rssSampleCount());
        assertEquals("windows-working-set", event.rssMetric());
    }

    @Test
    void readsComputedDuboOutcomesAndFinalDopAsSeparateCollections() throws Exception {
        Path log = tempDir.resolve("dubo-and-dop.txt");
        Files.writeString(log, """
                {
                  "schemaVersion": "1.0",
                  "engineVersion": "v3-window",
                  "events": [{
                    "phase": "MINING",
                    "minSup": 2.0,
                    "duboCompared": [
                      {"patternKey":"A,C","value":2.0,"minSup":2.0,"passesMinSup":true}
                    ],
                    "patterns": [
                      {"key":"A,C","support":3,"dampedOccupancy":2.25}
                    ],
                    "snapshot": {}
                  }]
                }
                """);

        LogDocument.PhaseEvent event = LogDocument.read(log).events().getFirst();

        assertEquals(1, event.duboCompared().size());
        assertEquals(true, event.duboCompared().get(0).getAsJsonObject()
                .get("passesMinSup").getAsBoolean());
        assertEquals(1, event.patterns().size());
        assertEquals("A,C", event.patterns().get(0).getAsJsonObject().get("key").getAsString());
    }
}
