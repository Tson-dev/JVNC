package dhopm.common.contract;

/**
 * Receives fine-grained mining observations (pattern accepted, DOP check, prune check).
 * Implemented by CLI {@code stream} and the FE mining screen. Engines MUST call this only
 * when a listener is set — zero overhead otherwise. The listener is invoked from mining
 * worker threads, so implementations must be thread-safe.
 */
@FunctionalInterface
public interface MiningEventListener {

    void onEvent(MiningEvent event);
}
