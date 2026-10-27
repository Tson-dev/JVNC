package org.jvnc.dhopm.viewer;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DhoListReconstructorTest {
    @Test
    void reconstructsActiveDhoListNodesAndSeparatesEngineDuboFromDerivedDubo() {
        LogDocument.PhaseEvent event = new LogDocument.PhaseEvent("MINING", "", "sample.dat", 1.0,
                0, 0, 0, 0, null, "unavailable",
                JsonParser.parseString("{\"partial\":0.25,\"factor\":0.5}").getAsJsonObject(),
                JsonParser.parseString("""
                        {"lastTid":4,"transactionCount":4,"activeTransactionCount":2,"itemCount":2,
                         "transactions":[
                           {"tid":1,"items":["A","B"],"active":false},
                           {"tid":3,"items":["A"],"active":true},
                           {"tid":4,"items":["B"],"active":true}
                         ]}
                        """).getAsJsonObject(),
                JsonParser.parseString("""
                        [{"patternKey":"B","value":1.0,"support":1}]
                        """).getAsJsonArray());

        List<DhoListReconstructor.Node> nodes = DhoListReconstructor.reconstruct(event);

        assertEquals(List.of("A", "B"), nodes.stream().map(DhoListReconstructor.Node::item).toList());
        assertEquals(1, nodes.getFirst().rank());
        assertEquals(1, nodes.getFirst().support());
        assertEquals(0.5, nodes.getFirst().dampedOccupancy(), 1e-12);
        assertEquals(0.5, nodes.getFirst().dubo(), 1e-12);
        assertEquals("supplemental/reconstructed", nodes.getFirst().duboSource());
        assertEquals("engine log", nodes.get(1).duboSource());
        assertEquals(1, nodes.getFirst().entries().size());
        assertEquals(3, nodes.getFirst().entries().getFirst().tid());
        assertEquals(0.5, nodes.getFirst().entries().getFirst().contribution(), 1e-12);
    }

    @Test
    void acceptsLegacySnapshotsWithoutActiveFlags() {
        LogDocument.PhaseEvent event = new LogDocument.PhaseEvent("RECONSTRUCTION", "", "legacy.dat", 0.0,
                0, 0, 0, 0, null, "unavailable",
                JsonParser.parseString("{\"factor\":1.0}").getAsJsonObject(),
                JsonParser.parseString("""
                        {"lastTid":1,"transactionCount":1,"itemCount":1,
                         "transactions":[{"tid":1,"items":["X"]}]}
                        """).getAsJsonObject(),
                JsonParser.parseString("[]").getAsJsonArray());

        List<DhoListReconstructor.Node> nodes = DhoListReconstructor.reconstruct(event);

        assertEquals(1, nodes.size());
        assertEquals(1.0, nodes.getFirst().dampedOccupancy(), 1e-12);
    }
}
