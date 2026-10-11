package dhopm.v3.mining;

import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.window.DecayLookup;

/**
 * Builds a conditional-list node by intersecting two nodes' live entry sets (two-pointer over the
 * SoA arrays, both ascending in TID — INV-B). The new pattern length is passed by the caller and
 * DO is accumulated during the walk with the exact V1/V2 formula
 * {@code (newPatternLen / |T|) * f^(TL − Td)} (canonical 2.2 GĐ3).
 */
public final class ConditionalListBuilderOptimized {

    private ConditionalListBuilderOptimized() {
    }

    /**
     * @param nodeA        pivot node (pattern {@code prefix ∪ {item_i}})
     * @param nodeB        partner node (pattern {@code prefix ∪ {item_j}})
     * @param newPatternLen  length of {@code prefix ∪ {item_i, item_j}}
     * @param decayLookup  decay lookup bit-identical to {@code Math.pow(f, age)}
     * @param tl           current TL
     * @return a new node labelled with nodeB's item (DO already accumulated), or an empty node
     *         when the two sets share no live TID (caller checks {@code size()}).
     */
    public static DHONodeOptimized intersect(DHONodeOptimized nodeA, DHONodeOptimized nodeB,
                                             int newPatternLen, DecayLookup decayLookup, int tl) {
        DHONodeOptimized out = new DHONodeOptimized(nodeB.itemId);
        int i = nodeA.head();
        int j = nodeB.head();
        int endA = nodeA.size();
        int endB = nodeB.size();
        while (i < endA && j < endB) {
            int tidA = nodeA.tidAt(i);
            int tidB = nodeB.tidAt(j);
            if (tidA == tidB) {
                int len = nodeA.lenAt(i);
                out.addEntry(tidA, len);
                out.setDo(out.doValue() + (newPatternLen / (double) len) * decayLookup.get(tl - tidA));
                i++;
                j++;
            } else if (tidA < tidB) {
                i++;
            } else {
                j++;
            }
        }
        return out;
    }
}