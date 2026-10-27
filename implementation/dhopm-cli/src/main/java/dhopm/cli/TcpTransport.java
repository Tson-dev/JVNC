package dhopm.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP transport of the FE contract (D46): the BE opens {@code localhost:7079} and waits for {@code connect}
 * from FE/app clients. Each connection is an independent JSONL session. Multiple sessions run in
 * parallel; within a session requests are handled in arrival order (the socket read loop is serial).
 */
public final class TcpTransport {

    /** Default port pinned by D46. */
    public static final int DEFAULT_PORT = 7079;

    private final CommandDispatcher dispatcher;
    private final int port;

    public TcpTransport(CommandDispatcher dispatcher, int port) {
        this.dispatcher = dispatcher;
        this.port = port;
    }

    /** Blocks forever, accepting connections and serving each on its own thread. */
    public void serveForever(PrintStream logStream) throws IOException {
        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            logStream.println("dhopm-cli serve: listening on tcp/" + port + " (protocol " + CommandDispatcher.PROTOCOL + ")");
            ExecutorService pool = Executors.newCachedThreadPool();
            while (!server.isClosed()) {
                Socket socket = server.accept();
                pool.submit(() -> serveConnection(socket, logStream));
            }
        }
    }

    private void serveConnection(Socket socket, PrintStream logStream) {
        ServeLog log = ServeLog.open(logStream, String.valueOf(socket.getRemoteSocketAddress()));
        ServeLog previous = ServeLog.bind(log);
        try (socket;
             InputStream in = socket.getInputStream();
             PrintStream out = new PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            ServeLog.info("client connected " + socket.getRemoteSocketAddress());
            new ServeLoop(dispatcher).run(in, out, log);
            ServeLog.info("client disconnected " + socket.getRemoteSocketAddress());
        } catch (IOException e) {
            ServeLog.error("connection error: " + e.getMessage());
        } finally {
            ServeLog.unbind(previous);
        }
    }
}
