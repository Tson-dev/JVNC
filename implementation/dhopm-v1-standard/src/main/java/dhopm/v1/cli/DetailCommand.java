package dhopm.v1.cli;

import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.contract.Phase;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v1.engine.MiningEngine;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lệnh {@code detail}: debug chi tiết TỪNG PHẦN — đếm nodes/entries/root tasks + ms của
 * 3 pha, kèm top-N mẫu theo DO (double 6 số lẻ). Dùng khi muốn biết pipeline chạy ra sao.
 */
final class DetailCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(args, from));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        System.out.println("== " + CliSupport.header(p) + " ==");
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        try (MiningEngine engine = new MiningEngine(CliSupport.config(p))) {
            TimingRecorder rec = new TimingRecorder();
            engine.setPhaseListener(rec);
            int from0 = 0;
            int per = Math.max(1, (int) Math.ceil(all.size() / (double) p.parts()));
            for (int part = 1; from0 < all.size(); part++) {
                int to = Math.min(from0 + per, all.size());
                engine.loadBatch(all.subList(from0, to));
                MineResult r = engine.mineNow();

                System.out.println("-- part " + part + " --");
                System.out.printf(Locale.ROOT,
                        "  transactions=%d  lastTid=%d  global.nodes=%d  global.entries=%d%n",
                        r.totalTransactions(), r.lastTid(), engine.globalNodeCount(), engine.globalEntryCount());
                System.out.printf(Locale.ROOT,
                        "  minSup=partial*N=%.4f*%d=%.2f  rootTasks=%d%n",
                        p.partial(), r.totalTransactions(), r.minSup(), engine.lastMiningTasks());
                System.out.printf(Locale.ROOT,
                        "  construction=%dms  reconstruction=%dms  mining=%dms  total=%dms  heap=%.1fMB%n",
                        rec.phaseMs(Phase.CONSTRUCTION), rec.phaseMs(Phase.RECONSTRUCTION),
                        rec.phaseMs(Phase.MINING), rec.totalMs(), rec.peakHeapBytes() / 1048576.0);

                List<Pattern> top = r.patterns().stream()
                        .sorted(Comparator.comparingDouble(Pattern::dampedOccupancy).reversed())
                        .limit(Math.min(p.top(), r.patterns().size()))
                        .toList();
                System.out.println("  top " + top.size() + "/" + r.patterns().size() + " patterns theo DO:");
                for (Pattern pt : top) {
                    System.out.printf(Locale.ROOT, "    %-24s DO=%.6f  tid=%d%n",
                            String.join(" ", pt.items()), pt.dampedOccupancy(), pt.tids().length);
                }
                from0 = to;
            }
        }
        return 0;
    }
}