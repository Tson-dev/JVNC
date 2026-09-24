package dhopm.v1.engine;

import dhopm.common.contract.MineResult;
import dhopm.common.transaction.Transaction;
import dhopm.common.testkit.GoldenCases;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Streaming semantics: loading N transactions in several batches and re-mining
 * after each must produce the same final result as one full load, bit-for-bit.
 */
class IncrementalTest {

    private static Map<String, Long> bits(MineResult r) {
        Map<String, Long> m = new LinkedHashMap<>();
        r.patterns().forEach(p -> m.put(p.canonicalKey(), Double.doubleToLongBits(p.dampedOccupancy())));
        return m;
    }

    @Test
    void incrementalLoadEqualsFullLoad() {
        List<Transaction> stream = GoldenCases.tc3().transactions(); // base stream, ∂=0.10, f=0.9
        Map<String, Long> full;
        try (MiningEngine engine = MiningEngine.defaults(0.10, 0.9)) {
            engine.loadBatch(stream);
            full = bits(engine.mineNow());
        }

        Map<String, Long> inc;
        try (MiningEngine engine = MiningEngine.defaults(0.10, 0.9)) {
            engine.loadBatch(stream.subList(0, 3));
            bits(engine.mineNow()); // intermediate mine after part 1
            engine.loadBatch(stream.subList(3, stream.size()));
            inc = bits(engine.mineNow());
        }

        assertEquals(full, inc, "part 2 report uses the full stream TL/minSup");
    }

    @Test
    void incrementalTc6MatchesGolden() {
        // TC6: only the first 4 transactions (DB0), ∂=0.25, f=0.9
        Map<String, Double> dos;
        try (MiningEngine engine = MiningEngine.defaults(0.25, 0.9)) {
            engine.loadBatch(GoldenCases.db0Stream());
            dos = new LinkedHashMap<>();
            engine.mineNow().patterns().forEach(p -> dos.put(p.canonicalKey(), p.dampedOccupancy()));
        }
        assertEquals(3, dos.size());
        assertEquals(1.0000, dos.get("C,D,F"), 1e-4, "FCD=1.0000");
        assertEquals(1.4812, dos.get("C,D"), 1e-4, "CD=1.4812");
        assertEquals(1.2218, dos.get("C,D,E"), 1e-4, "CDE=1.2218");
    }
}