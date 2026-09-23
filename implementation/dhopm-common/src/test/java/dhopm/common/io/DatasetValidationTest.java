package dhopm.common.io;

import dhopm.common.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regenerates the dataset statistics table (OVERALL-PLAN 5.2) from the actual files
 * and asserts they still match. Also guarantees every dataset is readable by the FIMI reader
 * and explicitly validates scalability source kosarak.
 */
class DatasetValidationTest {

    private record Expected(String file, int transactions, int distinctItems, double avgLen) {
    }

    private static final List<Expected> TABLE = List.of(
            new Expected("chess.dat", 3_196, 75, 37.0),
            new Expected("connect.dat", 67_557, 129, 43.0),
            new Expected("kosarak.dat", 990_002, 41_270, 8.1),
            new Expected("mushroom.dat", 8_124, 119, 23.0),
            new Expected("pumsb.dat", 49_046, 2_113, 74.0),
            new Expected("pumsb_star.dat", 49_046, 2_088, 50.5),
            new Expected("retail.dat", 88_162, 16_470, 10.3));

    private static Path datasetDir() {
        return Path.of(System.getProperty("dhopm.dataset.dir", "../../dataset")).toAbsolutePath();
    }

    @Test
    void allSevenDatasetsMatchTable52() throws IOException {
        Path dir = datasetDir();
        assertTrue(Files.isDirectory(dir), "dataset dir not found: " + dir);
        for (Expected e : TABLE) {
            Path file = dir.resolve(e.file());
            assertTrue(Files.isRegularFile(file), "missing: " + e.file());

            List<Transaction> stream = new FimiTransactionReader(file).toList();
            assertEquals(e.transactions, stream.size(), e.file() + " transactions (TID counter)");

            Set<String> items = new HashSet<>();
            long totalItems = 0;
            for (Transaction t : stream) {
                totalItems += t.length();
                for (String s : t.items()) {
                    items.add(s);
                }
            }
            assertEquals(e.distinctItems, items.size(), e.file() + " distinct items");
            double avgLen = totalItems / (double) stream.size();
            assertEquals(e.avgLen, avgLen, 0.06, e.file() + " avg length");

            int[] tids = stream.stream().mapToInt(Transaction::tid).toArray();
            for (int i = 1; i < tids.length; i++) {
                assertTrue(tids[i] > tids[i - 1], e.file() + " TID must be strictly increasing");
            }
        }
    }

    @Test
    void kosarakIsLoadedForScalability() throws IOException {
        Path file = datasetDir().resolve("kosarak.dat");
        long first = new FimiTransactionReader(file).streamPositions(990_000);
        assertTrue(first >= 990_000, "kosarak must support 200K..990K scalability window");
    }
}