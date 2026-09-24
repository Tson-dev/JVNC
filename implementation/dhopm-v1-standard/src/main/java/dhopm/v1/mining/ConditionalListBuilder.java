package dhopm.v1.mining;

import dhopm.v1.metrics.MetricCalculator;
import dhopm.v1.model.DHONode;
import dhopm.v1.model.Entry;

import java.util.List;

/**
 * Builds conditional list nodes by intersecting two nodes' entry sets
 * (two-pointer, both lists ascending in TID — INV-B). The new pattern length is
 * {@code prefixLen + 2} and DO is accumulated during the walk (canonical 2.2 GĐ3).
 *
 * <p>The resulting node keeps the merged TID order (INV-D: no re-sorting).
 */
public final class ConditionalListBuilder {

    private ConditionalListBuilder() {
    }

    /**
     * @param nodeA        pivot node (pattern prefix ∪ {item_i})
     * @param nodeB        partner node (pattern prefix ∪ {item_j})
     * @param newPatternLen  {@code |prefix| + 2} (length of prefix ∪ {item_i,item_j})
     * @return a new node labelled with nodeB's item, DO already accumulated, or
     *         an empty node if the two sets do not share any TID (caller checks support()).
     */
    public static DHONode intersect(DHONode nodeA, DHONode nodeB, int newPatternLen, double f, int tl) {
        DHONode out = new DHONode(nodeB.item());
        List<Entry> ea = nodeA.entries();
        List<Entry> eb = nodeB.entries();
        int i = 0;
        int j = 0;
        while (i < ea.size() && j < eb.size()) {
            Entry a = ea.get(i);
            Entry b = eb.get(j);
            if (a.tid() == b.tid()) {
                out.appendEntry(a.tid(), a.len());
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