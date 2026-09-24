package dhopm.v1.dubo;

import dhopm.v1.model.DHONode;
import dhopm.v1.model.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * DUBO upper bound (canonical 2.3, decision C1).
 *
 * <p>Entries are grouped by transaction length (ascending); group k holds sums
 * {@code (n_k, T_k)} with {@code n_k} = entry count and {@code T_k} = largest TID.
 * A single decay factor {@code f^(TL − T_k)} is applied to the whole group sum (C1):
 * {@code DUBO(X,k) = f^(TL − T_k) × Σ_{i≥k} n_i × (l_k / l_i)}, {@code DUBO(X) = max_k}.
 */
public final class DuboCalculator {

    private DuboCalculator() {
    }

    public static double dubo(DHONode node, double f, int tl) {
        // length -> {count, lastTid}; TreeMap keeps lengths ascending.
        TreeMap<Integer, int[]> groups = new TreeMap<>();
        for (Entry e : node.entries()) {
            int[] g = groups.computeIfAbsent(e.len(), k -> new int[2]);
            g[0]++;
            if (e.tid() > g[1]) {
                g[1] = e.tid();
            }
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
        return best;
    }
}