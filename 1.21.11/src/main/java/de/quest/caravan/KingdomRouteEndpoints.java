package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.data.PlayerQuestData;
import de.quest.kingdom.KingdomVillageService;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.core.BlockPos;

/** Physical survey snapshots coexist with immutable legacy home/destination identity coordinates. */
final class KingdomRouteEndpoints {
    private KingdomRouteEndpoints() {}

    static void captureValidated(PlayerQuestData data, int route, BlockPos home, BlockPos destination) {
        if (data == null || route < 0 || route >= Math.min(TradeRouteService.MAX_ROUTES,
                data.getTradeRouteInt("route_count")) || !valid(home) || !valid(destination)) {
            throw new IllegalArgumentException("Invalid validated route endpoints");
        }
        write(data, route, "anchor_home", home);
        write(data, route, "anchor_destination", destination);
    }

    static RoutePoint pathHome(PlayerQuestData data, int route) {
        RoutePoint saved = read(data, route, "anchor_home");
        if (saved != null) return saved;
        BlockPos yard = TradeRouteData.physicalHomeAnchor(data);
        if (yard != null) return point(yard);
        int x = data.getTradeRouteInt("home_x"), z = data.getTradeRouteInt("home_z");
        return TradeRouteSurveyData.hasV2Geometry(data, route)
                ? new RoutePoint(x, TradeRouteSurveyData.routeHomeElevation(data, route), z) : new RoutePoint(x, z);
    }

    static RoutePoint pathDestination(PlayerQuestData data, int route) {
        RoutePoint saved = read(data, route, "anchor_destination");
        if (saved != null) return saved;
        int x = TradeRouteData.routeInt(data, route, "x"), z = TradeRouteData.routeInt(data, route, "z");
        return TradeRouteSurveyData.hasV2Geometry(data, route)
                ? new RoutePoint(x, TradeRouteSurveyData.routeDestinationElevation(data, route), z) : new RoutePoint(x, z);
    }

    static RoutePoint surveyHome(LivingKingdomsState state, PlayerQuestData data, String dimension, int route) {
        if (TradeRouteData.isPlayerYard(data)) {
            BlockPos yard = TradeRouteData.physicalHomeAnchor(data);
            return yard == null ? pathHome(data, route) : point(yard);
        }
        return KingdomVillageService.arrival(state, new VillageKey(dimension,
                data.getTradeRouteInt("home_x"), data.getTradeRouteInt("home_z")))
                .map(KingdomRouteEndpoints::point).orElseGet(() -> pathHome(data, route));
    }

    static RoutePoint surveyDestination(LivingKingdomsState state, PlayerQuestData data, String dimension, int route) {
        return KingdomVillageService.arrival(state, new VillageKey(dimension,
                TradeRouteData.routeInt(data, route, "x"), TradeRouteData.routeInt(data, route, "z")))
                .map(KingdomRouteEndpoints::point).orElseGet(() -> pathDestination(data, route));
    }

    static boolean requiresResurvey(LivingKingdomsState state, PlayerQuestData data, String dimension, int route) {
        return !surveyHome(state, data, dimension, route).equals(pathHome(data, route))
                || !surveyDestination(state, data, dimension, route).equals(pathDestination(data, route));
    }

    private static RoutePoint point(BlockPos pos) { return new RoutePoint(pos.getX(), pos.getY(), pos.getZ()); }

    private static RoutePoint read(PlayerQuestData data, int route, String suffix) {
        String prefix = TradeRouteData.routeKey(route, suffix);
        if (!data.hasTradeRouteFlag(prefix)) return null;
        BlockPos pos = new BlockPos(data.getTradeRouteInt(prefix + "_x"), data.getTradeRouteInt(prefix + "_y"),
                data.getTradeRouteInt(prefix + "_z"));
        return valid(pos) ? point(pos) : null;
    }

    private static void write(PlayerQuestData data, int route, String suffix, BlockPos pos) {
        String prefix = TradeRouteData.routeKey(route, suffix);
        data.setTradeRouteInt(prefix + "_x", pos.getX()); data.setTradeRouteInt(prefix + "_y", pos.getY());
        data.setTradeRouteInt(prefix + "_z", pos.getZ()); data.setTradeRouteFlag(prefix, true);
    }

    private static boolean valid(BlockPos pos) {
        return pos != null && Math.abs((long) pos.getX()) <= 30_000_000L
                && Math.abs((long) pos.getZ()) <= 30_000_000L && Math.abs((long) pos.getY()) <= 4096L;
    }
}
