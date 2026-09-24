package dhopm.v1.metrics;

/**
 * Pure metrics functions (canonical 2.3). Isolated behind Strategy so V2 can
 * swap the computation without touching the pipeline (GoF Strategy).
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