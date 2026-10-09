package dhopm.v2.cli;

import dhopm.common.contract.Phase;
import dhopm.common.contract.Pattern;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v2.engine.MiningEngineV2;

import java.util.Comparator;
import java.util.List;

/**
 * Lệnh {@code detail}: như {@code mine} nhưng thêm thống kê dựng (nodes/entries/roots) mỗi part
 * và top-N mẫu theo DO (G2-M6 — windowInfo nằm trong output).
 */
final class DetailCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(args, from));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        System.out.println(CliSupport.header(p));
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        try (MiningEngineV2 engine = new MiningEngineV2(p.config())) {
            TimingRecorder rec = new TimingRecorder();
            engine.setPhaseListener(rec);
            int from0 = 0;
            int per = Math.max(1, (int) Math.ceil(all.size() / (double) p.parts()));
            for (int part = 1; from0 < all.size(); part++) {
                int to = Math.min(from0 + per, all.size());
                engine.loadBatch(all.subList(from0, to));
                var r = engine.mineNow();
                System.out.println(CliSupport.windowLine(engine.windowInfo()));
                MineCommand.printWarnings(engine);
                System.out.printf("part %d: loaded=%d last_tid=%d nodes=%d entries=%d roots=%d"
                                + " C=%dms R=%dms M=%dms total=%dms heap=%.1fMB patterns=%d%n",
                        part, r.totalTransactions(), r.lastTid(), engine.globalNodeCount(), engine.globalEntryCount(),
                        engine.lastMiningTasks(),
                        rec.phaseMs(Phase.CONSTRUCTION), rec.phaseMs(Phase.RECONSTRUCTION), rec.phaseMs(Phase.MINING),
                        rec.totalMs(), rec.peakHeapBytes() / 1048576.0, r.patterns().size());

                List<Pattern> sorted = new java.util.ArrayList<>(r.patterns());
                sorted.sort(Comparator.comparingDouble(Pattern::dampedOccupancy).reversed());
                int top = Math.min(p.top(), sorted.size());
                for (int i = 0; i < top; i++) {
                    Pattern pattern = sorted.get(i);
                    System.out.printf("  %3d. %-24s DO=%.6f%n", i + 1, pattern.canonicalKey(), pattern.dampedOccupancy());
                }
                from0 = to;
            }
        }
        return 0;
    }
}