package dhopm;

import dhopm.algo.DampedUpperBound;
import dhopm.algo.DhopmMiner;
import dhopm.config.MiningParameters;
import dhopm.io.TransactionReader;
import dhopm.model.Pattern;
import dhopm.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Command-line entry point.
 * <p>
 * Usage: {@code java dhopm.Main [file] [-f <decay factor>] [-d <threshold>]}
 * <ul>
 *   <li>{@code file} - optional path to a text file of transactions; when
 *       omitted the transactions are read from standard input.</li>
 *   <li>{@code -f} - decaying factor in (0,1], default 0.9.</li>
 *   <li>{@code -d} - min-support ratio in (0,1], default 0.15.</li>
 * </ul>
 * Each line holds one transaction: space-separated items, e.g. {@code A B C D}.
 * The program prints the set of DOPs (Damped High Occupancy Patterns).
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        Cli cli = Cli.parse(args);

        String input = cli.file == null
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                : Files.readString(Path.of(cli.file), StandardCharsets.UTF_8);

        MiningParameters params = MiningParameters.builder()
                .f(cli.f)
                .delta(cli.delta)
                .build();

        List<Transaction> transactions = TransactionReader.read(input);

        DhopmMiner miner = new DhopmMiner(params, new DampedUpperBound());
        for (Transaction t : transactions) {
            miner.consume(t);
        }

        List<Pattern> dops = miner.mine();
        dops.sort(Comparator.comparing(Pattern::displayName));

        System.out.printf("f = %.3f, delta = %.3f, |DB| = %d%n",
                params.f(), params.delta(), transactions.size());
        System.out.println("DOPs (" + dops.size() + "):");
        for (Pattern p : dops) {
            System.out.printf("  %s: %.4f%n", p.displayName(), p.doValue());
        }
    }

    private static final class Cli {
        double f = 0.9;
        double delta = 0.15;
        String file;

        static Cli parse(String[] args) {
            Cli cli = new Cli();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-f", "--f" -> cli.f = Double.parseDouble(args[++i]);
                    case "-d", "--delta" -> cli.delta = Double.parseDouble(args[++i]);
                    default -> {
                        if (args[i].startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + args[i]);
                        }
                        cli.file = args[i];
                    }
                }
            }
            return cli;
        }
    }
}