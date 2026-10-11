package dhopm.v3.construction;

import dhopm.v3.dho.DHOListOptimized;
import dhopm.v3.dho.DHONodeOptimized;
import dhopm.v3.io.ItemDictionary;
import dhopm.common.transaction.Transaction;

/**
 * GĐ1: append the {@code (tid, |T|)} entry of every item of a transaction to its node.
 * The window write/evict orchestration lives in the engine; this builder is a thin,
 * allocation-light adapter over {@link DHOListOptimized} + {@link ItemDictionary}.
 */
public final class DHOListBuilderOptimized {

    private final ItemDictionary dictionary;
    private final DHOListOptimized dhoList;

    public DHOListBuilderOptimized(ItemDictionary dictionary) {
        this.dictionary = dictionary;
        this.dhoList = new DHOListOptimized();
    }

    /** Adds entries for every item of a written transaction (both window and paper mode). */
    public void add(Transaction t) {
        int len = t.length();
        for (String itemName : t.items()) {
            int itemId = dictionary.getOrAssignId(itemName);
            DHONodeOptimized node = dhoList.getOrCreateNode(itemId);
            node.addEntry(t.tid(), len);
        }
    }

    public DHOListOptimized getDhoList() {
        return dhoList;
    }

    public ItemDictionary getDictionary() {
        return dictionary;
    }
}