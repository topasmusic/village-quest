package de.quest.caravan;

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

final class KingdomRouteEndpointsTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void validatedPhysicalTargetsPreserveLegacyIdentityConnectionAndWaypoints() {
        var data = data();
        UUID connection = TradeRouteData.ensureConnectionId(data, 0);
        data.setTradeRouteInt("route_0_waypoint_count", 1);
        data.setTradeRouteInt("route_0_waypoint_0_x", 50);
        data.setTradeRouteFlag("concord_claim", true);
        KingdomRouteEndpoints.captureValidated(data, 0, new BlockPos(2, -20, 0), new BlockPos(104, -20, 0));
        assertEquals(0, data.getTradeRouteInt("home_x"));
        assertEquals(100, data.getTradeRouteInt("route_0_x"));
        assertEquals(connection, TradeRouteData.connectionId(data, 0));
        assertEquals(50, data.getTradeRouteInt("route_0_waypoint_0_x"));
        assertTrue(data.hasTradeRouteFlag("concord_claim"));
        var home = KingdomRouteEndpoints.pathHome(data, 0);
        var end = KingdomRouteEndpoints.pathDestination(data, 0);
        assertEquals(2, home.x()); assertEquals(-20, home.y());
        assertEquals(104, end.x()); assertEquals(-20, end.y());
    }

    @Test void councilMoveRequiresResurveyAndReadOnlyComparisonNeverRetargetsSavedPath() {
        var state = new LivingKingdomsState(); var data = data();
        UUID owner = UUID.randomUUID(), town = UUID.randomUUID();
        state.registerCustom(town, owner, VillageKey.overworld(100, 0), new BlockPos(100, -20, 0), "Talheim");
        state.confirmZone(owner, town, 0, new LivingKingdomsState.Zone(92, 108, -24, -16, -8, 8));
        assertTrue(KingdomRouteEndpoints.requiresResurvey(state, data, "minecraft:overworld", 0));
        KingdomRouteEndpoints.captureValidated(data, 0, new BlockPos(0, 64, 0), new BlockPos(100, -20, 0));
        assertFalse(KingdomRouteEndpoints.requiresResurvey(state, data, "minecraft:overworld", 0));
        state.moveCouncil(owner, town, 1, new BlockPos(104, -20, 0));
        assertTrue(KingdomRouteEndpoints.requiresResurvey(state, data, "minecraft:overworld", 0));
        assertEquals(100, KingdomRouteEndpoints.pathDestination(data, 0).x());
        assertEquals(104, KingdomRouteEndpoints.surveyDestination(state, data, "minecraft:overworld", 0).x());
        assertEquals(100, data.getTradeRouteInt("route_0_x"));
    }

    @Test void unboundLegacyRoutesStayHorizontalAndPlayerYardIgnoresVillageCouncil() {
        var state = new LivingKingdomsState(); var data = data();
        state.registerGenerated(VillageKey.overworld(100, 0), new BlockPos(100, 63, 0), "Talheim");
        assertFalse(KingdomRouteEndpoints.requiresResurvey(state, data, "minecraft:overworld", 0));
        assertFalse(KingdomRouteEndpoints.pathDestination(data, 0).hasElevation());
        UUID owner = UUID.randomUUID(), home = UUID.randomUUID();
        state.registerCustom(home, owner, VillageKey.overworld(0, 0), new BlockPos(0, -20, 0), "Lindenau");
        state.confirmZone(owner, home, 0, new LivingKingdomsState.Zone(-8, 8, -24, -16, -8, 8));
        TradeRouteData.bindPlayerYard(data, 0, 70, 0);
        assertEquals(70, KingdomRouteEndpoints.surveyHome(state, data, "minecraft:overworld", 0).y());
        assertFalse(KingdomRouteEndpoints.requiresResurvey(state, data, "minecraft:overworld", 0));
    }

    private static PlayerQuestData data() {
        var data = new PlayerQuestData();
        TradeRouteData.bindVillageHome(data, 0, 0);
        data.setTradeRouteInt("route_count", 1);
        data.setTradeRouteInt("route_0_x", 100); data.setTradeRouteInt("route_0_z", 0);
        return data;
    }

    @Test void relocatedPlayerYardUsesItsCurrentAnchorWithoutRewritingCapturedRoute() {
        var state = new LivingKingdomsState(); var data = data();
        TradeRouteData.bindPlayerYard(data, 0, 70, 0);
        KingdomRouteEndpoints.captureValidated(data, 0, new BlockPos(0, 70, 0), new BlockPos(100, 64, 0));
        TradeRouteData.bindPlayerYard(data, 5, 72, 0);
        assertTrue(KingdomRouteEndpoints.requiresResurvey(state, data, "minecraft:overworld", 0));
        assertEquals(5, KingdomRouteEndpoints.surveyHome(state, data, "minecraft:overworld", 0).x());
        assertEquals(0, KingdomRouteEndpoints.pathHome(data, 0).x());
    }
}
