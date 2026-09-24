package dhopm.v1.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Engine;
import dhopm.common.testkit.GoldenAssert;
import dhopm.common.testkit.GoldenCase;
import dhopm.common.testkit.GoldenCases;
import dhopm.common.testkit.GoldenRunner;
import dhopm.v1.engine.MiningEngine;

import java.util.List;

/**
 * Lệnh {@code golden}: chạy TestKit TC1–TC8 qua engine V1 (engine mới cho mỗi TC vì TID
 * khởi động lại từ 1 — INV-A), in báo cáo PASS/FAIL. Exit code 1 nếu có FAIL.
 */
final class GoldenCommand {

    int run(String[] args, int from) {
        List<GoldenCase> cases = GoldenCases.all();
        StringBuilder report = new StringBuilder();
        boolean allOk = true;
        for (GoldenCase c : cases) {
            boolean ok;
            try (MiningEngine engine = new MiningEngine(MiningConfig.of(c.partial(), c.decayFactor()))) {
                ok = GoldenRunner.run(engine, c, GoldenAssert.GOLDEN_TOLERANCE, report);
            }
            allOk &= ok;
            report.append(ok ? "  PASS\n" : "  FAIL\n");
        }
        System.out.println("== golden TC1-TC8 (tolerance " + GoldenAssert.GOLDEN_TOLERANCE + ") ==");
        System.out.print(report);
        System.out.println(allOk ? "== ALL " + cases.size() + " TC PASS ==" : "== SOME TC FAIL ==");
        return allOk ? 0 : 1;
    }
}