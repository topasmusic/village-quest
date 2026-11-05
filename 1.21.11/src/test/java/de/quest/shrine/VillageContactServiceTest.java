package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageContactServiceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void contactCreatesKnownVillageWithoutCreatingATradeRoute() {
        PlayerQuestData data = new PlayerQuestData();

        VillageContactService.ContactResult result =
                VillageContactService.establish(data, 144, -208, VillageBondType.APIARY);

        assertTrue(result.accepted());
        assertTrue(result.created());
        assertEquals(1, VillageContactService.contactCount(data));
        assertEquals(1, VillageBondService.historicalVillageCount(data));
        assertEquals(0, data.getTradeRouteInt("route_count"));
        assertEquals(VillageBondType.APIARY, result.contact().type());
        assertFalse(VillageContactService.shouldExposeInConnectedNetwork(data, 0, false));
        assertTrue(VillageContactService.shouldExposeInConnectedNetwork(data, 0, true));
        assertEquals(VillageBondLevel.KNOWN.id() + 1,
                data.getTradeRouteInt(VillageBondService.villageKey(0, "level")));
    }

    @Test
    void repeatedContactKeepsTheOriginalIdentityAndRecord() {
        PlayerQuestData data = new PlayerQuestData();
        VillageContactService.establish(data, 32, 64, VillageBondType.FORGE);

        VillageContactService.ContactResult repeated =
                VillageContactService.establish(data, 32, 64, VillageBondType.ARCHIVE);

        assertTrue(repeated.accepted());
        assertFalse(repeated.created());
        assertEquals(1, VillageContactService.contactCount(data));
        assertEquals(1, VillageBondService.historicalVillageCount(data));
        assertEquals(VillageBondType.FORGE, repeated.contact().type());
    }

    @Test
    void existingHistoricalVillageCanBecomeAContactWithoutChangingItsIdentity() {
        PlayerQuestData data = new PlayerQuestData();
        int index = VillageBondService.ensureVillageRecord(data, -96, 320, VillageBondType.PASTURE);

        VillageContactService.ContactResult result =
                VillageContactService.establish(data, -96, 320, VillageBondType.GRANARY);

        assertEquals(index, result.contact().villageIndex());
        assertTrue(result.created());
        assertEquals(VillageBondType.PASTURE, result.contact().type());
        assertEquals(1, VillageContactService.contactCount(data));
    }

    @Test
    void villageHistoryLimitRejectsContactWithoutAliasingExistingData() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("bond_village_count", VillageBondService.MAX_HISTORICAL_VILLAGES);

        VillageContactService.ContactResult result = VillageContactService.establish(
                data, 9_999, -9_999, VillageBondType.GRANARY);

        assertFalse(result.accepted());
        assertFalse(result.created());
        assertNull(result.contact());
        assertEquals(0, VillageContactService.contactCount(data));
        assertEquals(0, data.getTradeRouteInt("route_count"));
    }

    @Test
    void identicalCoordinatesInDifferentDimensionsCreateDistinctContacts() {
        PlayerQuestData data = new PlayerQuestData();

        VillageContactService.ContactResult overworld = VillageContactService.establish(
                data, "minecraft:overworld", 32, 64, VillageBondType.FORGE);
        VillageContactService.ContactResult nether = VillageContactService.establish(
                data, "minecraft:the_nether", 32, 64, VillageBondType.ARCHIVE);

        assertTrue(overworld.created());
        assertTrue(nether.created());
        assertEquals(2, VillageContactService.contactCount(data));
        assertEquals(2, VillageBondService.historicalVillageCount(data));
        assertEquals(VillageBondType.ARCHIVE, nether.contact().type());
    }
}
