package dhopm.v2.mining;

import dhopm.v2.dho.DHONode;
import dhopm.v2.dho.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * DUBO upper bound (canonical 2.3, C1) + the node ceiling {@code Z(X)} in a single walk.
 *
 * <p>Entries are grouped by transaction length (ascending); group k holds sums
 * {@code (n_k, T_k)} with {@code n_k} = entry count and {@code T_k} = largest TID.
 * A single decay factor {@code f^(TL − T_k)} is applied to the whole group sum (C1):
 * {@code DUBO(X,k) = f^(TL − T_k) × Σ_{i≥k} n_i × (l_k / l_i)}, {@code DUBO(X) = max_k}.
 *
 * <p>Only LIVE entries are grouped (dead prefix skipped). Both bounds are consumed by
 * {@link dhopm.v2.mining.Miner} as {@code UB'(X) = min(DUBO(X), Z(X))} when the window is finite;
 * {@code Z(X)} is ignored in paper mode so the prune is exactly V1's (INV-I).
 *
 * @param dubo  {@code DUBO(X)}
 * @param zX    {@code Z(X) = Σ f^(TL−t)} over live entries
 */
public record DuboBound(double dubo, double zX) {

    /** One walk, both bounds. */
    public static DuboBound compute(DHONode node, double f, int tl) {
        // length -> {count, lastTid}; TreeMap keeps lengths ascending.
        TreeMap<Integer, int[]> groups = new TreeMap<>();
        double z = 0.0;
        for (int i = node.head(); i < node.entries().size(); i++) {
            Entry e = node.entries().get(i);
            int[] g = groups.computeIfAbsent(e.len(), k -> new int[2]);
            g[0]++;
            if (e.tid() > g[1]) {
                g[1] = e.tid();
            }
            z += Math.pow(f, tl - e.tid());
        }

        List<Map.Entry<Integer, int[]>> byLen = new ArrayList<>(groups.entrySet()); // ascending length
        double best = 0.0;
        for (int k = 0; k < byLen.size(); k++) {
            int lk = byLen.get(k).getKey();
            int tk = byLen.get(k).getValue()[1];
            double decay = Math.pow(f, tl - tk);
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