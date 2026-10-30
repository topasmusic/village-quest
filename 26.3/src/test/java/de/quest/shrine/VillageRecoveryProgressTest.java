package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Server-free episode data contracts; live loss validation is a separate admission gate. */
final class VillageRecoveryProgressTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { VillageBondMigrationTest.bootstrap(); }

    @Test void villageCreditsSurviveNativeReloadsAndCannotBeErasedByAnEpisodeSwitch() {
        var state = VillageBondMigrationTest.load(new net.minecraft.nbt.CompoundTag());
        var owner = UUID.randomUUID(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var progress = VillageIntroRequestProgress.open(state.getPlayerData(owner), a, 0, false);
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        assertTrue(progress.rebind(b, 0, false));
        for (int i = 0; i < 3; i++) {
            state = VillageBondMigrationTest.load(VillageBondMigrationTest.save(state));
            progress = VillageIntroRequestProgress.open(state.getPlayerData(owner), b, 0, false);
            assertTrue(progress.rebind(a, 1, false));
            assertFalse(progress.offers(1, false).contains(VillageIntroRequestType.PATH_STONE));
            assertEquals(1, progress.paidCount());
            assertTrue(progress.rebind(b, 0, false));
        }
    }
    @Test void returningToVillageRestoresItsMaskAndNeverTransfersCompletions() {
        var data = new PlayerQuestData();
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var progress = VillageIntroRequestProgress.open(data, a, 0, false);
        assertTrue(progress.accept(VillageIntroRequestType.REPAIR_TIMBER, 1, 0, false));
        assertTrue(progress.complete(VillageIntroRequestType.REPAIR_TIMBER));
        var oldEpisode = progress.episode();
        assertTrue(progress.rebind(b, 0, false));
        assertNotEquals(oldEpisode, progress.episode());
        assertEquals(3, progress.offers(0, false).size());
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        assertEquals(2, progress.paidCount());
        assertTrue(progress.rebind(a, 1, false));
        assertFalse(progress.offers(1, false).contains(VillageIntroRequestType.REPAIR_TIMBER));
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 1, false));
        assertFalse(progress.paysActive());
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        assertTrue(progress.offers(2, true).isEmpty());
        assertTrue(progress.rebind(b, 1, false));
        assertTrue(progress.rebind(a, 2, true));
        assertTrue(progress.offers(2, true).isEmpty());
        assertEquals(2, progress.paidCount());
    }

    @Test void switchDropsOnlyUnfinishedHarvestAndRejectsSameVillageReroll() {
        var data = new PlayerQuestData(); var village = UUID.randomUUID();
        var progress = VillageIntroRequestProgress.open(data, village, 0, false);
        assertTrue(progress.accept(VillageIntroRequestType.HARVEST_HELP, 1, 0, false));
        assertTrue(progress.harvest(UUID.randomUUID()));
        var episode = progress.episode();
        assertFalse(progress.rebind(village, 0, false));
        assertEquals(episode, progress.episode()); assertEquals(1, progress.harvestCount());
        assertTrue(progress.rebind(UUID.randomUUID(), 0, false));
        assertNull(progress.active()); assertEquals(0, progress.harvestCount());
        assertEquals(0, progress.paidCount());
    }

    @Test void unknownOrPartialHistoryAndInvalidGenerationArePreserved() {
        for (String suffix : new String[]{"schema", "generation", "completed", "future"}) {
            var data = new PlayerQuestData(); var destination = UUID.randomUUID();
            var progress = VillageIntroRequestProgress.open(data, UUID.randomUUID(), 0, false);
            var key = VillageIntroRequestProgress.creditPrefix(destination) + suffix;
            data.setTradeRouteInt(key, 99);
            var ints = java.util.Map.copyOf(data.getTradeRouteIntState());
            var strings = java.util.Map.copyOf(data.getTradeRouteStringState());
            assertFalse(progress.rebind(destination, 0, false));
            assertEquals(ints, data.getTradeRouteIntState());
            assertEquals(strings, data.getTradeRouteStringState());
        }
        var data = new PlayerQuestData();
        data.setTradeRouteInt(PlayerQuestData.PROGRESS_GENERATION, -1);
        assertTrue(VillageIntroRequestProgress.open(data, UUID.randomUUID(), 0, false).offers(0, false).isEmpty());
        assertThrows(IllegalStateException.class, data::personalRestart);
    }

    @Test void resetReopensRealWorkButRetainsPaidBudgetAndGeneration() {
        var data = new PlayerQuestData(); var village = UUID.randomUUID();
        var progress = VillageIntroRequestProgress.open(data, village, 0, false);
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        var fresh = data.personalRestart();
        var next = VillageIntroRequestProgress.open(fresh, village, 0, false);
        assertEquals(2, fresh.progressGeneration());
        assertTrue(next.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
        assertFalse(next.paysActive());
        assertTrue(next.complete(VillageIntroRequestType.PATH_STONE));
        assertEquals(1, next.paidCount());
    }
}
