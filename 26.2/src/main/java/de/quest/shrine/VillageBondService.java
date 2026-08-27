package de.quest.shrine;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.caravan.TradeRouteService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.registry.ModBlocks;
import de.quest.registry.ModItems;
import de.quest.network.Payloads;
import de.quest.util.TimeUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Persistent village identities, local requests, and player-owned wayshrines. */
public final class VillageBondService {
    private static final String VILLAGE_COUNT = "bond_village_count";
    private static final String SHRINE_COUNT = "bond_shrine_count";
    private static final String SIGIL_GRANTED = "bond_sigil_granted";
    private static final String LEDGER_LENS_INSTALLED = "bond_ledger_lens_installed";
    private static final String LAST_TRAVEL_TIME = "bond_last_travel_time";
    private static final String LAST_TRAVEL_COOLDOWN_TICKS = "bond_last_travel_cooldown_ticks";
    private static final String ADMIN_TEST_NETWORK = "bond_admin_test_network";
    private static final String PENDING_CHARGE_TIME = "bond_pending_charge_time";
    private static final String PENDING_CHARGE_X = "bond_pending_charge_x";
    private static final String PENDING_CHARGE_Y = "bond_pending_charge_y";
    private static final String PENDING_CHARGE_Z = "bond_pending_charge_z";
    private static final String DECORATION_COUNT = "bond_decoration_count";
    private static final int MAX_VILLAGES = 8;
    private static final int GUEST_TRAVEL_MULTIPLIER = 2;
    private static final int CHARGES_PER_MAGIC_SHARD = 5;
    private static final int MAX_SHRINE_CHARGES = 50;
    private static final long CHARGE_CONFIRM_TICKS = 20L * 10L;
    private static final int MAX_DECORATIONS = 32;
    private static final int KNOWN_TRAVEL_COOLDOWN_TICKS = 20 * 60 * 10;
    private static final int TRUSTED_TRAVEL_COOLDOWN_TICKS = 20 * 60 * 5;
    private static final int ALLIED_TRAVEL_COOLDOWN_TICKS = 20 * 60 * 4;
    private static final int TRUSTED_REQUESTS = 2;
    private static final int ALLIED_REQUESTS = 8;

    private VillageBondService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static String villageKey(int index, String suffix) {
        return "bond_village_" + index + "_" + suffix;
    }

    private static String shrineKey(int index, String suffix) {
        return "bond_shrine_" + index + "_" + suffix;
    }

    private static String decorationKey(int index, String suffix) {
        return "bond_decoration_" + index + "_" + suffix;
    }

    public static int villageCount(ServerLevel world, UUID playerId) {
        return world == null || playerId == null ? 0
                : Math.min(MAX_VILLAGES, Math.max(0, data(world, playerId).getTradeRouteInt(VILLAGE_COUNT)));
    }

    public static int shrineCount(ServerLevel world, UUID playerId) {
        return world == null || playerId == null ? 0
                : Math.min(MAX_VILLAGES, Math.max(0, data(world, playerId).getTradeRouteInt(SHRINE_COUNT)));
    }

