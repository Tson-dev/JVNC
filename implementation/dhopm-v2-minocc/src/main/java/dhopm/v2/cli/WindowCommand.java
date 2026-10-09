package dhopm.v2.cli;

import dhopm.common.window.WindowMath;

import java.util.Locale;

/**
 * Lệnh {@code window}: tra cứu cửa sổ suy giảm KHÔNG cần dataset (G2-D14). Vì TL chưa biết,
 * chỉ in biên cực đại ASYMPTOTIC {@code dp_max → 1/((1−f)·W)} kèm hậu tố "%"; biên chính xác
 * {@code Z/N_eff} là {@code null} cho tới khi có TL (xem {@code validate --dataset}).
 */
final class WindowCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        CliSupport.Params p = CliSupport.parse(args, from);
        double f = p.f();
        double minOcc = p.minOcc();
        System.out.printf(Locale.ROOT, "damped window lookup: f=%s minOcc=%s%n",
                CliSupport.fmt(f), CliSupport.fmt(minOcc));

        try {
            long w = WindowMath.computeWindow(f, minOcc);
            if (w == WindowMath.INFINITE) {
                System.out.println("  W(f,minOcc) = INFINITE (no window: minOcc=0 or f=1) => paper mode");
                System.out.println("  dp_max(asymptotic) = 1.0  (100 %)");
            } else {
                double asym = WindowMath.maxPartialAsymptotic(f, minOcc);
                System.out.printf(Locale.ROOT, "  W(f,minOcc) = %d%n", w);
                System.out.printf(Locale.ROOT, "  dp_max(asymptotic) = %.6f  (~%.3f %%)%n", asym, asym * 100);
                System.out.println("  dp_max(exact) = null (needs TL; run `validate --dataset <file>` for the exact bound)");
                System.out.printf(Locale.ROOT, "  tail after W: f^W/(1-f) = %.6f (<= minOcc by construction)%n",
                        Math.pow(f, w) / (1.0 - f));
            }
            return 0;
        } catch (IllegalArgumentException e) {
            System.out.println("  ERROR: " + e.getMessage());
            return 1;
        }
    }
}