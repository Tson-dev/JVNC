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
        Path path = resolveDataset(dataset);
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

    /**
     * Định vị file dataset. Theo contract (08-FE-CONTRACT.md) FE chỉ gửi TÊN
     * dataset (vd {@code "retail.dat"}) — BE tự tìm trong thư mục dataset của
     * mình. Vẫn chấp nhận đường dẫn cũ (relative {@code "dataset/retail.dat"}
     * hay absolute) để tương thích ngược với CLI/benchmark.
     */
    private static Path resolveDataset(String dataset) {
        Path direct = Paths.get(dataset);
        if (Files.exists(direct)) {
            return direct;
        }
        if (!direct.isAbsolute() && !hasSeparator(dataset)) {
            for (String base : datasetDirCandidates()) {
                Path inDir = Paths.get(base, dataset);
                if (Files.exists(inDir)) {
                    return inDir;
                }
            }
        }
        return direct;
    }

    /**
     * Thư mục chứa dataset của BE. Ưu tiên system property
     * {@code dhopm.dataset.dir}, nếu thiếu thử theo {@code user.dir} (chạy từ
     * gốc repo hoặc từ module dhopm-cli).
     */
    private static List<String> datasetDirCandidates() {
        List<String> dirs = new ArrayList<>();
        String prop = System.getProperty("dhopm.dataset.dir");
        if (prop != null && !prop.isBlank()) {
            dirs.add(prop);
        }
        Path userDir = Paths.get(System.getProperty("user.dir"));
        dirs.add(userDir.resolve("dataset").toString());
        dirs.add(userDir.resolve("..").resolve("dataset").toString());
        return dirs;
    }

    private static boolean hasSeparator(String s) {
        return s.indexOf('/') >= 0 || s.indexOf('\\') >= 0;
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
