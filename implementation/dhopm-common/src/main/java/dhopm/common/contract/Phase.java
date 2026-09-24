package dhopm.common.contract;

/**
 * The three pipeline stages (OVERALL-PLAN 2.2).
 */
public enum Phase {
    /** Construction / incremental update of the global DHO-List (one scan). */
    CONSTRUCTION,
    /** Reconstruction: recompute DO + stable sort by support. */
    RECONSTRUCTION,
    /** Mining: DFS pattern expansion over roots / subtrees. */
    MINING
}