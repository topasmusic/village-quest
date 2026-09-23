package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TradeRouteDataTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyExplicitPlayerYardCountsAsPersonalHomestead() {
        PlayerQuestData data = new PlayerQuestData();
        TradeRouteData.bindVillageHome(data, 100, -20);

        assertFalse(TradeRouteData.isPlayerYardNear(data, new BlockPos(100, 64, -20), 16));

        TradeRouteData.bindPlayerYard(data, 100, -20);

        assertTrue(TradeRouteData.isPlayerYardNear(data, new BlockPos(112, 200, -20), 12));
        assertFalse(TradeRouteData.isPlayerYardNear(data, new BlockPos(113, 64, -20), 12));
    }

    @Test
    void malformedOrUnboundHomesteadStateFailsClosed() {
        PlayerQuestData data = new PlayerQuestData();

        assertFalse(TradeRouteData.isPlayerYardNear(null, BlockPos.ZERO, 16));
        assertFalse(TradeRouteData.isPlayerYardNear(data, BlockPos.ZERO, 16));
        TradeRouteData.bindPlayerYard(data, 0, 0);
        assertTrue(TradeRouteData.isPlayerYardNear(data, BlockPos.ZERO, -3));
    }

    @Test
    void homesteadProximityOnlyMatchesTheOverworldDimension() {
        PlayerQuestData data = new PlayerQuestData();
        TradeRouteData.bindPlayerYard(data, 100, -20);
        BlockPos sameCoordinates = new BlockPos(100, 64, -20);

        assertTrue(TradeRouteService.isNearPlayerYard(
                data, Level.OVERWORLD, sameCoordinates, 16));
        assertFalse(TradeRouteService.isNearPlayerYard(
                data, Level.NETHER, sameCoordinates, 16));
    }

    @Test
    void undergroundAndElevatedYardsPreserveTheirPhysicalAnchor() {
        PlayerQuestData underground = new PlayerQuestData();
        TradeRouteData.bindPlayerYard(underground, 20, -32, 40);
        PlayerQuestData elevated = new PlayerQuestData();
        TradeRouteData.bindPlayerYard(elevated, -10, 144, 12);

        assertEquals(new BlockPos(20, -32, 40), TradeRouteData.physicalHomeAnchor(underground));
        assertEquals(new BlockPos(-10, 144, 12), TradeRouteData.physicalHomeAnchor(elevated));
    }

    @Test
    void legacyHomeHasNoInventedPhysicalElevation() {
        PlayerQuestData data = new PlayerQuestData();
        TradeRouteData.bindPlayerYard(data, 20, 40);

        assertFalse(TradeRouteData.hasPhysicalHomeAnchor(data));
        assertEquals(null, TradeRouteData.physicalHomeAnchor(data));
    }

    @Test
    void removingRouteCompactsIncidentClockAndOwnerFinalizationStage() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_count", 2);
        data.setTradeRouteInt("route_0_event_online_seconds", 111);
        data.setTradeRouteInt("route_1_event_online_seconds", 222);
        data.setTradeRouteInt("route_1_event_progress", 2);

        assertTrue(TradeRouteData.removeRoute(data, 0, TradeRouteService.MAX_ROUTES));

        assertEquals(222, data.getTradeRouteInt("route_0_event_online_seconds"));
        assertEquals(2, data.getTradeRouteInt("route_0_event_progress"));
        assertEquals(0, data.getTradeRouteInt("route_1_event_online_seconds"));
    }
}
