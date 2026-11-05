package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.content.story.NightBellsMarkerProgress;
import de.quest.quest.story.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class NightBellsMarkerReloadTest {
    @Test void legacyAndExactAreasAndPaidChapterEvidenceSurviveTwoNativeReloads() {
        var state=QuestState.fromNbt(new CompoundTag());var owner=UUID.randomUUID();var data=state.getPlayerData(owner);
        data.setActiveStoryArc(StoryArcType.NIGHT_BELLS);data.setStoryChapterProgress("night_bells",1);
        data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND,1);data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X,-100);
        data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+"1_0",31);
        PersonalResetReceipts.storyPaidThrough(data,StoryArcType.NIGHT_BELLS,1);
        assertTrue(NightBellsMarkerProgress.record(data::getStoryInt,data::setStoryInt,1,new BlockPos(-16,70,-8),3));
        var expected=Map.copyOf(data.getStoryIntState());
        for(int round=0;round<2;round++) {
            state=QuestState.fromNbt(QuestState.toNbt(state));data=state.getPlayerData(owner);
            assertEquals(expected,data.getStoryIntState());assertEquals(2,NightBellsMarkerProgress.count(data::getStoryInt,1));
            assertEquals(-16,NightBellsMarkerProgress.exactSectors(data::getStoryInt,1).getFirst().getX());
            assertTrue(PersonalResetReceipts.storyPaid(data,StoryArcType.NIGHT_BELLS,0));
            assertEquals(1,data.getStoryChapterProgress("night_bells"));
        }
    }
}
