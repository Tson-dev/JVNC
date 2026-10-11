package dhopm.cli;

import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Engine;
import dhopm.v1.engine.MiningEngine;
import dhopm.v2.engine.MiningEngineV2;
import dhopm.v3.engine.MiningEngineV3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed registry of the mining versions the Manager can dispatch to (D45: G5 before G4).
 *
 * <p>Hard-coded on purpose (YAGNI, DECISIONS section 6): no SPI/plugin discovery. V4 is listed as
 * {@code pending} so the {@code hello} handshake can advertise the roadmap without pretending it runs.
 */
public final class VersionRegistry {

    /** A registered version: its id, the manifest status and the engine factory. */
    public record Entry(String id, String displayName, String status, EngineFactory factory) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public VersionRegistry() {
        register(new Entry("v1", "v1-standard", "ready", MiningEngine::new));
        register(new Entry("v2", "v2-minocc", "ready", MiningEngineV2::new));
        register(new Entry("v3", "v3-optimized", "ready", MiningEngineV3::new));
        register(new Entry("v4", "v4-extreme", "pending", null));
    }

    private void register(Entry entry) {
        entries.put(entry.id(), entry);
    }

    public boolean isRegistered(String id) {
        return entries.containsKey(id);
    }

    public boolean isReady(String id) {
        Entry e = entries.get(id);
        return e != null && e.factory() != null;
    }

    /** Factory for a ready version, or a protocol error ({@code UNKNOWN_VERSION}/{@code NOT_IMPLEMENTED}). */
    public EngineFactory factory(String id) {
        Entry e = entries.get(id);
        if (e == null) {
            throw new ManagerException(Codes.UNKNOWN_VERSION, "unknown version: " + id);
        }
        if (e.factory() == null) {
            throw new ManagerException(Codes.NOT_IMPLEMENTED, "version not implemented yet: " + id);
        }
        return e.factory();
    }

    /** Display name of the engine behind a version (for the {@code jobs[].engine} field). */
    public String displayName(String id) {
        Entry e = entries.get(id);
        return e == null ? id : e.displayName();
    }

    /** Ready version ids in registration order. */
    public List<String> readyVersions() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.factory() != null) {
                out.add(e.id());
            }
        }
        return out;
    }

    /** Creates the engine for {@code version}, enforcing version-specific parameter support. */
    public Engine create(String version, MiningConfig config) {
        if (version.equals("v1") && config.minOcc() != 0.0) {
            throw new ManagerException(Codes.INVALID_PARAMETER,
                    "v1-standard is paper mode (minOcc=0) only; minOcc=" + config.minOcc() + " needs v2 or v3");
        }
        return factory(version).create(config);
    }

    /** Manifest lines for the {@code hello} handshake and {@code info}/{@code version}. */
    public List<Map<String, Object>> manifest() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("engine", e.displayName());
            m.put("status", e.status());
            out.add(m);
        }
        return out;
    }
}
