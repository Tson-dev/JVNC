package dhopm.v2.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseAwareEngine;
import dhopm.common.contract.PhaseListener;
import dhopm.common.contract.ProgressAwareEngine;
import dhopm.common.contract.WindowAwareEngine;
import dhopm.common.contract.WindowListener;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.WorkerPool;
import dhopm.common.window.ParameterValidator;
import dhopm.common.window.WindowInfo;
import dhopm.common.window.WindowMath;
import dhopm.v2.construction.DHOListBuilder;
import dhopm.v2.dho.DHOList;
import dhopm.v2.dho.DHONode;
import dhopm.v2.mining.Miner;
import dhopm.v2.reconstruction.Reconstructor;
import dhopm.v2.window.WindowBuffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import dhopm.common.window.ParameterValidator.Issue;
import dhopm.common.window.ParameterValidator.Severity;

/**
 * V2 engine: the damped window minOcc (plan 02). Same pipeline as V1 (construction → reconstruction →
 * mining, Level 1 threading, deterministic) with four window extensions:
 *
 * <ol>
 *   <li><b>GĐ0</b> — a circular {@code W}-slot handle buffer; evict {@code m−W} before writing
 *       {@code m}; entries point at their slot handle (ref1) and test liveness via the handle
 *       payload (D39). Transactions outside the final window of a bulk batch are skipped entirely.</li>
 *   <li><b>two-phase minSup</b> — {@code ∂ × N_eff}, {@code N_eff = min(TL, W)} (C9/D11), computed
 *       at {@code mineNow()} (INV-F).</li>
 *   <li><b>DO ceiling short-circuit</b> — if {@code ∂×N_eff &gt; Z(f,TL) + ε} the DOP set is
 *       provably empty ⇒ return ∅ in O(1).</li>
 *   <li><b>tighter prune bound</b> — {@code UB'(X) = min(DUBO(X), Z(X))} when the window is finite.</li>
 * </ol>
 *
 * <p><b>INV-I (paper mode):</b> with minOcc = 0 there is no window: {ref1 = null} entries, N_eff = TL,
 * DUBO-only prune — the run is bit-for-bit identical to V1. Validation is fail-fast on
 * {@code MIN_OCC_TOO_LARGE} / {@code WINDOW_TOO_LARGE} (ERROR); warnings are accumulated and
 * re-exposed after every {@code mineNow()}.
 */
public final class MiningEngineV2 implements PhaseAwareEngine, ProgressAwareEngine, WindowAwareEngine, AutoCloseable {

    private final MiningConfig config;
    private final WorkerPool pool;
    private final DHOList global = new DHOList();
    private final long windowSize;
    private final boolean windowTruncated;
    private final WindowBuffer buffer;
    private final List<Issue> warnings = new ArrayList<>();

    private long totalTransactions;
    private int tl;
    private long globalEntries;
    private int lastMiningTasks;
    private long evictions;
    private long liveEntries;
    private long deadEntries;
    private WindowInfo windowInfo = WindowInfo.none();

    private PhaseListener phaseListener;
    private MiningProgressListener miningProgressListener;
    private WindowListener windowListener;

    public MiningEngineV2(MiningConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        for (Issue issue : ParameterValidator.validate(config)) {
            if (issue.severity() == Severity.ERROR) {
                throw new IllegalArgumentException("invalid V2 config: " + issue);
            }
            warnings.add(issue);
        }
        this.windowSize = WindowMath.computeWindow(config.decayFactor(), config.minOcc());
        this.windowTruncated = windowSize != WindowMath.INFINITE;
        this.buffer = windowTruncated ? new WindowBuffer(windowSize) : null;
        this.pool = new WorkerPool(config.workers());
    }

    /** Library default: {@code minOcc = 1e-6} (D41) — a real (truncating) window. */
    public static MiningEngineV2 defaults(double partial, double f) {
        return new MiningEngineV2(MiningConfig.of(partial, f, MiningConfig.DEFAULT_MIN_OCC));
    }

    @Override
    public String name() {
        return "v2-minocc";
    }

    @Override
    public void setPhaseListener(PhaseListener listener) {
        this.phaseListener = listener;
    }

    @Override
    public void setMiningProgressListener(MiningProgressListener listener) {
        this.miningProgressListener = listener;
    }

    @Override
    public void setWindowListener(WindowListener listener) {
        this.windowListener = listener;
    }

    @Override
    public WindowInfo windowInfo() {
        return windowInfo;
    }

    /** Read-only stats for tooling (no internal leakage). */

    public MiningConfig config() {
        return config;
    }

    /** Validation warnings from the last {@link #mineNow()} (stable codes). */
    public List<Issue> warnings() {
        return List.copyOf(warnings);
    }

    public long windowSize() {
        return windowSize;
    }

    public int globalNodeCount() {
        return global.size();
    }

    public long globalEntryCount() {
        return globalEntries;
    }

    public long totalLoaded() {
        return totalTransactions;
    }

    public int lastTid() {
        return tl;
    }

    public int lastMiningTasks() {
        return lastMiningTasks;
    }

