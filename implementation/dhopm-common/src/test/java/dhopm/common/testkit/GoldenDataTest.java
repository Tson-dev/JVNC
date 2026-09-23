package dhopm.common.testkit;

import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenDataTest {

    @Test
    void hasAllEightCases() {
        assertEquals(8, GoldenCases.all().size());
    }

    @Test
    void tidsAreAscendingAndTransactionsNotEmpty() {
        for (GoldenCase c : GoldenCases.all()) {
            int prev = 0;
            for (var t : c.transactions()) {
                assertTrue(t.tid() > prev, c.id() + " TID must increase");
                assertEquals(t.items().length, t.length());
                assertTrue(t.items().length > 0, c.id() + " empty transaction");
                prev = t.tid();
            }
            assertEquals(prev, c.lastTid());
        }
    }

    @Test
    void expectedPatternCountsMatchHandRun() {
        assertEquals(2, GoldenCases.tc1().expected().size());
        assertEquals(0, GoldenCases.tc2().expected().size());
        assertEquals(15, GoldenCases.tc3().expected().size());
        assertEquals(0, GoldenCases.tc4().expected().size());
        assertEquals(9, GoldenCases.tc5().expected().size());
        assertEquals(3, GoldenCases.tc6().expected().size());
        assertEquals(9, GoldenCases.tc7().expected().size());
        assertEquals(1, GoldenCases.tc8().expected().size());
    }

    @Test
    void expectedPatternsHaveDistinctCanonicalKeys() {
        for (GoldenCase c : GoldenCases.all()) {
            List<String> keys = new ArrayList<>();
            for (Pattern p : c.expected()) {
                keys.add(p.canonicalKey());
            }
            long distinct = keys.stream().distinct().count();
            assertEquals(keys.size(), distinct, c.id() + " duplicate canonical key");
            long sorted = keys.stream().filter(k -> k.equals(sort(k))).count();
            assertEquals(keys.size(), sorted, c.id() + " key not canonical-sorted");
        }
    }

    private static String sort(String key) {
        String[] parts = key.split(",");
        Arrays.sort(parts);
        return String.join(",", parts);
    }

    @Test
    void tc6UsesOnlyDb0() {
        assertEquals(4, GoldenCases.tc6().totalTransactions());
        assertEquals(4, GoldenCases.tc6().lastTid());
    }
}