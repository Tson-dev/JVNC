package dhopm.v1.cli;

import dhopm.common.contract.Phase;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v1.engine.MiningEngine;

import java.util.List;

/**
 * Lệnh {@code mine}: xuất NGẮN — một khối header + 1 dòng/part + dòng tổng.
 * Dùng khi chỉ cần kiểm tra nhanh (gọi dạng cũ {@code --dataset …} cũng vào đây).
 */
final class MineCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(args, from));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        System.out.println(CliSupport.header(p));
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        System.out.printf("%6s %8s %9s %10s %10s %9s %9s %9s %9s%n",
                "part", "loaded", "last_tid", "constr_ms", "reconst_ms", "mining_ms", "total_ms", "patterns", "heap_mb");

        long totalMs = 0;
        int lastPatterns = 0;
        double peakHeapMb = 0;
        try (MiningEngine engine = new MiningEngine(CliSupport.config(p))) {
            TimingRecorder rec = new TimingRecorder();
            engine.setPhaseListener(rec);
            int from0 = 0;
            int per = Math.max(1, (int) Math.ceil(all.size() / (double) p.parts()));
            for (int part = 1; from0 < all.size(); part++) {
                int to = Math.min(from0 + per, all.size());
                engine.loadBatch(all.subList(from0, to));
                var r = engine.mineNow();
                lastPatterns = r.patterns().size();
                totalMs = rec.totalMs();
                peakHeapMb = rec.peakHeapBytes() / 1048576.0;
                System.out.printf("%6d %8d %9d %10d %10d %9d %9d %9d %9.1f%n",
                        part, r.totalTransactions(), r.lastTid(),
                        rec.phaseMs(Phase.CONSTRUCTION), rec.phaseMs(Phase.RECONSTRUCTION), rec.phaseMs(Phase.MINING),
                        totalMs, lastPatterns, peakHeapMb);
                from0 = to;
            }
        }
        System.out.printf("total: total_ms=%d, patterns(last part)=%d, heap_peak=%.1f MB%n", totalMs, lastPatterns, peakHeapMb);
        return 0;
    }
}