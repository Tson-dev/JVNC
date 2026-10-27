package dhopm.cli;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-connection request/processing/response logger for the serve loop (TCP/stdio).
 *
 * <p>The serve loop binds a {@link ServeLog} to the current thread for the duration of one request
 * ({@link #bind}/{@link #unbind}); any handler on that thread (dispatcher, mining runner) can then
 * emit {@code PROC} lines without threading a logger through every signature. Requests and
 * responses are logged by {@link ServeLoop}; per-version engine work is logged by
 * {@link MiningRunner}. When nothing is bound (one-shot CLI, unit tests) every static call is a
 * no-op, so the CLI keeps zero overhead off the serve path.
 *
 * <p>Format: {@code HH:mm:ss.SSS conn#<n> <peer> <LEVEL> <message>}. Long responses/events are
 * truncated to {@link #MAX_PREVIEW} chars so a mining result cannot flood the console.
 */
public final class ServeLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Longest request/response/event preview printed before truncating. */
    public static final int MAX_PREVIEW = 2000;

    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static final ThreadLocal<ServeLog> CURRENT = new ThreadLocal<>();

    private final PrintStream out;
    private final String tag;

    private ServeLog(PrintStream out, String tag) {
        this.out = out;
        this.tag = tag;
    }

    /** Opens a logger for one connection, assigning it the next connection number. */
    public static ServeLog open(PrintStream out, String peer) {
        return new ServeLog(out, "conn#" + CONNECTIONS.incrementAndGet() + " " + peer);
    }

    /** Binds {@code log} to the current thread; returns the previously bound logger (may be null). */
    static ServeLog bind(ServeLog log) {
        ServeLog previous = CURRENT.get();
        CURRENT.set(log);
        return previous;
    }

    /** Restores the logger returned by {@link #bind}. */
    static void unbind(ServeLog previous) {
        CURRENT.set(previous);
    }

    /** Whether a logger is bound to the current thread. */
    public static boolean active() {
        return CURRENT.get() != null;
    }

    // ------------------------------------------------------------ static (thread-bound) API

    /** Logs a request line received from the client. */
    public static void request(String raw) {
        write("REQ ", raw);
    }

    /** Logs a processing step (command dispatch, dataset load, per-version engine work). */
    public static void processing(String message) {
        write("PROC", message);
    }

    /** Logs a response line sent back to the client. */
    public static void response(String raw) {
        write("RES ", raw);
    }

    /** Logs a stream event emitted ahead of the final response. */
    public static void event(String raw) {
        write("EVT ", raw);
    }

    public static void info(String message) {
        write("INFO", message);
    }

    public static void error(String message) {
        write("ERR ", message);
    }

    private static void write(String level, String message) {
        ServeLog log = CURRENT.get();
        if (log != null) {
            log.emit(level, message);
        }
    }

    // ------------------------------------------------------------ instance (connection) API

    private void emit(String level, String message) {
        String line = TS.format(LocalTime.now()) + " " + tag + " " + level + " " + preview(message);
        synchronized (out) {
            out.println(line);
        }
    }

    /** Truncates a payload so a large result set cannot flood the log. */
    static String preview(String message) {
        if (message == null || message.length() <= MAX_PREVIEW) {
            return String.valueOf(message);
        }
        return message.substring(0, MAX_PREVIEW) + "... (+" + (message.length() - MAX_PREVIEW) + " chars)";
    }
}
