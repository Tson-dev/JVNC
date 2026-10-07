package dhopm.common.io;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Extension policy for a dataset path handed to the CLI.
 *
 * <p>A dataset path must carry an explicit file extension, and that extension decides how the
 * bytes are opened:
 *
 * <ul>
 *   <li>{@link Kind#TEXT} — plain text, one transaction per line (FIMI or {@code <TID> items} text).
 *   <li>{@link Kind#ZIP} — a ZIP archive holding exactly one dataset file.</li>
 * </ul>
 *
 * <p>Archive formats other than ZIP are rejected on purpose: ZIP is the format the JDK reads
 * natively ({@link java.util.zip.ZipInputStream}) and the one every target platform ships tools
 * for, so {@code .rar} / {@code .7z} would need a third-party dependency for no benefit.
 */
public final class DatasetFile {

    /** How the dataset bytes are opened. */
    public enum Kind {
        TEXT,
        ZIP
    }

    private static final Set<String> TEXT_EXTENSIONS = Set.of("dat", "txt", "text", "csv", "tsv");
    private static final Set<String> ZIP_EXTENSIONS = Set.of("zip");
    private static final Set<String> REJECTED_ARCHIVE_EXTENSIONS = Set.of("rar", "7z");

    private DatasetFile() {
    }

    /**
     * Lower-cased extension without the leading dot, or {@code ""} when the file name has none.
     */
    public static String extensionOf(Path path) {
        Path name = path.getFileName();
        if (name == null) {
            return "";
        }
        String fileName = name.toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static Kind kindOf(String extension) {
        if (ZIP_EXTENSIONS.contains(extension)) {
            return Kind.ZIP;
        }
        return Kind.TEXT;
    }

    /**
     * Validates that {@code path} names a dataset file this CLI can open.
     *
     * @return how the dataset must be opened
     * @throws IllegalArgumentException when the path has no extension, or an unsupported one;
     *         the message is user-facing because the CLI prints it verbatim
     */
    public static Kind requireSupported(Path path) {
        String extension = extensionOf(path);
        if (extension.isEmpty()) {
            throw new IllegalArgumentException("dataset path needs a file extension: " + path
                    + " (expected one of " + supported() + ")");
        }
        if (REJECTED_ARCHIVE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("unsupported archive format '." + extension + "': " + path
                    + " - only .zip is supported (JDK-native, portable);"
                    + " re-pack the dataset as .zip or use a plain text file");
        }
        if (ZIP_EXTENSIONS.contains(extension) || TEXT_EXTENSIONS.contains(extension)) {
            return kindOf(extension);
        }
        throw new IllegalArgumentException("unsupported dataset extension '." + extension + "': " + path
                + " (expected one of " + supported() + ")");
    }

    private static String supported() {
        return ".dat/.txt/.text/.csv/.tsv or .zip";
    }
}