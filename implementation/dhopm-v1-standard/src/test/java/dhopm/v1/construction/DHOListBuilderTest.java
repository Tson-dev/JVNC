package dhopm.v1.construction;

import dhopm.common.testkit.GoldenCases;
import dhopm.common.transaction.Transaction;
import dhopm.v1.engine.MiningEngine;
import dhopm.v1.model.DHOList;
import dhopm.v1.model.DHONode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DHOListBuilderTest {

    @Test
    void buildsGlobalListInCreationOrder() {
        DHOList global = new DHOList();
        for (Transaction t : GoldenCases.baseStream()) {
            DHOListBuilder.add(t, global);
        }
        assertEquals(List.of("A", "C", "D", "E", "F", "B", "G"),
                global.nodes().stream().map(DHONode::item).toList(), "creation order = first occurrence");

        DHONode a = global.get("A");
        assertEquals(4, a.support());
        assertEquals(List.of(1, 2, 7, 8), a.entries().stream().map(e -> e.tid()).toList(), "entries in TID order (INV-B)");
        assertEquals(List.of(4, 3, 4, 3), a.entries().stream().map(e -> e.len()).toList(), "|T| kept per entry");
    }

    @Test
    void engineRejectsNonIncreasingTid() {
        try (MiningEngine engine = MiningEngine.defaults(0.15, 0.9)) {
            assertThrows(IllegalArgumentException.class,
                    () -> engine.loadBatch(List.of(
                            new Transaction(2, new String[]{"A"}),
                            new Transaction(1, new String[]{"B"}))),
                    "INV-A: strictly increasing TIDs, 2 then 1 must fail");
        }
    }

    @Test
    void engineRejectsDuplicateTid() {
        try (MiningEngine engine = MiningEngine.defaults(0.15, 0.9)) {
            assertThrows(IllegalArgumentException.class,
                    () -> engine.loadBatch(List.of(
                            new Transaction(1, new String[]{"A"}),
                            new Transaction(1, new String[]{"B"}))),
                    "INV-A: duplicate TID 1 must fail");
        }
    }

    @Test
    void emptyStreamProducesEmptyResult() {
        try (MiningEngine engine = MiningEngine.defaults(0.15, 0.9)) {
            assertEquals(0, engine.mineNow().patterns().size());
            assertTrue(engine.mineNow().patterns().isEmpty());
        }
    }
}