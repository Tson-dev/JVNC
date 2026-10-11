package dhopm.v3.window;

import dhopm.common.window.WindowMath;

/**
 * Decay lookup: {@code get(age) = f^age}, cached in a table for the finite window
 * {@code age ∈ [0, W-1]}; otherwise falls back to {@code Math.pow(f, age)}.
 *
 * <p>Bit-parity: the table is built with {@code Math.pow(f, k)} (NOT repeated multiplication) so
 * {@code table[age]} is bit-identical to V1/V2's {@code Math.pow(f, tl - tid)} — required by
 * {@code dhopm.common.testkit.GoldenAssert#samePatternSet} (exact {@code Double.compare}).
 */
public final class DecayLookup {

    private final double f;
    private final boolean infinite;
    private final double[] table;   // table[k] = f^k for k = 0..maxAge
    private final int maxAge;       // W - 1 (finite) / -1 (infinite)

    private DecayLookup(double f, boolean infinite, double[] table, int maxAge) {
        this.f = f;
        this.infinite = infinite;
        this.table = table;
        this.maxAge = maxAge;
    }

    public static DecayLookup create(double f, long windowSize) {
        if (windowSize == WindowMath.INFINITE || f == 1.0) {
            return new DecayLookup(f, true, null, -1);
        }
        int maxAge = Math.toIntExact(windowSize) - 1;
        double[] table = new double[maxAge + 1];
        for (int k = 0; k <= maxAge; k++) {
            table[k] = Math.pow(f, k);
        }
        return new DecayLookup(f, false, table, maxAge);
    }

    /** {@code f^age}, bit-identical to {@code Math.pow(f, age)}. */
    public double get(int age) {
        if (infinite) {
            return Math.pow(f, age);
        }
        if (age >= 0 && age <= maxAge) {
            return table[age];
        }
        return Math.pow(f, age);
    }

    public boolean isInfinite() {
        return infinite;
    }
}