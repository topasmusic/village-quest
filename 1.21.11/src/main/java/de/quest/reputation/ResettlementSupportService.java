package de.quest.reputation;

import de.quest.data.QuestState;
import de.quest.reputation.SocialReputationData.SupportContribution;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.util.TimeUtil;
import de.quest.village.VillageLifeState;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import de.quest.caravan.TradeRouteService;
import de.quest.registry.ModBlocks;
import de.quest.shrine.VillageBondService;

/** A contribution never creates inhabitants or restores a settlement by itself. */
public final class ResettlementSupportService {
    private ResettlementSupportService() {}

    public record SupportOffer(UUID id, VillageKey village, int cycle, boolean accepted, boolean supplied, boolean barred) {}
    public record SubmissionResult(boolean applied, int wheat, int planks, String reasonKey) {}

    public static SupportOffer offer(ServerPlayer player, VillageKey village) {
        if (!SocialReputationService.enabled()) return null;
        if (player == null || village == null) return null;
        MinecraftServer server = player.level().getServer(); QuestState state = QuestState.get(server);
        var personal = state.getPlayerData(player.getUUID()); VillageLifeState life = VillageLifeState.get(server);
        int cycle = life.abandonmentCycle(village);
        if (cycle <= 0 || life.status(village) != VillageLifeState.Status.ABANDONED || !personal.socialReputation().writable()
                || VillageBondService.historicalVillageIndex(personal, village.dimension(), village.anchorX(), village.anchorZ()) < 0) return null;
        var contribution = personal.socialReputation().supportView().get(village);
        boolean accepted = contribution != null && contribution.cycle() == cycle;
        UUID id = SocialReputationService.instanceId(BenefitKind.RESETTLEMENT, player.getUUID(), village + ":" + cycle);
        return new SupportOffer(id, village, cycle, accepted, accepted && contribution.supplied(), accepted && contribution.barred());
    }

    private static boolean validBoard(ServerPlayer player, BlockPos board) {
        if (player == null || board == null || !player.isAlive() || player.isSpectator()) return false;
        var world = player.level();
        return world.hasChunkAt(board) && player.blockPosition().distSqr(board) <= 64
                && world.getBlockState(board).is(ModBlocks.GUILD_NOTICE_POST)
                && TradeRouteService.isNearPlayerYard(world, player.getUUID(), board, 24);
    }

    public static boolean accept(ServerPlayer player, UUID offerId, VillageKey village, BlockPos board) {
        if (!validBoard(player, board)) return false;
        SupportOffer offer = offer(player, village);
        if (offer == null || !offer.id().equals(offerId)) return false;
        QuestState state = QuestState.get(player.level().getServer());
        boolean accepted = accept(players(state), player.getUUID(), village, offer.cycle(), true);
        if (accepted) state.setDirty(); return accepted;
    }

