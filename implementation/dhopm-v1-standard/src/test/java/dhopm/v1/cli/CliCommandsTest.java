package dhopm.v1.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ lệnh chuẩn (G1-D7) chạy trên cùng dataset FIMI tạm: xuất đúng định dạng từng lệnh,
 * cú pháp cũ {@code --dataset …} = {@code mine}, và {@code golden} pass đủ TC1-TC8.
 */
class CliCommandsTest {

    @TempDir
    Path tmp;

    private final List<String> lines = Arrays.asList(
            "1 2 3", "1 2 4", "2 3 4", "1 3 4", "1 2 3 4", "2 3", "1 4", "3 4");

    private Path fimi() throws Exception {
        Path file = tmp.resolve("mini.dat");
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private String run(String... args) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            System.setOut(ps);
            try {
                int code = new Main().dispatch(args);
                assertEquals(0, code, "command must exit 0: " + Arrays.toString(args));
            } finally {
                System.setOut(old);
            }
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void legacyArgsAreMine() throws Exception {
        String out = run("--dataset", fimi().toString(), "--partial", "0.15", "--f", "0.9", "--parts", "1");
        assertTrue(out.contains("part"), "mine table header expected:\n" + out);
        assertTrue(out.contains("patterns"), "mine summary expected:\n" + out);
    }

    @Test
    void mineCommand() throws Exception {
        String out = run("mine", "--dataset", fimi().toString(), "--partial", "0.15", "--f", "0.9", "--parts", "1");
        assertTrue(out.contains("total_ms"), "total line expected:\n" + out);
    }

    @Test
    void detailCommandReportsPerPartStats() throws Exception {
        String out = run("detail", "--dataset", fimi().toString(), "--parts", "2", "--top", "3");
        assertTrue(out.contains("global.nodes"), "node stats expected:\n" + out);
        assertTrue(out.contains("rootTasks"), "root task count expected:\n" + out);
        assertTrue(out.contains("construction="), "phase timing expected:\n" + out);
        assertTrue(out.contains("top "), "top-N patterns expected:\n" + out);
    }

    @Test
    void streamCommandEmitsLoadAndTickEvents() throws Exception {
        String out = run("stream", "--dataset", fimi().toString(), "--parts", "1");
        assertTrue(out.contains("[load  ]"), "load event expected:\n" + out);
        assertTrue(out.contains("[mining]"), "mining completion event expected:\n" + out);
    }

    @Test
    void inspectCommandReportsDatasetStats() throws Exception {
        String out = run("inspect", "--dataset", fimi().toString(), "--top", "2");
        assertTrue(out.contains("distinctItems"), "distinct item count expected:\n" + out);
        assertTrue(out.contains("avgLen"), "avg length expected:\n" + out);
        assertTrue(out.contains("support="), "top items expected:\n" + out);
    }

    @Test
    void limitStopsAfterRequestedTransactions() throws Exception {
        // dataset có 8 tx; --limit 3 -> chỉ đọc 3 tx đầu rồi mining
        String out = run("mine", "--dataset", fimi().toString(), "--limit", "3",
                "--partial", "0.15", "--f", "0.9", "--parts", "1");
        assertTrue(out.contains("limit=3"), "limit must appear in header:\n" + out);
        assertTrue(out.contains("stopped after reading 3"), "truncation marker expected:\n" + out);
        assertTrue(!out.contains("whole dataset read"), "must not claim full read:\n" + out);
    }

    @Test
    void limitLargerThanDatasetReadsEverything() throws Exception {
        // dataset chỉ có 8 tx; --limit 100 -> đọc HẾT, đánh dấu và bỏ qua limit
        String out = run("inspect", "--dataset", fimi().toString(), "--limit", "100");
        assertTrue(out.contains("limit=100"), "limit must appear in header:\n" + out);
        assertTrue(out.contains("whole dataset read (8 tx)"), "whole-dataset marker expected:\n" + out);
        assertTrue(out.contains("distinctItems"), "full stats expected:\n" + out);
    }

    @Test
    void negativeLimitIsRejected() throws Exception {
        try {
            new Main().dispatch(new String[]{"mine", "--dataset", fimi().toString(), "--limit", "-1"});
            throw new AssertionError("expected CliException for negative limit");
        } catch (CliSupport.CliException e) {
            assertTrue(e.getMessage().contains("--limit needs a non-negative long"),
                    "negative limit must be rejected, got: " + e.getMessage());
        }
    }

    @Test
    void goldenCommandPassesAllCases() throws Exception {
        String out = run("golden");
        assertTrue(out.contains("ALL 8 TC PASS"), "golden must pass all TC1-TC8:\n" + out);
    }

    @Test
    void unknownCommandFailsGracefully() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream old = System.out;
        try (PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            System.setOut(ps);
            int code = new Main().dispatch(new String[]{"frobnicate"});
            assertEquals(2, code);
        } finally {
            System.setOut(old);
        }
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("unknown command"));
    }
}