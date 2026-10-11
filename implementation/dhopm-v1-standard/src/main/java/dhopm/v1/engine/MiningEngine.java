package dhopm.v1.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.MiningEvent;
import dhopm.common.contract.MiningEventListener;
import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseAwareEngine;
import dhopm.common.contract.PhaseListener;
import dhopm.common.contract.ProgressAwareEngine;
import dhopm.common.contract.StreamAwareEngine;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.WorkerPool;
import dhopm.v1.construction.DHOListBuilder;
import dhopm.v1.mining.Miner;
import dhopm.v1.model.DHOList;
import dhopm.v1.model.DHONode;
import dhopm.v1.reconstruction.Reconstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1 engine implementing the canonical pipeline (OVERALL-PLAN 2.2):
 * construction via {@link DHOListBuilder}, reconstruction via {@link Reconstructor},
 * mining via {@link Miner}. Threading Level 1, deterministic (INV-E), fail-fast (INV-A).
 *
 * <p>Tool-facing API (plan 00 §4.2): besides {@link Engine}, this engine exposes
 * {@link PhaseAwareEngine} (per-stage timing) and {@link ProgressAwareEngine} (coarse
 * mining progress). Read-only stats getters stay public; internal structures do not leak.
 */
public final class MiningEngine implements PhaseAwareEngine, ProgressAwareEngine, StreamAwareEngine, AutoCloseable {

    private final MiningConfig config;
    private final WorkerPool pool;
    private final DHOList global = new DHOList();
    private long totalTransactions;
    private int tl;
    private long globalEntries;
    private int lastMiningTasks;
    private List<DHONode> lastDho = List.of();
    private PhaseListener phaseListener;
    private MiningProgressListener miningProgressListener;
    private MiningEventListener eventListener;
    private int eventDetail = -1;

    public MiningEngine(MiningConfig config) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.pool = new WorkerPool(config.workers());
    }

    public static MiningEngine defaults(double partial, double f) {
        return new MiningEngine(MiningConfig.of(partial, f));
    }

    @Override
    public String name() {
        return "v1-standard";
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
    public void setMiningEventListener(MiningEventListener listener, int detail) {
        this.eventListener = listener;
        this.eventDetail = detail;
    }

    /** Read-only stats for tooling (no internal leakage). */

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

    /**
     * Snapshot of the global DHO-List AFTER the last reconstruction (nodes sorted by support
     * ascending, ties in creation order). Read-only — used by the {@code detail} CLI command.
     */
    public List<DHONode> dhoListAfterReconstruction() {
        return lastDho;
    }

    @Override
    public void loadBatch(List<Transaction> batch) {
        long start = phaseListener != null ? System.nanoTime() : 0L;
        for (Transaction t : batch) {
            if (t.tid() <= tl) {
                throw new IllegalArgumentException(
                        "stream TIDs must be strictly increasing (INV-A); got " + t.tid() + " after " + tl);
            }
            tl = t.tid();
            totalTransactions++;
            DHOListBuilder.add(t, global);
            globalEntries += t.length();
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
        double minSup = config.partial() * totalTransactions;

        long r0 = phaseListener != null ? System.nanoTime() : 0L;
        List<DHONode> sorted = Reconstructor.run(global, config.decayFactor(), tl, pool);
        lastDho = sorted;
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.RECONSTRUCTION, r0, System.nanoTime());
        }

        long m0 = phaseListener != null || miningProgressListener != null ? System.nanoTime() : 0L;
        Map<String, Pattern> results = new LinkedHashMap<>();
        // Real-time PATTERN events are deduplicated across root tasks (a pattern can be reached
        // through several prefixes) so the stream shows each accepted pattern exactly once.
        final MiningEventListener sink = eventListener == null ? null : dedupPatterns(eventListener);
        List<Callable<Map<String, Pattern>>> tasks = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).support() < minSup) {
                continue; // no root task
            }
            final int index = i;
            tasks.add(() -> Miner.mineRoot(sorted, index, config.decayFactor(), config.epsilon(), minSup, tl,
                    sink, eventDetail));
        }
        lastMiningTasks = tasks.size();
        List<Map<String, Pattern>> partials;
        if (miningProgressListener == null) {
            partials = pool.invokeAll(tasks);
        } else {
            Set<String> seen = ConcurrentHashMap.newKeySet();
            partials = pool.invokeAll(tasks, (idx, partial) -> {
                if (partial != null) {
                    seen.addAll(partial.keySet());
                }
                miningProgressListener.onProgress(new MiningProgress(
                        idx, tasks.size(), seen.size(), (System.nanoTime() - m0) / 1_000_000L));
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

    /**
     * Wraps the listener so a pattern is reported once per canonical key: real-time PATTERN events
     * and accepted DOP checks (contract {@code --detail 2}) are deduplicated across roots/prefixes.
     * Rejected checks and prune (UBDO) events still repeat, since they describe each actual step.
     */
    private static MiningEventListener dedupPatterns(MiningEventListener delegate) {
        Set<String> patterns = ConcurrentHashMap.newKeySet();
        Set<String> acceptedDop = ConcurrentHashMap.newKeySet();
        return event -> {
            switch (event.kind()) {
                case PATTERN -> {
                    if (!patterns.add(event.key())) {
                        return;
                    }
                }
                case DOP_CHECK -> {
                    if (event.passed() && !acceptedDop.add(event.key())) {
                        return;
                    }
                }
                default -> {
                }
            }
            delegate.onEvent(event);
        };
    }
}