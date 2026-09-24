package dhopm.common.contract;

/**
 * Receives coarse-grained mining progress (one tick per completed root subtree).
 * Implemented by CLI {@code stream}, the debug/compare app (G4) loading screen, etc.
 * Engines MUST call this only when a listener is set — zero overhead otherwise (§4.2 P3).
 */
@FunctionalInterface
public interface MiningProgressListener {

    void onProgress(MiningProgress progress);
}