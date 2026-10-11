package dhopm.v3.reconstruction;

import dhopm.v3.dho.DHOListOptimized;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.metrics.MetricCalculator;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * GĐ2: discard the dead prefix of every global node (single-threaded, O(k) per node), recompute
 * DO from 0 over the LIVE region {@code [head, size)} (C5) and stable-sort by support ASCENDING
 * (ties keep creation order — C3).
 *
 * <p>One task per node; each node is accumulated on a single thread, so DO is bit-identical for
 * any pool size (INV-E). In paper mode every entry is live (head = 0) and the pass equals V1.
 */
public final class ReconstructorOptimized {

    private ReconstructorOptimized() {
    }

    public static List<DHONodeOptimized> run(DHOListOptimized global, WindowBufferOptimized window,
                                             DecayLookup decayLookup, int tl, ForkJoinPool pool) {
        List<DHONodeOptimized> nodes = global.nodesList();
        for (DHONodeOptimized node : nodes) {
            node.discardDeadPrefix(window);
        }

        List<DoTask> tasks = new ArrayList<>(nodes.size());
        for (DHONodeOptimized node : nodes) {
            if (node.size() > node.head()) {
                tasks.add(new DoTask(node, decayLookup, tl));
            }
        }
        for (DoTask task : tasks) {
            pool.submit(task);
        }
        for (DoTask task : tasks) {
            task.join();
        }

        List<DHONodeOptimized> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(DHONodeOptimized::support)); // stable → ties keep creation order
        return sorted;
    }

    /** Parallel DO recomputation for one node (accumulation stays on its own thread). */
    private static final class DoTask extends RecursiveTask<Void> {
        private final DHONodeOptimized node;
        private final DecayLookup decayLookup;
        private final int tl;

        DoTask(DHONodeOptimized node, DecayLookup decayLookup, int tl) {
            this.node = node;
            this.decayLookup = decayLookup;
            this.tl = tl;
        }

        @Override
        protected Void compute() {
            node.setDo(MetricCalculator.computeDO(node, decayLookup, tl));
            return null;
        }
    }
}