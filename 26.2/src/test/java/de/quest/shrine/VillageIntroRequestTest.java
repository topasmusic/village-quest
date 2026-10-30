package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageIntroRequestTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void episodeAndAcceptedHarvestSurviveActualQuestStateReload() {
        var state = VillageBondMigrationTest.load(new net.minecraft.nbt.CompoundTag());
        var owner = UUID.randomUUID(); var village = UUID.randomUUID();
        var progress = VillageIntroRequestProgress.open(state.getPlayerData(owner), village, 0, false);
        assertTrue(progress.accept(VillageIntroRequestType.HARVEST_HELP, 1, 0, false));
        var receipt = UUID.randomUUID(); assertTrue(progress.harvest(receipt));
        var episode = progress.episode();
        for (int i = 0; i < 3; i++) {
            state = VillageBondMigrationTest.load(VillageBondMigrationTest.save(state));
            progress = VillageIntroRequestProgress.open(state.getPlayerData(owner), village, 0, false);
            assertEquals(episode, progress.episode()); assertEquals(1, progress.harvestCount());
            assertFalse(progress.harvest(receipt));
        }
    }
    @Test void unknownAndPartialRecordsArePreservedAndCannotGrantOffers() {
        for (int schema : new int[]{0, 2}) {
            var data = new PlayerQuestData();
            data.setTradeRouteInt("village_intro.schema", schema);
            data.setTradeRouteString("village_intro.episode", "unrecognized");
            var progress = VillageIntroRequestProgress.open(data, UUID.randomUUID(), 0, false);
            assertFalse(progress.writable()); assertTrue(progress.offers(0, false).isEmpty());
            assertEquals("unrecognized", data.getTradeRouteString("village_intro.episode"));
        }
    }
    @Test void twoDifferentOffersShareOneEpisodeAndNeverTouchTheRegularDay() {
        var data = new PlayerQuestData();
        var village = UUID.randomUUID();
        data.setTradeRouteInt("sentinel.request_day", 37);
        var progress = VillageIntroRequestProgress.open(data, village, 0, false);
        assertEquals(3, progress.offers(0, false).size());
        assertTrue(progress.accept(VillageIntroRequestType.REPAIR_TIMBER, 1, 0, false));
        assertFalse(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
        assertTrue(progress.complete(VillageIntroRequestType.REPAIR_TIMBER));
        assertFalse(progress.accept(VillageIntroRequestType.REPAIR_TIMBER, 1, 1, false));
        assertFalse(progress.accept(VillageIntroRequestType.PATH_STONE, 2, 1, false));
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 1, false));
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        assertTrue(progress.offers(2, true).isEmpty());
        assertEquals(37, data.getTradeRouteInt("sentinel.request_day"));
        assertEquals(2, progress.paidCount());
    }

    @Test void anotherOrdinaryVillageCannotRerollTheEpisode() {
        var data = new PlayerQuestData();
        var village = UUID.randomUUID();
        var progress = VillageIntroRequestProgress.open(data, village, 0, false);
        assertEquals(village, VillageIntroRequestProgress.open(data, UUID.randomUUID(), 0, false).village());
        assertEquals(progress.episode(), VillageIntroRequestProgress.open(data, village, 0, false).episode());
        assertTrue(VillageIntroRequestProgress.open(new PlayerQuestData(), village, 2, false).offers(2, true).isEmpty());
    }

    @Test void harvestNeedsEightDistinctConfirmedReceiptsAfterAcceptance() {
        var progress = VillageIntroRequestProgress.open(new PlayerQuestData(), UUID.randomUUID(), 0, false);
        assertFalse(progress.harvest(UUID.randomUUID()));
        assertTrue(progress.accept(VillageIntroRequestType.HARVEST_HELP, 1, 0, false));
        var first = UUID.randomUUID();
        assertTrue(progress.harvest(first));
        assertFalse(progress.harvest(first));
        for (int i = 0; i < 12; i++) progress.harvest(UUID.randomUUID());
        assertEquals(8, progress.harvestCount());
    }
    @Test void cancellationInvalidatesOldEpisodeWithoutRefundingCompletedOrPaidCredits() {
        var data=new PlayerQuestData(); var village=UUID.randomUUID();
        var progress=VillageIntroRequestProgress.open(data,village,0,false);
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE,1,0,false));
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        assertTrue(progress.accept(VillageIntroRequestType.HARVEST_HELP,1,1,false));
        assertTrue(progress.harvest(UUID.randomUUID())); var episode=progress.episode();
        assertFalse(progress.cancel(VillageIntroRequestType.REPAIR_TIMBER));
        assertEquals(episode,progress.episode());
        assertTrue(progress.cancel(VillageIntroRequestType.HARVEST_HELP));
        assertNotEquals(episode,progress.episode()); assertEquals(0,progress.harvestCount());
        assertEquals(1,progress.paidCount()); assertNull(progress.active());
        assertFalse(progress.offers(1,false).contains(VillageIntroRequestType.PATH_STONE));
        assertTrue(progress.accept(VillageIntroRequestType.REPAIR_TIMBER,1,1,false));
    }
}
