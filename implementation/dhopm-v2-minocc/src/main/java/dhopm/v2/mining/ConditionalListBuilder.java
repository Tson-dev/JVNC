package dhopm.v2.mining;

import dhopm.v2.metrics.MetricCalculator;
import dhopm.v2.dho.DHONode;
import dhopm.v2.dho.Entry;

import java.util.List;

/**
 * Builds conditional list nodes by intersecting two nodes' LIVE entry sets
 * (two-pointer, both lists ascending in TID — INV-B). The new pattern length is
 * {@code prefixLen + 2} and DO is accumulated during the walk (canonical 2.2 GĐ3).
 *
 * <p>Both inputs were already discarded of dead prefixes (reconstruction), so the walk
 * {@code [headA..sizeA) × [headB..sizeB)} visits only transactions still inside the window —
 * the resulting node is created already window-truncated. The merged TID order is preserved
 * (INV-D: no re-sorting). In paper mode head = 0 for both inputs and the walk is identical to V1.
 */
public final class ConditionalListBuilder {

    private ConditionalListBuilder() {
    }

    /**
     * @param nodeA        pivot node (pattern prefix ∪ {item_i})
     * @param nodeB        partner node (pattern prefix ∪ {item_j})
     * @param newPatternLen  {@code |prefix| + 2} (length of prefix ∪ {item_i,item_j})
     * @return a new node labelled with nodeB's item, DO already accumulated, or
     *         an empty node if the two sets do not share any TID (caller checks support())
     */
    public static DHONode intersect(DHONode nodeA, DHONode nodeB, int newPatternLen, double f, int tl) {
        DHONode out = new DHONode(nodeB.item());
        List<Entry> ea = nodeA.entries();
        List<Entry> eb = nodeB.entries();
        int i = nodeA.head();
        int j = nodeB.head();
        while (i < ea.size() && j < eb.size()) {
            Entry a = ea.get(i);
            Entry b = eb.get(j);
            if (a.tid() == b.tid()) {
                out.append(a.ref1(), a.len(), a.tid());
                out.addToDo(MetricCalculator.dampedContribution(newPatternLen, a.len(), f, tl, a.tid()));
                i++;
                j++;
            } else if (a.tid() < b.tid()) {
                i++;
            } else {
                j++;
            }
        }
        return out;
    }
}