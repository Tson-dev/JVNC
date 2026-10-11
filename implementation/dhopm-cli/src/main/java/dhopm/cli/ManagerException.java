package dhopm.cli;

/**
 * A request-level failure carrying a stable protocol {@link Codes code}. The dispatcher turns it into
 * an error envelope ({@code ok:false, code, message}) — never a Java stack trace on the wire.
 */
public final class ManagerException extends RuntimeException {

    private final String code;

    public ManagerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ManagerException missing(String what) {
        return new ManagerException(Codes.MISSING_PARAMETER, "missing required parameter: " + what);
    }

    public static ManagerException invalid(String message) {
        return new ManagerException(Codes.INVALID_PARAMETER, message);
    }
}
