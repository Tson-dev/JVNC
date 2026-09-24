package dhopm.v1.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.io.TextTransactionReader;
import dhopm.common.transaction.Transaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     */
    static Loaded load(Params p) throws CliException {
        Path path = Paths.get(p.dataset());
        if (!Files.exists(path)) {
            throw new CliException("dataset not found: " + path);
        }
        try {
            dhopm.common.io.TransactionSource source = switch (p.format()) {
                case "fimi" -> new FimiTransactionReader(path);
                case "text" -> new TextTransactionReader(path);
                default -> throw new CliException("unknown format: " + p.format());
            };
            List<Transaction> all = new java.util.ArrayList<>();
            long remaining = p.limit();
            while (source.hasNext() && (p.limit() == 0 || remaining-- > 0)) {
                all.add(source.next());
            }
            if (all.isEmpty()) {
                throw new CliException("empty dataset: " + path);
            }
            return new Loaded(all, p.limit(), !source.hasNext());
        } catch (java.io.IOException e) {
            throw new CliException("failed to read dataset: " + e.getMessage());
        }
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
                  mine     short summary (default for legacy --dataset ... calls)
                  detail   per-part debug: nodes/entries/roots + ms + top-N DO patterns
                  stream   real-time log: load events + mining progress ticks
                  golden   run TestKit TC1-TC8 through the engine, print PASS/FAIL
                  inspect  dataset/config stats without mining

                Options:
                  --dataset <file>   --format fimi|text (default fimi)
                  --partial <d [0,1]>  --f <(0,1]>  --workers <n>  --parts <n>  --top <n>
                  --limit <n>   read at most the FIRST n transactions, then mine
                                (0 or omitted = read all; if dataset is smaller,
                                 whole dataset is read and the limit is ignored)""";
    }
}