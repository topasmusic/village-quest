package de.quest.party;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import org.junit.jupiter.api.Test;

final class QuestShareProfilesTest {
    @Test
    void dailyProfilesPoolEveryCurrentObjectiveAndRetainLegacyCollarFlag() {
        assertTrue(QuestShareProfiles.sharesDailyInt(
                DailyQuestService.DailyQuestType.PET_COLLAR, DailyQuestKeys.PET_COLLAR_PROGRESS));
        assertTrue(QuestShareProfiles.sharesDailyFlag(
                DailyQuestService.DailyQuestType.PET_COLLAR, DailyQuestKeys.PET_COLLAR_DONE));

        assertTrue(QuestShareProfiles.sharesDailyInt(
                DailyQuestService.DailyQuestType.WOODCUTTING, DailyQuestKeys.WOOD_PROGRESS));
        assertTrue(QuestShareProfiles.sharesDailyInt(
                DailyQuestService.DailyQuestType.WOODCUTTING, DailyQuestKeys.COAL_PROGRESS));

        assertTrue(QuestShareProfiles.sharesDailyInt(
                DailyQuestService.DailyQuestType.COAL_MINING, DailyQuestKeys.SMITH_COAL_PROGRESS));
        assertTrue(QuestShareProfiles.sharesDailyInt(
                DailyQuestService.DailyQuestType.COAL_MINING, DailyQuestKeys.IRON_PROGRESS));
        assertFalse(QuestShareProfiles.sharesDailyInt(
                DailyQuestService.DailyQuestType.COAL_MINING, DailyQuestKeys.COAL_PROGRESS));
    }
}
