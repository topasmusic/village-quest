package de.quest.client.screen;

import de.quest.network.VillageNetworkPayloads.NoticeJourneyPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoticeJourneyLayoutTest {
    @Test void everyStageHasContainedNonOverlappingActionsAndMatchingHitRegions() {
        for (int stage = 0; stage <= 20; stage++) for (boolean requests : new boolean[] {false, true}) {
            var buttons = NoticeJourneyLayout.buttons(stage, requests);
            for (var button : buttons) {
                assertTrue(button.x() >= 18 && button.x() + button.width() <= NoticeJourneyLayout.WIDTH-28);
                assertTrue(button.y() >= 195 && button.y() + button.height() <= NoticeJourneyLayout.HEIGHT-32);
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
        assertEquals(240, choices.get(0).width());
        assertEquals(choices.get(0).width(), choices.get(1).width());
        assertEquals(NoticeJourneyLayout.WIDTH, choices.get(0).x() + choices.get(1).x() + choices.get(1).width());
        assertEquals(233, choices.get(0).y());
        assertEquals(257, choices.get(2).y());
    }
    @Test void finishedStoriesDoNotPresentRefreshAsTheirPrimaryAction() {
        assertTrue(NoticeJourneyLayout.buttons(NoticeJourneyPayload.REMEMBERED,false)
                .stream().noneMatch(NoticeJourneyLayout.Button::primary));
    }
    @Test void welcomeActionFollowsItsBodyWithoutReservingMissingObjectiveRows() {
        var actions=NoticeJourneyLayout.buttons(NoticeJourneyPayload.QUESTMASTER,false);
        assertEquals(195,actions.getFirst().y());
        assertTrue(actions.getFirst().contains(280,203));
        assertEquals(257,actions.get(1).y());
    }
}
