package dhopm.common.testkit;

import dhopm.common.contract.Pattern;
import dhopm.common.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * The canonical golden test cases TC1-TC8 (hand-run Phần 11).
 *
 * <p>The 8-transaction stream used by TC1-TC6 is the same one shipped as
 * {@code dataset/default.dat} (DB0 + DB1 + DB2 from Table 1 of the paper).
 */
public final class GoldenCases {

    private GoldenCases() {
    }

    private static Transaction tr(int tid, String... items) {
        return new Transaction(tid, items);
    }

    private static Pattern p(double doValue, String... items) {
        return new Pattern(items, doValue, new int[0]);
    }

    /** The 8-transaction stream: A C D E | A E F | B C D E | C D F | B F | D E F | A B C F | A E G */
    public static List<Transaction> baseStream() {
        return List.of(
                tr(1, "A", "C", "D", "E"),
                tr(2, "A", "E", "F"),
                tr(3, "B", "C", "D", "E"),
                tr(4, "C", "D", "F"),
                tr(5, "B", "F"),
                tr(6, "D", "E", "F"),
                tr(7, "A", "B", "C", "F"),
                tr(8, "A", "E", "G"));
    }

    /** First 4 transactions of the base stream (DB0), used by TC6. */
    public static List<Transaction> db0Stream() {
        return baseStream().subList(0, 4);
    }

    public static List<GoldenCase> all() {
        List<GoldenCase> cases = new ArrayList<>();
        cases.add(tc1());
        cases.add(tc2());
        cases.add(tc3());
        cases.add(tc4());
        cases.add(tc5());
        cases.add(tc6());
        cases.add(tc7());
        cases.add(tc8());
        return cases;
    }

    // --- TC1: paper example, basic ------------------------------------------------
    // f = 0.9, ∂ = 0.15  ->  AE=1.2601, F=1.2553
    public static GoldenCase tc1() {
        return new GoldenCase("TC1", baseStream(), 0.9, 0.15,
                List.of(p(1.2601, "A", "E"), p(1.2553, "F")));
    }

    // --- TC2: higher threshold, ∂ = 0.20 -> none
    public static GoldenCase tc2() {
        return new GoldenCase("TC2", baseStream(), 0.9, 0.20, List.of());
    }

    // --- TC3: lower threshold, ∂ = 0.10 -> 15 patterns
    public static GoldenCase tc3() {
        return new GoldenCase("TC3", baseStream(), 0.9, 0.10,
                List.of(
                        p(1.0000, "G", "A", "E"),
                        p(0.9000, "B", "A", "C", "F"),
                        p(1.1790, "B", "F"),
                        p(0.8551, "A"),
                        p(1.2601, "A", "E"),
                        p(0.8043, "A", "F"),
                        p(0.9718, "C", "D"),
                        p(0.8016, "C", "D", "E"),
                        p(0.8874, "C", "F"),
                        p(1.0744, "D", "E"),
                        p(0.8100, "D", "E", "F"),
                        p(0.9774, "D", "F"),
                        p(1.0477, "E"),
                        p(0.8943, "E", "F"),
                        p(1.2553, "F")));
    }

    // --- TC4: different decay factor f = 0.8 -> none
    public static GoldenCase tc4() {
        return new GoldenCase("TC4", baseStream(), 0.8, 0.15, List.of());
    }

    // --- TC5: no decay f = 1.0 -> classic HOP, 9 patterns (DO = O)
    public static GoldenCase tc5() {
        return new GoldenCase("TC5", baseStream(), 1.0, 0.15,
                List.of(
                        p(1.5000, "B", "F"),
                        p(1.8333, "A", "E"),
                        p(1.6667, "C", "D"),
                        p(1.5000, "C", "D", "E"),
                        p(1.6667, "D", "E"),
                        p(1.3333, "D", "F"),
                        p(1.5000, "E"),
                        p(1.3333, "E", "F"),
                        p(1.7500, "F")));
    }

    // --- TC6: scan only DB0 (4 transactions), ∂ = 0.25 -> 3 patterns
    public static GoldenCase tc6() {
        return new GoldenCase("TC6", db0Stream(), 0.9, 0.25,
                List.of(p(1.0000, "F", "C", "D"), p(1.4812, "C", "D"), p(1.2218, "C", "D", "E")));
    }

    // --- TC7: custom 10-transaction stream, ∂ = 0.15 -> 9 patterns
    public static GoldenCase tc7() {
        List<Transaction> stream = List.of(
                tr(1, "A", "B", "C"),
                tr(2, "A", "B"),
                tr(3, "B", "C", "D"),
                tr(4, "A", "C"),
                tr(5, "A", "B", "C", "D"),
                tr(6, "B", "D"),
                tr(7, "A", "C"),
                tr(8, "A", "B", "C"),
                tr(9, "B", "C", "D"),
                tr(10, "A", "B"));
        return new GoldenCase("TC7", stream, 0.9, 0.15,
                List.of(
                        p(1.8212, "D", "C", "B"),
                        p(1.8702, "D", "B"),
                        p(1.8922, "A"),
                        p(2.3540, "A", "C"),
                        p(1.6403, "A", "C", "B"),
                        p(2.5240, "A", "B"),
                        p(1.6364, "C"),
                        p(2.0124, "C", "B"),
                        p(2.0495, "B")));
    }

    // --- TC8: edge case - one item per transaction, ∂ = 0.30 -> 1 pattern
    public static GoldenCase tc8() {
        List<Transaction> stream = List.of(
                tr(1, "A"), tr(2, "B"), tr(3, "A"), tr(4, "C"), tr(5, "A"));
        return new GoldenCase("TC8", stream, 0.9, 0.30,
                List.of(p(2.4661, "A")));
    }
}