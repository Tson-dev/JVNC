package dhopm.model;

/**
 * Immutable reference to one occurrence of an item/pattern:
 * the transaction id {@code tid} and its length {@code tLen}.
 */
public record Entry(int tid, int tLen) {
}