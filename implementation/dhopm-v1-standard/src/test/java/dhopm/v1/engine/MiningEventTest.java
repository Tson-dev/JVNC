package dhopm.v1.engine;

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
 * Contract for the new streaming listener (INV-E) and the DHO-List exposure used by {@code detail}:
 * enabling the listener must not change the mined result, each accepted pattern streams exactly once,
 * and the reconstructed DHO-List is readable after {@code mineNow()}.
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
        try (MiningEngine e = new MiningEngine(cfg)) {
            if (sink != null) {
                e.setMiningEventListener(sink::add, detail);
            }
            e.loadBatch(GoldenCases.tc3().transactions());
            return e.mineNow();
        }
    }

    @Test
    void listenerDoesNotChangeResultInvE() {
        MiningConfig cfg = new MiningConfig(0.10, 0.9, MiningConfig.DEFAULT_EPSILON, 4);
        MineResult ref = mine(cfg, null, -1);
        List<MiningEvent> events = new ArrayList<>();
        MineResult withListener = mine(cfg, events, 0);
        assertEquals(bits(ref), bits(withListener), "INV-E: listener must not change the result");
        assertTrue(events.stream().anyMatch(e -> e.kind() == MiningEvent.Kind.PATTERN),
                "accepted patterns must stream");
        assertTrue(events.stream().anyMatch(e -> e.kind() == MiningEvent.Kind.DOP_CHECK),
                "detail 0 must also stream DOP checks");
    }

    @Test
    void eachAcceptedPatternStreamsOnce() {
        MiningConfig cfg = new MiningConfig(0.10, 0.9, MiningConfig.DEFAULT_EPSILON, 4);
        List<MiningEvent> events = new ArrayList<>();
        MineResult r = mine(cfg, events, -1);
        long accepted = events.stream().filter(e -> e.kind() == MiningEvent.Kind.PATTERN).count();
        assertEquals(r.patterns().size(), accepted, "each accepted pattern streams exactly once");
    }

    @Test
    void dhoListExposedAfterReconstruction() {
        try (MiningEngine e = new MiningEngine(new MiningConfig(0.10, 0.9, MiningConfig.DEFAULT_EPSILON, 2))) {
            e.loadBatch(GoldenCases.tc3().transactions());
            e.mineNow();
            assertFalse(e.dhoListAfterReconstruction().isEmpty(), "DHO-List must be exposed for detail");
        }
    }
}
