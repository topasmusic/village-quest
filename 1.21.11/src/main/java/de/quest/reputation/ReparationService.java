package de.quest.reputation;

import de.quest.caravan.TradeRouteService;
import de.quest.data.QuestState;
import de.quest.entity.QuestMasterEntity;
import de.quest.registry.ModBlocks;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ordinary local aid, with no economy, professional reputation or permanent bond reward. */
public final class ReparationService {
    private ReparationService() {}
    public enum AnchorKind { BOARD, QUESTMASTER }
    public record InteractionAnchor(AnchorKind kind, String dimension, BlockPos position, UUID entityId) {}
    public record AidLine(MaterialOption material, int supplied, int required, int carried) {}
    public record ReparationView(UUID caseId, long revision, boolean major, int ticksRemaining, MaterialOption selected,
                                 java.util.List<AidLine> materials) {
        public ReparationView { materials = java.util.List.copyOf(materials); }
    }
    public record Submission(boolean applied, Map<MaterialOption, Integer> accepted, String reasonKey) {
        public Submission { accepted = Map.copyOf(accepted); }
    }
    public record ActionResult(boolean applied, String reasonKey, ReparationView view) {}

    private static boolean current(SocialReputationData data, UUID id, long revision) {
        return data.writable() && data.activeCase() != null && data.activeCase().id().equals(id) && data.revision() == revision;
    }
    public static boolean choose(SocialReputationData data, UUID id, long revision, MaterialOption selected) {
        return selected != null && current(data, id, revision) && data.selectAid(selected);
    }
    public static Submission submit(SocialReputationData data, UUID id, long revision, Map<MaterialOption, Integer> inventory) {
        return submit(data, id, revision, inventory, data.activeCase() == null ? 0 : data.activeCase().lastOffenceTick());
    }
    public static Submission submit(SocialReputationData data, UUID id, long revision, Map<MaterialOption, Integer> inventory, long tick) {
        if (!current(data, id, revision)) return new Submission(false, Map.of(), "reputation.village-quest.reparation.stale");
        Map<MaterialOption, Integer> accepted = new EnumMap<>(MaterialOption.class);
        for (MaterialOption material : MaterialOption.values()) {
            int amount = data.recordAid(material, Math.max(0, inventory.getOrDefault(material, 0)));
            if (amount > 0) accepted.put(material, amount);
        }
        if (accepted.isEmpty()) return new Submission(false, Map.of(), "reputation.village-quest.reparation.materials");
        complete(data, tick); return new Submission(true, accepted, "");
    }
    public static boolean complete(SocialReputationData data) {
        return complete(data, data.activeCase() == null ? 0 : data.activeCase().lastOffenceTick());
    }
    public static boolean complete(SocialReputationData data, long completionTick) {
        var active = data.activeCase();
        if (!data.writable() || active == null || active.onlineTicksRemaining() != 0 || !active.aidComplete()) return false;
        int before = data.guildTrust(); UUID id = active.id();
        Map<de.quest.village.VillageLifeState.VillageKey, Integer> beforeLocal = new java.util.HashMap<>();
        active.affected().forEach(village -> beforeLocal.put(village, data.localTrust(village)));
        data.closeCase();
        Map<de.quest.village.VillageLifeState.VillageKey, Integer> deltas = new java.util.HashMap<>();
        beforeLocal.forEach((village, value) -> { int delta = data.localTrust(village) - value; if (delta != 0) deltas.put(village, delta); });
        data.appendHistory(new SocialReputationData.HistoryEntry(id, "reparation.completed", before, data.guildTrust(), deltas, Math.max(active.lastOffenceTick(), completionTick)));
        return true;
    }
    public static boolean tick(SocialReputationData data, int ticks, boolean eligible) {
        return tick(data, ticks, eligible, data.activeCase() == null ? 0 : data.activeCase().lastOffenceTick() + Math.max(0, ticks));
    }
    public static boolean tick(SocialReputationData data, int ticks, boolean eligible, long completionTick) {
        if (!eligible || ticks <= 0 || data.activeCase() == null || !data.writable()) return false;
        int before = data.activeCase().onlineTicksRemaining(); data.advanceOnlineTicks(ticks);
        boolean completed = complete(data, completionTick); return completed || data.activeCase().onlineTicksRemaining() != before;
    }
    public static boolean beginReconciliation(SocialReputationData data, long tick) {
        if (!data.writable() || data.activeCase() != null) return false;
        Set<VillageKey> affected = data.localTrustView().entrySet().stream().filter(entry -> entry.getValue() <= -20)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (data.guildTrust() > -20 && affected.isEmpty()) return false;
        UUID id = UUID.randomUUID();
        if (affected.isEmpty()) data.openCase(id, Offence.ASSAULT, null, tick);
        else affected.stream().sorted().forEach(village -> data.openCase(id, Offence.ASSAULT, village, tick));
        return true;
    }
    public static boolean pardon(SocialReputationData data, long tick) {
        if (!data.writable()) return false;
        if (data.activeCase() == null) beginReconciliation(data, tick);
        if (data.activeCase() == null) return false;
        int before = data.guildTrust(); UUID id = data.activeCase().id();
        Map<VillageKey, Integer> localBefore = new java.util.HashMap<>();
        data.activeCase().affected().forEach(village -> localBefore.put(village, data.localTrust(village)));
        data.closeCase();
        Map<VillageKey, Integer> deltas = new java.util.HashMap<>();
        localBefore.forEach((village, value) -> { int delta = data.localTrust(village) - value; if (delta != 0) deltas.put(village, delta); });
        data.appendHistory(new SocialReputationData.HistoryEntry(id, "admin.pardon", before, data.guildTrust(), deltas, tick)); return true;
    }

