package org.jvnc.dhopm.v2;

import java.util.List;

public record Transaction(int tid, List<String> items) {
    public Transaction {
        if (tid < 1) {
            throw new IllegalArgumentException("TID must be positive.");
        }
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("A transaction must contain at least one item.");
        }
    }
}
