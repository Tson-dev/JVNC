package org.jvnc.dhopm.v1;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class DatasetReader {
    private DatasetReader() {
    }

    public static List<List<String>> read(Path path, int limit) throws IOException {
        if (limit < 0) {
            throw new IllegalArgumentException("Transaction limit cannot be negative.");
        }
        List<List<String>> transactions = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                List<String> items = List.of(tokens);
                if (new HashSet<>(items).size() != items.size()) {
                    throw new IOException("Duplicate item on line " + lineNumber + ".");
                }
                transactions.add(items);
                if (limit > 0 && transactions.size() >= limit) {
                    break;
                }
            }
        }
        return List.copyOf(transactions);
    }
}