    public static SubmissionResult submit(ServerPlayer player, UUID offerId, VillageKey village, BlockPos board) {
        SubmissionResult invalid = new SubmissionResult(false, 0, 0, "reputation.village-quest.support.invalid");
        if (!validBoard(player, board)) return invalid;
        SupportOffer offer = offer(player, village);
        if (offer == null || !offer.id().equals(offerId) || !offer.accepted() || offer.supplied() || offer.barred()) return invalid;
        var inventory = player.getInventory(); int wheat = 0, planks = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(Items.WHEAT)) wheat += stack.getCount();
            else if (stack.is(ItemTags.PLANKS)) planks += stack.getCount();
        }
        if (wheat < 32 || planks < 16) return new SubmissionResult(false, 0, 0, "reputation.village-quest.support.materials");
        QuestState state = QuestState.get(player.level().getServer());
        if (!recordSupply(state.getPlayerData(player.getUUID()).socialReputation(), village, offer.cycle())) return invalid;
        // No callbacks between validation and consumption; both disjoint predicates were checked on the server thread.
        int remainingWheat = 32, remainingPlanks = 16;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(Items.WHEAT)) { int take = Math.min(remainingWheat, stack.getCount()); stack.shrink(take); remainingWheat -= take; }
            else if (stack.is(ItemTags.PLANKS)) { int take = Math.min(remainingPlanks, stack.getCount()); stack.shrink(take); remainingPlanks -= take; }
        }
        inventory.setChanged(); player.inventoryMenu.broadcastChanges(); state.setDirty();
        return new SubmissionResult(true, 32, 16, "");
    }

    public static boolean accept(Map<UUID, SocialReputationData> players, UUID player, VillageKey village,
                                 int cycle, boolean abandoned) {
        SocialReputationData data = players.get(player);
        if (!abandoned || cycle <= 0 || data == null || !data.writable()) return false;
        var previous = data.supportView().get(village);
        if (previous != null && previous.cycle() >= cycle) return false;
        if (data.activeCase() != null && data.activeCase().affected().contains(village)) return false;
        long helpers = players.values().stream().map(value -> value.supportView().get(village))
                .filter(value -> value != null && value.cycle() == cycle).count();
        if (helpers >= 8) return false;
        UUID id = SocialReputationService.instanceId(BenefitKind.RESETTLEMENT, player, village + ":" + cycle);
        data.putSupport(village, new SupportContribution(id, cycle, false, false, false)); return true;
    }

    /** Called only after the server has validated and consumed the complete supply bundle. */
    public static boolean recordSupply(SocialReputationData data, VillageKey village, int cycle) {
        var contribution = data.supportView().get(village);
        if (!data.writable() || contribution == null || contribution.cycle() != cycle || contribution.supplied()
                || contribution.resolved() || contribution.barred()) return false;
        data.putSupport(village, new SupportContribution(contribution.id(), cycle, true, false, false)); return true;
    }

    public static Map<UUID, MutationResult> resolve(Map<UUID, SocialReputationData> players, VillageKey village,
                                                   int cycle, long reset, long tick) {
        return resolve(players, village, cycle, reset, tick, true);
    }
    public static Map<UUID, MutationResult> resolve(Map<UUID, SocialReputationData> players, VillageKey village,
                                                   int cycle, long reset, long tick, boolean enabled) {
        Map<UUID, MutationResult> results = new HashMap<>();
        players.forEach((player, data) -> {
            var contribution = data.supportView().get(village);
            if (!data.writable() || contribution == null || contribution.cycle() != cycle || contribution.resolved()) return;
            boolean eligible = enabled && contribution.supplied() && !contribution.barred()
                    && (data.activeCase() == null || !data.activeCase().affected().contains(village));
            MutationResult result;
            if (eligible) result = SocialReputationService.recordBenefit(data,
                    new BenefitEvent(contribution.id(), BenefitKind.RESETTLEMENT, player, List.of(village), reset), tick);
            else {
                data.markReceipt(BenefitKind.RESETTLEMENT, contribution.id());
                result = new MutationResult(true, 0, Map.of(), data.revision());
            }
            data.putSupport(village, new SupportContribution(contribution.id(), cycle, contribution.supplied(), true, contribution.barred()));
            results.put(player, result);
        });
        return Map.copyOf(results);
    }

    private static Map<UUID, SocialReputationData> players(QuestState state) {
        Map<UUID, SocialReputationData> players = new HashMap<>();
        state.getPlayersView().forEach((id, data) -> players.put(id, data.socialReputation())); return players;
    }
    public static void onRestored(MinecraftServer server, VillageKey village, int cycle) {
        QuestState state = QuestState.get(server);
        if (!resolve(players(state), village, cycle, TimeUtil.currentDay(), state.socialServerTick(), SocialReputationService.enabled()).isEmpty()) state.setDirty();
    }

    /** Repairs a save interruption between the settlement transition and personal contribution resolution. */
    public static void reconcile(MinecraftServer server) {
        QuestState state = QuestState.get(server); VillageLifeState life = VillageLifeState.get(server);
        Map<VillageKey, Integer> pending = new HashMap<>();
        players(state).values().forEach(data -> data.supportView().forEach((village, contribution) -> {
            if (!contribution.resolved() && life.status(village) == VillageLifeState.Status.ACTIVE
                    && life.abandonmentCycle(village) == contribution.cycle()) pending.put(village, contribution.cycle());
        }));
        pending.forEach((village, cycle) -> onRestored(server, village, cycle));
    }
}
