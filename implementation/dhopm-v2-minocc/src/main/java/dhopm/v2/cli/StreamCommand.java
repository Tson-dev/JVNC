package dhopm.v2.cli;

import dhopm.common.contract.MiningProgress;
import dhopm.common.transaction.Transaction;
import dhopm.v2.engine.MiningEngineV2;

import java.util.List;

/**
 * Lệnh {@code stream}: log thời gian thực — sự kiện nạp + tick tiến trình mining
 * theo từng root-subtree (cùng cấu trúc V1, kèm dòng window ở cuối mỗi mineNow).
 */
final class StreamCommand {

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
            engine.setMiningProgressListener((MiningProgress prog) -> System.out.printf(
                    "  mining: roots %d/%d patterns=%d elapsed_ms=%d%n",
                    prog.completedRootTasks(), prog.totalRootTasks(), prog.patternsFound(), prog.elapsedMs()));

            int from0 = 0;
            int per = Math.max(1, (int) Math.ceil(all.size() / (double) p.parts()));
            for (int part = 1; from0 < all.size(); part++) {
                int to = Math.min(from0 + per, all.size());
                List<Transaction> chunk = all.subList(from0, to);
                System.out.printf("load: part=%d tx=%d (tid %d..%d)%n", part, chunk.size(),
                        chunk.get(0).tid(), chunk.get(chunk.size() - 1).tid());
                engine.loadBatch(chunk);
                var r = engine.mineNow();
                System.out.println("mineNow: patterns=" + r.patterns().size() + " | " + CliSupport.windowLine(engine.windowInfo()));
                MineCommand.printWarnings(engine);
                from0 = to;
            }
        }
        return 0;
    }
}