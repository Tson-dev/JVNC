package dhopm.v3.cli;

import java.util.Locale;

/**
 * V3 CLI driver — minimal standalone entry point so V3 can be run by hand like V1/V2.
 *
 * <pre>
 *   dhopm.v3.cli.Main &lt;command&gt; [options]
 *
 *   mine      ...  inspect  ...  window  ...  validate  ...
 * </pre>
 */
public final class Main {

    public static void main(String[] args) {
        System.out.printf(Locale.ROOT, "JVNC | %s | %s%n", Runtime.version(), System.getProperty("os.name"));
        int code;
        try {
            code = dispatch(args);
        } catch (CliSupport.CliException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println("run \"dhopm.v3.cli.Main help\" for usage.");
            code = 2;
        } catch (Exception e) {
            System.err.println("unexpected error: " + e);
            e.printStackTrace(System.err);
            code = 3;
        }
        System.exit(code);
    }

    /** Package-private for tests (command dispatch returns the exit code, no System.exit). */
    static int dispatch(String[] args) throws CliSupport.CliException {
        if (args.length == 0) {
            System.out.println("missing command.");
            System.out.println(CliSupport.usages());
            return 2;
        }
        String first = args[0];
        return switch (first) {
            case "help", "-h", "--help" -> {
                System.out.println(CliSupport.usages());
                yield 0;
            }
            case "mine" -> new MineCommand().run(args, 1);
            case "inspect" -> new InspectCommand().run(args, 1);
            case "window" -> new WindowCommand().run(args, 1);
            case "validate" -> new ValidateCommand().run(args, 1);
            default -> {
                if (first.startsWith("--")) { // legacy syntax → mine
                    yield new MineCommand().run(args, 0);
                }
                System.out.println("unknown command: " + first);
                System.out.println(CliSupport.usages());
                yield 2;
            }
        };
    }
}
