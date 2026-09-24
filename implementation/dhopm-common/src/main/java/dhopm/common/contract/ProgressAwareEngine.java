package dhopm.common.contract;

/**
 * Engine that exposes coarse mining progress (GoF Observer) so tooling (CLI {@code stream},
 * benchmark, G4 app) can show real-time state without reaching into the algorithm.
 * Contract per plan 00 §4.2: listener must be a {@code null}-safe no-op path,
 * and enabling it must NOT change results (INV-E).
 */
public interface ProgressAwareEngine extends Engine {

    void setMiningProgressListener(MiningProgressListener listener);
}