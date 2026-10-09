package dhopm.v2.cli;

import dhopm.common.transaction.Transaction;
import dhopm.common.window.WindowMath;

import java.util.List;

/**
 * Lệnh {@code inspect}: thống kê dataset + cấu hình + cửa sổ mà KHÔNG mining (như V1, bổ sung W).
 * Dùng để nhìn nhanh kích thước dữ liệu trước khi chọn (∂, f, minOcc) cho các kịch bản tăng trưởng.
 */
final class InspectCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.requireDataset(CliSupport.parse(args, from));
        CliSupport.Loaded ld = CliSupport.load(p);
        List<Transaction> all = ld.transactions();

        int maxLen = 0;
        for (Transaction t : all) {
            maxLen = Math.max(maxLen, t.length());
        }
        System.out.println(CliSupport.header(p));
        String note = CliSupport.limitNote(ld);
        if (!note.isEmpty()) {
            System.out.println(note);
        }
        System.out.println("transactions = " + all.size());
        if (!all.isEmpty()) {
            System.out.println("last_tid     = " + all.get(all.size() - 1).tid());
        }
        System.out.println("max_len      = " + maxLen);
        long w = WindowMath.computeWindow(p.f(), p.minOcc());
        if (w == WindowMath.INFINITE) {
            System.out.println("window       = INFINITE (paper mode)");
            System.out.println("N_eff        = " + all.size());
            System.out.println("minSup       = " + CliSupport.fmt4(p.partial() * all.size()));
        } else {
            long nEff = WindowMath.effectiveTransactions(all.isEmpty() ? 0 : all.get(all.size() - 1).tid(), w);
            System.out.println("window       = W=" + w + " N_eff=" + nEff
                    + " Z=" + CliSupport.fmt4(WindowMath.maxDO(p.f(), all.isEmpty() ? 0 : all.get(all.size() - 1).tid()))
                    + " dp_max(asym)=" + CliSupport.fmt4(WindowMath.maxPartialAsymptotic(p.f(), p.minOcc())));
            System.out.println("minSup       = " + CliSupport.fmt4(p.partial() * nEff));
        }
        return 0;
    }
}