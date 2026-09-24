package dhopm.v1.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Pattern;
import dhopm.common.testkit.GoldenCases;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INV-E: worker count must not change the outcome, bit-for-bit.
 * Each node is accumulated sequentially (C5), merges happen after the join,
 * so DO bits are identical for any pool size 1/2/4/CPU.
 */
class DeterminismTest {

    private static Map<String, Long> runWith(int workers) {
        try (MiningEngine engine = new MiningEngine(
                new MiningConfig(0.10, 0.9, MiningConfig.DEFAULT_EPSILON, workers))) {
            engine.loadBatch(GoldenCases.tc3().transactions());
            Map<String, Long> bits = new LinkedHashMap<>();
            for (Pattern p : engine.mineNow().patterns()) {
                bits.put(p.canonicalKey(), Double.doubleToLongBits(p.dampedOccupancy()));
            }
            return bits;
        }
    }

    @Test
    void identicalAcrosWorkerCounts() {
        Map<String, Long> ref = runWith(1);
        assertEquals(15, ref.size(), "TC3 has 15 patterns");
        for (int workers : new int[]{2, 4, MiningConfig.DEFAULT_WORKERS}) {
            Map<String, Long> actual = runWith(workers);
            assertEquals(ref, actual, "workers=" + workers + " must be bit-for-bit identical");
        }
    }

    @Test
    void repeatedMineNowOnSameEngineIsStable() {
        try (MiningEngine engine = MiningEngine.defaults(0.10, 0.9)) {
            engine.loadBatch(GoldenCases.tc3().transactions());
            Map<String, Long> r1 = toBits(engine.mineNow());
            Map<String, Long> r2 = toBits(engine.mineNow());
            assertEquals(r1, r2);
        }
    }

    private static Map<String, Long> toBits(dhopm.common.contract.MineResult r) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Pattern p : r.patterns()) {
            m.put(p.canonicalKey(), Double.doubleToLongBits(p.dampedOccupancy()));
        }
        return m;
    }
}