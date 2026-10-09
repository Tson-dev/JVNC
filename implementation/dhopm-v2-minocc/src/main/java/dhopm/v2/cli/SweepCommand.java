package dhopm.v2.cli;

import dhopm.common.transaction.Transaction;
import dhopm.v2.engine.MiningEngineV2;

import java.util.List;

/**
 * Lệnh {@code sweep}: quét tổ hợp tham số (∂, f, minOcc) (G2-M6). Đầu vào là grid:
 * {@code --partials "0.05,0.1,0.2"} {@code --fs "0.8,0.9"} {@code --minOccs "0,1e-6,1e-3"}.
 * Với mỗi tổ hợp chạy mine trên một phần đầu (hoặc toàn bộ khi không có {@code --limit})
 * và in một bảng {@code W | N_eff | minSup | patterns | ms}. Dataset là bắt buộc.
 */
final class SweepCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        String[] partials = {"0.10", "0.15", "0.20"};
        String[] fs = {"0.90"};
        String[] minOccs = {"0", "1e-6", "1e-3"};
        for (int i = from; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--partials" -> partials = split(args[i + 1]);
                case "--fs" -> fs = split(args[i + 1]);
                case "--minOccs" -> minOccs = split(args[i + 1]);
                default -> { /* standard keys are re-parsed below */ }
            }
        }

        // Standard options (wrong grid keys would already have been rejected).
        List<String> standard = new java.util.ArrayList<>();
        for (int i = from; i + 1 < args.length; i += 2) {
            boolean grid = args[i].equals("--partials") || args[i].equals("--fs") || args[i].equals("--minOccs");
            if (!grid) {
                standard.add(args[i]);
                standard.add(args[i + 1]);
            }
        }
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(standard.toArray(new String[0]), 0));

        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();
        System.out.println(CliSupport.header(p));
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        System.out.printf("sweep: %d partials x %d fs x %d minOccs = %d runs (tx=%d)%n",
                partials.length, fs.length, minOccs.length, partials.length * fs.length * minOccs.length,
                all.size());
        System.out.printf("%8s %8s %10s %9s %8s %10s %10s %9s%n",
                "partial", "f", "minOcc", "W", "N_eff", "minSup", "patterns", "run_ms");

        int runs = 0;
        for (String partial : partials) {
            for (String f : fs) {
                for (String minOcc : minOccs) {
                    dhopm.common.config.MiningConfig config = new dhopm.common.config.MiningConfig(
                            CliSupport.parseNum(partial), CliSupport.parseNum(f), p.epsilon(),
                            CliSupport.parseNum(minOcc), p.workers());
                    long r0 = System.nanoTime();
                    try (MiningEngineV2 engine = new MiningEngineV2(config)) {
                        engine.loadBatch(all);
                        var r = engine.mineNow();
                        long ms = (System.nanoTime() - r0) / 1_000_000L;
                        runs++;
                        var w = engine.windowInfo();
                        System.out.printf("%8s %8s %10s %9s %8s %10s %10s %9d%n",
                                partial, f, minOcc, w.isInfinite() ? "inf" : Long.toString(w.windowSize()),
                                w.effectiveTransactions(), CliSupport.fmt4(r.minSup()), r.patterns().size(), ms);
                    } catch (IllegalArgumentException e) {
                        runs++;
                        System.out.printf("%8s %8s %10s %9s %8s %10s %10s %9s%n",
                                partial, f, minOcc, "-", "-", "-", "-", "config-err");
                    }
                }
            }
        }
        System.out.printf("sweep total: runs=%d%n", runs);
        return 0;
    }

    private static String[] split(String text) throws CliSupport.CliException {
        String[] parts = text.trim().split(",");
        if (parts.length == 0) {
            throw new CliSupport.CliException("empty sweep list");
        }
        return parts;
    }
}