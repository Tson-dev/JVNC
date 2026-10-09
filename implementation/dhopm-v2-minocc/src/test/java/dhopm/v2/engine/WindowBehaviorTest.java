package dhopm.v2.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.testkit.GoldenAssert;
import dhopm.common.testkit.GoldenCases;
import dhopm.common.transaction.Transaction;
import dhopm.common.window.ParameterValidator;
import dhopm.common.window.WindowMath;
import dhopm.v1.engine.MiningEngine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC9–TC18 + edge cases E1–E10 (plan 02 §6, OVERALL-PLAN §6.4).
 *
 * <p>Because the docs carry contradictions (notably the E7 boundary vs the minOcc∈[0,1) domain), these
 * tests pin the SEMANTICS the implementation commits to; full spec conformance is early access.
 */
class WindowBehaviorTest {

    private static Transaction tr(int tid, String... items) {
        return new Transaction(tid, items);
    }

    private static MineResult v1(double partial, double f, List<Transaction> stream, int workers) {
        try (MiningEngine e = new MiningEngine(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    private static MineResult v2(double partial, double f, double minOcc, List<Transaction> stream, int workers) {
        try (MiningEngineV2 e = new MiningEngineV2(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, minOcc, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    private static Map<String, Double> toMap(List<Pattern> patterns) {
        Map<String, Double> m = new HashMap<>();
        for (Pattern p : patterns) {
            m.put(p.canonicalKey(), p.dampedOccupancy());
        }
        return m;
    }

    // --- TC10: window wider than the stream ⇒ ≡ paper --------------------------------
    @Test
    void tc10_windowWiderThanStreamEqualsPaper() {
        // minOcc=0.5, f=0.9 → W = ceil(ln(0.05)/ln 0.9) = 29 ≥ TL=8
        assertEquals(29, WindowMath.computeWindow(0.9, 0.5));
        MineResult paper = v1(0.15, 0.9, GoldenCases.baseStream(), 4);
        MineResult win = v2(0.15, 0.9, 0.5, GoldenCases.baseStream(), 4);
        assertTrue(GoldenAssert.samePatternSet(paper, win), "W ≥ TL must not truncate anything (TC10)");
        assertEquals(paper.minSup(), win.minSup(), 1e-12);
    }

    // --- TC11: narrow window, INV-G |DO_win − DO_full| ≤ minOcc -----------------------------
    @Test
    void tc11_narrowWindowInvG() {
        // f=0.5, minOcc=0.5 → W=2, TL=8. Tail dropped per item = 0.5²/0.5 = 0.5 ≤ minOcc.
        assertEquals(2, WindowMath.computeWindow(0.5, 0.5));
        double partial = 0.35;
        MineResult full = v1(partial, 0.5, GoldenCases.baseStream(), 4);
        MineResult win = v2(partial, 0.5, 0.5, GoldenCases.baseStream(), 4);

        Map<String, Double> fullMap = toMap(full.patterns());
        Map<String, Double> winMap = toMap(win.patterns());
        for (Map.Entry<String, Double> e : fullMap.entrySet()) {
            double w = winMap.getOrDefault(e.getKey(), 0.0);
            assertTrue(e.getValue() - w <= 0.5 + 1e-9,
                    "INV-G violated for " + e.getKey() + ": full=" + e.getValue() + " win=" + w);
        }
        // Every DOP_full stays a DOP_win (two-phase minSup is never above the full-run threshold in a
        // way that would drop a full DOP: DO_win ≥ DO_full − minOcc and minSup_win ≤ minSup_full).
        for (String key : fullMap.keySet()) {
            assertTrue(winMap.containsKey(key), "DOP_full " + key + " lost in the window run");
        }
    }

    // --- TC12: f = 1 + minOcc > 0 ⇒ W = ∞ ⇒ ≡ TC5 -----------------------------------------
    @Test
    void tc12_factoryOneKeepsFullWindow() {
        MineResult noDecay = v1(0.15, 1.0, GoldenCases.baseStream(), 4);
        MineResult win = v2(0.15, 1.0, 1e-3, GoldenCases.baseStream(), 4);
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(0.15, 1.0, MiningConfig.DEFAULT_EPSILON, 1e-3, 4))) {
            assertTrue(e.windowInfo().isInfinite(), "f=1 ⇒ W=∞ regardless of minOcc");
        }
        assertTrue(GoldenAssert.samePatternSet(noDecay, win), "f=1 must behave as paper (TC12)");
    }

    // --- TC13: dead prefix, head advances, support = size − head -----------------------
    @Test
    void tc13_deadPrefixInvariantAndTruncatedEquivalence() {
        // f=0.5, minOcc=0.3 → W=3.
        assertEquals(3, WindowMath.computeWindow(0.5, 0.3));
        List<Transaction> stream = List.of(
                tr(1, "A", "B"), tr(2, "B", "C"), tr(3, "A", "C"),
                tr(4, "A", "B"), tr(5, "B", "C"), tr(6, "A", "C"),
                tr(7, "A", "B"), tr(8, "B", "C"), tr(9, "A", "C"), tr(10, "A", "B", "C"));
        double partial = 0.4;

        List<Transaction> truncated = stream.subList(stream.size() - 3, stream.size());
        MineResult expected = v1(partial, 0.5, truncated, 1);

        // Load one batch (bulk). Every of the last 3 tids is written; older ones are skipped.
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(partial, 0.5, MiningConfig.DEFAULT_EPSILON, 0.3, 1))) {
            e.loadBatch(stream);
            MineResult actual = e.mineNow();
            assertTrue(GoldenAssert.samePatternSet(expected, actual),
                    "window(10 txs) must equal V1 on the last W=3 transactions");
            // Bulk skip: only the outer txs fall inside the final window, nothing is ever evicted,
            // but the live entry count equals the sum of the last-3 transaction lengths.
            assertEquals(3 + 2 + 2, e.windowInfo().liveEntries(), "live entries = last 3 tx lengths");
            assertEquals(0, e.deadEntryCount(), "bulk skip never materializes dead entries");
        }

        // Streaming one-by-one: every tx is written, eviction creates the dead prefix.
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(partial, 0.5, MiningConfig.DEFAULT_EPSILON, 0.3, 1))) {
            for (Transaction t : stream) {
                e.loadBatch(List.of(t));
            }
            MineResult actual = e.mineNow();
            assertTrue(GoldenAssert.samePatternSet(expected, actual),
                    "streamed load must give the same last-W result as bulk/V1-truncated (TC13)");
            assertTrue(e.evictionCount() == stream.size() - 3, "7 evictions for W=3, TL=10");
            assertEquals(e.globalEntryCount(), e.windowInfo().liveEntries() + e.windowInfo().deadEntries());
            assertTrue(e.windowInfo().deadEntries() > 0, "dead prefix must exist after evictions");
        }
    }

