package dhopm.v1.mining;

import dhopm.common.contract.Pattern;
import dhopm.v1.dubo.DuboCalculator;
import dhopm.v1.model.DHONode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giai đoạn 3 (GĐ3): DFS pattern-growth mining (canonical 2.2).
 *
 * <p>Rules applied per node (in list order):
 * <ul>
 *   <li>support &lt; minSup → skip entirely (C2);
 *   <li>{@code DO ≥ minSup − ε} → add pattern (C4);
 *   <li>DUBO &lt; minSup − ε → prune (no expansion);
 *   <li>otherwise build the conditional list by intersecting with every following node.
 * </ul>
 *
 * <p>Each root subtree (fixed first item) is processed as an independent unit so Level 1
 * mining can parallelize per root and merge after join (deterministic — INV-E).
 */
public final class Miner {

    private Miner() {
    }

    /** Mines the subtree rooted at {@code list.get(start)} with an empty prefix. */
    public static Map<String, Pattern> mineRoot(List<DHONode> list, int start,
                                                double f, double epsilon, double minSup, int tl) {
        return process(list, start, new String[0], 0, f, epsilon, minSup, tl);
    }

    private static Map<String, Pattern> process(List<DHONode> list, int start,
                                                String[] prefix, int prefixLen,
                                                double f, double epsilon, double minSup, int tl) {
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

            double dubo = DuboCalculator.dubo(node, f, tl);
            if (dubo < minSup - epsilon) {
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
                results.putAll(process(ncl, 0, items, prefixLen + 1, f, epsilon, minSup, tl));
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