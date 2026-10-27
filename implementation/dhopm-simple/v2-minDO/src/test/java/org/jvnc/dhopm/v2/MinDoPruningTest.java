package org.jvnc.dhopm.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinDoPruningTest {
    @TempDir
    Path tempDir;

    @Test
    void directlyTestsFullTransactionContributionAndRemovesExpiredTransactions() throws Exception {
        Path dataset = tempDir.resolve("transactions.dat");
        Files.writeString(dataset, "A B\nA\nB\n");

        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0));
        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.0, 0.5, 0.25, 0.0);

        assertEquals(3, reconstruction.snapshot().transactionCount());
        assertEquals(2, reconstruction.snapshot().activeTransactionCount());
        assertEquals(1, reconstruction.evictedTransactions());
        assertFalse(reconstruction.snapshot().transactions().getFirst().active());
        assertTrue(reconstruction.snapshot().transactions().get(1).active());
    }

    @Test
    void zeroMinDoDisablesPruningEvenWithPositiveEpsilon() throws Exception {
        Path dataset = tempDir.resolve("transactions.dat");
        Files.writeString(dataset, "A\nA\n");

        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0));
        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.0, 0.5, 0.0, 1e-6);

        assertEquals(2, reconstruction.snapshot().activeTransactionCount());
        assertEquals(0, reconstruction.evictedTransactions());
    }

    @Test
    void unitThresholdReturnsEmptyReconstructedSnapshot() throws Exception {
        Path dataset = tempDir.resolve("transactions.dat");
        Files.writeString(dataset, "A\n");

        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0));
        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.25, 0.9, 1.0, 1e-6);
        DhopmEngine.PhaseResult mining = engine.mine();

        assertEquals(0, reconstruction.snapshot().activeTransactionCount());
        assertTrue(mining.patterns().isEmpty());
    }

    @Test
    void factorOneKeepsAllTransactionsWhenMinDoIsBelowOne() throws Exception {
        Path dataset = tempDir.resolve("transactions.dat");
        Files.writeString(dataset, "A\nB\n");

        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0));
        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.0, 1.0, 0.25, 0.0);

        assertEquals(2, reconstruction.snapshot().activeTransactionCount());
        assertEquals(0, reconstruction.evictedTransactions());
    }
}
