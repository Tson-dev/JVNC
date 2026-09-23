package dhopm.common.testkit;

import dhopm.common.contract.Engine;
import dhopm.common.contract.MineResult;

import java.util.List;

/**
 * Runs an {@link Engine} against the golden cases and prints a report.
 *
 * <p>Used by engine modules (G1/G2/G3) to gate correctness; the same cases feed the
 * benchmark/compare app at G4.
 */
public final class GoldenRunner {

    private GoldenRunner() {
    }

    public static boolean run(Engine engine, GoldenCase golden, double tolerance, StringBuilder report) {
        engine.loadBatch(golden.transactions());
        MineResult result = engine.mineNow();
        report.append(golden.id()).append("  f=").append(golden.decayFactor())
                .append(" ∂=").append(golden.partial())
                .append(" minSup=").append(golden.partial() * golden.totalTransactions())
                .append(" -> ").append(result.patterns().size()).append('/').append(golden.expected().size())
                .append(" patterns\n");
        return GoldenAssert.matches(result, golden, tolerance, report);
    }

    public static boolean runAll(Engine engine, List<GoldenCase> cases, StringBuilder report) {
        boolean allOk = true;
        for (GoldenCase c : cases) {
            boolean ok = run(engine, c, GoldenAssert.GOLDEN_TOLERANCE, report);
            allOk &= ok;
            report.append(ok ? "  PASS\n" : "  FAIL\n");
        }
        return allOk;
    }
}