    public static boolean hasSigil(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).hasTradeRouteFlag(SIGIL_GRANTED);
    }

    public static void grantSigil(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || hasSigil(world, player.getUUID())) return;
        data(world, player.getUUID()).setTradeRouteFlag(SIGIL_GRANTED, true);
        give(player, GuildArchiveService.issueInitial(world, player, ArchiveItem.WAYFARERS_SIGIL,
                new ItemStack(ModItems.WAYFARERS_SIGIL)));
        QuestState.get(world.getServer()).setDirty();
    }

    public static void installLensInLedger(ServerLevel world, ServerPlayer player) {
        installLensInLedger(world, player, true);
    }

    private static void installLensInLedger(ServerLevel world, ServerPlayer player, boolean announce) {
        if (world == null || player == null) return;
        PlayerQuestData data = data(world, player.getUUID());
        data.setTradeRouteFlag(LEDGER_LENS_INSTALLED, true);
        removeAll(player, ModItems.CARTOGRAPHERS_LENS);
        QuestState.get(world.getServer()).setDirty();
        if (announce) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.lens_installed")
                    .withStyle(ChatFormatting.AQUA), false);
            world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.8f, 1.15f);
        }
    }

    private static boolean hasInstalledLedgerLens(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasTradeRouteFlag(LEDGER_LENS_INSTALLED)) return true;
        if (StoryQuestService.chapterIndex(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS) > 0
                || StoryQuestService.isCompleted(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS)) {
            installLensInLedger(world, player, false);
            return true;
        }
        return false;
    }

    /** Lets an upgraded Ledger inspect only destinations that are already connected. */
    public static InteractionResult useInstalledLedgerLens(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasInstalledLedgerLens(world, player)) {
            return InteractionResult.PASS;
        }
        if (TradeRouteService.hasActiveSurvey(world, player.getUUID())) {
            return InteractionResult.PASS;
        }
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (marker == null || !TradeRouteService.isRegisteredDestination(
                world, player.getUUID(), marker.centerX(), marker.centerZ())) {
            return InteractionResult.PASS;
        }
        return useLens(world, player);
    }

    public static VillageBondView inspectCurrentVillage(ServerLevel world, ServerPlayer player, boolean announce) {
        if (world == null || player == null) return null;
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (marker == null || !TradeRouteService.isRegisteredDestination(world, player.getUUID(), marker.centerX(), marker.centerZ())) {
            if (announce) player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.not_connected")
                    .withStyle(ChatFormatting.RED), false);
            return null;
        }
        int index = ensureVillage(world, player.getUUID(), marker);
        VillageBondView view = view(world, player.getUUID(), index);
        if (announce && view != null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.inspect",
                    view.type().label(), view.level().label(), view.request().title(),
                    view.request().amount(), new ItemStack(view.request().item()).getHoverName())
                    .withStyle(ChatFormatting.GOLD), false);
        }
        return view;
    }

    private static void recordStoryInspection(ServerLevel world, ServerPlayer player, VillageBondView view) {
        if (view == null || !StoryQuestService.isActive(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS)
                || StoryQuestService.chapterIndex(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS) != 0) return;
        String flag = StoryQuestKeys.SHRINES_INSPECTED_PREFIX + view.x() + "_" + view.z();
        if (StoryQuestService.hasStoryFlag(world, player.getUUID(), flag)) return;
        StoryQuestService.setStoryFlag(world, player.getUUID(), flag, true);
        StoryQuestService.addQuestIntClamped(world, player.getUUID(), StoryQuestKeys.SHRINES_VILLAGES_INSPECTED, 1, 2);
        StoryQuestService.completeIfEligible(world, player);
    }

    private static int ensureVillage(ServerLevel world, UUID playerId,
                                     ShadowsTradeRoadEncounterService.VillageMarker marker) {
        PlayerQuestData data = data(world, playerId);
        int existing = findVillage(data, marker.centerX(), marker.centerZ());
        if (existing >= 0) return existing;
        int count = Math.min(MAX_VILLAGES, Math.max(0, data.getTradeRouteInt(VILLAGE_COUNT)));
        if (count >= MAX_VILLAGES) return MAX_VILLAGES - 1;
        VillageBondType type = classify(world, marker, playerId);
        data.setTradeRouteInt(villageKey(count, "x"), marker.centerX());
        data.setTradeRouteInt(villageKey(count, "z"), marker.centerZ());
        data.setTradeRouteInt(villageKey(count, "type"), type.id() + 1);
        data.setTradeRouteInt(villageKey(count, "level"), VillageBondLevel.KNOWN.id() + 1);
        data.setTradeRouteInt(villageKey(count, "request"),
                VillageRequestType.forVillage(type, marker.centerX() * 31 + marker.centerZ()).id() + 1);
        data.setTradeRouteInt(VILLAGE_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty();
        return count;
    }

    private static VillageBondType classify(ServerLevel world,
                                            ShadowsTradeRoadEncounterService.VillageMarker marker,
                                            UUID playerId) {
        AABB area = new AABB(marker.minX() - 8.0, world.getMinY(), marker.minZ() - 8.0,
                marker.maxX() + 9.0, world.getMaxY(), marker.maxZ() + 9.0);
        int farmers = 0, smiths = 0, shepherds = 0, archives = 0;
        for (Villager villager : world.getEntitiesOfClass(Villager.class, area, Villager::isAlive)) {
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            if (profession.is(VillagerProfession.FARMER) || profession.is(VillagerProfession.FISHERMAN)) farmers++;
            if (profession.is(VillagerProfession.TOOLSMITH) || profession.is(VillagerProfession.WEAPONSMITH)
                    || profession.is(VillagerProfession.ARMORER)) smiths++;
            if (profession.is(VillagerProfession.SHEPHERD) || profession.is(VillagerProfession.LEATHERWORKER)) shepherds++;
            if (profession.is(VillagerProfession.LIBRARIAN) || profession.is(VillagerProfession.CARTOGRAPHER)
                    || profession.is(VillagerProfession.CLERIC)) archives++;
        }
        if (smiths > farmers && smiths >= shepherds && smiths >= archives) return VillageBondType.FORGE;
        if (shepherds > farmers && shepherds >= archives) return VillageBondType.PASTURE;
        if (archives > farmers) return VillageBondType.ARCHIVE;
        String biome = world.getBiome(new BlockPos(marker.centerX(), world.getSeaLevel(), marker.centerZ()))
                .unwrapKey().map(key -> key.identifier().toString()).orElse("").toLowerCase(Locale.ROOT);
        if (biome.contains("flower") || biome.contains("meadow") || biome.contains("forest") || biome.contains("cherry")) {
            return VillageBondType.APIARY;
        }
        return VillageBondType.GRANARY;
    }

    public static InteractionResult useNoticePost(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null || !world.getBlockState(pos).is(ModBlocks.GUILD_NOTICE_POST)) {
            return InteractionResult.FAIL;
        }
        VillageBondView view = inspectCurrentVillage(world, player, false);
        if (view == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.notice_invalid")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        sendNoticeBoardScreen(world, player, pos, view);
        return InteractionResult.SUCCESS;
    }

    public static void handleNoticeBoardAction(ServerPlayer player, Payloads.NoticeBoardActionPayload payload) {
        if (player == null || payload == null || payload.action() != Payloads.NoticeBoardActionPayload.ACTION_DELIVER
                || !(player.level() instanceof ServerLevel world)) return;
        BlockPos pos = new BlockPos(payload.worldX(), payload.worldY(), payload.worldZ());
        if (player.blockPosition().distSqr(pos) > 64.0 || !world.getBlockState(pos).is(ModBlocks.GUILD_NOTICE_POST)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.notice_invalid")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        VillageBondView view = inspectCurrentVillage(world, player, false);
        if (view == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.notice_invalid")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        fulfillRequest(world, player, view);
        VillageBondView refreshed = inspectCurrentVillage(world, player, false);
        if (refreshed != null) sendNoticeBoardScreen(world, player, pos, refreshed);
    }

    private static void sendNoticeBoardScreen(ServerLevel world, ServerPlayer player,
                                              BlockPos pos, VillageBondView view) {
        VillageRequestType request = view.request();
        int available = count(player, request.item());
        boolean requestAvailable = requestAvailable(world, player.getUUID(), view.index());
        Component nextLevel;
        Component nextPerk;
        int nextThreshold;
        if (view.level() == VillageBondLevel.KNOWN) {
            nextLevel = VillageBondLevel.TRUSTED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.trusted");
            nextThreshold = TRUSTED_REQUESTS;
        } else if (view.level() == VillageBondLevel.TRUSTED) {
            nextLevel = VillageBondLevel.ALLIED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.allied");
            nextThreshold = ALLIED_REQUESTS;
        } else {
            nextLevel = Component.translatable("screen.village-quest.notice_board.max_level");
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.complete");
            nextThreshold = 0;
        }
        ServerPlayNetworking.send(player, new Payloads.NoticeBoardPayload(
                pos.getX(), pos.getY(), pos.getZ(), view.type().label(), view.level().label(),
                request.title(), new ItemStack(request.item()), request.amount(), available,
                request.reward(), CurrencyService.getBalance(world, player.getUUID()), view.completions(),
                view.level().id(), nextThreshold, nextLevel, nextPerk, requestAvailable,
                requestAvailable && available >= request.amount()));
    }

    private static boolean fulfillRequest(ServerLevel world, ServerPlayer player, VillageBondView view) {
        VillageRequestType request = view.request();
        PlayerQuestData data = data(world, player.getUUID());
        int requestDay = currentRequestDay();
        if (!requestAvailable(data.getTradeRouteInt(villageKey(view.index(), "request_day")), requestDay)) {
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.village_bond.request_daily_locked")
                    .withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        if (!consume(player, request.item(), request.amount())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_missing",
                    request.amount(), new ItemStack(request.item()).getHoverName()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        int completions = data.getTradeRouteInt(villageKey(view.index(), "completions")) + 1;
        data.setTradeRouteInt(villageKey(view.index(), "completions"), completions);
        VillageBondLevel level = levelForCompletions(completions);
        data.setTradeRouteInt(villageKey(view.index(), "level"), level.id() + 1);
        data.setTradeRouteInt(villageKey(view.index(), "request_day"), requestDay);
        data.setTradeRouteInt(villageKey(view.index(), "request"),
                VillageRequestType.nextAfter(view.type(), request).id() + 1);
        CurrencyService.addBalance(world, player.getUUID(), request.reward());
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_complete",
                request.title(), level.label(), CurrencyService.formatDelta(request.reward()))
                .withStyle(ChatFormatting.GREEN), false);
        world.playSound(null, posFor(view), SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.8f, 1.15f);
        updateStoryBondProgress(world, player);
        return true;
    }

    public static InteractionResult useWayshrine(ServerLevel world, ServerPlayer player, BlockPos pos) {
        BlockState clickedState = world.getBlockState(pos);
        if (clickedState.is(ModBlocks.GUILD_WAYSHRINE)
                && clickedState.getValue(de.quest.content.block.GuildWayshrineBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            pos = pos.below();
        }
        if (player.getMainHandItem().is(ModItems.MAGIC_SHARD)) {
            return chargeWayshrine(world, player, pos);
        }
        UUID networkOwner = shrineOwner(world, pos);
        boolean holdingSigil = player.getMainHandItem().is(ModItems.WAYFARERS_SIGIL);
        if (player.isShiftKeyDown() && holdingSigil) {
            if (!GuildArchiveService.validateUse(world, player, player.getMainHandItem(), ArchiveItem.WAYFARERS_SIGIL)) {
                return InteractionResult.FAIL;
            }
            return inspectWithSigil(world, player, pos, world.getBlockState(pos));
        }
        if (networkOwner == null) {
            if (!holdingSigil || !hasSigil(world, player.getUUID())) {
                player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.sigil_bind_required")
                        .withStyle(ChatFormatting.RED), false);
                return InteractionResult.FAIL;
            }
            if (!GuildArchiveService.validateUse(world, player, player.getMainHandItem(), ArchiveItem.WAYFARERS_SIGIL)) {
                return InteractionResult.FAIL;
            }
        }
        boolean owner = networkOwner == null || networkOwner.equals(player.getUUID());
        UUID networkId = networkOwner == null ? player.getUUID() : networkOwner;
        int current = shrineAt(world, networkId, pos);
        if (current < 0) {
            current = bindWayshrine(world, player, pos);
            if (current < 0) return InteractionResult.FAIL;
            networkId = player.getUUID();
            owner = true;
        }
        if (!world.getBlockState(pos).getValue(de.quest.content.block.GuildWayshrineBlock.ACTIVE)) {
            if (!owner || !holdingSigil
                    || !GuildArchiveService.validateUse(world, player, player.getMainHandItem(), ArchiveItem.WAYFARERS_SIGIL)) {
                player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.sigil_activate_required")
                        .withStyle(ChatFormatting.RED), false);
                return InteractionResult.FAIL;
            }
            world.setBlock(pos, world.getBlockState(pos).setValue(de.quest.content.block.GuildWayshrineBlock.ACTIVE, true), 3);
            if (world.getBlockState(pos.above()).is(ModBlocks.GUILD_WAYSHRINE)) {
                world.setBlock(pos.above(), world.getBlockState(pos.above()).setValue(de.quest.content.block.GuildWayshrineBlock.ACTIVE, true), 3);
            }
        }
        if (shrineCount(world, networkId) < 2) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.need_second")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }
        sendWayshrineScreen(world, player, networkId, current, owner);
        return InteractionResult.SUCCESS;
    }

    private static int bindWayshrine(ServerLevel world, ServerPlayer player, BlockPos pos) {
        PlayerQuestData data = data(world, player.getUUID());
        UUID existingOwner = shrineOwner(world, pos);
        if (existingOwner != null && !existingOwner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.owned")
                    .withStyle(ChatFormatting.RED), false);
            return -1;
        }
        UUID nearbyOwner = shrineOwnerNear(world, pos, player.getUUID(), 64.0);
        if (nearbyOwner != null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.shared_network_nearby")
                    .withStyle(ChatFormatting.RED), false);
            return -1;
        }
        int village = nearestVillage(world, player.getUUID(), pos, 96);
        boolean atHome = TradeRouteService.isNearHome(world, player.getUUID(), pos, 64);
        if (!atHome && village < 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.untrusted")
                    .withStyle(ChatFormatting.RED), false);
            return -1;
        }
        int count = shrineCount(world, player.getUUID());
        if (count >= MAX_VILLAGES) return -1;
        data.setTradeRouteInt(shrineKey(count, "x"), pos.getX());
        data.setTradeRouteInt(shrineKey(count, "y"), pos.getY());
        data.setTradeRouteInt(shrineKey(count, "z"), pos.getZ());
        data.setTradeRouteInt(shrineKey(count, "village"), village + 1);
        data.setTradeRouteString(shrineKey(count, "name"), "");
        data.setTradeRouteInt(shrineKey(count, "charges"), 0);
        data.setTradeRouteInt(SHRINE_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.bound", count + 1)
                .withStyle(ChatFormatting.AQUA), false);
        updateStoryShrineProgress(world, player);
        return count;
    }

    public static void handleTravel(ServerPlayer player, Payloads.WayshrineTravelPayload payload) {
        if (player == null || payload == null || !(player.level() instanceof ServerLevel world)) return;
        int current = payload.currentIndex();
        int target = payload.targetIndex();
        UUID networkOwner = nearbyNetworkOwner(world, player, current);
        if (networkOwner == null) return;
        int count = shrineCount(world, networkOwner);
        if (current < 0 || target < 0 || current >= count || target >= count || current == target) return;
        PlayerQuestData data = data(world, networkOwner);
        BlockPos currentPos = shrinePos(data, current);
        if (player.blockPosition().distSqr(currentPos) > 36.0
                || !world.getBlockState(currentPos).is(ModBlocks.GUILD_WAYSHRINE)) return;
        travel(world, player, networkOwner, current, target, payload.useCharge());
    }

    private static InteractionResult chargeWayshrine(ServerLevel world, ServerPlayer player, BlockPos pos) {
        UUID networkOwner = shrineOwner(world, pos);
        if (networkOwner == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.charge_unbound")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        int shrine = shrineAt(world, networkOwner, pos);
        if (shrine < 0) return InteractionResult.FAIL;
        PlayerQuestData networkData = data(world, networkOwner);
        int charges = Math.max(0, networkData.getTradeRouteInt(shrineKey(shrine, "charges")));
        if (charges >= MAX_SHRINE_CHARGES) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.charge_full",
                    MAX_SHRINE_CHARGES).withStyle(ChatFormatting.YELLOW), false);
            return InteractionResult.FAIL;
        }
        PlayerQuestData travelerData = data(world, player.getUUID());
        long now = world.getGameTime();
        long pendingAt = Integer.toUnsignedLong(travelerData.getTradeRouteInt(PENDING_CHARGE_TIME));
        boolean sameShrine = travelerData.getTradeRouteInt(PENDING_CHARGE_X) == pos.getX()
                && travelerData.getTradeRouteInt(PENDING_CHARGE_Y) == pos.getY()
                && travelerData.getTradeRouteInt(PENDING_CHARGE_Z) == pos.getZ();
        boolean confirmed = pendingAt > 0 && sameShrine && now >= pendingAt
                && now - pendingAt <= CHARGE_CONFIRM_TICKS;
        if (!confirmed) {
            travelerData.setTradeRouteInt(PENDING_CHARGE_TIME, (int) now);
            travelerData.setTradeRouteInt(PENDING_CHARGE_X, pos.getX());
            travelerData.setTradeRouteInt(PENDING_CHARGE_Y, pos.getY());
            travelerData.setTradeRouteInt(PENDING_CHARGE_Z, pos.getZ());
            QuestState.get(world.getServer()).setDirty();
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.1f);
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.charge_confirm",
                    CHARGES_PER_MAGIC_SHARD, 10).withStyle(ChatFormatting.YELLOW), false);
            return InteractionResult.SUCCESS;
        }
        clearPendingCharge(travelerData);
        if (!player.isCreative()) player.getMainHandItem().shrink(1);
        int updated = Math.min(MAX_SHRINE_CHARGES, charges + CHARGES_PER_MAGIC_SHARD);
        networkData.setTradeRouteInt(shrineKey(shrine, "charges"), updated);
        QuestState.get(world.getServer()).setDirty();
        world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
        player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.charged",
                CHARGES_PER_MAGIC_SHARD, updated, MAX_SHRINE_CHARGES).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return InteractionResult.SUCCESS;
    }

    private static void clearPendingCharge(PlayerQuestData data) {
        data.setTradeRouteInt(PENDING_CHARGE_TIME, 0);
        data.setTradeRouteInt(PENDING_CHARGE_X, 0);
        data.setTradeRouteInt(PENDING_CHARGE_Y, 0);
        data.setTradeRouteInt(PENDING_CHARGE_Z, 0);
    }

    public static void handleRename(ServerPlayer player, Payloads.WayshrineRenamePayload payload) {
        if (player == null || payload == null || !(player.level() instanceof ServerLevel world)) return;
        int index = payload.currentIndex();
        int count = shrineCount(world, player.getUUID());
        if (index < 0 || index >= count) return;
        PlayerQuestData data = data(world, player.getUUID());
        BlockPos pos = shrinePos(data, index);
        if (player.blockPosition().distSqr(pos) > 36.0 || !world.getBlockState(pos).is(ModBlocks.GUILD_WAYSHRINE)) return;
        String name = sanitizeShrineName(payload.name());
        data.setTradeRouteString(shrineKey(index, "name"), name);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.renamed",
                name.isBlank() ? defaultShrineName(world, player.getUUID(), index) : Component.literal(name))
                .withStyle(ChatFormatting.AQUA), false);
        sendWayshrineScreen(world, player, player.getUUID(), index, true);
    }

    private static InteractionResult travel(ServerLevel world, ServerPlayer player, UUID networkOwner,
                                            int current, int target, boolean useCharge) {
        PlayerQuestData networkData = data(world, networkOwner);
        PlayerQuestData travelerData = data(world, player.getUUID());
        if (player.hurtTime > 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.combat")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        if (!travelerData.hasTradeRouteFlag(ADMIN_TEST_NETWORK)
                && TradeRouteService.hasActiveRouteEvent(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.route_event")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        long last = Integer.toUnsignedLong(travelerData.getTradeRouteInt(LAST_TRAVEL_TIME));
        long now = world.getGameTime();
        int storedCooldown = travelerData.getTradeRouteInt(LAST_TRAVEL_COOLDOWN_TICKS);
        if (storedCooldown <= 0) storedCooldown = TRUSTED_TRAVEL_COOLDOWN_TICKS;
        if (last > 0 && now - last < storedCooldown) {
            long seconds = (storedCooldown - (now - last) + 19L) / 20L;
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.cooldown", seconds)
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        BlockPos from = shrinePos(networkData, current);
        BlockPos destination = shrinePos(networkData, target);
        if (!world.getBlockState(destination).is(ModBlocks.GUILD_WAYSHRINE)
                || !world.getBlockState(destination).getValue(de.quest.content.block.GuildWayshrineBlock.ACTIVE)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.destination_invalid")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        BlockPos arrival = safeArrival(world, destination);
        if (arrival == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.destination_blocked")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        boolean guest = !networkOwner.equals(player.getUUID());
        TravelTerms terms = travelTerms(world, networkOwner, current, target);
        int cost = travelCost(from, destination) * terms.priceMultiplier()
                * (guest ? GUEST_TRAVEL_MULTIPLIER : 1);
        int charges = Math.max(0, networkData.getTradeRouteInt(shrineKey(current, "charges")));
        if (useCharge) {
            if (charges < terms.chargeCost()) {
                player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.charge_missing_amount",
                                terms.chargeCost())
                        .withStyle(ChatFormatting.RED), false);
                return InteractionResult.FAIL;
            }
            networkData.setTradeRouteInt(shrineKey(current, "charges"), charges - terms.chargeCost());
        } else {
            if (!CurrencyService.removeBalance(world, player.getUUID(), cost)) {
                player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.cost_missing",
                        CurrencyService.formatBalance(cost)).withStyle(ChatFormatting.RED), false);
                return InteractionResult.FAIL;
            }
        }
        travelerData.setTradeRouteInt(LAST_TRAVEL_TIME, (int) now);
        travelerData.setTradeRouteInt(LAST_TRAVEL_COOLDOWN_TICKS, terms.cooldownTicks());
        QuestState.get(world.getServer()).setDirty();
        world.playSound(null, from, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.8f);
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        world.playSound(null, destination, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.35f);
        player.sendSystemMessage(Component.translatable(useCharge
                        ? "message.village-quest.wayshrine.travel_charged"
                        : "message.village-quest.wayshrine.travel",
                target + 1, CurrencyService.formatDelta(-cost)).withStyle(ChatFormatting.AQUA), false);
        return InteractionResult.SUCCESS;
    }

    private static void sendWayshrineScreen(ServerLevel world, ServerPlayer player, UUID networkOwner,
                                            int current, boolean owner) {
        int cooldown = cooldownSeconds(world, player.getUUID());
        String ownerName = world.getServer().services().nameToIdCache().get(networkOwner)
                .map(profile -> profile.name()).orElse(owner ? player.getGameProfile().name() : "Guild");
        List<Payloads.TradeRouteShrineData> shrines = shrinePayloads(world, networkOwner, current);
        if (!owner) {
            shrines = shrines.stream().map(shrine -> new Payloads.TradeRouteShrineData(
                    shrine.index(), shrine.worldX(), shrine.worldY(), shrine.worldZ(), shrine.name(),
                    shrine.current(), shrine.cost() * GUEST_TRAVEL_MULTIPLIER,
                    shrine.bondTier(), shrine.chargeCost(), shrine.cooldownSeconds())).toList();
        }
        ServerPlayNetworking.send(player, new Payloads.WayshrinePayload(current, shrines,
                ownerName, owner, owner ? 1 : GUEST_TRAVEL_MULTIPLIER, cooldown,
                CurrencyService.getBalance(world, player.getUUID()),
                Math.max(0, data(world, networkOwner).getTradeRouteInt(shrineKey(current, "charges"))),
                CHARGES_PER_MAGIC_SHARD, MAX_SHRINE_CHARGES));
    }

    private static int cooldownSeconds(ServerLevel world, UUID playerId) {
        PlayerQuestData playerData = data(world, playerId);
        long last = Integer.toUnsignedLong(playerData.getTradeRouteInt(LAST_TRAVEL_TIME));
        int duration = playerData.getTradeRouteInt(LAST_TRAVEL_COOLDOWN_TICKS);
        if (duration <= 0) duration = TRUSTED_TRAVEL_COOLDOWN_TICKS;
        long remaining = last <= 0 ? 0 : duration - (world.getGameTime() - last);
        return (int) Math.max(0, (remaining + 19L) / 20L);
    }

    private static UUID nearbyNetworkOwner(ServerLevel world, ServerPlayer player, int current) {
        if (current < 0) return null;
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            int count = shrineCount(world, entry.getKey());
            if (current >= count) continue;
            BlockPos pos = shrinePos(entry.getValue(), current);
            if (player.blockPosition().distSqr(pos) <= 36.0 && world.getBlockState(pos).is(ModBlocks.GUILD_WAYSHRINE)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static InteractionResult useLens(ServerLevel world, ServerPlayer player) {
        VillageBondView view = inspectCurrentVillage(world, player, true);
        if (view == null) return InteractionResult.FAIL;
        recordStoryInspection(world, player, view);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult inspectWithSigil(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.GUILD_WAYSHRINE)) {
            if (state.getValue(de.quest.content.block.GuildWayshrineBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) pos = pos.below();
            UUID owner = shrineOwner(world, pos);
            int shrine = owner == null ? -1 : shrineAt(world, owner, pos);
            Component message = shrine >= 0
                    ? Component.translatable("message.village-quest.wayshrine.inspect_bound", shrine + 1)
                    : Component.translatable("message.village-quest.wayshrine.inspect_unbound");
            player.sendSystemMessage(message.copy().withStyle(
                    shrine >= 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW), false);
            return InteractionResult.SUCCESS;
        }
        int distance = TradeRouteService.distanceToNearestRoute(world, player.getUUID(), pos);
        player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.inspect_route_distance", distance)
                .withStyle(distance <= 8 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return InteractionResult.SUCCESS;
    }

    public static boolean canBreakWayshrine(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (world == null || player == null || pos == null || state == null || !state.is(ModBlocks.GUILD_WAYSHRINE)) {
            return true;
        }
        if (player.isCreative()) return true;
        BlockPos base = state.getValue(de.quest.content.block.GuildWayshrineBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos;
        UUID owner = shrineOwner(world, base);
        if (owner == null || owner.equals(player.getUUID())) return true;
        player.sendSystemMessage(Component.translatable("message.village-quest.wayshrine.owner_break_only")
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    public static boolean isActiveRuinMilestone(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null
                || !StoryQuestService.isActive(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS)
                || StoryQuestService.chapterIndex(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS) != 1
                || !StoryQuestService.hasStoryFlag(world, player.getUUID(), StoryQuestKeys.SHRINES_RUIN_PLACED)) {
            return false;
        }
        int x = StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_X);
        int y = StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Y);
        int z = StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TARGET_Z);
        return pos.distSqr(new BlockPos(x, y, z)) <= 36.0;
    }

    public static void onBlockRemoved(ServerLevel world, UUID playerId, BlockPos pos) {
        if (world == null || pos == null) return;
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            removeShrineAt(world, entry.getKey(), entry.getValue(), pos);
        }
    }

    private static void removeShrineAt(ServerLevel world, UUID playerId, PlayerQuestData data, BlockPos pos) {
        int count = shrineCount(world, playerId);
        int found = -1;
        for (int i = 0; i < count; i++) {
            if (shrinePos(data, i).equals(pos)) { found = i; break; }
        }
        if (found < 0) return;
        for (int i = found; i < count - 1; i++) {
            for (String suffix : List.of("x", "y", "z", "village", "charges")) {
                data.setTradeRouteInt(shrineKey(i, suffix), data.getTradeRouteInt(shrineKey(i + 1, suffix)));
            }
            data.setTradeRouteString(shrineKey(i, "name"), data.getTradeRouteString(shrineKey(i + 1, "name")));
        }
        for (String suffix : List.of("x", "y", "z", "village", "charges")) data.setTradeRouteInt(shrineKey(count - 1, suffix), 0);
        data.setTradeRouteString(shrineKey(count - 1, "name"), "");
        data.setTradeRouteInt(SHRINE_COUNT, count - 1);
        QuestState.get(world.getServer()).setDirty();
    }

    public static List<VillageBondView> villages(ServerLevel world, UUID playerId) {
        List<VillageBondView> result = new ArrayList<>();
        for (int i = 0; i < villageCount(world, playerId); i++) {
            VillageBondView view = view(world, playerId, i);
            if (view != null) result.add(view);
        }
        result.sort(Comparator.comparingInt(VillageBondView::index));
        return List.copyOf(result);
    }

    public static List<Payloads.TradeRouteBondData> bondPayloads(ServerLevel world, UUID playerId) {
        return villages(world, playerId).stream().map(view -> new Payloads.TradeRouteBondData(
                view.index(), view.x(), view.z(), view.type().label(), view.level().label(),
                view.request().title(), view.completions())).toList();
    }

    public static List<Payloads.TradeRouteShrineData> shrinePayloads(ServerLevel world, UUID playerId, int current) {
        PlayerQuestData data = data(world, playerId);
        BlockPos from = current >= 0 && current < shrineCount(world, playerId) ? shrinePos(data, current) : null;
        List<Payloads.TradeRouteShrineData> result = new ArrayList<>();
        for (int i = 0; i < shrineCount(world, playerId); i++) {
            BlockPos pos = shrinePos(data, i);
            int village = data.getTradeRouteInt(shrineKey(i, "village")) - 1;
            String customName = sanitizeShrineName(data.getTradeRouteString(shrineKey(i, "name")));
            Component name = customName.isBlank() ? defaultShrineName(world, playerId, i) : Component.literal(customName);
            TravelTerms terms = current >= 0 ? travelTerms(world, playerId, current, i)
                    : termsForLevel(shrineLevel(world, playerId, i));
            result.add(new Payloads.TradeRouteShrineData(i, pos.getX(), pos.getY(), pos.getZ(),
                    name, i == current, from == null ? 0 : travelCost(from, pos) * terms.priceMultiplier(),
                    terms.tierId(), terms.chargeCost(), terms.cooldownTicks() / 20));
        }
        return List.copyOf(result);
    }

    private static Component defaultShrineName(ServerLevel world, UUID playerId, int index) {
        PlayerQuestData data = data(world, playerId);
        int village = data.getTradeRouteInt(shrineKey(index, "village")) - 1;
        return village < 0 ? Component.translatable("text.village-quest.wayshrine.homestead", index + 1)
                : Component.translatable("text.village-quest.wayshrine.village", index + 1,
                view(world, playerId, village) == null ? Component.translatable("text.village-quest.wayshrine.unknown")
                        : view(world, playerId, village).type().label());
    }

    private static String sanitizeShrineName(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}§]", "").trim().replaceAll("\\s+", " ");
        return clean.length() <= 32 ? clean : clean.substring(0, 32).trim();
    }

    public static void registerDecoration(ServerLevel world, ServerPlayer player, BlockPos pos, int type) {
        if (world == null || player == null || pos == null || type < 0 || type > 1) return;
        if (type == 0 && inspectCurrentVillage(world, player, false) == null) return;
        PlayerQuestData data = data(world, player.getUUID());
        int count = Math.min(MAX_DECORATIONS, Math.max(0, data.getTradeRouteInt(DECORATION_COUNT)));
        for (int i = 0; i < count; i++) {
            if (decorationPos(data, i).equals(pos)) return;
        }
        if (count >= MAX_DECORATIONS) return;
        data.setTradeRouteInt(decorationKey(count, "type"), type + 1);
        data.setTradeRouteInt(decorationKey(count, "x"), pos.getX());
        data.setTradeRouteInt(decorationKey(count, "y"), pos.getY());
        data.setTradeRouteInt(decorationKey(count, "z"), pos.getZ());
        data.setTradeRouteInt(DECORATION_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty();
    }

    public static void removeDecoration(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null) return;
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            PlayerQuestData data = entry.getValue();
            int count = Math.min(MAX_DECORATIONS, Math.max(0, data.getTradeRouteInt(DECORATION_COUNT)));
            for (int found = count - 1; found >= 0; found--) {
                if (!decorationPos(data, found).equals(pos)) continue;
                for (int i = found; i < count - 1; i++) copyDecoration(data, i + 1, i);
                for (String suffix : List.of("type", "x", "y", "z")) data.setTradeRouteInt(decorationKey(count - 1, suffix), 0);
                data.setTradeRouteInt(DECORATION_COUNT, count - 1);
                QuestState.get(world.getServer()).setDirty();
                break;
            }
        }
    }

    public static List<Payloads.TradeRouteDecorationData> decorationPayloads(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        int count = Math.min(MAX_DECORATIONS, Math.max(0, data.getTradeRouteInt(DECORATION_COUNT)));
        List<Payloads.TradeRouteDecorationData> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos pos = decorationPos(data, i);
            result.add(new Payloads.TradeRouteDecorationData(
                    Math.max(0, data.getTradeRouteInt(decorationKey(i, "type")) - 1),
                    pos.getX(), pos.getY(), pos.getZ()));
        }
        return List.copyOf(result);
    }

    public static boolean isNearAnyBondAnchor(ServerLevel world, BlockPos pos, int radius) {
        if (world == null || pos == null) return false;
        long radiusSquared = Math.max(0, radius) * (long) Math.max(0, radius);
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            UUID owner = entry.getKey();
            PlayerQuestData ownerData = entry.getValue();
            for (VillageBondView village : villages(world, owner)) {
                long dx = (long) village.x() - pos.getX();
                long dz = (long) village.z() - pos.getZ();
                if (dx * dx + dz * dz <= radiusSquared) return true;
            }
            int shrineCount = Math.min(MAX_VILLAGES, Math.max(0, ownerData.getTradeRouteInt(SHRINE_COUNT)));
            for (int i = 0; i < shrineCount; i++) {
                if (shrinePos(ownerData, i).distSqr(pos) <= radiusSquared) return true;
            }
            int decorationCount = Math.min(MAX_DECORATIONS, Math.max(0, ownerData.getTradeRouteInt(DECORATION_COUNT)));
            for (int i = 0; i < decorationCount; i++) {
                if (decorationPos(ownerData, i).distSqr(pos) <= radiusSquared) return true;
            }
        }
        return false;
    }

    private static UUID shrineOwner(ServerLevel world, BlockPos pos) {
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            PlayerQuestData ownerData = entry.getValue();
            int count = Math.min(MAX_VILLAGES, Math.max(0, ownerData.getTradeRouteInt(SHRINE_COUNT)));
            for (int i = 0; i < count; i++) if (shrinePos(ownerData, i).equals(pos)) return entry.getKey();
        }
        return null;
    }

    private static UUID shrineOwnerNear(ServerLevel world, BlockPos pos, UUID excludedOwner, double radius) {
        double radiusSquared = radius * radius;
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            if (entry.getKey().equals(excludedOwner)) continue;
            PlayerQuestData ownerData = entry.getValue();
            int count = Math.min(MAX_VILLAGES, Math.max(0, ownerData.getTradeRouteInt(SHRINE_COUNT)));
            for (int i = 0; i < count; i++) {
                if (shrinePos(ownerData, i).distSqr(pos) <= radiusSquared) return entry.getKey();
            }
        }
        return null;
    }

    private static BlockPos decorationPos(PlayerQuestData data, int index) {
        return new BlockPos(data.getTradeRouteInt(decorationKey(index, "x")),
                data.getTradeRouteInt(decorationKey(index, "y")), data.getTradeRouteInt(decorationKey(index, "z")));
    }

    private static void copyDecoration(PlayerQuestData data, int from, int to) {
        for (String suffix : List.of("type", "x", "y", "z")) {
            data.setTradeRouteInt(decorationKey(to, suffix), data.getTradeRouteInt(decorationKey(from, suffix)));
        }
    }

    public static void adminTestSetup(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return;
        TradeRouteService.adminCreateTestNetwork(world, player);
        TradeRouteService.adminClearAllTestEvents(world, player.getUUID());
        PlayerQuestData data = data(world, player.getUUID());
        data.setTradeRouteInt(VILLAGE_COUNT, 0);
        data.setTradeRouteInt(SHRINE_COUNT, 0);
        data.setTradeRouteInt(DECORATION_COUNT, 0);
        data.setTradeRouteFlag(SIGIL_GRANTED, false);
        data.setTradeRouteFlag(LEDGER_LENS_INSTALLED, true);
        data.setTradeRouteFlag(ADMIN_TEST_NETWORK, true);
        grantSigil(world, player);
        give(player, new ItemStack(ModItems.GUILD_NOTICE_POST, 3));
        give(player, new ItemStack(ModItems.GUILD_WAYSHRINE, 3));
        give(player, new ItemStack(ModItems.EMBERGLASS_LANTERN, 16));
        give(player, new ItemStack(ModItems.GUILD_MILESTONE, 8));
        give(player, GuildArchiveService.issueInitial(world, player, ArchiveItem.GUILD_COURIERS_SATCHEL,
                new ItemStack(ModItems.GUILD_COURIERS_SATCHEL)));
        QuestState.get(world.getServer()).setDirty();
    }

    private static VillageBondView view(ServerLevel world, UUID playerId, int index) {
        if (index < 0 || index >= villageCount(world, playerId)) return null;
        PlayerQuestData data = data(world, playerId);
        VillageBondType type = VillageBondType.byId(Math.max(0, data.getTradeRouteInt(villageKey(index, "type")) - 1));
        int completions = Math.max(0, data.getTradeRouteInt(villageKey(index, "completions")));
        VillageBondLevel level = levelForCompletions(completions);
        int requestId = Math.max(0, data.getTradeRouteInt(villageKey(index, "request")) - 1);
        VillageRequestType request = VillageRequestType.byId(requestId, type, index);
        return new VillageBondView(index, data.getTradeRouteInt(villageKey(index, "x")),
                data.getTradeRouteInt(villageKey(index, "z")), type, level, request,
                completions);
    }

    private static int findVillage(PlayerQuestData data, int x, int z) {
        int count = Math.min(MAX_VILLAGES, data.getTradeRouteInt(VILLAGE_COUNT));
        for (int i = 0; i < count; i++) {
            if (Math.abs(data.getTradeRouteInt(villageKey(i, "x")) - x) <= 8
                    && Math.abs(data.getTradeRouteInt(villageKey(i, "z")) - z) <= 8) return i;
        }
        return -1;
    }

    private static int nearestVillage(ServerLevel world, UUID playerId, BlockPos pos, int radius) {
        int best = -1;
        double bestDistance = radius * (double) radius;
        for (VillageBondView view : villages(world, playerId)) {
            double distance = pos.distSqr(new BlockPos(view.x(), pos.getY(), view.z()));
            if (distance <= bestDistance) { best = view.index(); bestDistance = distance; }
        }
        return best;
    }

    private static int shrineAt(ServerLevel world, UUID playerId, BlockPos pos) {
        PlayerQuestData data = data(world, playerId);
        for (int i = 0; i < shrineCount(world, playerId); i++) {
            if (shrinePos(data, i).equals(pos)) return i;
        }
        return -1;
    }

    private static BlockPos shrinePos(PlayerQuestData data, int index) {
        return new BlockPos(data.getTradeRouteInt(shrineKey(index, "x")),
                data.getTradeRouteInt(shrineKey(index, "y")), data.getTradeRouteInt(shrineKey(index, "z")));
    }

    private static BlockPos posFor(VillageBondView view) { return new BlockPos(view.x(), 64, view.z()); }

    static int travelCost(BlockPos from, BlockPos to) {
        double distance = Math.sqrt(from.distSqr(to));
        return distance < 500.0 ? 2 : distance < 1500.0 ? 4 : 6;
    }

    private static TravelTerms travelTerms(ServerLevel world, UUID owner, int from, int to) {
        VillageBondLevel fromLevel = shrineLevel(world, owner, from);
        VillageBondLevel toLevel = shrineLevel(world, owner, to);
        return termsForLevel(fromLevel.id() <= toLevel.id() ? fromLevel : toLevel);
    }

    private static VillageBondLevel shrineLevel(ServerLevel world, UUID owner, int shrine) {
        PlayerQuestData ownerData = data(world, owner);
        int village = ownerData.getTradeRouteInt(shrineKey(shrine, "village")) - 1;
        VillageBondView bond = village < 0 ? null : view(world, owner, village);
        return bond == null ? VillageBondLevel.ALLIED : bond.level();
    }

    private static TravelTerms termsForLevel(VillageBondLevel level) {
        if (level == VillageBondLevel.KNOWN) {
            return new TravelTerms(2, KNOWN_TRAVEL_COOLDOWN_TICKS, 2, VillageBondLevel.KNOWN.id());
        }
        if (level == VillageBondLevel.ALLIED) {
            return new TravelTerms(1, ALLIED_TRAVEL_COOLDOWN_TICKS, 1, VillageBondLevel.ALLIED.id());
        }
        return new TravelTerms(1, TRUSTED_TRAVEL_COOLDOWN_TICKS, 1, VillageBondLevel.TRUSTED.id());
    }

    private record TravelTerms(int priceMultiplier, int cooldownTicks, int chargeCost, int tierId) {}

    private static BlockPos safeArrival(ServerLevel world, BlockPos shrine) {
        for (BlockPos offset : List.of(shrine.north(2), shrine.south(2), shrine.east(2), shrine.west(2),
                shrine.north().east(), shrine.north().west(), shrine.south().east(), shrine.south().west())) {
            if (world.getWorldBorder().isWithinBounds(offset) && world.getBlockState(offset).isAir()
                    && world.getBlockState(offset.above()).isAir()
                    && world.getBlockState(offset.below()).isFaceSturdy(world, offset.below(), net.minecraft.core.Direction.UP)) {
                return offset;
            }
        }
        return null;
    }

    private static boolean consume(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) total += inventory.getItem(i).getCount();
        if (total < amount) return false;
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed); remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    static VillageBondLevel levelForCompletions(int completions) {
        if (completions >= ALLIED_REQUESTS) return VillageBondLevel.ALLIED;
        if (completions >= TRUSTED_REQUESTS) return VillageBondLevel.TRUSTED;
        return VillageBondLevel.KNOWN;
    }

    static boolean requestAvailable(int lastCompletionDay, int currentDay) {
        return lastCompletionDay != currentDay;
    }

    private static int currentRequestDay() {
        return Math.toIntExact(TimeUtil.currentDay());
    }

    private static boolean requestAvailable(ServerLevel world, UUID playerId, int villageIndex) {
        int lastCompletionDay = data(world, playerId)
                .getTradeRouteInt(villageKey(villageIndex, "request_day"));
        return requestAvailable(lastCompletionDay, currentRequestDay());
    }

    /** Clears only the per-village notice-board lock for admin daily-reset testing. */
    public static void adminResetDailyState(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return;
        PlayerQuestData data = data(world, playerId);
        for (int i = 0; i < villageCount(world, playerId); i++) {
            data.setTradeRouteInt(villageKey(i, "request_day"), 0);
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        if (player == null || item == null) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void removeAll(ServerPlayer player, net.minecraft.world.item.Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) stack.setCount(0);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }

    private static void updateStoryBondProgress(ServerLevel world, ServerPlayer player) {
        if (!StoryQuestService.isActive(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS)) return;
        long trusted = villages(world, player.getUUID()).stream().filter(view -> view.level().id() >= VillageBondLevel.TRUSTED.id()).count();
        StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_TRUSTED_VILLAGES, (int) trusted);
        StoryQuestService.completeIfEligible(world, player);
    }

    private static void updateStoryShrineProgress(ServerLevel world, ServerPlayer player) {
        if (!StoryQuestService.isActive(world, player.getUUID(), StoryArcType.SHRINES_BETWEEN_ROADS)) return;
        StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHRINES_ACTIVATED, shrineCount(world, player.getUUID()));
        StoryQuestService.completeIfEligible(world, player);
    }

    public record VillageBondView(int index, int x, int z, VillageBondType type,
                                  VillageBondLevel level, VillageRequestType request, int completions) {}
}
