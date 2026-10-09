package dhopm.common.config;

/**
 * Mining configuration (shared contract).
 *
 * <p>Validation follows the canonical spec (OVERALL-PLAN 2.1 / D9, D29):
 * {@code partial in [0,1]}, {@code decayFactor in (0,1]} (f=1.0 allowed for the non-decay TC5),
 * {@code epsilon > 0}, {@code minOcc in [0,1)} (D30: {@code minOcc >= 1/(1-f)} is rejected
 * by the window validator), workers &ge; 1.
 *
 * <p>Naming (D9): the threshold ratio is denoted by {@code ∂} in the paper — {@code ∂} stands for
 * <em>partial</em> (partial differential symbol), NOT delta. {@code minOcc} is the official name of
 * the occupancy threshold that fixes the damped window size; {@code epsilon} is kept as the numeric
 * comparison tolerance (default 1e-9).
 *
 * @param partial     support threshold ratio {@code ∂} (partial, not delta)
 * @param decayFactor decay factor {@code f}
 * @param epsilon  numeric tolerance used for threshold comparisons (default {@value #DEFAULT_EPSILON})
 * @param minOcc     occupancy threshold that fixes the damped window size {@code W(f,minOcc)}
 *                    (0 = no window = paper mode; default 0 at the library level for {@code of},
 *                    V2+ engines use {@value #DEFAULT_MIN_OCC})
 * @param workers     number of worker threads (default = CPU count, decided D4)
 */
public record MiningConfig(double partial, double decayFactor, double epsilon, double minOcc, int workers) {

    /** Default comparison tolerance ε = 1e-9 (C4 / D29). */
    public static final double DEFAULT_EPSILON = 1e-9;

    /** Default window error for V2+ engines (D41: library default V2+ = 1e-6). */
    public static final double DEFAULT_MIN_OCC = 1e-6;

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
        if (Double.isNaN(minOcc) || minOcc < 0.0 || minOcc >= 1.0) {
            throw new IllegalArgumentException("minOcc must be in [0,1): " + minOcc);
        }
        if (workers < 1) {
            throw new IllegalArgumentException("workers must be >= 1: " + workers);
        }
    }

    /** Convenience 4-arg constructor (paper mode: minOcc = 0, no window). */
    public MiningConfig(double partial, double decayFactor, double epsilon, int workers) {
        this(partial, decayFactor, epsilon, 0.0, workers);
    }

    /** Paper-mode config (V1 and TC1–TC8 regression): ε = 1e-9, minOcc = 0. */
    public static MiningConfig of(double partial, double decayFactor) {
        return new MiningConfig(partial, decayFactor, DEFAULT_EPSILON, 0.0, DEFAULT_WORKERS);
    }

    /** Window-mode config for V2+: ε = 1e-9, minOcc = {@code minOcc}. */
    public static MiningConfig of(double partial, double decayFactor, double minOcc) {
        return new MiningConfig(partial, decayFactor, DEFAULT_EPSILON, minOcc, DEFAULT_WORKERS);
    }

    /** Same config but with minOcc replaced (used to build V2 configs from a base). */
    public MiningConfig withMinOcc(double newMinOcc) {
        return new MiningConfig(partial, decayFactor, epsilon, newMinOcc, workers);
    }

    /** {@code minSup = partial * totalTransactions} computed at mining time (no window). */
    public double minSup(long totalTransactions) {
        return partial * totalTransactions;
    }
}