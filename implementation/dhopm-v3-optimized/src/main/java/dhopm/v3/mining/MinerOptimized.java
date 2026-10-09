package dhopm.v3.mining;

import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseListener;
import dhopm.common.util.TimingRecorder;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.metrics.ZCalculator;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Optimized Miner (GĐ3) with Level 2 threading.
 * - ForkJoinPool for work stealing
 * - One task per root node
 * - Subtree splitting: if conditional list size > threshold, split recursively
 * - Short-circuit: minSup > Z + epsilonCmp ⇒ ∅
 * - Deterministic: results merged in canonical order (C6)
 * - Uses shared thread-safe list for pattern collection
 */
public final class MinerOptimized {

    private final WindowBufferOptimized window;
    private final DecayLookup decayLookup;
    private final int tl;
    private final double f;
    private final double minSup;
    private final double epsilonCmp;
    private final ForkJoinPool pool;
    private final PhaseListener phaseListener;
    private final MiningProgressListener progressListener;

    // Results - thread-safe for parallel collection
    private final List<PatternResult> patterns = new CopyOnWriteArrayList<>();
    private boolean windowTruncated = false;
    private int miningTasks = 0;

    // Subtree splitting threshold
    private static final int MIN_SUBTREE_SIZE = 8;

    public MinerOptimized(WindowBufferOptimized window, DecayLookup decayLookup, int tl, double f,
                          double minSup, double epsilonCmp, ForkJoinPool pool,
                          PhaseListener phaseListener, MiningProgressListener progressListener) {
        this.window = window;
        this.decayLookup = decayLookup;
        this.tl = tl;
        this.f = f;
        this.minSup = minSup;
        this.epsilonCmp = epsilonCmp;
        this.pool = pool;
        this.phaseListener = phaseListener;
        this.progressListener = progressListener;
    }

    /**
     * Runs mining on the given sorted nodes.
     * @return list of patterns found (itemIds + DO)
     */
    public List<PatternResult> mine(List<DHONodeOptimized> sortedNodes, List<DuboBound> duboBounds) {
        TimingRecorder.Record rec = phaseListener != null ? new TimingRecorder.Record(Phase.MINING) : null;

        // Short-circuit: minSup > Z(f, TL) + epsilonCmp ⇒ ∅
        double z = ZCalculator.compute(f, tl);
        if (minSup > z + epsilonCmp) {
            if (rec != null) {
                rec.end();
                phaseListener.onPhase(Phase.MINING, rec.startNanos, rec.endNanos);
            }
            windowTruncated = true;
            return List.of();
        }

        // Check if window is active
        windowTruncated = (minSup != minSup * tl / Math.min(tl, window.windowSize));

        // Prepare root tasks
        List<MiningTask> tasks = new ArrayList<>();
        for (int i = 0; i < sortedNodes.size(); i++) {
            DHONodeOptimized node = sortedNodes.get(i);
            if (node.support(window) >= minSup) {
                DuboBound bound = duboBounds.get(i);
                tasks.add(new MiningTask(node, bound, new int[]{node.itemId}, node.doValue, sortedNodes, i + 1));
            }
        }
        miningTasks = tasks.size();

        // Execute root tasks
        if (progressListener != null && !tasks.isEmpty()) {
            List<java.util.concurrent.ForkJoinTask<Void>> fjTasks = new ArrayList<>();
            for (MiningTask task : tasks) {
                fjTasks.add(pool.submit(task));
            }
            int completed = 0;
            for (java.util.concurrent.ForkJoinTask<Void> fjTask : fjTasks) {
                fjTask.join();
                completed++;
                progressListener.onProgress(new MiningProgress(completed, tasks.size(), patterns.size(), 0));
            }
        } else {
            pool.invokeAll(tasks);
        }

        // Sort results by canonical key (C6)
        patterns.sort(Comparator.comparing(PatternResult::canonicalKey));

        if (rec != null) {
            rec.end();
            phaseListener.onPhase(Phase.MINING, rec.startNanos, rec.endNanos);
        }
        return new ArrayList<>(patterns);
    }

