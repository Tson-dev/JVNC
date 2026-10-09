package dhopm.v2.window;

import dhopm.common.transaction.Transaction;

/**
 * Two-level reference (G2-D13): a stable, {@code TID-sized} slot handle. The handle itself is
 * the second reference (classical {@code ref2}); the first reference chain is
 * {@code Node.alive} → {@link dhopm.v2.dho.Entry} → {@code Handle} → {@link Transaction}.
 *
 * <p>Only the payload is cleared on eviction ({@code tx = null}) — the identity of the handle is
 * stable for its slot (INV-H), so an {@link dhopm.v2.dho.Entry} can test liveness purely by
 * comparing {@code handle.tid == entry.tid()} (D39: slot-reuse detection).
 *
 * <p>Package is {@code dhopm.v2.window} per plan 02 §5.7.
 */
public final class Handle {

    /** TID of the transaction currently bound to this slot (last written). */
    public int tid;

    /** Distinct-item length of that transaction. */
    public int len;

    /** Payload; {@code null} ⇔ the slot is evicted / not yet written (INV-H). */
    public Transaction tx;

    /** O(1) liveness check (D39) — a live slot must still carry its transaction. */
    public boolean live() {
        return tx != null;
    }

    @Override
    public String toString() {
        return "Handle{slot=" + tid + ", live=" + (tx != null) + "}";
    }
}