package org.jvnc.dhopm.v3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DhopmEngine {
    private final Map<Integer, Transaction> transactions = new LinkedHashMap<>();
    private final Map<String, List<Entry>> globalEntries = new LinkedHashMap<>();
    private final List<List<String>> pendingTransactions = new ArrayList<>();
    private List<Node> reconstructed = List.of();
    private boolean datasetLoaded;
    private boolean reconstructedReady;
    private double lastPartial;
    private double lastFactor;
    private double lastMinSup;
    private int totalTid;
    private int currentWindow = Integer.MAX_VALUE;
    private double constructionFactor = Double.NaN;
    private double constructionMinDo = Double.NaN;
    private double constructionEpsilon = Double.NaN;

    public int lastTid() {
        return totalTid;
    }

    public int transactionCount() {
        return totalTid;
    }

    public int itemCount() {
        return globalEntries.size();
    }

    public int pendingCount() {
        return pendingTransactions.size();
    }

    public void addPendingTransaction(String transaction) {
        String trimmed = transaction == null ? "" : transaction.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Transaction cannot be empty.");
        }
        List<String> items = List.of(trimmed.split("\\s+"));
        if (items.stream().distinct().count() != items.size()) {
            throw new IllegalArgumentException("A transaction cannot contain duplicate items.");
        }
        pendingTransactions.add(items);
        reconstructedReady = false;
    }

    public PhaseResult construct(PathInput input, double factor, double minDo, double epsilon) throws Exception {
        validateFactor(factor);
        validateMinDo(minDo);
        validateEpsilon(epsilon);
        if (datasetLoaded && (Double.compare(factor, constructionFactor) != 0
                || Double.compare(minDo, constructionMinDo) != 0
                || Double.compare(epsilon, constructionEpsilon) != 0)) {
            throw new IllegalStateException("Changing factor, minDO, or epsilon requires a new data epoch.");
        }
        long started = System.nanoTime();
        int addedFromDataset = 0;
        int addedFromQueue = 0;
        int evicted = 0;
        List<TransactionSnapshot> evictedDuringConstruction = new ArrayList<>();
        currentWindow = calculateWindow(factor, minDo, epsilon);
        if (!datasetLoaded) {
            List<List<String>> initial = DatasetReader.read(input.path(), input.limit());
            for (List<String> items : initial) {
                evicted += appendTransaction(items, evictedDuringConstruction);
                addedFromDataset++;
            }
            for (List<String> items : pendingTransactions) {
                evicted += appendTransaction(items, evictedDuringConstruction);
                addedFromQueue++;
            }
            pendingTransactions.clear();
            if (addedFromDataset == 0 && addedFromQueue == 0) {
                throw new IllegalStateException("The selected dataset contains no transactions.");
            }
            datasetLoaded = true;
            constructionFactor = factor;
            constructionMinDo = minDo;
            constructionEpsilon = epsilon;
        } else if (!pendingTransactions.isEmpty()) {
            for (List<String> items : pendingTransactions) {
                evicted += appendTransaction(items, evictedDuringConstruction);
                addedFromQueue++;
            }
            pendingTransactions.clear();
        } else {
            throw new IllegalStateException("No new dataset or queued transactions to construct.");
        }
        reconstructedReady = false;
        Snapshot snapshot = snapshot(evictedDuringConstruction);
        return new PhaseResult("CONSTRUCTION", System.nanoTime() - started,
                addedFromDataset, addedFromQueue, evicted, snapshot, List.of(), List.of(),
                new Parameters(lastPartial, factor, minDo, epsilon, input == null ? 0 : input.limit()), 0);
    }

    public PhaseResult reconstruct(double partial, double factor, double minDo, double epsilon) {
        if (!datasetLoaded) {
            throw new IllegalStateException("Run Construction before Reconstruction.");
        }
        validateUnitInterval(partial, "partial");
        validateFactor(factor);
        validateMinDo(minDo);
        validateEpsilon(epsilon);
        if (Double.compare(factor, constructionFactor) != 0
                || Double.compare(minDo, constructionMinDo) != 0
                || Double.compare(epsilon, constructionEpsilon) != 0) {
            throw new IllegalStateException(
                    "Changing factor, minDO, or epsilon requires Construction to start a new data epoch.");
        }
        long started = System.nanoTime();
        int tl = lastTid();
        double minSup = partial * transactions.size();
        List<Node> nodes = new ArrayList<>();
        List<DuboObservation> observations = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> item : globalEntries.entrySet()) {
            Node node = new Node(item.getKey(), 1, item.getValue());
            node.recompute(factor, tl);
            nodes.add(node);
        }
        nodes.sort(Comparator.comparingInt(Node::support).thenComparing(n -> n.item));
        reconstructed = List.copyOf(nodes);
        reconstructedReady = true;
        lastPartial = partial;
        lastFactor = factor;
        lastMinSup = minSup;
        Parameters parameters = new Parameters(partial, factor, minDo, epsilon, 0);
        return new PhaseResult("RECONSTRUCTION", System.nanoTime() - started, 0, 0, 0,
                snapshot(), observations, List.of(), parameters, minSup);
    }

    public PhaseResult mine() {
        if (!reconstructedReady) {
            throw new IllegalStateException("Run Reconstruction before Mining.");
        }
        long started = System.nanoTime();
        double minSup = lastMinSup;
        List<PatternResult> patterns = new ArrayList<>();
        List<DuboObservation> observations = new ArrayList<>();
        mineRecursive(reconstructed, 0, List.of(), minSup, lastFactor, lastTid(),
                patterns, observations);
        return new PhaseResult("MINING", System.nanoTime() - started, 0, 0, 0,
                snapshot(), observations, patterns, new Parameters(lastPartial, lastFactor, 0, 0, 0), minSup);
    }

    public Snapshot snapshot() {
        return snapshot(List.of());
    }

    private Snapshot snapshot(List<TransactionSnapshot> evictedDuringPhase) {
        List<TransactionSnapshot> snapshot = new ArrayList<>(transactions.values().stream()
                .map(t -> new TransactionSnapshot(t.tid(), t.items(), true))
                .toList());
        snapshot.addAll(evictedDuringPhase);
        snapshot.sort(Comparator.comparingInt(TransactionSnapshot::tid));
        return new Snapshot(lastTid(), lastTid(), transactions.size(),
                globalEntries.size(), snapshot);
    }

    private int appendTransaction(List<String> items, List<TransactionSnapshot> evictedDuringConstruction) {
        if (totalTid == Integer.MAX_VALUE) {
            throw new IllegalStateException("Maximum supported TID has been reached.");
        }
        int tid = ++totalTid;
        Transaction transaction = new Transaction(tid, items);
        transactions.put(tid, transaction);
        for (String item : items) {
            globalEntries.computeIfAbsent(item, ignored -> new ArrayList<>())
                    .add(new Entry(tid, items.size()));
        }
        int expiredTid = currentWindow == 0 ? tid : tid - currentWindow;
        Transaction expired = expiredTid > 0 ? removeTransaction(expiredTid) : null;
        if (expired == null) {
            return 0;
        }
        evictedDuringConstruction.add(
                new TransactionSnapshot(expired.tid(), expired.items(), false));
        return 1;
    }

    private Transaction removeTransaction(int tid) {
        Transaction expired = transactions.remove(tid);
        if (expired == null) {
            return null;
        }
        for (String item : expired.items()) {
            List<Entry> entries = globalEntries.get(item);
            if (entries != null) {
                entries.removeIf(entry -> entry.tid == tid);
                if (entries.isEmpty()) {
                    globalEntries.remove(item);
                }
            }
        }
        return expired;
    }

    private static int calculateWindow(double factor, double minDo, double epsilon) {
        if (minDo == 0.0) {
            return Integer.MAX_VALUE;
        }
        double threshold = minDo + epsilon;
        if (threshold >= 1.0) {
            return 0;
        }
        if (factor == 1.0) {
            return Integer.MAX_VALUE;
        }
        double estimate = Math.ceil(Math.log(threshold) / Math.log(factor));
        if (!Double.isFinite(estimate) || estimate > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        int window = Math.max(0, (int) estimate);
        while (window > 0 && Math.pow(factor, window - 1) <= threshold) {
            window--;
        }
        while (Math.pow(factor, window) > threshold) {
            if (window == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Calculated W exceeds the supported transaction range.");
            }
            window++;
        }
        return window;
    }

    private static void validateFactor(double factor) {
        if (!Double.isFinite(factor) || factor <= 0 || factor > 1) {
            throw new IllegalArgumentException("factor must be finite and in (0, 1].");
        }
    }

    private static void validateMinDo(double minDo) {
        if (!Double.isFinite(minDo) || minDo < 0) {
            throw new IllegalArgumentException("minDO must be finite and non-negative.");
        }
    }

    private static void validateEpsilon(double epsilon) {
        if (!Double.isFinite(epsilon) || epsilon < 0) {
            throw new IllegalArgumentException("epsilon must be finite and non-negative.");
        }
    }

    private void mineRecursive(List<Node> list, int start, List<String> prefix,
                               double minSup, double factor, int tl,
                               List<PatternResult> patterns, List<DuboObservation> observations) {
        for (int i = start; i < list.size(); i++) {
            Node node = list.get(i);
            if (node.support() < minSup) {
                continue;
            }
            List<String> items = new ArrayList<>(prefix);
            items.add(node.item);
            String key = canonicalKey(items);
            if (node.dampedOccupancy >= minSup) {
                patterns.add(new PatternResult(key, List.copyOf(items), node.support(),
                        node.dampedOccupancy));
            }
            double dubo = node.dubo(factor, tl);
            observations.add(new DuboObservation(key, 0.0, dubo, node.support(),
                    node.dampedOccupancy, minSup, dubo >= minSup));
            if (dubo < minSup) {
                continue;
            }
            List<Node> next = new ArrayList<>();
            for (int j = i + 1; j < list.size(); j++) {
                Node partner = list.get(j);
                if (partner.support() < minSup) {
                    continue;
                }
                Node combined = intersect(node, partner, factor, tl);
                if (combined.support() > 0) {
                    next.add(combined);
                }
            }
            if (!next.isEmpty()) {
                mineRecursive(next, 0, items, minSup, factor, tl, patterns, observations);
            }
        }
    }

    private static Node intersect(Node left, Node right, double factor, int tl) {
        List<Entry> entries = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.entries.size() && j < right.entries.size()) {
            Entry a = left.entries.get(i);
            Entry b = right.entries.get(j);
            if (a.tid < b.tid) {
                i++;
            } else if (a.tid > b.tid) {
                j++;
            } else {
                entries.add(a);
                i++;
                j++;
            }
        }
        Node combined = new Node(right.item, left.patternLength + 1, entries);
        combined.recompute(factor, tl);
        return combined;
    }

    private static String canonicalKey(List<String> items) {
        return items.stream().sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    private static void validateUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1].");
        }
    }

    private record Entry(int tid, int transactionLength) {
    }

    private static final class Node {
        private final String item;
        private final int patternLength;
        private final List<Entry> entries;
        private double dampedOccupancy;

        private Node(String item, int patternLength, List<Entry> entries) {
            this.item = item;
            this.patternLength = patternLength;
            this.entries = List.copyOf(entries);
        }

        private int support() {
            return entries.size();
        }

        private void recompute(double factor, int tl) {
            dampedOccupancy = 0.0;
            for (Entry entry : entries) {
                dampedOccupancy += ((double) patternLength / entry.transactionLength)
                        * Math.pow(factor, tl - entry.tid);
            }
        }

        private double dubo(double factor, int tl) {
            TreeMap<Integer, LengthGroup> groups = new TreeMap<>();
            for (Entry entry : entries) {
                LengthGroup group = groups.computeIfAbsent(entry.transactionLength,
                        ignored -> new LengthGroup());
                group.count++;
                group.latestTid = Math.max(group.latestTid, entry.tid);
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
                double value = Math.pow(factor, tl - byLength.get(k).getValue().latestTid) * sum;
                maximum = Math.max(maximum, value);
            }
            return maximum;
        }
    }

    private static final class LengthGroup {
        private int count;
        private int latestTid;
    }

    public record PathInput(java.nio.file.Path path, int limit) {
    }

    public record Parameters(double partial, double factor, double minDo, double epsilon, int limit) {
    }

    public record TransactionSnapshot(int tid, List<String> items, boolean active) {
    }

    public record Snapshot(int lastTid, int transactionCount, int activeTransactionCount, int itemCount,
                           List<TransactionSnapshot> transactions) {
    }

    public record PatternResult(String key, List<String> items, int support, double dampedOccupancy) {
    }

    public record DuboObservation(String patternKey, double before, double value, int support,
                                  double dampedOccupancy, double minSup, boolean passesMinSup) {
    }

    public record PhaseResult(String phase, long elapsedNanos, int datasetTransactionsAdded,
                              int queuedTransactionsAdded, int evictedTransactions,
                              Snapshot snapshot, List<DuboObservation> dubo,
                              List<PatternResult> patterns, Parameters parameters, double minSup) {
        public PhaseResult(String phase, long elapsedNanos, int datasetTransactionsAdded,
                           int queuedTransactionsAdded, int evictedTransactions,
                           Snapshot snapshot, List<DuboObservation> dubo,
                           List<PatternResult> patterns) {
            this(phase, elapsedNanos, datasetTransactionsAdded, queuedTransactionsAdded,
                    evictedTransactions, snapshot, dubo, patterns, null, 0);
        }
    }
}
