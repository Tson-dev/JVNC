package org.jvnc.dhopm.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class DuboSupplementer {
    private DuboSupplementer() {
    }

    static List<PrunedPattern> findPrunedDescendants(LogDocument.PhaseEvent event) {
        if (!"MINING".equals(event.phase()) || !Double.isFinite(event.minSup())) {
            return List.of();
        }

        Map<String, Integer> supportByItem = new TreeMap<>();
        for (JsonElement element : array(event.snapshot(), "transactions")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject transaction = element.getAsJsonObject();
            if (!active(transaction)) {
                continue;
            }
            for (JsonElement item : array(transaction, "items")) {
                supportByItem.merge(item.getAsString(), 1, Integer::sum);
            }
        }
        List<String> rootOrder = supportByItem.entrySet().stream()
                .filter(entry -> entry.getValue() >= event.minSup())
                .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
        Map<String, Integer> rankByItem = new TreeMap<>();
        for (int i = 0; i < rootOrder.size(); i++) {
            rankByItem.put(rootOrder.get(i), i);
        }

        Set<String> computed = new HashSet<>();
        Set<String> finalPatterns = new HashSet<>();
        for (JsonElement element : event.duboCompared()) {
            if (element.isJsonObject()) {
                computed.add(text(element.getAsJsonObject(), "patternKey"));
            }
        }
        for (JsonElement element : event.patterns()) {
            if (element.isJsonObject()) {
                finalPatterns.add(text(element.getAsJsonObject(), "key"));
            }
        }

        Set<String> pruned = new LinkedHashSet<>();
        for (JsonElement element : event.duboCompared()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject observation = element.getAsJsonObject();
            double value = number(observation, "value");
            double minSup = number(observation, "minSup");
            if (!Double.isFinite(value)) {
                continue;
            }
            if (!Double.isFinite(minSup)) {
                minSup = event.minSup();
            }
            boolean passes = observation.has("passesMinSup")
                    ? observation.get("passesMinSup").getAsBoolean()
                    : value >= minSup;
            if (passes) {
                continue;
            }

            List<Integer> ranks = new ArrayList<>();
            boolean validPrefix = true;
            for (String item : text(observation, "patternKey").split(",")) {
                Integer rank = rankByItem.get(item);
                if (rank == null) {
                    validPrefix = false;
                    break;
                }
                ranks.add(rank);
            }
            if (!validPrefix || ranks.isEmpty()) {
                continue;
            }
            ranks.sort(Integer::compareTo);
            int lastRank = ranks.getLast();
            if (ranks.stream().distinct().count() != ranks.size()) {
                continue;
            }
            List<String> prefix = ranks.stream().map(rootOrder::get).toList();
            addExtensions(prefix, lastRank + 1, rootOrder, pruned);
        }

        return pruned.stream()
                .filter(key -> !computed.contains(key) && !finalPatterns.contains(key))
                .map(key -> new PrunedPattern(key, "recompute"))
                .toList();
    }

    private static void addExtensions(List<String> prefix, int start,
                                      List<String> rootOrder, Set<String> results) {
        for (int i = start; i < rootOrder.size(); i++) {
            List<String> extension = new ArrayList<>(prefix);
            extension.add(rootOrder.get(i));
            results.add(extension.stream().sorted().reduce((a, b) -> a + "," + b).orElse(""));
            addExtensions(extension, i + 1, rootOrder, results);
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

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
    }

    record PrunedPattern(String patternKey, String source) {
    }
}
