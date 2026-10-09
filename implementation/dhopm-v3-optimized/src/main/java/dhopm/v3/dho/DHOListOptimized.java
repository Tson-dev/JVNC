package dhopm.v3.dho;

import dhopm.v3.window.WindowBufferOptimized;

/**
 * Optimized DHO-List: growable array of nodes indexed by itemId.
 * Replaces V2 LinkedHashMap<Item, DHONode>.
 * - nodes[itemId] = DHONodeOptimized (null if item not seen)
 * - creationOrder: list of itemIds in order of first appearance (for stable sort C3)
 */
public final class DHOListOptimized {

    private DHONodeOptimized[] nodes = new DHONodeOptimized[64];
    private final java.util.ArrayList<Integer> creationOrder = new java.util.ArrayList<>();

    /** Gets or creates a node for the given itemId. */
    public DHONodeOptimized getOrCreateNode(int itemId) {
        if (itemId >= nodes.length) {
            int newLen = Math.max(nodes.length * 2, itemId + 1);
            nodes = java.util.Arrays.copyOf(nodes, newLen);
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

    /** Returns nodes in creation order (for stable sort by support C3). */
    public Iterable<DHONodeOptimized> nodesInCreationOrder() {
        return () -> new java.util.Iterator<>() {
            int idx = 0;
            public boolean hasNext() { return idx < creationOrder.size(); }
            public DHONodeOptimized next() { return nodes[creationOrder.get(idx++)]; }
        };
    }

    /** Total number of nodes created. */
    public int nodeCount() {
        return creationOrder.size();
    }

    /** Discards dead prefix for all nodes (call before reconstruction). */
    public void discardAllDeadPrefixes(WindowBufferOptimized window) {
        for (int itemId : creationOrder) {
            nodes[itemId].discardDeadPrefix(window);
        }
    }

    /** Resets all nodes for reuse across datasets. */
    public void reset() {
        for (int itemId : creationOrder) {
            nodes[itemId].reset();
        }
        creationOrder.clear();
    }
}