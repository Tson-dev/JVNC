package dhopm.common.contract;

import dhopm.common.window.WindowInfo;

/**
 * Window-aware engine (V2+): exposes the damped-window state to a {@link WindowListener}.
 * Observability contract per plan 00 §4.2 P2/P3: the listener is a null-safe no-op path and
 * enabling it must NOT change results (INV-E).
 */
public interface WindowAwareEngine extends Engine {

    /** Registers the window observer (may be {@code null} to disable). */
    void setWindowListener(WindowListener listener);

    /** Latest window state of the last {@code mineNow()} (never {@code null}). */
    WindowInfo windowInfo();
}