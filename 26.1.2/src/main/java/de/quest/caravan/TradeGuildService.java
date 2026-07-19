package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.reputation.ReputationService;
import de.quest.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Long-term progression, freight contracts and investments for the caravan network. */
public final class TradeGuildService {
    private static final String CONTRACT_TYPE = "guild_contract_type";
    private static final String CONTRACT_ROUTE = "guild_contract_route";
    private static final String CONTRACT_DUE_DAY = "guild_contract_due_day";
    private static final String CONTRACT_SUPPLIED = "guild_contract_supplied";
    private static final String CONTRACTS_COMPLETED = "guild_contracts_completed";
    private static final int[] RANK_THRESHOLDS = {0, 30, 65, 110, 165};

    private TradeGuildService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static int guildScore(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null || !TradeRouteService.hasRouteAccess(world, playerId)) return 0;
        int score = TradeRouteService.routeCount(world, playerId) * 10
                + Math.max(0, data(world, playerId).getTradeRouteInt(CONTRACTS_COMPLETED)) * 8;
        for (int route = 0; route < TradeRouteService.routeCount(world, playerId); route++) {
            score += TradeRouteService.routeQuality(world, playerId, route) / 10;
            score += Math.min(30, TradeRouteService.routeSuccesses(world, playerId, route) * 3);
        }
        return score;
    }

    public static int guildRank(ServerLevel world, UUID playerId) {
        int score = guildScore(world, playerId);
        int rank = 1;
        for (int i = 0; i < RANK_THRESHOLDS.length; i++) {
            if (score >= RANK_THRESHOLDS[i]) rank = i + 1;
        }
        return Math.min(5, rank);
    }

    public static Component rankLabel(int rank) {
        return Component.translatable("text.village-quest.trade_guild.rank." + Math.max(1, Math.min(5, rank)));
    }

    public static List<Component> overview(ServerLevel world, UUID playerId) {
        expireIfNeeded(world, playerId);
        List<Component> lines = new ArrayList<>();
        int rank = guildRank(world, playerId);
        int score = guildScore(world, playerId);
        int next = rank >= 5 ? score : RANK_THRESHOLDS[rank];
        lines.add(Component.translatable("message.village-quest.trade_guild.overview",
                rankLabel(rank), score, next,
                TradeRouteService.incomeToday(world, playerId),
                TradeRouteService.escrow(world, playerId)).withStyle(ChatFormatting.GOLD));
        for (int route = 0; route < TradeRouteService.routeCount(world, playerId); route++) {
            lines.add(Component.translatable("message.village-quest.trade_guild.route",
                    route + 1,
                    TradeRouteService.specialization(world, playerId, route).label(),
                    TradeRouteService.routeQuality(world, playerId, route),
                    TradeRouteService.routeDistanceBlocks(world, playerId, route),
                    TradeRouteService.routeSuccesses(world, playerId, route)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(currentContractLine(world, playerId));
        return lines;
    }

    public static List<TradeContractType> offers(ServerLevel world, UUID playerId) {
        int rank = guildRank(world, playerId);
        List<TradeContractType> eligible = new ArrayList<>();
        for (TradeContractType type : TradeContractType.values()) {
            if (type.requiredGuildRank() <= rank) eligible.add(type);
        }
        if (eligible.isEmpty()) return List.of();
        int start = Math.floorMod(playerId.hashCode() * 31 + (int) TimeUtil.currentDay() * 17, eligible.size());
        List<TradeContractType> offers = new ArrayList<>();
        for (int i = 0; i < Math.min(3, eligible.size()); i++) {
            offers.add(eligible.get((start + i) % eligible.size()));
        }
        return List.copyOf(offers);
    }

    public static List<Component> contractBoard(ServerLevel world, UUID playerId) {
        expireIfNeeded(world, playerId);
        List<Component> lines = new ArrayList<>();
        TradeContractType active = activeContract(world, playerId);
        if (active != null) {
            lines.add(currentContractLine(world, playerId));
            return lines;
        }
        lines.add(Component.translatable("message.village-quest.trade_guild.contract_board")
                .withStyle(ChatFormatting.GOLD));
        List<TradeContractType> offers = offers(world, playerId);
        for (int i = 0; i < offers.size(); i++) {
            TradeContractType type = offers.get(i);
            lines.add(Component.translatable("message.village-quest.trade_guild.contract_offer",
                    i + 1, type.title(), type.amount(), new ItemStack(type.item()).getHoverName(),
                    CurrencyService.formatBalance(type.reward()), type.specialization().label())
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    public static boolean acceptContract(ServerLevel world, ServerPlayer player, int offerNumber, int routeIndex) {
        if (world == null || player == null || activeContract(world, player.getUUID()) != null) return false;
        List<TradeContractType> offers = offers(world, player.getUUID());
        if (offerNumber < 1 || offerNumber > offers.size()
                || routeIndex < 0 || routeIndex >= TradeRouteService.routeCount(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.contract_invalid")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        TradeContractType type = offers.get(offerNumber - 1);
        PlayerQuestData data = data(world, player.getUUID());
        data.setTradeRouteInt(CONTRACT_TYPE, type.ordinal() + 1);
        data.setTradeRouteInt(CONTRACT_ROUTE, routeIndex + 1);
        data.setTradeRouteInt(CONTRACT_DUE_DAY, (int) TimeUtil.currentDay() + 3);
        data.setTradeRouteFlag(CONTRACT_SUPPLIED, false);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.contract_accepted",
                type.title(), routeIndex + 1).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static boolean supplyContract(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return false;
        TradeContractType type = activeContract(world, player.getUUID());
        PlayerQuestData data = data(world, player.getUUID());
        if (type == null || data.hasTradeRouteFlag(CONTRACT_SUPPLIED)) return false;
        if (expired(data)) {
            failContract(world, player.getUUID());
            return false;
        }
        if (!consume(player, type)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.contract_missing",
                    type.amount(), new ItemStack(type.item()).getHoverName()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        data.setTradeRouteFlag(CONTRACT_SUPPLIED, true);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.contract_supplied",
                type.title(), data.getTradeRouteInt(CONTRACT_ROUTE)).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static void onRouteArrival(ServerLevel world, UUID ownerId, int routeIndex) {
        if (world == null || ownerId == null) return;
        PlayerQuestData data = data(world, ownerId);
        TradeContractType type = activeContract(world, ownerId);
        if (type == null) return;
        if (expired(data)) {
            failContract(world, ownerId);
            return;
        }
        if (!data.hasTradeRouteFlag(CONTRACT_SUPPLIED) || data.getTradeRouteInt(CONTRACT_ROUTE) != routeIndex + 1) return;
        double multiplier = 1.0 + Math.min(0.35, TradeRouteService.routeDistanceBlocks(world, ownerId, routeIndex) / 2000.0);
        if (TradeRouteService.specialization(world, ownerId, routeIndex) == type.specialization()) multiplier += 0.25;
        if (TradeRouteService.hasUpgrade(world, ownerId, routeIndex, TradeRouteUpgrade.TRADE_OFFICE)) multiplier += 0.25;
        int reward = Math.max(type.reward(), (int) Math.round(type.reward() * multiplier));
        CurrencyService.addBalance(world, ownerId, reward);
        ReputationService.add(world, ownerId, ReputationService.ReputationTrack.TRADE, 8 + guildRank(world, ownerId) * 2);
        data.setTradeRouteInt(CONTRACTS_COMPLETED, data.getTradeRouteInt(CONTRACTS_COMPLETED) + 1);
        clearContract(data);
        QuestState.get(world.getServer()).setDirty();
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.contract_complete",
                    type.title(), CurrencyService.formatDelta(reward)).withStyle(ChatFormatting.GREEN), false);
        }
    }

    public static void onRouteRemoved(ServerLevel world, UUID ownerId, int removedRouteIndex) {
        if (world == null || ownerId == null) return;
        PlayerQuestData data = data(world, ownerId);
        TradeContractType active = activeContract(world, ownerId);
        if (active == null) return;
        int assigned = data.getTradeRouteInt(CONTRACT_ROUTE) - 1;
        if (assigned == removedRouteIndex) {
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null && data.hasTradeRouteFlag(CONTRACT_SUPPLIED)) {
                ItemStack returned = new ItemStack(active.item(), active.amount());
                if (!owner.getInventory().add(returned)) owner.drop(returned, false);
                owner.inventoryMenu.broadcastChanges();
            }
            clearContract(data);
            if (owner != null) owner.sendSystemMessage(Component.translatable(
                    "message.village-quest.trade_guild.contract_route_removed").withStyle(ChatFormatting.RED), false);
        } else if (assigned > removedRouteIndex) {
            data.setTradeRouteInt(CONTRACT_ROUTE, assigned);
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static Component currentContractLine(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        TradeContractType type = activeContract(world, playerId);
        if (type == null) return Component.translatable("message.village-quest.trade_guild.contract_none").withStyle(ChatFormatting.GRAY);
        return Component.translatable("message.village-quest.trade_guild.contract_current",
                type.title(), data.getTradeRouteInt(CONTRACT_ROUTE),
                data.hasTradeRouteFlag(CONTRACT_SUPPLIED)
                        ? Component.translatable("text.village-quest.trade_guild.loaded")
                        : Component.translatable("text.village-quest.trade_guild.awaiting_cargo"),
                data.getTradeRouteInt(CONTRACT_DUE_DAY) - (int) TimeUtil.currentDay())
                .withStyle(ChatFormatting.GRAY);
    }

    private static TradeContractType activeContract(ServerLevel world, UUID playerId) {
        int stored = data(world, playerId).getTradeRouteInt(CONTRACT_TYPE) - 1;
        TradeContractType[] values = TradeContractType.values();
        return stored >= 0 && stored < values.length ? values[stored] : null;
    }

    private static boolean expired(PlayerQuestData data) {
        return data.getTradeRouteInt(CONTRACT_DUE_DAY) <= (int) TimeUtil.currentDay();
    }

    private static void expireIfNeeded(ServerLevel world, UUID playerId) {
        if (activeContract(world, playerId) != null && expired(data(world, playerId))) {
            failContract(world, playerId);
        }
    }

    private static void failContract(ServerLevel world, UUID ownerId) {
        PlayerQuestData data = data(world, ownerId);
        clearContract(data);
        QuestState.get(world.getServer()).setDirty();
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.contract_expired")
                .withStyle(ChatFormatting.RED), false);
    }

    private static void clearContract(PlayerQuestData data) {
        data.setTradeRouteInt(CONTRACT_TYPE, 0);
        data.setTradeRouteInt(CONTRACT_ROUTE, 0);
        data.setTradeRouteInt(CONTRACT_DUE_DAY, 0);
        data.setTradeRouteFlag(CONTRACT_SUPPLIED, false);
    }

    private static boolean consume(ServerPlayer player, TradeContractType type) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(type.item())) total += stack.getCount();
        }
        if (total < type.amount()) return false;
        int remaining = type.amount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(type.item())) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
