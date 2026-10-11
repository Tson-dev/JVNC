package dhopm.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges per-version jobs into the schema of plans/08-FE-CONTRACT.md (section 7). Result identity is
 * {@code (partial, f, minOcc, TL)} (R7); all jobs share one config, so jobs are directly comparable.
 */
public final class ResultAggregator {

    private ResultAggregator() {
    }

    /** The compact {@code jobs[]} array (one row per version). */
    public static List<Map<String, Object>> jobs(List<MiningRunner.Job> jobs) {
        List<Map<String, Object>> out = new ArrayList<>(jobs.size());
        for (MiningRunner.Job job : jobs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("version", job.version());
            m.put("engine", job.engine());
            m.put("total_ms", job.timing().totalMs());
            m.put("patterns", job.patternCount());
            out.add(m);
        }
        return out;
    }

    /** The {@code perVersion} object: per-version patterns + window + issues + timing. */
    public static Map<String, Object> perVersion(List<MiningRunner.Job> jobs, boolean withStats) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (MiningRunner.Job job : jobs) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("engine", job.engine());
            v.put("total_ms", job.timing().totalMs());
            v.put("totalTransactions", job.result().totalTransactions());
            v.put("lastTid", job.result().lastTid());
            v.put("minSup", job.result().minSup());
            v.put("window", Wire.window(job.window()));
            v.put("issues", Wire.issues(job.issues()));
            v.put("patterns", Wire.patterns(job.result().patterns()));
            if (withStats) {
                Map<String, Object> stats = new LinkedHashMap<>(job.engineStats());
                stats.put("phaseMs", MiningRunner.phaseMs(job.timing()));
                v.put("stats", stats);
            }
            out.put(job.version(), v);
        }
        return out;
    }
}
