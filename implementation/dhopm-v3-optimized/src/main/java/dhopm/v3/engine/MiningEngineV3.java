package dhopm.v3.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Engine;
import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseAwareEngine;
import dhopm.common.contract.PhaseListener;
import dhopm.common.contract.ProgressAwareEngine;
import dhopm.common.contract.WindowAwareEngine;
import dhopm.common.contract.WindowListener;
import dhopm.common.util.TimingRecorder;
import dhopm.common.util.WorkerPool;
import dhopm.v3.construction.DHOListBuilderOptimized;
import dhopm.v3.dho.DHOListOptimized;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.io.ItemDictionary;
import dhopm.v3.mining.MinerOptimized;
import dhopm.v3.mining.DuboBound;
import dhopm.v3.reconstruction.ReconstructorOptimized;
import dhopm.v3.metrics.ZCalculator;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;
import dhopm.common.transaction.Transaction;
import dhopm.common.window.WindowInfo;
import dhopm.common.window.WindowMath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * V3 Optimized Mining Engine.
 * Implements PhaseAwareEngine + ProgressAwareEngine + WindowAwareEngine + AutoCloseable.
 * Uses ForkJoinPool (Level 2 threading), optimized data structures (SoA, int itemId, no Handle).
 */
public final class MiningEngineV3 implements PhaseAwareEngine, ProgressAwareEngine, WindowAwareEngine, AutoCloseable {

    private final MiningConfig config;
    private final ForkJoinPool forkJoinPool;
    private final WorkerPool workerPool;

    private final ItemDictionary dictionary = new ItemDictionary();
    private final WindowBufferOptimized window;
    private final DecayLookup decayLookup;
    private final DHOListBuilderOptimized builder;
    private final TimingRecorder timingRecorder = new TimingRecorder();

    private PhaseListener phaseListener;
    private MiningProgressListener progressListener;
    private WindowListener windowListener;

    private boolean closed = false;
    private int totalLoaded = 0;
    private int lastTid = 0;
    private int globalNodeCount = 0;
    private int globalEntryCount = 0;
    private int lastMiningTasks = 0;
    private boolean windowTruncated = false;
    private List<String> warnings = new ArrayList<>();

    public MiningEngineV3(MiningConfig config) {
        this.config = config;
        this.forkJoinPool = new ForkJoinPool(config.workers());
        this.workerPool = new WorkerPool(config.workers());

        int windowSize = (config.minOcc() == 0.0 || config.f() == 1.0)
                ? WindowMath.INFINITE
                : (int) WindowMath.computeWindow(config.f(), config.minOcc());
        this.window = new WindowBufferOptimized(windowSize);
        this.decayLookup = DecayLookup.create(config.f(), windowSize);
        this.builder = new DHOListBuilderOptimized(config, window, dictionary);
    }

