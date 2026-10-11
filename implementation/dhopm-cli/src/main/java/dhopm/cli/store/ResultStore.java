package dhopm.cli.store;

import dhopm.cli.Codes;
import dhopm.cli.ManagerException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backing store for mined result files (D48).
 *
 * <p>Writing happens on a single dedicated thread fed by a queue so a slow disk never blocks a mining
 * worker; several concurrent mines just enqueue their content. File naming is
 * {@code mine_<yy-MM-dd>_<NNN>.txt} where {@code NNN} counts up within the day and resets at midnight.
 */
public final class ResultStore implements AutoCloseable {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yy-MM-dd");

    private final Path dir;
    private final BlockingQueue<WriteTask> queue = new LinkedBlockingQueue<>();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final Thread writer;
    private volatile boolean running = true;
    private final Object idLock = new Object();
    private String currentDay = "";
    private int counter = 0;

    private record WriteTask(String fileID, String content, Path target) {
    }

    public ResultStore(Path dir) {
        this.dir = dir;
        this.writer = new Thread(this::drain, "dhopm-result-store");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    public Path dir() {
        return dir;
    }

    /** Allocates the next file id for today, e.g. {@code 26-10-11_001}; resets {@code NNN} per day. */
    public String nextFileId() {
        synchronized (idLock) {
            String today = LocalDate.now().format(DAY);
            if (!today.equals(currentDay)) {
                currentDay = today;
                counter = 0;
            }
            counter++;
            return today + "_" + String.format("%03d", counter);
        }
    }

    public Path pathOf(String fileID) {
        return dir.resolve("mine_" + fileID + ".txt");
    }

    /** Enqueues a write; returns immediately (non-blocking, D48). */
    public void write(String fileID, String content) {
        writeAt(dir, fileID, content);
    }

    /** Enqueues a write into an explicit directory (used by {@code --export <path>}). */
    public void writeAt(Path targetDir, String fileID, String content) {
        Path target = targetDir.resolve("mine_" + fileID + ".txt");
        queue.add(new WriteTask(fileID, content, target));
    }

    /** Reads a stored file back (for {@code fetch}). */
    public String read(String fileID) {
        Path path = pathOf(fileID);
        if (!Files.isRegularFile(path)) {
            throw new ManagerException(Codes.FILE_NOT_FOUND, "no such saved file: " + fileID);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManagerException(Codes.FILE_NOT_FOUND, "cannot read saved file: " + e.getMessage());
        }
    }

    /** Blocks until the current queue is drained (tests / orderly shutdown). */
    public void flush() throws InterruptedException {
        while (!queue.isEmpty()) {
            Thread.sleep(5);
        }
        Thread.sleep(10);
    }

    private void drain() {
        while (running || !queue.isEmpty()) {
            try {
                WriteTask task = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }
                Files.createDirectories(task.target().getParent());
                Files.writeString(task.target(), task.content(), StandardCharsets.UTF_8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                lastError.set(e);
            }
        }
    }

    public Throwable lastError() {
        return lastError.get();
    }

    @Override
    public void close() {
        running = false;
        writer.interrupt();
    }
}
