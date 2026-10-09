package dhopm.v2.metrics;

import dhopm.common.window.WindowMath;
import dhopm.v2.dho.DHONode;
import dhopm.v2.dho.Entry;

/**
 * The DO ceiling {@code Z(f,TL)} (canonical 2.5) and its node-level counterpart {@code Z(X)} used
 * for the tighter prune bound {@code UB'(X) = min(DUBO(X), Z(X))}.
 *
 * <p>{@code globalMaxDo} delegates to the shared {@link WindowMath#maxDO} (the engine short-circuit
 * uses it); {@code zOfNode}, like DUBO, sums only LIVE entries and emits the same f-powers.
 */
public final class ZCalculator {

    private ZCalculator() {
    }

    /** {@code Z(f,TL)} — shared closed form. */
    public static double globalMaxDo(double f, long tl) {
        return WindowMath.maxDO(f, tl);
    }

    /** {@code Z(X) = Σ_{t ∈ T_live(X)} f^(TL−t)} — node-level ceiling (UB' component). */
    public static double zOfNode(DHONode node, double f, int tl) {
        double z = 0.0;
        for (int i = node.head(); i < node.entries().size(); i++) {
            Entry e = node.entries().get(i);
            z += Math.pow(f, tl - e.tid());
        }
        return z;
    }
}