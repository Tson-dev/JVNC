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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void mineAcceptsZippedDataset() throws Exception {
        Path zip = zipOf(fimi(), "mini.dat.zip");
        String out = run("mine", "--dataset", zip.toString(), "--partial", "0.15", "--f", "0.9", "--parts", "1");
        assertTrue(out.contains("total_ms"), "mine must run on a .zip dataset:\n" + out);
        assertTrue(out.contains("mini.dat"), "inner entry name should be reported:\n" + out);
    }

    @Test
    void zippedDatasetMatchesPlainFileResult() throws Exception {
        Path plain = fimi();
        String fromPlain = run("mine", "--dataset", plain.toString(), "--parts", "1");
        String fromZip = run("mine", "--dataset", zipOf(plain, "same.dat.zip").toString(), "--parts", "1");
        assertEquals(patternColumn(fromPlain), patternColumn(fromZip),
                "zip must not change mining result");
    }

    @Test
    void mineRejectsDatasetPathWithoutExtension() throws Exception {
        Path noExt = tmp.resolve("noextension");
        Files.write(noExt, lines, StandardCharsets.UTF_8);
        CliSupport.CliException e = assertThrows(CliSupport.CliException.class,
                () -> new Main().dispatch(new String[]{"mine", "--dataset", noExt.toString()}));
        assertTrue(e.getMessage().contains("needs a file extension"), e.getMessage());
    }

    @Test
    void mineRejectsRarAndSevenZip() throws Exception {
        for (String name : new String[]{"data.rar", "data.7z"}) {
            Path file = tmp.resolve(name);
            Files.write(file, lines, StandardCharsets.UTF_8);
            CliSupport.CliException e = assertThrows(CliSupport.CliException.class,
                    () -> new Main().dispatch(new String[]{"mine", "--dataset", file.toString()}), name);
            assertTrue(e.getMessage().contains("only .zip is supported"), e.getMessage());
        }
    }

    @Test
    void mineRejectsUnknownExtension() throws Exception {
        Path file = tmp.resolve("data.parquet");
        Files.write(file, lines, StandardCharsets.UTF_8);
        CliSupport.CliException e = assertThrows(CliSupport.CliException.class,
                () -> new Main().dispatch(new String[]{"mine", "--dataset", file.toString()}));
        assertTrue(e.getMessage().contains("unsupported dataset extension"), e.getMessage());
    }

    /**
     * Extracts the pattern counts from a mine run so plain and zipped inputs can be compared.
     * Deliberately ignores timing and heap columns, which legitimately differ between the two.
     */
    private static String patternColumn(String out) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher total =
                java.util.regex.Pattern.compile("patterns\\(last part\\)=(\\d+)").matcher(out);
        if (total.find()) {
            sb.append("total=").append(total.group(1));
        }
        for (String line : out.split("\\R")) {
            String t = line.trim();
            // per-part rows are purely numeric: part loaded last_tid ... patterns heap_mb
            if (!t.matches("^\\d+(?:\\s+\\d+(?:\\.\\d+)?)+$")) {
                continue;
            }
            String[] parts = t.split("\\s+");
            sb.append(" | p=").append(parts[parts.length - 2]);
        }
        return sb.toString();
    }

    private Path zipOf(Path source, String zipName) throws Exception {
        Path zip = tmp.resolve(zipName);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(source.getFileName().toString()));
            out.write(Files.readAllBytes(source));
            out.closeEntry();
        }
        return zip;
    }
}