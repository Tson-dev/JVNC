package dhopm.common.io;

import dhopm.common.transaction.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

/**
 * Text format reader: {@code <TID> <item1> <item2> ...} on each line.
 * Used for test cases where explicit TIDs are needed (blank lines and {@code #} lines are skipped).
 *
 * <p>Accepts a {@link Path} or a raw {@link InputStream}; the stream form is what lets ZIP-packaged
 * datasets reuse the exact same parsing without extracting to disk first.
 */
public final class TextTransactionReader implements TransactionSource {

    private final BufferedReader reader;
    private Transaction next;
    private boolean closed;

    public TextTransactionReader(Path path) throws IOException {
        reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        advance();
    }

    public TextTransactionReader(InputStream in) {
        reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
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
                if (tokens.length < 2) {
                    throw new IOException("text line missing TID: " + line);
                }
                int tid = Integer.parseInt(tokens[0]);
                String[] items = new String[tokens.length - 1];
                System.arraycopy(tokens, 1, items, 0, items.length);
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
}