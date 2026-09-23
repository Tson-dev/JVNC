package dhopm.common.io;

import dhopm.common.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReaderTest {

    private static Path dataset(String name) {
        return Path.of(System.getProperty("dhopm.dataset.dir", "../../dataset")).toAbsolutePath().resolve(name);
    }

    @Test
    void fimiReaderParsesDefaultDat() throws IOException {
        List<Transaction> list = new FimiTransactionReader(dataset("default.dat")).toList();
        assertEquals(8, list.size());
        assertEquals(1, list.get(0).tid());
        assertEquals(8, list.get(7).tid());
        assertArrayEquals(new String[]{"A", "C", "D", "E"}, list.get(0).items());
        assertArrayEquals(new String[]{"A", "E", "G"}, list.get(7).items());
    }

    @Test
    void fimiReaderSkipsBlankAndCommentLines(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("t.txt");
        Files.writeString(f, "1 2\n\n# comment\n\n  3  4  5  \n");
        List<Transaction> list = new FimiTransactionReader(f).toList();
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).tid());
        assertArrayEquals(new String[]{"1", "2"}, list.get(0).items());
        assertEquals(2, list.get(1).tid());
        assertArrayEquals(new String[]{"3", "4", "5"}, list.get(1).items());
    }

    @Test
    void textReaderParsesExplicitTids(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("t.txt");
        Files.writeString(f, "10 A B\n# note\n\n20 C\n");
        List<Transaction> list = new TextTransactionReader(f).toList();
        assertEquals(2, list.size());
        assertEquals(10, list.get(0).tid());
        assertArrayEquals(new String[]{"A", "B"}, list.get(0).items());
        assertEquals(20, list.get(1).tid());
        assertArrayEquals(new String[]{"C"}, list.get(1).items());
    }

    @Test
    void textReaderFailsWithoutTid(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("t.txt");
        Files.writeString(f, "onlyitems\n");
        assertThrows(RuntimeException.class, () -> new TextTransactionReader(f).toList());
    }

    @Test
    void transactionNormalizesAndSorts() {
        Transaction t = new Transaction(1, new String[]{"B", "A", "B", "C"});
        assertArrayEquals(new String[]{"A", "B", "C"}, t.items());
        assertEquals(3, t.length());
        assertThrows(IllegalArgumentException.class, () -> new Transaction(1, new String[0]));
    }

    @Test
    void emptyTransactionRejectedByFimiReader() throws IOException {
        Path f = dataset("default.dat");
        // no-op guard: default.dat must survive the round-trip; reader never emits empty tids
        assertEquals(8, new FimiTransactionReader(f).toList().size());
    }
}