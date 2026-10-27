package org.jvnc.dhopm.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeWorkerClientTest {
    @TempDir
    Path tempDir;

    @Test
    void retainsEngineStateAndSamplesWorkerRssAcrossPhases() throws Exception {
        Path dataset = tempDir.resolve("worker.dat");
        Files.writeString(dataset, "A B\nA\n");
        DhopmEngine.Parameters parameters = new DhopmEngine.Parameters(
                0.25, 0.9, 1e-4, 1e-6, 0);

        try (ComputeWorkerClient worker = new ComputeWorkerClient()) {
            long workerPid = worker.processId();
            assertNotEquals(ProcessHandle.current().pid(), workerPid);

            ComputeWorkerClient.WorkerResult construction = worker.construct(dataset, 0, parameters);
            assertEquals(2, construction.result().snapshot().transactionCount());
            assertEquals(workerPid, construction.metrics().workerPid());

            ComputeWorkerClient.WorkerResult reconstruction = worker.reconstruct(parameters);
            assertEquals(workerPid, reconstruction.metrics().workerPid());
            ComputeWorkerClient.WorkerResult mining = worker.mine();
            assertEquals(workerPid, mining.metrics().workerPid());

            worker.addPendingTransaction("B C");
            ComputeWorkerClient.WorkerResult nextConstruction = worker.construct(null, 0, parameters);
            assertEquals(3, nextConstruction.result().snapshot().transactionCount());
            assertEquals(workerPid, nextConstruction.metrics().workerPid());

            assertNotNull(construction.metrics().rss());
            LogSession session = new LogSession();
            session.add(LogSession.event(construction.result(), parameters, "worker.dat",
                    construction.metrics()));
            JsonObject loggedEvent = JsonParser.parseString(session.toJson())
                    .getAsJsonObject().getAsJsonArray("events").get(0).getAsJsonObject();
            assertEquals(workerPid, loggedEvent.get("workerPid").getAsLong());
            assertEquals("windows-working-set", loggedEvent.get("rssMetric").getAsString());
            assertEquals(construction.metrics().rss().rssPeakSampledBytes(),
                    loggedEvent.get("rssPeakSampledBytes").getAsLong());
            session.add(LogSession.event(mining.result(), parameters, "worker.dat", mining.metrics()));
            JsonObject loggedMining = JsonParser.parseString(session.toJson())
                    .getAsJsonObject().getAsJsonArray("events").get(1).getAsJsonObject();
            assertEquals(mining.result().patterns().size(), loggedMining.getAsJsonArray("patterns").size());
            assertEquals(mining.result().dubo().size(),
                    loggedMining.getAsJsonArray("duboCompared").size());
            assertEquals(mining.result().dubo().getFirst().passesMinSup(),
                    loggedMining.getAsJsonArray("duboCompared").get(0).getAsJsonObject()
                            .get("passesMinSup").getAsBoolean());
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                assertEquals("available", construction.metrics().rss().rssStatus());
                assertTrue(construction.metrics().rss().rssSampleCount() > 0);
                assertNotNull(construction.metrics().rss().rssStartBytes());
                assertNotNull(construction.metrics().rss().rssEndBytes());
                assertNotNull(construction.metrics().rss().rssPeakSampledBytes());
            }

            Path nextDataset = tempDir.resolve("next-epoch.dat");
            Files.writeString(nextDataset, "Z\n");
            worker.resetEpoch();
            ComputeWorkerClient.WorkerResult nextEpoch = worker.construct(nextDataset, 0, parameters);
            assertEquals(1, nextEpoch.result().snapshot().transactionCount());
            worker.close();
            assertFalse(worker.available());
            assertFalse(ProcessHandle.of(workerPid).map(ProcessHandle::isAlive).orElse(false));
        }
    }
}
