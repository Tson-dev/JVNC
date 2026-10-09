package dhopm.v2.window;

import java.util.Arrays;

/**
 * The damped window: a circular array of {@code W = W(f,minOcc)} handle slots indexed by
 * {@code slot = tid mod W} (canonical 2.4 / G2-D5). The window is unused when {@code W = ∞}
 * (minOcc = 0 or f = 1); engines then never touch this buffer and {@code ref1 = null} entries run the
 * exact V1 pipeline (INV-I).
 *
 * <p>Eviction before write (G2-D4, G2-D5): slot {@code m−W} is cleared before slot {@code m} is
 * written, so a written transaction is never immediately evicted and the {@code clear-before-write}
 * invariant holds for every slot.
 */
public final class WindowBuffer {

    private final Handle[] slots;

    /** @param windowSize {@code W(f,minOcc)} — must be finite (caller guarantees). */
    public WindowBuffer(long windowSize) {
        this.slots = new Handle[Math.toIntExact(windowSize)];
    }

    public int size() {
        return slots.length;
    }

    /**
     * Handle for TID {@code tid} (created lazily). Live only if {@link #write(int, Transaction)} has
     * been called with that same tid and it was not evicted since (D39).
     */
    public Handle handle(int tid) {
        Handle h = slots[tid % slots.length];
        if (h == null) {
            h = new Handle();
            slots[tid % slots.length] = h;
        }
        return h;
    }

    /**
     * Binds the slot of {@code tid} to the transaction (GĐ0). Must run AFTER the victim
     * {@code tid − W} was evicted (clear-before-write).
     */
    public void write(int tid, dhopm.common.transaction.Transaction tx) {
        Handle h = handle(tid);
        h.tid = tid;
        h.len = tx.length();
        h.tx = tx;
    }

    /**
     * Clears the payload of TID {@code tid}.
     *
     * @return {@code true} if a live handle was actually cleared (a real eviction)
     */
    public boolean evict(int tid) {
        Handle h = slots[tid % slots.length];
        if (h != null && h.tx != null) {
            h.tx = null;
            return true;
        }
        return false;
    }

    /** Dump for the debug/CLI tools (slot → live tid). */
    public String describe() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            Handle h = slots[i];
            if (h != null) {
                out.add(i + ":" + (h.tx != null ? h.tid + "*" : h.tid));
            }
        }
        return Arrays.toString(out.toArray());
    }
}