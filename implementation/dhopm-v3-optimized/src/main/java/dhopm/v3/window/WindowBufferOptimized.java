package dhopm.v3.window;

import dhopm.common.window.WindowMath;

/**
 * Structure-of-Arrays circular window buffer (replaces V2 Handle + WindowBuffer).
 * All arrays sized to W = window size; index = tid mod W.
 * Evict = set alive[slot] = 0 (O(1), no object allocation).
 * No Handle objects; Entry stores only int txSlot.
 */
public final class WindowBufferOptimized {

    public final int windowSize;           // W(f, minOcc)
    public final int[] txId;               // transaction ID at each slot
    public final short[] txLen;            // |T| (transaction length) at each slot
    public final byte[] alive;             // 1 = live, 0 = evicted
    public final double[] decay;           // f^(TL - tid) for each slot (updated each mineNow)

    private int lastTid = 0;

    public WindowBufferOptimized(int windowSize) {
        this.windowSize = windowSize;
        this.txId = new int[windowSize];
        this.txLen = new short[windowSize];
        this.alive = new byte[windowSize];
        this.decay = new double[windowSize];
    }

    /**
     * Evicts the transaction that is exactly W steps before the given tid.
     * Called before writing the new transaction at tid.
     * @return true if a slot was actually evicted (was alive)
     */
    public boolean evictBeforeWrite(int tid) {
        int evictTid = tid - windowSize;
        if (evictTid <= 0) return false;
        int slot = evictTid % windowSize;
        if (alive[slot] == 1) {
            alive[slot] = 0;
            return true;
        }
        return false;
    }

    /**
     * Writes a transaction into the buffer at the given tid.
     * @return the slot index (tid mod W)
     */
    public int write(int tid, int txLen) {
        int slot = tid % windowSize;
        txId[slot] = tid;
        this.txLen[slot] = (short) txLen;
        alive[slot] = 1;
        lastTid = tid;
        return slot;
    }

    /**
     * Checks if the slot for a given tid is still alive (not evicted).
     * Used by Entry.isLive() equivalent check.
     */
    public boolean isAlive(int tid) {
        int slot = tid % windowSize;
        return alive[slot] == 1 && txId[slot] == tid;
    }

    /** Gets the transaction length |T| for a live slot. */
    public int getTxLen(int tid) {
        int slot = tid % windowSize;
        return txLen[slot];
    }

    /** Gets the precomputed decay value for a live slot. */
    public double getDecay(int tid) {
        int slot = tid % windowSize;
        return decay[slot];
    }

    /**
     * Updates all decay values for the current TL (total transactions loaded).
     * Called once per mineNow before reconstruction.
     * decay[slot] = f^(TL - txId[slot]) for alive slots.
     */
    public void updateDecay(double f, int tl) {
        if (f == 1.0) {
            // f=1 ⇒ all decay = 1
            for (int i = 0; i < windowSize; i++) {
                if (alive[i] == 1) decay[i] = 1.0;
            }
            return;
        }
        for (int i = 0; i < windowSize; i++) {
            if (alive[i] == 1) {
                int age = tl - txId[i];  // age ∈ [0, W-1]
                decay[i] = Math.pow(f, age);
            }
        }
    }

    /** Returns the number of live entries in the window. */
    public int liveCount() {
        int count = 0;
        for (byte b : alive) if (b == 1) count++;
        return count;
    }

    /** Returns the last TID written. */
    public int getLastTid() {
        return lastTid;
    }

    /** Resets the buffer (for reuse across datasets). */
    public void reset() {
        java.util.Arrays.fill(alive, (byte) 0);
        lastTid = 0;
    }
}