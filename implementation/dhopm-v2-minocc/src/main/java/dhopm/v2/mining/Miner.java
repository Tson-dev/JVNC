package dhopm.v2.mining;

import dhopm.common.contract.MiningEvent;
import dhopm.common.contract.MiningEventListener;
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
     * @param listener        optional real-time event sink ({@code null} = no streaming)
     * @param detail          step detail level: {@code < 0} = PATTERN only, {@code 0..2} = contract 6.4
     */
    public static Map<String, Pattern> mineRoot(List<DHONode> list, int start,
                                                double f, double epsilon, double minSup, int tl,
                                                boolean windowTruncated,
                                                MiningEventListener listener, int detail) {
        return process(list, start, new String[0], 0, f, epsilon, minSup, tl, windowTruncated, listener, detail);
    }

    private static Map<String, Pattern> process(List<DHONode> list, int start,
                                                String[] prefix, int prefixLen,
                                                double f, double epsilon, double minSup, int tl,
                                                boolean windowTruncated,
                                                MiningEventListener listener, int detail) {
        Map<String, Pattern> results = new LinkedHashMap<>();
        for (int i = start; i < list.size(); i++) {
            DHONode node = list.get(i);
            if (node.support() < minSup) {
                continue; // C2: skip entirely (no DOP check, no expansion)
            }

            String[] items = extend(prefix, prefixLen, node.item());
            String key = canonicalKey(items);
            double doValue = node.doValue();
            boolean dopPassed = doValue >= minSup - epsilon; // C4
            if (dopPassed) {
                int[] tids = node.tids();
                if (results.putIfAbsent(key, new Pattern(items, doValue, tids)) == null && listener != null) {
                    listener.onEvent(MiningEvent.pattern(key, doValue, node.support(), tids));
                }
            }

            DuboBound bound = DuboBound.compute(node, f, tl);
            double ub = windowTruncated ? Math.min(bound.dubo(), bound.zX()) : bound.dubo();
            boolean ubPassed = ub >= minSup - epsilon;
            emit(listener, detail, key, doValue, dopPassed, ub, bound.zX(), minSup, ubPassed);
            if (!ubPassed) {
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
                results.putAll(process(ncl, 0, items, prefixLen + 1, f, epsilon, minSup, tl, windowTruncated,
                        listener, detail));
            }
        }
        return results;
    }

    /**
     * Emits step events (contract 6.4) when a detail level is requested:
     * {@code 2} = accepted DOP, {@code 1} = + accepted prune, {@code 0} = + rejected cases.
     */
    private static void emit(MiningEventListener listener, int detail, String key,
                             double doValue, boolean dopPassed, double ubdo, double z,
                             double minSup, boolean ubPassed) {
        if (listener == null || detail < 0) {
            return;
        }
        if (dopPassed || detail == 0) {
            listener.onEvent(MiningEvent.dopCheck(key, doValue, minSup, dopPassed));
        }
        if (detail <= 1 && (ubPassed || detail == 0)) {
            listener.onEvent(MiningEvent.ubdo(key, ubdo, z, minSup, ubPassed));
        }
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