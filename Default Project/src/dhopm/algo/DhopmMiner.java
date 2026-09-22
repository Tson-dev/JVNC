package dhopm.algo;

import dhopm.config.MiningParameters;
import dhopm.model.DhoList;
import dhopm.model.DhoNode;
import dhopm.model.Pattern;
import dhopm.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Facade of the DHOPM algorithm (Damped High Occupancy Pattern Mining).
 * <p>
 * Data are fed incrementally with {@link #consume(Transaction)} (single scan,
 * no re-scan of old data). Each call to {@link #mine()} runs the three phases:
 * (1) construction / update of the global DHO-List, (2) reconstruction (DO
 * recomputation + support sort), (3) DFS-based pattern expansion that prunes
 * unpromising subtrees with the {@link UpperBoundCalculator} strategy.
 */
public final class DhopmMiner {

    private final MiningParameters params;
    private final UpperBoundCalculator upperBound;
    private final DhoList global = new DhoList();
    private int latestTid = 0;

    public DhopmMiner(MiningParameters params, UpperBoundCalculator upperBound) {
        this.params = Objects.requireNonNull(params, "params");
        this.upperBound = Objects.requireNonNull(upperBound, "upperBound");
    }

    /** Feeds one transaction into the global DHO-List (construction phase). */
    public void consume(Transaction transaction) {
        latestTid = transaction.tid();
        for (String item : transaction.items()) {
            global.addEntry(transaction.tid(), item, transaction.length());
        }
    }

    /** Reconstructs the global DHO-List and mines all damped high occupancy patterns. */
    public List<Pattern> mine() {
        global.reconstruct(params.f(), latestTid);
        double minSup = params.minSup(latestTid);
        List<Pattern> results = new ArrayList<>();
        dfs(global, List.of(), minSup, results);
        return results;
    }

    /**
     * DFS pattern growth over a (global or conditional) DHO-List. Every node is
     * checked for validity (DO >= minSup) and for expandability (DUBO >= minSup);
     * nodes with support < minSup are pruned without further inspection.
     */
    private void dfs(DhoList cl, List<String> prefix, double minSup, List<Pattern> output) {
        for (int i = 0; i < cl.size(); i++) {
            DhoNode left = cl.node(i);
            if (left.support() < minSup) {
                continue;
            }
            List<String> candidate = append(prefix, left.name());
            if (left.doValue() >= minSup) {
                output.add(new Pattern(candidate, left.doValue()));
            }
            if (upperBound.upperBound(left, params.f(), latestTid) < minSup) {
                continue;
            }

            DhoList conditional = new DhoList();
            for (int j = i + 1; j < cl.size(); j++) {
                DhoNode merged = left.mergeWith(cl.node(j), prefix.size() + 2, params.f(), latestTid);
                if (merged != null) {
                    conditional.add(merged);
                }
            }
            if (!conditional.isEmpty()) {
                dfs(conditional, candidate, minSup, output);
            }
        }
    }

    private static List<String> append(List<String> prefix, String item) {
        List<String> result = new ArrayList<>(prefix.size() + 1);
        result.addAll(prefix);
        result.add(item);
        return result;
    }
}