package dhopm.common.testkit;

import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * Golden comparison logic.
 *
 * <p>Set comparison per canonical C6: patterns are matched by canonical key
 * (items sorted by natural order), DO values must match within a small tolerance
 * (golden values have 4 decimals, tolerance 1e-6 is safe).
 */
public final class GoldenAssert {

    private GoldenAssert() {
    }

    public static final double GOLDEN_TOLERANCE = 1e-6;

    /** Comparison used between two real engine runs (exact double equality). */
    public static boolean samePatternSet(MineResult a, MineResult b) {
        Map<String, Double> mapA = toMap(a.patterns());
        Map<String, Double> mapB = toMap(b.patterns());
        if (!mapA.keySet().equals(mapB.keySet())) {
            return false;
        }
        for (Map.Entry<String, Double> e : mapA.entrySet()) {
            if (Double.compare(e.getValue(), mapB.get(e.getKey())) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks an engine result against a golden case. Appends a fine-grained line to {@code report}
     * (missing / extra / wrong-DO) when not empty.
     */
    public static boolean matches(MineResult actual, GoldenCase golden, double tolerance, StringBuilder report) {
        Map<String, Double> got = toMap(actual.patterns());
        Map<String, Double> want = toMap(golden.expected());

        boolean ok = true;
        for (Map.Entry<String, Double> e : want.entrySet()) {
            Double v = got.get(e.getKey());
            if (v == null) {
                ok = false;
                if (report != null) {
                    report.append("  MISSING ").append(e.getKey()).append("=").append(format(e.getValue())).append('\n');
                }
            } else if (Math.abs(v - e.getValue()) > tolerance) {
                ok = false;
                if (report != null) {
                    report.append("  WRONG   ").append(e.getKey()).append(" expected=").append(format(e.getValue()))
                            .append(" got=").append(format(v)).append('\n');
                }
            }
        }
        for (String key : got.keySet()) {
            if (!want.containsKey(key)) {
                ok = false;
                if (report != null) {
                    report.append("  EXTRA   ").append(key).append("=").append(format(got.get(key))).append('\n');
                }
            }
        }
        return ok;
    }

    private static Map<String, Double> toMap(java.util.List<Pattern> patterns) {
        Map<String, Double> m = new HashMap<>();
        for (Pattern p : patterns) {
            m.put(p.canonicalKey(), p.dampedOccupancy());
        }
        return m;
    }

    private static String format(double v) {
        return String.format("%.4f", v);
    }
}