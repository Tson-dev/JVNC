package dhopm.v2.cli;

import dhopm.common.transaction.Transaction;
import dhopm.common.window.ParameterValidator;

import java.util.List;

/**
 * Lệnh {@code validate}: kiểm tra cấu hình (D19/D30/D31/D38/G2-D14). In mọi issue với mã ổn định
 * {@code MIN_OCC_TOO_LARGE|WINDOW_TOO_LARGE|PARTIAL_ZERO|INFEASIBLE_PARTIAL}.
 *
 * <p>Nếu có {@code --dataset}: đọc stream (hay {@code --limit} phần đầu) để biết TL và duyệt khả thi
 * CHÍNH XÁC bằng {@code Z(f,TL)/N_eff} (D38); nếu không, chỉ duyệt miền và ước lượng asymptotic.
 * Exit code 1 khi tồn tại issue {@code ERROR}.
 */
final class ValidateCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.parse(args, from);
        List<ParameterValidator.Issue> issues;
        long tl = Long.MIN_VALUE;
        if (p.dataset() != null) {
            CliSupport.Loaded ld = CliSupport.load(p);
            tl = ld.transactions().isEmpty() ? 0 : ld.transactions().get(ld.transactions().size() - 1).tid();
            issues = ParameterValidator.validate(p.config(), tl);
        } else {
            issues = ParameterValidator.validate(p.config());
        }

        System.out.println(CliSupport.header(p));
        System.out.println("== config validation ==");
        if (tl != Long.MIN_VALUE) {
            System.out.println("TL(last tid) = " + tl + " (exact feasibility, D38)");
        } else {
            System.out.println("TL unknown (domain checks + asymptotic estimate only)");
        }
        boolean error = false;
        for (ParameterValidator.Issue issue : issues) {
            System.out.println(issue);
            error |= issue.severity() == ParameterValidator.Severity.ERROR;
        }
        if (issues.isEmpty()) {
            System.out.println("OK - no issues");
        }
        System.out.println(error ? "== INVALID (errors present) ==" : "== VALID ==");
        return error ? 1 : 0;
    }
}