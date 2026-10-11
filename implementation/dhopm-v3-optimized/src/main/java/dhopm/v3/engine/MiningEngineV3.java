package dhopm.v3.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseAwareEngine;
import dhopm.common.contract.PhaseListener;
import dhopm.common.contract.ProgressAwareEngine;
import dhopm.common.contract.WindowAwareEngine;
import dhopm.common.contract.WindowListener;
import dhopm.common.transaction.Transaction;
import dhopm.common.window.ParameterValidator;
import dhopm.common.window.WindowInfo;
import dhopm.common.window.WindowMath;
import dhopm.common.window.ParameterValidator.Issue;
import dhopm.common.window.ParameterValidator.Severity;
import dhopm.v3.construction.DHOListBuilderOptimized;
import dhopm.v3.dho.DHOListOptimized;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.io.ItemDictionary;
import dhopm.v3.mining.MinerOptimized;
import dhopm.v3.reconstruction.ReconstructorOptimized;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * V3 engine: same damped-window pipeline as V2 (GĐ0 eviction, two-phase minSup, DO-ceiling
 * short-circuit, tighter {@code min(DUBO, Z)} prune) but with SoA node storage
 * ({@code int[] tids / int[] lens} instead of Entry/Handle objects), {@code int} item IDs +
 * {@link ItemDictionary}, a {@code Math.pow}-backed {@link DecayLookup} table and ForkJoinPool
 * threading at reconstruction + mining (Level 2).
 *
 * <p>Number parity: DO / DUBO / Z are built with the exact V1/V2 formulas so the resulting DOP
 * set is bit-for-bit identical — use {@code dhopm.common.testkit.GoldenAssert#samePatternSet} to
 * compare. Paper mode ({@code minOcc=0} or {@code f=1}) equals V1 (INV-I).
 */
public final class MiningEngineV3 implements PhaseAwareEngine, ProgressAwareEngine, WindowAwareEngine, AutoCloseable {

    private final MiningConfig config;
    private final ForkJoinPool pool;
    private final long windowSize;
    private final boolean windowTruncated;
    private final WindowBufferOptimized window;
    private final DecayLookup decayLookup;
    private final ItemDictionary dictionary = new ItemDictionary();
    private final DHOListBuilderOptimized builder;
    private final List<Issue> warnings = new ArrayList<>();

    private long totalTransactions;
    private int tl;
    private long globalEntries;
    private int lastMiningTasks;
    private long evictions;
    private long liveEntries;
    private long deadEntries;
    private boolean closed;
    private WindowInfo windowInfo = WindowInfo.none();

    private PhaseListener phaseListener;
    private MiningProgressListener miningProgressListener;
    private WindowListener windowListener;

    public MiningEngineV3(MiningConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        for (Issue issue : ParameterValidator.validate(config)) {
            if (issue.severity() == Severity.ERROR) {
                throw new IllegalArgumentException("invalid V3 config: " + issue);
            }
            warnings.add(issue);
        }
        this.windowSize = WindowMath.computeWindow(config.decayFactor(), config.minOcc());
        this.windowTruncated = windowSize != WindowMath.INFINITE;
        this.window = new WindowBufferOptimized(windowSize);
        this.decayLookup = DecayLookup.create(config.decayFactor(), windowSize);
        this.builder = new DHOListBuilderOptimized(dictionary);
        this.pool = new ForkJoinPool(config.workers());
    }

    /** Library default: {@code minOcc = 1e-6} (D41) — a real (truncating) window. */
    public static MiningEngineV3 defaults(double partial, double f) {
        return new MiningEngineV3(MiningConfig.of(partial, f, MiningConfig.DEFAULT_MIN_OCC));
    }

    @Override
    public String name() {
        return "v3-optimized";
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

    public MiningConfig config() {
        return config;
    }

    /** Validation warnings (stable codes). */
    public List<Issue> warnings() {
        return List.copyOf(warnings);
    }

    public long windowSize() {
        return windowSize;
    }

    public int globalNodeCount() {
        return builder.getDhoList().nodeCount();
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

    public ItemDictionary dictionary() {
        return dictionary;
    }

    public DHOListOptimized dhoList() {
        return builder.getDhoList();
    }

    @Override
    public void loadBatch(List<Transaction> batch) {
        if (closed) {
            throw new IllegalStateException("Engine closed");
        }
        long start = phaseListener != null ? System.nanoTime() : 0L;
        if (!batch.isEmpty()) {
            if (windowTruncated) {
                int batchMax = batch.get(batch.size() - 1).tid();
                for (Transaction t : batch) {
                    requireIncreasing(t);
                    tl = t.tid();
                    totalTransactions++;
                    // GĐ0: evict the transaction leaving the window (clear-before-write).
                    long victim = (long) tl - windowSize;
                    if (victim >= 1L && window.evictTid((int) victim)) {
                        evictions++;
                    }
                    // Skip writing anything that will be outside the FINAL window of this batch
                    // (bulk-load optimization): only the last W matter for the next mineNow().
                    if ((long) batchMax - t.tid() < windowSize) {
                        window.write(t.tid());
                        builder.add(t);
                        globalEntries += t.length();
                    }
                }
            } else {
                for (Transaction t : batch) {
                    requireIncreasing(t);
                    tl = t.tid();
                    totalTransactions++;
                    builder.add(t);
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
        if (closed) {
            throw new IllegalStateException("Engine closed");
        }
        if (totalTransactions == 0) {
            return new MineResult(List.of(), 0, 0, 0.0);
        }

        double minSup = WindowMath.minSup(config.partial(), tl, windowSize); // two-phase (INV-F)
        double z = WindowMath.maxDO(config.decayFactor(), tl);
        long nEff = WindowMath.effectiveTransactions(tl, windowSize);

        // Re-validate with the known stream length (may add INFEASIBLE_PARTIAL / PARTIAL_ZERO).
        List<Issue> issues = ParameterValidator.validate(config, tl);
        for (Issue issue : issues) {
            if (issue.severity() == Severity.ERROR) {
                throw new IllegalArgumentException("invalid V3 config at mining time: " + issue);
            }
        }
        warnings.clear();
        warnings.addAll(issues);

        lastMiningTasks = 0;

        // DO-ceiling short-circuit (D19): ∂×N_eff > Z + ε ⇒ DOP set provably empty.
        if (minSup > z + config.epsilon()) {
            windowInfo = new WindowInfo(windowSize, nEff, minSup, z, evictions, 0, 0);
            emitWindow();
            return new MineResult(List.of(), totalTransactions, tl, minSup);
        }

        long r0 = phaseListener != null ? System.nanoTime() : 0L;
        List<DHONodeOptimized> sorted = ReconstructorOptimized.run(
                builder.getDhoList(), window, decayLookup, tl, pool);
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.RECONSTRUCTION, r0, System.nanoTime());
        }

        long live = 0;
        long dead = 0;
        for (DHONodeOptimized node : builder.getDhoList().nodesList()) { // post-discard snapshot
            live += node.support();
            dead += node.head();
        }
        liveEntries = live;
        deadEntries = dead;
        windowInfo = new WindowInfo(windowSize, nEff, minSup, z, evictions, live, dead);
        emitWindow();

        long m0 = phaseListener != null || miningProgressListener != null ? System.nanoTime() : 0L;
        MinerOptimized miner = new MinerOptimized(decayLookup, tl, minSup, config.epsilon(), pool);
        List<MinerOptimized.PatternResult> results = miner.mine(sorted, miningProgressListener);
        lastMiningTasks = miner.getMiningTasks();
        if (phaseListener != null) {
            phaseListener.onPhase(Phase.MINING, m0, System.nanoTime());
        }

        Map<String, Pattern> byKey = new LinkedHashMap<>();
        for (MinerOptimized.PatternResult pr : results) {
            Pattern pattern = toPattern(pr);
            byKey.putIfAbsent(pattern.canonicalKey(), pattern);
        }
        List<Pattern> patterns = new ArrayList<>(byKey.values());
        patterns.sort(Comparator.comparing(Pattern::canonicalKey));
        return new MineResult(patterns, totalTransactions, tl, minSup);
    }

    @Override
    public void close() {
        if (!closed) {
            pool.shutdown();
            closed = true;
        }
    }

    private Pattern toPattern(MinerOptimized.PatternResult pr) {
        String[] names = new String[pr.itemIds().length];
        for (int i = 0; i < names.length; i++) {
            names[i] = dictionary.getName(pr.itemIds()[i]);
        }
        return new Pattern(names, pr.doValue(), new int[0]);
    }

    private void emitWindow() {
        if (windowListener != null) {
            windowListener.onWindow(windowInfo);
        }
    }

    private void requireIncreasing(Transaction t) {
        if (t.tid() <= tl) {
            throw new IllegalArgumentException(
                    "stream TIDs must be strictly increasing (INV-A); got " + t.tid() + " after " + tl);
        }
    }
}