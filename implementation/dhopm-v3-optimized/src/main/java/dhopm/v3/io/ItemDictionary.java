package dhopm.v3.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bidirectional dictionary for item String ↔ int ID mapping.
 * Used by IO layer only; computation layer uses int IDs exclusively.
 * Thread-safe for concurrent reads (construction parallel).
 */
public final class ItemDictionary {

    private final ConcurrentHashMap<String, Integer> nameToId = new ConcurrentHashMap<>();
    private final List<String> idToName = new ArrayList<>();

    /**
     * Gets the ID for an item name, assigning a new ID if not present.
     * IDs are assigned sequentially starting from 0.
     */
    public int getOrAssignId(String name) {
        return nameToId.computeIfAbsent(name, n -> {
            int id = idToName.size();
            idToName.add(n);
            return id;
        });
    }

    /** Gets the ID for an item name, or -1 if not present. */
    public int getId(String name) {
        Integer id = nameToId.get(name);
        return id == null ? -1 : id;
    }

    /** Gets the name for an item ID, or null if out of bounds. */
    public String getName(int id) {
        return (id >= 0 && id < idToName.size()) ? idToName.get(id) : null;
    }

    /** Returns the number of distinct items. */
    public int size() {
        return idToName.size();
    }

    /** Returns all item names in ID order (for output/debug). */
    public List<String> allNames() {
        return List.copyOf(idToName);
    }

    /** Clears the dictionary (for reuse across datasets). */
    public void clear() {
        nameToId.clear();
        idToName.clear();
    }
}