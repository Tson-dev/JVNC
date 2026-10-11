package dhopm.common.contract;

/**
 * One fine-grained mining observation emitted via {@link MiningEventListener} for real-time
 * tooling (CLI {@code stream}, FE mining screen). Unlike {@link MiningProgress} (coarse,
 * one tick per completed root), these events describe individual decisions inside the DFS:
 *
 * <ul>
 *   <li>{@link Kind#PATTERN} — a DOP pattern was accepted (C4). Carries {@code doValue},
 *       {@code support} and {@code tids}; emitted in real time so the UI can show each pattern
 *       the moment it is reached.</li>
 *   <li>{@link Kind#DOP_CHECK} — the {@code DO ≥ minSup − ε} test for a candidate pattern
 *       (contract 6.4 {@code --detail 2}); {@code passed} tells whether it was accepted.</li>
 *   <li>{@link Kind#UBDO} — the prune test {@code UB'(X) ≥ minSup − ε} for a prefix
 *       (contract 6.4 {@code --detail 1/0}); {@code ubdo}/{@code z} report the bounds.</li>
 * </ul>
 *
 * <p>Step events (DOP_CHECK/UBDO) are only emitted when a detail level is requested; PATTERN
 * events are always emitted when a listener is set. Enabling the listener MUST NOT change the
 * mined result (INV-E) and MUST be zero-cost when the listener is {@code null}.
 *
 * @param kind     event kind (never {@code null})
 * @param key      canonical pattern key (items sorted by natural order, joined by comma — C6)
 * @param passed   whether the described test passed (always {@code true} for {@link Kind#PATTERN})
 * @param doValue  damped occupancy (PATTERN / DOP_CHECK)
 * @param ubdo     DUBO bound (UBDO)
 * @param z        node ceiling {@code Z(X)} (UBDO)
 * @param minSup   effective minimum support at mining time (DOP_CHECK / UBDO)
 * @param support  live support of the pattern (PATTERN only; 0 otherwise)
 * @param tids     live transaction ids of the pattern (PATTERN only; empty otherwise)
 */
public record MiningEvent(
        Kind kind,
        String key,
        boolean passed,
        double doValue,
        double ubdo,
        double z,
        double minSup,
        int support,
        int[] tids) {

    public enum Kind { PATTERN, DOP_CHECK, UBDO }

    /** A pattern accepted at C4 (real-time DOP). */
    public static MiningEvent pattern(String key, double doValue, int support, int[] tids) {
        return new MiningEvent(Kind.PATTERN, key, true, doValue, 0.0, 0.0, 0.0, support, tids);
    }

    /** The {@code DO ≥ minSup − ε} candidate test. */
    public static MiningEvent dopCheck(String key, double doValue, double minSup, boolean passed) {
        return new MiningEvent(Kind.DOP_CHECK, key, passed, doValue, 0.0, 0.0, minSup, 0, EMPTY_TIDS);
    }

    /** The {@code UB'(X) ≥ minSup − ε} prune test. */
    public static MiningEvent ubdo(String key, double ubdo, double z, double minSup, boolean passed) {
        return new MiningEvent(Kind.UBDO, key, passed, 0.0, ubdo, z, minSup, 0, EMPTY_TIDS);
    }

    private static final int[] EMPTY_TIDS = new int[0];
}
