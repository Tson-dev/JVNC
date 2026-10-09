package dhopm.v3.construction;

import dhopm.common.config.MiningConfig;
import dhopm.v3.dho.DHOListOptimized;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.io.ItemDictionary;
import dhopm.v3.window.WindowBufferOptimized;
import dhopm.common.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Optimized DHO-List Builder (GĐ1 Construction).
 * Supports both sequential and parallel construction (optional, switchable).
 * Parallel: splits batch into chunks, each builds partial node arrays, then merges.
 */
public final class DHOListBuilderOptimized {

    private final MiningConfig config;
    private final WindowBufferOptimized window;
    private final ItemDictionary dictionary;
    private final DHOListOptimized dhoList;
    private int lastTid = 0;

    public DHOListBuilderOptimized(MiningConfig config, WindowBufferOptimized window, ItemDictionary dictionary) {
        this.config = config;
        this.window = window;
        this.dictionary = dictionary;
        this.dhoList = new DHOListOptimized();
    }

    /** Loads a batch of transactions (sequential - default). */
    public void loadBatch(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            loadTransaction(tx);
        }
    }

    /** Loads a batch in parallel (optional, for Level 2). */
    public void loadBatchParallel(List<Transaction> transactions, ForkJoinPool pool) {
        if (transactions.size() < 1000) {
            loadBatch(transactions);
            return;
        }
        int workers = config.workers();
        int chunkSize = (transactions.size() + workers - 1) / workers;
        List<PartialBuilder> tasks = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, transactions.size());
            tasks.add(new PartialBuilder(transactions.subList(i, end), dictionary, config, window));
        }
        pool.invokeAll(tasks);
        // Merge partial results
        for (PartialBuilder pb : tasks) {
            mergePartial(pb);
        }
    }

    /** Loads a single transaction (sequential). */
    private void loadTransaction(Transaction tx) {
        int tid = tx.tid();
        lastTid = tid;

        // Evict before write (GĐ0)
        window.evictBeforeWrite(tid);

        // Write to window buffer, get slot
        int slot = window.write(tid, tx.items().length);

        // Add entry to each item's node
        for (int itemId : tx.items()) {
            DHONodeOptimized node = dhoList.getOrCreateNode(itemId);
            node.addEntry(slot, tx.items().length);
        }
    }

    /** Merges a partial builder's results into the main DHO list. */
    private void mergePartial(PartialBuilder pb) {
        for (int itemId = 0; itemId < pb.partialNodes.length; itemId++) {
            DHONodeOptimized partialNode = pb.partialNodes[itemId];
            if (partialNode != null && partialNode.size > 0) {
                DHONodeOptimized mainNode = dhoList.getOrCreateNode(itemId);
                for (int i = 0; i < partialNode.size; i++) {
                    mainNode.addEntry(partialNode.txSlot[i], partialNode.ref2[i]);
                }
            }
        }
    }

    public DHOListOptimized getDhoList() {
        return dhoList;
    }

    public int getLastTid() {
        return lastTid;
    }

    public ItemDictionary getDictionary() {
        return dictionary;
    }

    /** Partial builder for parallel construction - builds node arrays for a chunk. */
    private static final class PartialBuilder extends RecursiveAction {
        private final List<Transaction> chunk;
        private final ItemDictionary dictionary;
        private final MiningConfig config;
        private final WindowBufferOptimized window;
        public DHONodeOptimized[] partialNodes = new DHONodeOptimized[64];

        PartialBuilder(List<Transaction> chunk, ItemDictionary dictionary, MiningConfig config, WindowBufferOptimized window) {
            this.chunk = chunk;
            this.dictionary = dictionary;
            this.config = config;
            this.window = window;
        }

        @Override
        protected void compute() {
            for (Transaction tx : chunk) {
                int tid = tx.tid();
                int slot = tid % window.windowSize;

                for (int itemId : tx.items()) {
                    if (itemId >= partialNodes.length) {
                        int newLen = Math.max(partialNodes.length * 2, itemId + 1);
                        partialNodes = java.util.Arrays.copyOf(partialNodes, newLen);
                    }
                    DHONodeOptimized node = partialNodes[itemId];
                    if (node == null) {
                        node = new DHONodeOptimized(itemId);
                        partialNodes[itemId] = node;
                    }
                    node.addEntry(slot, tx.items().length);
                }
            }
        }
    }
}