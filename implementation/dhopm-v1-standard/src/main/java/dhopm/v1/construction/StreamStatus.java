package dhopm.v1.construction;

/**
 * Stream status: total transactions scanned so far and the latest TID (TL).
 * Computes {@code minSup = partial × total} at mining time (canonical 2.1).
 */
public record StreamStatus(long totalTransactions, int lastTid) {

    public double minSup(double partial) {
        return partial * totalTransactions;
    }
}