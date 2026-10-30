package de.quest.quest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-thread identities for transient encounters. Absence is never death. */
public final class EncounterEntityLifecycle {
    private static final Set<UUID> EXPECTED = new HashSet<>();
    private static final Set<UUID> DEAD = new HashSet<>();
    private static final Set<UUID> TERMINATED = new HashSet<>();
    private static final Set<String> TAGS = Set.of("vq_trade_road_caravan", "vq_trade_road_traitor",
            "vq_trade_road_hostile", "vq_empty_caravan_bait", "vq_empty_caravan_attacker", "vq_trade_route_attacker");
    private EncounterEntityLifecycle() {}
    /** Register before addFreshEntity: Fabric's load callback also runs for new spawns. */
    public static void expect(Entity entity) { EXPECTED.add(entity.getUUID()); }
    public static boolean spawn(ServerLevel world, Entity entity) {
        if (!world.noCollision(entity)) return false;
        expect(entity);
        if (world.addFreshEntity(entity)) return true;
        forget(entity.getUUID());
        return false;
    }
    public static void forget(UUID id) { EXPECTED.remove(id); DEAD.remove(id); TERMINATED.remove(id); }
    public static boolean defeated(UUID id, Entity entity) {
        return DEAD.contains(id) || entity instanceof LivingEntity living && living.isDeadOrDying();
    }
    public static boolean terminated(UUID id) { return TERMINATED.contains(id); }
    public static void onDeath(LivingEntity entity) {
        if (EXPECTED.contains(entity.getUUID())) DEAD.add(entity.getUUID());
    }
    public static void onLoad(Entity entity, ServerLevel world) {
        if (managed(entity) && (!EXPECTED.contains(entity.getUUID())
                || world.dimension() != net.minecraft.world.level.Level.OVERWORLD)) entity.discard();
    }
    public static void onUnload(Entity entity, ServerLevel world) {
        if (!EXPECTED.contains(entity.getUUID())) return;
        var reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            TERMINATED.add(entity.getUUID());
            if (reason == Entity.RemovalReason.KILLED) DEAD.add(entity.getUUID());
        }
    }
    /** Successful merchants may finish their ordinary timed farewell without encounter ownership. */
    public static void release(Entity entity) {
        for (String tag : TAGS) entity.removeTag(tag);
        forget(entity.getUUID());
    }
    private static boolean managed(Entity entity) {
        return entity.entityTags().stream().anyMatch(TAGS::contains);
    }
    public static void reset() { EXPECTED.clear(); DEAD.clear(); TERMINATED.clear(); }
}
