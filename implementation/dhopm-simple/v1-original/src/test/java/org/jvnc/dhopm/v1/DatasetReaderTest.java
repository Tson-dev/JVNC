package org.jvnc.dhopm.v1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsNonEmptyLinesAndHonorsTransactionLimit() throws Exception {
        Path file = tempDir.resolve("input.dat");
        Files.writeString(file, "A B\n\nC\tD\nE\n");

        assertEquals(2, DatasetReader.read(file, 2).size());
        assertEquals(3, DatasetReader.read(file, 0).size());
    }

    @Test
    void rejectsDuplicateItemsOnOneTransaction() throws Exception {
        Path file = tempDir.resolve("duplicate.dat");
        Files.writeString(file, "A B A\n");

        assertThrows(IOException.class, () -> DatasetReader.read(file, 0));
    }
}
