package dhopm.v3.mining;

import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * GĐ3: DFS pattern-growth over the sorted global list (canonical 2.2). Per node, in list order:
 * <ol>
 *   <li>support &lt; minSup → skip entirely (C2);
 *   <li>{@code DO ≥ minSup − ε} → emit {@code prefix ∪ {item_i}} (C4);
 *   <li>{@code min(DUBO, Z) < minSup − ε} → prune (no expansion);
 *   <li>else build the conditional list by intersecting with every following support-meeting node.
 * </ol>
 *
 * <p>Level 2: when a conditional list reaches {@value #FORK_THRESHOLD} nodes the recursion forks
 * independent subtree tasks through the engine's ForkJoinPool (work-stealing); results are merged
 * after join in index order, so the output is deterministic for any pool size (INV-E).
 */
public final class MinerOptimized {

    private static final int FORK_THRESHOLD = 16;

    private final DecayLookup decayLookup;
    private final int tl;
    private final double minSup;
    private final double epsilon;
    private final ForkJoinPool pool;
    private final boolean canFork;
    private int lastRootTasks;

    public MinerOptimized(DecayLookup decayLookup, int tl, double minSup, double epsilon, ForkJoinPool pool) {
        this.decayLookup = decayLookup;
        this.tl = tl;
        this.minSup = minSup;
        this.epsilon = epsilon;
        this.pool = pool;
        this.canFork = pool.getParallelism() > 1;
    }

    /** Mines the subtrees rooted at every support-meeting node of the sorted global list. */
    public List<PatternResult> mine(List<DHONodeOptimized> sortedNodes, MiningProgressListener progressListener) {
        List<SubtreeTask> roots = new ArrayList<>();
        for (int i = 0; i < sortedNodes.size(); i++) {
            if (sortedNodes.get(i).support() >= minSup) {
                roots.add(new SubtreeTask(sortedNodes, i, new int[0]));
            }
        }
        lastRootTasks = roots.size();
        if (roots.isEmpty()) {
            return List.of();
        }

        for (SubtreeTask task : roots) {
            pool.submit(task);
        }

        List<PatternResult> all = new ArrayList<>();
        int completed = 0;
        long m0 = progressListener != null ? System.nanoTime() : 0L;
        for (SubtreeTask task : roots) {
            all.addAll(task.join());
            completed++;
            if (progressListener != null) {
                progressListener.onProgress(new MiningProgress(
                        completed, roots.size(), all.size(), (System.nanoTime() - m0) / 1_000_000L));
            }
        }
        return all;
    }

    public int getMiningTasks() {
        return lastRootTasks;
    }

    /** Subtree rooted at {@code list.get(start)}; {@code prefix} = item IDs of the current prefix. */
    private final class SubtreeTask extends RecursiveTask<List<PatternResult>> {
        private final List<DHONodeOptimized> list;
        private final int start;
        private final int[] prefix;

        SubtreeTask(List<DHONodeOptimized> list, int start, int[] prefix) {
            this.list = list;
            this.start = start;
            this.prefix = prefix;
        }

        @Override
        protected List<PatternResult> compute() {
            DHONodeOptimized node = list.get(start);
            if (node.support() < minSup) {
                return List.of(); // C2
            }
            List<PatternResult> results = new ArrayList<>();
            int[] candidate = append(prefix, node.itemId);
            if (node.doValue() >= minSup - epsilon) { // C4
                results.add(new PatternResult(candidate, node.doValue()));
            }
            if (DuboBound.compute(node, decayLookup, tl).belowMinSup(minSup, epsilon)) {
                return results; // prune: no expansion
            }
            List<DHONodeOptimized> cond = buildConditional(node, start + 1, candidate.length + 1);
            if (cond.isEmpty()) {
                return results;
            }
            if (canFork && cond.size() >= FORK_THRESHOLD) {
                List<SubtreeTask> subs = new ArrayList<>(cond.size());
                for (int k = 0; k < cond.size(); k++) {
                    subs.add(new SubtreeTask(cond, k, candidate));
                }
                for (SubtreeTask sub : subs) {
                    sub.fork();
                }
                for (SubtreeTask sub : subs) {
                    results.addAll(sub.join());
                }
            } else {
                for (int k = 0; k < cond.size(); k++) {
                    results.addAll(new SubtreeTask(cond, k, candidate).compute());
                }
            }
            return results;
        }

        private List<DHONodeOptimized> buildConditional(DHONodeOptimized node, int fromIndex, int nextPatternLen) {
            List<DHONodeOptimized> cond = new ArrayList<>();
            for (int j = fromIndex; j < list.size(); j++) {
                DHONodeOptimized partner = list.get(j);
                if (partner.support() < minSup) {
                    continue;
                }
                DHONodeOptimized combined = ConditionalListBuilderOptimized.intersect(
                        node, partner, nextPatternLen, decayLookup, tl);
                if (combined.size() > 0) {
                    cond.add(combined);
                }
            }
            return cond;
        }
    }

    private static int[] append(int[] prefix, int itemId) {
        int[] out = Arrays.copyOf(prefix, prefix.length + 1);
        out[prefix.length] = itemId;
        return out;
    }

    /** A mined pattern: item IDs in DFS order + its DO. */
    public record PatternResult(int[] itemIds, double doValue) {

        public String canonicalKey() {
            int[] sorted = itemIds.clone();
            Arrays.sort(sorted);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorted.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(sorted[i]);
            }
            return sb.toString();
        }
    }
}