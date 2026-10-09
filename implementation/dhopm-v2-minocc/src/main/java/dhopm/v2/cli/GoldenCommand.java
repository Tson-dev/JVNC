package dhopm.v2.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.testkit.GoldenAssert;
import dhopm.common.testkit.GoldenCase;
import dhopm.common.testkit.GoldenCases;
import dhopm.common.testkit.GoldenRunner;
import dhopm.v2.engine.MiningEngineV2;

import java.util.List;

/**
 * Lệnh {@code golden}: chạy TestKit qua engine V2 —
 * <ul>
 *   <li>TC1–TC8 với minOcc = 0 (INV-I: kết quả phải khớp golden cũ);</li>
 *   <li>TC9/TC10: minOcc = 0 và minOcc sao cho W ≥ TL đều phải ≡ paper.</li>
 * </ul>
 * Exit code 1 nếu có FAIL. (TC11–TC18 được nghiệm thu trong test JUnit của module.)
 */
final class GoldenCommand {

    int run(String[] args, int from) throws CliSupport.CliException {
        StringBuilder report = new StringBuilder();
        boolean allOk = true;
        for (GoldenCase c : GoldenCases.all()) {
            try (MiningEngineV2 engine = new MiningEngineV2(
                    new MiningConfig(c.partial(), c.decayFactor(), MiningConfig.DEFAULT_EPSILON, 0.0, 1))) {
                allOk &= GoldenRunner.run(engine, c, GoldenAssert.GOLDEN_TOLERANCE, report);
            }
        }
        System.out.println("== golden TC1-TC8 via V2 (minOcc=0, tolerance " + GoldenAssert.GOLDEN_TOLERANCE + ") ==");
        System.out.print(report);
        System.out.println(allOk ? "== ALL " + GoldenCases.all().size() + " TC PASS ==" : "== SOME TC FAIL ==");
        return allOk ? 0 : 1;
    }
}