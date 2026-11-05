package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.guildtown.GuildTownProgress;
import de.quest.guildtown.GuildTownStory;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class PlayerQuestDataIsolationTest {
    private static final UUID PLAYER = UUID.fromString("cf36705a-e8ec-4a79-9d14-18699a2253be");

    @Test
    void normalStoryClearRemovesOnlyQuestmasterScratchAndPreservesGuildTownLifecycle() {
        PlayerQuestData data = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginStory(
                data, GuildTownStory.SHARED_TABLE, 3, GuildTownProgress.RECOVERY));
        GuildTownProgress.addStoryProgress(data, GuildTownStory.SHARED_TABLE, 1, 7, 24);
        data.setStoryInt("classic_story.progress", 5);
        data.setStoryFlag("classic_story.receipt", true);

        data.clearStoryProgress();

        assertEquals(GuildTownStory.SHARED_TABLE.id(), GuildTownProgress.activeStoryId(data));
        assertEquals(GuildTownProgress.ACTIVE,
                GuildTownProgress.storyState(data, GuildTownStory.SHARED_TABLE));
        assertEquals(7, GuildTownProgress.storyProgress(data, GuildTownStory.SHARED_TABLE, 1));
        assertEquals(0, data.getStoryInt("classic_story.progress"));
        assertFalse(data.hasStoryFlag("classic_story.receipt"));
    }

    @Test
    void completedGuildTownStorySurvivesSubsequentNormalStoryLifecycleClears() {
        PlayerQuestData data = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginStory(
                data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.PREVENTIVE));
        assertTrue(GuildTownProgress.markStoryReady(data, GuildTownStory.LONG_DRIVE));
        assertTrue(GuildTownProgress.completeStory(data, GuildTownStory.LONG_DRIVE));

        data.setStoryInt("normal.accepted", 1);
        data.clearStoryProgress();
        data.setStoryInt("normal.completed", 1);
        data.clearStoryProgress();

        assertTrue(GuildTownProgress.storyCompleted(data, GuildTownStory.LONG_DRIVE));
        assertEquals(GuildTownProgress.COMPLETE,
                GuildTownProgress.storyState(data, GuildTownStory.LONG_DRIVE));
    }

    @Test
    void pilgrimLifecycleClearPreservesProsperityAndDailyRerollState() {
        PlayerQuestData data = new PlayerQuestData();
        data.setPilgrimInt("pilgrim_lantern_skeletons", 6);
        data.setPilgrimFlag("pilgrim_contract_ready", true);
        data.setPilgrimFlag("pilgrim_contract_suppress_offer", true);
        data.setPilgrimFlag("pilgrim_contract_completed.quench_for_the_hall", true);
        data.setPilgrimInt("prosperity.rank.market", 3);
        data.setPilgrimFlag("prosperity.collection.guild_banner", true);
        data.setPilgrimInt("prosperity.festival_charges", 2);
        data.setPilgrimInt("prosperity.ceremony_charges", 1);
        data.setPilgrimInt("prosperity.stat.services", 17);
        data.setPilgrimInt("prosperity.commission.day", 42);
        data.setPilgrimInt("balance.daily_reroll_day", 91);
        data.setPilgrimInt("balance.daily_reroll_count", 2);

        data.clearPilgrimProgress();

        assertEquals(0, data.getPilgrimInt("pilgrim_lantern_skeletons"));
        assertFalse(data.hasPilgrimFlag("pilgrim_contract_ready"));
        assertFalse(data.hasPilgrimFlag("pilgrim_contract_suppress_offer"));
        assertTrue(data.hasPilgrimFlag("pilgrim_contract_completed.quench_for_the_hall"));
        assertEquals(3, data.getPilgrimInt("prosperity.rank.market"));
        assertTrue(data.hasPilgrimFlag("prosperity.collection.guild_banner"));
        assertEquals(2, data.getPilgrimInt("prosperity.festival_charges"));
        assertEquals(1, data.getPilgrimInt("prosperity.ceremony_charges"));
        assertEquals(17, data.getPilgrimInt("prosperity.stat.services"));
        assertEquals(42, data.getPilgrimInt("prosperity.commission.day"));
        assertEquals(91, data.getPilgrimInt("balance.daily_reroll_day"));
        assertEquals(2, data.getPilgrimInt("balance.daily_reroll_count"));
    }

    @Test
    void routeResetRemovesRouteDomainWithoutClearingOtherPersistentSystems() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("home_x", 32);
        data.setTradeRouteFlag("home_bound", true);
        data.setTradeRouteInt("route_count", 1);
        data.setTradeRouteInt("route_0_x", 256);
        data.setTradeRouteString("route_0_name", "North Road");
        data.setTradeRouteFlag("route_0_stopped", true);
        data.setTradeRouteInt("survey_point_count", 1);
        data.setTradeRouteInt("network_escrow", 8);
        data.setTradeRouteInt("guild_contract_type", 2);
        data.setTradeRouteInt("guild_contracts_completed", 9);

        data.setTradeRouteInt("bond_village_count", 1);
        data.setTradeRouteInt("bond_village_0_x", 144);
        data.setTradeRouteFlag("bond_village_0_contact", true);
        data.setTradeRouteFlag("guild_intro.welcome_completed", true);
        data.setTradeRouteInt("guild_town.chronicle.sequence", 4);
        data.setTradeRouteString("guild_town.chronicle.entry.4.event", "story.completed.shared_table");
        data.setTradeRouteString("prosperity.commission.offer", "iron_delivery");
        data.setTradeRouteFlag("archive.first_core_restored", true);

        data.clearTradeRoutes();

        assertEquals(0, data.getTradeRouteInt("home_x"));
        assertFalse(data.hasTradeRouteFlag("home_bound"));
        assertEquals(0, data.getTradeRouteInt("route_count"));
        assertEquals(0, data.getTradeRouteInt("route_0_x"));
        assertEquals("", data.getTradeRouteString("route_0_name"));
        assertFalse(data.hasTradeRouteFlag("route_0_stopped"));
        assertEquals(0, data.getTradeRouteInt("survey_point_count"));
        assertEquals(0, data.getTradeRouteInt("network_escrow"));
        assertEquals(0, data.getTradeRouteInt("guild_contract_type"));
        assertEquals(9, data.getTradeRouteInt("guild_contracts_completed"));

        assertEquals(1, data.getTradeRouteInt("bond_village_count"));
        assertEquals(144, data.getTradeRouteInt("bond_village_0_x"));
        assertTrue(data.hasTradeRouteFlag("bond_village_0_contact"));
        assertTrue(data.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertEquals(4, data.getTradeRouteInt("guild_town.chronicle.sequence"));
        assertEquals("story.completed.shared_table",
                data.getTradeRouteString("guild_town.chronicle.entry.4.event"));
        assertEquals("iron_delivery", data.getTradeRouteString("prosperity.commission.offer"));
        assertTrue(data.hasTradeRouteFlag("archive.first_core_restored"));
    }

    @Test
    void isolatedStateSurvivesSaveReloadAfterIndependentLifecycleClears() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.beginStory(
                data, GuildTownStory.LANTERNS_IN_BLOOM, 1, GuildTownProgress.PREVENTIVE));
        data.setPilgrimInt("prosperity.rank.agriculture", 3);
        data.setPilgrimInt("balance.daily_reroll_count", 2);
        data.setTradeRouteFlag("guild_intro.welcome_completed", true);
        data.setTradeRouteFlag("bond_village_0_contact", true);
        data.setTradeRouteInt("route_count", 1);
        data.setStoryInt("normal_story_scratch", 4);
        data.setPilgrimInt("pilgrim_smoke_creepers", 2);

        data.clearStoryProgress();
        data.clearPilgrimProgress();
        data.clearTradeRoutes();
        PlayerQuestData restored = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);

        assertEquals(GuildTownStory.LANTERNS_IN_BLOOM.id(), GuildTownProgress.activeStoryId(restored));
        assertEquals(3, restored.getPilgrimInt("prosperity.rank.agriculture"));
        assertEquals(2, restored.getPilgrimInt("balance.daily_reroll_count"));
        assertTrue(restored.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertTrue(restored.hasTradeRouteFlag("bond_village_0_contact"));
        assertEquals(0, restored.getTradeRouteInt("route_count"));
        assertEquals(0, restored.getStoryInt("normal_story_scratch"));
        assertEquals(0, restored.getPilgrimInt("pilgrim_smoke_creepers"));
    }
}
