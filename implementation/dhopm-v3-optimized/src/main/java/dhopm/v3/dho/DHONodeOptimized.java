package dhopm.v3.dho;

import dhopm.v3.window.WindowBufferOptimized;

/**
 * SoA DHO node (replaces V2 DHONode + Entry). Entries are two parallel arrays
 * {@code tids[] / lens[]}, appended in TID order (INV-B). Each occurrence stores its real TID
 * (not a buffer slot — fixes the old slot-reuse bug D39) and {@link #discardDeadPrefix} advances
 * {@code head} past the dead prefix once per mineNow, so {@code support() = size - head} is O(1)
 * and the live region is {@code [head, size)}.
 */
public final class DHONodeOptimized {

    private static final int INITIAL_CAPACITY = 8;
    private static final int GROW_FACTOR = 2;

    public final int itemId;
    private int[] tids;
    private int[] lens;
    private int head;
    private int size;
    private double doValue;

    public DHONodeOptimized(int itemId) {
        this.itemId = itemId;
        this.tids = new int[INITIAL_CAPACITY];
        this.lens = new int[INITIAL_CAPACITY];
    }

    /** Appends one occurrence {@code (tid, |T|)}; TIDs must arrive ascending (INV-B). */
    public void addEntry(int tid, int len) {
        if (size == tids.length) {
            int newCap = Math.max(tids.length * GROW_FACTOR, size + 1);
            tids = java.util.Arrays.copyOf(tids, newCap);
            lens = java.util.Arrays.copyOf(lens, newCap);
        }
        tids[size] = tid;
        lens[size] = len;
        size++;
    }

    /** Live entry count (C2 support). */
    public int support() {
        return size - head;
    }

    /** Index of the first live entry (0 in paper mode). */
    public int head() {
        return head;
    }

    /** Total entries ever appended (dead prefix included). */
    public int size() {
        return size;
    }

    public int tidAt(int i) {
        return tids[i];
    }

    public int lenAt(int i) {
        return lens[i];
    }

    public double doValue() {
        return doValue;
    }

    public void setDo(double value) {
        this.doValue = value;
    }

    /** Advances {@code head} past dead (evicted) entries; idempotent, O(k) with k = dead entries. */
    public void discardDeadPrefix(WindowBufferOptimized window) {
        while (head < size && !window.isAlive(tids[head])) {
            head++;
        }
    }
}