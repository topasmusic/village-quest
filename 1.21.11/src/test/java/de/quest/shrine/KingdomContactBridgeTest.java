package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.content.story.ShadowsTradeRoadEncounterService.VillageMarker;
import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomContactBridgeTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void generatedContactLazilyCreatesOneSharedIdAcrossPlayersWithoutFounder() {
        var state = new LivingKingdomsState();
        var alice = new PlayerQuestData();
        var bob = new PlayerQuestData();
        var a = VillageContactService.bindCanonical(state, alice, "minecraft:overworld", marker(100),
                new BlockPos(100, 64, 100), VillageBondType.FORGE);
        var b = VillageContactService.bindCanonical(state, bob, "minecraft:overworld", marker(100),
                new BlockPos(100, 65, 100), VillageBondType.APIARY);
        assertTrue(a.accepted()); assertTrue(b.accepted());
        assertEquals(1, state.villages().size());
        var village = state.byLegacy(VillageKey.overworld(100, 100)).orElseThrow();
        assertNull(village.founder());
        assertEquals(village.id().toString(), alice.getTradeRouteString(VillageBondService.villageKey(0, "canonical_id")));
        assertEquals(village.id().toString(), bob.getTradeRouteString(VillageBondService.villageKey(0, "canonical_id")));
        assertEquals(0, alice.getTradeRouteInt("route_count"));
        assertEquals(VillageBondType.APIARY, b.contact().type());
    }

    @Test void historicalToleranceAliasPreservesExistingRecordAndThenRepeatsWithoutDuplication() {
        var state = new LivingKingdomsState();
        var data = new PlayerQuestData();
        VillageContactService.establish(data, 100, 100, VillageBondType.FORGE);
        data.setTradeRouteInt(VillageBondService.villageKey(0, "level"), VillageBondLevel.ALLIED.id() + 1);
        var result = VillageContactService.bindCanonical(state, data, "minecraft:overworld", marker(105),
                new BlockPos(105, 64, 100), VillageBondType.APIARY);
        assertEquals(0, result.contact().villageIndex());
        assertEquals(100, result.contact().x());
        assertEquals(VillageBondType.FORGE, result.contact().type());
        var canonical = state.byLegacy(VillageKey.overworld(105, 100)).orElseThrow();
        assertEquals(canonical.id(), state.byLegacy(VillageKey.overworld(100, 100)).orElseThrow().id());
        var repeated = VillageContactService.bindCanonical(state, data, "minecraft:overworld", marker(105),
                new BlockPos(105, 64, 100), VillageBondType.APIARY);
        assertEquals(0, repeated.contact().villageIndex());
        assertEquals(1, VillageBondService.historicalVillageCount(data));
        assertEquals(VillageBondLevel.ALLIED.id() + 1, data.getTradeRouteInt(VillageBondService.villageKey(0, "level")));
    }

    @Test void closeCustomVillageNeverAdoptsAnUnrelatedGeneratedToleranceRecord() {
        var state = new LivingKingdomsState();
        var data = new PlayerQuestData();
        VillageContactService.establish(data, 100, 100, VillageBondType.FORGE);
        UUID id = UUID.randomUUID();
        state.registerCustom(id, UUID.randomUUID(), VillageKey.overworld(103, 100),
                new BlockPos(103, -20, 100), "Talheim").orElseThrow();
        var result = VillageContactService.bindCanonical(state, data, "minecraft:overworld", marker(103),
                new BlockPos(103, -20, 100), VillageBondType.APIARY);
        assertEquals(1, result.contact().villageIndex());
        assertEquals(2, VillageBondService.historicalVillageCount(data));
        assertEquals("", data.getTradeRouteString(VillageBondService.villageKey(0, "canonical_id")));
        assertEquals(id.toString(), data.getTradeRouteString(VillageBondService.villageKey(1, "canonical_id")));
    }

    private static VillageMarker marker(int x) { return new VillageMarker(x + "_100", x, 100, x - 8, x + 8, 92, 108); }
}
