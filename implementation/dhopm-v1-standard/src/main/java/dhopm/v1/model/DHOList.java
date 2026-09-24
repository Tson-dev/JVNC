package dhopm.v1.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The global DHO-List: nodes keyed by item name, insertion order = node creation order
 * (first occurrence of the item). Used by the construction phase (createOrGet + append)
 * and by the reconstructor (snapshot in creation order → stable sort by support — C3).
 */
public final class DHOList {

    private final Map<String, DHONode> byItem = new LinkedHashMap<>();

    public DHONode createOrGet(String item) {
        return byItem.computeIfAbsent(item, DHONode::new);
    }

    public DHONode get(String item) {
        return byItem.get(item);
    }

    public boolean isEmpty() {
        return byItem.isEmpty();
    }

    public int size() {
        return byItem.size();
    }

    /** Snapshot of all nodes in creation order. */
    public List<DHONode> nodes() {
        return new ArrayList<>(byItem.values());
    }
}