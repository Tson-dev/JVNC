package dhopm.algo;

import dhopm.model.DhoNode;
import dhopm.model.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Damped Upper Bound Occupancy (DUBO) pruning strategy proposed in the
 * paper (Eq. 7-8). Transactions are grouped by length in ascending order; for
 * each reference group {@code k}:
 * <pre>
 * DUBO(X,k) = f^(latestTid - T_k) * sum_{i>=k} n_i * (l_k / l_i)
 * DUBO(X)   = max_k DUBO(X,k)
 * </pre>
 * where {@code n_i} is the number of transactions of length {@code l_i} and
 * {@code T_k} the largest TID inside group {@code k}. Note that with {@code f=1}
 * the bound degenerates to the classical {@code UBO} of Deng (2020).
 */
public final class DampedUpperBound implements UpperBoundCalculator {

    @Override
    public double upperBound(DhoNode node, double f, int latestTid) {
        Map<Integer, int[]> byLen = new TreeMap<>(); // length -> {count, maxTid}
        for (Entry e : node.entries()) {
            int[] group = byLen.computeIfAbsent(e.tLen(), k -> new int[]{0, 0});
            group[0]++;
            group[1] = Math.max(group[1], e.tid());
        }

        List<Map.Entry<Integer, int[]>> groups = new ArrayList<>(byLen.entrySet());
        double maximum = 0.0;
        for (int k = 0; k < groups.size(); k++) {
            var gk = groups.get(k);
            double weightedSum = 0.0;
            for (int i = k; i < groups.size(); i++) {
                var gi = groups.get(i);
                weightedSum += gi.getValue()[0] * (gk.getKey() / (double) gi.getKey());
            }
            double duboK = weightedSum * Math.pow(f, latestTid - gk.getValue()[1]);
            maximum = Math.max(maximum, duboK);
        }
        return maximum;
    }
}