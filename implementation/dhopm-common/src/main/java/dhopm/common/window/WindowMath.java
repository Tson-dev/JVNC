package dhopm.common.window;

/**
 * Window math — the closed-form formulas behind the damped window minOcc (canonical 2.3 / D10, D38).
 *
 * <p>All four versions share these definitions:
 * <ul>
 *   <li>{@code W(f,minOcc)} size of the damped window;
 *   <li>{@code Z(f,TL)} total damped mass of the stream — also the physical ceiling of any DO;
 *   <li>{@code N_eff = min(TL, W)} effective DB size (two-phase minSup, C9/D11);
 *   <li>two distinct "max partial" formulas that MUST NOT be mixed (D38):
 *     {@link #maxPartialExact} (finite, exact — validator) and
 *     {@link #maxPartialAsymptotic} (TL→∞ limit — lookup tables only).
 * </ul>
 */
public final class WindowMath {

    private WindowMath() {
    }

    /** Sentinel for an unbounded window (minOcc = 0 or f = 1). */
    public static final long INFINITE = Long.MAX_VALUE;

    /** Default cap guarding allocation (canonical 2.1: {@code maxWindow = 10^7}). */
    public static final long DEFAULT_MAX_WINDOW = 10_000_000L;

    /**
     * {@code W(f,minOcc) = ceil(ln(minOcc(1−f)) / ln f)}.
     *
     * @param f       decay factor, in (0,1]
     * @param minOcc systematic error, in [0,1)
     * @return window size, or {@link #INFINITE} when minOcc = 0 or f = 1
     * @throws IllegalArgumentException when minOcc ≥ 1/(1−f) (empty window, D30) or W exceeds {@code maxWindow}
     */
    public static long computeWindow(double f, double minOcc) {
        return computeWindow(f, minOcc, DEFAULT_MAX_WINDOW);
    }

    /**
     * @see #computeWindow(double, double)
     * @param maxWindow guard that rejects configurations that would allocate too much
     */
    public static long computeWindow(double f, double minOcc, long maxWindow) {
        if (Double.isNaN(f) || f <= 0.0 || f > 1.0) {
            throw new IllegalArgumentException("f must be in (0,1]: " + f);
        }
        if (Double.isNaN(minOcc) || minOcc < 0.0 || minOcc >= 1.0) {
            throw new IllegalArgumentException("minOcc must be in [0,1): " + minOcc);
        }
        if (minOcc == 0.0 || f == 1.0) {
            return INFINITE;
        }
        if (minOcc >= 1.0 / (1.0 - f)) {
            throw new IllegalArgumentException(
                    "minOcc too large: minOcc = " + minOcc + " >= 1/(1−f) = " + (1.0 / (1.0 - f))
                            + " ⇒ W = 0 (empty window) [MIN_OCC_TOO_LARGE]");
        }
        double w = Math.ceil(Math.log(minOcc * (1.0 - f)) / Math.log(f));
        if (!(w >= 1.0) || w > maxWindow) {
            throw new IllegalArgumentException(
                    "window W(f,minOcc) = " + w + " exceeds maxWindow = " + maxWindow + " [WINDOW_TOO_LARGE]");
        }
        return (long) w;
    }

    /**
     * Total damped mass of the whole stream {@code Z(f,TL) = Σ_{k=0..TL−1} f^k = (1−f^TL)/(1−f)}.
     * This is the physical ceiling of every DO (canonical 2.5).
     */
    public static double maxDO(double f, long tl) {
        if (tl <= 0) {
            return 0.0;
        }
        if (f == 1.0) {
            return tl;
        }
        return (1.0 - Math.pow(f, tl)) / (1.0 - f);
    }

    /** Effective DB size {@code N_eff = min(TL, W)}; {@code W = INFINITE ⇒ N_eff = TL}. */
    public static long effectiveTransactions(long tl, long windowSize) {
        return windowSize == INFINITE ? tl : Math.min(tl, windowSize);
    }

    /** Two-phase minSup {@code ∂ × N_eff} (C9/D11). */
    public static double minSup(double partial, long tl, long windowSize) {
        return partial * effectiveTransactions(tl, windowSize);
    }

    /**
     * EXACT feasible bound {@code ∂ ≤ Z(f,TL) / N_eff} (D38 — the only one the validator may use).
     *
     * @return 0 when the config is invalid (window empty), otherwise Z/N_eff ∈ (0,1]
     */
    public static double maxPartialExact(double f, double minOcc, long tl) {
        final long w;
        try {
            w = computeWindow(f, minOcc);
        } catch (IllegalArgumentException e) {
            return 0.0;
        }
        long nEff = effectiveTransactions(tl, w);
        if (nEff <= 0) {
            return 0.0;
        }
        return maxDO(f, tl) / nEff;
    }

    /**
     * ASYMPTOTIC feasible bound {@code ∂ ≲ 1/((1−f)·W)} — the limit TL→∞ (edge of phase 2).
     * For lookup tables / the {@code window} CLI only (D38, G2-D14). Never use it to reject configs.
     */
    public static double maxPartialAsymptotic(double f, double minOcc) {
        long w = computeWindow(f, minOcc);
        if (w == INFINITE) {
            return 1.0;
        }
        return 1.0 / ((1.0 - f) * w);
    }
}