    public long evictionCount() {
        return evictions;
    }

    public long liveEntryCount() {
        return liveEntries;
    }

    public long deadEntryCount() {
        return deadEntries;
    }

    @Override
    public void loadBatch(List<Transaction> batch) {
        long start = phaseListener != null ? System.nanoTime() : 0L;
        if (!batch.isEmpty()) {
            if (windowTruncated) {
                int batchMax = batch.get(batch.size() - 1).tid();
                for (Transaction t : batch) {
                    requireIncreasing(t);
                    tl = t.tid();
                    totalTransactions++;
                    // GĐ0 evict the transaction leaving the window (clear-before-write).
                    long victim = t.tid() - windowSize;
                    if (victim >= 1L && buffer.evict((int) victim)) {
                        evictions++;
                    }
                    // Skip writing anything that will be outside the FINAL window of this batch
                    // (bulk-load optimization): only the last W matter for the next mineNow().
                    if ((long) batchMax - t.tid() < windowSize) {
                        buffer.write(t.tid(), t);
                        DHOListBuilder.add(t, global, buffer);
                        globalEntries += t.length();
                    }
                }
            } else {
                for (Transaction t : batch) {
                    requireIncreasing(t);
                    tl = t.tid();
                    totalTransactions++;
                    DHOListBuilder.addNoWindow(t, global);
                    globalEntries += t.length();
                }
            }
        }
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.CONSTRUCTION, start, System.nanoTime());
        }
    }

    @Override
    public MineResult mineNow() {
        if (totalTransactions == 0) {
            return new MineResult(List.of(), 0, 0, 0.0);
        }

        double minSup = WindowMath.minSup(config.partial(), tl, windowSize); // INV-F: two-phase
        double z = WindowMath.maxDO(config.decayFactor(), tl);

        // Re-validate with the known stream length (may add INFEASIBLE_PARTIAL / PARTIAL_ZERO).
        List<Issue> issues = ParameterValidator.validate(config, tl);
        for (Issue issue : issues) {
            if (issue.severity() == Severity.ERROR) {
                throw new IllegalArgumentException("invalid V2 config at mining time: " + issue);
            }
        }
        warnings.clear();
        warnings.addAll(issues);

        // DO-ceiling short-circuit (D19): ∂×N_eff > Z + ε ⇒ DOP set provably empty.
        if (minSup > z + config.epsilon()) {
            windowInfo = new WindowInfo(windowSize, WindowMath.effectiveTransactions(tl, windowSize),
                    minSup, z, evictions, 0, 0);
            if (windowListener != null) {
                windowListener.onWindow(windowInfo);
            }
            return new MineResult(List.of(), totalTransactions, tl, minSup);
        }

        long r0 = phaseListener != null ? System.nanoTime() : 0L;
        List<DHONode> sorted = Reconstructor.run(global, config.decayFactor(), tl, pool);
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.RECONSTRUCTION, r0, System.nanoTime());
        }

        long live = 0;
        long dead = 0;
        for (DHONode node : global.nodes()) { // post-discard snapshot
            live += node.support();
            dead += node.head();
        }
        liveEntries = live;
        deadEntries = dead;
        windowInfo = new WindowInfo(windowSize, WindowMath.effectiveTransactions(tl, windowSize),
                minSup, z, evictions, live, dead);
        if (windowListener != null) {
            windowListener.onWindow(windowInfo);
        }

        long m0 = phaseListener != null || miningProgressListener != null ? System.nanoTime() : 0L;
        Map<String, Pattern> results = new LinkedHashMap<>();
        List<Callable<Map<String, Pattern>>> tasks = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).support() < minSup) {
                continue; // no root task
            }
            final int index = i;
            tasks.add(() -> Miner.mineRoot(sorted, index, config.decayFactor(), config.epsilon(),
                    minSup, tl, windowTruncated));
        }
        lastMiningTasks = tasks.size();
        List<Map<String, Pattern>> partials;
        if (miningProgressListener == null) {
            partials = pool.invokeAll(tasks);
        } else {
            int[] merged = new int[1];
            partials = pool.invokeAll(tasks, (idx, partial) -> {
                if (partial != null) {
                    merged[0] += partial.size();
                }
                miningProgressListener.onProgress(new MiningProgress(
                        idx, tasks.size(), merged[0], (System.nanoTime() - m0) / 1_000_000L));
            });
        }
        for (Map<String, Pattern> partial : partials) {
            if (partial != null) {
                results.putAll(partial);
            }
        }
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.MINING, m0, System.nanoTime());
        }

        List<Pattern> patterns = new ArrayList<>(results.values());
        patterns.sort(Comparator.comparing(Pattern::canonicalKey));
        return new MineResult(patterns, totalTransactions, tl, minSup);
    }

    @Override
    public void close() {
        pool.shutdown();
    }

    private void requireIncreasing(Transaction t) {
        if (t.tid() <= tl) {
            throw new IllegalArgumentException(
                    "stream TIDs must be strictly increasing (INV-A); got " + t.tid() + " after " + tl);
        }
    }
}