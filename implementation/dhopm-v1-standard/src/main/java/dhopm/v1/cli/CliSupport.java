package dhopm.v1.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Pattern;
import dhopm.common.io.DatasetFile;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.io.TextTransactionReader;
import dhopm.common.io.TransactionSource;
import dhopm.common.io.ZipDataset;
import dhopm.common.transaction.Transaction;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Giao diện dùng chung cho bộ lệnh CLI V1 (G1-D7): đọc tham số kiểu {@code --key value},
 * nạp dataset (FIMI/text), tạo config/engine. CLI chỉ dùng API mở trong `dhopm-common`
 * và `dhopm.v1.engine` — không truy cập nội bộ thuật toán (plan 00 §4.2).
 */
final class CliSupport {

    private CliSupport() {
    }

    /** All common options with defaults applied after parsing. */
    record Params(String dataset, String format, double partial, double f, int workers, int parts, int top,
                  long limit) {

        static Params defaults() {
            return new Params(null, "fimi", 0.15, 0.9, MiningConfig.DEFAULT_WORKERS, 1, 5, 0);
        }

        Params with(String key, String value) throws CliException {
            return switch (key) {
                case "--dataset" -> new Params(value, format, partial, f, workers, parts, top, limit);
                case "--format" -> new Params(dataset, value, partial, f, workers, parts, top, limit);
                case "--partial" -> new Params(dataset, format, num(key, value), f, workers, parts, top, limit);
                case "--f" -> new Params(dataset, format, partial, num(key, value), workers, parts, top, limit);
                case "--workers" -> new Params(dataset, format, partial, f, integer(key, value), parts, top, limit);
                case "--parts" -> new Params(dataset, format, partial, f, workers, integer(key, value), top, limit);
                case "--top" -> new Params(dataset, format, partial, f, workers, parts, integer(key, value), limit);
                case "--limit" -> new Params(dataset, format, partial, f, workers, parts, top, longValue(key, value));
                default -> throw new CliException("unknown parameter: " + key);
            };
        }

        Params validated() throws CliException {
            if (limit < 0) {
                throw new CliException("parameter --limit needs a non-negative long, got: " + limit);
            }
            return this;
        }
    }

    /**
     * Kết quả nạp dataset đã áp {@code --limit}: {@code reachedEnd=true} có nghĩa đã đọc
     * HẾT dataset (dataset ≤ limit nên limit được bỏ qua); ngược lại ta dừng sau
     * {@code transactions.size()} giao dịch đầu (dataset còn dữ liệu phía sau).
     */
    record Loaded(List<Transaction> transactions, long limit, boolean reachedEnd) {
        boolean limited() {
            return limit > 0;
        }
    }

