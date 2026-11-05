package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class KingdomRouteConnectionsTest {
    @Test void onlyAuthoritativelyDeletedOrReplacedIdsArePermanentLoss() {
        var data=new PlayerQuestData(); TradeRouteData.bindVillageHome(data,0,64,0); data.setTradeRouteInt("route_count",1); route(data,0,100);
        var old=TradeRouteData.connectionId(data,0);
        data.setTradeRouteFlag("route_0_stopped",true);
        assertFalse(KingdomRouteConnections.permanentlyMissing(data,old));
        assertFalse(KingdomRouteConnections.permanentlyMissing(null,old));
        data.setTradeRouteString("route_0_connection_id","");
        assertFalse(KingdomRouteConnections.permanentlyMissing(data,old));
        data.setTradeRouteString("route_0_connection_id",UUID.randomUUID().toString());
        assertTrue(KingdomRouteConnections.permanentlyMissing(data,old));
        data.setTradeRouteInt("route_count",0); assertTrue(KingdomRouteConnections.permanentlyMissing(data,old));
    }
    @Test void duplicateConnectionIdsCannotAttributeAConsumedDeliveryToAnotherDestination() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); town(state,owner,0); town(state,owner,100); town(state,owner,200);
        var data=new PlayerQuestData(); TradeRouteData.bindVillageHome(data,0,64,0); data.setTradeRouteInt("route_count",2);
        route(data,0,100); route(data,1,200);
        data.setTradeRouteString("route_1_connection_id",TradeRouteData.connectionId(data,0).toString());
        assertTrue(KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).isEmpty(),
                "Neither ambiguous route may supply pair, project or freight evidence");
    }
    @Test void directPairUsesStableConnectionWithoutInventingASpokeToSpokeConnection() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=town(state,owner,0); var b=town(state,owner,100); var c=town(state,owner,200);
        var data=new PlayerQuestData(); TradeRouteData.bindVillageHome(data,0,64,0); data.setTradeRouteInt("route_count",2);
        route(data,0,100); route(data,1,200); var id=TradeRouteData.ensureConnectionId(data,0);
        var rows=KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE);
        assertEquals(2,rows.size()); assertTrue(rows.stream().allMatch(KingdomRouteConnections.Connection::active));
        assertTrue(rows.getFirst().joins(a,b)); assertTrue(rows.getFirst().joins(b,a)); assertFalse(rows.stream().anyMatch(r -> r.joins(b,c)));
        assertEquals(id,rows.getFirst().id());
        TradeRouteData.copyRouteEntries(data,Map.copyOf(data.getTradeRouteIntState()),Map.copyOf(data.getTradeRouteStringState()),Set.copyOf(data.getTradeRouteFlags()),1,0);
        data.setTradeRouteInt("route_count",1);
        assertTrue(KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).getFirst().joins(a,c));
    }
    @Test void stoppedSurveyedOrMovedRoutesRemainVisibleWithTheirRealReason() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); town(state,owner,0); var b=town(state,owner,100);
        var data=new PlayerQuestData(); TradeRouteData.bindVillageHome(data,0,64,0); data.setTradeRouteInt("route_count",1); route(data,0,100);
        data.setTradeRouteFlag("route_0_stopped",true);
        assertEquals("player_paused",KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).getFirst().reason());
        data.setTradeRouteFlag("route_0_stopped",false); data.setTradeRouteInt("survey_route",1);
        assertEquals("survey_in_progress",KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).getFirst().reason());
        data.setTradeRouteInt("survey_route",0); state.moveCouncil(owner,b,1,new BlockPos(103,64,0));
        assertEquals("anchor_moved",KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).getFirst().reason());
    }
    @Test void yardsMissingIdsAndUnknownEndpointsNeverMintConnectionEvidence() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); town(state,owner,0); town(state,owner,100);
        var data=new PlayerQuestData(); TradeRouteData.bindVillageHome(data,0,64,0); data.setTradeRouteInt("route_count",1); route(data,0,100);
        data.setTradeRouteString("route_0_connection_id","");
        assertTrue(KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).isEmpty());
        assertEquals("",data.getTradeRouteString("route_0_connection_id"));
        TradeRouteData.ensureConnectionId(data,0); TradeRouteData.bindPlayerYard(data,0,64,0);
        assertTrue(KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).isEmpty());
        TradeRouteData.bindVillageHome(data,0,64,0); data.setTradeRouteInt("route_0_x",999);
        assertTrue(KingdomRouteConnections.read(state,owner,data,key -> VillageLifeState.Status.ACTIVE).isEmpty());
    }
    private static UUID town(LivingKingdomsState s,UUID owner,int x) {
        UUID id=UUID.randomUUID(); s.registerCustom(id,owner,VillageLifeState.VillageKey.overworld(x,0),new BlockPos(x,64,0),"Town "+x).orElseThrow(); s.confirmZone(owner,id,0,new LivingKingdomsState.Zone(x-8,x+8,60,68,-8,8)); return id;
    }
    private static void route(PlayerQuestData data,int slot,int x) {
        data.setTradeRouteInt("route_"+slot+"_x",x); TradeRouteData.ensureConnectionId(data,slot);
        KingdomRouteEndpoints.captureValidated(data,slot,new BlockPos(0,64,0),new BlockPos(x,64,0));
    }
}
