package dhopm.v1.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseAwareEngine;
import dhopm.common.contract.PhaseListener;
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
import java.util.concurrent.Callable;

/**
 * V1 engine implementing the canonical pipeline (OVERALL-PLAN 2.2):
 * construction via {@link DHOListBuilder}, reconstruction via {@link Reconstructor},
 * mining via {@link Miner}. Threading Level 1, deterministic (INV-E), fail-fast (INV-A).
 */
public final class MiningEngine implements PhaseAwareEngine, AutoCloseable {

    private final MiningConfig config;
    private final WorkerPool pool;
    private final DHOList global = new DHOList();
    private long totalTransactions;
    private int tl;
    private PhaseListener phaseListener;

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
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.RECONSTRUCTION, r0, System.nanoTime());
        }

        long m0 = phaseListener != null ? System.nanoTime() : 0L;
        Map<String, Pattern> results = new LinkedHashMap<>();
        List<Callable<Map<String, Pattern>>> tasks = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).support() < minSup) {
                continue; // no root task
            }
            final int index = i;
            tasks.add(() -> Miner.mineRoot(sorted, index, config.decayFactor(), config.epsilon(), minSup, tl));
        }
        for (Map<String, Pattern> partial : pool.invokeAll(tasks)) {
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
}