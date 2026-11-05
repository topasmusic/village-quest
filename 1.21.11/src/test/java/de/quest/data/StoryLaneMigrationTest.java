package de.quest.data;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryLane;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class StoryLaneMigrationTest {
    private final UUID owner = UUID.randomUUID();

    private CompoundTag legacy(int chapter) {
        var state = QuestState.fromNbt(new CompoundTag());
        var data = state.getPlayerData(owner);
        data.setActiveStoryArc(StoryArcType.LIVING_KINGDOMS);
        data.setStoryChapterProgress("living_kingdoms", chapter);
        data.setStoryFlag("story_ready_living_kingdoms_" + chapter, true);
        data.setStoryInt("guild_town.saved", 31);
        data.setStoryInt("classic_saved", 19);
        data.setTradeRouteString("kingdoms.story.target", UUID.randomUUID().toString());
        data.setTradeRouteInt("kingdoms.story.target_epoch", 9);
        data.setTradeRouteFlag("kingdoms.story.deed.saved", true);
        PersonalResetReceipts.storyPaidThrough(data, StoryArcType.LIVING_KINGDOMS, chapter);
        var root = QuestState.toNbt(state);
        root.getCompoundOrEmpty("questManager").remove("storyLaneFormat");
        root.getCompoundOrEmpty("questManager").remove("kingdomStoryActive");
        return root;
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4})
    void legacyMovesBeforeSanitizationAndSurvivesTwoNativeRoundTrips(int chapter) {
        var root = legacy(chapter);
        var oldManager = root.getCompoundOrEmpty("questManager");
        var state = QuestState.fromNbt(root);
        for (int round = 0; round < 2; round++) {
            var data = state.getPlayerData(owner);
            assertNull(data.getActiveStoryArc());
            assertTrue(data.isKingdomStoryActive());
            assertEquals(chapter, data.getStoryChapterProgress("living_kingdoms"));
            assertTrue(data.hasTradeRouteFlag("kingdoms.story.ready." + chapter));
            assertFalse(data.hasStoryFlag("story_ready_living_kingdoms_" + chapter));
            assertEquals(31, data.getStoryInt("guild_town.saved"));
            assertEquals(19, data.getStoryInt("classic_saved"));
            assertEquals(chapter > 0, PersonalResetReceipts.storyPaid(data, StoryArcType.LIVING_KINGDOMS, 0));
            var saved = QuestState.toNbt(state);
            var manager = saved.getCompoundOrEmpty("questManager");
            assertEquals(1, manager.getIntOr("storyLaneFormat", -1));
            assertEquals(oldManager.get("tradeRouteStrings"), manager.get("tradeRouteStrings"));
            assertEquals(oldManager.get("tradeRouteInts"), manager.get("tradeRouteInts"));
            assertEquals(root.get("vanillaRewards"), saved.get("vanillaRewards"));
            state = QuestState.fromNbt(saved);
        }
    }

    @Test void bothNewSlotsAndClassicReadySurviveReload() {
        var state = QuestState.fromNbt(new CompoundTag());
        var data = state.getPlayerData(owner);
        data.setActiveStoryArc(StoryLane.CORE, StoryArcType.NIGHT_BELLS);
        data.setActiveStoryArc(StoryLane.KINGDOM, StoryArcType.LIVING_KINGDOMS);
        data.setStoryFlag("story_ready_night_bells_2", true);
        data.setStoryChapterProgress("night_bells", 2);
        data.setStoryChapterProgress("living_kingdoms", 3);
        for (int i=0;i<2;i++) {
            state = QuestState.fromNbt(QuestState.toNbt(state));
            data = state.getPlayerData(owner);
            assertEquals(StoryArcType.NIGHT_BELLS, data.getActiveStoryArc());
            assertTrue(data.isKingdomStoryActive());
            assertTrue(data.hasStoryFlag("story_ready_night_bells_2"));
        }
    }

    @Test void legacyKingdomReadyIsTransferredEvenWhenCoreOccupiesOldSlot() {
        var root=legacy(2);
        var manager=root.getCompoundOrEmpty("questManager");
        manager.getListOrEmpty("activeStoryArc").getCompoundOrEmpty(0).putString("v","night_bells");
        var state=QuestState.fromNbt(root); var data=state.getPlayerData(owner);
        assertEquals(StoryArcType.NIGHT_BELLS,data.getActiveStoryArc());
        assertFalse(data.isKingdomStoryActive());
        assertTrue(data.hasTradeRouteFlag("kingdoms.story.ready.2"));
        assertFalse(data.hasStoryFlag("story_ready_living_kingdoms_2"));
    }

    @Test void completedAndMalformedKingdomAreNotReactivated() {
        for (boolean completed : new boolean[]{false,true}) {
            var root=legacy(0);
            var manager=root.getCompoundOrEmpty("questManager");
            var rows=new ListTag(); var row=new CompoundTag();
            row.putString("id",owner.toString()); row.putString("key","living_kingdoms");
            row.putInt("v",completed?5:91); rows.add(row); manager.put("storyChapterProgress",rows);
            if(completed) {
                var entries=new ListTag(); var entry=new CompoundTag();
                entry.putString("id",owner.toString()); entry.putString("key","living_kingdoms");
                entries.add(entry); manager.put("storyCompletedEntries",entries);
            }
            var state=QuestState.fromNbt(root);
            assertFalse(state.getPlayerData(owner).isKingdomStoryActive());
        }
    }

    @Test void newerFormatKeepsRawRecordsAndRefusesDestructiveReset() {
        var root=legacy(2); var manager=root.getCompoundOrEmpty("questManager");
        manager.putInt("storyLaneFormat",47);
        manager.putString("kingdomStoryActive","future opaque owner data");
        manager.putLong("storyFutureReceipt",876543210L);
        var state=QuestState.fromNbt(root);
        assertThrows(IllegalStateException.class,()->state.restartPersonalProgress(owner));
        assertThrows(IllegalStateException.class,state::resetAllProgress);
        assertThrows(IllegalStateException.class,()->state.replacePersonalProgress(owner,new PlayerQuestData()));
        var saved=QuestState.toNbt(state).getCompoundOrEmpty("questManager");
        for(String key:manager.keySet()) if(key.startsWith("story") || key.equals("activeStoryArc") || key.equals("kingdomStoryActive"))
            assertEquals(manager.get(key),saved.get(key),key);
        var twice=QuestState.toNbt(QuestState.fromNbt(QuestState.toNbt(state))).getCompoundOrEmpty("questManager");
        assertEquals(saved,twice);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"missing","wrong_type","bad_owner","duplicate","extra_field"})
    void malformedNewActiveListIsPreservedInsteadOfDroppingStoryOwners(String kind) {
        var root=legacy(0); var manager=root.getCompoundOrEmpty("questManager");
        manager.putInt("storyLaneFormat",1);
        var rows=new ListTag(); var row=new CompoundTag(); row.putString("id",owner.toString()); rows.add(row);
        switch(kind) {
            case "missing" -> manager.remove("kingdomStoryActive");
            case "wrong_type" -> manager.putString("kingdomStoryActive","unknown");
            case "bad_owner" -> { row.putString("id","broken"); manager.put("kingdomStoryActive",rows); }
            case "duplicate" -> { rows.add(row.copy()); manager.put("kingdomStoryActive",rows); }
            default -> { row.putInt("future",2); manager.put("kingdomStoryActive",rows); }
        }
        var state=QuestState.fromNbt(root);
        assertThrows(IllegalStateException.class,()->state.restartPersonalProgress(owner));
        assertEquals(manager,QuestState.toNbt(state).getCompoundOrEmpty("questManager"));
    }
}
