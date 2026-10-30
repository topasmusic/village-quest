package de.quest.data;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryLane;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native player data only. Loader/runtime activation is a separate integration step. */
final class StoryLaneDataTest {
    @ParameterizedTest @EnumSource(StoryArcType.class)
    void everyArcHasExactlyOneFixedLane(StoryArcType arc) {
        assertEquals(arc == StoryArcType.LIVING_KINGDOMS ? StoryLane.KINGDOM : StoryLane.CORE,
                StoryLane.forArc(arc));
    }

    @Test void independentSlotsRejectWrongLaneWithoutChangingEither() {
        var data = new PlayerQuestData();
        data.setActiveStoryArc(StoryLane.CORE, StoryArcType.NIGHT_BELLS);
        data.setActiveStoryArc(StoryLane.KINGDOM, StoryArcType.LIVING_KINGDOMS);
        assertThrows(IllegalArgumentException.class,
                () -> data.setActiveStoryArc(StoryLane.CORE, StoryArcType.LIVING_KINGDOMS));
        assertThrows(IllegalArgumentException.class,
                () -> data.setActiveStoryArc(StoryLane.KINGDOM, StoryArcType.FAILING_HARVEST));
        assertEquals(StoryArcType.NIGHT_BELLS, data.getActiveStoryArc(StoryLane.CORE));
        assertEquals(StoryArcType.LIVING_KINGDOMS, data.getActiveStoryArc(StoryLane.KINGDOM));
        data.setActiveStoryArc(StoryLane.CORE, null);
        assertTrue(data.isKingdomStoryActive());
        data.setActiveStoryArc(StoryLane.CORE, StoryArcType.SILENT_FORGE);
        data.setActiveStoryArc(StoryLane.KINGDOM, null);
        assertEquals(StoryArcType.SILENT_FORGE, data.getActiveStoryArc());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void legacyKingdomConversionKeepsChapterCountersDeedsAndPaidReceipts(int chapter) {
        var data = new PlayerQuestData();
        data.setActiveStoryArc(StoryArcType.LIVING_KINGDOMS);
        data.setStoryChapterProgress("living_kingdoms", chapter);
        data.setStoryDiscovered("living_kingdoms", true);
        data.setStoryInt("core_counter", 17);
        data.setStoryInt("guild_town.saved", 29);
        data.setStoryFlag("story_ready_living_kingdoms_" + chapter, true);
        data.setStoryFlag("core_flag", true);
        data.setTradeRouteString("kingdoms.story.target", "preserve-target");
        data.setTradeRouteInt("kingdoms.story.target_epoch", 4);
        data.setTradeRouteFlag("kingdoms.story.deed.saved", true);
        data.setStoryCooldownUntil(1234567L);
        PersonalResetReceipts.storyPaidThrough(data, StoryArcType.LIVING_KINGDOMS, chapter);
        var ints = Map.copyOf(data.getStoryIntState());
        var chapters = Map.copyOf(data.getStoryChapterProgressState());
        var discovered = Set.copyOf(data.getStoryDiscovered());
        var routeInts = Map.copyOf(data.getTradeRouteIntState());
        var routeStrings = Map.copyOf(data.getTradeRouteStringState());
        assertTrue(data.migrateLegacyStoryLane());
        assertNull(data.getActiveStoryArc());
        assertTrue(data.isKingdomStoryActive());
        assertFalse(data.hasStoryFlag("story_ready_living_kingdoms_" + chapter));
        assertTrue(data.hasTradeRouteFlag("kingdoms.story.ready." + chapter));
        assertTrue(data.hasStoryFlag("core_flag"));
        assertTrue(data.hasTradeRouteFlag("kingdoms.story.deed.saved"));
        assertEquals(ints, data.getStoryIntState());
        assertEquals(chapters, data.getStoryChapterProgressState());
        assertEquals(discovered, data.getStoryDiscovered());
        assertEquals(routeInts, data.getTradeRouteIntState());
        assertEquals(routeStrings, data.getTradeRouteStringState());
        assertEquals(1234567L, data.getStoryCooldownUntil());
        for (int i = 0; i < 5; i++)
            assertEquals(i < chapter, PersonalResetReceipts.storyPaid(data, StoryArcType.LIVING_KINGDOMS, i));
        assertFalse(data.migrateLegacyStoryLane());
    }

    @Test void completedLegacyKingdomIsNotReactivated() {
        var data = new PlayerQuestData();
        data.setActiveStoryArc(StoryArcType.LIVING_KINGDOMS);
        data.setStoryCompleted("living_kingdoms", true);
        data.setStoryChapterProgress("living_kingdoms", 5);
        assertTrue(data.migrateLegacyStoryLane());
        assertFalse(data.isKingdomStoryActive());
        assertTrue(data.hasStoryCompleted("living_kingdoms"));
        assertEquals(5, data.getStoryChapterProgress("living_kingdoms"));
    }

    @Test void coreMigrationIsNoOpAndExplicitFullStoryResetClearsBothSlots() {
        var data = new PlayerQuestData();
        data.setActiveStoryArc(StoryLane.CORE, StoryArcType.ARTIFACT_JOURNEY);
        data.setActiveStoryArc(StoryLane.KINGDOM, StoryArcType.LIVING_KINGDOMS);
        data.setStoryInt("guild_town.saved", 11);
        assertFalse(data.migrateLegacyStoryLane());
        assertEquals(StoryArcType.ARTIFACT_JOURNEY, data.getActiveStoryArc());
        assertTrue(data.isKingdomStoryActive());
        data.resetStoryState();
        assertNull(data.getActiveStoryArc());
        assertFalse(data.isKingdomStoryActive());
        assertEquals(11, data.getStoryInt("guild_town.saved"));
    }

    @Test void fullStoryResetClearsBothLaneNoticesButKeepsPayoutFrontiersAndLocalProgress() {
        var data=new PlayerQuestData();
        data.setStoryFlag("story_ready_night_bells_0",true);
        data.setTradeRouteFlag("kingdoms.story.ready.0",true);
        data.setTradeRouteString("kingdoms.story.offer_notice","living_kingdoms:0");
        data.setStoryInt("guild_town.saved",13);
        PersonalResetReceipts.storyPaidThrough(data,StoryArcType.LIVING_KINGDOMS,2);
        data.resetStoryState();
        assertFalse(data.hasStoryFlag("story_ready_night_bells_0"));
        assertFalse(data.hasTradeRouteFlag("kingdoms.story.ready.0"));
        assertEquals("",data.getTradeRouteString("kingdoms.story.offer_notice"));
        assertTrue(PersonalResetReceipts.storyPaid(data,StoryArcType.LIVING_KINGDOMS,1));
        assertEquals(13,data.getStoryInt("guild_town.saved"));
    }
}
