package dhopm.v1.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A DHO-List node (global or conditional). Stores the last item of the pattern
 * (or the item itself for length-1 patterns), the entry set {@code <TID, |T|>}
 * (always appended in TID order — INV-B) and the accumulated DO value.
 *
 * <p>DO is mutated only by the reconstructor (global nodes) or at construction
 * of a conditional node (two-pointer). Per C5 a node is always accumulated
 * sequentially on a single thread.
 */
public final class DHONode {

    private final String item;
    private final List<Entry> entries = new ArrayList<>();
    private double doValue;

    public DHONode(String item) {
        this.item = item;
    }

    /** Test/verification factory: builds a node with pre-ordered entries. */
    public static DHONode of(String item, Entry[] entries) {
        DHONode n = new DHONode(item);
        for (Entry e : entries) {
            n.appendEntry(e.tid(), e.len());
        }
        return n;
    }

    public String item() {
        return item;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int support() {
        return entries.size();
    }

    public double doValue() {
        return doValue;
    }

    public void appendEntry(int tid, int len) {
        entries.add(new Entry(tid, len));
    }

    public void resetDo() {
        doValue = 0.0;
    }

    public void addToDo(double delta) {
        doValue += delta;
    }

    /** TIDs where this node occurs, ascending (derived from the entry set). */
    public int[] tids() {
        int[] tids = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            tids[i] = entries.get(i).tid();
        }
        return tids;
    }

    @Override
    public String toString() {
        return item + "[" + support() + ",DO=" + doValue + "]";
    }
}