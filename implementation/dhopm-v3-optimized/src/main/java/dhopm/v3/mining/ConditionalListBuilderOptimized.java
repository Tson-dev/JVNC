package dhopm.v3.mining;

import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.window.DecayLookup;
import dhopm.v3.window.WindowBufferOptimized;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimized Conditional List Builder (GĐ3 mining).
 * Builds conditional DHO lists for a pattern by intersecting entry lists.
 * Uses two-pointer merge on TID-ordered txSlot arrays.
 */
public final class ConditionalListBuilderOptimized {

    private final WindowBufferOptimized window;
    private final int tl;
    private final double f;
    private final DecayLookup decayLookup;

    public ConditionalListBuilderOptimized(WindowBufferOptimized window, int tl, double f, DecayLookup decayLookup) {
        this.window = window;
        this.tl = tl;
        this.f = f;
        this.decayLookup = decayLookup;
    }

    /**
     * Builds conditional list for a pattern by intersecting the base node with partner nodes.
     * @param baseNode the node to build conditional list for
     * @param partnerNodes list of partner nodes (already filtered by support ≥ minSup)
     * @return list of conditional nodes (each represents base ∪ {partner})
     */
    public List<ConditionalNode> build(DHONodeOptimized baseNode, List<DHONodeOptimized> partnerNodes, double minSup, double epsilonCmp) {
        List<ConditionalNode> result = new ArrayList<>();

        for (DHONodeOptimized partner : partnerNodes) {
            if (partner == baseNode) continue;
            if (partner.support(window) < minSup) continue; // C2 prune

            ConditionalNode cond = intersect(baseNode, partner, minSup, epsilonCmp);
            if (cond != null && cond.node.size > cond.node.head) {
                result.add(cond);
            }
        }
        return result;
    }

    /**
     * Two-pointer intersection of two TID-ordered entry lists.
     * Returns a new conditional node with entries = intersection.
     */
    private ConditionalNode intersect(DHONodeOptimized base, DHONodeOptimized partner, double minSup, double epsilonCmp) {
        DHONodeOptimized condNode = new DHONodeOptimized(partner.itemId);

        int i = base.head;
        int j = partner.head;
        int baseSize = base.size;
        int partnerSize = partner.size;

        while (i < baseSize && j < partnerSize) {
            int baseSlot = base.txSlot[i];
            int partnerSlot = partner.txSlot[j];

            // Skip dead entries
            if (!isLive(baseSlot)) { i++; continue; }
            if (!isLive(partnerSlot)) { j++; continue; }

            int baseTid = window.txId[baseSlot];
            int partnerTid = window.txId[partnerSlot];

            if (baseTid == partnerTid) {
                int txLen = window.txLen[baseSlot];
                condNode.addEntry(baseSlot, txLen);
                i++;
                j++;
            } else if (baseTid < partnerTid) {
                i++;
            } else {
                j++;
            }
        }

        if (condNode.size == 0) {
            return null;
        }

        // Compute DO for the conditional node
        double doValue = 0.0;
        for (int k = 0; k < condNode.size; k++) {
            int slot = condNode.txSlot[k];
            if (isLive(slot)) {
                double decay = window.decay[slot];
                doValue += decay / condNode.ref2[k];
            }
        }
        condNode.doValue = doValue;

        return new ConditionalNode(partner.itemId, condNode, doValue);
    }

    private boolean isLive(int slot) {
        return window.alive[slot] == 1 && window.txId[slot] != 0;
    }

    /** Result of conditional list building: partner itemId + node + DO. */
    public static final class ConditionalNode {
        public final int partnerItemId;
        public final DHONodeOptimized node;
        public final double doValue;

        public ConditionalNode(int partnerItemId, DHONodeOptimized node, double doValue) {
            this.partnerItemId = partnerItemId;
            this.node = node;
            this.doValue = doValue;
        }
    }
}