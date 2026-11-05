package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class QuestStateStoryTurnInReceiptTest {
    private static final UUID PLAYER = UUID.fromString("0f9d6511-600a-40a6-bfd2-3292ec569327");

    @Test
    void chapterScopedSharedTurnInReceiptSurvivesReloadAndClearsWithStoryProgress() {
        StoryArcType arc = StoryArcType.FAILING_HARVEST;
        String receipt = StoryQuestKeys.sharedTurnInConsumed(arc, 2);
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        data.setActiveStoryArc(arc);
        data.setStoryChapterProgress(arc.id(), 2);
        data.setStoryFlag(receipt, true);

        PlayerQuestData restored = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);

        assertTrue(restored.hasStoryFlag(receipt));
        assertFalse(restored.hasStoryFlag(StoryQuestKeys.sharedTurnInConsumed(arc, 3)));
        restored.clearStoryProgress();
        assertFalse(restored.hasStoryFlag(receipt));
    }
}
