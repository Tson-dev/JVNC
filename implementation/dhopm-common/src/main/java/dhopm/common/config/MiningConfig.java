package dhopm.common.config;

/**
 * Mining configuration (shared contract).
 *
 * <p>Validation follows the canonical spec (OVERALL-PLAN 2.1):
 * {@code partial in [0,1]}, {@code decayFactor in (0,1]} (f=1.0 allowed for the non-decay TC5),
 * epsilon &gt; 0, workers &ge; 1.
 *
 * <p>Naming: the threshold ratio is denoted by {@code ∂} in the paper — {@code ∂} stands for
 * <em>partial</em> (partial differential symbol), NOT delta.
 *
 * @param partial     support threshold ratio {@code ∂} (partial, not delta)
 * @param decayFactor decay factor {@code f}
 * @param epsilon     numeric tolerance used for threshold comparisons (default {@value DEFAULT_EPSILON})
 * @param workers     number of worker threads (default = CPU count, decided D4)
 */
public record MiningConfig(double partial, double decayFactor, double epsilon, int workers) {

    public static final double DEFAULT_EPSILON = 1e-9;

    public static final int DEFAULT_WORKERS = Runtime.getRuntime().availableProcessors();

    public MiningConfig {
        if (Double.isNaN(partial) || partial < 0.0 || partial > 1.0) {
            throw new IllegalArgumentException("partial must be in [0,1]: " + partial);
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

    public static MiningConfig of(double partial, double decayFactor) {
        return new MiningConfig(partial, decayFactor, DEFAULT_EPSILON, DEFAULT_WORKERS);
    }

    /** {@code minSup = partial * totalTransactions} computed at mining time. */
    public double minSup(long totalTransactions) {
        return partial * totalTransactions;
    }
}