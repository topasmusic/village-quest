package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import java.util.UUID;

/** Names belong to the saved connection, not to spawned merchant entities. */
final class CaravanCrewData {
    private static final String[] FIRST_NAMES = {
            "Mira", "Elric", "Torren", "Hanna", "Bram", "Cira", "Alda", "Doran",
            "Esme", "Fenn", "Rowan", "Liora", "Kellan", "Nessa", "Osric", "Tilda",
            "Marin", "Soren", "Edric", "Anwen", "Beric", "Lyra", "Corin", "Mara"
    };
    private static final String[] SURNAMES = {
            "Bellweather", "Fenner", "Ashford", "Westmere", "Cartwright", "Wayfarer",
            "Oakridge", "Fairbrook", "Hearthwick", "Stonewell", "Bracken", "Thornfield",
            "Riverbend", "Mosswood", "Dunford", "Briarwood", "Ashbrook", "Whitlock",
            "Meadowcroft", "Vale", "Kestrel", "Brambleford", "Alderwick", "Northfield"
    };

    private CaravanCrewData() {}

    static String replacementName(UUID id, String previous) {
        int first = Math.floorMod(id.hashCode(), FIRST_NAMES.length);
        int surname = Math.floorMod((int) id.getMostSignificantBits(), SURNAMES.length);
        String name = FIRST_NAMES[first] + " " + SURNAMES[surname];
        return name.equals(previous) ? FIRST_NAMES[first] + " " + SURNAMES[(surname + 1) % SURNAMES.length] : name;
    }

    static Crew ensure(PlayerQuestData data, int routeIndex) {
        if (data == null || routeIndex < 0 || routeIndex >= data.getTradeRouteInt("route_count")) {
            return new Crew("", "", "", "");
        }
        UUID connection = TradeRouteData.ensureConnectionId(data, routeIndex);
        String[] names = new String[CaravanRole.values().length];
        int firstSeed = Math.floorMod((int) (connection.getMostSignificantBits()
                ^ connection.getLeastSignificantBits()), FIRST_NAMES.length);
        int surnameSeed = Math.floorMod((int) (connection.getMostSignificantBits() >>> 32
                ^ connection.getLeastSignificantBits()), SURNAMES.length);
        for (CaravanRole role : CaravanRole.values()) {
            String key = TradeRouteData.routeKey(routeIndex,
                    "crew_" + role.name().toLowerCase(java.util.Locale.ROOT) + "_name");
            String name = data.getTradeRouteString(key);
            if (name.isBlank()) {
                name = FIRST_NAMES[(firstSeed + role.ordinal() * 7) % FIRST_NAMES.length] + " "
                        + SURNAMES[(surnameSeed + role.ordinal() * 11) % SURNAMES.length];
                data.setTradeRouteString(key, name);
            }
            names[role.ordinal()] = name;
        }
        return new Crew(names[0], names[1], names[2], names[3]);
    }

    static boolean isComplete(PlayerQuestData data, int routeIndex) {
        if (data == null) return false;
        for (CaravanRole role : CaravanRole.values()) {
            if (data.getTradeRouteString(TradeRouteData.routeKey(routeIndex,
                    "crew_" + role.name().toLowerCase(java.util.Locale.ROOT) + "_name")).isBlank()) {
                return false;
            }
        }
        return true;
    }

    record Crew(String master, String trader, String guard, String courier) {
        String name(CaravanRole role) {
            return switch (role) {
                case MASTER -> master;
                case TRADER -> trader;
                case GUARD -> guard;
                case COURIER -> courier;
            };
        }
    }
}
