package dhopm.v1.cli;

import dhopm.common.contract.MiningEvent;
import dhopm.common.contract.MiningEventListener;
import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.contract.Pattern;
import dhopm.common.transaction.Transaction;
import dhopm.v1.engine.MiningEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lệnh {@code stream}: log THỜI GIAN THỰC (hợp đồng 6.3/6.4/7.3).
 *
 * <ul>
 *   <li>mặc định — in mỗi pattern NGAY khi đạt DOP (real-time) + danh sách DOP cuối mỗi part;</li>
 *   <li>{@code --step} — thay bằng log từng bước mining, mức chi tiết {@code --detail 0/1/2}
 *       ({@code 2}=DOP đạt, {@code 1}=+prune đạt, {@code 0}=+trường hợp bị bỏ);</li>
 *   <li>luôn kèm tick tiến trình (root k/total) và bảng kết quả cuối.</li>
 * </ul>
 *
 * <p>Bật listener không làm đổi kết quả (INV-E); sự kiện đến từ worker thread nên printer
 * đồng bộ hoá khi in.
 */
final class StreamCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        StreamFlags f = StreamFlags.split(args, from);
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(f.rest(), 1));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        System.out.println("== " + CliSupport.header(p) + " / stream (real-time) ==");
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        Printer printer = new Printer(f.step());
        try (MiningEngine engine = new MiningEngine(CliSupport.config(p))) {
            engine.setMiningProgressListener(printer);
            engine.setMiningEventListener(printer, f.step() ? f.detail() : -1);

            int from0 = 0;
            int per = Math.max(1, (int) Math.ceil(all.size() / (double) p.parts()));
            for (int part = 1; from0 < all.size(); part++) {
                int to = Math.min(from0 + per, all.size());
                List<Transaction> chunk = all.subList(from0, to);
                printer.line(String.format(Locale.ROOT, "load: part=%d tx=%d (tid %d..%d)",
                        part, chunk.size(), chunk.get(0).tid(), chunk.get(chunk.size() - 1).tid()));
                engine.loadBatch(chunk);
                var r = engine.mineNow();
                printer.dopList(r.patterns());
                from0 = to;
            }
        }
        return 0;
    }

    /** {@code --step} (cờ), {@code --detail 0|1|2} (giá trị) tách khỏi các cặp key/value còn lại. */
    record StreamFlags(boolean step, int detail, String[] rest) {

        static StreamFlags split(String[] args, int from) {
            boolean step = false;
            boolean detailGiven = false;
            int detail = 2;
            List<String> rest = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (i >= from && a.equals("--step")) {
                    step = true;
                    continue;
                }
                if (i >= from && a.equals("--detail")) {
                    if (i + 1 >= args.length) {
                        throw new CliSupport.CliException("missing value for parameter: --detail");
                    }
                    detail = parseDetail(args[++i]);
                    detailGiven = true;
                    continue;
                }
                rest.add(a);
            }
            return new StreamFlags(step || detailGiven, detail, rest.toArray(new String[0]));
        }

        private static int parseDetail(String value) {
            int d;
            try {
                d = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new CliSupport.CliException("--detail needs an integer 0..2, got: " + value);
            }
            if (d < 0 || d > 2) {
                throw new CliSupport.CliException("--detail must be 0, 1 or 2, got: " + value);
            }
            return d;
        }
    }

    /** Thread-safe renderer for progress ticks + mining events + the final DOP table. */
    static final class Printer implements MiningProgressListener, MiningEventListener {

        private final boolean stepMode;
        private Integer lastPercent;

        Printer(boolean stepMode) {
            this.stepMode = stepMode;
        }

        synchronized void line(String s) {
            System.out.println(s);
            System.out.flush();
        }

        @Override
        public synchronized void onProgress(MiningProgress progress) {
            int ll = (int) Math.floor(progress.fraction() * 100.0 / 5.0) * 5;
            if (lastPercent == null || ll != lastPercent
                    || progress.completedRootTasks() >= progress.totalRootTasks()) {
                lastPercent = ll;
                System.out.printf(Locale.ROOT, "  mining: roots %d/%d patterns=%d elapsed_ms=%d%n",
                        progress.completedRootTasks(), progress.totalRootTasks(),
                        progress.patternsFound(), progress.elapsedMs());
                System.out.flush();
            }
        }

        @Override
        public synchronized void onEvent(MiningEvent e) {
            switch (e.kind()) {
                case PATTERN -> {
                    if (!stepMode) {
                        System.out.printf(Locale.ROOT, "  pattern: %-24s DO=%.6f support=%d tids=%s%n",
                                e.key(), e.doValue(), e.support(), CliSupport.fmtTids(e.tids()));
                        System.out.flush();
                    }
                }
                case DOP_CHECK -> {
                    if (stepMode) {
                        System.out.printf(Locale.ROOT, "  step DOP_CHECK %-20s do=%.6f minSup=%.4f passed=%s%n",
                                e.key(), e.doValue(), e.minSup(), e.passed());
                        System.out.flush();
                    }
                }
                case UBDO -> {
                    if (stepMode) {
                        System.out.printf(Locale.ROOT, "  step UBDO      %-20s ubdo=%.6f z=%.4f minSup=%.4f passed=%s%n",
                                e.key(), e.ubdo(), e.z(), e.minSup(), e.passed());
                        System.out.flush();
                    }
                }
            }
        }

        /** Final DOP list for a completed part (sorted by DO desc). */
        synchronized void dopList(List<Pattern> patterns) {
            List<Pattern> sorted = new ArrayList<>(patterns);
            sorted.sort(Comparator.comparingDouble(Pattern::dampedOccupancy).reversed());
            System.out.println("DOP (" + sorted.size() + "):");
            int i = 1;
            for (Pattern pt : sorted) {
                System.out.printf(Locale.ROOT, "  %3d. %-24s DO=%.6f support=%d%n",
                        i++, pt.canonicalKey(), pt.dampedOccupancy(), pt.tids().length);
            }
            System.out.flush();
        }
    }
}
