package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.daily.FirstDailyChoiceService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.weekly.WeeklyQuestService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** A hand-authored fixture matching the list-based QuestState schema written by 2.3.1. */
final class QuestState231MigrationFixtureTest {
    private static final UUID LEGACY = UUID.fromString("10c6ad52-b5fb-4d84-baa6-1f0271f9dc70");
    private static final UUID CONTACT_ONLY = UUID.fromString("ec5bc690-5e78-4e34-ad7b-e805f127fb67");

    @Test
    void realistic231FixtureMigratesOnceAndSurvivesAnotherReload() {
        QuestState migratedState = QuestState.fromNbt(legacy231Fixture());
        PlayerQuestData migrated = migratedState.getPlayerData(LEGACY);

        assertTrue(FirstDailyChoiceService.isCompleted(migrated));
        assertTrue(migrated.hasTradeRouteFlag("guild_intro.first_daily_migration_checked"));
        assertFalse(migrated.hasTradeRouteFlag("guild_intro.welcome_active"));
        assertFalse(migrated.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertEquals(DailyQuestService.DailyQuestType.WHEAT_HARVEST, migrated.getDailyChoice());
        assertEquals(742L, migrated.getAcceptedDay());
        assertEquals(17, migrated.getDailyInt("wheat_progress"));
        assertEquals(WeeklyQuestService.WeeklyQuestType.ROAD_WARDEN, migrated.getWeeklyChoice());
        assertEquals(105L, migrated.getWeeklyAcceptedCycle());
        assertEquals(9, migrated.getWeeklyInt("hostiles_defeated"));
        assertEquals(StoryArcType.FAILING_HARVEST, migrated.getActiveStoryArc());
        assertEquals(2, migrated.getStoryChapterProgress("failing_harvest"));
        assertEquals("quench_for_the_hall", migrated.getActivePilgrimContractId());
        assertEquals(6, migrated.getPilgrimInt("quench_water_collected"));
        assertEquals(1, migrated.getTradeRouteInt("route_count"));
        assertEquals(320, migrated.getTradeRouteInt("route_0_destination_x"));
        assertEquals(-144, migrated.getTradeRouteInt("bond_village_0_z"));
        assertEquals(0L, migrated.getCurrencyBalance());
        assertEquals(0, migrated.getStoryInt("guild_town.active_story"));

        PlayerQuestData contact = migratedState.getPlayerData(CONTACT_ONLY);
        assertTrue(contact.hasTradeRouteFlag("bond_village_0_contact"));
        assertEquals(0, contact.getTradeRouteInt("route_count"));
        assertFalse(FirstDailyChoiceService.isCompleted(contact));
        assertTrue(contact.hasTradeRouteFlag("guild_intro.first_daily_migration_checked"));

        QuestState reloadedState = QuestState.fromNbt(QuestState.toNbt(migratedState));
        PlayerQuestData reloaded = reloadedState.getPlayerData(LEGACY);
        assertFalse(FirstDailyChoiceService.migratePre24Progress(reloaded));
        assertTrue(FirstDailyChoiceService.isCompleted(reloaded));
        assertEquals(17, reloaded.getDailyInt("wheat_progress"));
        assertEquals(9, reloaded.getWeeklyInt("hostiles_defeated"));
        assertEquals(2, reloaded.getStoryChapterProgress("failing_harvest"));
        assertEquals("quench_for_the_hall", reloaded.getActivePilgrimContractId());
        assertEquals(1, reloaded.getTradeRouteInt("route_count"));
        assertEquals(0, reloadedState.getPlayerData(CONTACT_ONLY).getTradeRouteInt("route_count"));
        assertFalse(FirstDailyChoiceService.isCompleted(reloadedState.getPlayerData(CONTACT_ONLY)));
    }

    private static CompoundTag legacy231Fixture() {
        CompoundTag manager = new CompoundTag();
        manager.put("dailyDiscovered", list(idEntry(LEGACY)));
        manager.put("dailyChoice", list(stringEntry(LEGACY, "WHEAT_HARVEST")));
        manager.put("acceptedDay", list(longEntry(LEGACY, 742L)));
        manager.put("dailyProgressInts", list(namedIntEntry(LEGACY, "wheat_progress", 17)));
        manager.put("weeklyChoice", list(stringEntry(LEGACY, "ROAD_WARDEN")));
        manager.put("weeklyAcceptedCycle", list(longEntry(LEGACY, 105L)));
        manager.put("weeklyProgressInts", list(namedIntEntry(LEGACY, "hostiles_defeated", 9)));
        manager.put("activeStoryArc", list(stringEntry(LEGACY, "failing_harvest")));
        manager.put("storyChapterProgress", list(namedIntEntry(LEGACY, "failing_harvest", 2)));
        manager.put("storyProgressInts", list(namedIntEntry(LEGACY, "story.failing_harvest.delivered", 8)));
        manager.put("pilgrimActiveContract", list(stringEntry(LEGACY, "quench_for_the_hall")));
        manager.put("pilgrimProgressInts", list(namedIntEntry(LEGACY, "quench_water_collected", 6)));

        ListTag routeInts = new ListTag();
        routeInts.add(namedIntEntry(LEGACY, "route_count", 1));
        routeInts.add(namedIntEntry(LEGACY, "route_0_destination_x", 320));
        routeInts.add(namedIntEntry(LEGACY, "route_0_destination_z", -144));
        routeInts.add(namedIntEntry(LEGACY, "route_0_quality", 64));
        routeInts.add(namedIntEntry(LEGACY, "bond_village_count", 1));
        routeInts.add(namedIntEntry(LEGACY, "bond_village_0_x", 320));
        routeInts.add(namedIntEntry(LEGACY, "bond_village_0_z", -144));
        routeInts.add(namedIntEntry(LEGACY, "bond_village_0_type", 2));
        routeInts.add(namedIntEntry(LEGACY, "bond_village_0_level", 2));
        routeInts.add(namedIntEntry(CONTACT_ONLY, "bond_village_count", 1));
        routeInts.add(namedIntEntry(CONTACT_ONLY, "bond_village_0_x", -900));
        routeInts.add(namedIntEntry(CONTACT_ONLY, "bond_village_0_z", 700));
        routeInts.add(namedIntEntry(CONTACT_ONLY, "bond_village_0_type", 3));
        manager.put("tradeRouteInts", routeInts);

        ListTag routeFlags = new ListTag();
        routeFlags.add(namedKeyEntry(LEGACY, "home_bound"));
        routeFlags.add(namedKeyEntry(CONTACT_ONLY, "bond_village_0_contact"));
        manager.put("tradeRouteFlags", routeFlags);

        CompoundTag root = new CompoundTag();
        root.put("questManager", manager);
        return root;
    }

    private static ListTag list(CompoundTag entry) {
        ListTag list = new ListTag();
        list.add(entry);
        return list;
    }

    private static CompoundTag idEntry(UUID id) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", id.toString());
        return entry;
    }

    private static CompoundTag stringEntry(UUID id, String value) {
        CompoundTag entry = idEntry(id);
        entry.putString("v", value);
        return entry;
    }

    private static CompoundTag longEntry(UUID id, long value) {
        CompoundTag entry = idEntry(id);
        entry.putLong("v", value);
        return entry;
    }

    private static CompoundTag namedIntEntry(UUID id, String key, int value) {
        CompoundTag entry = idEntry(id);
        entry.putString("key", key);
        entry.putInt("v", value);
        return entry;
    }

    private static CompoundTag namedKeyEntry(UUID id, String key) {
        CompoundTag entry = idEntry(id);
        entry.putString("key", key);
        return entry;
    }
}
