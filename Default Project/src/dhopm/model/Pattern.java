package dhopm.model;

import java.util.List;

/**
 * A mined Damped High Occupancy Pattern: the item sequence plus its damped
 * occupancy value.
 */
public record Pattern(List<String> items, double doValue) {

    /** Concatenated display name, following the paper's convention (e.g. "AE"). */
    public String displayName() {
        return String.join("", items);
    }
}