package de.quest.guildtown;

import de.quest.data.PlayerQuestData;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageRequestType;

/** One short follow-up per historical village, independent of route slots. */
public final class GuildTownAfterstoryLedger {
    private GuildTownAfterstoryLedger() {}

    public enum State { NOT_STARTED, ACTIVE, COMPLETE }

    private static String key(int villageIndex, String suffix) {
        return VillageBondService.villageKey(villageIndex, "afterstory_" + suffix);
    }

    public static State state(PlayerQuestData data, int villageIndex) {
        if (data == null || villageIndex < 0) return State.NOT_STARTED;
        return switch (data.getTradeRouteInt(key(villageIndex, "state"))) {
            case 1 -> State.ACTIVE;
            case 2 -> State.COMPLETE;
            default -> State.NOT_STARTED;
        };
    }

    public static VillageRequestType request(PlayerQuestData data, int villageIndex) {
        if (state(data, villageIndex) == State.NOT_STARTED) return null;
        int id = data.getTradeRouteInt(key(villageIndex, "request")) - 1;
        for (VillageRequestType request : VillageRequestType.values()) {
            if (request.id() == id) return request;
        }
        return null;
    }

    public static boolean begin(PlayerQuestData data, int villageIndex, VillageRequestType request) {
        if (data == null || villageIndex < 0 || request == null
                || state(data, villageIndex) != State.NOT_STARTED) return false;
        data.setTradeRouteInt(key(villageIndex, "request"), request.id() + 1);
        data.setTradeRouteInt(key(villageIndex, "state"), 1);
        return true;
    }

    public static boolean complete(PlayerQuestData data, int villageIndex) {
        if (state(data, villageIndex) != State.ACTIVE || request(data, villageIndex) == null) return false;
        data.setTradeRouteInt(key(villageIndex, "state"), 2);
        return true;
    }
}
