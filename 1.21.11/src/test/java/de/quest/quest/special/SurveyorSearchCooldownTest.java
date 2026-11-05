package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import org.junit.jupiter.api.Test;

final class SurveyorSearchCooldownTest {
    @Test void failedSearchStillReservesExactlyThirtySecondsForPlayer(){
        var data=new PlayerQuestData();assertTrue(SurveyorCompassQuestService.beginSearch(data,100));
        assertEquals(700,data.getSurveyorCompassSearchCooldownUntil());
        assertFalse(SurveyorCompassQuestService.beginSearch(data,100));
        assertFalse(SurveyorCompassQuestService.beginSearch(data,699));
        assertTrue(SurveyorCompassQuestService.beginSearch(data,700));
    }
    @Test void anotherPlayerHasAnIndependentDeadline(){
        var alice=new PlayerQuestData();var bob=new PlayerQuestData();
        assertTrue(SurveyorCompassQuestService.beginSearch(alice,100));
        assertTrue(SurveyorCompassQuestService.beginSearch(bob,100));
        assertFalse(SurveyorCompassQuestService.beginSearch(alice,200));
    }
}
