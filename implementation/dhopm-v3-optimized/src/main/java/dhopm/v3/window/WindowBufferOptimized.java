package dhopm.v3.window;

import dhopm.common.window.WindowMath;

/**
 * Structure-of-Arrays damped-window buffer (replaces V2 WindowBuffer + Handle objects).
 *
 * <p>Allocates {@code int[W]} ({@code txId}) + {@code byte[W]} ({@code alive}), indexed by
 * {@code slot = tid mod W}; a transaction stays live while {@code alive[slot] == 1 &&
 * txId[slot] == tid}. Eviction just clears {@code alive[slot]} (O(1), no allocation) and must run
 * BEFORE writing {@code tid} (clear-before-write, G2-D4/D5).
 *
 * <p>In paper mode ({@code W = INFINITE}) the buffer is empty and {@link #isAlive} is always
 * {@code true} — identical to V1 (INV-I). Transaction lengths are NOT stored here: nodes keep
 * their own SoA {@code tids[]/lens[]}.
 */
public final class WindowBufferOptimized {

    private final boolean infinite;
    private final int[] txId;
    private final byte[] alive;

    /** @param windowSize {@code W(f,minOcc)} or {@link WindowMath#INFINITE} (paper mode). */
    public WindowBufferOptimized(long windowSize) {
        this.infinite = windowSize == WindowMath.INFINITE;
        if (infinite) {
            this.txId = null;
            this.alive = null;
        } else {
            int w = Math.toIntExact(windowSize);
            this.txId = new int[w];
            this.alive = new byte[w];
        }
    }

    public boolean isInfinite() {
        return infinite;
    }

    /** Number of slots (finite mode only; -1 in paper mode). */
    public int capacity() {
        return infinite ? -1 : alive.length;
    }

    /**
     * Evicts the transaction leaving the window for the current insert: {@code tid - W}.
     *
     * @return true if a live slot was actually cleared (a real eviction)
     */
    public boolean evictTid(int tid) {
        if (infinite) {
            return false;
        }
        int slot = tid % alive.length;
        if (alive[slot] == 1) {
            alive[slot] = 0;
            return true;
        }
        return false;
    }

    /** Binds slot {@code tid mod W} to {@code tid}. Caller must have evicted {@code tid - W} first. */
    public void write(int tid) {
        if (infinite) {
            return;
        }
        int slot = tid % alive.length;
        txId[slot] = tid;
        alive[slot] = 1;
    }

    /** Live ⇔ written with this exact tid and not evicted since (D39); always true in paper mode. */
    public boolean isAlive(int tid) {
        if (infinite) {
            return true;
        }
        int slot = tid % alive.length;
        return alive[slot] == 1 && txId[slot] == tid;
    }

    /** Number of currently live slots (debug/tooling only). */
    public int liveCount() {
        if (infinite) {
            return 0;
        }
        int c = 0;
        for (byte b : alive) {
            if (b == 1) {
                c++;
            }
        }
        return c;
    }
}