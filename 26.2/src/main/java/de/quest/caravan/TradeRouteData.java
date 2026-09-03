package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Central persistence vocabulary for the legacy trade-route key/value schema. */
final class TradeRouteData {
    private static final String HOME_X = "home_x";
    private static final String HOME_Z = "home_z";
    private static final String HOME_BOUND = "home_bound";
    private static final String HOME_PLAYER_YARD = "home_player_yard";
    private static final String ROUTE_PREFIX = "route_";

    private TradeRouteData() {}

    static void bindVillageHome(PlayerQuestData data, int x, int z) {
        data.setTradeRouteInt(HOME_X, x);
        data.setTradeRouteInt(HOME_Z, z);
        data.setTradeRouteFlag(HOME_BOUND, true);
        data.setTradeRouteFlag(HOME_PLAYER_YARD, false);
    }

    static void bindPlayerYard(PlayerQuestData data, int x, int z) {
        data.setTradeRouteInt(HOME_X, x);
        data.setTradeRouteInt(HOME_Z, z);
        data.setTradeRouteFlag(HOME_BOUND, true);
        data.setTradeRouteFlag(HOME_PLAYER_YARD, true);
    }

    static void clearRouteEntries(PlayerQuestData data) {
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.startsWith(ROUTE_PREFIX)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        for (String flag : List.copyOf(data.getTradeRouteFlags())) {
            if (flag.startsWith(ROUTE_PREFIX)) {
                data.setTradeRouteFlag(flag, false);
            }
        }
        for (String key : List.copyOf(data.getTradeRouteStringState().keySet())) {
            if (key.startsWith(ROUTE_PREFIX)) {
                data.setTradeRouteString(key, "");
            }
        }
    }

    static void copyRouteEntries(PlayerQuestData data,
                                 Map<String, Integer> savedInts,
                                 Map<String, String> savedStrings,
                                 Set<String> savedFlags,
                                 int sourceIndex,
                                 int targetIndex) {
        String sourcePrefix = ROUTE_PREFIX + sourceIndex + "_";
        String targetPrefix = ROUTE_PREFIX + targetIndex + "_";
        for (Map.Entry<String, Integer> entry : savedInts.entrySet()) {
            if (entry.getKey().startsWith(sourcePrefix)) {
                data.setTradeRouteInt(targetPrefix + entry.getKey().substring(sourcePrefix.length()), entry.getValue());
            }
        }
        for (String flag : savedFlags) {
            if (flag.startsWith(sourcePrefix)) {
                data.setTradeRouteFlag(targetPrefix + flag.substring(sourcePrefix.length()), true);
            }
        }
        for (Map.Entry<String, String> entry : savedStrings.entrySet()) {
            if (entry.getKey().startsWith(sourcePrefix)) {
                data.setTradeRouteString(targetPrefix + entry.getKey().substring(sourcePrefix.length()), entry.getValue());
            }
        }
    }

    static boolean hasHome(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(HOME_BOUND);
    }

    static boolean isPlayerYard(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(HOME_PLAYER_YARD);
    }

    static int quality(PlayerQuestData data, int routeIndex) {
        int quality = routeInt(data, routeIndex, "quality");
        return quality <= 0 ? 20 : Math.min(100, quality);
    }

    static int storedLivery(PlayerQuestData data, int routeIndex) {
        int stored = routeInt(data, routeIndex, "livery");
        return stored <= 0 ? Math.floorMod(routeIndex, TradeRouteService.MAX_ROUTES)
                : Math.max(0, Math.min(TradeRouteService.MAX_ROUTES - 1, stored - 1));
    }

    static TradeRouteStatus status(PlayerQuestData data, int routeIndex) {
        TradeRouteStatus status = TradeRouteStatus.byId(routeInt(data, routeIndex, "status"));
        return status == TradeRouteStatus.UNKNOWN ? TradeRouteStatus.DANGEROUS : status;
    }

    static TradeRouteEventType event(PlayerQuestData data, int routeIndex) {
        return TradeRouteEventType.byId(routeInt(data, routeIndex, "event"));
    }

    static boolean isStopped(PlayerQuestData data, int routeIndex) {
        return data.hasTradeRouteFlag(routeKey(routeIndex, "stopped"));
    }

    static int routeInt(PlayerQuestData data, int routeIndex, String suffix) {
        return data.getTradeRouteInt(routeKey(routeIndex, suffix));
    }

    static void setRouteInt(PlayerQuestData data, int routeIndex, String suffix, int value) {
        data.setTradeRouteInt(routeKey(routeIndex, suffix), value);
    }

    static String routeKey(int routeIndex, String suffix) {
        return ROUTE_PREFIX + routeIndex + "_" + suffix;
    }

    static Component routeName(int routeIndex) {
        return Component.translatable("text.village-quest.trade_route.name", routeIndex + 1);
    }

    static Component routeName(PlayerQuestData data, int routeIndex) {
        String custom = data == null ? "" : data.getTradeRouteString(routeKey(routeIndex, "name"));
        return custom.isBlank() ? routeName(routeIndex) : Component.literal(custom);
    }

    static Component villageName(PlayerQuestData data, int routeIndex) {
        String custom = data == null ? "" : data.getTradeRouteString(routeKey(routeIndex, "name"));
        return custom.isBlank()
                ? Component.translatable("text.village-quest.trade_route.node.village", routeIndex + 1)
                : Component.literal(custom);
    }

    static String sanitizeRouteName(String requestedName) {
        if (requestedName == null) {
            return "";
        }
        String clean = ChatFormatting.stripFormatting(requestedName).replaceAll("\\p{Cntrl}", "")
                .trim().replaceAll("\\s+", " ");
        if (clean.length() > 24) {
            clean = clean.substring(0, 24).trim();
        }
        return clean;
    }

    static int currentWorldDay(ServerLevel world) {
        return world == null ? 0 : (int) (world.getOverworldClockTime() / 24000L);
    }

    static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }
}
