package dhopm.common.contract;

/**
 * Summary of one engine run for benchmarking / the compare app.
 *
 * @param engineName        engine id
 * @param constructionMs    wall ms of the construction (load) phase
 * @param reconstructionMs  wall ms of the reconstruction phase (-1 if not measured)
 * @param miningMs          wall ms of the mining phase (-1 if not measured)
 * @param patternCount      number of returned DOP patterns
 * @param lastTid           latest scanned TID (TL)
 * @param minSup            minimum support used for this run
 * @param heapBytes         highest observed heap when measuring
 */
public record RunMetrics(String engineName, long constructionMs, long reconstructionMs, long miningMs,
                         int patternCount, int lastTid, double minSup, long heapBytes) {

    public String csvHeader() {
        return "engine,construction_ms,reconstruction_ms,mining_ms,patterns,last_tid,min_sup,heap_bytes";
    }

    public String csvRow() {
        return engineName + "," + constructionMs + "," + reconstructionMs + "," + miningMs + ","
                + patternCount + "," + lastTid + "," + minSup + "," + heapBytes;
    }
}