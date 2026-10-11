package dhopm.cli;

import dhopm.cli.config.ConfigStore;
import dhopm.cli.json.Json;
import dhopm.cli.store.ResultStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manager CLI/API (G5): exercises the shared dispatcher through both the CLI argv form
 * ({@link Arguments}) and the JSON wire form, covering mine/stream, sessions, config, export/fetch
 * and the v1 paper-mode guard.
 */
class ManagerCliTest {

    @TempDir
    Path tmp;

    private final List<String> lines = Arrays.asList(
            "1 2 3", "1 2 4", "2 3 4", "1 3 4", "1 2 3 4", "2 3", "1 4", "3 4");

    private Path fimi() throws Exception {
        Path file = tmp.resolve("mini.dat");
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private CommandDispatcher dispatcher() {
        return new CommandDispatcher(new VersionRegistry(), new ConfigStore(tmp.resolve("config.json")),
                new ResultStore(tmp.resolve("mine")));
    }

    private static Map<String, Object> req(String... args) {
        return Arguments.parse(args);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String text) {
        return (Map<String, Object>) Json.parse(text);
    }

    @Test
    void infoAdvertisesReadyVersions() {
        String body = Json.stringify(dispatcher().dispatch(req("info")));
        assertTrue(body.contains("\"protocol\":1"), body);
        assertTrue(body.contains("\"id\":\"v3\""), body);
        assertTrue(body.contains("\"status\":\"ready\""), body);
        assertTrue(body.contains("\"id\":\"v4\""), body);
        assertTrue(body.contains("\"status\":\"pending\""), body);
    }

    @Test
    void windowReportsAsymptoticBound() {
        Map<String, Object> response = dispatcher().dispatch(
                req("window", "0.9", "--minOcc", "0.000001"));
        Map<String, Object> window = json(Json.stringify(response.get("window")));
        assertEquals(153, ((Number) window.get("size")).intValue());
        assertNotNull(response.get("maxPartialAsymptotic"));
    }

    @Test
    void mineReturnsPatterns() throws Exception {
        Map<String, Object> response = dispatcher().dispatch(req(
                "mine", fimi().toString(), "0.15", "0.9", "0", "--minOcc", "0.000001",
                "--versions", "v3"));
        assertEquals(Boolean.TRUE, response.get("ok"));
        List<?> patterns = (List<?>) response.get("patterns");
        assertFalse(patterns.isEmpty(), Json.stringify(response));
        List<?> jobs = (List<?>) response.get("jobs");
        assertEquals(1, jobs.size());
    }

    @Test
    void v1RejectsNonZeroMinOcc() throws Exception {
        ManagerException e = assertThrows(ManagerException.class, () -> dispatcher().dispatch(req(
                "mine", fimi().toString(), "0.15", "0.9", "0", "--minOcc", "0.001",
                "--versions", "v1")));
        assertEquals(Codes.INVALID_PARAMETER, e.code());
        assertTrue(e.getMessage().contains("paper mode"), e.getMessage());
    }

    @Test
    void streamEmitsEventsBeforeResponse() throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        dispatcher().dispatch(req("stream", fimi().toString(), "0.15", "0.9", "0",
                "--minOcc", "0.000001", "--versions", "v3", "--step", "--detail", "2"), events::add);
        assertFalse(events.isEmpty(), "stream must emit events");
        assertTrue(events.stream().anyMatch(e -> "progress".equals(e.get("event"))), "progress expected");
        assertTrue(events.stream().anyMatch(e -> "window".equals(e.get("event"))), "window expected");
        assertTrue(events.stream().anyMatch(e -> "step".equals(e.get("event"))), "step expected");
        assertTrue(events.stream().allMatch(e -> e.get("v").equals(1)), "every event carries protocol version");
    }

    @Test
    void sessionLifecycle() {
        CommandDispatcher d = dispatcher();
        Map<String, Object> created = d.dispatch(json(
                "{\"cmd\":\"session.create\",\"config\":{\"partial\":0.15,\"f\":0.9,\"minOcc\":0}}"));
        String id = String.valueOf(created.get("session"));

        Map<String, Object> feed = d.dispatch(json(
                "{\"cmd\":\"feed\",\"session\":\"" + id + "\",\"transactions\":["
                        + "{\"tid\":1,\"items\":[\"A\",\"E\"]},{\"tid\":2,\"items\":[\"A\",\"E\",\"F\"]}]}"));
        assertEquals(2, ((Number) feed.get("loaded")).intValue());

        Map<String, Object> mined = d.dispatch(json(
                "{\"cmd\":\"session.mine\",\"session\":\"" + id + "\",\"versions\":[\"v3\"],\"minOcc\":0}"));
        assertFalse(((List<?>) mined.get("patterns")).isEmpty(), Json.stringify(mined));
    }

    @Test
    void configGetSetList() {
        CommandDispatcher d = dispatcher();
        d.dispatch(json("{\"cmd\":\"config\",\"action\":\"set\",\"key\":\"detail\",\"value\":\"2\"}"));
        Map<String, Object> got = d.dispatch(json("{\"cmd\":\"config\",\"action\":\"get\",\"key\":\"detail\"}"));
        assertEquals(2, ((Number) got.get("value")).intValue());
        Map<String, Object> listed = d.dispatch(json("{\"cmd\":\"config\",\"action\":\"list\"}"));
        assertTrue(listed.containsKey("config"));
    }

    @Test
    void configRejectsUnknownKey() {
        ManagerException e = assertThrows(ManagerException.class,
                () -> dispatcher().dispatch(json("{\"cmd\":\"config\",\"action\":\"get\",\"key\":\"nope\"}")));
        assertEquals(Codes.CONFIG_KEY_UNKNOWN, e.code());
    }

    @Test
    void exportThenFetchRoundTrip() throws Exception {
        CommandDispatcher d = dispatcher();
        Map<String, Object> mined = d.dispatch(req("mine", fimi().toString(), "0.15", "0.9", "0",
                "--minOcc", "0.000001", "--versions", "v3", "--export"));
        Map<String, Object> saved = (Map<String, Object>) mined.get("saved");
        assertNotNull(saved, "export must record a saved file");
        String fileID = String.valueOf(saved.get("fileID"));

        d.results().flush();
        Map<String, Object> fetched = d.dispatch(req("fetch", fileID));
        assertEquals(fileID, fetched.get("fileID"));
        assertTrue(String.valueOf(fetched.get("content")).contains("patterns"), Json.stringify(fetched));
    }

    @Test
    void bareExportFlagParsesAsBoolean() {
        Map<String, Object> parsed = req("mine", "d", "0.1", "0.9", "0", "--minOcc", "0", "--export");
        assertEquals(Boolean.TRUE, parsed.get("export"));
    }

    @Test
    void malformedJsonBecomesInvalidParameter() {
        String response = new ServeLoop(dispatcher()).handleLine("{not json");
        assertTrue(response.contains(Codes.INVALID_PARAMETER), response);
    }

    @Test
    void unknownCommandIsReported() {
        String response = new ServeLoop(dispatcher()).handleLine("{\"id\":5,\"cmd\":\"bogus\"}");
        assertTrue(response.contains(Codes.UNKNOWN_COMMAND), response);
        assertTrue(response.contains("\"id\":5"), response);
    }
}
