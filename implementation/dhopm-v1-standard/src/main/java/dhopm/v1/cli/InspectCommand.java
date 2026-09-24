package dhopm.v1.cli;

import dhopm.common.transaction.Transaction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Lệnh {@code inspect}: thống kê dataset/config KHÔNG mining — tổng số giao dịch, distinct
 * items, độ dài trung bình/max, Σ entries, minSup theo ∂, top items theo support.
 */
final class InspectCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(args, from));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        long entries = 0;
        int maxLen = 0;
        long lenSum = 0;
        Map<String, Integer> freq = new HashMap<>();
        int lastTid = 0;
        for (Transaction t : all) {
            entries += t.length();
            lenSum += t.length();
            maxLen = Math.max(maxLen, t.length());
            lastTid = Math.max(lastTid, t.tid());
            for (String item : t.items()) {
                freq.merge(item, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> topItems = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.max(1, p.top()))
                .toList();

        System.out.println("== " + CliSupport.header(p) + " / inspect ==");
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        System.out.printf(Locale.ROOT,
                "  transactions=%d  lastTid=%d  distinctItems=%d  entries=%d  avgLen=%.2f  maxLen=%d%n",
                all.size(), lastTid, freq.size(), entries, entries / (double) all.size(), maxLen);
        System.out.printf(Locale.ROOT, "  minSup=partial*N=%.4f*%d=%.2f%n", p.partial(), all.size(), p.partial() * all.size());
        System.out.println("  top " + topItems.size() + " items theo support:");
        for (Map.Entry<String, Integer> e : topItems) {
            System.out.printf(Locale.ROOT, "    %-24s support=%d (%.2f%%)%n",
                    e.getKey(), e.getValue(), 100.0 * e.getValue() / all.size());
        }
        return 0;
    }
}