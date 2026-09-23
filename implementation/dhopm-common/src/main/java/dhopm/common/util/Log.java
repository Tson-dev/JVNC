package dhopm.common.util;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared logging utility (scaffold at G0).
 *
 * <p>Independence requirement (OVERALL-PLAN 4.1 / NFR-LOG): logging must NOT slow down the
 * algorithm. Therefore this is off by default and engines will be instrumented via a Decorator
 * at the contract layer — never inside the mining hot path.
 */
public final class Log {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static volatile boolean enabled = false;
    private static volatile PrintStream out = System.out;

    private Log() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean flag) {
        enabled = flag;
    }

    public static void setOut(PrintStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream");
        }
        out = stream;
    }

    public static void log(String message) {
        if (enabled) {
            out.println(TS.format(LocalTime.now()) + " [DHOPM] " + message);
        }
    }

    /** Prints elapsed time of a labeled timed block (only when enabled). */
    public static void time(String label, long startNanos) {
        if (enabled) {
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            out.println(TS.format(LocalTime.now()) + " [DHOPM] " + label + " took " + ms + " ms");
        }
    }
}