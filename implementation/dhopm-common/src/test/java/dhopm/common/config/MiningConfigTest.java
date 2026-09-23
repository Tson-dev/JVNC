package dhopm.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiningConfigTest {

    @Test
    void acceptsValidValues() {
        MiningConfig c = MiningConfig.of(0.15, 0.9);
        assertEquals(0.15, c.delta());
        assertEquals(0.9, c.decayFactor());
        assertEquals(MiningConfig.DEFAULT_EPSILON, c.epsilon());
        assertEquals(MiningConfig.DEFAULT_WORKERS, c.workers());
        assertEquals(MiningConfig.DEFAULT_WORKERS, Runtime.getRuntime().availableProcessors());
    }

    @Test
    void acceptsNoDecayCase() {
        MiningConfig c = MiningConfig.of(0.15, 1.0);
        assertEquals(1.0, c.decayFactor());
    }

    @Test
    void rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> MiningConfig.of(-0.1, 0.9));
        assertThrows(IllegalArgumentException.class, () -> MiningConfig.of(1.1, 0.9));
        assertThrows(IllegalArgumentException.class, () -> MiningConfig.of(0.15, 0.0));
        assertThrows(IllegalArgumentException.class, () -> MiningConfig.of(0.15, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new MiningConfig(0.15, 0.9, 0.0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MiningConfig(0.15, 0.9, 1e-6, 0));
    }

    @Test
    void minSupIsDeltaTimesTotal() {
        MiningConfig c = MiningConfig.of(0.15, 0.9);
        assertEquals(1.2, c.minSup(8), 1e-12);
        assertEquals(3.0, c.minSup(20), 1e-12);
    }
}