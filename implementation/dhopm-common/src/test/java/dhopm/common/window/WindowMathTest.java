package dhopm.common.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WindowMathTest — M1 acceptance (plan 02 §6 / D38 / G2-D14).
 *
 * <p>Cross-checks the closed-form W against the canonical lookup table (OVERALL-PLAN §2.3.1),
 * the feasible-∂ table (§6.4), and the two maxPartial formulas (D38 — mandatory case TL &lt; W
 * so the exact and the asymptotic formulas are never mixed).
 */
class WindowMathTest {

    private static final double TOL = 1e-12;

    // --- W lookup table (OVERALL-PLAN §2.3.1) -----------------------------------------------
    private static long w(double f, double eps) {
        return WindowMath.computeWindow(f, eps);
    }

    @Test
    void wMatchesLookupTable() {
        assertEquals(11, w(0.5, 1e-3));
        assertEquals(21, w(0.5, 1e-6));
        assertEquals(31, w(0.5, 1e-9));
        assertEquals(41, w(0.5, 1e-12));

        assertEquals(39, w(0.8, 1e-3));
        assertEquals(70, w(0.8, 1e-6));
        assertEquals(101, w(0.8, 1e-9));
        assertEquals(132, w(0.8, 1e-12));

        assertEquals(88, w(0.9, 1e-3));
        assertEquals(153, w(0.9, 1e-6));
        assertEquals(219, w(0.9, 1e-9));
        assertEquals(285, w(0.9, 1e-12));

        assertEquals(194, w(0.95, 1e-3));
        assertEquals(328, w(0.95, 1e-6));
        assertEquals(463, w(0.95, 1e-9));
        assertEquals(598, w(0.95, 1e-12));

        assertEquals(1146, w(0.99, 1e-3));
        assertEquals(1833, w(0.99, 1e-6));
        assertEquals(2521, w(0.99, 1e-9));
        assertEquals(3208, w(0.99, 1e-12));

        assertEquals(13809, w(0.999, 1e-3));
        assertEquals(20713, w(0.999, 1e-6));
        assertEquals(27618, w(0.999, 1e-9));
        assertEquals(34522, w(0.999, 1e-12));
    }

    // --- edge behaviour (canonical 2.3.1) ---------------------------------------------------
    @Test
    void minOccZeroMeansInfiniteWindow() {
        assertEquals(WindowMath.INFINITE, w(0.9, 0.0));
        assertEquals(WindowMath.INFINITE, w(0.5, 0.0));
    }

    @Test
    void fEqualOneMeansInfiniteWindow() {
        assertEquals(WindowMath.INFINITE, w(1.0, 1e-3));
        assertEquals(WindowMath.INFINITE, w(1.0, 0.0));
    }

    @Test
    void minOccAtLeastOneOverOneMinusFIsRejected() {
        // canonical rule (D30): minOcc ≥ 1/(1−f) ⇒ W = 0. For f=0.9 the boundary is 10.
        assertThrows(IllegalArgumentException.class, () -> w(0.9, 10.0));
        assertThrows(IllegalArgumentException.class, () -> w(0.9, 100.0));
        assertThrows(IllegalArgumentException.class, () -> w(0.8, 5.0));
        assertThrows(IllegalArgumentException.class, () -> w(0.5, 2.0));
        assertThrows(IllegalArgumentException.class, () -> w(0.99, 100.0));
    }

    @Test
    void windowRespectsMaxWindowGuard() {
        assertThrows(IllegalArgumentException.class, () -> WindowMath.computeWindow(0.999999, 1e-12, 1_000_000L));
    }

    // --- Z(f,TL) ---------------------------------------------------------------------------
    @Test
    void maxDoIsGeometricSeries() {
        assertEquals(1.0 + 0.9 + 0.81 + 0.729, WindowMath.maxDO(0.9, 4), TOL); // = 3.439
        assertEquals(4.0, WindowMath.maxDO(1.0, 4), TOL);
        assertEquals(0.0, WindowMath.maxDO(0.9, 0), TOL);
        assertEquals((1 - Math.pow(0.9, 8)) / 0.1, WindowMath.maxDO(0.9, 8), TOL);
    }

