package dhopm.common.contract;

/**
 * Observer of pipeline phases (GoF Observer, used by the debug app / benchmark driver).
 *
 * <p>Engines MUST only call this at phase <em>boundaries</em> and only when a listener is set
 * (otherwise zero overhead) — never inside the mining hot path (NFR-LOG / OVERALL-PLAN 4.1).
 */
public interface PhaseListener {

    void onPhase(Phase phase, long startNanos, long endNanos);
}