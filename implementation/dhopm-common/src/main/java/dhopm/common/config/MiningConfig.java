package dhopm.common.config;

/**
 * Mining configuration (shared contract).
 *
 * <p>Validation follows the canonical spec (OVERALL-PLAN 2.1):
 * {@code delta in [0,1]}, {@code decayFactor in (0,1]} (f=1.0 allowed for the non-decay TC5),
 * epsilon &gt; 0, workers &ge; 1.
 *
 * @param delta       support threshold ratio {@code ∂}
 * @param decayFactor decay factor {@code f}
 * @param epsilon     numeric tolerance used for threshold comparisons (default {@value DEFAULT_EPSILON})
 * @param workers     number of worker threads (default = CPU count, decided D4)
 */
public record MiningConfig(double delta, double decayFactor, double epsilon, int workers) {

    public static final double DEFAULT_EPSILON = 1e-9;

    public static final int DEFAULT_WORKERS = Runtime.getRuntime().availableProcessors();

    public MiningConfig {
        if (Double.isNaN(delta) || delta < 0.0 || delta > 1.0) {
            throw new IllegalArgumentException("delta must be in [0,1]: " + delta);
        }
        if (Double.isNaN(decayFactor) || decayFactor <= 0.0 || decayFactor > 1.0) {
            throw new IllegalArgumentException("decayFactor must be in (0,1]: " + decayFactor);
        }
        if (Double.isNaN(epsilon) || epsilon <= 0.0) {
            throw new IllegalArgumentException("epsilon must be > 0: " + epsilon);
        }
        if (workers < 1) {
            throw new IllegalArgumentException("workers must be >= 1: " + workers);
        }
    }

    public static MiningConfig of(double delta, double decayFactor) {
        return new MiningConfig(delta, decayFactor, DEFAULT_EPSILON, DEFAULT_WORKERS);
    }

    /** {@code minSup = delta * totalTransactions} computed at mining time. */
    public double minSup(long totalTransactions) {
        return delta * totalTransactions;
    }
}