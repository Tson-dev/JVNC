package dhopm.common.contract;

/**
 * One mining-progress tick observed via {@link MiningProgressListener}.
 * Coarse-grained (per completed root-subtree) — NOT fine-grained per node,
 * exactly to keep progress reporting off the mining hot path (plan 00 §4.2 P3).
 *
 * @param completedRootTasks number of root-subtree tasks finished in this mineNow() call
 * @param totalRootTasks     total root-subtree tasks scheduled in this mineNow() call
 * @param patternsFound      patterns accumulated from the completed tasks so far
 * @param elapsedMs          wall-clock ms since this mineNow() mining phase started
 */
public record MiningProgress(long completedRootTasks, long totalRootTasks, int patternsFound, long elapsedMs) {

    public double fraction() {
        return totalRootTasks == 0 ? 1.0 : completedRootTasks / (double) totalRootTasks;
    }
}