    public int getMiningTasks() {
        return miningTasks;
    }

    public boolean isWindowTruncated() {
        return windowTruncated;
    }

    /** Task for mining a single root node (and its conditional subtrees recursively). */
    private final class MiningTask extends RecursiveAction {
        private final DHONodeOptimized node;
        private final DuboBound bound;
        private final int[] prefixItemIds;
        private final double prefixDO;
        private final List<DHONodeOptimized> allNodes;
        private final int startIndex;

        MiningTask(DHONodeOptimized node, DuboBound bound, int[] prefixItemIds, double prefixDO,
                   List<DHONodeOptimized> allNodes, int startIndex) {
            this.node = node;
            this.bound = bound;
            this.prefixItemIds = prefixItemIds;
            this.prefixDO = prefixDO;
            this.allNodes = allNodes;
            this.startIndex = startIndex;
        }

        @Override
        protected void compute() {
            // C4: emit prefix as pattern if it meets minSup
            if (prefixDO >= minSup - epsilonCmp) {
                patterns.add(new PatternResult(prefixItemIds, prefixDO));
            }

            // Prune: if effective bound < minSup, stop
            if (bound.belowMinSup(minSup, epsilonCmp)) {
                return;
            }

            // Build conditional list for partners after this node
            ConditionalListBuilderOptimized builder = new ConditionalListBuilderOptimized(window, tl, f, decayLookup);

            List<DHONodeOptimized> partners = new ArrayList<>();
            for (int i = startIndex; i < allNodes.size(); i++) {
                DHONodeOptimized partner = allNodes.get(i);
                if (partner.support(window) >= minSup) {
                    partners.add(partner);
                }
            }

            List<ConditionalListBuilderOptimized.ConditionalNode> condNodes = builder.build(node, partners, minSup, epsilonCmp);

            if (condNodes.isEmpty()) {
                return;
            }

            // If many conditional nodes, split into subtasks
            if (condNodes.size() > MIN_SUBTREE_SIZE) {
                List<MiningTask> subtasks = new ArrayList<>();
                for (ConditionalListBuilderOptimized.ConditionalNode cn : condNodes) {
                    int[] newPrefix = new int[prefixItemIds.length + 1];
                    System.arraycopy(prefixItemIds, 0, newPrefix, 0, prefixItemIds.length);
                    newPrefix[prefixItemIds.length] = cn.partnerItemId;
                    DuboBound condBound = new DuboBound(cn.node.doValue, ZCalculator.compute(f, tl));
                    subtasks.add(new MiningTask(cn.node, condBound, newPrefix, cn.doValue, allNodes, allNodes.size()));
                }
                ForkJoinTask.invokeAll(subtasks);
            } else {
                // Sequential for small subtrees
                for (ConditionalListBuilderOptimized.ConditionalNode cn : condNodes) {
                    int[] newPrefix = new int[prefixItemIds.length + 1];
                    System.arraycopy(prefixItemIds, 0, newPrefix, 0, prefixItemIds.length);
                    newPrefix[prefixItemIds.length] = cn.partnerItemId;
                    DuboBound condBound = new DuboBound(cn.node.doValue, ZCalculator.compute(f, tl));
                    new MiningTask(cn.node, condBound, newPrefix, cn.doValue, allNodes, allNodes.size()).compute();
                }
            }
        }
    }

    /** Pattern result with item IDs and DO. */
    public static final class PatternResult {
        public final int[] itemIds;
        public final double doValue;

        public PatternResult(int[] itemIds, double doValue) {
            this.itemIds = itemIds;
            this.doValue = doValue;
        }

        public String canonicalKey() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < itemIds.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(itemIds[i]);
            }
            return sb.toString();
        }
    }
}