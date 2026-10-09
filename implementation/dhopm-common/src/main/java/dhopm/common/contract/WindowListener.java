package dhopm.common.contract;

import dhopm.common.window.WindowInfo;

/**
 * Observer of the damped-window lifecycle (evictions / live &amp; dead entries / N_eff),
 * implemented by CLI {@code window}, benchmark and the G4 debug app. Engines MUST call this only
 * when a listener is set and only at phase boundaries — zero overhead otherwise (§4.2 P3).
 */
@FunctionalInterface
public interface WindowListener {

    void onWindow(WindowInfo info);
}