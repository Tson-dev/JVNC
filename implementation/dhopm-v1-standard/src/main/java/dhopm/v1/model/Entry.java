package dhopm.v1.model;

/**
 * One occurrence of a node (item / pattern) inside a transaction.
 * Entries of a node are appended in TID order (INV-B), {@code tid} is the transaction id
 * and {@code len} is {@code |T|}, the distinct-item length of that transaction.
 */
public record Entry(int tid, int len) {
}