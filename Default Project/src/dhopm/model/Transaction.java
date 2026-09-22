package dhopm.model;

import java.util.List;

/**
 * A single transaction: {@code tid} (1-based order) plus the list of items.
 */
public record Transaction(int tid, List<String> items) {

    public int length() {
        return items.size();
    }
}