    /** Default config with minOcc = 1e-6 (D41). */
    public static MiningEngineV3 defaults(double partial, double f) {
        MiningConfig config = new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON_CMP,
                MiningConfig.DEFAULT_EPSILON_WINDOW, MiningConfig.DEFAULT_WORKERS);
        return new MiningEngineV3(config);
    }

    @Override
    public void setPhaseListener(PhaseListener listener) {
        this.phaseListener = listener;
    }

    @Override
    public void setMiningProgressListener(MiningProgressListener listener) {
        this.progressListener = listener;
    }

    @Override
    public void setWindowListener(WindowListener listener) {
        this.windowListener = listener;
    }

    @Override
    public void loadBatch(List<Transaction> transactions) {
        if (closed) throw new IllegalStateException("Engine closed");
        TimingRecorder.Record rec = phaseListener != null ? new TimingRecorder.Record(Phase.CONSTRUCTION) : null;

        for (Transaction tx : transactions) {
            int tid = tx.tid();
            lastTid = tid;
            totalLoaded++;

            // Evict before write (GĐ0)
            window.evictBeforeWrite(tid);

            // Skip if outside window (GĐ0 skip-write)
            if (window.windowSize != WindowMath.INFINITE) {
                if (tid <= lastTid - window.windowSize) {
                    continue;
                }
            }

            // Write to window
            int slot = window.write(tid, tx.items().length);

            // Map items to IDs and add entries
            for (String itemName : tx.items()) {
                int itemId = dictionary.getOrAssignId(itemName);
                DHONodeOptimized node = builder.getDhoList().getOrCreateNode(itemId);
                node.addEntry(slot, tx.items().length);
            }
        }

        if (rec != null) {
            rec.end();
            phaseListener.onPhase(Phase.CONSTRUCTION, rec.startNanos, rec.endNanos);
        }
    }

    @Override
    public MineResult mineNow() {
        if (closed) throw new IllegalStateException("Engine closed");

        DHOListOptimized dhoList = builder.getDhoList();
        int tl = totalLoaded;
        int w = window.windowSize;
        int nEff = (w == WindowMath.INFINITE) ? tl : Math.min(tl, w);
        double minSup = config.partial() * nEff;

        // Reconstruction
        ReconstructorOptimized reconstructor = new ReconstructorOptimized(
                dhoList, window, decayLookup, tl, config.f(), minSup, phaseListener);
        List<DHONodeOptimized> sortedNodes = reconstructor.reconstruct(forkJoinPool);

        // Mining
        MinerOptimized miner = new MinerOptimized(
                window, decayLookup, tl, config.f(), minSup, config.epsilonCmp(),
                forkJoinPool, phaseListener, progressListener);
        List<MinerOptimized.PatternResult> patternResults = miner.mine(sortedNodes, reconstructor.getDuboBounds());

        // Convert to common MineResult
        List<Pattern> patterns = new ArrayList<>();
        for (MinerOptimized.PatternResult pr : patternResults) {
            String[] itemNames = new String[pr.itemIds.length];
            for (int i = 0; i < pr.itemIds.length; i++) {
                itemNames[i] = dictionary.getName(pr.itemIds[i]);
            }
            // tids not tracked in V3 miner yet - empty array
            patterns.add(new Pattern(itemNames, pr.doValue, new int[0]));
        }

        // Stats
        globalNodeCount = dhoList.nodeCount();
        globalEntryCount = 0;
        for (DHONodeOptimized node : dhoList.nodesInCreationOrder()) {
            globalEntryCount += node.size;
        }
        lastMiningTasks = miner.getMiningTasks();
        windowTruncated = miner.isWindowTruncated();

        // Build warnings
        warnings.clear();
        if (windowTruncated) {
            warnings.add("Window active: minOcc=" + config.minOcc() + " ⇒ minSup frozen at ∂×W");
        }
        if (config.minOcc() == 0.0) {
            warnings.add("Window inactive (minOcc=0): paper mode, results ≡ V1");
        }

        MineResult result = new MineResult(patterns, tl, lastTid, minSup);

        if (windowListener != null) {
            windowListener.onWindowUpdate(getWindowInfo());
        }

        return result;
    }

    @Override
    public WindowInfo getWindowInfo() {
        int w = window.windowSize;
        int nEff = (w == WindowMath.INFINITE) ? totalLoaded : Math.min(totalLoaded, w);
        double minSup = config.partial() * nEff;
        double z = ZCalculator.compute(config.f(), totalLoaded);
        return new WindowInfo(
                w == WindowMath.INFINITE ? WindowMath.INFINITE : w,
                nEff,
                minSup,
                z,
                0, // evictions - not tracked separately yet
                window.liveCount(),
                0  // dead entries
        );
    }

    @Override
    public void close() {
        if (!closed) {
            forkJoinPool.shutdown();
            workerPool.shutdown();
            closed = true;
        }
    }

    // Getters for stats
    public int globalNodeCount() { return globalNodeCount; }
    public int globalEntryCount() { return globalEntryCount; }
    public int totalLoaded() { return totalLoaded; }
    public int lastTid() { return lastTid; }
    public int lastMiningTasks() { return lastMiningTasks; }
    public MiningConfig config() { return config; }
    public ItemDictionary dictionary() { return dictionary; }
    public WindowBufferOptimized window() { return window; }
    public DHOListOptimized dhoList() { return builder.getDhoList(); }
}