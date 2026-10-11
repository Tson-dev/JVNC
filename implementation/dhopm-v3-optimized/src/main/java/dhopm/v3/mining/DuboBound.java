package dhopm.v3.mining;

import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.window.DecayLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * DUBO upper bound + the per-node ceiling {@code Z(X)} in one walk over the live entries
 * {@code node.head() .. node.size()} (canonical 2.3 / C1).
 *
 * <p>Pruning uses {@code min(DUBO(X), Z(X))}: both are valid upper bounds of the DO of every
 * super-pattern of X — {@code P ≤ DUBO(X)} and {@code P ≤ Z(X) = Σ f^(TL−t)} over the live
 * occurrences of X — so the bound is safe in every mode: it never removes a DOP, hence the
 * emitted DOP set is identical to V1's DUBO-only prune (invariance holds bit-for-bit).
 *
 * @param dubo {@code DUBO(X)}
 * @param zX   {@code Z(X) = Σ f^(TL−t)} over live entries
 */
public record DuboBound(double dubo, double zX) {

    public double effectiveBound() {
        return Math.min(dubo, zX);
    }

    public boolean belowMinSup(double minSup, double epsilon) {
        return effectiveBound() < minSup - epsilon;
    }

    /** One walk, both bounds (entries grouped by |T| ascending; a single decay factor per group). */
    public static DuboBound compute(DHONodeOptimized node, DecayLookup decayLookup, int tl) {
        TreeMap<Integer, int[]> groups = new TreeMap<>();
        double z = 0.0;
        int end = node.size();
        for (int i = node.head(); i < end; i++) {
            int len = node.lenAt(i);
            int tid = node.tidAt(i);
            int[] g = groups.computeIfAbsent(len, k -> new int[2]);
            g[0]++;
            if (tid > g[1]) {
                g[1] = tid;
            }
            z += decayLookup.get(tl - tid);
        }

        List<Map.Entry<Integer, int[]>> byLen = new ArrayList<>(groups.entrySet()); // ascending |T|
        double best = 0.0;
        for (int k = 0; k < byLen.size(); k++) {
            int lk = byLen.get(k).getKey();
            int tk = byLen.get(k).getValue()[1];
            double decay = decayLookup.get(tl - tk);
            double sum = 0.0;
            for (int i = k; i < byLen.size(); i++) {
                int[] gi = byLen.get(i).getValue();
                int li = byLen.get(i).getKey();
                sum += gi[0] * ((double) lk / li);
            }
            double value = decay * sum;
            if (value > best) {
                best = value;
            }
        }
        return new DuboBound(best, z);
    }
}