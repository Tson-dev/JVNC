package dhopm.cli;

import dhopm.cli.json.Json;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Manager entry point (G5). Two modes:
 *
 * <pre>
 *   Manager &lt;command&gt; [args] [--json]     one-shot (default: text summary; --json: contract envelope)
 *   Manager serve [--tcp] [--port 7079]   JSONL server (stdio by default, TCP with --tcp)
 * </pre>
 *
 * <p>Exit codes: 0 ok, 1 request error (protocol envelope printed), 2 usage error.
 */
public final class ManagerMain {

    private ManagerMain() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        CommandDispatcher dispatcher = new CommandDispatcher(new VersionRegistry());

        if (args.length > 0 && args[0].equals("serve")) {
            runServe(dispatcher, args, out);
            return;
        }

        boolean json = containsJson(args);
        Map<String, Object> request;
        try {
            request = Arguments.parse(args);
        } catch (ManagerException e) {
            out.println(Json.stringify(CommandDispatcher.errorResponse(0, e.code(), e.getMessage())));
            out.println(usage());
            System.exit(2);
            return;
        }

        try {
            CommandDispatcher.EventSink sink = e -> out.println(Json.stringify(e));
            Map<String, Object> response = dispatcher.dispatch(request, sink);
            if (json) {
                out.println(Json.stringify(response));
            } else {
                TextRenderer.render(out, request, response);
            }
            System.exit(0);
        } catch (ManagerException e) {
            if (json) {
                out.println(Json.stringify(CommandDispatcher.errorResponse(0, e.code(), e.getMessage())));
            } else {
                out.println("error [" + e.code() + "]: " + e.getMessage());
            }
            System.exit(1);
        } catch (RuntimeException e) {
            if (json) {
                out.println(Json.stringify(CommandDispatcher.errorResponse(0, Codes.INTERNAL_ERROR, String.valueOf(e))));
            } else {
                out.println("internal error: " + e);
            }
            System.exit(1);
        }
    }

    private static void runServe(CommandDispatcher dispatcher, String[] args, PrintStream out) throws Exception {
        boolean tcp = false;
        int port = TcpTransport.DEFAULT_PORT;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--tcp" -> tcp = true;
                case "--port" -> {
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                }
                default -> {
                    // ignore serve-time output flags
                }
            }
        }
        ServeLoop loop = new ServeLoop(dispatcher);
        if (tcp) {
            new TcpTransport(dispatcher, port).serveForever(out);
        } else {
            // stdio: the protocol owns stdout, so logs go to stderr
            loop.run(System.in, out, ServeLog.open(System.err, "stdio"));
        }
    }

    private static boolean containsJson(String[] args) {
        for (String a : args) {
            if (a.equals("--json")) {
                return true;
            }
        }
        return false;
    }

    private static String usage() {
        return """
                dhopm.cli.ManagerMain <command> [args] [--json]
                  mine/detail/stream <dataset> <partial> <factor> <limit> --minOcc <x> [flags]
                  window <f> --minOcc <x> [--tl <long>]
                  validate <partial> <f> --minOcc <x> [--tl <long>] [--dataset <file>]
                  inspect <dataset> [--f <f> --minOcc <x>]
                  golden [--versions v1,v2,v3]   info / version
                  sweep <dataset> --f <f> --partials a,b --minOcc <x> [--versions v1,v2]
                  config get|set|list           fetch <fileID>
                  serve [--tcp] [--port 7079]""";
    }
}
