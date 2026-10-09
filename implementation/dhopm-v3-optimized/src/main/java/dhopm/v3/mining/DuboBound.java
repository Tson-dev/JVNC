package dhopm.v3.mining;

/**
 * DUBO bound with Z ceiling (replaces V2 DuboBound record).
 * - dubo: DUBO upper bound for a pattern/node
 * - zx: Z(f, TL) ceiling for the pattern
 * - effectiveBound: min(dubo, zx) - used for pruning (only when window finite)
 */
public final class DuboBound {

    public final double dubo;
    public final double zx;
    public final double effectiveBound;

    public DuboBound(double dubo, double zx) {
        this.dubo = dubo;
        this.zx = zx;
        this.effectiveBound = Math.min(dubo, zx);
    }

    /** Returns the effective bound for pruning (min of DUBO and Z). */
    public double getEffectiveBound() {
        return effectiveBound;
    }

    /** Checks if the bound is below minSup (should prune). */
    public boolean belowMinSup(double minSup, double epsilonCmp) {
        return effectiveBound < minSup - epsilonCmp;
    }
}