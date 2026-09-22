package dhopm.io;

import dhopm.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses textual input into transactions. Every non-blank line is a transaction
 * whose items are separated by whitespace; TIDs are assigned sequentially
 * (1-based) in line order.
 */
public final class TransactionReader {

    private TransactionReader() {
    }

    public static List<Transaction> read(String text) {
        List<Transaction> transactions = new ArrayList<>();
        int tid = 1;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            List<String> items = List.of(line.trim().split("\\s+"));
            if (!items.isEmpty()) {
                transactions.add(new Transaction(tid++, items));
            }
        }
        return transactions;
    }
}