package dhopm.v3.dho;

import dhopm.v3.window.WindowBufferOptimized;

/**
 * Optimized DHO Node using Structure-of-Arrays for entries.
 * Replaces V2 DHONode + Entry record.
 * - txSlot[]: circular buffer slot index (tid mod W) for each entry
 * - ref2[]: cached |T| (transaction length) for each entry
 * - head: index of first LIVE entry (entries before head are dead/evicted)
 * Entries are appended in TID order (INV-B).
 */
public final class DHONodeOptimized {

    public final int itemId;                    // int item ID (index into dictionary)
    public int[] txSlot;                        // slot indices (tid mod W)
    public int[] ref2;                          // cached |T| for each entry
    public int head = 0;                        // index of first live entry
    public int size = 0;                        // total entries ever added (including dead)
    public double doValue = 0.0;                // computed DO (GĐ2)

    // Growth capacity
    private static final int INITIAL_CAPACITY = 8;
    private static final int GROW_FACTOR = 2;

    public DHONodeOptimized(int itemId) {
        this.itemId = itemId;
        this.txSlot = new int[INITIAL_CAPACITY];
        this.ref2 = new int[INITIAL_CAPACITY];
    }

    /**
     * Appends an entry (called during construction).
     * @param txSlot circular buffer slot (tid mod W)
     * @param txLen  transaction length |T|
     */
    public void addEntry(int txSlot, int txLen) {
        ensureCapacity(size + 1);
        this.txSlot[size] = txSlot;
        this.ref2[size] = txLen;
        size++;
    }

    /** Number of LIVE entries (from head to size-1 that are still alive in window). */
    public int liveSize(WindowBufferOptimized window) {
        int count = 0;
        for (int i = head; i < size; i++) {
            int slot = txSlot[i];
            int tid = window.txId[slot];
            if (window.alive[slot] == 1 && tid == window.txId[slot]) { // window.isAlive(tid)
                count++;
            }
        }
        return count;
    }

    /** Support = number of live entries (O(1) via head). */
    public int support(WindowBufferOptimized window) {
        // Since entries are in TID order and evicted prefix is contiguous,
        // we can advance head to first live entry
        discardDeadPrefix(window);
        return size - head;
    }

    /**
     * Advances head past evicted entries.
     * Called once per node before reconstruction/mining.
     */
    public void discardDeadPrefix(WindowBufferOptimized window) {
        while (head < size) {
            int slot = txSlot[head];
            int tid = window.txId[slot];
            if (window.alive[slot] == 1 && tid == window.txId[slot]) {
                break; // found first live
            }
            head++;
        }
    }

    /** Returns the TID of the first live entry, or -1 if none. */
    public int firstLiveTid(WindowBufferOptimized window) {
        discardDeadPrefix(window);
        if (head >= size) return -1;
        int slot = txSlot[head];
        return window.txId[slot];
    }

    /** Gets the transaction length |T| for the entry at index i. */
    public int getTxLen(int i) {
        return ref2[i];
    }

    /** Gets the slot for the entry at index i. */
    public int getTxSlot(int i) {
        return txSlot[i];
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > txSlot.length) {
            int newCap = Math.max(txSlot.length * GROW_FACTOR, minCapacity);
            txSlot = java.util.Arrays.copyOf(txSlot, newCap);
            ref2 = java.util.Arrays.copyOf(ref2, newCap);
        }
    }

    /** Resets the node for reuse (clears entries, keeps arrays). */
    public void reset() {
        head = 0;
        size = 0;
        doValue = 0.0;
    }
}