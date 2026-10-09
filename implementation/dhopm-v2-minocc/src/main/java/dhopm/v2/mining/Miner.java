package dhopm.v2.mining;

import dhopm.common.contract.Pattern;
import dhopm.v2.dho.DHONode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giai đoạn 3 (GĐ3): DFS pattern-growth mining (canonical 2.2) — V2 extension.
 *
 * <p>Rules applied per node (in list order):
 * <ul>
 *   <li>support &lt; minSup → skip entirely (C2) — support is window-truncated;
 *   <li>{@code DO ≥ minSup − ε} → add pattern (C4);
 *   <li>{@code UB' < minSup − ε} → prune (no expansion), where
 *       {@code UB' = min(DUBO(X), Z(X))} when the window is finite (tighter than DUBO alone),
 *       and {@code UB' = DUBO(X)} in paper mode (bit-for-bit V1 — INV-I);
 *   <li>otherwise build the conditional list by intersecting with every following node.
 * </ul>
 *
 * <p>Each root subtree (fixed first item) is processed as an independent deterministic unit so
 * Level 1 mining can parallelize per root and merge after join (INV-E).
 */
public final class Miner {

    private Miner() {
    }

    /**
     * @param windowTruncated whether the finite-window bound {@code min(DUBO, Z)} is applied;
     *                        {@code false} ⇔ minOcc = 0 (paper mode) ⇔ V1 prune.
     */
    public static Map<String, Pattern> mineRoot(List<DHONode> list, int start,
                                                double f, double epsilon, double minSup, int tl,
                                                boolean windowTruncated) {
        return process(list, start, new String[0], 0, f, epsilon, minSup, tl, windowTruncated);
    }

    private static Map<String, Pattern> process(List<DHONode> list, int start,
                                                String[] prefix, int prefixLen,
                                                double f, double epsilon, double minSup, int tl,
                                                boolean windowTruncated) {
        Map<String, Pattern> results = new LinkedHashMap<>();
        for (int i = start; i < list.size(); i++) {
            DHONode node = list.get(i);
            if (node.support() < minSup) {
                continue; // C2: skip entirely (no DOP check, no expansion)
            }

            String[] items = extend(prefix, prefixLen, node.item());
            if (node.doValue() >= minSup - epsilon) { // C4
                results.putIfAbsent(canonicalKey(items), new Pattern(items, node.doValue(), node.tids()));
            }

            DuboBound bound = DuboBound.compute(node, f, tl);
            double ub = windowTruncated ? Math.min(bound.dubo(), bound.zX()) : bound.dubo();
            if (ub < minSup - epsilon) {
                continue; // prune: do not expand this prefix
            }

            List<DHONode> ncl = new ArrayList<>();
            for (int j = i + 1; j < list.size(); j++) {
                DHONode partner = list.get(j);
                if (partner.support() < minSup) {
                    continue; // |Xi ∩ Xj| <= support_j < minSup → C2 would skip it anyway
                }
                DHONode combined = ConditionalListBuilder.intersect(node, partner, prefixLen + 2, f, tl);
                if (combined.support() > 0) {
                    ncl.add(combined);
                }
            }
            if (!ncl.isEmpty()) {
                results.putAll(process(ncl, 0, items, prefixLen + 1, f, epsilon, minSup, tl, windowTruncated));
            }
        }
        return results;
    }

    private static String[] extend(String[] prefix, int prefixLen, String item) {
        String[] items = new String[prefixLen + 1];
        System.arraycopy(prefix, 0, items, 0, prefixLen);
        items[prefixLen] = item;
        return items;
    }

    private static String canonicalKey(String[] items) {
        String[] sorted = items.clone();
        java.util.Arrays.sort(sorted);
        return String.join(",", sorted);
    }
}