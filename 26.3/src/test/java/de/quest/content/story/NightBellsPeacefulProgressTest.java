package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.content.story.ShadowsTradeRoadEncounterService.VillageMarker;
import net.minecraft.core.BlockPos;

final class NightBellsPeacefulProgressTest {
    private static final VillageMarker STORY = new VillageMarker("100_100", 100, 100, 0, 200, 0, 200);
    private static final VillageMarker OTHER = new VillageMarker("350_100", 350, 100, 300, 400, 0, 200);

    @Test
    void difficultySwitchResetsObjectivesButRetainsStoryVillageIdentity() {
        for (int chapter = 0; chapter < 4; chapter++) {
            var reset = NightBellsStoryArc.modeSpecificIntKeys(chapter);
            assertTrue(reset.contains(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL));
            assertFalse(reset.contains(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND));
            assertFalse(reset.contains(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X));
            assertFalse(reset.contains(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z));
        }
        assertFalse(NightBellsPeacefulProgress.inStoryVillage(OTHER,
                new BlockPos(350, 70, 100), true, 100, 100));
        assertTrue(NightBellsPeacefulProgress.inStoryVillage(STORY,
                new BlockPos(100, 70, 100), true, 100, 100));
    }

    @Test
    void peacefulActionsStayInsideTheAcceptedStoryVillage() {
        assertTrue(NightBellsPeacefulProgress.inStoryVillage(STORY, new BlockPos(40, 70, 100), true, 100, 100));
        assertFalse(NightBellsPeacefulProgress.inStoryVillage(null, new BlockPos(100, 70, 100), true, 100, 100));
        assertFalse(NightBellsPeacefulProgress.inStoryVillage(OTHER, new BlockPos(350, 70, 100), true, 100, 100));
    }

    @Test
    void fullStructureBoundsAcceptLargeIrregularVillageDistricts() {
        VillageMarker farDistrict = new VillageMarker("100_100", 100, 100, 0, 200, 0, 200);
        assertTrue(NightBellsPeacefulProgress.inStoryVillage(farDistrict, new BlockPos(195, 70, 180), true, 100, 100));
        assertTrue(NightBellsPeacefulProgress.inStoryVillage(farDistrict, new BlockPos(212, 70, 180), true, 100, 100));
        assertFalse(NightBellsPeacefulProgress.inStoryVillage(farDistrict, new BlockPos(240, 70, 180), true, 100, 100));
    }

    @Test
    void unboundObjectiveCannotSilentlyMatchAnotherVillage() {
        assertFalse(NightBellsPeacefulProgress.inStoryVillage(STORY, new BlockPos(100, 70, 100), false, 0, 0));
        assertFalse(NightBellsPeacefulProgress.inStoryVillage(OTHER, new BlockPos(350, 70, 100), true, 100, 100));
    }
    @Test
    void everyChapterRequiresInfrastructureAndSupplies() {
        assertTrue(NightBellsPeacefulProgress.complete(true, 0, 1, 3, true, true, false));
        assertFalse(NightBellsPeacefulProgress.complete(true, 0, 0, 3, true, true, false));
        assertFalse(NightBellsPeacefulProgress.complete(true, 0, 1, 2, true, true, false));
        assertFalse(NightBellsPeacefulProgress.complete(true, 0, 1, 3, false, true, false));

        assertTrue(NightBellsPeacefulProgress.complete(true, 1, 0, 3, true, true, false));
        assertFalse(NightBellsPeacefulProgress.complete(true, 1, 0, 2, true, true, false));

        assertTrue(NightBellsPeacefulProgress.complete(true, 2, 0, 3, true, true, false));
        assertFalse(NightBellsPeacefulProgress.complete(true, 2, 0, 3, false, true, false));
    }

    @Test
    void finalPreparationCannotBeReducedToASingleTurnIn() {
        assertFalse(NightBellsPeacefulProgress.complete(false, 3, 1, 4, true, true, true));
        assertTrue(NightBellsPeacefulProgress.complete(true, 3, 1, 4, true, true, true));
        assertFalse(NightBellsPeacefulProgress.complete(true, 3, 0, 4, true, true, true));
        assertFalse(NightBellsPeacefulProgress.complete(true, 3, 1, 3, true, true, true));
        assertFalse(NightBellsPeacefulProgress.complete(true, 3, 1, 4, false, true, true));
        assertFalse(NightBellsPeacefulProgress.complete(true, 3, 1, 4, true, false, true));
        assertFalse(NightBellsPeacefulProgress.complete(true, 3, 1, 4, true, true, false));
    }
}
