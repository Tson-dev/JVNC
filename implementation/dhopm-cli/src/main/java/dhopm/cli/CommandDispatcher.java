package dhopm.cli;

import dhopm.cli.json.Json;
import dhopm.cli.config.ConfigStore;
import dhopm.cli.session.SessionManager;
import dhopm.cli.store.ResultStore;
import dhopm.common.config.MiningConfig;
import dhopm.common.contract.Engine;
import dhopm.common.contract.MiningProgress;
import dhopm.common.contract.MiningProgressListener;
import dhopm.common.testkit.GoldenAssert;
import dhopm.common.testkit.GoldenCase;
import dhopm.common.testkit.GoldenCases;
import dhopm.common.testkit.GoldenRunner;
import dhopm.common.transaction.Transaction;
import dhopm.common.window.ParameterValidator;
import dhopm.common.window.WindowMath;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a request (JSON object or CLI argv turned into a map) to a handler and returns the response
 * envelope of plans/08-FE-CONTRACT.md. This is the only place that knows the command set; every
 * handler goes through the public {@code dhopm-common} API (D6/D7).
 */
public final class CommandDispatcher {

    public static final int PROTOCOL = 1;
    public static final String MANAGER = "dhopm-cli";

    /** Commands advertised in the {@code hello} handshake (section 5.3). */
    public static final List<String> COMMANDS = List.of(
            "hello", "mine", "detail", "stream", "window", "validate", "inspect", "golden",
            "sweep", "info", "version", "config", "fetch", "session.create", "feed", "session.mine");

    /** Streams progress/step/pattern events ahead of the final response (serve/TCP). */
    @FunctionalInterface
    public interface EventSink {
        void emit(Map<String, Object> event);
    }

    private static final EventSink NOOP = event -> {
    };

    private final VersionRegistry registry;
    private final MiningRunner runner;
    private final ConfigStore config;
    private final ResultStore results;
    private final SessionManager sessions = new SessionManager();

    public CommandDispatcher(VersionRegistry registry) {
        this(registry, new ConfigStore());
    }

    public CommandDispatcher(VersionRegistry registry, ConfigStore config) {
        this(registry, config, new ResultStore(resolveExportDir(config)));
    }

    public CommandDispatcher(VersionRegistry registry, ConfigStore config, ResultStore results) {
        this.registry = registry;
        this.config = config;
        this.results = results;
        this.runner = new MiningRunner(registry);
    }

    private static Path resolveExportDir(ConfigStore config) {
        Object dir = config.get("export.dir");
        Path preferred = Path.of(String.valueOf(dir));
        try {
            Files.createDirectories(preferred);
            return preferred;
        } catch (Exception e) {
            Path fallback = Path.of(System.getProperty("user.dir"), "mine");
            try {
                Files.createDirectories(fallback);
            } catch (Exception ignored) {
                // last resort: the writer will surface EXPORT_FAILED when it actually tries
            }
            return fallback;
        }
    }

    public VersionRegistry registry() {
        return registry;
    }

    public ConfigStore config() {
        return config;
    }

    public ResultStore results() {
        return results;
    }

    /** Dispatches one request map; throws {@link ManagerException} for protocol errors. */
    public Map<String, Object> dispatch(Map<String, Object> request) {
        return dispatch(request, NOOP);
    }

    /** Dispatches one request, emitting stream events through {@code sink} (serve/TCP only). */
    public Map<String, Object> dispatch(Map<String, Object> request, EventSink sink) {
        Params p = new Params(request);
        String cmd = p.str("cmd");
        if (cmd == null || cmd.isBlank()) {
            throw ManagerException.missing("cmd");
        }
        return switch (cmd) {
            case "hello" -> hello(p);
            case "info", "version" -> info(p);
            case "window" -> window(p);
            case "validate" -> validate(p);
            case "inspect" -> inspect(p);
            case "golden" -> golden(p);
            case "mine" -> mine(p, false, sink);
            case "detail" -> mine(p, true, sink);
            case "stream" -> stream(p, sink);
            case "sweep" -> sweep(p);
            case "config" -> config(p);
            case "fetch" -> fetch(p);
            case "session.create" -> sessionCreate(p);
            case "feed" -> feed(p);
            case "session.mine" -> sessionMine(p, sink);
            default -> throw new ManagerException(Codes.UNKNOWN_COMMAND, "unknown command: " + cmd);
        };
    }

