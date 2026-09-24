package dhopm.v1.reconstruction;

import dhopm.common.testkit.GoldenCases;
import dhopm.common.transaction.Transaction;
import dhopm.common.util.WorkerPool;
import dhopm.v1.construction.DHOListBuilder;
import dhopm.v1.model.DHOList;
import dhopm.v1.model.DHONode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconstructionTest {

    private static final double F = 0.9;
    private static final int TL = 8;

    private DHOList buildGlobal() {
        DHOList global = new DHOList();
        for (Transaction t : GoldenCases.baseStream()) {
            DHOListBuilder.add(t, global);
        }
        return global;
    }

    @Test
    void recomputesDoPerNode() {
        try (WorkerPool pool = new WorkerPool(1)) {
            List<DHONode> sorted = Reconstructor.run(buildGlobal(), F, TL, pool);
            DHONode a = sorted.stream().filter(n -> n.item().equals("A")).findFirst().orElseThrow();
            DHONode f = sorted.stream().filter(n -> n.item().equals("F")).findFirst().orElseThrow();
            assertEquals(0.8551, a.doValue(), 1e-4, "DO(A) from hand-run Phần 11");
            assertEquals(1.2553, f.doValue(), 1e-4, "DO(F) from hand-run Phần 11");
        }
    }

    @Test
    void stableSortKeepsCreationOrderForEqualSupport() {
        try (WorkerPool pool = new WorkerPool(1)) {
            List<DHONode> sorted = Reconstructor.run(buildGlobal(), F, TL, pool);
            List<String> items = sorted.stream().map(DHONode::item).toList();
            // supports 1..5 ascending; A and C both have 4 → A (created first) must precede C (C3)
            assertEquals(List.of("G", "B", "A", "C", "D", "E", "F"), items,
                    "stable sort by support, ties keep creation order");
        }
    }

    @Test
    void reconstructionIsReproducibleOnRepeatedMine() {
        try (WorkerPool pool = new WorkerPool(2)) {
            DHOList global = buildGlobal();
            List<DHONode> r1 = Reconstructor.run(global, F, TL, pool);
            List<DHONode> r2 = Reconstructor.run(global, F, TL, pool);
            for (int i = 0; i < r1.size(); i++) {
                assertEquals(r1.get(i).item(), r2.get(i).item());
                assertEquals(r1.get(i).doValue(), r2.get(i).doValue());
            }
        }
    }

    @Test
    void doValuesFitGoldenTableAtFourDecimals() {
        try (WorkerPool pool = new WorkerPool(1)) {
            List<DHONode> sorted = Reconstructor.run(buildGlobal(), F, TL, pool);
            // every global length-1 DO must be within 5e-5 of a 4-decimal value
            for (DHONode n : sorted) {
                double rounded = Math.round(n.doValue() * 1e4) / 1e4;
                assertTrue(Math.abs(n.doValue() - rounded) < 5e-5,
                        n.item() + " DO " + n.doValue() + " not representable at 4 decimals");
            }
        }
    }
}