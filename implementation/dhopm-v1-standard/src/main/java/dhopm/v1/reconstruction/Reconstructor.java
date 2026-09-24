package dhopm.v1.reconstruction;

import dhopm.v1.metrics.MetricCalculator;
import dhopm.v1.model.DHOList;
import dhopm.v1.model.DHONode;
import dhopm.v1.model.Entry;
import dhopm.common.util.WorkerPool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Giai đoạn 2 (GĐ2): recompute DO of every global node (from 0, sequentially over entries — C5)
 * and stable-sort nodes by support ascending (ties keep creation order — C3).
 *
 * <p>Level 1 threading: one task per node. Each node is processed on a single thread
 * so the accumulation is bit-for-bit identical for any pool size (INV-E).
 */
public final class Reconstructor {

    private Reconstructor() {
    }

    public static List<DHONode> run(DHOList global, double f, int tl, WorkerPool pool) {
        List<DHONode> nodes = global.nodes(); // snapshot in creation order
        List<Runnable> tasks = new ArrayList<>(nodes.size());
        for (DHONode node : nodes) {
            tasks.add(() -> recomputeDo(node, f, tl));
        }
        for (Runnable task : tasks) {
            pool.submit(task);
        }
        pool.awaitAll();

        List<DHONode> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(DHONode::support)); // stable → ties keep creation order
        return sorted;
    }

    private static void recomputeDo(DHONode node, double f, int tl) {
        node.resetDo();
        for (Entry e : node.entries()) {
            // length-1 pattern (global nodes) → occupancy = 1 / |T|
            node.addToDo(MetricCalculator.dampedContribution(1, e.len(), f, tl, e.tid()));
        }
    }
}