    // --- N_eff / minSup (two-phase, C9/D11) -------------------------------------------------
    @Test
    void effectiveTransactionsAndMinSup() {
        assertEquals(4L, WindowMath.effectiveTransactions(4, 153));
        assertEquals(153L, WindowMath.effectiveTransactions(1000, 153));
        assertEquals(1000L, WindowMath.effectiveTransactions(1000, WindowMath.INFINITE));
        assertEquals(1.2, WindowMath.minSup(0.15, 8, WindowMath.INFINITE), TOL);  // ≡ paper
        assertEquals(0.6, WindowMath.minSup(0.15, 4, 153), TOL);                  // phase 1 (TL < W)
        assertEquals(22.95, WindowMath.minSup(0.15, 1000, 153), TOL);             // phase 2 frozen
        assertEquals(22.95, WindowMath.minSup(0.15, 100000, 153), TOL);           // frozen regardless of TL
    }

    // --- D38: the two maxPartial formulas MUST be distinct -------------------------------
    @Test
    void maxPartialExactPhaseOneMuchLargerThanAsymptotic() {
        // f=0.9, minOcc=1e-6, TL=4: exact = 3.439/4 = 85.98 %, asymptotic = 1/(0.1*153) = 6.54 %
        double exact = WindowMath.maxPartialExact(0.9, 1e-6, 4);
        double asym = WindowMath.maxPartialAsymptotic(0.9, 1e-6);
        assertEquals(3.439 / 4.0, exact, 1e-12);
        assertEquals(1.0 / (0.1 * 153.0), asym, 1e-12);
        assertTrue(exact > asym * 10, "exact must be ≫ asymptotic at short TL (D38)");
    }

    @Test
    void maxPartialExactConvergesToAsymptoticAtLongTl() {
        double exact = WindowMath.maxPartialExact(0.9, 1e-3, 88);  // TL = W
        double asym = WindowMath.maxPartialAsymptotic(0.9, 1e-3);
        assertEquals(WindowMath.maxDO(0.9, 88) / 88.0, exact, 1e-12);
        // Phase 2 boundary: the plan's "×1.00" is to ~1e-4 (0.9^88 ≈ 9.4e-5 leftover), not exact.
        assertEquals(asym, exact, 1e-4);
    }

    @Test
    void maxPartialExactForTc1ConfigIsFeasible() {
        // TC1: f=0.9, minOcc=0 ⇒ window infinite, TL=8 ⇒ ∂ ≤ Z/8 = 5.679/8 = 0.7099
        assertEquals(WindowMath.maxDO(0.9, 8) / 8.0, WindowMath.maxPartialExact(0.9, 0.0, 8), TOL);
        assertEquals(1.0, WindowMath.maxPartialAsymptotic(0.9, 0.0), TOL);
    }

    // --- feasible-∂ table (§6.4, asymptotic column) ----------------------------------------
    @Test
    void feasiblePartialTable() {
        assertEquals(1.0 / (0.1 * 88.0), WindowMath.maxPartialAsymptotic(0.9, 1e-3), 1e-12);   // 11.4 %
        assertEquals(1.0 / (0.2 * 70.0), WindowMath.maxPartialAsymptotic(0.8, 1e-6), 1e-12);   // 7.14 %
        assertEquals(1.0 / (0.05 * 328.0), WindowMath.maxPartialAsymptotic(0.95, 1e-6), 1e-12); // 6.10 %
        assertEquals(1.0 / (0.01 * 1833.0), WindowMath.maxPartialAsymptotic(0.99, 1e-6), 1e-12); // 5.46 %
    }
}