package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import java.util.Locale;
import java.util.UUID;

/** Saved logical lives; discarding a rendered entity never calls recordDeath. */
public final class CaravanCrewLifecycle {
    private CaravanCrewLifecycle() {}
    private static boolean active;
    public static void activate() { active = true; }
    public static boolean mortalityEnabled() { return active && de.quest.config.VillageQuestServerConfig.get().socialReputation().caravanMortality(); }
    public static boolean protectedFromDamage() { return active && !de.quest.config.VillageQuestServerConfig.get().socialReputation().caravanMortality(); }
    /** null role is explicitly the pack mule, never an invented merchant role. */
    public record CrewMember(UUID id, int generation, CaravanRole role, String name, boolean dead) {}
    private static String key(int route, CaravanRole role, String field) {
        return TradeRouteData.routeKey(route, "crew_" + (role == null ? "mule" : role.name().toLowerCase(Locale.ROOT)) + "_" + field);
    }
    public static CrewMember resolve(PlayerQuestData data, int route, CaravanRole role) {
        if (data == null || route < 0 || route >= data.getTradeRouteInt("route_count")) throw new IllegalArgumentException("Invalid crew connection");
        UUID connection = TradeRouteData.ensureConnectionId(data, route);
        CaravanCrewData.ensure(data, route);
        UUID id;
        try { id = UUID.fromString(data.getTradeRouteString(key(route, role, "id"))); }
        catch (IllegalArgumentException missing) {
            id = UUID.nameUUIDFromBytes(("vq-crew-life-v1:" + connection + ":" + (role == null ? "mule" : role.name()))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            data.setTradeRouteString(key(route, role, "id"), id.toString());
        }
        String name = data.getTradeRouteString(key(route, role, "name"));
        if (role == null && name.isBlank()) {
            name = CaravanPackAnimalPolicy.name(connection); data.setTradeRouteString(key(route, null, "name"), name);
        }
        return new CrewMember(id, Math.max(0, data.getTradeRouteInt(key(route, role, "generation"))), role, name,
                data.hasTradeRouteFlag(key(route, role, "dead")));
    }
    public static CrewMember resolveMule(PlayerQuestData data, int route) { return resolve(data, route, null); }

    public static boolean recordDeath(PlayerQuestData data, int route, CaravanRole role, UUID memberId) {
        CrewMember current = resolve(data, route, role);
        if (current.dead() || !current.id().equals(memberId)) return false;
        data.setTradeRouteFlag(key(route, role, "dead"), true);
        data.setTradeRouteFlag(TradeRouteData.routeKey(route, "crew_journey_interrupted"), true); return true;
    }
    public static boolean onSafeDeparture(PlayerQuestData data, int route, boolean safe) {
        if (!safe) return false;
        boolean changed = false;
        for (CaravanRole role : CaravanRole.values()) changed |= replace(data, route, role);
        changed |= replace(data, route, null);
        if (changed) data.setTradeRouteFlag(TradeRouteData.routeKey(route, "crew_journey_interrupted"), false);
        return changed;
    }
    private static boolean replace(PlayerQuestData data, int route, CaravanRole role) {
        // Merely reaching a departure must not create absent optional roles or a mule.
        if (!data.hasTradeRouteFlag(key(route, role, "dead"))) return false;
        CrewMember previous = resolve(data, route, role); UUID id = UUID.randomUUID();
        String name;
        if (role == null) {
            name = CaravanPackAnimalPolicy.name(id);
            while (name.equals(previous.name())) { id = UUID.randomUUID(); name = CaravanPackAnimalPolicy.name(id); }
        } else name = CaravanCrewData.replacementName(id, previous.name());
        data.setTradeRouteString(key(route, role, "id"), id.toString());
        data.setTradeRouteString(key(route, role, "name"), name);
        data.setTradeRouteInt(key(route, role, "generation"), previous.generation() == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.generation() + 1);
        data.setTradeRouteFlag(key(route, role, "dead"), false); return true;
    }
}
