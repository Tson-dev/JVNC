package dhopm.cli.config;

import dhopm.cli.Codes;
import dhopm.cli.ManagerException;
import dhopm.cli.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Three-level configuration (D49): {@code default (built-in) < JSON file < CLI args}.
 *
 * <p>The JSON file lives at {@code %APPDATA%\dhopm\config.json} on Windows and
 * {@code ~/.config/dhopm/config.json} otherwise. CLI arguments are supplied per request and, when
 * present, override the file and the defaults at the call site.
 */
public final class ConfigStore {

    private final Path file;
    private final Map<String, Object> defaults = new LinkedHashMap<>();
    private Map<String, Object> fileValues = new LinkedHashMap<>();

    public ConfigStore() {
        this(defaultFile());
    }

    public ConfigStore(Path file) {
        this.file = file;
        defaults.put("isLog", Boolean.FALSE);
        defaults.put("export.dir", defaultExportDir());
        defaults.put("workers", Runtime.getRuntime().availableProcessors());
        defaults.put("detail", 1);
        loadFile();
    }

    private static Path defaultFile() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "dhopm", "config.json");
            }
        }
        return Path.of(System.getProperty("user.home"), ".config", "dhopm", "config.json");
    }

    private static String defaultExportDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null && !userProfile.isBlank()) {
                return Path.of(userProfile, "mine").toString();
            }
        }
        return "/mine";
    }

    public Path file() {
        return file;
    }

    @SuppressWarnings("unchecked")
    private void loadFile() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Object parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> m) {
                fileValues = (Map<String, Object>) m;
            }
        } catch (IOException | Json.JsonException e) {
            // a corrupt config file must not stop the Manager: fall back to defaults
            fileValues = new LinkedHashMap<>();
        }
    }

    /** Effective value for {@code key}: file overrides default. */
    public Object get(String key) {
        requireKey(key);
        return fileValues.getOrDefault(key, defaults.get(key));
    }

    /** Effective values for all keys (defaults merged with file). */
    public Map<String, Object> list() {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        merged.putAll(fileValues);
        return merged;
    }

    /** Sets a value (validated against the key's type) and persists the file. */
    public Object set(String key, Object value) {
        Object normalized = coerce(key, value);
        fileValues.put(key, normalized);
        persist();
        return normalized;
    }

    private void requireKey(String key) {
        if (!defaults.containsKey(key)) {
            throw new ManagerException(Codes.CONFIG_KEY_UNKNOWN, "unknown config key: " + key);
        }
    }

    private Object coerce(String key, Object value) {
        requireKey(key);
        return switch (key) {
            case "isLog" -> toBoolean(value);
            case "workers", "detail" -> toInteger(key, value);
            case "export.dir" -> String.valueOf(value);
            default -> value;
        };
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1")) {
            return true;
        }
        if (s.equals("false") || s.equals("0")) {
            return false;
        }
        throw new ManagerException(Codes.CONFIG_INVALID_VALUE, "expected boolean, got: " + value);
    }

    private static int toInteger(String key, Object value) {
        try {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ManagerException(Codes.CONFIG_INVALID_VALUE, key + " expects an integer, got: " + value);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.stringify(fileValues), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManagerException(Codes.CONFIG_INVALID_VALUE, "cannot write config file: " + e.getMessage());
        }
    }
}
