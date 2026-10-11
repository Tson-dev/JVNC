package dhopm.cli;

import dhopm.cli.json.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSONL server over a bring-your-own stream pair — used by {@code Manager serve} on stdio (test/CI,
 * C3) and, with a socket's streams, by the TCP transport (D46). One request per line, one response
 * per line; protocol errors never kill the loop. Stream events are written ahead of the response.
 */
public final class ServeLoop {

    private final CommandDispatcher dispatcher;

    public ServeLoop(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Reads requests until EOF. */
    public void run(InputStream in, PrintStream out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                out.println(handleLine(line, out));
                out.flush();
            }
        } catch (IOException e) {
            // client went away / stream closed: the loop simply ends
        }
    }

    /** Handles one JSONL request line without an event channel (tests). */
    public String handleLine(String line) {
        return handleLine(line, null);
    }

    /**
     * Handles one JSONL request line, emitting stream events to {@code out} (when non-null) and
     * returning the final response line.
     */
    public String handleLine(String line, PrintStream out) {
        long id = 0;
        try {
            Object parsed = Json.parse(line);
            if (!(parsed instanceof Map<?, ?> map)) {
                return Json.stringify(CommandDispatcher.errorResponse(0, Codes.INVALID_PARAMETER,
                        "request must be a JSON object"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) map;
            Object idObj = request.get("id");
            if (idObj instanceof Number n) {
                id = n.longValue();
            }
            CommandDispatcher.EventSink sink = out == null ? event -> {
            } : event -> {
                out.println(Json.stringify(event));
                out.flush();
            };
            return Json.stringify(dispatcher.dispatch(request, sink));
        } catch (ManagerException e) {
            return Json.stringify(CommandDispatcher.errorResponse(id, e.code(), e.getMessage()));
        } catch (Json.JsonException e) {
            return Json.stringify(CommandDispatcher.errorResponse(id, Codes.INVALID_PARAMETER,
                    "malformed JSON: " + e.getMessage()));
        } catch (RuntimeException e) {
            return Json.stringify(CommandDispatcher.errorResponse(id, Codes.INTERNAL_ERROR,
                    String.valueOf(e.getMessage())));
        }
    }
}
