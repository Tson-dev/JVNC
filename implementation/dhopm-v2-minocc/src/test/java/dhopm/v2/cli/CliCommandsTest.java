package dhopm.v2.cli;

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
 * V2 command set on the same mini FIMI dataset: {@code mine} returns the DOP list, {@code detail}
 * adds the DHO-List after reconstruction with root markers, and {@code stream} emits each pattern in
 * real time (+ optional {@code --step --detail} mining-step log) and the final DOP list.
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
    void mineReturnsDopList() throws Exception {
        String out = run("mine", "--dataset", fimi().toString(), "--partial", "0.15", "--f", "0.9", "--parts", "1");
        assertTrue(out.contains("patterns ("), "DOP-list header expected:\n" + out);
        assertTrue(out.contains("DO="), "DOP rows expected:\n" + out);
    }

    @Test
    void detailReportsDhoListAfterReconstruction() throws Exception {
        String out = run("detail", "--dataset", fimi().toString(), "--parts", "1");
        assertTrue(out.contains("DHO-List after reconstruction"),
                "DHO-List after reconstruction expected:\n" + out);
        assertTrue(out.contains("[R]") || out.contains("[ ]"), "root markers expected:\n" + out);
        assertTrue(out.contains("head="), "head offset column expected for V2:\n" + out);
    }

    @Test
    void streamEmitsPatternsAndFinalDop() throws Exception {
        String out = run("stream", "--dataset", fimi().toString(), "--parts", "1");
        assertTrue(out.contains("load: part=1"), "load event expected:\n" + out);
        assertTrue(out.contains("DOP ("), "final DOP list expected:\n" + out);
    }

    @Test
    void streamStepLogsMiningSteps() throws Exception {
        String out = run("stream", "--dataset", fimi().toString(), "--parts", "1", "--step", "--detail", "0");
        assertTrue(out.contains("step DOP_CHECK") || out.contains("step UBDO"),
                "step log expected with --step --detail 0:\n" + out);
    }
}
