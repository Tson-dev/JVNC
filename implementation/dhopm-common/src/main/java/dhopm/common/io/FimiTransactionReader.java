package dhopm.common.io;

import dhopm.common.transaction.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

/**
 * FIMI format reader: one transaction per line, items are whitespace-separated integers.
 * {@code TID = 1-based transaction counter} (blank lines and lines starting with {@code #} are skipped).
 */
public final class FimiTransactionReader implements TransactionSource {

    private final BufferedReader reader;
    private Transaction next;
    private int tid;
    private boolean closed;

    public FimiTransactionReader(Path path) throws IOException {
        reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        advance();
    }

    private void advance() {
        try {
            if (closed) {
                next = null;
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                String[] items = new String[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    items[i] = tokens[i];
                }
                tid++;
                next = new Transaction(tid, items);
                return;
            }
            closeQuietly();
            next = null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void closeQuietly() {
        if (!closed) {
            closed = true;
            try {
                reader.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Transaction next() {
        if (next == null) {
            throw new NoSuchElementException();
        }
        Transaction t = next;
        advance();
        return t;
    }

    /**
     * Consumes the stream until a transaction with tid &gt;= {@code targetTid} is read
     * and returns the last consumed tid. Supported for windowed / partial-load mining
     * (e.g. kosarak scalability 200K..990K).
     */
    public long streamPositions(long targetTid) {
        long lastTid = 0;
        while (hasNext()) {
            Transaction t = next();
            lastTid = t.tid();
            if (lastTid >= targetTid) {
                break;
            }
        }
        return lastTid;
    }
}