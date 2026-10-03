package de.quest.caravan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Transient defensive response; only a confirmed personal assault/death may create one. */
public final class CaravanProtectionService {
    private CaravanProtectionService() {}
    private static final Map<UUID, Response> responses = new HashMap<>();
    public record Response(UUID connection, BlockPos anchor, Map<UUID, Long> attackers, Map<UUID, Long> warnings) {
        public Response { anchor = anchor.immutable(); attackers = Map.copyOf(attackers); warnings = Map.copyOf(warnings); }
    }
    public static Response start(UUID connection, UUID attacker, BlockPos anchor, long tick) {
        return new Response(connection, anchor, Map.of(attacker, tick), Map.of());
    }
    public static Response renew(Response previous, UUID attacker, long tick) {
        Map<UUID, Long> next = new HashMap<>(previous.attackers());
        next.entrySet().removeIf(entry -> tick >= entry.getValue() + 400); next.put(attacker, tick);
        return new Response(previous.connection(), previous.anchor(), next, previous.warnings());
    }
    public static boolean active(Response response, long tick) {
        return response != null && (response.attackers().values().stream().anyMatch(last -> tick >= last && tick - last < 400)
                || response.warnings().values().stream().anyMatch(last -> tick >= last && tick - last < 600));
    }
    public static boolean mayChase(Response response, UUID target, BlockPos guard, BlockPos targetPosition, long tick, boolean safeCorridor) {
        if (!safeCorridor || response == null || guard == null || targetPosition == null || target == null) return false;
        Long last = response.attackers().get(target);
        return last != null && tick >= last && tick - last < 400
                && response.anchor().distSqr(guard) <= 24 * 24 && response.anchor().distSqr(targetPosition) <= 24 * 24;
    }
    static void warn(UUID connection, UUID attacker, BlockPos anchor, long tick) {
        Response previous = responses.get(connection);
        Map<UUID, Long> warnings = new HashMap<>();
        if (active(previous, tick)) warnings.putAll(previous.warnings());
        warnings.entrySet().removeIf(entry -> tick >= entry.getValue() + 600); warnings.put(attacker, tick);
        responses.put(connection, new Response(connection, active(previous, tick) ? previous.anchor() : anchor,
                active(previous, tick) ? previous.attackers() : Map.of(), warnings));
    }
    static java.util.Set<UUID> threats(Response response, long tick) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        response.attackers().forEach((id, last) -> { if (tick >= last && tick - last < 400) ids.add(id); });
        response.warnings().forEach((id, last) -> { if (tick >= last && tick - last < 600) ids.add(id); });
        return ids;
    }
    static void react(UUID connection, UUID attacker, BlockPos anchor, long tick) {
        Response previous = responses.get(connection);
        responses.put(connection, active(previous, tick) ? renew(previous, attacker, tick) : start(connection, attacker, anchor, tick));
    }
    static Response response(UUID connection, long tick) {
        Response response = responses.get(connection);
        if (active(response, tick)) return response;
        responses.remove(connection); return null;
    }
    public static void resetRuntime() { responses.clear(); }
}
