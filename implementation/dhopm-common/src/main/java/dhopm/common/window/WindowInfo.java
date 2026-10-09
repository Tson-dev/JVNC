package dhopm.common.window;

/**
 * Observable window state of one {@code mineNow()} run (NFR-N8 / plan 02 §5.3).
 *
 * @param windowSize           {@code W(f,minOcc)}; {@link WindowMath#INFINITE} = no window (minOcc=0 or f=1)
 * @param effectiveTransactions {@code N_eff = min(TL, W)} used for minSup
 * @param minSup               effective minimum support at mining time (two-phase, INV-F)
 * @param maxDO                {@code Z(f,TL)} — physical ceiling of every DO (short-circuit bound)
 * @param evictions            total number of evicted transactions (GĐ0) across the whole engine lifetime
 * @param liveEntries          total live entries across all global nodes at mining time
 * @param deadEntries          total dead (prefix) entries across all global nodes at mining time
 */
public record WindowInfo(long windowSize, long effectiveTransactions, double minSup, double maxDO,
                         long evictions, long liveEntries, long deadEntries) {

    /** Window inactive ⇔ minOcc = 0 or f = 1 (paper mode). */
    public boolean isInfinite() {
        return windowSize == WindowMath.INFINITE;
    }

    /** Empty/not-yet-computed marker. */
    public static WindowInfo none() {
        return new WindowInfo(WindowMath.INFINITE, 0, 0.0, 0.0, 0, 0, 0);
    }
}