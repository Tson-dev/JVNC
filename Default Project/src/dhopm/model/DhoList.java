package dhopm.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection of {@link DhoNode}s. One instance plays the role of the global
 * DHO-List (built by a single scan and once reconstructed) and another the role
 * of every conditional DHO-List created during DFS mining.
 * <p>
 * Reconstruction computes, for each length-1 node, the damped occupancy
 * {@code sum(1/|T| * f^(latestTid - tid))} and then sorts nodes ascending by
 * support so that the most prunable items are visited first.
 */
public final class DhoList {

    private final List<DhoNode> nodes = new ArrayList<>();
    private final Map<String, DhoNode> byName = new LinkedHashMap<>();

    /** Construction/update step: add one occurrence of {@code item} in a transaction. */
    public void addEntry(int tid, String item, int tLen) {
        DhoNode node = byName.get(item);
        if (node == null) {
            node = new DhoNode(item);
            byName.put(item, node);
            nodes.add(node);
        }
        node.add(new Entry(tid, tLen));
    }

    /** Adds an already-built node (used when assembling conditional lists). */
    public void add(DhoNode node) {
        nodes.add(node);
        byName.putIfAbsent(node.name(), node);
    }

    /**
     * Reconstruction sub-procedure: recompute DO for every node (single-item
     * occupancy {@code 1/|T|}) and re-sort the list ascending by support.
     */
    public void reconstruct(double f, int latestTid) {
        for (DhoNode node : nodes) {
            double doValue = 0.0;
            for (Entry e : node.entries()) {
                doValue += Math.pow(f, latestTid - e.tid()) / e.tLen();
            }
            node.setDo(doValue);
        }
        nodes.sort(Comparator.comparingInt(DhoNode::support));
    }

    public DhoNode node(int index) {
        return nodes.get(index);
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}