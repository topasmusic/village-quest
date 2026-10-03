package de.quest.reputation;

import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import de.quest.data.QuestState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.function.BiFunction;
import java.util.HashMap;
import java.util.Map;

/** Evidence contains the actual accepted health/absorption loss, never requested weapon damage. */
public final class ReputationDamageAdapter {
    private ReputationDamageAdapter() {}
    private static boolean active;
    private static long lastCauseSweep = -1;
    private static final ReputationNotificationQueue NOTIFICATIONS = new ReputationNotificationQueue();
    private static BiFunction<ServerLevel, LivingEntity, ProtectedVictim> crewResolver = (world, entity) -> null;
    private static final Map<UUID, RecentCause> recentCauses = new HashMap<>();
    public record ProtectedVictim(UUID logicalId, VictimKind kind, VillageKey village, UUID owner, UUID connection) {
        public ProtectedVictim(UUID logicalId, VictimKind kind, VillageKey village) { this(logicalId, kind, village, null, null); }
    }
    private record RecentCause(UUID attacker, long tick) {}
    public static final class DamageFrame {
        public final ProtectedVictim victim;
        public final UUID attacker;
        public final float health, absorption;
        public float childDamage;
        public float actualDamage;
        public boolean measuringDamage;
        public boolean fatalConfirmed;
        private DamageFrame(ProtectedVictim victim, UUID attacker, LivingEntity entity) {
            this.victim = victim; this.attacker = attacker; health = entity.getHealth(); absorption = entity.getAbsorptionAmount();
        }
    }
    /** Activated only after consequence/reparation/UI integration is ready. */
    public static void activate(BiFunction<ServerLevel, LivingEntity, ProtectedVictim> resolver) {
        crewResolver = resolver; active = true;
    }
    public static void resetRuntime() { recentCauses.clear(); lastCauseSweep = -1; NOTIFICATIONS.clear(); }
    public static DamageFrame begin(ServerLevel world, LivingEntity entity, DamageSource source) {
        if (!active || !SocialReputationService.enabled()) return null;
        ProtectedVictim victim = crewResolver.apply(world, entity);
        if (victim == null && entity instanceof Villager) {
            VillageKey village = QuestState.get(world.getServer()).protectedVillages().resolve(world, entity.blockPosition()).orElse(null);
            if (village != null) victim = new ProtectedVictim(entity.getUUID(), VictimKind.VILLAGER, village);
        }
        if (victim == null) return null;
        return new DamageFrame(victim, attacker(source), entity);
    }
    public static void finish(ServerLevel world, LivingEntity entity, DamageSource source, DamageFrame frame, float damage) {
        if (damage <= 0 || !Float.isFinite(damage)) return;
        QuestState state = QuestState.get(world.getServer()); long tick = state.socialServerTick();
        UUID attacker = frame.attacker; boolean fatal = frame.fatalConfirmed;
        boolean environmentalFollowup = source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
        if (attacker == null && fatal && environmentalFollowup) {
            RecentCause previous = recentCauses.get(entity.getUUID());
            if (previous != null && tick >= previous.tick() && tick - previous.tick() <= 200) attacker = previous.attacker();
        }
        if (lastCauseSweep < 0 || tick - lastCauseSweep >= 20) {
            recentCauses.entrySet().removeIf(entry -> tick > entry.getValue().tick() + 200);
            lastCauseSweep = tick;
        }
        ServerPlayer player = attacker == null ? null : world.getServer().getPlayerList().getPlayer(attacker);
        if (player != null && (player.isSpectator() || player.isCreative()
                && !de.quest.config.VillageQuestServerConfig.get().socialReputation().trackCreative())) {
            recentCauses.remove(entity.getUUID()); return;
        }
        if (!fatal && attacker != null) recentCauses.put(entity.getUUID(), new RecentCause(attacker, tick));
        else if (fatal || !environmentalFollowup) recentCauses.remove(entity.getUUID());
        // An unattributed fire/fall tick does not refresh or erase a still-recent player cause.
        // The original 200-tick deadline remains in force; other damage sources invalidate it.
        if (attacker == null) return;
        var result = ReputationIncidentService.record(world.getServer(), new DamageEvidence(attacker,
                frame.victim.logicalId(), frame.victim.kind(), frame.victim.village(), damage, fatal, tick));
        if (result.outcome() != ReputationIncidentService.Outcome.IGNORED)
            de.quest.caravan.TradeRouteService.reactToAggression(world, frame.victim, result);
        if (player != null && result.outcome() != ReputationIncidentService.Outcome.IGNORED) {
            var message = result.outcome() == ReputationIncidentService.Outcome.FATAL
                    ? net.minecraft.network.chat.Component.translatable("reputation.village-quest.incident.fatal_named", entity.getDisplayName())
                    : net.minecraft.network.chat.Component.translatable("reputation.village-quest.incident." + result.outcome().name().toLowerCase(java.util.Locale.ROOT));
            if (result.guildDelta() != 0) message.append(net.minecraft.network.chat.Component.translatable("reputation.village-quest.incident.points", result.guildDelta()));
            NOTIFICATIONS.offer(player.getUUID(), result.outcome() == ReputationIncidentService.Outcome.FATAL ? 2 : result.outcome() == ReputationIncidentService.Outcome.ASSAULT ? 1 : 0, message);
        }
    }
    public static void forgetPlayer(UUID player) { NOTIFICATIONS.remove(player); }
    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        long tick = QuestState.get(server).socialServerTick();
        for (UUID id : NOTIFICATIONS.pendingPlayers()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) { NOTIFICATIONS.remove(id); continue; }
            var message = NOTIFICATIONS.poll(id, tick); if (message != null) player.sendSystemMessage(message, false);
        }
    }
    public enum VictimKind { CREW, VILLAGER, MULE }
    public record DamageEvidence(UUID attacker, UUID logicalVictim, VictimKind kind, VillageKey village,
                                 float acceptedDamage, boolean fatal, long serverTick) {}
    public static float acceptedDamage(float healthBefore, float absorptionBefore, float healthAfter, float absorptionAfter, boolean accepted) {
        if (!accepted) return 0;
        float damage = Math.max(0, healthBefore - healthAfter) + Math.max(0, absorptionBefore - absorptionAfter);
        return Float.isFinite(damage) ? damage : 0;
    }
    public static UUID attacker(DamageSource source) {
        if (source == null) return null;
        // The current causing entity takes priority; never use an entity's stale lastHurtByPlayer.
        Entity cause = source.getEntity();
        return playerOwner(cause != null ? cause : source.getDirectEntity(), 0);
    }
    private static UUID playerOwner(Entity entity, int depth) {
        if (entity == null || depth > 4) return null;
        if (entity instanceof ServerPlayer player) return player.getUUID();
        if (entity instanceof TamableAnimal animal) return playerOwner(animal.getOwner(), depth + 1);
        if (entity instanceof Projectile projectile) return playerOwner(projectile.getOwner(), depth + 1);
        if (entity instanceof PrimedTnt tnt) return playerOwner(tnt.getOwner(), depth + 1);
        return null;
    }
}
