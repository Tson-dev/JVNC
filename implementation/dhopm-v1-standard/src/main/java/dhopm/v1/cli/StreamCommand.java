package dhopm.v1.cli;

import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.Phase;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.TimingRecorder;
import dhopm.v1.engine.MiningEngine;

import java.util.List;
import java.util.Locale;

/**
 * Lệnh {@code stream}: log THỜI GIAN THỰC — sự kiện load từng phần + tick tiến trình mining
 * (root k/total, patterns, ms) qua {@link MiningProgressListener}, có flush từng dòng.
 * Bật listener không làm đổi kết quả (INV-E) — `stream` và `mine` cho cùng output sau khi xong.
 */
final class StreamCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(args, from));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        System.out.println("== " + CliSupport.header(p) + " / stream (real-time) ==");
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        try (MiningEngine engine = new MiningEngine(CliSupport.config(p))) {
            TimingRecorder rec = new TimingRecorder();
            engine.setPhaseListener(rec);
            engine.setMiningProgressListener(progress -> printTick(progress));

            int from0 = 0;
            int per = Math.max(1, (int) Math.ceil(all.size() / (double) p.parts()));
            for (int part = 1; from0 < all.size(); part++) {
                int to = Math.min(from0 + per, all.size());
                List<Transaction> chunk = all.subList(from0, to);
                System.out.printf("  [load  ] part %d: %d tx (tid %d..%d)%n",
                        part, chunk.size(), chunk.get(0).tid(), chunk.get(chunk.size() - 1).tid());
                long l0 = System.nanoTime();
                engine.loadBatch(chunk);
                System.out.printf("  [load  ] xong %dms, nodes=%d entries=%d total=%d lastTid=%d%n",
                        (System.nanoTime() - l0) / 1_000_000L,
                        engine.globalNodeCount(), engine.globalEntryCount(),
                        engine.totalLoaded(), engine.lastTid());
                long m0 = System.nanoTime();
                var r = engine.mineNow();
                System.out.printf(Locale.ROOT,
                        "  [mining] xong %dms -> %d patterns; C=%dms R=%dms M=%dms heap=%.1fMB%n",
                        (System.nanoTime() - m0) / 1_000_000L, r.patterns().size(),
                        rec.phaseMs(Phase.CONSTRUCTION), rec.phaseMs(Phase.RECONSTRUCTION),
                        rec.phaseMs(Phase.MINING), rec.peakHeapBytes() / 1048576.0);
                from0 = to;
            }
        }
        return 0;
    }

    /** Throttle: in tối đa ~20 tick/phần (5%..100%), kèm tick cuối qua callback có flush. */
    private void printTick(MiningProgress progress) {
        int ll = (int) Math.floor(progress.fraction() * 100.0 / 5.0) * 5;
        if (lastPercent == null || ll != lastPercent || progress.completedRootTasks() >= progress.totalRootTasks()) {
            lastPercent = ll;
            System.out.printf(Locale.ROOT, "  [tick  ] root %d/%d (%.0f%%)  patterns=%d  %dms%n",
                    progress.completedRootTasks(), progress.totalRootTasks(),
                    progress.fraction() * 100.0, progress.patternsFound(), progress.elapsedMs());
            System.out.flush();
        }
    }

    private Integer lastPercent;
}