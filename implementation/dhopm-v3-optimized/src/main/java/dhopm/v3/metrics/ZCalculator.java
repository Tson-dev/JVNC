package dhopm.v3.metrics;

import dhopm.common.window.WindowMath;

/**
 * DO ceiling calculator: Z(f, TL) = (1 - f^TL) / (1 - f).
 * Maximum possible DO for any pattern (when |X| = 1 and appears in every transaction).
 * Identical to V2/common, kept here for module independence.
 */
public final class ZCalculator {

    private ZCalculator() {}

    /** Z(f, TL) = (1 - f^TL) / (1 - f). f ∈ (0,1], TL ≥ 1. */
    public static double compute(double f, long tl) {
        if (f == 1.0) {
            return tl; // limit as f→1
        }
        return (1.0 - Math.pow(f, tl)) / (1.0 - f);
    }

    /** Z(f, TL) using precomputed decay table if available. */
    public static double compute(double f, long tl, double fPowerTL) {
        if (f == 1.0) return tl;
        return (1.0 - fPowerTL) / (1.0 - f);
    }
}