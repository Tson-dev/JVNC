package dhopm.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Engine;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseAwareEngine;
import dhopm.common.contract.ProgressAwareEngine;
import dhopm.common.contract.WindowAwareEngine;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.common.window.ParameterValidator;
import dhopm.common.window.WindowInfo;
import dhopm.common.window.WindowMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one mining job per requested version (G5-M1/M2). Each job gets a fresh engine, feeds the
 * dataset in {@code parts} batches (final {@code mineNow} sees all data, so the DOP set is
 * partition-independent) and captures timing, window state and validator issues off the hot path.
 */
public final class MiningRunner {

    /** Result of running one version. */
    public record Job(String version, String engine, MineResult result, WindowInfo window,
                      List<ParameterValidator.Issue> issues, TimingRecorder timing,
                      Map<String, Object> engineStats) {
        public int patternCount() {
            return result.patterns().size();
        }
    }

    private final VersionRegistry registry;

    public MiningRunner(VersionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Runs {@code versions} over {@code transactions} with a shared {@code config}.
     *
     * @param perVersionConfig optional per-version {@code {partial,f,minOcc}} overrides; when present
     *                         and inconsistent the request is rejected (R7 / MISMATCHED_WINDOW_CONFIG)
     */
    public List<Job> run(List<String> versions, MiningConfig config, List<Transaction> transactions,
                         int parts, Map<String, Object> perVersionConfig,
                         MiningProgressListener progressListener) {
        checkMismatch(versions, config, perVersionConfig);
        List<Job> jobs = new ArrayList<>(versions.size());
        for (String version : versions) {
            jobs.add(runOne(version, config, transactions, parts, progressListener));
        }
        return jobs;
    }

    private Job runOne(String version, MiningConfig config, List<Transaction> transactions, int parts,
                       MiningProgressListener progressListener) {
        Engine engine = registry.create(version, config);
        TimingRecorder rec = new TimingRecorder();
        if (engine instanceof PhaseAwareEngine pa) {
            pa.setPhaseListener(rec);
        }
        final WindowInfo[] captured = new WindowInfo[1];
        if (engine instanceof WindowAwareEngine wa) {
            wa.setWindowListener(w -> captured[0] = w);
        }
        if (progressListener != null && engine instanceof ProgressAwareEngine pra) {
            pra.setMiningProgressListener(progressListener);
        }

        try {
            int per = Math.max(1, (int) Math.ceil(transactions.size() / (double) Math.max(1, parts)));
            int from = 0;
            MineResult result = new MineResult(List.of(), 0, 0, 0.0);
            while (from < transactions.size()) {
                int to = Math.min(from + per, transactions.size());
                engine.loadBatch(transactions.subList(from, to));
                result = engine.mineNow();
                from = to;
            }
            if (transactions.isEmpty()) {
                result = engine.mineNow();
            }

            List<Transaction> loaded = transactions;
            long tl = result.lastTid();
            WindowInfo window = captured[0] != null ? captured[0] : paperWindow(config, loaded, tl);
            List<ParameterValidator.Issue> issues = ParameterValidator.validate(config, tl);
            Map<String, Object> engineStats = structuralStats(engine);
            return new Job(version, registry.displayName(version), result, window, issues, rec, engineStats);
        } finally {
            if (engine instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // best-effort: closing a worker pool must never fail a successful mine
                }
            }
        }
    }

    private static WindowInfo paperWindow(MiningConfig config, List<Transaction> transactions, long tl) {
        long total = transactions.size();
        long effectiveTl = tl == 0 ? total : tl;
        long w = WindowMath.computeWindow(config.decayFactor(), config.minOcc());
        long nEff = WindowMath.effectiveTransactions(effectiveTl, w);
        double minSup = WindowMath.minSup(config.partial(), effectiveTl, w);
        double z = WindowMath.maxDO(config.decayFactor(), effectiveTl);
        return new WindowInfo(w, nEff, minSup, z, 0, 0, 0);
    }

    /** Best-effort structural counters exposed by the concrete engines (for {@code detail}). */
    private static Map<String, Object> structuralStats(Engine engine) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (engine instanceof dhopm.v2.engine.MiningEngineV2 e2) {
            stats.put("roots", e2.globalNodeCount());
            stats.put("globalEntries", e2.globalEntryCount());
        } else if (engine instanceof dhopm.v3.engine.MiningEngineV3 e3) {
            stats.put("roots", e3.globalNodeCount());
            stats.put("globalEntries", e3.globalEntryCount());
        } else if (engine instanceof dhopm.v1.engine.MiningEngine e1) {
            stats.put("roots", e1.globalNodeCount());
            stats.put("globalEntries", e1.globalEntryCount());
        }
        return stats;
    }

    private void checkMismatch(List<String> versions, MiningConfig base, Map<String, Object> perVersionConfig) {
        if (perVersionConfig == null || perVersionConfig.size() < 2) {
            return;
        }
        String ref = null;
        for (String version : versions) {
            Object raw = perVersionConfig.get(version);
            if (raw == null) {
                continue;
            }
            Params p = new Params(asMap(raw));
            double partial = p.has("partial") ? p.requiredNum("partial") : base.partial();
            double f = p.has("f") ? p.requiredNum("f") : base.decayFactor();
            double minOcc = p.has("minOcc") ? p.requiredNum("minOcc") : base.minOcc();
            String key = partial + "|" + f + "|" + minOcc;
            if (ref == null) {
                ref = key;
            } else if (!ref.equals(key)) {
                throw new ManagerException(Codes.MISMATCHED_WINDOW_CONFIG,
                        "versions must share (partial, f, minOcc); " + ref + " != " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw ManagerException.invalid("versionConfig entries must be objects");
    }

    /** Per-phase milliseconds map for {@code detail}. */
    public static Map<String, Object> phaseMs(TimingRecorder rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("g0", rec.phaseMs(Phase.CONSTRUCTION));
        m.put("g1", rec.phaseMs(Phase.RECONSTRUCTION));
        m.put("g2", rec.phaseMs(Phase.MINING));
        return m;
    }

    /** Union of patterns from all jobs, keyed by canonical key, preserving order of first appearance. */
    public static List<Pattern> aggregate(List<Job> jobs) {
        Map<String, Pattern> byKey = new LinkedHashMap<>();
        for (Job job : jobs) {
            for (Pattern p : job.result().patterns()) {
                byKey.putIfAbsent(p.canonicalKey(), p);
            }
        }
        return new ArrayList<>(byKey.values());
    }
}
