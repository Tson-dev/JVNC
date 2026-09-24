package dhopm.v1.cli;

import java.util.Locale;

/**
 * V1 CLI driver — dispatcher cho bộ lệnh chuẩn (G1-D7, plan tổng thể 4.2):
 *
 * <pre>
 *   dhopm.v1.cli.Main &lt;command&gt; [options]
 *
 *   mine     tóm tắt ngắn (1 dòng/part + tổng)            default khi gọi dạng cũ --dataset …
 *   detail   chi tiết từng pha + top-N mẫu theo DO
 *   stream   log thời gian thực (load + tick mining)
 *   golden   TestKit TC1-TC8, in PASS/FAIL
 *   inspect  thống kê dataset không mining
 *
 *   option: --dataset &lt;fimi|text&gt; --format fimi|text --partial ∂ --f f --workers n --parts n --top n
 * </pre>
 *
 * Cú pháp cũ {@code --dataset …} vẫn chạy (= lệnh {@code mine}).
 */
public final class Main {

    public static void main(String[] args) {
        System.out.printf(Locale.ROOT, "JVNC | %s | %s%n", Runtime.version(), System.getProperty("os.name"));
        int code;
        try {
            code = dispatch(args);
        } catch (CliSupport.CliException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println("run \"dhopm.v1.cli.Main help\" for usage.");
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
            case "golden" -> new GoldenCommand().run(args, 1);
            case "inspect" -> new InspectCommand().run(args, 1);
            default -> {
                if (first.startsWith("--")) { // cú pháp cũ → mine
                    yield new MineCommand().run(args, 0);
                }
                System.out.println("unknown command: " + first);
                System.out.println(CliSupport.usages());
                yield 2;
            }
        };
    }
}