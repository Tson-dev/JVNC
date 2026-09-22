package dhopm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A node of a DHO-List (Damped High Occupancy List).
 * <p>
 * For length-1 nodes the {@code name} is the item itself; for derived nodes it
 * is the item appended to a prefix. {@code entries} record every transaction in
 * which the represented pattern occurs, sorted ascending by {@code tid}.
 * {@code doValue} holds the accumulated damped occupancy.
 * <p>
 * Supports the two-pointer pattern extension used during DFS mining.
 */
public final class DhoNode {

    private final List<Entry> entries = new ArrayList<>();
    private final String name;
    private double doValue;

    public DhoNode(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public double doValue() {
        return doValue;
    }

    /** Support equals the number of stored entries. */
    public int support() {
        return entries.size();
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    void add(Entry entry) {
        entries.add(entry);
    }

    void setDo(double value) {
        this.doValue = value;
    }

    /**
     * Builds the node for the pattern "prefix + {this} + {other}" by intersecting
     * this node's entries with {@code other}'s entries on equal {@code tid}.
     * <p>
     * On every common transaction the combined pattern (length {@code patternLen})
     * contributes {@code patternLen / tLen * f^(latestTid - tid)} to the DO.
     *
     * @return the combined node, or {@code null} when the two nodes share no transaction
     */
    public DhoNode mergeWith(DhoNode other, int patternLen, double f, int latestTid) {
        DhoNode merged = new DhoNode(other.name);
        int p = 0;
        int q = 0;
        while (p < entries.size() && q < other.entries.size()) {
            Entry a = entries.get(p);
            Entry b = other.entries.get(q);
            if (a.tid() < b.tid()) {
                p++;
            } else if (b.tid() < a.tid()) {
                q++;
            } else {
                merged.entries.add(a);
                merged.doValue += (patternLen / (double) a.tLen()) * Math.pow(f, latestTid - a.tid());
                p++;
                q++;
            }
        }
        return merged.entries.isEmpty() ? null : merged;
    }
}