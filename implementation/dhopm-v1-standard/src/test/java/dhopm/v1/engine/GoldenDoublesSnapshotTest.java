package dhopm.v1.engine;

import dhopm.common.testkit.GoldenCases;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regenerates {@code docs/golden-doubles-v1.json} — the machine-precision snapshot of the
 * V1 engine's actual outputs (canonicalKey = full double) used by V2/V3 as reference.
 * Written during the normal test run (module working directory = module basedir).
 */
class GoldenDoublesSnapshotTest {

    @Test
    void dumpMachinePrecisionDoubles() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generator", "dhopm.v1.engine.MiningEngine");
        root.put("note", "canonicalKey := items sorted natural order joined by ','; value := full double (round-trips exactly)");
        List<Object> cases = new ArrayList<>();
        for (var golden : GoldenCases.all()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", golden.id());
            entry.put("f", golden.decayFactor());
            entry.put("partial", golden.partial());
            entry.put("minSup", golden.partial() * golden.totalTransactions());
            Map<String, String> patterns = new LinkedHashMap<>();
            try (MiningEngine engine = MiningEngine.defaults(golden.partial(), golden.decayFactor())) {
                engine.loadBatch(golden.transactions());
                for (var p : engine.mineNow().patterns()) {
                    patterns.put(p.canonicalKey(), Double.toString(p.dampedOccupancy()));
                }
            }
            entry.put("patterns", patterns);
            cases.add(entry);
        }
        root.put("cases", cases);

        String json = toJson(root, 0);
        Path target = Path.of("docs", "golden-doubles-v1.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, json + "\n", StandardCharsets.UTF_8);
        assertTrue(Files.size(target) > 0);
        assertFalse(json.contains("NaN"), "no NaN allowed in the snapshot");
    }

    private static String toJson(Object o, int indent) {
        String pad = "  ".repeat(indent);
        String pad1 = "  ".repeat(indent + 1);
        if (o instanceof String s) {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        if (o instanceof Number n) {
            return n.toString();
        }
        if (o instanceof Boolean b) {
            return b.toString();
        }
        if (o instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sb.append(pad1).append("\"").append(e.getKey()).append("\": ").append(toJson(e.getValue(), indent + 1));
                if (++i < m.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(pad).append("}");
            return sb.toString();
        }
        if (o instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < l.size(); i++) {
                sb.append(pad1).append(toJson(l.get(i), indent + 1));
                if (i + 1 < l.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(pad).append("]");
            return sb.toString();
        }
        return "null";
    }
}