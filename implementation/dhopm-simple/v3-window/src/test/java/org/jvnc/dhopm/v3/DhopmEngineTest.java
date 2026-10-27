package org.jvnc.dhopm.v3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhopmEngineTest {
    @TempDir
    Path tempDir;

    @Test
    void matchesPaperExampleForDampedOccupancyAndPatternMining() throws Exception {
        Path dataset = tempDir.resolve("db0.dat");
        Files.writeString(dataset, """
                A C D E
                A E F
                B C D E
                C D F
                """);

        DhopmEngine engine = new DhopmEngine();
        DhopmEngine.PhaseResult construction = engine.construct(
                new DhopmEngine.PathInput(dataset, 0), 0.9, 1e-4, 1e-6);
        assertEquals(4, construction.snapshot().lastTid());
        assertEquals(6, construction.snapshot().itemCount());

        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.25, 0.9, 1e-4, 1e-6);
        assertEquals(1.0, reconstruction.minSup());
        DhopmEngine.PhaseResult mining = engine.mine();

        DhopmEngine.PatternResult cd = mining.patterns().stream()
                .filter(pattern -> "C,D".equals(pattern.key()))
                .findFirst()
                .orElseThrow();
        assertEquals(1.4811666666666666, cd.dampedOccupancy(), 1e-12);
        assertFalse(mining.patterns().stream().anyMatch(pattern -> "A,E".equals(pattern.key())));
        assertTrue(mining.dubo().stream().anyMatch(observation ->
                "C,D".equals(observation.patternKey()) && observation.value() >= 1.0));
    }

    @Test
    void consumesQueuedTransactionsOnlyOnceAndKeepsTidContinuous() throws Exception {
        Path dataset = tempDir.resolve("single.dat");
        Files.writeString(dataset, "A B\n");
        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0), 0.9, 1e-4, 1e-6);

        engine.addPendingTransaction("B C");
        DhopmEngine.PhaseResult second = engine.construct(null, 0.9, 1e-4, 1e-6);
        assertEquals(2, second.snapshot().lastTid());
        assertEquals(1, second.queuedTransactionsAdded());
        assertThrows(IllegalStateException.class, () -> engine.construct(null, 0.9, 1e-4, 1e-6));
    }

    @Test
    void rejectsDuplicateItemsAndInvalidFactor() throws Exception {
        DhopmEngine engine = new DhopmEngine();
        assertThrows(IllegalArgumentException.class, () -> engine.addPendingTransaction("A A"));
        Path dataset = tempDir.resolve("single.dat");
        Files.writeString(dataset, "A\n");
        engine.construct(new DhopmEngine.PathInput(dataset, 0), 0.9, 1e-4, 1e-6);
        assertThrows(IllegalArgumentException.class, () -> engine.reconstruct(0.5, 0.0, 1e-4, 1e-6));
    }

    @Test
    void constructionRetainsTransactionsInsideTheMinDoWindow() throws Exception {
        Path dataset = tempDir.resolve("window.dat");
        Files.writeString(dataset, "A\nB\nC\nD\n");

        DhopmEngine engine = new DhopmEngine();
        DhopmEngine.PhaseResult construction = engine.construct(
                new DhopmEngine.PathInput(dataset, 0), 0.5, 0.25, 0.0);

        assertEquals(4, construction.snapshot().lastTid());
        assertEquals(2, construction.snapshot().activeTransactionCount());
        assertEquals(2, construction.evictedTransactions());
        assertEquals(List.of(3, 4), construction.snapshot().transactions().stream()
                .filter(DhopmEngine.TransactionSnapshot::active)
                .map(DhopmEngine.TransactionSnapshot::tid).toList());
        assertEquals(List.of(1, 2), construction.snapshot().transactions().stream()
                .filter(transaction -> !transaction.active())
                .map(DhopmEngine.TransactionSnapshot::tid).toList());
    }

    @Test
    void zeroMinDoDisablesWindowEvictionAndUnitThresholdEmptiesIt() throws Exception {
        Path dataset = tempDir.resolve("window.dat");
        Files.writeString(dataset, "A\nB\n");

        DhopmEngine noCutoff = new DhopmEngine();
        DhopmEngine.PhaseResult unlimited = noCutoff.construct(
                new DhopmEngine.PathInput(dataset, 0), 0.5, 0.0, 1e-6);
        assertEquals(2, unlimited.snapshot().activeTransactionCount());
        assertEquals(0, unlimited.evictedTransactions());

        DhopmEngine emptyWindow = new DhopmEngine();
        DhopmEngine.PhaseResult empty = emptyWindow.construct(
                new DhopmEngine.PathInput(dataset, 0), 0.5, 1.0, 1e-6);
        assertEquals(0, empty.snapshot().activeTransactionCount());
        assertEquals(2, empty.evictedTransactions());
    }

    @Test
    void factorOneHasNoFiniteWindowWhenThresholdIsBelowOne() throws Exception {
        Path dataset = tempDir.resolve("window.dat");
        Files.writeString(dataset, "A\nB\n");

        DhopmEngine engine = new DhopmEngine();
        DhopmEngine.PhaseResult construction = engine.construct(
                new DhopmEngine.PathInput(dataset, 0), 1.0, 0.25, 0.0);

        assertEquals(2, construction.snapshot().activeTransactionCount());
        assertEquals(0, construction.evictedTransactions());
    }

    @Test
    void rejectsChangingWindowParametersWithoutStartingANewEpoch() throws Exception {
        Path dataset = tempDir.resolve("window.dat");
        Files.writeString(dataset, "A\n");

        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0), 0.9, 1e-4, 1e-6);
        engine.addPendingTransaction("B");

        assertThrows(IllegalStateException.class, () -> engine.construct(null, 0.8, 1e-4, 1e-6));
        assertThrows(IllegalStateException.class, () -> engine.reconstruct(0.5, 0.8, 1e-4, 1e-6));
    }

    @Test
    void miningUsesMinSupBasedOnRetainedWindowRatherThanTotalStreamLength() throws Exception {
        Path dataset = tempDir.resolve("long-window.dat");
        Files.writeString(dataset, "A\n".repeat(100));

        DhopmEngine engine = new DhopmEngine();
        DhopmEngine.PhaseResult construction = engine.construct(
                new DhopmEngine.PathInput(dataset, 0), 0.9, 0.5, 0.0);
        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.1, 0.9, 0.5, 0.0);
        DhopmEngine.PhaseResult mining = engine.mine();

        assertEquals(7, construction.snapshot().activeTransactionCount());
        assertEquals(0.7, reconstruction.minSup(), 1e-12);
        assertEquals(reconstruction.minSup(), mining.minSup(), 1e-12);
        assertTrue(mining.patterns().stream().anyMatch(pattern -> "A".equals(pattern.key())));
    }
}
