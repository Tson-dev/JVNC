package dhopm.v1.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Phase;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.io.TextTransactionReader;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v1.engine.MiningEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * V1 command line driver. Incremental load ({@code --parts}): construction is incremental,
 * mining re-runs on each part — mirrors the canonical streaming demo.
 *
 * <pre>
 *   --dataset &lt;fimi|text file&gt;
 *   --format  fimi (default, TID = line index) | text (explicit TID,item list)
 *   --partial &lt;∂ in [0,1], default 0.15&gt;
 *   --f      &lt;decay in (0,1], default 0.9&gt;
 *   --workers &lt;threads, default CPU count&gt;
 *   --parts  &lt;incremental chunks, default 1&gt;
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String dataset = null;
        String format = "fimi";
        double partial = 0.15;
        double f = 0.9;
        int workers = MiningConfig.DEFAULT_WORKERS;
        int parts = 1;

        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--dataset" -> dataset = args[i + 1];
                case "--format" -> format = args[i + 1];
                case "--partial" -> partial = Double.parseDouble(args[i + 1]);
                case "--f" -> f = Double.parseDouble(args[i + 1]);
                case "--workers" -> workers = Integer.parseInt(args[i + 1]);
                case "--parts" -> parts = Integer.parseInt(args[i + 1]);
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (dataset == null || parts < 1) {
            System.err.println("usage: --dataset <file> [--format fimi|text] [--partial ∂] [--f f] [--workers n] [--parts n]");
            System.exit(2);
        }

        Path path = Paths.get(dataset);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("dataset not found: " + path);
        }

        List<Transaction> all = switch (format) {
            case "fimi" -> new FimiTransactionReader(path).toList();
            case "text" -> new TextTransactionReader(path).toList();
            default -> throw new IllegalArgumentException("unknown format: " + format);
        };
        if (all.isEmpty()) {
            throw new IllegalArgumentException("empty dataset: " + path);
        }

        MiningConfig config = new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, workers);
        TimingRecorder rec = new TimingRecorder();

        try (MiningEngine engine = new MiningEngine(config)) {
            engine.setPhaseListener(rec);
            System.out.println("part,loaded,last_tid,construction_ms,reconstruction_ms,mining_ms,total_ms,patterns,heap_mb");
            int from = 0;
            int per = (all.size() + parts - 1) / parts;
            for (int p = 1; from < all.size(); p++) {
                int to = Math.min(from + per, all.size());
                engine.loadBatch(all.subList(from, to));
                var r = engine.mineNow();
                System.out.println(p + "," + r.totalTransactions() + "," + r.lastTid() + ","
                        + rec.phaseMs(Phase.CONSTRUCTION) + "," + rec.phaseMs(Phase.RECONSTRUCTION) + ","
                        + rec.phaseMs(Phase.MINING) + "," + rec.totalMs() + "," + r.patterns().size() + ","
                        + (rec.peakHeapBytes() / 1048576.0));
                from = to;
            }
        }
    }
}