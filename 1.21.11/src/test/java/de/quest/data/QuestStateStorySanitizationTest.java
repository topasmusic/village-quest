package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.quest.story.StoryArcType;
import org.junit.jupiter.api.Test;

final class QuestStateStorySanitizationTest {
    @Test
    void invalidActiveChapterClearsWithoutCompletingOrRewarding() {
        PlayerQuestData data = new PlayerQuestData();
        data.setCurrencyBalance(41);
        data.setActiveStoryArc(StoryArcType.NIGHT_BELLS);
        data.setStoryChapterProgress(StoryArcType.NIGHT_BELLS.id(), 999);

        assertTrue(QuestState.sanitizeLoadedPlayerData(data));

        assertNull(data.getActiveStoryArc());
        assertEquals(0, data.getStoryChapterProgress(StoryArcType.NIGHT_BELLS.id()));
        assertFalse(data.hasStoryCompleted(StoryArcType.NIGHT_BELLS.id()));
        assertEquals(41, data.getCurrencyBalance());
    }

    @Test
    void completedStoryClampsMalformedChapterAndUnknownIdsAreRemoved() {
        PlayerQuestData data = new PlayerQuestData();
        data.setStoryCompleted(StoryArcType.FAILING_HARVEST.id(), true);
        data.setStoryChapterProgress(StoryArcType.FAILING_HARVEST.id(), 999);
        data.setStoryChapterProgress("removed_story", 7);

        assertTrue(QuestState.sanitizeLoadedPlayerData(data));

        assertEquals(StoryArcType.FAILING_HARVEST.chapterCount(),
                data.getStoryChapterProgress(StoryArcType.FAILING_HARVEST.id()));
        assertEquals(0, data.getStoryChapterProgress("removed_story"));
        assertTrue(data.hasStoryCompleted(StoryArcType.FAILING_HARVEST.id()));
    }
}
