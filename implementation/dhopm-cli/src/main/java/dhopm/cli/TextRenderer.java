package dhopm.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/** Human-readable rendering of a response for the text CLI (the JSON channel is the contract). */
final class TextRenderer {

    private TextRenderer() {
    }

    @SuppressWarnings("unchecked")
    static void render(PrintStream out, Map<String, Object> request, Map<String, Object> response) {
        String cmd = String.valueOf(request.get("cmd"));
        switch (cmd) {
            case "mine", "detail", "stream" -> mine(out, response, cmd.equals("detail"));
            case "window" -> window(out, response);
            case "validate" -> validate(out, response);
            case "inspect" -> inspect(out, response);
            case "golden" -> golden(out, response);
            case "sweep" -> sweep(out, response);
            case "info", "version" -> info(out, response);
            default -> out.println(CommandDispatcher.toJson(response));
        }
    }

    @SuppressWarnings("unchecked")
    private static void mine(PrintStream out, Map<String, Object> r, boolean detail) {
        Map<String, Object> cfg = (Map<String, Object>) r.get("config");
        out.println("mine | config=" + cfg);
        out.printf("transactions=%s lastTid=%s minSup=%s maxDO=%s%n",
                r.get("totalTransactions"), r.get("lastTid"), r.get("minSup"), r.get("maxDO"));
        printWindow(out, r.get("window"));
        printIssues(out, r.get("issues"));
        List<Map<String, Object>> jobs = (List<Map<String, Object>>) r.get("jobs");
        for (Map<String, Object> job : jobs) {
            out.printf("  %-4s %-14s total_ms=%-6s patterns=%s%n",
                    job.get("version"), job.get("engine"), job.get("total_ms"), job.get("patterns"));
        }
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) r.get("patterns");
        out.println("patterns (" + patterns.size() + "):");
        for (Map<String, Object> p : patterns) {
            out.println("  " + p.get("key") + " do=" + p.get("do") + " support=" + p.get("support"));
        }
        if (detail) {
            Map<String, Object> perVersion = (Map<String, Object>) r.get("perVersion");
            for (Map.Entry<String, Object> e : perVersion.entrySet()) {
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                out.println("  [" + e.getKey() + "] stats=" + v.get("stats"));
            }
        }
    }

    private static void window(PrintStream out, Map<String, Object> r) {
        out.println("window=" + r.get("window")
                + " maxDOAsymptotic=" + r.get("maxDOAsymptotic")
                + " maxPartialAsymptotic=" + r.get("maxPartialAsymptotic")
                + " maxDOExact=" + r.get("maxDOExact")
                + " maxPartialExact=" + r.get("maxPartialExact"));
    }

    @SuppressWarnings("unchecked")
    private static void validate(PrintStream out, Map<String, Object> r) {
        out.println("validate: valid=" + r.get("valid"));
        printIssues(out, r.get("issues"));
    }

    private static void inspect(PrintStream out, Map<String, Object> r) {
        out.println("inspect: dataset=" + r.get("dataset") + " transactions=" + r.get("transactions")
                + " lastTid=" + r.get("lastTid") + " maxLen=" + r.get("maxLen"));
        if (r.containsKey("window")) {
            printWindow(out, r.get("window"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void golden(PrintStream out, Map<String, Object> r) {
        out.println("golden: allPass=" + r.get("allPass"));
        for (Map<String, Object> c : (List<Map<String, Object>>) r.get("cases")) {
            out.println("  " + c.get("version") + " " + c.get("id") + " " + (Boolean.TRUE.equals(c.get("pass")) ? "PASS" : "FAIL"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void sweep(PrintStream out, Map<String, Object> r) {
        out.printf("%10s %6s %10s %10s %9s %9s %s%n", "partial", "f", "minOcc", "minSup", "patterns", "ms", "issue");
        for (Map<String, Object> row : (List<Map<String, Object>>) r.get("rows")) {
            out.printf("%10s %6s %10s %10s %9s %9s %s%n", row.get("partial"), row.get("f"), row.get("minOcc"),
                    row.get("minSup"), row.get("patterns"), row.get("total_ms"), row.get("issue"));
        }
    }

    private static void info(PrintStream out, Map<String, Object> r) {
        out.println("manager=" + r.get("manager") + " protocol=" + r.get("protocol"));
        out.println("versions=" + r.get("versions"));
        out.println("commands=" + r.get("commands"));
    }

    @SuppressWarnings("unchecked")
    private static void printWindow(PrintStream out, Object windowObj) {
        if (!(windowObj instanceof Map<?, ?> w)) {
            return;
        }
        Object size = w.get("size");
        String sizeText = size == null ? "INFINITE" : String.valueOf(size);
        out.println("window: W=" + sizeText + " N_eff=" + w.get("effective")
                + " minSup=" + w.get("minSup") + " Z=" + w.get("maxDO"));
    }

    @SuppressWarnings("unchecked")
    private static void printIssues(PrintStream out, Object issuesObj) {
        if (issuesObj instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> i = (Map<String, Object>) o;
                out.println("  " + i.get("severity") + " [" + i.get("code") + "] " + i.get("message"));
            }
        }
    }
}
