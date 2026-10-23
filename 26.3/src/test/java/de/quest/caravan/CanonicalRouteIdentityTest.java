package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CanonicalRouteIdentityTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void connectedCanonicalTownDoesNotMakeItsCloseNeighborConnected() {
        var state = new LivingKingdomsState(); var data = new PlayerQuestData(); UUID owner = UUID.randomUUID();
        var a = state.registerCustom(UUID.randomUUID(), owner, VillageKey.overworld(100, 100),
                new BlockPos(100, -20, 100), "Talheim").orElseThrow();
        var b = state.registerCustom(UUID.randomUUID(), owner, VillageKey.overworld(103, 100),
                new BlockPos(103, -20, 100), "Nearbrook").orElseThrow();
        VillageContactService.establish(data, a, VillageBondType.FORGE);
        VillageContactService.establish(data, b, VillageBondType.APIARY);
        data.setTradeRouteInt("route_count", 1); data.setTradeRouteInt("route_0_x", 100);
        data.setTradeRouteInt("route_0_z", 100); data.setTradeRouteInt("route_0_village_index", 1);
        assertTrue(TradeRouteService.isRegisteredDestination(data, 100, 100));
        assertFalse(TradeRouteService.isRegisteredDestination(data, 103, 100));
        assertFalse(TradeRouteService.isRegisteredDestination(state, data, "minecraft:overworld", 103, 100));
    }

    @Test void verifiedGeneratedAliasConnectsOldRouteWithoutChangingCoordinatesOrClaim() {
        var state = new LivingKingdomsState(); var data = new PlayerQuestData();
        var village = state.registerGenerated(VillageKey.overworld(105, 100), new BlockPos(105, 63, 100), "Talheim").orElseThrow();
        state.recognizeLegacyAlias(village.id(), VillageKey.overworld(100, 100));
        VillageBondService.ensureCanonicalVillageRecord(data, village.id(), "minecraft:overworld", 100, 100, VillageBondType.FORGE);
        data.setTradeRouteInt("route_count", 1); data.setTradeRouteInt("route_0_x", 100);
        data.setTradeRouteInt("route_0_z", 100); data.setTradeRouteInt("route_0_village_index", 1);
        data.setTradeRouteFlag("concord_claim", true);
        assertTrue(TradeRouteService.isRegisteredDestination(state, data, "minecraft:overworld", 105, 100));
        assertFalse(TradeRouteService.isRegisteredDestination(state, data, "minecraft:the_nether", 105, 100));
        assertEquals(100, data.getTradeRouteInt("route_0_x"));
        assertTrue(data.hasTradeRouteFlag("concord_claim"));
    }
}
