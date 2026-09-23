package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MarketRoadTroublesStoryArcTest {
    @Test
    void ledgerPaperWorkLeavesThePaperDeliveryAfterBindingBooks() {
        int bookInput = MarketRoadTroublesStoryArc.ledgerBookTarget() * 3;

        assertEquals(
                MarketRoadTroublesStoryArc.ledgerPaperDeliveryTarget() + bookInput,
                MarketRoadTroublesStoryArc.ledgerPaperWorkTarget()
        );
        assertEquals(
                MarketRoadTroublesStoryArc.ledgerPaperDeliveryTarget(),
                MarketRoadTroublesStoryArc.ledgerPaperWorkTarget() - bookInput
        );
    }
}
