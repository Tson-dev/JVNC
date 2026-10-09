package dhopm.v2.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.testkit.GoldenAssert;
import dhopm.common.testkit.GoldenCase;
import dhopm.common.testkit.GoldenCases;
import dhopm.common.transaction.Transaction;
import dhopm.v1.engine.MiningEngine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INV-I (minOcc = 0 ⇒ V2 ≡ V1 bit-for-bit) and INV-E (determinism across pool sizes).
 * Acceptance M5 / plan 02 §6 — TC9 is exactly the minOcc = 0 + TC1 data regression.
 */
class InvarianceTest {

    private static MineResult runV1(double partial, double f, List<Transaction> stream, int workers) {
        try (MiningEngine e = new MiningEngine(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    private static MineResult runV2Paper(double partial, double f, List<Transaction> stream, int workers) {
        try (MiningEngineV2 e = new MiningEngineV2(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, 0.0, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    @Test
    void tc9_minOccZeroIsBitForBitV1() {
        for (GoldenCase g : GoldenCases.all()) {
            MineResult v1 = runV1(g.partial(), g.decayFactor(), g.transactions(), 4);
            MineResult v2 = runV2Paper(g.partial(), g.decayFactor(), g.transactions(), 4);
            assertTrue(GoldenAssert.samePatternSet(v1, v2),
                    "INV-I fails on " + g.id() + " (minOcc=0 must reproduce V1 exactly)");
            assertEquals(v1.minSup(), v2.minSup(), "minSup mismatch on " + g.id());
        }
    }

    @Test
    void minOccZeroDoesNotActivateWindow() {
        try (MiningEngineV2 e = new MiningEngineV2(new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 0.0, 2))) {
            e.loadBatch(GoldenCases.baseStream());
            e.mineNow();
            assertTrue(e.windowInfo().isInfinite(), "minOcc=0 must keep the window inactive (paper mode)");
            assertEquals(0, e.evictionCount(), "minOcc=0 must never evict (no GĐ0)");
            assertEquals(0, e.deadEntryCount(), "minOcc=0 must never produce dead entries");
        }
    }

    @Test
    void invarianceAcrossPoolSizes() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int[] workers = {1, 2, 4, cpus};
        MineResult baseline = runV2Paper(0.15, 0.9, GoldenCases.baseStream(), 1);
        for (int w : workers) {
            MineResult r = runV2Paper(0.15, 0.9, GoldenCases.baseStream(), w);
            assertTrue(GoldenAssert.samePatternSet(baseline, r),
                    "V2 (minOcc=0) must be pool-size independent, mismatch at workers=" + w);
        }
    }

    @Test
    void deterministicAcrossPoolSizesWindowed() {
        // windowed mode (minOcc>0) must also be deterministic (INV-E), not just minOcc=0.
        int cpus = Runtime.getRuntime().availableProcessors();
        int[] workers = {1, 2, 4, cpus};
        List<Transaction> stream = GoldenCases.baseStream();
        MineResult baseline = runWindowed(0.15, 0.9, 5e-4, stream, 1);
        for (int w : workers) {
            MineResult r = runWindowed(0.15, 0.9, 5e-4, stream, w);
            assertTrue(GoldenAssert.samePatternSet(baseline, r),
                    "V2 (minOcc>0) must be pool-size independent, mismatch at workers=" + w);
        }
    }

    private static MineResult runWindowed(double partial, double f, double minOcc, List<Transaction> stream, int workers) {
        try (MiningEngineV2 e = new MiningEngineV2(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, minOcc, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }
}