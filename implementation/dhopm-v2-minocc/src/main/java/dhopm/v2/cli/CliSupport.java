package dhopm.v2.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.io.DatasetFile;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.io.TextTransactionReader;
import dhopm.common.io.TransactionSource;
import dhopm.common.io.ZipDataset;
import dhopm.common.transaction.Transaction;
import dhopm.common.window.WindowInfo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared CLI support for the V2 command set (G2-M6). Mirrors {@code dhopm.v1.cli.CliSupport} and adds:
 * the window parameter {@code --minOcc} (+ {@code --epsilon}), the two-phase window state print
 * and the pre-emptive config validation used by {@code mine}/{@code detail}/{@code sweep}.
 */
final class CliSupport {

    private CliSupport() {
    }

    /** All common options with defaults applied after parsing. Library default minOcc = 1e-6 (D41). */
    record Params(String dataset, String format, double partial, double f, double minOcc, double epsilon,
                  int workers, int parts, int top, long limit) {

        static Params defaults() {
            return new Params(null, "fimi", 0.15, 0.9, MiningConfig.DEFAULT_MIN_OCC,
                    MiningConfig.DEFAULT_EPSILON, MiningConfig.DEFAULT_WORKERS, 1, 5, 0);
        }

        Params with(String key, String value) throws CliException {
            return switch (key) {
                case "--dataset" -> new Params(value, format, partial, f, minOcc, epsilon, workers, parts, top, limit);
                case "--format" -> new Params(dataset, value, partial, f, minOcc, epsilon, workers, parts, top, limit);
                case "--partial" -> new Params(dataset, format, num(key, value), f, minOcc, epsilon, workers, parts, top, limit);
                case "--f" -> new Params(dataset, format, partial, num(key, value), minOcc, epsilon, workers, parts, top, limit);
                case "--minOcc" -> new Params(dataset, format, partial, f, num(key, value), epsilon, workers, parts, top, limit);
                case "--epsilon" -> new Params(dataset, format, partial, f, minOcc, num(key, value), workers, parts, top, limit);
                case "--workers" -> new Params(dataset, format, partial, f, minOcc, epsilon, integer(key, value), parts, top, limit);
                case "--parts" -> new Params(dataset, format, partial, f, minOcc, epsilon, workers, integer(key, value), top, limit);
                case "--top" -> new Params(dataset, format, partial, f, minOcc, epsilon, workers, parts, integer(key, value), limit);
                case "--limit" -> new Params(dataset, format, partial, f, minOcc, epsilon, workers, parts, top, longValue(key, value));
                default -> throw new CliException("unknown parameter: " + key);
            };
        }

        Params validated() throws CliException {
            if (limit < 0) {
                throw new CliException("parameter --limit needs a non-negative long, got: " + limit);
            }
            return this;
        }

        MiningConfig config() {
            return new MiningConfig(partial, f, epsilon, minOcc, workers);
        }
    }

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

    static String header(Params p) {
        return "v2-minocc | dataset=" + (p.dataset() == null ? "-" : p.dataset())
                + " | format=" + p.format() + " | partial=" + fmt(p.partial())
                + " | f=" + fmt(p.f()) + " | minOcc=" + fmt(p.minOcc())
                + " | workers=" + p.workers() + " | parts=" + p.parts()
                + (p.limit() > 0 ? " | limit=" + p.limit() : "");
    }

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

    /** One-line window state for {@code mine}/{@code detail} output (windowInfo, G2-M6). */
    static String windowLine(WindowInfo w) {
        if (w.isInfinite()) {
            return "window: none (minOcc=0 or f=1 ⇒ W=∞, paper mode) | N_eff=" + w.effectiveTransactions()
                    + " minSup=" + fmt4(w.minSup()) + " Z=" + fmt4(w.maxDO());
        }
        return "window: W=" + w.windowSize() + " N_eff=" + w.effectiveTransactions()
                + " minSup=" + fmt4(w.minSup()) + " Z=" + fmt4(w.maxDO())
                + " evict=" + w.evictions() + " live=" + w.liveEntries() + " dead=" + w.deadEntries();
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%g", v);
    }

    /** Numeric parser shared with sweep/validate (throws {@link CliException} on bad input). */
    static double parseNum(String value) throws CliException {
        return num("value", value);
    }

    static String fmt4(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    static String usages() {
        return """
                dhopm.v2.cli.Main <command> [options]

                Commands (V2 MINOCC/WINDOW — plan 02 §7):
                  mine     short summary (per-part rows + totals) with the window block
                  detail   per-part debug: nodes/entries/roots + ms + top-N DO patterns + window block
                  stream   real-time log: load events + mining progress ticks
                  window   damped-window lookup (NO dataset): W(f,minOcc), asymptotic dp_max + suffix
                  validate config checks: stable issue codes + exact feasibility when a dataset is given
                  sweep    scan (partial, f, minOcc) combinations, mine each, print a table
                  golden   TestKit: TC1-TC8 with minOcc=0 (INV-I vs golden) + TC10/TC12/TC18 window checks
                  inspect  dataset/config stats without mining

                Options:
                  --dataset <file>   --format fimi|text (default fimi)
                  --partial <∂ [0,1]>  --f <(0,1]>  --minOcc <[0,1)> (default 1e-6)  --epsilon <ε> (default 1e-9)
                  --workers <n>  --parts <n>  --top <n>
                  --limit <n>   read at most the FIRST n transactions, then mine
                                (0 or omitted = read all; smaller dataset ⇒ whole dataset is read)""";
    }
}