    static final class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }

    /** Parses pairs from args[from..]; tolerates a trailing value-less key. */
    static Params parse(String[] args, int from) throws CliException {
        Params p = Params.defaults();
        for (int i = from; i + 1 < args.length; i += 2) {
            p = p.with(args[i], args[i + 1]);
        }
        if (from < args.length && (args.length - from) % 2 != 0) {
            throw new CliException("missing value for parameter: " + args[args.length - 1]);
        }
        return p.validated();
    }

    static Params requireDataset(Params p) throws CliException {
        if (p.dataset() == null) {
            throw new CliException("missing required option --dataset <file>");
        }
        return p;
    }

    private static double num(String key, String value) throws CliException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new CliException("parameter " + key + " needs a real number, got: " + value);
        }
    }

    private static int integer(String key, String value) throws CliException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CliException("parameter " + key + " needs an integer, got: " + value);
        }
    }

    private static long longValue(String key, String value) throws CliException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new CliException("parameter " + key + " needs a long, got: " + value);
        }
    }

    /**
     * Nạp dataset theo format; nếu {@code --limit > 0} chỉ lấy tối đa {@code limit} giao
     * dịch ĐẦU (đọc lười, không nạp cả file 1M+ dòng vào bộ nhớ) rồi báo đã hết dataset
     * hay chưa. {@code limit=0} = đọc hết như cũ.
     *
     * <p>Đường dẫn bắt buộc có extension ({@link DatasetFile}); {@code .zip} được giải nén
     * trực tiếp, không ghi tạm ra đĩa.
     */
    static Loaded load(Params p) throws CliException {
        Path path = Paths.get(p.dataset());
        if (!Files.exists(path)) {
            throw new CliException("dataset not found: " + path);
        }
        DatasetFile.Kind kind;
        try {
            kind = DatasetFile.requireSupported(path);
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage());
        }
        try {
            if (kind == DatasetFile.Kind.ZIP) {
                try (ZipDataset zip = ZipDataset.open(path)) {
                    return read(zip.openStream(), p, path + " [" + zip.entryName() + "]");
                }
            }
            try (InputStream in = Files.newInputStream(path)) {
                return read(in, p, path.toString());
            }
        } catch (java.io.IOException e) {
            throw new CliException("failed to read dataset: " + e.getMessage());
        }
    }

    private static Loaded read(InputStream in, Params p, String label) throws CliException {
        TransactionSource source = switch (p.format()) {
            case "fimi" -> new FimiTransactionReader(in);
            case "text" -> new TextTransactionReader(in);
            default -> throw new CliException("unknown format: " + p.format());
        };
        List<Transaction> all = new ArrayList<>();
        long remaining = p.limit();
        while (source.hasNext() && (p.limit() == 0 || remaining-- > 0)) {
            all.add(source.next());
        }
        if (all.isEmpty()) {
            throw new CliException("empty dataset: " + label);
        }
        return new Loaded(all, p.limit(), !source.hasNext());
    }

    static MiningConfig config(Params p) {
        return new MiningConfig(p.partial(), p.f(), MiningConfig.DEFAULT_EPSILON, p.workers());
    }

    static String header(Params p) {
        return "v1-standard | dataset=" + (p.dataset() == null ? "-" : p.dataset())
                + " | format=" + p.format() + " | partial=" + fmt(p.partial())
                + " | f=" + fmt(p.f()) + " | workers=" + p.workers() + " | parts=" + p.parts()
                + (p.limit() > 0 ? " | limit=" + p.limit() : "");
    }

    /** Marker khi dùng {@code --limit}: báo đọc hết dataset (limit bị bỏ qua) hay tạm dừng. */
    static String limitNote(Loaded ld) {
        if (!ld.limited()) {
            return "";
        }
        if (ld.reachedEnd()) {
            return "note: limit=" + ld.limit() + " ignored - whole dataset read (" + ld.transactions().size() + " tx)";
        }
        return "note: limit=" + ld.limit() + " - stopped after reading " + ld.transactions().size()
                + " tx (dataset has more)";
    }

    /** Prints the mined DOP list (sorted by DO desc) so {@code mine} returns the actual results. */
    static void printPatterns(List<Pattern> patterns) {
        List<Pattern> sorted = new ArrayList<>(patterns);
        sorted.sort(Comparator.comparingDouble(Pattern::dampedOccupancy).reversed());
        System.out.println("patterns (" + sorted.size() + "):");
        int i = 1;
        for (Pattern pt : sorted) {
            System.out.printf("  %3d. %-24s DO=%.6f%n", i++, pt.canonicalKey(), pt.dampedOccupancy());
        }
    }

    /**
     * Prints the global DHO-List after reconstruction (sorted by support ascending) with the
     * candidate roots marked {@code [R]} ({@code support ≥ minSup = ∂×N}). Used by {@code detail}.
     */
    static void printDhoList(List<dhopm.v1.model.DHONode> nodes, double minSup) {
        System.out.println("DHO-List after reconstruction (" + nodes.size()
                + " nodes; [R] = root support >= minSup=" + fmt4(minSup) + "):");
        for (dhopm.v1.model.DHONode n : nodes) {
            boolean root = n.support() >= minSup - MiningConfig.DEFAULT_EPSILON;
            System.out.printf("  %s %-20s support=%-4d DO=%.6f  entries=%-4d tids=%s%n",
                    root ? "[R]" : "[ ]", n.item(), n.support(), n.doValue(), n.entries().size(),
                    fmtTids(n.tids()));
        }
    }

    /** Compact TID list (capped so a large support does not flood the log). */
    static String fmtTids(int[] tids) {
        int cap = 12;
        if (tids.length <= cap) {
            return Arrays.toString(tids);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cap; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(tids[i]);
        }
        return sb.append(", ...(").append(tids.length).append(")]").toString();
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%g", v);
    }

    static String fmt4(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    static String usages() {
        return """
                dhopm.v1.cli.Main <command> [options]

                Commands (shared set for all 3 versions - overall plan 4.2):
                  mine     summary (per-part rows + totals) then the DOP list (default for legacy --dataset ... calls)
                  detail   per-part debug: nodes/entries/roots + ms + DHO-List after reconstruction + top-N DO patterns
                  stream   real-time log: load events + mining progress ticks + each pattern when reached,
                           then the final DOP list. Add --step to log each mining step instead
                           (--detail 0|1|2: 2=accepted DOP, 1=+accepted prune, 0=+rejected cases)
                  golden   run TestKit TC1-TC8 through the engine, print PASS/FAIL
                  inspect  dataset/config stats without mining

                Options:
                  --dataset <file>   --format fimi|text (default fimi)
                                    path must have an extension: .dat/.txt/.text/.csv/.tsv (plain text)
                                    or .zip (archive holding exactly 1 dataset file; .rar/.7z rejected)
                  --partial <d [0,1]>  --f <(0,1]>  --workers <n>  --parts <n>  --top <n>
                  --limit <n>   read at most the FIRST n transactions, then mine
                                (0 or omitted = read all; if dataset is smaller,
                                 whole dataset is read and the limit is ignored)""";
    }
}