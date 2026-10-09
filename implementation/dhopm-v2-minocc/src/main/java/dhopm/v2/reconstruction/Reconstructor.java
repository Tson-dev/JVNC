package dhopm.v2.reconstruction;

import dhopm.v2.metrics.MetricCalculator;
import dhopm.v2.dho.DHOList;
import dhopm.v2.dho.DHONode;
import dhopm.v2.dho.Entry;
import dhopm.common.util.WorkerPool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Giai đoạn 2 (GĐ2): recompute DO of every global node (from 0, sequentially over LIVE entries — C5)
 * and stable-sort nodes by support ascending (ties keep creation order — C3).
 *
 * <p>The dead prefix is discarded once per node (O(k) per node) BEFORE the DO pass and the sort,
 * so {@code support() = size() − head()} reflects only live transactions in the window.
 * In paper mode every entry is live (ref1 = null), {@code head} stays 0 and the pass is
 * bit-for-bit identical to V1 (INV-I).
 *
 * <p>Level 1 threading: one task per node. Each node is processed on a single thread so the
 * accumulation is deterministic for any pool size (INV-E).
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
        node.discardDeadPrefix();
        for (int i = node.head(); i < node.entries().size(); i++) {
            Entry e = node.entries().get(i);
            // length-1 pattern (global nodes) → occupancy = 1 / |T|
            node.addToDo(MetricCalculator.dampedContribution(1, e.len(), f, tl, e.tid()));
        }
    }
}