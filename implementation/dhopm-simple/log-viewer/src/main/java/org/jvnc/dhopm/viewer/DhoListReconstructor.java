package org.jvnc.dhopm.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class DhoListReconstructor {
    private DhoListReconstructor() {
    }

    static List<Node> reconstruct(LogDocument.PhaseEvent event) {
        double factor = number(event.parameters(), "factor");
        int tl = integer(event.snapshot(), "lastTid");
        if (!Double.isFinite(factor) || factor <= 0.0 || factor > 1.0 || tl < 0) {
            throw new IllegalArgumentException("The phase snapshot lacks valid factor/TL metadata.");
        }

        JsonArray transactionData = array(event.snapshot(), "transactions");
        Map<String, List<Entry>> entriesByItem = new TreeMap<>();
        int activeTransactions = 0;
        for (JsonElement element : transactionData) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Snapshot transaction must be a JSON object.");
            }
            JsonObject transaction = element.getAsJsonObject();
            if (!active(transaction)) {
                continue;
            }
            activeTransactions++;
            int tid = integer(transaction, "tid");
            JsonArray items = array(transaction, "items");
            if (tid <= 0 || items.isEmpty()) {
                throw new IllegalArgumentException("Snapshot contains an invalid TID or empty transaction.");
            }
            int transactionLength = items.size();
            double decay = Math.pow(factor, tl - tid);
            for (JsonElement item : items) {
                String name = item.getAsString();
                double contribution = decay / transactionLength;
                entriesByItem.computeIfAbsent(name, ignored -> new ArrayList<>())
                        .add(new Entry(tid, transactionLength, contribution));
            }
        }
        validateCount(event.snapshot(), "activeTransactionCount", activeTransactions);

        Map<String, Double> loggedDubo = new HashMap<>();
        for (JsonElement element : event.duboCompared()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject observation = element.getAsJsonObject();
            String patternKey = text(observation, "patternKey");
            double value = number(observation, "value");
            if (patternKey != null && patternKey.indexOf(',') < 0 && Double.isFinite(value)) {
                loggedDubo.put(patternKey, value);
            }
        }

        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> item : entriesByItem.entrySet()) {
            List<Entry> entries = item.getValue();
            entries.sort(Comparator.comparingInt(Entry::tid));
            double dampedOccupancy = entries.stream().mapToDouble(Entry::contribution).sum();
            Double engineDubo = loggedDubo.get(item.getKey());
            nodes.add(new Node(item.getKey(), 0, entries.size(), dampedOccupancy,
                    engineDubo != null ? engineDubo : calculateDubo(entries, factor, tl),
                    engineDubo == null ? "supplemental/reconstructed" : "engine log",
                    List.copyOf(entries)));
        }
        nodes.sort(Comparator.comparingInt(Node::support).thenComparing(Node::item));
        List<Node> ranked = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            ranked.add(new Node(node.item(), i + 1, node.support(), node.dampedOccupancy(),
                    node.dubo(), node.duboSource(), node.entries()));
        }
        return List.copyOf(ranked);
    }

    private static double calculateDubo(List<Entry> entries, double factor, int tl) {
        TreeMap<Integer, LengthGroup> groups = new TreeMap<>();
        for (Entry entry : entries) {
            LengthGroup group = groups.computeIfAbsent(entry.transactionLength(),
                    ignored -> new LengthGroup());
            group.count++;
            group.latestTid = Math.max(group.latestTid, entry.tid());
        }
        List<Map.Entry<Integer, LengthGroup>> byLength = new ArrayList<>(groups.entrySet());
        double maximum = 0.0;
        for (int k = 0; k < byLength.size(); k++) {
            int length = byLength.get(k).getKey();
            double sum = 0.0;
            for (int i = k; i < byLength.size(); i++) {
                sum += byLength.get(i).getValue().count
                        * ((double) length / byLength.get(i).getKey());
            }
            double bound = Math.pow(factor, tl - byLength.get(k).getValue().latestTid) * sum;
            maximum = Math.max(maximum, bound);
        }
        return maximum;
    }

    private static void validateCount(JsonObject snapshot, String field, int actual) {
        if (!snapshot.has(field) || snapshot.get(field).isJsonNull()) {
            return;
        }
        int expected = snapshot.get(field).getAsInt();
        if (expected != actual) {
            throw new IllegalArgumentException("Snapshot " + field + " is " + expected
                    + ", but transaction data contains " + actual + ".");
        }
    }

    private static boolean active(JsonObject transaction) {
        JsonElement value = transaction.get("active");
        return value == null || value.isJsonNull() || value.getAsBoolean();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? -1 : value.getAsInt();
    }

    private static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    record Node(String item, int rank, int support, double dampedOccupancy,
                double dubo, String duboSource, List<Entry> entries) {
    }

    record Entry(int tid, int transactionLength, double contribution) {
    }

    private static final class LengthGroup {
        private int count;
        private int latestTid;
    }
}
