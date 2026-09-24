package dhopm.v1.engine;

import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.testkit.GoldenAssert;
import dhopm.common.testkit.GoldenCase;
import dhopm.common.testkit.GoldenCases;
import dhopm.common.testkit.GoldenRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1 engine must reproduce every golden case TC1-TC8 (hand-run Phần 11)
 * with tolerance 1e-4 and return patterns sorted by canonical key (C6).
 */
class GoldenEngineTest {

    @Test
    void matchesAllGoldenCases() {
        StringBuilder report = new StringBuilder();
        boolean allOk = true;
        for (GoldenCase golden : GoldenCases.all()) {
            try (MiningEngine engine = MiningEngine.defaults(golden.partial(), golden.decayFactor())) {
                boolean ok = GoldenRunner.run(engine, golden, GoldenAssert.GOLDEN_TOLERANCE, report);
                allOk &= ok;
                report.append("  ").append(ok ? "PASS" : "FAIL").append("\n");
            }
        }
        System.out.println("\n" + report);
        assertTrue(allOk, report.toString());
    }

    @Test
    void resultsAreSortedByCanonicalKey() {
        for (GoldenCase golden : List.of(GoldenCases.tc1(), GoldenCases.tc3(), GoldenCases.tc7())) {
            try (MiningEngine engine = MiningEngine.defaults(golden.partial(), golden.decayFactor())) {
                engine.loadBatch(golden.transactions());
                MineResult r = engine.mineNow();
                List<Pattern> patterns = r.patterns();
                for (int i = 1; i < patterns.size(); i++) {
                    assertTrue(patterns.get(i - 1).canonicalKey().compareTo(patterns.get(i).canonicalKey()) <= 0,
                            golden.id() + " patterns must be sorted by canonical key");
                }
            }
        }
    }
}