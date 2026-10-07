package dhopm.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Opens a ZIP-packaged dataset.
 *
 * <p>The archive is expected to hold exactly one dataset file, which keeps the mapping
 * "one path = one dataset" unambiguous and avoids picking a random file out of a bundle.
 * Only ZIP is supported — see {@link DatasetFile} for why {@code .rar} / {@code .7z} are rejected.
 *
 * <p>Reading is streamed: the entry is inflated on demand and never materialised on disk, so a
 * multi-hundred-megabyte dataset costs only the buffer it is compressed into.
 */
public final class ZipDataset implements AutoCloseable {

    private final ZipFile zip;
    private final ZipEntry entry;

    private ZipDataset(ZipFile zip, ZipEntry entry) {
        this.zip = zip;
        this.entry = entry;
    }

    /**
     * Opens the single dataset entry inside {@code archive}.
     *
     * @throws IOException when the file is not a readable ZIP, holds no entry, or holds more than one
     */
    public static ZipDataset open(Path archive) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(archive.toFile());
        } catch (ZipException e) {
            throw new IOException("not a readable ZIP archive: " + archive, e);
        }
        List<? extends ZipEntry> entries = zip.stream()
                .filter(e -> !e.isDirectory())
                .toList();
        if (entries.isEmpty()) {
            zip.close();
            throw new IOException("ZIP archive holds no dataset file: " + archive);
        }
        if (entries.size() > 1) {
            int n = entries.size();
            zip.close();
            throw new IOException("ZIP archive must hold exactly 1 dataset file, found " + n + ": " + archive);
        }
        return new ZipDataset(zip, entries.get(0));
    }

    /** Name of the dataset file inside the archive (for example {@code retail.dat}). */
    public String entryName() {
        return entry.getName();
    }

    /** Uncompressed size in bytes, or {@code -1} when the archive does not record it. */
    public long uncompressedSize() {
        return entry.getSize();
    }

    /** Opens a fresh stream over the dataset entry; the caller owns it. */
    public InputStream openStream() throws IOException {
        return zip.getInputStream(entry);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /** True when {@code path} exists and is a regular file ending in {@code .zip}. */
    public static boolean looksLikeZip(Path path) {
        return Files.isRegularFile(path) && DatasetFile.kindOf(DatasetFile.extensionOf(path)) == DatasetFile.Kind.ZIP;
    }
}