package dhopm.v2.cli;

import java.util.Locale;

/**
 * V2 CLI driver (G2-M6). Same dispatch style as V1 plus the window command set.
 *
 * <pre>
 *   dhopm.v2.cli.Main &lt;command&gt; [options]
 *
 *   mine     ...  detail  ...  stream  ...  golden  ...  inspect  ...
 *   window   damped-window lookup without a dataset (asymptotic dp_max + suffix)
 *   validate config checks with stable codes (MIN_OCC_TOO_LARGE / WINDOW_TOO_LARGE /
 *            PARTIAL_ZERO / INFEASIBLE_PARTIAL)
 *   sweep    scan combinations of (∂, f, minOcc)
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
            System.err.println("run \"dhopm.v2.cli.Main help\" for usage.");
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
            case "detail" -> new DetailCommand().run(args, 1);
            case "stream" -> new StreamCommand().run(args, 1);
            case "window" -> new WindowCommand().run(args, 1);
            case "validate" -> new ValidateCommand().run(args, 1);
            case "sweep" -> new SweepCommand().run(args, 1);
            case "golden" -> new GoldenCommand().run(args, 1);
            case "inspect" -> new InspectCommand().run(args, 1);
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