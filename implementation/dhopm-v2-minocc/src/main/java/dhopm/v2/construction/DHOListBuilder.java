package dhopm.v2.construction;

import dhopm.common.transaction.Transaction;
import dhopm.v2.dho.DHOList;
import dhopm.v2.dho.DHONode;
import dhopm.v2.window.Handle;
import dhopm.v2.window.WindowBuffer;

/**
 * Giai đoạn 1 (GĐ1): builds the global DHO-List by a single scan (canonical 2.2).
 *
 * <p><b>Finite window:</b> the engine has already performed GĐ0 (evict {@code m−W} then write the
 * handle of {@code m}) for every scanning transaction; this builder only links the node entry to
 * that stable handle via {@code ref1}. <b>Paper mode (minOcc = 0):</b> {@link #addNoWindow} apends
 * {@code ref1 = null} entries that reproduce the V1 pipeline exactly (INV-I).
 *
 * <p>Single-threaded (Level 1) to avoid races on the global map and keep INV-B
 * (entries appended in TID order). TIDs are validated strictly increasing (INV-A) by the engine.
 */
public final class DHOListBuilder {

    private DHOListBuilder() {
    }

    /** Finite-window append: entry points at the engine-written handle slot of {@code t}. */
    public static void add(Transaction t, DHOList global, WindowBuffer buffer) {
        for (String item : t.items()) {
            DHONode node = global.createOrGet(item);
            Handle h = buffer.handle(t.tid());
            node.append(h, t.length(), t.tid());
        }
    }

    /** Paper-mode append (minOcc = 0): {@code ref1 = null} entries — bit-for-bit V1 path. */
    public static void addNoWindow(Transaction t, DHOList global) {
        for (String item : t.items()) {
            DHONode node = global.createOrGet(item);
            node.append(null, t.length(), t.tid());
        }
    }
}