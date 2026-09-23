package dhopm.common.testkit;

import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenAssertTest {

    private static List<Pattern> copy(List<Pattern> src) {
        List<Pattern> out = new ArrayList<>();
        for (Pattern p : src) {
            out.add(new Pattern(p.items().clone(), p.dampedOccupancy(), p.tids().clone()));
        }
        return out;
    }

    @Test
    void matchesSelf() {
        GoldenCase golden = new GoldenCase("X", GoldenCases.tc1().transactions(), 0.9, 0.15, copy(GoldenCases.tc1().expected()));
        MineResult result = new MineResult(copy(golden.expected()), golden.totalTransactions(), golden.lastTid(), 0.15 * golden.totalTransactions());
        StringBuilder report = new StringBuilder();
        assertTrue(GoldenAssert.matches(result, golden, GoldenAssert.GOLDEN_TOLERANCE, report));
    }

    @Test
    void detectsWrongValue() {
        GoldenCase golden = GoldenCases.tc1();
        List<Pattern> wrong = new ArrayList<>();
        for (Pattern p : golden.expected()) {
            wrong.add(new Pattern(p.items().clone(), p.dampedOccupancy() + 0.001, new int[0]));
        }
        MineResult result = new MineResult(wrong, golden.totalTransactions(), golden.lastTid(), 0.15 * golden.totalTransactions());
        StringBuilder report = new StringBuilder();
        assertFalse(GoldenAssert.matches(result, golden, GoldenAssert.GOLDEN_TOLERANCE, report));
        assertTrue(report.toString().contains("WRONG"));
    }

    @Test
    void detectsMissingAndExtra() {
        GoldenCase golden = GoldenCases.tc1();
        List<Pattern> partial = List.of(new Pattern(new String[]{"F"}, 1.2553, new int[0]));
        MineResult result = new MineResult(partial, golden.totalTransactions(), golden.lastTid(), 0.15 * golden.totalTransactions());
        StringBuilder report = new StringBuilder();
        assertFalse(GoldenAssert.matches(result, golden, GoldenAssert.GOLDEN_TOLERANCE, report));
        assertTrue(report.toString().contains("MISSING"));
    }

    @Test
    void orderDoesNotMatter() {
        GoldenCase golden = GoldenCases.tc3();
        List<Pattern> reversed = new ArrayList<>(copy(golden.expected()));
        java.util.Collections.reverse(reversed);
        MineResult result = new MineResult(reversed, golden.totalTransactions(), golden.lastTid(), 0.10 * golden.totalTransactions());
        assertTrue(GoldenAssert.matches(result, golden, GoldenAssert.GOLDEN_TOLERANCE, null));
    }

    @Test
    void samePatternSetCmpUsesCanonicalKeys() {
        Pattern a1 = new Pattern(new String[]{"A", "E"}, 1.2601, new int[]{1, 2, 8});
        Pattern a2 = new Pattern(new String[]{"E", "A"}, 1.2601, new int[0]);
        MineResult m1 = new MineResult(List.of(a1), 8, 8, 1.2);
        MineResult m2 = new MineResult(List.of(a2), 8, 8, 1.2);
        assertTrue(GoldenAssert.samePatternSet(m1, m2));
    }
}