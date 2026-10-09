package dhopm.v2.metrics;

/**
 * Pure metrics functions (canonical 2.3). Bit-for-bit identical to V1 — the window does not change
 * the per-occurrence math, only the set of LIVE occurrences it is applied over (window-truncated).
 */
public final class MetricCalculator {

    private MetricCalculator() {
    }

    /** Occupancy of a pattern in one transaction: {@code |X| / |Td|}. */
    public static double occupancy(int patternLen, int transactionLen) {
        return patternLen / (double) transactionLen;
    }

    /** Decay factor of one transaction: {@code f^(TL − Td)}. */
    public static double decay(double f, int tl, int tid) {
        return Math.pow(f, tl - tid);
    }

    /** Damped occupancy contribution of one occurrence. */
    public static double dampedContribution(int patternLen, int transactionLen, double f, int tl, int tid) {
        return occupancy(patternLen, transactionLen) * decay(f, tl, tid);
    }
}