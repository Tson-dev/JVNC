package dhopm.v3.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
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
 * V3 invariance: INV-I (minOcc = 0 ⇒ V3 ≡ V1 bit-for-bit), INV-E (determinism across pool sizes)
 * and direct V3 ≡ V2 equality (V3 must reproduce V2's windowed DOP set exactly).
 */
class InvarianceTest {

    private static MineResult runV1(double partial, double f, List<Transaction> stream, int workers) {
        try (MiningEngine e = new MiningEngine(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    private static MineResult runV3(double partial, double f, double minOcc, List<Transaction> stream, int workers) {
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, minOcc, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    private static MineResult runV2(double partial, double f, double minOcc, List<Transaction> stream, int workers) {
        try (dhopm.v2.engine.MiningEngineV2 e = new dhopm.v2.engine.MiningEngineV2(
                new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, minOcc, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    @Test
    void minOccZeroIsBitForBitV1() {
        for (GoldenCase g : GoldenCases.all()) {
            MineResult v1 = runV1(g.partial(), g.decayFactor(), g.transactions(), 4);
            MineResult v3 = runV3(g.partial(), g.decayFactor(), 0.0, g.transactions(), 4);
            assertTrue(GoldenAssert.samePatternSet(v1, v3),
                    "INV-I fails on " + g.id() + " (minOcc=0 must reproduce V1 exactly)");
            assertEquals(v1.minSup(), v3.minSup(), "minSup mismatch on " + g.id());
        }
    }

    @Test
    void minOccZeroEqualsV2Paper() {
        for (GoldenCase g : GoldenCases.all()) {
            MineResult v2 = runV2(g.partial(), g.decayFactor(), 0.0, g.transactions(), 4);
            MineResult v3 = runV3(g.partial(), g.decayFactor(), 0.0, g.transactions(), 4);
            assertTrue(GoldenAssert.samePatternSet(v2, v3),
                    "V3 (minOcc=0) must equal V2 (minOcc=0) on " + g.id());
        }
    }

    @Test
    void minOccZeroDoesNotActivateWindow() {
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 0.0, 2))) {
            e.loadBatch(GoldenCases.baseStream());
            e.mineNow();
            assertTrue(e.windowInfo().isInfinite(), "minOcc=0 must keep the window inactive (paper mode)");
            assertEquals(0, e.evictionCount(), "minOcc=0 must never evict (no GĐ0)");
            assertEquals(0, e.deadEntryCount(), "minOcc=0 must never produce dead entries");
        }
    }

    @Test
    void invarianceAcrossPoolSizesPaper() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int[] workers = {1, 2, 4, cpus};
        MineResult baseline = runV3(0.15, 0.9, 0.0, GoldenCases.baseStream(), 1);
        for (int w : workers) {
            MineResult r = runV3(0.15, 0.9, 0.0, GoldenCases.baseStream(), w);
            assertTrue(GoldenAssert.samePatternSet(baseline, r),
                    "V3 (minOcc=0) must be pool-size independent, mismatch at workers=" + w);
        }
    }

    @Test
    void deterministicAcrossPoolSizesWindowed() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int[] workers = {1, 2, 4, cpus};
        List<Transaction> stream = GoldenCases.baseStream();
        MineResult baseline = runV3(0.15, 0.9, 5e-4, stream, 1);
        for (int w : workers) {
            MineResult r = runV3(0.15, 0.9, 5e-4, stream, w);
            assertTrue(GoldenAssert.samePatternSet(baseline, r),
                    "V3 (minOcc>0) must be pool-size independent, mismatch at workers=" + w);
        }
    }
}