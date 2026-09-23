package dhopm.common.transaction;

import java.util.Arrays;

/**
 * One transaction in the stream.
 *
 * <p>Canonical rules (see OVERALL-PLAN 2.1):
 * <ul>
 *   <li>{@code items} are distinct and normalized (sorted by natural order);
 *   <li>an empty transaction is rejected at creation (prevents division by zero);
 *   <li>TIDs increase over the whole stream (validated by the readers / driver).
 * </ul>
 *
 * @param tid    transaction id (strictly increasing over the stream)
 * @param items  distinct items, sorted by natural order
 */
public record Transaction(int tid, String[] items) {

    public Transaction {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("transaction must contain at least one item (tid=" + tid + ")");
        }
        String[] normalized = items.clone();
        Arrays.sort(normalized);
        String[] distinct = new String[normalized.length];
        int n = 0;
        for (String s : normalized) {
            if (n == 0 || !distinct[n - 1].equals(s)) {
                distinct[n++] = s;
            }
        }
        items = n == distinct.length ? distinct : Arrays.copyOf(distinct, n);
    }

    public int length() {
        return items.length;
    }
}