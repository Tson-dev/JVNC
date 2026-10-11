package dhopm.v3.engine;

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
 * V3 windowed-behavior acceptance, mirroring V2's TC9–TC18 / E1–E10 where relevant, PLUS the
 * decisive V3 ≡ V2 correspondence: V3 must produce the exact same DOP set (bit-for-bit DO) as
 * V2 for the same (∂, f, minOcc).
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
        try (dhopm.v2.engine.MiningEngineV2 e = new dhopm.v2.engine.MiningEngineV2(
                new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, minOcc, workers))) {
            e.loadBatch(stream);
            return e.mineNow();
        }
    }

    private static MineResult v3(double partial, double f, double minOcc, List<Transaction> stream, int workers) {
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(partial, f, MiningConfig.DEFAULT_EPSILON, minOcc, workers))) {
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

    // --- decisive: V3 ≡ V2 across (∂, f, minOcc) ----------------------------------------
    @Test
    void v3EqualsV2AcrossCombos() {
        double[][] combos = {
                {0.9, 0.3, 0.35},   // W=3
                {0.9, 1e-6, 0.10},  // W=153 > TL=8 (paper-equivalent)
                {0.5, 0.3, 0.40},   // W=3
                {0.8, 1e-2, 0.15},  // W=55 > 8
                {0.8, 3e-1, 0.20},  // W=14 > 8
                {0.5, 0.5, 0.35},   // W=2
                {0.9, 0.5, 0.15},   // W=29 > 8
        };
        for (double[] combo : combos) {
            double f = combo[0], minOcc = combo[1], partial = combo[2];
            List<Transaction> stream = GoldenCases.baseStream();
            MineResult a = v2(partial, f, minOcc, stream, 2);
            MineResult b = v3(partial, f, minOcc, stream, 2);
            assertTrue(GoldenAssert.samePatternSet(a, b),
                    "V3 must be bit-for-bit equal to V2 (f=" + f + ", minOcc=" + minOcc + ", ∂=" + partial + ")");
            assertEquals(a.minSup(), b.minSup(), 0.0, "minSup must match V2");
            assertEquals(a.patterns().size(), b.patterns().size(), "pattern count must match V2");
        }
    }

    // --- TC10: window wider than the stream ⇒ ≡ paper ------------------------------------
    @Test
    void tc10_windowWiderThanStreamEqualsPaper() {
        assertEquals(29, WindowMath.computeWindow(0.9, 0.5));
        MineResult paper = v1(0.15, 0.9, GoldenCases.baseStream(), 4);
        MineResult win = v3(0.15, 0.9, 0.5, GoldenCases.baseStream(), 4);
        assertTrue(GoldenAssert.samePatternSet(paper, win), "W ≥ TL must not truncate anything (TC10)");
        assertEquals(paper.minSup(), win.minSup(), 1e-12);
    }

    // --- TC11: narrow window, INV-G |DO_win − DO_full| ≤ minOcc ----------------------------
    @Test
    void tc11_narrowWindowInvG() {
        assertEquals(2, WindowMath.computeWindow(0.5, 0.5));
        double partial = 0.35;
        MineResult full = v1(partial, 0.5, GoldenCases.baseStream(), 4);
        MineResult win = v3(partial, 0.5, 0.5, GoldenCases.baseStream(), 4);

        Map<String, Double> fullMap = toMap(full.patterns());
        Map<String, Double> winMap = toMap(win.patterns());
        for (Map.Entry<String, Double> e : fullMap.entrySet()) {
            double w = winMap.getOrDefault(e.getKey(), 0.0);
            assertTrue(e.getValue() - w <= 0.5 + 1e-9,
                    "INV-G violated for " + e.getKey() + ": full=" + e.getValue() + " win=" + w);
        }
        for (String key : fullMap.keySet()) {
            assertTrue(winMap.containsKey(key), "DOP_full " + key + " lost in the window run");
        }
    }

    // --- TC12: f = 1 + minOcc > 0 ⇒ W = ∞ ⇒ ≡ no-decay paper -------------------------------
    @Test
    void tc12_factoryOneKeepsFullWindow() {
        MineResult noDecay = v1(0.15, 1.0, GoldenCases.baseStream(), 4);
        MineResult win = v3(0.15, 1.0, 1e-3, GoldenCases.baseStream(), 4);
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(0.15, 1.0, MiningConfig.DEFAULT_EPSILON, 1e-3, 4))) {
            assertTrue(e.windowInfo().isInfinite(), "f=1 ⇒ W=∞ regardless of minOcc");
        }
        assertTrue(GoldenAssert.samePatternSet(noDecay, win), "f=1 must behave as paper (TC12)");
    }

    // --- TC13: dead prefix, head advances, support = size − head ---------------------------
    @Test
    void tc13_deadPrefixInvariantAndTruncatedEquivalence() {
        assertEquals(3, WindowMath.computeWindow(0.5, 0.3));
        List<Transaction> stream = List.of(
                tr(1, "A", "B"), tr(2, "B", "C"), tr(3, "A", "C"),
                tr(4, "A", "B"), tr(5, "B", "C"), tr(6, "A", "C"),
                tr(7, "A", "B"), tr(8, "B", "C"), tr(9, "A", "C"), tr(10, "A", "B", "C"));
        double partial = 0.4;

        List<Transaction> truncated = stream.subList(stream.size() - 3, stream.size());
        MineResult expected = v1(partial, 0.5, truncated, 1);

        // Bulk load: only the last 3 tids are written; nothing is ever evicted.
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(partial, 0.5, MiningConfig.DEFAULT_EPSILON, 0.3, 1))) {
            e.loadBatch(stream);
            MineResult actual = e.mineNow();
            assertTrue(GoldenAssert.samePatternSet(expected, actual),
                    "window(10 txs) must equal V1 on the last W=3 transactions");
            assertEquals(2 + 2 + 3, e.windowInfo().liveEntries(), "live entries = last 3 tx lengths");
            assertEquals(0, e.deadEntryCount(), "bulk skip never materializes dead entries");
        }

        // Streaming one-by-one: every tx is written, eviction creates the dead prefix.
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(partial, 0.5, MiningConfig.DEFAULT_EPSILON, 0.3, 1))) {
            for (Transaction t : stream) {
                e.loadBatch(List.of(t));
            }
            MineResult actual = e.mineNow();
            assertTrue(GoldenAssert.samePatternSet(expected, actual),
                    "streamed load must give the same last-W result as bulk/V1-truncated (TC13)");
            assertEquals(stream.size() - 3, e.evictionCount(), "7 evictions for W=3, TL=10");
            assertEquals(e.globalEntryCount(), e.windowInfo().liveEntries() + e.windowInfo().deadEntries());
            assertTrue(e.windowInfo().deadEntries() > 0, "dead prefix must exist after evictions");
        }
    }

    // --- TC14: two-phase minSup grows then freezes -----------------------------------------
    @Test
    void tc14_twoPhaseMinSupFreezesAtWindow() {
        assertEquals(31, WindowMath.computeWindow(0.9, 0.4));
        double partial = 0.5;
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            stream.add(tr(i, "A"));
        }
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(partial, 0.9, MiningConfig.DEFAULT_EPSILON, 0.4, 1))) {
            e.loadBatch(stream.subList(0, 20));
            assertEquals(0.5 * 20, e.mineNow().minSup(), 1e-12, "phase 1 uses ∂×TL");

            e.loadBatch(stream.subList(20, 50));
            assertEquals(0.5 * 31, e.mineNow().minSup(), 1e-12, "two-phase minSup freezes at ∂×W");

            e.loadBatch(stream.subList(50, 60));
            assertEquals(0.5 * 31, e.mineNow().minSup(), 1e-12);
        }
    }

    // --- TC15: config validation is fail-fast ----------------------------------------------
    @Test
    void tc15_failFastOnInvalidConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 5.0, 1),
                "minOcc ∉ [0,1) must be rejected by the config record");
        assertThrows(IllegalArgumentException.class,
                () -> new MiningEngineV3(new MiningConfig(0.15, 0.999999, MiningConfig.DEFAULT_EPSILON, 1e-6, 1)));
    }

    // --- TC16: O(1) short-circuit ∅ ---------------------------------------------------------
    @Test
    void tc16_shortCircuitWhenMinSupExceedsDoCeiling() {
        assertEquals(153, WindowMath.computeWindow(0.9, 1e-6));
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            stream.add(tr(i, "A", "B", "C"));
        }
        try (MiningEngineV3 e = new MiningEngineV3(
                new MiningConfig(0.15, 0.9, MiningConfig.DEFAULT_EPSILON, 1e-6, 1))) {
            e.loadBatch(stream);
            MineResult r = e.mineNow();
            assertTrue(r.patterns().isEmpty(), "∂×N_eff > Z ⇒ provably empty");
            assertTrue(e.warnings().stream().anyMatch(i -> i.code().equals(ParameterValidator.INFEASIBLE_PARTIAL)),
                    "must surface an INFEASIBLE_PARTIAL warning");
            assertEquals(WindowMath.maxDO(0.9, 200), e.windowInfo().maxDO(), 1e-12);
            assertEquals(0.15 * 153, e.windowInfo().minSup(), 1e-12);
        }
    }

    // --- TC17: item A — appears 1000× outside + once inside the window ----------------------
    @Test
    void tc17_itemAOutsideWindowHasNoLiveSupport() {
        assertEquals(2, WindowMath.computeWindow(0.5, 0.5));
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 999; i++) {
            stream.add(tr(i, "A"));
        }
        stream.add(tr(1000, "C"));
        stream.add(tr(1001, "A"));
        try (MiningEngineV3 e = new MiningEngineV3(
                new MiningConfig(0.8, 0.5, MiningConfig.DEFAULT_EPSILON, 0.5, 1))) {
            e.loadBatch(stream);
            MineResult r = e.mineNow();
            assertFalse(r.patterns().stream().anyMatch(p -> p.canonicalKey().equals("A")),
                    "A's 1000 historic occurrences must not count (TC17)");
        }
    }

    // --- TC18: window vs full recompute across combos --------------------------------------
    @Test
    void tc18_windowEqualsTruncatedRecomputeOverCombos() {
        double[][] combos = {
                {0.9, 0.3, 0.35}, // f, minOcc, ∂  (W=3)
                {0.9, 1e-6, 0.10}, // W=153 > TL=8 → pure paper
                {0.5, 0.3, 0.40}, // W=3
                {0.8, 1e-2, 0.15}, // W=55 > 8
                {0.8, 3e-1, 0.20}, // W=14 > 8
        };
        for (double[] combo : combos) {
            double f = combo[0], minOcc = combo[1], partial = combo[2];
            long w = WindowMath.computeWindow(f, minOcc);
            List<Transaction> stream = GoldenCases.baseStream();
            List<Transaction> truncated = (w != WindowMath.INFINITE && w < stream.size())
                    ? stream.subList(stream.size() - (int) w, stream.size())
                    : stream;

            MineResult win = v3(partial, f, minOcc, stream, 2);
            MineResult expected = v1(partial, f, truncated, 2);
            assertTrue(GoldenAssert.samePatternSet(expected, win),
                    "window result must equal V1 on the last-W recompute (f=" + f + ", minOcc=" + minOcc + ")");
        }
    }

    // --- E2: f=1 degenerate but runs --------------------------------------------------------
    @Test
    void e2_decayOneIsDegenerateButRuns() {
        List<Transaction> stream = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            stream.add(tr(i, "A"));
        }
        try (MiningEngineV3 e = new MiningEngineV3(new MiningConfig(1.0, 1.0, MiningConfig.DEFAULT_EPSILON, 1e-3, 1))) {
            e.loadBatch(stream);
            MineResult r = e.mineNow();
            assertTrue(e.windowInfo().isInfinite(), "f=1 ⇒ W=∞");
            assertEquals(1, r.patterns().size());
            assertEquals("A", r.patterns().get(0).canonicalKey());
            assertEquals(8.0, r.patterns().get(0).dampedOccupancy(), 1e-12);
        }
    }
}