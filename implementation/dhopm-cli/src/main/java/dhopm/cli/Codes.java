package dhopm.cli;

/** Stable error/warning codes of the FE protocol contract (plans/08-FE-CONTRACT.md section 10). */
public final class Codes {

    private Codes() {
    }

    // request errors
    public static final String UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String UNKNOWN_VERSION = "UNKNOWN_VERSION";
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";
    public static final String MIN_OCC_TOO_LARGE = "MIN_OCC_TOO_LARGE";
    public static final String WINDOW_TOO_LARGE = "WINDOW_TOO_LARGE";
    public static final String MISMATCHED_WINDOW_CONFIG = "MISMATCHED_WINDOW_CONFIG";
    public static final String DATASET_NOT_FOUND = "DATASET_NOT_FOUND";
    public static final String UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT";
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String EXPORT_FAILED = "EXPORT_FAILED";
    public static final String CONFIG_KEY_UNKNOWN = "CONFIG_KEY_UNKNOWN";
    public static final String CONFIG_INVALID_VALUE = "CONFIG_INVALID_VALUE";
    public static final String NOT_IMPLEMENTED = "NOT_IMPLEMENTED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // client-side (documented for FE; the BE never emits these)
    public static final String BE_NOT_FOUND = "BE_NOT_FOUND";
    public static final String PROTOCOL_MISMATCH = "PROTOCOL_MISMATCH";
    public static final String CONNECT_REFUSED = "CONNECT_REFUSED";

    // warnings
    public static final String PARTIAL_ZERO = "PARTIAL_ZERO";
    public static final String INFEASIBLE_PARTIAL = "INFEASIBLE_PARTIAL";
}