    // ---------------------------------------------------------------- envelope

    private Map<String, Object> envelope(Params p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.longVal("id", 0L));
        m.put("v", PROTOCOL);
        m.put("ok", true);
        return m;
    }

    // ---------------------------------------------------------------- hello / info

    private Map<String, Object> hello(Params p) {
        Map<String, Object> m = envelope(p);
        m.put("protocol", PROTOCOL);
        m.put("manager", MANAGER);
        m.put("versions", registry.manifest());
        m.put("commands", COMMANDS);
        return m;
    }

    private Map<String, Object> info(Params p) {
        Map<String, Object> m = envelope(p);
        m.put("protocol", PROTOCOL);
        m.put("manager", MANAGER);
        m.put("runtime", Runtime.version().toString());
        m.put("versions", registry.manifest());
        m.put("commands", COMMANDS);
        return m;
    }

    // ---------------------------------------------------------------- window

    private Map<String, Object> window(Params p) {
        double f = p.requiredNum("f");
        double minOcc = p.requiredNum("minOcc");
        Map<String, Object> block = Wire.windowLookup(f, minOcc);

        Map<String, Object> sizeOnly = new LinkedHashMap<>();
        sizeOnly.put("size", block.get("size"));

        Map<String, Object> m = envelope(p);
        Double tl = p.num("tl");
        if (tl != null) {
            long tlLong = tl.longValue();
            long w = WindowMath.computeWindow(f, minOcc);
            long nEff = WindowMath.effectiveTransactions(tlLong, w);
            sizeOnly.put("effective", nEff);
            m.put("window", sizeOnly);
            m.put("maxDOExact", WindowMath.maxDO(f, tlLong));
            m.put("maxPartialExact", WindowMath.maxPartialExact(f, minOcc, tlLong));
        } else {
            m.put("window", sizeOnly);
        }
        m.put("maxDOAsymptotic", block.get("maxDOAsymptotic"));
        m.put("maxPartialAsymptotic", block.get("maxPartialAsymptotic"));
        m.put("maxDOExact", m.getOrDefault("maxDOExact", null));
        m.put("maxPartialExact", m.getOrDefault("maxPartialExact", null));
        return m;
    }

    // ---------------------------------------------------------------- validate

    private Map<String, Object> validate(Params p) {
        double partial = p.requiredNum("partial");
        double f = p.requiredNum("f");
        double minOcc = p.requiredNum("minOcc");
        MiningConfig config = buildConfig(partial, f, minOcc, p);

        List<ParameterValidator.Issue> issues;
        Double tlParam = p.num("tl");
        if (p.has("dataset")) {
            DatasetLoader.Loaded loaded = DatasetLoader.load(p.str("dataset"), p.str("format"), p.longVal("limit", 0));
            issues = ParameterValidator.validate(config, loaded.lastTid());
        } else if (tlParam != null) {
            issues = ParameterValidator.validate(config, tlParam.longValue());
        } else {
            issues = ParameterValidator.validate(config);
        }
        Map<String, Object> m = envelope(p);
        m.put("config", Wire.config(config));
        m.put("issues", Wire.issues(issues));
        m.put("valid", issues.stream().noneMatch(i -> i.severity() == ParameterValidator.Severity.ERROR));
        return m;
    }

    // ---------------------------------------------------------------- inspect

    private Map<String, Object> inspect(Params p) {
        String dataset = p.requiredStr("dataset");
        DatasetLoader.Loaded loaded = DatasetLoader.load(dataset, p.str("format"), p.longVal("limit", 0));
        List<Transaction> all = loaded.transactions();
        int maxLen = 0;
        for (Transaction t : all) {
            maxLen = Math.max(maxLen, t.length());
        }
        Map<String, Object> m = envelope(p);
        m.put("dataset", loaded.label());
        m.put("transactions", all.size());
        m.put("lastTid", loaded.lastTid());
        m.put("maxLen", maxLen);
        if (loaded.limited()) {
            m.put("limitNote", loaded.reachedEnd()
                    ? "limit=" + loaded.limit() + " ignored - whole dataset read"
                    : "limit=" + loaded.limit() + " - stopped after reading " + all.size() + " tx");
        }
        Double f = p.num("f");
        Double minOcc = p.num("minOcc");
        if (f != null && minOcc != null) {
            long tl = loaded.lastTid();
            long w = WindowMath.computeWindow(f, minOcc);
            long nEff = WindowMath.effectiveTransactions(tl, w);
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("size", w == WindowMath.INFINITE ? null : w);
            block.put("effective", nEff);
            block.put("maxDO", WindowMath.maxDO(f, tl));
            block.put("maxPartialAsymptotic", WindowMath.maxPartialAsymptotic(f, minOcc));
            m.put("window", block);
        }
        return m;
    }

    // ---------------------------------------------------------------- golden

    private Map<String, Object> golden(Params p) {
        List<String> versions = versionsOrDefault(p);
        int workers = p.intVal("workers", MiningConfig.DEFAULT_WORKERS);
        List<Map<String, Object>> cases = new ArrayList<>();
        boolean all = true;
        for (String version : versions) {
            if (version.equals("v1")) {
                // v1 is paper mode; GoldenCases are paper cases (minOcc=0) so it is allowed
            }
            for (GoldenCase gc : GoldenCases.all()) {
                MiningConfig cfg = new MiningConfig(gc.partial(), gc.decayFactor(),
                        MiningConfig.DEFAULT_EPSILON, 0.0, workers);
                Engine engine = registry.create(version, cfg);
                StringBuilder sb = new StringBuilder();
                boolean pass;
                try {
                    pass = GoldenRunner.run(engine, gc, GoldenAssert.GOLDEN_TOLERANCE, sb);
                } finally {
                    closeQuietly(engine);
                }
                all &= pass;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", gc.id());
                c.put("version", version);
                c.put("pass", pass);
                cases.add(c);
            }
        }
        Map<String, Object> m = envelope(p);
        m.put("allPass", all);
        m.put("cases", cases);
        return m;
    }

    // ---------------------------------------------------------------- mine / detail / stream

    private record MinePrep(MiningConfig config, List<String> versions, List<Transaction> transactions,
                            int parts, Map<String, Object> perVersionConfig) {
    }

    private MinePrep prepareMine(Params p) {
        double partial = p.requiredNum("partial");
        double f = p.requiredNum("f");
        double minOcc = p.requiredNum("minOcc");
        MiningConfig config = buildConfig(partial, f, minOcc, p);
        List<String> versions = versionsOrDefault(p);
        for (String version : versions) {
            if (!registry.isRegistered(version)) {
                throw new ManagerException(Codes.UNKNOWN_VERSION, "unknown version: " + version);
            }
        }
        DatasetLoader.Loaded loaded = DatasetLoader.load(p.requiredStr("dataset"), p.str("format"), p.longVal("limit", 0));
        return new MinePrep(config, versions, loaded.transactions(), Math.max(1, p.intVal("parts", 1)),
                asMapOrNull(p.raw().get("versionConfig")));
    }

    private Map<String, Object> mine(Params p, boolean detail, EventSink sink) {
        MinePrep prep = prepareMine(p);
        List<MiningRunner.Job> jobs = runner.run(prep.versions(), prep.config(), prep.transactions(),
                prep.parts(), prep.perVersionConfig(), null);
        Map<String, Object> m = mineResponse(p, detail, prep.config(), jobs);
        maybeExport(p, m);
        return m;
    }

    private Map<String, Object> stream(Params p, EventSink sink) {
        MinePrep prep = prepareMine(p);
        boolean step = p.boolVal("step", false);
        int detail = p.intVal("detail", intConfig("detail", 1));
        List<MiningRunner.Job> jobs = new ArrayList<>();
        for (String version : prep.versions()) {
            MiningProgressListener progress = pr -> sink.emit(progressEvent(p, version, pr));
            List<MiningRunner.Job> one = runner.run(List.of(version), prep.config(), prep.transactions(),
                    prep.parts(), prep.perVersionConfig(), progress);
            MiningRunner.Job job = one.get(0);
            jobs.add(job);
            sink.emit(windowEvent(p, version, job));
            if (step) {
                emitSteps(p, version, job, detail, sink);
            } else {
                emitPatterns(p, version, job, sink);
            }
        }
        Map<String, Object> m = mineResponse(p, false, prep.config(), jobs);
        maybeExport(p, m);
        return m;
    }

    private Map<String, Object> mineResponse(Params p, boolean detail, MiningConfig config,
                                             List<MiningRunner.Job> jobs) {
        MiningRunner.Job primary = jobs.get(0);
        Map<String, Object> m = envelope(p);
        m.put("config", Wire.config(config));
        m.put("totalTransactions", primary.result().totalTransactions());
        m.put("lastTid", primary.result().lastTid());
        m.put("minSup", primary.result().minSup());
        m.put("maxDO", primary.window().maxDO());
        m.put("window", Wire.window(primary.window()));
        m.put("issues", Wire.issues(primary.issues()));
        m.put("jobs", ResultAggregator.jobs(jobs));
        m.put("patterns", Wire.patterns(MiningRunner.aggregate(jobs)));
        m.put("perVersion", ResultAggregator.perVersion(jobs, detail));
        return m;
    }

    // ---------------------------------------------------------------- stream events

    private Map<String, Object> event(Params p, String name) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", p.longVal("id", 0L));
        e.put("v", PROTOCOL);
        e.put("event", name);
        return e;
    }

    private Map<String, Object> progressEvent(Params p, String version, MiningProgress pr) {
        Map<String, Object> e = event(p, "progress");
        e.put("version", version);
        e.put("completedRootTasks", pr.completedRootTasks());
        e.put("totalRootTasks", pr.totalRootTasks());
        e.put("patternsFound", pr.patternsFound());
        e.put("elapsedMs", pr.elapsedMs());
        return e;
    }

    private Map<String, Object> windowEvent(Params p, String version, MiningRunner.Job job) {
        Map<String, Object> e = event(p, "window");
        e.put("version", version);
        e.put("window", Wire.window(job.window()));
        return e;
    }

    private void emitPatterns(Params p, String version, MiningRunner.Job job, EventSink sink) {
        int index = 0;
        for (var pattern : job.result().patterns()) {
            Map<String, Object> e = event(p, "pattern");
            e.put("version", version);
            e.put("index", index++);
            e.put("pattern", Wire.pattern(pattern));
            sink.emit(e);
        }
    }

    /**
     * Step log for {@code stream --step}. The public engine API only exposes phase/window/progress
     * observations (the DOP/UBDO expansion hooks live inside each engine and are not surfaced yet),
     * so steps are derived from the qualifying DOPs: {@code --detail 2} logs only DOP hits, higher
     * verbosity adds a note that finer UBDO traces are pending an engine-side hook.
     */
    private void emitSteps(Params p, String version, MiningRunner.Job job, int detail, EventSink sink) {
        for (var pattern : job.result().patterns()) {
            Map<String, Object> e = event(p, "step");
            e.put("version", version);
            e.put("phase", "MINING");
            e.put("kind", "DOP_CHECK");
            e.put("pattern", pattern.canonicalKey());
            e.put("passed", true);
            e.put("do", pattern.dampedOccupancy());
            e.put("minSup", job.result().minSup());
            sink.emit(e);
        }
        if (detail <= 1) {
            Map<String, Object> e = event(p, "step");
            e.put("version", version);
            e.put("phase", "MINING");
            e.put("kind", "NOTE");
            e.put("detailLevel", detail);
            e.put("message", "UBDO/DO expansion traces need an engine-side step hook; emitting DOP hits only");
            sink.emit(e);
        }
    }

    // ---------------------------------------------------------------- sessions

    private Map<String, Object> sessionCreate(Params p) {
        Map<String, Object> cfgMap = asMapOrNull(p.raw().get("config"));
        if (cfgMap == null) {
            throw ManagerException.missing("config");
        }
        Params cp = new Params(cfgMap);
        double partial = cp.requiredNum("partial");
        double f = cp.requiredNum("f");
        double minOcc = cp.has("minOcc") ? cp.requiredNum("minOcc") : 0.0;
        MiningConfig cfg = buildConfig(partial, f, minOcc, cp);
        SessionManager.Session session = sessions.create(cfg);
        Map<String, Object> m = envelope(p);
        m.put("session", session.id());
        m.put("config", Wire.config(cfg));
        return m;
    }

    private Map<String, Object> feed(Params p) {
        SessionManager.Session session = sessions.get(p.requiredStr("session"));
        int loaded = session.feed(SessionManager.parseTransactions(p));
        long w = WindowMath.computeWindow(session.config().decayFactor(), session.config().minOcc());
        long nEff = WindowMath.effectiveTransactions(session.lastTid(), w);
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("size", w == WindowMath.INFINITE ? null : w);
        window.put("effective", nEff);
        Map<String, Object> m = envelope(p);
        m.put("session", session.id());
        m.put("loaded", loaded);
        m.put("lastTid", session.lastTid());
        m.put("window", window);
        return m;
    }

    private Map<String, Object> sessionMine(Params p, EventSink sink) {
        SessionManager.Session session = sessions.get(p.requiredStr("session"));
        List<String> versions = versionsOrDefault(p);
        MiningConfig cfg = p.has("minOcc") ? session.config().withMinOcc(p.requiredNum("minOcc")) : session.config();
        List<MiningRunner.Job> jobs = runner.run(versions, cfg, session.transactions(),
                Math.max(1, p.intVal("parts", 1)), null, null);
        Map<String, Object> m = mineResponse(p, false, cfg, jobs);
        maybeExport(p, m);
        return m;
    }

    // ---------------------------------------------------------------- config / fetch

    private Map<String, Object> config(Params p) {
        String action = p.str("action");
        Map<String, Object> m = envelope(p);
        switch (action == null ? "" : action) {
            case "get" -> {
                String key = p.requiredStr("key");
                m.put("key", key);
                m.put("value", config.get(key));
            }
            case "set" -> {
                String key = p.requiredStr("key");
                if (!p.has("value")) {
                    throw ManagerException.missing("value");
                }
                m.put("key", key);
                m.put("value", config.set(key, p.raw().get("value")));
            }
            case "list" -> {
                m.put("config", config.list());
                m.put("file", config.file().toString());
            }
            default -> throw new ManagerException(Codes.UNKNOWN_COMMAND, "unknown config action: " + action);
        }
        return m;
    }

    private Map<String, Object> fetch(Params p) {
        String fileID = p.requiredStr("file");
        Map<String, Object> m = envelope(p);
        m.put("fileID", fileID);
        m.put("path", results.pathOf(fileID).toString());
        m.put("content", results.read(fileID));
        return m;
    }

    // ---------------------------------------------------------------- export (D48)

    private void maybeExport(Params p, Map<String, Object> response) {
        boolean exportFlag = p.has("export");
        boolean isLog = Boolean.TRUE.equals(config.get("isLog"));
        if (!exportFlag && !isLog) {
            return;
        }
        String fileID = results.nextFileId();
        Path dir = results.dir();
        Object exportValue = p.raw().get("export");
        boolean explicitPath = exportValue != null && !(exportValue instanceof Boolean);
        if (explicitPath) {
            try {
                dir = Path.of(String.valueOf(exportValue));
                Files.createDirectories(dir);
            } catch (Exception e) {
                dir = results.dir();
                addIssue(response, Codes.EXPORT_FAILED,
                        "cannot use export path '" + exportValue + "': " + e.getMessage() + "; using " + dir);
            }
        }
        String content = renderText(p, response);
        results.writeAt(dir, fileID, content);
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("fileID", fileID);
        saved.put("path", dir.resolve("mine_" + fileID + ".txt").toString());
        response.put("saved", saved);
    }

    private String renderText(Params p, Map<String, Object> response) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            TextRenderer.render(ps, p.raw(), response);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private void addIssue(Map<String, Object> response, String code, String message) {
        Object existing = response.get("issues");
        List<Object> issues = existing instanceof List<?> list ? new ArrayList<>((List<Object>) list) : new ArrayList<>();
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("severity", "WARNING");
        issue.put("code", code);
        issue.put("message", message);
        issues.add(issue);
        response.put("issues", issues);
    }

    private int intConfig(String key, int fallback) {
        Object value = config.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    // ---------------------------------------------------------------- sweep

    private Map<String, Object> sweep(Params p) {
        String dataset = p.requiredStr("dataset");
        double f = p.requiredNum("f");
        double minOcc = p.requiredNum("minOcc");
        List<Double> partials = p.numList("partials");
        if (partials == null || partials.isEmpty()) {
            Double single = p.num("partial");
            if (single == null) {
                throw ManagerException.missing("partials");
            }
            partials = List.of(single);
        }
        List<String> versions = versionsOrDefault(p);
        int workers = p.intVal("workers", MiningConfig.DEFAULT_WORKERS);
        int parts = Math.max(1, p.intVal("parts", 1));
        String format = p.str("format");
        long limit = p.longVal("limit", 0);

        List<Transaction> transactions = DatasetLoader.load(dataset, format, limit).transactions();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Double partial : partials) {
            MiningConfig config = buildConfig(partial, f, minOcc, p);
            List<MiningRunner.Job> jobs = runner.run(versions, config, transactions, parts, null, null);
            MiningRunner.Job primary = jobs.get(0);
            String issue = primary.issues().isEmpty() ? null : primary.issues().get(0).code();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partial", partial);
            row.put("f", f);
            row.put("minOcc", minOcc);
            row.put("minSup", primary.result().minSup());
            row.put("patterns", primary.patternCount());
            row.put("total_ms", primary.timing().totalMs());
            row.put("issue", issue);
            rows.add(row);
        }
        Map<String, Object> m = envelope(p);
        m.put("rows", rows);
        return m;
    }

    // ---------------------------------------------------------------- helpers

    /** Builds a validated {@link MiningConfig}; value/domain errors become protocol errors. */
    MiningConfig buildConfig(double partial, double f, double minOcc, Params p) {
        double epsilon = p.has("epsilon") ? p.requiredNum("epsilon") : MiningConfig.DEFAULT_EPSILON;
        int workers = p.intVal("workers", MiningConfig.DEFAULT_WORKERS);
        try {
            return new MiningConfig(partial, f, epsilon, minOcc, workers);
        } catch (IllegalArgumentException e) {
            throw new ManagerException(Codes.INVALID_PARAMETER, e.getMessage());
        }
    }

    private List<String> versionsOrDefault(Params p) {
        List<String> versions = p.strList("versions");
        if (versions == null || versions.isEmpty()) {
            return List.of("v3");
        }
        return versions;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMapOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw ManagerException.invalid("versionConfig must be a JSON object");
    }

    private static void closeQuietly(Engine engine) {
        if (engine instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    // ---------------------------------------------------------------- misc formatting

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%g", v);
    }

    /** Exposed for tests: serialize a response map in a stable order. */
    public static String toJson(Map<String, Object> response) {
        return Json.stringify(response);
    }

    /** Error envelope ({@code ok:false, code, message}) shared by every channel. */
    public static Map<String, Object> errorResponse(long id, String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("v", PROTOCOL);
        m.put("ok", false);
        m.put("code", code);
        m.put("message", message);
        return m;
    }
}
