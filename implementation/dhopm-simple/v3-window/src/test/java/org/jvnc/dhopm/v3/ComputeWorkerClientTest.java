package org.jvnc.dhopm.v3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeWorkerClientTest {
    @TempDir
    Path tempDir;

    @Test
    void retainsWindowStateAcrossPhasesConstructsQueuedTransactionsAndReportsWorkerMetrics()
            throws Exception {
        Path dataset = tempDir.resolve("worker-window.dat");
        Files.writeString(dataset, "A\nB\nC\nD\n");
        DhopmEngine.Parameters parameters = new DhopmEngine.Parameters(
                0.25, 0.5, 0.25, 0.0, 0);

        try (ComputeWorkerClient worker = new ComputeWorkerClient()) {
            long workerPid = worker.processId();
            assertNotEquals(ProcessHandle.current().pid(), workerPid);
            assertTrue(worker.startupNanos() >= 0);

            ComputeWorkerClient.WorkerResult construction = worker.construct(dataset, parameters);
            assertEquals(workerPid, construction.metrics().workerPid());
            assertEquals(4, construction.result().snapshot().lastTid());
            assertEquals(2, construction.result().snapshot().activeTransactionCount());

            ComputeWorkerClient.WorkerResult reconstruction = worker.reconstruct(parameters);
            assertEquals(workerPid, reconstruction.metrics().workerPid());
            assertEquals(4, reconstruction.result().snapshot().lastTid());
            assertEquals(2, reconstruction.result().snapshot().activeTransactionCount());
            assertEquals(2, reconstruction.result().snapshot().transactions().stream()
                    .filter(DhopmEngine.TransactionSnapshot::active).count());

            ComputeWorkerClient.WorkerResult mining = worker.mine();
            assertEquals(workerPid, mining.metrics().workerPid());
            assertEquals(4, mining.result().snapshot().lastTid());
            assertEquals(2, mining.result().snapshot().activeTransactionCount());

            worker.addPendingTransaction("D E");
            ComputeWorkerClient.WorkerResult queuedConstruction = worker.construct(null, parameters);
            assertEquals(workerPid, queuedConstruction.metrics().workerPid());
            assertEquals(1, queuedConstruction.result().queuedTransactionsAdded());
            assertEquals(5, queuedConstruction.result().snapshot().lastTid());
            assertEquals(2, queuedConstruction.result().snapshot().activeTransactionCount());
            assertEquals(0, worker.pendingCount());
            worker.reconstruct(parameters);
            assertEquals(0, worker.pendingCount());

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

            LogSession session = new LogSession();
            session.add(LogSession.event(mining.result(), parameters, dataset.getFileName().toString(),
                    mining.metrics()));
            String json = session.toJson();
            assertTrue(json.contains("\"heapUsedBytes\""));
            assertTrue(json.contains("\"heapPeakBytes\""));
            assertTrue(json.contains("\"workerCpuNanos\""));
            assertTrue(json.contains("\"rssBytes\""));
            assertTrue(json.contains("\"rssStatus\""));
            assertTrue(json.contains("\"heapUsedStartBytes\""));
            assertTrue(json.contains("\"heapUsedEndBytes\""));
            assertTrue(json.contains("\"rssStartBytes\""));
            assertTrue(json.contains("\"rssPeakSampledBytes\""));
            assertTrue(json.contains("\"rssSamplingIntervalMs\""));
            assertTrue(json.contains("\"workerPid\""));
            assertTrue(json.contains("\"workerStartupNanos\""));
            assertTrue(json.contains("\"gcCountDelta\""));
            assertTrue(json.contains("\"patterns\""));
            assertTrue(json.contains("\"passesMinSup\""));

            worker.addPendingTransaction("Z");
            assertEquals(1, worker.pendingCount());
            worker.resetEpoch();
            assertEquals(0, worker.pendingCount());
            ComputeWorkerClient.WorkerResult newEpoch = worker.construct(dataset, parameters);
            assertEquals(4, newEpoch.result().snapshot().lastTid());
            assertEquals(workerPid, newEpoch.metrics().workerPid());
        }
    }
}
