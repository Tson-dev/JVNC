package dhopm.v3.reconstruction;

import dhopm.common.contract.Phase;
import dhopm.common.contract.PhaseListener;
import dhopm.common.util.TimingRecorder;
import dhopm.v3.dho.DHOListOptimized;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.metrics.MetricCalculator;
import dhopm.v3.metrics.ZCalculator;
import dhopm.v3.mining.DuboBound;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Optimized Reconstructor (GĐ2).
 * - Discards dead prefixes for all nodes
 * - Computes DO for each node in parallel (one task per node)
 * - Stable sorts nodes by support (C3): descending support, tie-break by creation order
 * - Computes DUBO bounds per node
 */
public final class ReconstructorOptimized {

    private final DHOListOptimized dhoList;
    private final WindowBufferOptimized window;
    private final DecayLookup decayLookup;
    private final int tl;
    private final double f;
    private final double minSup;
    private final PhaseListener phaseListener;

    // Results
    private final List<DHONodeOptimized> sortedNodes = new ArrayList<>();
    private final List<DuboBound> duboBounds = new ArrayList<>();

    public ReconstructorOptimized(DHOListOptimized dhoList, WindowBufferOptimized window,
                                  DecayLookup decayLookup, int tl, double f, double minSup,
                                  PhaseListener phaseListener) {
        this.dhoList = dhoList;
        this.window = window;
        this.decayLookup = decayLookup;
        this.tl = tl;
        this.f = f;
        this.minSup = minSup;
        this.phaseListener = phaseListener;
    }

    /**
     * Runs reconstruction: discard dead prefixes → compute DO → stable sort by support → compute DUBO.
     * @return list of nodes sorted by support descending (stable by creation order)
     */
    public List<DHONodeOptimized> reconstruct(ForkJoinPool pool) {
        TimingRecorder.Record rec = phaseListener != null ? new TimingRecorder.Record(Phase.RECONSTRUCTION) : null;

        // 1. Discard dead prefixes (single-threaded, fast)
        dhoList.discardAllDeadPrefixes(window);

        // 2. Update decay values in window buffer
        if (!decayLookup.isInfinite()) {
            window.updateDecay(f, tl);
        }

        // 3. Compute DO for each node in parallel
        List<RecTask> tasks = new ArrayList<>();
        for (DHONodeOptimized node : dhoList.nodesInCreationOrder()) {
            if (node.size > node.head) { // has live entries
                tasks.add(new RecTask(node));
            }
        }
        pool.invokeAll(tasks);
        for (RecTask t : tasks) {
            t.join();
            sortedNodes.add(t.node);
        }

        // 4. Stable sort by support descending (C3)
        // Tie-break: creation order (which is the order in sortedNodes before sort)
        // We use List.sort which is stable (TimSort)
        sortedNodes.sort(Comparator
                .comparingInt((DHONodeOptimized n) -> -n.support(window)) // descending support
                .thenComparingInt(n -> n.itemId)); // creation order proxy (itemId assigned in creation order)

        // 5. Compute DUBO bounds
        computeDUBO();

        if (rec != null) {
            rec.end();
            phaseListener.onPhase(Phase.RECONSTRUCTION, rec.startNanos, rec.endNanos);
        }
        return sortedNodes;
    }

    private void computeDUBO() {
        // Group by transaction length (ref2) - use bucket array since length is bounded
        // Max length = max txLen in window (typically small)
        int maxLen = 0;
        for (DHONodeOptimized node : sortedNodes) {
            for (int i = node.head; i < node.size; i++) {
                int slot = node.txSlot[i];
                if (window.alive[slot] == 1) {
                    maxLen = Math.max(maxLen, window.txLen[slot]);
                }
            }
        }

        // DUBO groups: length -> {count, lastTid}
        int[] groupCount = new int[maxLen + 1];
        int[] groupLastTid = new int[maxLen + 1];

        for (DHONodeOptimized node : sortedNodes) {
            for (int i = node.head; i < node.size; i++) {
                int slot = node.txSlot[i];
                if (window.alive[slot] == 1) {
                    int len = window.txLen[slot];
                    groupCount[len]++;
                    groupLastTid[len] = Math.max(groupLastTid[len], window.txId[slot]);
                }
            }
        }

        // Compute DUBO per node: min(DUBO, Z(X))
        // DUBO = Σ_len (count_len / len) * f^(TL - lastTid_len)
        // Z(X) = Z(f, tl) * (1/|X|) -- but we use per-pattern Z in mining
        // Here we compute node-level DUBO for pruning
        duboBounds.clear();
        for (DHONodeOptimized node : sortedNodes) {
            double dubo = 0.0;
            for (int len = 1; len <= maxLen; len++) {
                if (groupCount[len] > 0) {
                    int age = tl - groupLastTid[len];
                    double decay = decayLookup.get(age);
                    dubo += (groupCount[len] / (double) len) * decay;
                }
            }
            double zx = ZCalculator.compute(f, tl); // Z(X) for |X|=1
            duboBounds.add(new DuboBound(dubo, zx));
        }
    }

    public List<DuboBound> getDuboBounds() {
        return duboBounds;
    }

    /** Task for parallel DO computation per node. */
    private final class RecTask extends RecursiveTask<Void> {
        final DHONodeOptimized node;

        RecTask(DHONodeOptimized node) {
            this.node = node;
        }

        @Override
        protected Void compute() {
            node.doValue = MetricCalculator.computeDO(node, window, tl);
            return null;
        }
    }
}