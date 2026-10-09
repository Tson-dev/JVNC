package dhopm.common.window;

import dhopm.common.config.MiningConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter validator (plan 02 §5.4 / D19 · D30 · D31 · D38 · G2-D14).
 *
 * <p>Produces issues with STABLE codes (M6 / CLI {@code validate}):
 * <ul>
 *   <li>{@code MIN_OCC_TOO_LARGE}   (error)   — minOcc ≥ 1/(1−f) ⇒ W = 0 (D30)</li>
 *   <li>{@code WINDOW_TOO_LARGE}    (error)   — W exceeds maxWindow (allocation guard)</li>
 *   <li>{@code PARTIAL_ZERO}        (warning) — ∂ = 0 ⇒ minSup = 0 ⇒ output blow-up (D31)</li>
 *   <li>{@code INFEASIBLE_PARTIAL}  (warning) — ∂ × N_eff &gt; Z(f,TL): the DOP set is provably empty.
 *       Uses the EXACT bound {@code Z/N_eff} (D38); when TL is unknown it is reported as approximate.</li>
 * </ul>
 * Domain checks of {@code MiningConfig} itself (∂∉[0,1], f∉(0,1], ε≤0, minOcc∉[0,1)) throw at
 * construction time (fail-fast); this class only checks cross-parameter / feasibility rules.
 */
public final class ParameterValidator {

    private ParameterValidator() {
    }

    /** One validation issue. */
    public record Issue(Severity severity, String code, String message) {
        @Override
        public String toString() {
            return severity + " [" + code + "] " + message;
        }
    }

    public enum Severity {ERROR, WARNING}

    public static final String MIN_OCC_TOO_LARGE = "MIN_OCC_TOO_LARGE";
    public static final String WINDOW_TOO_LARGE = "WINDOW_TOO_LARGE";
    public static final String PARTIAL_ZERO = "PARTIAL_ZERO";
    public static final String INFEASIBLE_PARTIAL = "INFEASIBLE_PARTIAL";

    /**
     * Validates the config knowing the stream length {@code tl} (exact feasibility, D38).
     * Called before every {@code mineNow()} by the V2 engine.
     */
    public static List<Issue> validate(MiningConfig config, long tl) {
        return validateDomain(config, tl);
    }

    /**
     * Validates the config WITHOUT knowing the stream length. Only the domain rules can be decided;
     * feasibility is limited to the asymptotic estimate and is explicitly labelled (G2-D14).
     */
    public static List<Issue> validate(MiningConfig config) {
        return validateDomain(config, Long.MIN_VALUE);
    }

    private static List<Issue> validateDomain(MiningConfig config, long tl) {
        List<Issue> issues = new ArrayList<>();
        double f = config.decayFactor();

        if (config.minOcc() > 0.0 && f < 1.0 && config.minOcc() >= 1.0 / (1.0 - f)) {
            issues.add(new Issue(Severity.ERROR, MIN_OCC_TOO_LARGE,
                    "minOcc = " + config.minOcc() + " ≥ 1/(1−f) = " + (1.0 / (1.0 - f))
                            + " ⇒ W = 0 (empty window); choose a smaller minOcc or a larger f"));
        }

        long w;
        try {
            w = WindowMath.computeWindow(f, config.minOcc());
        } catch (IllegalArgumentException e) {
            // window already reported above (or overflow); stop the feasibility math here
            return issues.size() > 0 ? issues : List.of(new Issue(Severity.ERROR, WINDOW_TOO_LARGE, e.getMessage()));
        }

        if (f > 0.9 && config.minOcc() > 0.0 && w != WindowMath.INFINITE && w > WindowMath.DEFAULT_MAX_WINDOW) {
            issues.add(new Issue(Severity.ERROR, WINDOW_TOO_LARGE,
                    "W(f,minOcc) = " + w + " > maxWindow = " + WindowMath.DEFAULT_MAX_WINDOW + "; cannot allocate"));
        }

        if (config.partial() == 0.0) {
            issues.add(new Issue(Severity.WARNING, PARTIAL_ZERO,
                    "∂ = 0 ⇒ minSup = 0 ⇒ EVERY non-empty pattern is a DOP; output may explode (D31)"));
        }

        if (tl != Long.MIN_VALUE) {
            double minSup = WindowMath.minSup(config.partial(), tl, w);
            double z = WindowMath.maxDO(f, tl);
            if (minSup > z + config.epsilon()) {
                issues.add(new Issue(Severity.WARNING, INFEASIBLE_PARTIAL,
                        "minSup = ∂×N_eff = " + minSup + " > Z(f,TL) = " + z
                                + " ⇒ the DOP set is provably EMPTY; reduce ∂ or increase f"
                                + " (exact feasible bound: ∂ ≤ " + WindowMath.maxPartialExact(f, config.minOcc(), tl)
                                + ")"));
            }
        }
        return issues;
    }
}