    public static boolean validAnchor(ServerPlayer player, InteractionAnchor anchor) {
        if (!SocialReputationService.enabled()) return false;
        if (player == null || anchor == null || anchor.position() == null || !player.isAlive() || player.isSpectator()) return false;
        var world = player.level();
        if (!world.dimension().identifier().toString().equals(anchor.dimension()) || !world.hasChunkAt(anchor.position())) return false;
        if (anchor.kind() == AnchorKind.QUESTMASTER) {
            var entity = anchor.entityId() == null ? null : world.getEntity(anchor.entityId());
            return entity instanceof QuestMasterEntity master && master.isAlive() && master.distanceToSqr(player) <= 64;
        }
        if (anchor.kind() != AnchorKind.BOARD || player.blockPosition().distSqr(anchor.position()) > 64
                || !world.getBlockState(anchor.position()).is(ModBlocks.GUILD_NOTICE_POST)) return false;
        if (TradeRouteService.isNearPlayerYard(world, player.getUUID(), anchor.position(), 24)) return true;
        var data = QuestState.get(world.getServer()).getPlayerData(player.getUUID()).socialReputation();
        var village = QuestState.get(world.getServer()).protectedVillages().resolve(world, anchor.position()).orElse(null);
        return village != null && (data.activeCase() != null
                ? data.activeCase().affected().contains(village) : data.localTrust(village) <= -20);
    }
    private static MaterialOption material(ItemStack stack) {
        if (stack.is(Items.WHEAT)) return MaterialOption.WHEAT;
        if (stack.is(ItemTags.PLANKS)) return MaterialOption.PLANKS;
        return stack.is(Items.IRON_INGOT) ? MaterialOption.IRON : null;
    }
    private static Map<MaterialOption, Integer> inventory(ServerPlayer player) {
        Map<MaterialOption, Integer> counts = new EnumMap<>(MaterialOption.class); var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i); MaterialOption material = material(stack);
            if (material != null) counts.merge(material, stack.getCount(), Integer::sum);
        } return counts;
    }
    public static ReparationView view(ServerPlayer player) {
        var data = QuestState.get(player.level().getServer()).getPlayerData(player.getUUID()).socialReputation();
        var active = data.activeCase(); if (active == null) return null;
        var inventory = inventory(player); java.util.List<AidLine> lines = new java.util.ArrayList<>();
        for (MaterialOption material : MaterialOption.values()) if (active.required(material) > 0)
            lines.add(new AidLine(material, active.delivered(material), active.required(material), inventory.getOrDefault(material, 0)));
        return new ReparationView(active.id(), data.revision(), active.major(), active.onlineTicksRemaining(), active.selected(), lines);
    }
    public static ReparationView open(ServerPlayer player, InteractionAnchor anchor) {
        if (!validAnchor(player, anchor)) return null;
        QuestState state = QuestState.get(player.level().getServer());
        if (beginReconciliation(state.getPlayerData(player.getUUID()).socialReputation(), state.socialServerTick())) state.setDirty();
        return view(player);
    }
    public static ActionResult choose(ServerPlayer player, UUID id, long revision, MaterialOption material, InteractionAnchor anchor) {
        if (!validAnchor(player, anchor)) return new ActionResult(false, "reputation.village-quest.reparation.anchor", view(player));
        QuestState state = QuestState.get(player.level().getServer());
        boolean changed = choose(state.getPlayerData(player.getUUID()).socialReputation(), id, revision, material);
        if (changed) state.setDirty(); return new ActionResult(changed, changed ? "" : "reputation.village-quest.reparation.stale", view(player));
    }
    public static ActionResult submit(ServerPlayer player, UUID id, long revision, InteractionAnchor anchor) {
        if (!validAnchor(player, anchor)) return new ActionResult(false, "reputation.village-quest.reparation.anchor", view(player));
        QuestState state = QuestState.get(player.level().getServer()); var data = state.getPlayerData(player.getUUID()).socialReputation();
        Submission result = submit(data, id, revision, inventory(player), state.socialServerTick());
        if (result.applied()) {
            Map<MaterialOption, Integer> remaining = new EnumMap<>(MaterialOption.class); remaining.putAll(result.accepted());
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                var stack = inventory.getItem(i); MaterialOption material = material(stack);
                if (material == null) continue;
                int take = Math.min(stack.getCount(), remaining.getOrDefault(material, 0));
                stack.shrink(take); remaining.put(material, remaining.getOrDefault(material, 0) - take);
            }
            inventory.setChanged(); player.inventoryMenu.broadcastChanges(); state.setDirty();
        }
        return new ActionResult(result.applied(), result.reasonKey(), view(player));
    }
    public static void onServerTick(MinecraftServer server) {
        if (!SocialReputationService.enabled()) return;
        QuestState state = QuestState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var data = state.getPlayerData(player.getUUID()).socialReputation();
            boolean hadCase = data.activeCase() != null;
            tick(data, 1, player.isAlive() && !player.isSpectator(), state.socialServerTick());
            if (hadCase && data.activeCase() == null) state.setDirty();
            if (hadCase && data.activeCase() == null) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "reputation.village-quest.reparation.completed"), false);
        }
    }
}
