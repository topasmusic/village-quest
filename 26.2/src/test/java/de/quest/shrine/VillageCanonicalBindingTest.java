package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageCanonicalBindingTest {
    @BeforeAll
    static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void adjacentCanonicalContactsStayDistinctInTheLegacyPersonalHistory() {
        PlayerQuestData data = new PlayerQuestData();
        LivingKingdomsState registry = new LivingKingdomsState();
        UUID owner = UUID.randomUUID();
        var a = registry.registerCustom(UUID.randomUUID(), owner, VillageKey.overworld(100, 100),
                new BlockPos(100, 64, 100), "Stoneford").orElseThrow();
        var b = registry.registerCustom(UUID.randomUUID(), owner, VillageKey.overworld(103, 100),
                new BlockPos(103, 64, 100), "Nearbrook").orElseThrow();
        var first = VillageContactService.establish(data, a, VillageBondType.FORGE);
        var second = VillageContactService.establish(data, b, VillageBondType.APIARY);
        assertEquals(0, first.contact().villageIndex());
        assertEquals(1, second.contact().villageIndex());
        assertEquals(2, VillageContactService.contactCount(data));
        assertEquals(0, VillageBondService.historicalVillageIndex(data, "minecraft:overworld", 100, 100));
        assertEquals(1, VillageBondService.historicalVillageIndex(data, "minecraft:overworld", 103, 100));
        assertEquals(-1, VillageBondService.historicalVillageIndex(data, "minecraft:overworld", 105, 100));
        assertNull(VillageContactService.findAt(data, 105, 100));
    }

    @Test
    void bindingAnExactHistoricalRecordKeepsItsPersonalTypeLevelAndClaims() {
        PlayerQuestData data = new PlayerQuestData();
        VillageContactService.establish(data, 100, 100, VillageBondType.FORGE);
        data.setTradeRouteInt(VillageBondService.villageKey(0, "level"), VillageBondLevel.ALLIED.id() + 1);
        data.setTradeRouteFlag("old_claim", true);
        UUID id = UUID.randomUUID();
        int index = VillageBondService.ensureCanonicalVillageRecord(data, id,
                "minecraft:overworld", 100, 100, VillageBondType.APIARY);
        assertEquals(0, index);
        assertEquals(1, VillageBondService.historicalVillageCount(data));
        assertEquals(id.toString(), data.getTradeRouteString(VillageBondService.villageKey(0, "canonical_id")));
        assertEquals(VillageBondType.FORGE, VillageContactService.read(data, 0).type());
        assertEquals(VillageBondLevel.ALLIED.id() + 1,
                data.getTradeRouteInt(VillageBondService.villageKey(0, "level")));
        assertTrue(data.hasTradeRouteFlag("old_claim"));
        assertEquals(0, data.getTradeRouteInt("route_count"));
    }

    @Test
    void canonicalIdCannotBeReboundToAnotherDimensionOrLegacyPosition() {
        PlayerQuestData data = new PlayerQuestData();
        UUID id = UUID.randomUUID();
        assertEquals(0, VillageBondService.ensureCanonicalVillageRecord(data, id,
                "minecraft:overworld", 100, 100, VillageBondType.FORGE));
        assertEquals(-1, VillageBondService.ensureCanonicalVillageRecord(data, id,
                "minecraft:overworld", 103, 100, VillageBondType.FORGE));
        assertEquals(-1, VillageBondService.ensureCanonicalVillageRecord(data, id,
                "minecraft:the_nether", 100, 100, VillageBondType.FORGE));
        assertEquals(1, VillageBondService.historicalVillageCount(data));
    }

    @Test
    void oldUnboundGeneratedContactsKeepTheirHistoricalTolerance() {
        PlayerQuestData data = new PlayerQuestData();
        VillageContactService.establish(data, 100, 100, VillageBondType.FORGE);
        assertEquals(0, VillageBondService.historicalVillageIndex(data, "minecraft:overworld", 105, 100));
        assertNotNull(VillageContactService.findAt(data, 105, 100));
    }
}
