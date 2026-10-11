package dhopm.cli;

import dhopm.common.io.DatasetFile;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.io.TextTransactionReader;
import dhopm.common.io.TransactionSource;
import dhopm.common.io.ZipDataset;
import dhopm.common.transaction.Transaction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads a dataset for one request, honouring {@code format} and {@code limit} exactly like the
 * per-version CLIs so {@code Manager mine --json} matches the text CLI on the same input.
 */
public final class DatasetLoader {

    private DatasetLoader() {
    }

    /** Loaded transactions plus the limit bookkeeping needed to explain truncation. */
    public record Loaded(List<Transaction> transactions, long limit, boolean reachedEnd, String label) {
        public boolean limited() {
            return limit > 0;
        }

        public long lastTid() {
            return transactions.isEmpty() ? 0 : transactions.get(transactions.size() - 1).tid();
        }
    }

    public static Loaded load(String dataset, String format, long limit) {
        if (dataset == null || dataset.isBlank()) {
            throw ManagerException.missing("dataset");
        }
        if (limit < 0) {
            throw ManagerException.invalid("limit must be a non-negative integer, got: " + limit);
        }
        Path path = Paths.get(dataset);
        if (!Files.exists(path)) {
            throw new ManagerException(Codes.DATASET_NOT_FOUND, "dataset not found: " + path);
        }
        DatasetFile.Kind kind;
        try {
            kind = DatasetFile.requireSupported(path);
        } catch (IllegalArgumentException e) {
            throw new ManagerException(Codes.UNSUPPORTED_FORMAT, e.getMessage());
        }
        try {
            if (kind == DatasetFile.Kind.ZIP) {
                try (ZipDataset zip = ZipDataset.open(path)) {
                    return read(zip.openStream(), format, limit, path + " [" + zip.entryName() + "]");
                }
            }
            try (InputStream in = Files.newInputStream(path)) {
                return read(in, format, limit, path.toString());
            }
        } catch (IOException e) {
            throw new ManagerException(Codes.DATASET_NOT_FOUND, "failed to read dataset: " + e.getMessage());
        }
    }

    private static Loaded read(InputStream in, String format, long limit, String label) {
        TransactionSource source = switch (format == null ? "fimi" : format.toLowerCase(Locale.ROOT)) {
            case "fimi", "" -> new FimiTransactionReader(in);
            case "text" -> new TextTransactionReader(in);
            default -> throw new ManagerException(Codes.UNSUPPORTED_FORMAT,
                    "unknown format: " + format + " (expected fimi|text)");
        };
        List<Transaction> all = new ArrayList<>();
        long remaining = limit;
        while (source.hasNext() && (limit == 0 || remaining-- > 0)) {
            all.add(source.next());
        }
        if (all.isEmpty()) {
            throw ManagerException.invalid("empty dataset: " + label);
        }
        return new Loaded(all, limit, !source.hasNext(), label);
    }
}