    // --- TC14: two-phase minSup (∂×min(TL,W)): grows then freezes ----------------------
    @Test
    void tc14_twoPhaseMinSupFreezesAtWindow() {
        // f=0.9, minOcc=0.4 → W=31.
        assertEquals(31, WindowMath.computeWindow(0.9, 0.4));
        double partial = 0.5;
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            stream.add(tr(i, "A"));
        }
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(partial, 0.9, MiningConfig.DEFAULT_EPSILON, 0.4, 1))) {
            e.loadBatch(stream.subList(0, 20)); // TL=20 < W → minSup = ∂×20
            assertEquals(0.5 * 20, e.mineNow().minSup(), 1e-12, "phase 1 uses ∂×TL");

            e.loadBatch(stream.subList(20, 50)); // TL=50 ≥ W → minSup = ∂×W (frozen)
            assertEquals(0.5 * 31, e.mineNow().minSup(), 1e-12, "two-phase minSup freezes at ∂×W");

            e.loadBatch(stream.subList(50, 60)); // TL=60 → still ∂×W
            assertEquals(0.5 * 31, e.mineNow().minSup(), 1e-12);
        }
    }

    // --- TC15: config validation is fail-fast ------------------------------------------
    @Test
    void tc15_failFastOnInvalidConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 5.0, 1),
                "minOcc ∉ [0,1) must be rejected by the config record");
        // f very close to 1, small minOcc → W ≈ 2.7e7 > maxWindow → WINDOW_TOO_LARGE at engine creation.
        assertThrows(IllegalArgumentException.class,
                () -> new MiningEngineV2(new MiningConfig(0.15, 0.999999, MiningConfig.DEFAULT_EPSILON, 1e-6, 1)));
    }

    // --- TC16 / E3–E5: validator + O(1) short-circuit ∅ --------------------------------
    @Test
    void tc16_shortCircuitWhenMinSupExceedsDoCeiling() {
        // f=0.9, minOcc=1e-6 → W=153; TL=200 ⇒ N_eff=153; ∂=0.15 ⇒ minSup=22.95; Z(0.9,200)≈9.999 < 22.95.
        assertEquals(153, WindowMath.computeWindow(0.9, 1e-6));
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            stream.add(tr(i, "A", "B", "C"));
        }
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 1e-6, 1))) {
            e.loadBatch(stream);
            MineResult r = e.mineNow();
            assertTrue(r.patterns().isEmpty(), "∂×N_eff > Z ⇒ provably empty (E3/E4/E5-style)");
            assertTrue(e.warnings().stream().anyMatch(i -> i.code().equals(ParameterValidator.INFEASIBLE_PARTIAL)),
                    "must surface an INFEASIBLE_PARTIAL warning");
            assertEquals(WindowMath.maxDO(0.9, 200), e.windowInfo().maxDO(), 1e-12);
            assertEquals(0.15 * 153, e.windowInfo().minSup(), 1e-12);
        }
    }

    // --- TC17: Draft-Idea "item A" — appears 1000× outside + once inside the window -----
    @Test
    void tc17_itemAOutsideWindowHasNoLiveSupport() {
        // f=0.5, minOcc=0.5 → W=2. A in 1..999, then C at 1000, A at 1001 ⇒ live support(A)=1.
        assertEquals(2, WindowMath.computeWindow(0.5, 0.5));
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 999; i++) {
            stream.add(tr(i, "A"));
        }
        stream.add(tr(1000, "C"));
        stream.add(tr(1001, "A"));
        double partial = 0.8; // minSup_win = 0.8×2 = 1.6 > support_sống(A)=1 ⇒ A pruned by C2.
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(partial, 0.5, MiningConfig.DEFAULT_EPSILON, 0.5, 1))) {
            e.loadBatch(stream);
            MineResult r = e.mineNow();
            assertFalse(r.patterns().stream().anyMatch(p -> p.canonicalKey().equals("A")),
                    "A's 1000 historic occurrences must not count (TC17)");
        }
    }

    // --- TC18: window vs full recompute across (∂,f,minOcc) ---------------------------------
    @Test
    void tc18_windowEqualsTruncatedRecomputeOverCombos() {
        double[][] combos = {
                {0.9, 0.3, 0.35}, // f, minOcc, ∂  (W=3)
                {0.9, 1e-6, 0.10}, // W=153 > TL=8 → pure paper (also TC10)
                {0.5, 0.3, 0.40}, // W=3
                {0.8, 1e-2, 0.15}, // W=55 > 8
                {0.8, 3e-1, 0.20}, // W = ceil(ln(0.06)/ln0.8) = 14 > 8
        };
        for (double[] combo : combos) {
            double f = combo[0], eps = combo[1], partial = combo[2];
            long w = WindowMath.computeWindow(f, eps);
            List<Transaction> stream = GoldenCases.baseStream();
            List<Transaction> truncated = (w != WindowMath.INFINITE && w < stream.size())
                    ? stream.subList(stream.size() - (int) w, stream.size())
                    : stream;

            MineResult win = v2(partial, f, eps, stream, 2);
            MineResult expected = v1(partial, f, truncated, 2);
            assertTrue(GoldenAssert.samePatternSet(expected, win),
                    "window result must equal V1 on the last-W recompute (f=" + f + ", minOcc=" + eps + ")");
        }
    }

    // --- E1–E10 quick edge checks --------------------------------------------------------
    @Test
    void e1_minOccZeroIsPaper() {
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 0.0, 1))) {
            e.loadBatch(GoldenCases.baseStream());
            assertTrue(e.windowInfo().isInfinite());
            assertTrue(GoldenAssert.samePatternSet(
                    v1(0.15, 0.9, GoldenCases.baseStream(), 1), e.mineNow()));
        }
    }

    @Test
    void e2_decayOneIsDegenerateButRuns() {
        // f=1 ⇒ W=∞. ∂=1.0: only a pattern with DO = TL can survive (degenerate, E2).
        // A single-item stream gives {A}: DO = 8 = minSup ⇒ exactly one DOP.
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            stream.add(tr(i, "A"));
        }
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(1.0, 1.0, MiningConfig.DEFAULT_EPSILON, 1e-3, 1))) {
            e.loadBatch(stream);
            MineResult r = e.mineNow();
            assertTrue(e.windowInfo().isInfinite(), "f=1 ⇒ W=∞");
            assertEquals(1, r.patterns().size());
            assertEquals("A", r.patterns().get(0).canonicalKey());
            assertEquals(8.0, r.patterns().get(0).dampedOccupancy(), 1e-12);
        }
    }

    @Test
    void e8_partialZeroWarnsAndExplodesOutput() {
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(0.0, 0.9, MiningConfig.DEFAULT_EPSILON, 1e-6, 1))) {
            e.loadBatch(GoldenCases.baseStream());
            MineResult r = e.mineNow();
            assertTrue(r.patterns().size() > 4, "∂=0 ⇒ every non-empty pattern is a DOP");
            assertTrue(e.warnings().stream().anyMatch(i -> i.code().equals(ParameterValidator.PARTIAL_ZERO)));
        }
    }

    @Test
    void e9_tlBelowWindowIsPhaseOne() {
        // f=0.9, minOcc=1e-6 → W=153; TL=4 < W ⇒ minSup = ∂×4 (phase 1) = paper.
        List<Transaction> db0 = GoldenCases.baseStream().subList(0, 4);
        MineResult win = v2(0.15, 0.9, 1e-6, db0, 1);
        assertEquals(0.15 * 4, win.minSup(), 1e-12);
        assertTrue(GoldenAssert.samePatternSet(v1(0.15, 0.9, db0, 1), win), "E9: TL<W ≡ paper");
    }

    @Test
    void e6_e10_feasiblePartialRuns() {
        // ∂=0.005 (E6) on a 200-tx stream, N_eff=153 ⇒ minSup=0.765 < Z ⇒ non-empty results.
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            stream.add(tr(i, "A", "B", "C"));
        }
        MineResult e6 = v2(0.005, 0.9, 1e-6, stream, 2);
        assertFalse(e6.patterns().isEmpty(), "E6: feasible small ∂ must yield patterns");
        assertEquals(0.005 * 153, e6.minSup(), 1e-12);

        // E10: ∂=0.06, TL=10 < W=153 ⇒ minSup = 0.6 (phase 1).
        MineResult e10 = v2(0.06, 0.9, 1e-6, GoldenCases.baseStream(), 2);
        assertEquals(0.06 * 8, e10.minSup(), 1e-12);
        assertFalse(e10.patterns().isEmpty());
    }
}