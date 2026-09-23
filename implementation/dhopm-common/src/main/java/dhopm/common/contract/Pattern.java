package dhopm.common.contract;

import java.util.Arrays;

/**
 * A mined damped occupancy pattern (result of the algorithm).
 *
 * @param items            items of the pattern, normalized order (canonical C6 for display/comparison)
 * @param dampedOccupancy  damped occupancy value DO at the moment of mining
 * @param tids             transactions containing the pattern (TIDs, ascending; may be empty when not needed)
 */
public record Pattern(String[] items, double dampedOccupancy, int[] tids) {

    /** Canonical key for set comparison: items sorted by natural order joined by comma (C6). */
    public String canonicalKey() {
        String[] sorted = items.clone();
        Arrays.sort(sorted);
        return String.join(",", sorted);
    }

    @Override
    public String toString() {
        return canonicalKey() + "=" + String.format("%.4f", dampedOccupancy);
    }
}