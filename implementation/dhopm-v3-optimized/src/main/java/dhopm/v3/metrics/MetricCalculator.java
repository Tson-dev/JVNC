package dhopm.v3.metrics;

import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.window.DecayLookup;

/**
 * DO metric (canonical 2.3). Same contributions as V1/V2, but computed through
 * {@link DecayLookup} (bit-identical to {@code Math.pow(f, tl - tid)}) over the SoA live region
 * {@code node.head() .. node.size()}.
 */
public final class MetricCalculator {

    private MetricCalculator() {
    }

    /** Occupancy of a pattern in one transaction: {@code |X| / |Td|}. */
    public static double occupancy(int patternLen, int transactionLen) {
        return patternLen / (double) transactionLen;
    }

    /**
     * DO of a length-1 pattern (global node): {@code Σ (1/|T|) · f^(TL−Td)} over live entries,
     * accumulated in TID order on a single thread (C5).
     */
    public static double computeDO(DHONodeOptimized node, DecayLookup decayLookup, int tl) {
        double sum = 0.0;
        int end = node.size();
        for (int i = node.head(); i < end; i++) {
            sum += (1.0 / node.lenAt(i)) * decayLookup.get(tl - node.tidAt(i));
        }
        return sum;
    }

    /** Support (C2) = number of live entries. */
    public static int support(DHONodeOptimized node) {
        return node.support();
    }
}