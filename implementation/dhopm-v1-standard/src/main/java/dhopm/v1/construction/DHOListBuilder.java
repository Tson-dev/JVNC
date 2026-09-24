package dhopm.v1.construction;

import dhopm.v1.model.DHOList;
import dhopm.v1.model.DHONode;
import dhopm.common.transaction.Transaction;

/**
 * Gia đoạn 1 (GĐ1): builds the global DHO-List by a single scan.
 *
 * <p>Single-threaded (Level 1) to avoid races on the global map and keep INV-B
 * (entries appended in TID order). TIDs are validated strictly increasing (INV-A).
 */
public final class DHOListBuilder {

    private DHOListBuilder() {
    }

    /**
     * Appends one transaction to the global list. Item order inside the transaction
     * follows the normalized {@link Transaction#items()} order (distinct &amp; sorted).
     */
    public static void add(Transaction t, DHOList global) {
        for (String item : t.items()) {
            DHONode node = global.createOrGet(item);
            node.appendEntry(t.tid(), t.length());
        }
    }
}