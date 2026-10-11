package dhopm.v2.engine;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.MineResult;
import dhopm.common.contract.MiningEvent;
import dhopm.common.contract.Pattern;
import dhopm.common.testkit.GoldenCases;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2 streaming listener contract (INV-E) + DHO-List exposure for {@code detail}. Mirrors V1 but
 * exercises the finite-window path so {@code UBDO} events carry {@code z} and the list carries heads.
 */
class MiningEventTest {

    private static Map<String, Long> bits(MineResult r) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Pattern p : r.patterns()) {
            m.put(p.canonicalKey(), Double.doubleToLongBits(p.dampedOccupancy()));
        }
        return m;
    }

    private static MineResult mine(MiningConfig cfg, List<MiningEvent> sink, int detail) {
        try (MiningEngineV2 e = new MiningEngineV2(cfg)) {
            if (sink != null) {
                e.setMiningEventListener(sink::add, detail);
            }
            e.loadBatch(GoldenCases.tc3().transactions());
            return e.mineNow();
        }
    }

    @Test
    void listenerDoesNotChangeResultInvE() {
        MiningConfig cfg = new MiningConfig(0.10, 0.9, MiningConfig.DEFAULT_EPSILON, MiningConfig.DEFAULT_MIN_OCC, 4);
        MineResult ref = mine(cfg, null, -1);
        List<MiningEvent> events = new ArrayList<>();
        MineResult withListener = mine(cfg, events, 0);
        assertEquals(bits(ref), bits(withListener), "INV-E: listener must not change the result");
        assertTrue(events.stream().anyMatch(e -> e.kind() == MiningEvent.Kind.DOP_CHECK),
                "detail 0 must stream DOP checks");
        assertTrue(events.stream().anyMatch(e -> e.kind() == MiningEvent.Kind.UBDO),
                "detail 0 must stream UBDO checks");
    }

    @Test
    void dhoListExposedAfterReconstruction() {
        try (MiningEngineV2 e = new MiningEngineV2(
                new MiningConfig(0.10, 0.9, MiningConfig.DEFAULT_EPSILON, MiningConfig.DEFAULT_MIN_OCC, 2))) {
            e.loadBatch(GoldenCases.tc3().transactions());
            e.mineNow();
            assertFalse(e.dhoListAfterReconstruction().isEmpty(), "DHO-List must be exposed for detail");
        }
    }
}
