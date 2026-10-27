package org.jvnc.dhopm.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeWorkerClientIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void persistentChildRetainsStateAcrossPhasesBuildsQueuedTransactionsAndReportsRss() throws Exception {
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
            assertEquals(2, reconstruction.result().snapshot().transactionCount());
            assertEquals(workerPid, reconstruction.metrics().workerPid());
            ComputeWorkerClient.WorkerResult mining = worker.mine();
            assertEquals(2, mining.result().snapshot().transactionCount());
            assertEquals(workerPid, mining.metrics().workerPid());

            worker.addPendingTransaction("B C");
            ComputeWorkerClient.WorkerResult nextConstruction = worker.construct(null, 0, parameters);
            assertEquals(3, nextConstruction.result().snapshot().transactionCount());
            assertEquals(1, nextConstruction.result().queuedTransactionsAdded());
            assertTrue(nextConstruction.result().snapshot().transactions().stream()
                    .anyMatch(transaction -> transaction.tid() == 3
                            && transaction.items().containsAll(java.util.List.of("B", "C"))));
            assertEquals(workerPid, nextConstruction.metrics().workerPid());

            WindowsRssSampler.Metrics rss = construction.metrics().rss();
            assertNotNull(rss);
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                assertEquals("available", rss.rssStatus());
                assertTrue(rss.rssSampleCount() > 0);
                assertNotNull(rss.rssStartBytes());
                assertNotNull(rss.rssEndBytes());
                assertNotNull(rss.rssPeakSampledBytes());
            } else {
                assertEquals("unsupported", rss.rssStatus());
            }

            worker.resetEpoch();
            ComputeWorkerClient.WorkerResult newEpoch = worker.construct(dataset, 0, parameters);
            assertEquals(2, newEpoch.result().snapshot().transactionCount());
            assertEquals(workerPid, newEpoch.metrics().workerPid());

            LogSession session = new LogSession();
            session.add(LogSession.event(mining, parameters, dataset.getFileName().toString()));
            String serialized = session.toJson();
            assertTrue(serialized.contains("\"schemaVersion\""));
            assertTrue(serialized.contains("\"engineVersion\""));
            assertTrue(serialized.contains("\"heapUsedBytes\""));
            assertTrue(serialized.contains("\"computeWallNanos\""));
            assertTrue(serialized.contains("\"rssStatus\""));
            assertTrue(serialized.contains("\"workerStartupNanos\""));
            assertTrue(serialized.contains("\"heapUsedStartBytes\""));
            assertTrue(serialized.contains("\"rssSamplingIntervalMs\""));
            assertTrue(serialized.contains("\"patterns\""));
            assertTrue(serialized.contains("\"passesMinSup\""));
        }
    }
}
