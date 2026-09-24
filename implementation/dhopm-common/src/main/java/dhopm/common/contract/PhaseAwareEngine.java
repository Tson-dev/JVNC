package dhopm.common.contract;

import dhopm.common.transaction.Transaction;

import java.util.List;

/**
 * Phase-aware engine: exposes the pipeline phases to a {@link PhaseListener}
 * (used by benchmark / debug app). Implementation contract:
 * <ul>
 *   <li>never measure inside the hot path;
 *   <li>when the listener is {@code null} the calls must be zero-cost.
 * </ul>
 */
public interface PhaseAwareEngine extends Engine {

    void setPhaseListener(PhaseListener listener);
}