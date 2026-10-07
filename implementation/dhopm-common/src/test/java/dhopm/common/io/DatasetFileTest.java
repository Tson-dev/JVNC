package dhopm.common.io;

import dhopm.common.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extension policy + ZIP-packaged datasets: accepted extensions open, rejected ones fail with a
 * message the CLI can print, and a zipped dataset yields exactly the same transactions as the
 * plain file it was packed from.
 */
class DatasetFileTest {

    @TempDir
    Path tmp;

    private static final List<String> LINES = List.of(
            "1 2 3", "1 2 4", "2 3 4", "1 3 4", "1 2 3 4", "2 3", "1 4", "3 4");

    private Path plain() throws IOException {
        Path file = tmp.resolve("mini.dat");
        Files.write(file, LINES, StandardCharsets.UTF_8);
        return file;
    }

    private Path zippedOf(Path source, String zipName) throws IOException {
        Path zip = tmp.resolve(zipName);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(source.getFileName().toString()));
            out.write(Files.readAllBytes(source));
            out.closeEntry();
        }
        return zip;
    }

    @Test
    void textExtensionsAreAccepted() {
        assertEquals(DatasetFile.Kind.TEXT, DatasetFile.requireSupported(tmp.resolve("a.dat")));
        assertEquals(DatasetFile.Kind.TEXT, DatasetFile.requireSupported(tmp.resolve("a.txt")));
        assertEquals(DatasetFile.Kind.TEXT, DatasetFile.requireSupported(tmp.resolve("a.text")));
        assertEquals(DatasetFile.Kind.TEXT, DatasetFile.requireSupported(tmp.resolve("a.csv")));
        assertEquals(DatasetFile.Kind.TEXT, DatasetFile.requireSupported(tmp.resolve("a.tsv")));
    }

    @Test
    void extensionMatchIsCaseInsensitive() {
        assertEquals(DatasetFile.Kind.TEXT, DatasetFile.requireSupported(tmp.resolve("a.DAT")));
        assertEquals(DatasetFile.Kind.ZIP, DatasetFile.requireSupported(tmp.resolve("a.ZIP")));
    }

    @Test
    void zipExtensionIsAccepted() {
        assertEquals(DatasetFile.Kind.ZIP, DatasetFile.requireSupported(tmp.resolve("a.zip")));
    }

    @Test
    void missingExtensionIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DatasetFile.requireSupported(tmp.resolve("dataset")));
        assertTrue(e.getMessage().contains("needs a file extension"), e.getMessage());
    }

    @Test
    void trailingDotCountsAsNoExtension() {
        assertThrows(IllegalArgumentException.class, () -> DatasetFile.requireSupported(tmp.resolve("a.")));
    }

    @Test
    void rarAndSevenZipAreRejectedWithGuidance() {
        for (String name : List.of("a.rar", "a.7z")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> DatasetFile.requireSupported(tmp.resolve(name)), name);
            assertTrue(e.getMessage().contains("only .zip is supported"), e.getMessage());
        }
    }

    @Test
    void unknownExtensionIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DatasetFile.requireSupported(tmp.resolve("a.parquet")));
        assertTrue(e.getMessage().contains("unsupported dataset extension"), e.getMessage());
    }

    @Test
    void zipDatasetYieldsSameTransactionsAsPlainFile() throws IOException {
        Path plain = plain();
        Path zip = zippedOf(plain, "mini.dat.zip");

        List<Transaction> fromPlain = new FimiTransactionReader(plain).toList();
        List<Transaction> fromZip;
        try (ZipDataset zd = ZipDataset.open(zip)) {
            assertEquals("mini.dat", zd.entryName());
            fromZip = new FimiTransactionReader(zd.openStream()).toList();
        }

        assertEquals(fromPlain.size(), fromZip.size());
        for (int i = 0; i < fromPlain.size(); i++) {
            assertEquals(fromPlain.get(i).tid(), fromZip.get(i).tid());
            assertArrayEquals(fromPlain.get(i).items(), fromZip.get(i).items());
        }
    }

    @Test
    void zipWithMoreThanOneEntryIsRejected() throws IOException {
        Path zip = tmp.resolve("two.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("a.dat"));
            out.write("1 2 3\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("b.dat"));
            out.write("1 2\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        IOException e = assertThrows(IOException.class, () -> ZipDataset.open(zip));
        assertTrue(e.getMessage().contains("exactly 1 dataset file"), e.getMessage());
    }

    @Test
    void zipWithNoEntriesIsRejected() throws IOException {
        Path zip = tmp.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.finish();
        }
        IOException e = assertThrows(IOException.class, () -> ZipDataset.open(zip));
        assertTrue(e.getMessage().contains("no dataset file"), e.getMessage());
    }

    @Test
    void nonZipContentIsRejected() throws IOException {
        Path fake = tmp.resolve("fake.zip");
        Files.write(fake, "not a zip at all".getBytes(StandardCharsets.UTF_8));
        IOException e = assertThrows(IOException.class, () -> ZipDataset.open(fake));
        assertTrue(e.getMessage().contains("not a readable ZIP"), e.getMessage());
    }

    @Test
    void textReaderAlsoWorksOverZipStream() throws IOException {
        Path text = tmp.resolve("tc.txt");
        Files.write(text, List.of("1 2 3", "2 3 4 5"), StandardCharsets.UTF_8);
        Path zip = zippedOf(text, "tc.txt.zip");
        try (ZipDataset zd = ZipDataset.open(zip)) {
            List<Transaction> all = new TextTransactionReader(zd.openStream()).toList();
            assertEquals(2, all.size());
            assertEquals(1, all.get(0).tid());
            assertArrayEquals(new String[]{"2", "3"}, all.get(0).items());
            assertEquals(2, all.get(1).tid());
            assertArrayEquals(new String[]{"3", "4", "5"}, all.get(1).items());
        }
    }

    @Test
    void looksLikeZipDistinguishesRealZips() throws IOException {
        assertTrue(ZipDataset.looksLikeZip(zippedOf(plain(), "look.dat.zip")));
        assertTrue(!ZipDataset.looksLikeZip(plain()));
        assertTrue(!ZipDataset.looksLikeZip(tmp.resolve("missing.zip")));
    }
}