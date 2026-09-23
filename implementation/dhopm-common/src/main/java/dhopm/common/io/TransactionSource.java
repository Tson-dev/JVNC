package dhopm.common.io;

import dhopm.common.transaction.Transaction;

import java.util.Iterator;

/**
 * Source of transactions for a stream/batch simulation.
 *
 * <p>Iterating yields transactions with strictly increasing TIDs.
 * Implementations may skip blank lines / comment lines and derive TIDs
 * (FIMI: 1-based transaction counter; text: explicit TID).
 */
public interface TransactionSource extends Iterator<Transaction> {

    /**
     * Loads every remaining transaction into a list, in stream order.
     */
    default java.util.List<Transaction> toList() {
        java.util.List<Transaction> out = new java.util.ArrayList<>();
        while (hasNext()) {
            out.add(next());
        }
        return out;
    }

    /**
     * Remaining (unconsumed) transaction count if cheap to know; otherwise -1.
     */
    default int estimatedRemaining() {
        return -1;
    }
}