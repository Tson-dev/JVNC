package dhopm.algo;

import dhopm.model.DhoNode;

/**
 * Strategy for over-estimating the (damped) occupancy of any super-pattern of X.
 * The upper bound must satisfy the anti-monotone property: whenever it drops
 * below {@code minSup}, no super-pattern of X can be a DOP, so the whole subtree
 * can be pruned safely.
 */
@FunctionalInterface
public interface UpperBoundCalculator {

    /**
     * Computes the damped upper bound for the pattern represented by {@code node}.
     */
    double upperBound(DhoNode node, double f, int latestTid);
}