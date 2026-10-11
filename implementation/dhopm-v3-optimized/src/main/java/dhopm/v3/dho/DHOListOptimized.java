package dhopm.v3.dho;

import java.util.ArrayList;
import java.util.List;

/**
 * DHO-List: growable array of nodes indexed by {@code itemId} plus the creation order (first
 * appearance of each item) used for the stable sort (C3) and deterministic iteration.
 */
public final class DHOListOptimized {

    private DHONodeOptimized[] nodes = new DHONodeOptimized[64];
    private final List<Integer> creationOrder = new ArrayList<>();

    /** Gets or creates a node for the given itemId (creation order = first-appearance order). */
    public DHONodeOptimized getOrCreateNode(int itemId) {
        if (itemId >= nodes.length) {
            nodes = java.util.Arrays.copyOf(nodes, Math.max(nodes.length * 2, itemId + 1));
        }
        DHONodeOptimized node = nodes[itemId];
        if (node == null) {
            node = new DHONodeOptimized(itemId);
            nodes[itemId] = node;
            creationOrder.add(itemId);
        }
        return node;
    }

    /** Gets the node for an itemId, or null if not present. */
    public DHONodeOptimized getNode(int itemId) {
        return (itemId >= 0 && itemId < nodes.length) ? nodes[itemId] : null;
    }

    /** Snapshot of all nodes in creation order (for the reconstructor / stats). */
    public List<DHONodeOptimized> nodesList() {
        List<DHONodeOptimized> out = new ArrayList<>(creationOrder.size());
        for (int id : creationOrder) {
            out.add(nodes[id]);
        }
        return out;
    }

    /** Total number of nodes created. */
    public int nodeCount() {
        return creationOrder.size();
    }
}