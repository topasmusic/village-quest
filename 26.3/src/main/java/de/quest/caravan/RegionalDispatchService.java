package de.quest.caravan;

import de.quest.caravan.RegionalDispatchLedger.Dispatch;
import de.quest.caravan.RegionalDispatchLedger.Endpoint;
import de.quest.caravan.RegionalDispatchLedger.Stage;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.guildtown.GuildTownProgress;
import de.quest.network.VillageNetworkPayloads;
import de.quest.registry.ModBlocks;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageLifeState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Connects saved dispatch freight to actual hub-and-spoke route arrivals. */
public final class RegionalDispatchService {
    private RegionalDispatchService() {}

    /** Route slots are only presentation choices; saved freight uses their connection UUIDs. */
    static List<Integer> destinationIndices(PlayerQuestData data, int sourceIndex, IntPredicate available) {
        if (data == null || available == null) return List.of();
        int count = Math.min(TradeRouteService.MAX_ROUTES,
                Math.max(0, data.getTradeRouteInt("route_count")));
        if (sourceIndex < 0 || sourceIndex >= count || !available.test(sourceIndex)) return List.of();
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            if (index != sourceIndex && available.test(index)) result.add(index);
        }
        return List.copyOf(result);
    }

    public static void open(ServerLevel world, ServerPlayer player, BlockPos board) {
        if (!validBoard(world, player, board)) return;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, board);
        int source = marker == null ? -1 : routeAt(data, marker.centerX(), marker.centerZ());
        List<VillageNetworkPayloads.RegionalDispatchOfferData> offers = new ArrayList<>();
        if (source >= 0 && TradeRouteService.hasRouteAccess(world, player.getUUID())) {
            for (int target : destinationIndices(data, source,
                    index -> dispatchCandidate(world, data, index))) {
                VillageBondType sourceType = routeType(data, source);
                VillageBondType targetType = routeType(data, target);
                Item cargo = cargo(sourceType);
                int amount = 8;
                offers.add(new VillageNetworkPayloads.RegionalDispatchOfferData(target,
                        Component.empty().append(RegionalDispatchCommission.title(sourceType, targetType))
                                .append(" · ").append(villageLabel(targetType,
                                        TradeRouteData.routeInt(data, target, "x"),
                                        TradeRouteData.routeInt(data, target, "z"))),
                        new ItemStack(cargo), amount, count(player, cargo), 12));
            }
        }
        Dispatch active = RegionalDispatchLedger.read(data);
        Component sourceLabel = source < 0
                ? Component.translatable("screen.village-quest.dispatch.no_source")
                : villageLabel(routeType(data, source), marker.centerX(), marker.centerZ());
        Component status = active == null
                ? Component.translatable(source < 0
                        ? "screen.village-quest.dispatch.need_source"
                        : !dispatchCandidate(world, data, source)
                        ? "screen.village-quest.dispatch.source_paused"
                        : offers.isEmpty() ? "screen.village-quest.dispatch.need_pair"
                        : "screen.village-quest.dispatch.available")
                : Component.translatable("screen.village-quest.dispatch.stage."
                        + active.stage().name().toLowerCase(java.util.Locale.ROOT));
        Component path = active == null ? Component.empty() : Component.empty()
                .append(RegionalDispatchCommission.title(
                        VillageBondType.byId(active.sourceType() - 1),
                        VillageBondType.byId(active.targetType() - 1)))
                .append(" · ").append(Component.translatable(
                        "screen.village-quest.dispatch.path",
                        villageLabel(VillageBondType.byId(active.sourceType() - 1),
                                active.sourceX(), active.sourceZ()),
                        villageLabel(VillageBondType.byId(active.targetType() - 1),
                                active.targetX(), active.targetZ())));
        ItemStack activeCargo = active == null || active.stage() == Stage.DELIVERED ? ItemStack.EMPTY
                : new ItemStack(cargo(VillageBondType.byId(active.sourceType() - 1)));
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.RegionalDispatchPayload(
                board.getX(), board.getY(), board.getZ(), sourceLabel, status, path,
                activeCargo, active == null ? 0 : active.amount(),
                active != null && active.stage() == Stage.CANCELLED_SAFE,
                active == null || active.stage() == Stage.DELIVERED, List.copyOf(offers)));
    }

    public static void handleAction(ServerPlayer player,
                                    VillageNetworkPayloads.RegionalDispatchActionPayload action) {
        if (player == null || action == null || !(player.level() instanceof ServerLevel world)) return;
        BlockPos board = new BlockPos(action.worldX(), action.worldY(), action.worldZ());
        if (!validBoard(world, player, board)) return;
        if (action.action() == VillageNetworkPayloads.RegionalDispatchActionPayload.START) {
            startAtBoard(world, player, board, action.routeIndex());
        } else if (action.action() == VillageNetworkPayloads.RegionalDispatchActionPayload.CLAIM) {
            claim(world, player);
        } else if (action.action() != VillageNetworkPayloads.RegionalDispatchActionPayload.REFRESH) {
            return;
        }
        open(world, player, board);
    }

    private static void startAtBoard(ServerLevel world, ServerPlayer player, BlockPos board, int target) {
        if (!de.quest.reputation.ReputationAccessService.require(world, player,
                de.quest.reputation.SocialReputationRules.ServiceKind.DISPATCH_START, null)) return;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, board);
        int source = marker == null ? -1 : routeAt(data, marker.centerX(), marker.centerZ());
        if (source < 0 || !TradeRouteService.hasRouteAccess(world, player.getUUID())
                || !destinationIndices(data, source,
                        index -> dispatchCandidate(world, data, index)).contains(target)) return;
        VillageBondType type = routeType(data, source);
        Item item = cargo(type);
        int amount = 8;
        if (count(player, item) < amount) {
            player.sendSystemMessage(Component.translatable("message.village-quest.dispatch.missing",
                    amount, new ItemStack(item).getHoverName()).withStyle(ChatFormatting.RED), false);
            return;
        }
        UUID sourceConnection = TradeRouteData.ensureConnectionId(data, source);
        UUID targetConnection = TradeRouteData.ensureConnectionId(data, target);
        Dispatch dispatch = new Dispatch(UUID.randomUUID(), sourceConnection, targetConnection,
                world.dimension().identifier().toString(), marker.centerX(), marker.centerZ(),
                TradeRouteData.routeInt(data, target, "x"), TradeRouteData.routeInt(data, target, "z"),
                type.id() + 1, routeType(data, target).id() + 1,
                BuiltInRegistries.ITEM.getKey(item).toString(), amount,
                de.quest.reputation.SocialReputationRules.rewardWithBonus(12,
                        de.quest.reputation.SocialReputationRules.decision(
                                de.quest.reputation.SocialReputationRules.ServiceKind.DISPATCH_START,
                                data.socialReputation(), null).rewardPercent()), Stage.WAITING_AT_SOURCE);
        if (!RegionalDispatchLedger.start(data, dispatch)) return;
        remove(player, item, amount);
        player.inventoryMenu.broadcastChanges();
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.dispatch.started")
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static void claim(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        Dispatch dispatch = RegionalDispatchLedger.read(data);
        if (dispatch == null || dispatch.stage() != Stage.CANCELLED_SAFE) return;
        Item item = cargo(VillageBondType.byId(dispatch.sourceType() - 1));
        if (!BuiltInRegistries.ITEM.getKey(item).toString().equals(dispatch.itemId())) return;
        var inventory = player.getInventory(); int remaining = dispatch.amount();
        var plainStack = new ItemStack(item);
        for (int slot = 0; slot < Math.min(36, inventory.getContainerSize()) && remaining > 0; slot++) {
            var existing = inventory.getItem(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, plainStack)) continue;
            int capacity = existing.isEmpty() ? plainStack.getMaxStackSize()
                    : Math.max(0, existing.getMaxStackSize() - existing.getCount());
            int returned = Math.min(remaining, capacity); if (returned <= 0) continue;
            if (existing.isEmpty()) inventory.setItem(slot, new ItemStack(item, returned)); else existing.grow(returned);
            remaining -= returned;
        }
        if (remaining < dispatch.amount()) {
            RegionalDispatchLedger.claimCancelled(data, dispatch.amount() - remaining);
            inventory.setChanged(); player.inventoryMenu.broadcastChanges();
            QuestState.get(world.getServer()).setDirty();
        }
        if (remaining > 0) player.sendSystemMessage(Component.translatable(
                "message.village-quest.dispatch.freight_remaining", remaining).withStyle(ChatFormatting.YELLOW), false);
    }

    private static boolean validBoard(ServerLevel world, ServerPlayer player, BlockPos board) {
        return world != null && player != null && board != null && player.level() == world
                && player.isAlive() && !player.isSpectator() && world == world.getServer().overworld()
                && player.blockPosition().distSqr(board) <= 64.0
                && world.hasChunkAt(board)
                && world.getBlockState(board).is(ModBlocks.GUILD_NOTICE_POST);
    }

    private static int routeAt(PlayerQuestData data, int x, int z) {
        int count = Math.min(TradeRouteService.MAX_ROUTES,
                Math.max(0, data.getTradeRouteInt("route_count")));
        for (int i = 0; i < count; i++) {
            if (Math.abs(TradeRouteData.routeInt(data, i, "x") - x) <= 8
                    && Math.abs(TradeRouteData.routeInt(data, i, "z") - z) <= 8) return i;
        }
        return -1;
    }

    private static VillageBondType routeType(PlayerQuestData data, int route) {
        int village = TradeRouteData.routeInt(data, route, "village_index") - 1;
        if (village < 0) return VillageBondType.GRANARY;
        return VillageBondType.byId(data.getTradeRouteInt(
                VillageBondService.villageKey(village, "type")) - 1);
    }

    private static boolean dispatchCandidate(ServerLevel world, PlayerQuestData data, int route) {
        if (!TradeRouteService.dispatchReady(world, data, route)) return false;
        int village = TradeRouteData.routeInt(data, route, "village_index") - 1;
        if (village < 0 || village >= VillageBondService.historicalVillageCount(data)) return false;
        int type = data.getTradeRouteInt(VillageBondService.villageKey(village, "type"));
        if (type < 1 || type > VillageBondType.values().length) return false;
        return data.getTradeRouteInt(VillageBondService.villageKey(village, "x"))
                == TradeRouteData.routeInt(data, route, "x")
                && data.getTradeRouteInt(VillageBondService.villageKey(village, "z"))
                == TradeRouteData.routeInt(data, route, "z")
                && (data.getTradeRouteString(VillageBondService.villageKey(village, "dimension")).isBlank()
                || data.getTradeRouteString(VillageBondService.villageKey(village, "dimension"))
                        .equals(world.dimension().identifier().toString()));
    }

    private static Component villageLabel(VillageBondType type, int x, int z) {
        return Component.translatable("screen.village-quest.dispatch.village", type.label(), x, z);
    }

    private static Item cargo(VillageBondType type) {
        return switch (type) {
            case GRANARY -> Items.BREAD;
            case FORGE -> Items.IRON_INGOT;
            case PASTURE -> Items.LEATHER;
            case APIARY -> Items.HONEYCOMB;
            case ARCHIVE -> Items.PAPER;
        };
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void remove(ServerPlayer player, Item item, int amount) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(amount, stack.getCount());
            stack.shrink(taken);
            amount -= taken;
        }
    }

    static Component caravanCargoLine(PlayerQuestData data, int routeIndex) {
        Dispatch dispatch = RegionalDispatchLedger.read(data);
        if (dispatch == null) return null;
        String raw = data.getTradeRouteString(TradeRouteData.routeKey(routeIndex, "connection_id"));
        UUID connection;
        try {
            connection = UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        boolean first = connection.equals(dispatch.sourceConnection())
                && (dispatch.stage() == Stage.WAITING_AT_SOURCE || dispatch.stage() == Stage.LEG_ONE);
        boolean second = connection.equals(dispatch.targetConnection())
                && (dispatch.stage() == Stage.WAITING_AT_HUB || dispatch.stage() == Stage.LEG_TWO);
        if (!first && !second) return null;
        String messageKey = switch (dispatch.stage()) {
            case WAITING_AT_SOURCE -> "message.village-quest.dispatch.caravan_collecting";
            case WAITING_AT_HUB -> "message.village-quest.dispatch.caravan_transferring";
            default -> "message.village-quest.dispatch.caravan_cargo";
        };
        return Component.empty().append(RegionalDispatchCommission.title(
                VillageBondType.byId(dispatch.sourceType() - 1),
                VillageBondType.byId(dispatch.targetType() - 1))).append(" · ").append(Component.translatable(messageKey,
                dispatch.amount(), new ItemStack(cargo(VillageBondType.byId(dispatch.sourceType() - 1))).getHoverName(),
                villageLabel(VillageBondType.byId(dispatch.targetType() - 1),
                        dispatch.targetX(), dispatch.targetZ())));
    }

    static void onCancelled(ServerLevel world, UUID ownerId, PlayerQuestData data, Dispatch dispatch) {
        GuildTownProgress.addPersonalChronicle(data,
                new GuildTownProgress.VillageIdentity(ownerId, dispatch.sourceX(), dispatch.sourceZ()),
                "dispatch.cancelled." + dispatch.id(), false, world.getGameTime());
        QuestState.get(world.getServer()).setDirty();
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(Component.translatable(
                "message.village-quest.dispatch.cancelled").withStyle(ChatFormatting.YELLOW), false);
    }

    static void onArrival(ServerLevel world, UUID ownerId, PlayerQuestData data,
                          UUID connection, boolean atVillage) {
        Dispatch dispatch = RegionalDispatchLedger.read(data);
        if (dispatch == null || !dispatch.dimension().equals(world.dimension().identifier().toString())) return;
        Endpoint endpoint = atVillage
                ? connection.equals(dispatch.sourceConnection()) ? Endpoint.SOURCE : Endpoint.TARGET
                : Endpoint.HUB;
        boolean source = available(world, dispatch.dimension(), dispatch.sourceX(), dispatch.sourceZ());
        boolean target = available(world, dispatch.dimension(), dispatch.targetX(), dispatch.targetZ());
        boolean hub = TradeRouteData.isPlayerYard(data) || available(world, dispatch.dimension(),
                data.getTradeRouteInt("home_x"), data.getTradeRouteInt("home_z"));
        if (RegionalDispatchLedger.arrive(data, connection, endpoint, source, target, hub)) {
            finish(world, ownerId, data, dispatch);
        } else if (RegionalDispatchLedger.read(data).stage() != dispatch.stage()) {
            QuestState.get(world.getServer()).setDirty();
        }
    }

    static void onOwnerTick(ServerLevel world, UUID ownerId, PlayerQuestData data) {
        Dispatch dispatch = RegionalDispatchLedger.read(data);
        if (dispatch == null || dispatch.stage() != Stage.HELD_AT_DESTINATION) return;
        if (RegionalDispatchLedger.restoreDestination(data,
                available(world, dispatch.dimension(), dispatch.targetX(), dispatch.targetZ()))) {
            finish(world, ownerId, data, dispatch);
        }
    }

    private static boolean available(ServerLevel world, String dimension, int x, int z) {
        return VillageLifeState.get(world.getServer()).status(
                new VillageLifeState.VillageKey(dimension, x, z)) == VillageLifeState.Status.ACTIVE;
    }

    private static void finish(ServerLevel world, UUID ownerId, PlayerQuestData data, Dispatch dispatch) {
        CurrencyService.addBalance(world, ownerId, dispatch.reward());
        de.quest.reputation.SocialReputationService.recordBenefit(world.getServer(),
                new de.quest.reputation.SocialReputationRules.BenefitEvent(dispatch.id(),
                        de.quest.reputation.SocialReputationRules.BenefitKind.DISPATCH, ownerId,
                        List.of(new VillageLifeState.VillageKey(dispatch.dimension(), dispatch.sourceX(), dispatch.sourceZ()),
                                new VillageLifeState.VillageKey(dispatch.dimension(), dispatch.targetX(), dispatch.targetZ())),
                        de.quest.util.TimeUtil.currentDay()));
        GuildTownProgress.addPersonalChronicle(data,
                new GuildTownProgress.VillageIdentity(ownerId, dispatch.targetX(), dispatch.targetZ()),
                "dispatch.delivered." + dispatch.id(), false, world.getGameTime());
        QuestState.get(world.getServer()).setDirty();
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(Component.translatable(
                "message.village-quest.dispatch.delivered", dispatch.reward())
                .withStyle(ChatFormatting.GREEN), false);
    }
}
