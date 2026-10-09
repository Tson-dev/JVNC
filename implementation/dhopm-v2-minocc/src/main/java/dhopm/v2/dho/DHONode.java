package dhopm.v2.dho;

import dhopm.v2.window.Handle;

import java.util.ArrayList;
import java.util.List;

/**
 * A DHO-List node (global or conditional). Stores the last item of the pattern, the entry set
 * {@code (ref1, len, tid)} — appended in TID order (INV-B) — a window-truncation head offset and
 * the accumulated DO value.
 *
 * <p><b>Dead-prefix invariant (D39):</b> with a finite window the dead entries of any node always
 * form a prefix of {@link #entries()} (eviction walks TIDs upward, so once an entry is dead every
 * older entry is dead too). {@link #head()} is advanced past that prefix once per mineNow by the
 * reconstructor; the live entries are {@code [head(), size())}, hence
 * {@code support() = size() − head()}.
 */
public final class DHONode {

    private final String item;
    private final List<Entry> entries = new ArrayList<>();
    private int head;
    private double doValue;

    public DHONode(String item) {
        this.item = item;
    }

    /** Test/verification factory: builds a node with pre-ordered paper-mode entries. */
    public static DHONode of(String item, Entry[] entries) {
        DHONode n = new DHONode(item);
        for (Entry e : entries) {
            n.append(e.ref1(), e.len(), e.tid());
        }
        return n;
    }

    public String item() {
        return item;
    }

    /** Full entry list (dead prefix included). Consumers must iterate from {@link #head()}. */
    public List<Entry> entries() {
        return entries;
    }

    /** Index of the first live entry (0 in paper mode). */
    public int head() {
        return head;
    }

    /** Live entry count (C2 support). */
    public int support() {
        return entries.size() - head;
    }

    public double doValue() {
        return doValue;
    }

    public void append(Handle ref1, int len, int tid) {
        entries.add(new Entry(ref1, len, tid));
    }

    public void resetDo() {
        doValue = 0.0;
    }

    public void addToDo(double delta) {
        doValue += delta;
    }

    /** Advances {@link #head()} past the dead prefix; idempotent, O(k) with k = dead entries. */
    public void discardDeadPrefix() {
        while (head < entries.size() && !entries.get(head).isLive()) {
            head++;
        }
    }

    /** TIDs where this node occurs, ascending (live entries only — derived on demand). */
    public int[] tids() {
        int[] tids = new int[entries.size() - head];
        for (int i = head; i < entries.size(); i++) {
            tids[i - head] = entries.get(i).tid();
        }
        return tids;
    }

    @Override
    public String toString() {
        return item + "[" + support() + ",DO=" + doValue + (head > 0 ? " head=" + head : "") + "]";
    }
}