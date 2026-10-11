package dhopm.v3.cli;

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
 * Minimal standalone V3 CLI (G3): same dispatch style as V1/V2 on a small FIMI dataset —
 * {@code mine}/{@code inspect}/{@code window}/{@code validate}, legacy {@code --dataset} syntax,
 * {@code --limit} handling and dataset-extension guards.
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
                int code = Main.dispatch(args);
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
        assertTrue(out.contains("window:"), "window block expected:\n" + out);
        assertTrue(out.contains("patterns ("), "DOP-list header expected:\n" + out);
        assertTrue(out.contains("DO="), "DOP rows expected:\n" + out);
    }

    @Test
    void inspectCommandReportsDatasetStats() throws Exception {
        String out = run("inspect", "--dataset", fimi().toString());
        assertTrue(out.contains("transactions = 8"), "transaction count expected:\n" + out);
        assertTrue(out.contains("max_len"), "max length expected:\n" + out);
        assertTrue(out.contains("window"), "window stats expected:\n" + out);
    }

    @Test
    void windowCommandPrintsAsymptoticBound() throws Exception {
        String out = run("window", "--f", "0.9", "--minOcc", "0.000001");
        assertTrue(out.contains("W(f,minOcc) = 153"), "window size expected:\n" + out);
        assertTrue(out.contains("dp_max(asymptotic)"), "asymptotic bound expected:\n" + out);
        assertTrue(out.contains("dp_max(exact) = null"), "exact bound must need a dataset:\n" + out);
    }

    @Test
    void validateCommandIsValid() throws Exception {
        String out = run("validate", "--f", "0.9", "--minOcc", "0.000001", "--partial", "0.15");
        assertTrue(out.contains("== VALID =="), "config should be valid:\n" + out);
    }

    @Test
    void limitStopsAfterRequestedTransactions() throws Exception {
        String out = run("mine", "--dataset", fimi().toString(), "--limit", "3",
                "--partial", "0.15", "--f", "0.9", "--parts", "1");
        assertTrue(out.contains("limit=3"), "limit must appear in header:\n" + out);
        assertTrue(out.contains("stopped after reading 3"), "truncation marker expected:\n" + out);
        assertTrue(!out.contains("whole dataset read"), "must not claim full read:\n" + out);
    }

    @Test
    void limitLargerThanDatasetReadsEverything() throws Exception {
        String out = run("inspect", "--dataset", fimi().toString(), "--limit", "100");
        assertTrue(out.contains("limit=100"), "limit must appear in header:\n" + out);
        assertTrue(out.contains("whole dataset read (8 tx)"), "whole-dataset marker expected:\n" + out);
    }

    @Test
    void negativeLimitIsRejected() throws Exception {
        CliSupport.CliException e = assertThrows(CliSupport.CliException.class,
                () -> Main.dispatch(new String[]{"mine", "--dataset", fimi().toString(), "--limit", "-1"}));
        assertTrue(e.getMessage().contains("--limit needs a non-negative long"),
                "negative limit must be rejected, got: " + e.getMessage());
    }

    @Test
    void unknownCommandFailsGracefully() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream old = System.out;
        try (PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            System.setOut(ps);
            int code = Main.dispatch(new String[]{"frobnicate"});
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
    void mineRejectsDatasetPathWithoutExtension() throws Exception {
        Path noExt = tmp.resolve("noextension");
        Files.write(noExt, lines, StandardCharsets.UTF_8);
        CliSupport.CliException e = assertThrows(CliSupport.CliException.class,
                () -> Main.dispatch(new String[]{"mine", "--dataset", noExt.toString()}));
        assertTrue(e.getMessage().contains("needs a file extension"), e.getMessage());
    }

    @Test
    void mineRejectsRarAndSevenZip() throws Exception {
        for (String name : new String[]{"data.rar", "data.7z"}) {
            Path file = tmp.resolve(name);
            Files.write(file, lines, StandardCharsets.UTF_8);
            CliSupport.CliException e = assertThrows(CliSupport.CliException.class,
                    () -> Main.dispatch(new String[]{"mine", "--dataset", file.toString()}), name);
            assertTrue(e.getMessage().contains("only .zip is supported"), e.getMessage());
        }
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
