package org.jvnc.dhopm.viewer;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuboSupplementerTest {
    @Test
    void listsOnlyDescendantsOfLoggedPrunedBoundsWithoutRecomputingMetrics() {
        LogDocument.PhaseEvent event = new LogDocument.PhaseEvent("MINING", "", "sample.dat", 2.0,
                0, 0, 0, 0, null, "unavailable",
                JsonParser.parseString("{\"factor\":0.5}").getAsJsonObject(),
                JsonParser.parseString("""
                        {"lastTid":3,"transactions":[
                          {"tid":1,"items":["A","B","C"]},
                          {"tid":2,"items":["A","B","C"]},
                          {"tid":3,"items":["A","B"]}
                        ]}
                        """).getAsJsonObject(),
                JsonParser.parseString("""
                        [{"patternKey":"C","value":1.0,"minSup":2.0,"passesMinSup":false},
                         {"patternKey":"A,B","value":2.5,"minSup":2.0,"passesMinSup":true}]
                        """).getAsJsonArray(),
                JsonParser.parseString("[{\"key\":\"A,C\",\"support\":2,\"dampedOccupancy\":1.5}]")
                        .getAsJsonArray());

        List<DuboSupplementer.PrunedPattern> results =
                DuboSupplementer.findPrunedDescendants(event);

        assertEquals(List.of("A,B,C", "B,C"),
                results.stream().map(DuboSupplementer.PrunedPattern::patternKey).toList());
        assertEquals(List.of("recompute", "recompute"),
                results.stream().map(DuboSupplementer.PrunedPattern::source).toList());
    }

    @Test
    void doesNotInventPrunedCandidatesForLegacyLogsWithoutComparisons() {
        LogDocument.PhaseEvent event = new LogDocument.PhaseEvent("MINING", "", "legacy.dat", 1.0,
                0, 0, 0, 0, null, "unavailable",
                JsonParser.parseString("{\"factor\":0.9}").getAsJsonObject(),
                JsonParser.parseString("""
                        {"lastTid":2,"transactions":[
                          {"tid":1,"items":["A","B"]},{"tid":2,"items":["A","B"]}
                        ]}
                        """).getAsJsonObject(),
                JsonParser.parseString("[]").getAsJsonArray());

        assertEquals(List.of(), DuboSupplementer.findPrunedDescendants(event));
    }
}
