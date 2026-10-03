package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TradeRouteSurveyMetricsTest {
    @Test
    void showsDirectAndDraftLengthsBeforeAndDuringSurvey() {
        var home = new TradeRouteSurveyMetrics.Point(0, 0, false);
        var village = new TradeRouteSurveyMetrics.Point(300, 0, false);
        var direct = TradeRouteSurveyMetrics.calculate(home, village, List.of(), 0.68);
        assertEquals(300, direct.straightBlocks());
        assertEquals(0, direct.surveyedBlocks());
        assertEquals(300, direct.estimatedBlocks());
        assertEquals(8, direct.estimatedMinutes());
        assertEquals(1, direct.expectedLegs());

        var detour = TradeRouteSurveyMetrics.calculate(home, village,
                List.of(new TradeRouteSurveyMetrics.Point(0, 400, false)), 0.68);
        assertEquals(400, detour.surveyedBlocks());
        assertEquals(900, detour.estimatedBlocks());
        assertEquals(21, detour.estimatedMinutes());
    }

    @Test
    void contiguousOceanPointsCountAsOneFerryStageAndLongDistanceRemainsInformation() {
        var result = TradeRouteSurveyMetrics.calculate(
                new TradeRouteSurveyMetrics.Point(0, 0, false),
                new TradeRouteSurveyMetrics.Point(5_000_000, 0, false),
                List.of(new TradeRouteSurveyMetrics.Point(100, 0, false),
                        new TradeRouteSurveyMetrics.Point(200, 0, true),
                        new TradeRouteSurveyMetrics.Point(300, 0, true),
                        new TradeRouteSurveyMetrics.Point(400, 0, false)), 0.68);
        assertEquals(3, result.expectedLegs());
        assertEquals(5_000_000, result.estimatedBlocks());
        assertTrue(result.unusuallyLong());
        assertEquals(167, result.estimatedMinutes());
    }
}
