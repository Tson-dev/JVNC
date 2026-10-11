package dhopm.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Pattern;
import dhopm.common.window.ParameterValidator;
import dhopm.common.window.WindowInfo;
import dhopm.common.window.WindowMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the {@code dhopm-common} API types into the wire model of plans/08-FE-CONTRACT.md. */
public final class Wire {

    private Wire() {
    }

    public static Map<String, Object> config(MiningConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("partial", c.partial());
        m.put("f", c.decayFactor());
        m.put("minOcc", c.minOcc());
        m.put("epsilon", c.epsilon());
        m.put("workers", c.workers());
        return m;
    }

    public static Map<String, Object> pattern(Pattern p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", List.of(p.items()));
        m.put("key", p.canonicalKey());
        m.put("do", p.dampedOccupancy());
        m.put("support", p.tids().length);
        List<Integer> tids = new ArrayList<>(p.tids().length);
        for (int t : p.tids()) {
            tids.add(t);
        }
        m.put("tids", tids);
        return m;
    }

    public static List<Map<String, Object>> patterns(List<Pattern> patterns) {
        List<Map<String, Object>> out = new ArrayList<>(patterns.size());
        for (Pattern p : patterns) {
            out.add(pattern(p));
        }
        return out;
    }

    /** WindowInfo with {@code size=null} meaning W = infinity (R8). */
    public static Map<String, Object> window(WindowInfo w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", w.isInfinite() ? null : w.windowSize());
        m.put("effective", w.effectiveTransactions());
        m.put("minSup", w.minSup());
        m.put("maxDO", w.maxDO());
        m.put("evictions", w.evictions());
        m.put("liveEntries", w.liveEntries());
        m.put("deadEntries", w.deadEntries());
        return m;
    }

    /** Lookup-only window block (no dataset, no TL) — only the asymptotic bounds are legal (D38). */
    public static Map<String, Object> windowLookup(double f, double minOcc) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            long size = WindowMath.computeWindow(f, minOcc);
            m.put("size", size == WindowMath.INFINITE ? null : size);
            double maxDOAsymptotic = f == 1.0 ? Double.POSITIVE_INFINITY : 1.0 / (1.0 - f);
            m.put("maxDOAsymptotic", maxDOAsymptotic);
            m.put("maxPartialAsymptotic", WindowMath.maxPartialAsymptotic(f, minOcc));
            m.put("maxDOExact", null);
            m.put("maxPartialExact", null);
            return m;
        } catch (IllegalArgumentException e) {
            throw new ManagerException(Codes.MIN_OCC_TOO_LARGE, e.getMessage());
        }
    }

    public static Map<String, Object> issue(ParameterValidator.Issue i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", i.severity().name());
        m.put("code", i.code());
        m.put("message", i.message());
        return m;
    }

    public static List<Map<String, Object>> issues(List<ParameterValidator.Issue> issues) {
        List<Map<String, Object>> out = new ArrayList<>(issues.size());
        for (ParameterValidator.Issue i : issues) {
            out.add(issue(i));
        }
        return out;
    }
}
