package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Read-only direct connections. A player yard and two spokes never prove a village pair. */
public final class KingdomRouteConnections {
    /** Authoritative saved route deletion; absent owner/home, malformed rows and temporary pauses stay unknown. */
    public static boolean permanentlyMissing(PlayerQuestData data,UUID id) {
        if(data==null || id==null || !TradeRouteData.hasHome(data)) return false;
        int count=data.getTradeRouteInt("route_count");
        if(count<0 || count>TradeRouteService.MAX_ROUTES) return false;
        for(int i=0;i<count;i++) {
            var current=TradeRouteData.connectionId(data,i);
            if(current==null || current.equals(id)) return false;
        }
        return true;
    }
    private KingdomRouteConnections() {}
    public record Connection(UUID owner,UUID id,UUID home,UUID destination,String reason,int distance,int quality) {
        public Connection(UUID owner,UUID id,UUID home,UUID destination,String reason) { this(owner,id,home,destination,reason,0,50); }
        public boolean active() { return reason.equals("active"); }
        public boolean joins(UUID a,UUID b) { return home.equals(a)&&destination.equals(b)||home.equals(b)&&destination.equals(a); }
    }
    public static Optional<Connection> route(LivingKingdomsState state,VillageLifeState life,UUID owner,PlayerQuestData data,int index) {
        if(data==null || index<0 || index>=Math.min(TradeRouteService.MAX_ROUTES,data.getTradeRouteInt("route_count"))) return Optional.empty();
        UUID id=TradeRouteData.connectionId(data,index);
        return id==null?Optional.empty():read(state,life,owner,data).stream().filter(c -> c.id().equals(id)).findFirst();
    }
    public static List<Connection> read(LivingKingdomsState state,VillageLifeState life,UUID owner,PlayerQuestData data) {
        return life==null?List.of():read(state,owner,data,life::status);
    }
    static List<Connection> read(LivingKingdomsState state,UUID owner,PlayerQuestData data,
                                 java.util.function.Function<VillageLifeState.VillageKey,VillageLifeState.Status> status) {
        if(state==null || owner==null || data==null || !TradeRouteData.hasHome(data) || TradeRouteData.isPlayerYard(data)) return List.of();
        String dimension="minecraft:overworld";
        var home=state.byLegacy(new VillageLifeState.VillageKey(dimension,data.getTradeRouteInt("home_x"),data.getTradeRouteInt("home_z")));
        if(home.isEmpty()) return List.of();
        var result=new ArrayList<Connection>(); var occurrences=new HashMap<UUID,Integer>();
        int count=Math.min(TradeRouteService.MAX_ROUTES,Math.max(0,data.getTradeRouteInt("route_count")));
        for(int i=0;i<count;i++) { var id=TradeRouteData.connectionId(data,i); if(id!=null) occurrences.merge(id,1,Integer::sum); }
        for(int i=0;i<count;i++) {
            var id=TradeRouteData.connectionId(data,i); if(id==null || id.equals(new UUID(0,0)) || occurrences.getOrDefault(id,0)!=1) continue;
            var target=state.byLegacy(new VillageLifeState.VillageKey(dimension,TradeRouteData.routeInt(data,i,"x"),TradeRouteData.routeInt(data,i,"z")));
            if(target.isEmpty() || target.get().id().equals(home.get().id())) continue;
            var reason=TradeRouteAvailability.reason(TradeRouteData.isStopped(data,i),TradeRouteSurveyData.activeSurveyIndex(data)==i,
                    TradeRouteData.event(data,i)!=null,false,status.apply(home.get().legacyKey()),status.apply(target.get().legacyKey()));
            if(!reason.suspendsForSettlement() && reason!=TradeRouteAvailability.Reason.SURVEY_IN_PROGRESS
                    && KingdomRouteEndpoints.requiresResurvey(state,data,dimension,i)) reason=TradeRouteAvailability.Reason.ANCHOR_MOVED;
            result.add(new Connection(owner,id,home.get().id(),target.get().id(),reason.name().toLowerCase(Locale.ROOT),
                    TradeRouteService.routeTraversalDistanceBlocks(data,i),TradeRouteData.quality(data,i)));
        }
        return List.copyOf(result);
    }
    public static List<Connection> read(MinecraftServer server) {
        var result=new ArrayList<Connection>(); var state=LivingKingdomsState.get(server); var life=VillageLifeState.get(server);
        QuestState.get(server).getPlayersView().forEach((owner,data) -> result.addAll(read(state,life,owner,data)));
        return List.copyOf(result);
    }
}
