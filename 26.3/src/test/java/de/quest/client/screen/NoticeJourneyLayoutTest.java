package de.quest.client.screen;

import de.quest.network.VillageNetworkPayloads.NoticeJourneyPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoticeJourneyLayoutTest {
    @Test void everyStageHasContainedNonOverlappingActionsAndMatchingHitRegions() {
        for (int stage = 0; stage <= 20; stage++) for (boolean requests : new boolean[] {false, true}) {
            var buttons = NoticeJourneyLayout.buttons(stage, requests);
            for (var button : buttons) {
                assertTrue(button.x() >= 18 && button.x() + button.width() <= 342);
                assertTrue(button.y() >= 142 && button.y() + button.height() <= 176);
                assertTrue(button.contains(button.x() + button.width() / 2., button.y() + 7));
                assertFalse(button.contains(button.x() + button.width(), button.y()));
                for (var other : buttons) if (button != other) {
                    assertFalse(button.x() < other.x() + other.width() && other.x() < button.x() + button.width()
                            && button.y() < other.y() + other.height() && other.y() < button.y() + button.height());
                }
            }
        }
    }
    @Test void choicesAreEqualAndCenteredWithNavigationInASeparateCenteredRow() {
        var choices = NoticeJourneyLayout.buttons(NoticeJourneyPayload.CHOOSE, false);
        assertEquals(134, choices.get(0).width());
        assertEquals(choices.get(0).width(), choices.get(1).width());
        assertEquals(360, choices.get(0).x() + choices.get(1).x() + choices.get(1).width());
        assertEquals(142, choices.get(0).y());
        assertEquals(162, choices.get(2).y());
    }
}
