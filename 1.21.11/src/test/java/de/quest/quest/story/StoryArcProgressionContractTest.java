package de.quest.quest.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class StoryArcProgressionContractTest {
    @Test
    void nightBellsRemainsInMainProgressionImmediatelyAfterCoreArcs() {
        List<StoryArcType> arcs = StoryArcType.questmasterArcs();

        assertEquals(StoryArcType.NIGHT_BELLS, arcs.get(StoryArcType.coreQuestmasterArcs().size()));
        assertTrue(StoryArcType.isQuestmasterArc(StoryArcType.NIGHT_BELLS));
    }

    @Test
    void registryFreeChapterMetadataMatchesRuntimeDefinitions() {
        for (StoryArcType type : StoryArcType.values()) {
            assertEquals(StoryQuestService.definition(type).chapterCount(), type.chapterCount());
        }
    }
}
