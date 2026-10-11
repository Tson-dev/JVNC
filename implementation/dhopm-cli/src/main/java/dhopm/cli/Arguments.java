package dhopm.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts CLI argv into the same request map the JSON transport produces (D47 "Hybrid"): positional
 * {@code <dataset> <partial> <factor> <limit>} plus {@code --flags}. The dispatcher then works on one
 * uniform map, so a command has a single implementation regardless of the channel.
 */
public final class Arguments {

    private Arguments() {
    }

    /** Flags that carry no value (presence implies true). */
    private static final Set<String> BOOLEAN_FLAGS = Set.of("--step", "--json", "--help", "-h");

    /** Flags whose value is optional (bare flag means "use default"). */
    private static final Set<String> OPTIONAL_VALUE_FLAGS = Set.of("--export");

    /** Canonical positional order per command. */
    private static Map<String, List<String>> positionalSchema() {
        Map<String, List<String>> schema = new LinkedHashMap<>();
        schema.put("mine", List.of("dataset", "partial", "f", "limit"));
        schema.put("detail", List.of("dataset", "partial", "f", "limit"));
        schema.put("stream", List.of("dataset", "partial", "f", "limit"));
        schema.put("window", List.of("f"));
        schema.put("validate", List.of("partial", "f"));
        schema.put("inspect", List.of("dataset"));
        schema.put("sweep", List.of("dataset"));
        schema.put("fetch", List.of("file"));
        schema.put("config", List.of("action", "key", "value"));
        return schema;
    }

    /** Parses {@code args} (including the command as {@code args[0]}) into a request map. */
    public static Map<String, Object> parse(String[] args) {
        if (args.length == 0) {
            throw ManagerException.missing("command");
        }
        String cmd = args[0];
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("cmd", cmd);
        List<String> positionals = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") || (a.startsWith("-") && a.length() == 2 && !a.equals("-1"))) {
                if (BOOLEAN_FLAGS.contains(a)) {
                    req.put(strip(a), Boolean.TRUE);
                    continue;
                }
                String key = strip(a);
                if (OPTIONAL_VALUE_FLAGS.contains(a)) {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        req.put(key, args[++i]);
                    } else {
                        req.put(key, Boolean.TRUE);
                    }
                    continue;
                }
                if (i + 1 >= args.length) {
                    throw ManagerException.missing("value for " + a);
                }
                req.put(key, args[++i]);
            } else {
                positionals.add(a);
            }
        }

        List<String> canonical = positionalSchema().getOrDefault(cmd, List.of());
        int pi = 0;
        for (String key : canonical) {
            if (pi >= positionals.size()) {
                break;
            }
            if (req.containsKey(key)) {
                continue;
            }
            req.put(key, positionals.get(pi++));
        }
        if (pi < positionals.size()) {
            throw ManagerException.invalid("too many positional arguments for '" + cmd + "': "
                    + positionals.subList(pi, positionals.size()));
        }
        return req;
    }

    private static String strip(String flag) {
        return flag.substring(2);
    }
}
