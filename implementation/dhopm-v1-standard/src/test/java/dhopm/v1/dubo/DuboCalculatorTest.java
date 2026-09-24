package dhopm.v1.dubo;

import dhopm.v1.model.DHONode;
import dhopm.v1.model.Entry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DUBO verified by hand (TL=8, f=0.9): E=4.5, B=1.8, CD=1.6402, AF=0.93, CDE=1.181, F=3.0375.
 */
class DuboCalculatorTest {

    @Test
    void duboMatchesHandComputedValues() {
        assertEquals(4.5000, DuboCalculator.dubo(node("E",
                e(1, 4), e(2, 3), e(3, 4), e(6, 3), e(8, 3)), 0.9, 8), 1e-4);
        assertEquals(1.8000, DuboCalculator.dubo(node("B",
                e(3, 4), e(5, 2), e(7, 4)), 0.9, 8), 1e-4);
        assertEquals(1.6402, DuboCalculator.dubo(node("CD",
                e(1, 4), e(3, 4), e(4, 3)), 0.9, 8), 1e-4);
        assertEquals(0.9300, DuboCalculator.dubo(node("AF",
                e(2, 3), e(7, 4)), 0.9, 8), 1e-4);
        assertEquals(1.1810, DuboCalculator.dubo(node("CDE",
                e(1, 4), e(3, 4)), 0.9, 8), 1e-4);
        assertEquals(3.0375, DuboCalculator.dubo(node("F",
                e(2, 3), e(4, 3), e(5, 2), e(6, 3), e(7, 4)), 0.9, 8), 1e-4);
    }

    @Test
    void duboIncreasesWithMoreEntries() {
        double single = DuboCalculator.dubo(node("X", e(1, 3), e(4, 4)), 0.9, 8);
        double doubled = DuboCalculator.dubo(node("X", e(1, 3), e(4, 4), e(7, 3)), 0.9, 8);
        assertEquals(0.8370, single, 1e-4);
        assertEquals(2.4750, doubled, 1e-4);
        org.junit.jupiter.api.Assertions.assertTrue(doubled > single);
    }

    private static DHONode node(String item, Entry... entries) {
        return DHONode.of(item, entries);
    }

    private static Entry e(int tid, int len) {
        return new Entry(tid, len);
    }
}