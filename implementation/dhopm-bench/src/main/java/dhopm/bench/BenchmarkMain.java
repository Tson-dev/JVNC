package dhopm.bench;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Phase;
import dhopm.common.io.FimiTransactionReader;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v1.engine.MiningEngine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Benchmark driver for the V1 engine (OVERALL-PLAN 5.2/5.4):
 * incremental load in {@code parts}, one {@code mineNow} per part, per-stage timings.
 *
 * <pre>
 *   --dataset &lt;fimi file&gt;
 *   --partial &lt;∂; default 0.15&gt;
 *   --f      &lt;decay; default 0.9&gt;
 *   --workers &lt;threads; default CPU&gt;
 *   --parts  &lt;chunks; default 5&gt;
 * </pre>
 */
public final class BenchmarkMain {

    public static void main(String[] args) throws Exception {
        String dataset = null;
        double partial = 0.15;
        double f = 0.9;
        int workers = MiningConfig.DEFAULT_WORKERS;
        int parts = 5;

        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--dataset" -> dataset = args[i + 1];
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
            System.err.println("usage: --dataset <file> [--partial ∂] [--f f] [--workers n] [--parts n]");
            System.exit(2);
        }

        Path path = Paths.get(dataset);
        List<Transaction> all = new FimiTransactionReader(path).toList();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("empty dataset: " + path);
        }

        MiningConfig config = new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, workers);
        TimingRecorder rec = new TimingRecorder();
        int per = Math.max(1, (int) Math.ceil(all.size() / (double) parts));

        System.out.println("engine=,v1-standard,database," + path.getFileName() + ",tx," + all.size()
                + ",partial," + partial + ",f," + f + ",workers," + workers);
        System.out.println("part,lines,last_tid,construction_ms,reconstruction_ms,mining_ms,total_ms,patterns,heap_mb");

        try (MiningEngine engine = new MiningEngine(config)) {
            engine.setPhaseListener(rec);
            int from = 0;
            for (int p = 1; from < all.size(); p++) {
                int to = Math.min(from + per, all.size());
                engine.loadBatch(all.subList(from, to));
                var r = engine.mineNow();
                printRow(to, r.lastTid(), rec, r.patterns().size());
                from = to;
            }
        }
    }

    private static void printRow(long scanned, int lastTid, TimingRecorder rec, int patterns) {
        System.out.println(scanned + "," + lastTid + ","
                + rec.phaseMs(Phase.CONSTRUCTION) + "," + rec.phaseMs(Phase.RECONSTRUCTION) + ","
                + rec.phaseMs(Phase.MINING) + "," + rec.totalMs() + "," + patterns + ","
                + (rec.peakHeapBytes() / 1048576.0));
    }
}