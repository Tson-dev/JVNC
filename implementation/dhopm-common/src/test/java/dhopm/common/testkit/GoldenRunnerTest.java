package dhopm.common.testkit;

import dhopm.common.contract.Engine;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.Pattern;
import dhopm.common.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenRunnerTest {

    /** Mock engine: replays the golden expected set (used to prove the harness works before G1). */
    private static Engine mock(GoldenCase golden) {
        return new Engine() {
            private final List<Pattern> result = golden.expected().stream()
                    .map(p -> new Pattern(p.items().clone(), p.dampedOccupancy(), p.tids().clone()))
                    .toList();

            @Override
            public String name() {
                return "mock-" + golden.id();
            }

            @Override
            public void loadBatch(List<Transaction> batch) {
            }

            @Override
            public MineResult mineNow() {
                return new MineResult(result, golden.totalTransactions(), golden.lastTid(),
                        golden.partial() * golden.totalTransactions());
            }
        };
    }

    @Test
    void runnerPassesTc1() {
        GoldenCase golden = GoldenCases.tc1();
        StringBuilder report = new StringBuilder();
        boolean ok = GoldenRunner.run(mock(golden), golden, GoldenAssert.GOLDEN_TOLERANCE, report);
        assertTrue(ok, report.toString());
        assertTrue(report.toString().contains("PASS") || report.toString().contains("-> 2/2"));
    }

    @Test
    void runnerPassesEmptyTc2() {
        GoldenCase golden = GoldenCases.tc2();
        StringBuilder report = new StringBuilder();
        assertTrue(GoldenRunner.run(mock(golden), golden, GoldenAssert.GOLDEN_TOLERANCE, report), report.toString());
    }

    @Test
    void runnerFailsOnPerturbedEngine() {
        GoldenCase golden = GoldenCases.tc1();
        Engine bad = new Engine() {
            @Override
            public String name() {
                return "mock-bad";
            }

            @Override
            public void loadBatch(List<Transaction> batch) {
            }

            @Override
            public MineResult mineNow() {
                return new MineResult(List.of(new Pattern(new String[]{"A", "E"}, 1.26, new int[0])),
                        golden.totalTransactions(), golden.lastTid(), golden.partial() * golden.totalTransactions());
            }
        };
        StringBuilder report = new StringBuilder();
        assertFalse(GoldenRunner.run(bad, golden, GoldenAssert.GOLDEN_TOLERANCE, report), report.toString());
    }
}