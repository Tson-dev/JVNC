package dhopm.bench;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Phase;
import dhopm.common.io.DatasetFile;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v1.engine.MiningEngine;
import dhopm.v2.engine.MiningEngineV2;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark driver for V1 (paper) and V2 (damped window minOcc) engines (OVERALL-PLAN 5.2/5.4, G2 ablation):
 * incremental load in {@code parts}, one {@code mineNow} per part, per-stage timings, CSV output.
 *
 * <pre>
 *   --dataset &lt;fimi file&gt;   --engine v1|v2 (default v1)   --minOcc &lt;window minOcc; default 1e-6 for v2&gt;
 *   --epsilon &lt;numeric tolerance; default 1e-9&gt;
 *   --partial &lt;∂; default 0.15&gt;   --f &lt;decay; default 0.9&gt;   --workers &lt;threads; default CPU&gt;
 *   --parts  &lt;chunks; default 5&gt;   --limit &lt;first n transactions; 0=all&gt;
 * </pre>
 */
public final class BenchmarkMain {

    public static void main(String[] args) throws Exception {
        String dataset = null;
        String engineId = "v1";
        double partial = 0.15;
        double f = 0.9;
        double minOcc = MiningConfig.DEFAULT_MIN_OCC;
        double epsilon = MiningConfig.DEFAULT_EPSILON;
        int workers = MiningConfig.DEFAULT_WORKERS;
        int parts = 5;
        long limit = 0;

        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--dataset" -> dataset = args[i + 1];
                case "--engine" -> engineId = args[i + 1];
                case "--minOcc" -> minOcc = Double.parseDouble(args[i + 1]);
                case "--epsilon" -> epsilon = Double.parseDouble(args[i + 1]);
                case "--partial" -> partial = Double.parseDouble(args[i + 1]);
                case "--f" -> f = Double.parseDouble(args[i + 1]);
                case "--workers" -> workers = Integer.parseInt(args[i + 1]);
                case "--parts" -> parts = Integer.parseInt(args[i + 1]);
                case "--limit" -> limit = Long.parseLong(args[i + 1]);
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (dataset == null || parts < 1 || limit < 0) {
            System.err.println("usage: --dataset <file> [--engine v1|v2] [--minOcc minOcc]"
                    + " [--epsilon tol] [--partial ∂] [--f f] [--workers n] [--parts n] [--limit n]");
            System.exit(2);
        }

        if (!engineId.equals("v1") && !engineId.equals("v2")) {
            System.err.println("unknown engine: " + engineId + " (use v1|v2)");
            System.exit(2);
        }

        Path path = Paths.get(dataset);
        List<Transaction> all = readLimited(path, limit);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("empty dataset: " + path);
        }

        MiningConfig config = engineId.equals("v2")
                ? new MiningConfig(partial, f, epsilon, minOcc, workers)
                : new MiningConfig(partial, f, epsilon, 0.0, workers);
        TimingRecorder rec = new TimingRecorder();
        int per = Math.max(1, (int) Math.ceil(all.size() / (double) parts));

        System.out.println("engine=" + engineId + (engineId.equals("v2") ? ",minOcc," + minOcc : "")
                + ",database," + path.getFileName() + ",tx," + all.size()
                + (limit > 0 ? ",limit," + limit : "")
                + ",partial," + partial + ",f," + f + ",workers," + workers);
        System.out.println("part,lines,last_tid,construction_ms,reconstruction_ms,mining_ms,total_ms,patterns,heap_mb");

        dhopm.common.contract.PhaseAwareEngine engine = newEngine(config, engineId);
        try {
            engine.setPhaseListener(rec);
            int from = 0;
            for (int p = 1; from < all.size(); p++) {
                int to = Math.min(from + per, all.size());
                engine.loadBatch(all.subList(from, to));
                var r = engine.mineNow();
                printRow(to, r.lastTid(), rec, r.patterns().size());
                from = to;
            }
        } finally {
            ((AutoCloseable) engine).close();
        }
    }

    private static dhopm.common.contract.PhaseAwareEngine newEngine(MiningConfig config, String engineId) {
        if (engineId.equals("v2")) {
            return new MiningEngineV2(config);
        }
        return new MiningEngine(config);
    }

    /** Reads at most {@code limit} transactions (0 = all). Works for plain and .zip datasets. */
    private static List<Transaction> readLimited(Path path, long limit) throws Exception {
        DatasetFile.Kind kind = DatasetFile.requireSupported(path);
        List<Transaction> all = new ArrayList<>();
        if (kind == DatasetFile.Kind.ZIP) {
            try (dhopm.common.io.ZipDataset zip = dhopm.common.io.ZipDataset.open(path);
                 InputStream in = zip.openStream()) {
                readInto(new FimiTransactionReader(in), all, limit);
            }
        } else {
            try (InputStream in = Files.newInputStream(path)) {
                readInto(new FimiTransactionReader(in), all, limit);
            }
        }
        return all;
    }

    private static void readInto(FimiTransactionReader reader, List<Transaction> all, long limit) {
        long remaining = limit;
        while (reader.hasNext() && (limit == 0 || remaining-- > 0)) {
            all.add(reader.next());
        }
    }

    private static void printRow(long scanned, int lastTid, TimingRecorder rec, int patterns) {
        System.out.println(scanned + "," + lastTid + ","
                + rec.phaseMs(Phase.CONSTRUCTION) + "," + rec.phaseMs(Phase.RECONSTRUCTION) + ","
                + rec.phaseMs(Phase.MINING) + "," + rec.totalMs() + "," + patterns + ","
                + (rec.peakHeapBytes() / 1048576.0));
    }
}