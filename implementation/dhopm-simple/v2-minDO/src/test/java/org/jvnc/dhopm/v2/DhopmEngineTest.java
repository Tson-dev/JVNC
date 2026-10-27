package org.jvnc.dhopm.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
                new DhopmEngine.PathInput(dataset, 0));
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
        engine.construct(new DhopmEngine.PathInput(dataset, 0));

        engine.addPendingTransaction("B C");
        DhopmEngine.PhaseResult second = engine.construct(null);
        assertEquals(2, second.snapshot().lastTid());
        assertEquals(1, second.queuedTransactionsAdded());
        assertThrows(IllegalStateException.class, () -> engine.construct(null));
    }

    @Test
    void rejectsDuplicateItemsAndInvalidFactor() throws Exception {
        DhopmEngine engine = new DhopmEngine();
        assertThrows(IllegalArgumentException.class, () -> engine.addPendingTransaction("A A"));
        Path dataset = tempDir.resolve("single.dat");
        Files.writeString(dataset, "A\n");
        engine.construct(new DhopmEngine.PathInput(dataset, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.reconstruct(0.5, 0.0, 1e-4, 1e-6));
    }

    @Test
    void miningUsesMinSupBasedOnTransactionsRetainedAfterMinDoPruning() throws Exception {
        Path dataset = tempDir.resolve("pruned-minsup.dat");
        Files.writeString(dataset, "A\nA\nA\nA\n");

        DhopmEngine engine = new DhopmEngine();
        engine.construct(new DhopmEngine.PathInput(dataset, 0));
        DhopmEngine.PhaseResult reconstruction = engine.reconstruct(0.75, 0.5, 0.3, 0.0);
        DhopmEngine.PhaseResult mining = engine.mine();

        assertEquals(2, reconstruction.snapshot().activeTransactionCount());
        assertEquals(1.5, reconstruction.minSup(), 1e-12);
        assertEquals(reconstruction.minSup(), mining.minSup(), 1e-12);
        assertTrue(mining.patterns().stream().anyMatch(pattern -> "A".equals(pattern.key())));
    }
}
