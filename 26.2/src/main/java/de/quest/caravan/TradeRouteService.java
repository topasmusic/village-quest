package de.quest.caravan;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.config.VillageQuestServerConfig;
import de.quest.config.ClientPreferenceService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.economy.ProsperityService;
import de.quest.entity.CaravanMerchantEntity;
import de.quest.entity.TraitorEntity;
import de.quest.network.Payloads;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModEntities;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import de.quest.shrine.VillageBondService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;

/**
 * Persistent trade-route simulation for the Caravan Yard.
 *
 * <p>Routes advance virtually even when their chunks are unloaded. A physical merchant group is only
 * materialized while a player is close to the calculated position. This keeps long routes visible without
 * permanently loading every chunk between two villages.</p>
 */
public final class TradeRouteService {
    public static final int MAX_ROUTES = 5;
    public static final int MAX_WAYPOINTS = 48;
    public static final int PROGRESS_MAX = 10_000;

    private static final String HOME_X = "home_x";
    private static final String HOME_Z = "home_z";
    private static final String HOME_BOUND = "home_bound";
    private static final String HOME_PLAYER_YARD = "home_player_yard";
    private static final String ROUTE_COUNT = "route_count";
    private static final String ROUTE_PREFIX = "route_";
    private static final String STOPPED_SUFFIX = "_stopped";
    private static final String SURVEY_ROUTE = "survey_route";
    private static final String SURVEY_POINT_COUNT = "survey_point_count";
    private static final String SURVEY_POINT_PREFIX = "survey_point_";
    private static final String SURVEY_WAS_STOPPED = "survey_was_stopped";
    private static final String INCOME_DAY = "network_income_day";
    private static final String INCOME_TODAY = "network_income_today";
    private static final String ESCROW = "network_escrow";
    private static final String TUTORIAL_EVENT_SEEN = "network_tutorial_event_seen";
    private static final String WARDEN_CHARGES = "network_warden_charges";
    private static final String WARDEN_USE_DAY = "network_warden_use_day";
    private static final String LEDGER_GRANT_RECORDED = "migration.1_23_0.caravan_ledger_granted";
    private static final String TAG_ROUTE_CARAVAN = "vq_trade_route_caravan";
    private static final String TAG_ROUTE_ATTACKER = "vq_trade_route_attacker";
    private static final String TAG_ROUTE_OWNER_PREFIX = "vq_trade_route_owner_";
    private static final String TAG_ROUTE_INDEX_PREFIX = "vq_trade_route_index_";
    private static final int MATERIALIZE_RADIUS = 104;
    private static final int DESPAWN_RADIUS = 136;
    private static final int EVENT_INTERACTION_RADIUS = 24;
    private static final int CARAVAN_SPAWN_SEARCH_RADIUS = 18;
    private static final int CARAVAN_CROWD_RADIUS = 18;
    private static final int CARAVAN_MAX_ROUTE_DRIFT = 96;
    private static final int CARAVAN_SOFT_REGROUP_DISTANCE = 18;
    private static final int CARAVAN_HARD_REGROUP_DISTANCE = 48;
    private static final int CARAVAN_VISIBLE_RECOVERY_RADIUS = 36;
    private static final int CARAVAN_STUCK_SECONDS = 9;
    private static final int CARAVAN_RECOVERY_GRACE_SECONDS = 5;
    private static final int CARAVAN_MAX_RECOVERIES = 2;
    private static final int FERRY_DOCK_SEARCH_RADIUS = 8;
    private static final double FERRY_BOARDING_DISTANCE_SQR = 3.5 * 3.5;
    private static final double FERRY_GROUP_READY_DISTANCE_SQR = 12.0 * 12.0;
    private static final int MATERIALIZATION_RETRY_TICKS = 20 * 15;
    private static final double CARAVAN_MIN_MOVEMENT_SQR = 0.35 * 0.35;
    private static final double CARAVAN_TARGET_DISTANCE_SQR = 6.0 * 6.0;
    private static final int NPC_DESPAWN_TICKS = 20 * 60 * 60;
    private static final int MAP_UPDATE_TICKS = 20;
    private static final int STORM_CAMP_SECONDS = 30;
    private static final int YARD_CONFIRM_TICKS = 20 * 30;
    private static final int YARD_CONFIRM_DISTANCE_SQR = 4 * 4;
    private static final String[] CARAVAN_NAMES = {"alda", "bram", "cira", "doran", "esme", "fenn"};

    private static final Map<RouteKey, CaravanRuntime> ACTIVE_CARAVANS = new HashMap<>();
    private static final Map<RouteKey, Long> MATERIALIZATION_RETRY_AT = new HashMap<>();
    private static final Map<UUID, RouteKey> ENTITY_ROUTES = new HashMap<>();
    private static final Map<UUID, RouteKey> ATTACKER_ROUTES = new HashMap<>();
    private static final Set<UUID> MAP_VIEWERS = new HashSet<>();
    private static final Set<UUID> MINIMAP_VIEWERS = new HashSet<>();
    private static final Map<UUID, YardConfirmation> YARD_CONFIRMATIONS = new HashMap<>();

    private TradeRouteService() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel world = server.overworld();
        long gameTime = world.getGameTime();
        if (gameTime % 20L != 0L) {
            return;
        }

        QuestState state = QuestState.get(server);
        for (Map.Entry<UUID, PlayerQuestData> entry : state.getPlayersView().entrySet()) {
            UUID ownerId = entry.getKey();
            PlayerQuestData data = entry.getValue();
            if (ownerId == null || data == null || !hasHome(data) || !hasRouteAccess(world, ownerId)) {
                continue;
            }
            int routeCount = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
            for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
                tickRoute(world, ownerId, data, routeIndex);
            }
        }

        cleanupInactiveCaravans(world);
        tickMapViewers(world, gameTime);
    }

    public static void resetRuntimeState() {
        ACTIVE_CARAVANS.clear();
        MATERIALIZATION_RETRY_AT.clear();
        ENTITY_ROUTES.clear();
        ATTACKER_ROUTES.clear();
        MAP_VIEWERS.clear();
        MINIMAP_VIEWERS.clear();
        YARD_CONFIRMATIONS.clear();
    }

    public static void despawnAll(ServerLevel world) {
        if (world != null) {
            for (CaravanRuntime runtime : ACTIVE_CARAVANS.values()) {
                discardRuntimeEntities(world, runtime);
            }
            for (Entity entity : allEntities(world)) {
                if (entity.entityTags().contains(TAG_ROUTE_CARAVAN)
                        || entity.entityTags().contains(TAG_ROUTE_ATTACKER)) {
                    entity.discard();
                }
            }
        }
        resetRuntimeState();
    }

    public static void handleDisconnect(UUID playerId) {
        if (playerId != null) {
            MAP_VIEWERS.remove(playerId);
            MINIMAP_VIEWERS.remove(playerId);
            YARD_CONFIRMATIONS.remove(playerId);
        }
    }

    public static boolean hasCaravanYard(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.CARAVAN_YARD);
    }

    public static boolean hasRouteAccess(ServerLevel world, UUID playerId) {
        return world != null && playerId != null
                && (hasCaravanYard(world, playerId)
                || VillageProjectService.isUnlocked(world, playerId, VillageProjectType.MARKET_CHARTER));
    }

    public static int routeCapacity(ServerLevel world, UUID playerId) {
        return hasCaravanYard(world, playerId) ? MAX_ROUTES : hasRouteAccess(world, playerId) ? 1 : 0;
    }

    /** Read-only support report for players and maintainers. */
    public static List<Component> diagnostics(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return List.of(Component.translatable("command.village-quest.diagnose.unavailable")
                    .withStyle(ChatFormatting.RED));
        }
        UUID ownerId = player.getUUID();
        PlayerQuestData data = data(world, ownerId);
        VillageQuestServerConfig config = VillageQuestServerConfig.get();
        List<Component> report = new ArrayList<>();
        report.add(Component.translatable("command.village-quest.diagnose.header", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        report.add(Component.translatable("command.village-quest.diagnose.reset",
                        config.resetZone().getId(),
                        String.format(java.util.Locale.ROOT, "%02d:00", config.dailyResetHour()),
                        config.weeklyResetDay().name(),
                        String.format(java.util.Locale.ROOT, "%02d:00", config.weeklyResetHour()))
                .withStyle(ChatFormatting.GRAY));
        report.add(Component.translatable("command.village-quest.diagnose.config",
                        config.caravanVisualMode().name(), config.allowPlayerCaravanYards())
                .withStyle(ChatFormatting.GRAY));

        if (!hasHome(data)) {
            report.add(Component.translatable("command.village-quest.diagnose.home_missing")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            report.add(Component.translatable("command.village-quest.diagnose.home",
                            Component.translatable(isPlayerYard(data)
                                    ? "text.village-quest.trade_route.node.homestead"
                                    : "text.village-quest.trade_route.node.caravan_yard"),
                            data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z))
                    .withStyle(ChatFormatting.GREEN));
        }

        int count = Math.min(MAX_ROUTES, Math.max(0, data.getTradeRouteInt(ROUTE_COUNT)));
        report.add(Component.translatable("command.village-quest.diagnose.routes",
                        count, routeCapacity(world, ownerId), incomeToday(world, ownerId), escrow(world, ownerId))
                .withStyle(count > routeCapacity(world, ownerId) ? ChatFormatting.RED : ChatFormatting.GRAY));
        for (int routeIndex = 0; routeIndex < count; routeIndex++) {
            RouteKey key = new RouteKey(ownerId, routeIndex);
            CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
            int physical = livingMerchantCount(world, runtime);
            TradeRouteEventType routeEvent = event(data, routeIndex);
            report.add(Component.translatable("command.village-quest.diagnose.route",
                            routeName(data, routeIndex),
                            isStopped(data, routeIndex)
                                    ? Component.translatable("text.village-quest.trade_route.paused")
                                    : status(data, routeIndex).label(),
                            clampProgress(routeInt(data, routeIndex, "progress")) / 100,
                            quality(data, routeIndex),
                            routeWaypoints(data, routeIndex).size(),
                            physical,
                            routeEvent == null
                                    ? Component.translatable("text.village-quest.trade_route.event.none")
                                    : routeEvent.label())
                    .withStyle(runtime != null && runtime.stuckSeconds >= CARAVAN_STUCK_SECONDS / 2
                            ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }

        int tagged = 0;
        int orphaned = 0;
        int stuck = 0;
        for (Entity entity : allEntities(world)) {
            if (!entity.entityTags().contains(ownerTag(ownerId))
                    || (!entity.entityTags().contains(TAG_ROUTE_CARAVAN)
                    && !entity.entityTags().contains(TAG_ROUTE_ATTACKER))) {
                continue;
            }
            tagged++;
            RouteKey key = ENTITY_ROUTES.get(entity.getUUID());
            if (key == null) key = ATTACKER_ROUTES.get(entity.getUUID());
            CaravanRuntime runtime = key == null ? null : ACTIVE_CARAVANS.get(key);
            if (runtime == null || (!runtime.merchantIds.contains(entity.getUUID())
                    && !runtime.attackerIds.contains(entity.getUUID()))) {
                orphaned++;
            }
        }
        for (Map.Entry<RouteKey, CaravanRuntime> entry : ACTIVE_CARAVANS.entrySet()) {
            if (entry.getKey().ownerId().equals(ownerId)
                    && entry.getValue().stuckSeconds >= CARAVAN_STUCK_SECONDS / 2) {
                stuck++;
            }
        }
        report.add(Component.translatable("command.village-quest.diagnose.entities", tagged, orphaned, stuck)
                .withStyle(orphaned > 0 ? ChatFormatting.RED : stuck > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        report.add(Component.translatable("command.village-quest.diagnose.cache")
                .withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(report);
    }

    public static void initializeProvisionalNetwork(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUUID())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (!hasHome(data) && village != null && isInhabitedVillage(world, village)) {
            bindVillageHome(data, village.centerX(), village.centerZ());
        }
        grantLedger(world, player);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.provisional_unlocked")
                .withStyle(ChatFormatting.GOLD), false);
    }

    public static void initializeCaravanYard(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (!hasHome(data) && village != null && isInhabitedVillage(world, village)) {
            bindVillageHome(data, village.centerX(), village.centerZ());
        }
        grantLedger(world, player);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard_unlocked")
                .withStyle(ChatFormatting.GOLD), false);
    }

    public static InteractionResult useLedger(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return InteractionResult.PASS;
        }
        if (!hasRouteAccess(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.locked")
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        collectEscrow(world, player);
        if (player.isShiftKeyDown()) {
            PlayerQuestData data = data(world, player.getUUID());
            if (activeSurveyIndex(data) >= 0) {
                markSurveyWaypoint(world, player);
            } else {
                registerCurrentVillage(world, player);
            }
        } else {
            openMap(world, player);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean registerCurrentVillage(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUUID())) {
            return false;
        }
        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (village == null) {
            return registerPlayerYard(world, player);
        }
        if (!isInhabitedVillage(world, village)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.register.abandoned")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (!hasHome(data)) {
            bindVillageHome(data, village.centerX(), village.centerZ());
            QuestState.get(world.getServer()).setDirty();
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.register.home_bound")
                    .withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        if (data.getTradeRouteInt(HOME_X) == village.centerX() && data.getTradeRouteInt(HOME_Z) == village.centerZ()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.register.home_same")
                    .withStyle(ChatFormatting.GRAY), false);
            return false;
        }

        int capacity = routeCapacity(world, player.getUUID());
        int count = Math.min(capacity, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            if (routeInt(data, i, "x") == village.centerX() && routeInt(data, i, "z") == village.centerZ()) {
                player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.register.duplicate")
                        .withStyle(ChatFormatting.GRAY), false);
                return false;
            }
        }
        if (count >= capacity) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.register.full", capacity)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        setRouteInt(data, count, "x", village.centerX());
        setRouteInt(data, count, "z", village.centerZ());
        setRouteInt(data, count, "progress", 0);
        setRouteInt(data, count, "direction", 1);
        setRouteInt(data, count, "quality", 20);
        setRouteInt(data, count, "status", TradeRouteStatus.DANGEROUS.id());
        data.setTradeRouteInt(ROUTE_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.register.success", count + 1)
                .withStyle(ChatFormatting.GREEN), false);
        world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.7f, 1.1f);
        refreshUi(world, player);
        return true;
    }

    /**
     * Deliberately binds the network home to a player-built base. A second use
     * within thirty seconds confirms the exact position so an accidental
     * sneak-click can never relocate an established network.
     */
    public static boolean registerPlayerYard(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUUID())) {
            return false;
        }
        if (!VillageQuestServerConfig.get().allowPlayerCaravanYards()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard.disabled")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (world != world.getServer().overworld() || player.level() != world) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard.wrong_dimension")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        int routeCount = Math.min(MAX_ROUTES, Math.max(0, data.getTradeRouteInt(ROUTE_COUNT)));
        if (routeCount > 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard.routes_exist")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        BlockPos yard = player.blockPosition();
        if (!isSafePlayerYard(world, yard)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard.unsafe")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        long now = world.getGameTime();
        YardConfirmation confirmation = YARD_CONFIRMATIONS.get(player.getUUID());
        if (confirmation == null || now > confirmation.expiresAt()
                || confirmation.position().distSqr(yard) > YARD_CONFIRM_DISTANCE_SQR) {
            YARD_CONFIRMATIONS.put(player.getUUID(), new YardConfirmation(yard.immutable(), now + YARD_CONFIRM_TICKS));
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard.confirm")
                    .withStyle(ChatFormatting.GOLD), false);
            return false;
        }

        YARD_CONFIRMATIONS.remove(player.getUUID());
        bindPlayerYard(data, yard.getX(), yard.getZ());
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.yard.bound",
                        yard.getX(), yard.getY(), yard.getZ())
                .withStyle(ChatFormatting.GREEN), false);
        world.playSound(null, yard, SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.7f, 1.05f);
        refreshUi(world, player);
        return true;
    }

    private static boolean isSafePlayerYard(ServerLevel world, BlockPos position) {
        if (world == null || position == null || !world.getWorldBorder().isWithinBounds(position)) {
            return false;
        }
        BlockPos ground = position.below();
        BlockState groundState = world.getBlockState(ground);
        BlockState feetState = world.getBlockState(position);
        BlockState headState = world.getBlockState(position.above());
        return groundState.isCollisionShapeFullBlock(world, ground)
                && feetState.getCollisionShape(world, position).isEmpty()
                && headState.getCollisionShape(world, position.above()).isEmpty()
                && world.getFluidState(position).isEmpty()
                && world.getFluidState(position.above()).isEmpty();
    }

    private static boolean isInhabitedVillage(ServerLevel world,
                                               ShadowsTradeRoadEncounterService.VillageMarker village) {
        if (world == null || village == null) {
            return false;
        }
        // Use the real structure footprint instead of a fixed center radius. Large CTOV
        // settlements can place their surviving villagers well beyond a vanilla-sized core.
        AABB villageArea = new AABB(
                village.minX() - 16.0, world.getMinY(), village.minZ() - 16.0,
                village.maxX() + 17.0, world.getMaxY(), village.maxZ() + 17.0);
        return !world.getEntitiesOfClass(Villager.class, villageArea,
                villager -> villager.isAlive() && !villager.isRemoved()).isEmpty();
    }

    public static void openMap(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUUID())) {
            return;
        }
        collectEscrow(world, player);
        MAP_VIEWERS.add(player.getUUID());
        ServerPlayNetworking.send(player, buildMapPayload(world, player.getUUID(), Payloads.TradeRouteMapPayload.ACTION_OPEN));
    }

    public static void handleMapAction(ServerPlayer player, Payloads.TradeRouteActionPayload payload) {
        if (player == null || payload == null || !(player.level() instanceof ServerLevel world)) {
            return;
        }
        if (payload.action() == Payloads.TradeRouteActionPayload.ACTION_CLOSE) {
            MAP_VIEWERS.remove(player.getUUID());
            return;
        }
        if (payload.action() == Payloads.TradeRouteActionPayload.ACTION_MINIMAP_TOGGLE) {
            toggleMinimap(world, player);
            return;
        }
        if (!hasRouteAccess(world, player.getUUID())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int routeIndex = payload.routeIndex();
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            return;
        }
        switch (payload.action()) {
            case Payloads.TradeRouteActionPayload.ACTION_TOGGLE -> toggleRoute(world, player, data, routeIndex);
            case Payloads.TradeRouteActionPayload.ACTION_SURVEY_START -> startRouteSurvey(world, player, routeIndex);
            case Payloads.TradeRouteActionPayload.ACTION_SURVEY_FINISH -> finishRouteSurvey(world, player, routeIndex);
            case Payloads.TradeRouteActionPayload.ACTION_SURVEY_CANCEL -> cancelRouteSurvey(world, player, routeIndex);
            case Payloads.TradeRouteActionPayload.ACTION_REMOVE -> removeRoute(world, player, routeIndex);
            default -> {
                return;
            }
        }
        if (MAP_VIEWERS.contains(player.getUUID())) {
            ServerPlayNetworking.send(player, buildMapPayload(world, player.getUUID(), Payloads.TradeRouteMapPayload.ACTION_UPDATE));
        }
    }

    public static boolean toggleMinimap(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (MINIMAP_VIEWERS.remove(playerId)) {
            ServerPlayNetworking.send(player, buildMapPayload(world, playerId,
                    Payloads.TradeRouteMapPayload.ACTION_MINIMAP_DISABLE));
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.minimap.disabled")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!hasRouteAccess(world, playerId)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.locked")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (player.level() != world) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.wrong_dimension")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        MINIMAP_VIEWERS.add(playerId);
        collectEscrow(world, player);
        ServerPlayNetworking.send(player, buildMapPayload(world, playerId,
                Payloads.TradeRouteMapPayload.ACTION_MINIMAP_ENABLE));
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.minimap.enabled")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    public static boolean startRouteSurvey(ServerLevel world, ServerPlayer player, int routeIndex) {
        if (world == null || player == null) {
            return false;
        }
        if (world != world.getServer().overworld() || player.level() != world) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.wrong_dimension")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!hasRouteAccess(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.locked")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.route_invalid", count)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        int active = activeSurveyIndex(data);
        if (active >= 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.active", routeName(active))
                    .withStyle(ChatFormatting.YELLOW), false);
            return active == routeIndex;
        }

        clearSurveyDraft(data);
        data.setTradeRouteInt(SURVEY_ROUTE, routeIndex + 1);
        data.setTradeRouteFlag(SURVEY_WAS_STOPPED, isStopped(data, routeIndex));
        data.setTradeRouteFlag(routeKey(routeIndex, STOPPED_SUFFIX.substring(1)), true);
        removeRuntime(world, new RouteKey(player.getUUID(), routeIndex));
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.started",
                routeName(routeIndex), MAX_WAYPOINTS).withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    public static boolean markSurveyWaypoint(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null
                || world != world.getServer().overworld() || player.level() != world) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.wrong_dimension")
                        .withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int routeIndex = activeSurveyIndex(data);
        if (routeIndex < 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.none")
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        int count = Math.min(MAX_WAYPOINTS, data.getTradeRouteInt(SURVEY_POINT_COUNT));
        if (count >= MAX_WAYPOINTS) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.full", MAX_WAYPOINTS)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        RoutePoint previous = count == 0
                ? new RoutePoint(data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z))
                : surveyPoint(data, count - 1);
        RoutePoint point = new RoutePoint(player.getBlockX(), player.getBlockZ());
        if (previous.distanceSquared(point) < 16.0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.too_close")
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        boolean waterTravel = player.getVehicle() instanceof AbstractBoat
                || isWaterTravelPoint(world, player.blockPosition());
        boolean ocean = waterTravel && world.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN);
        if (waterTravel && !ocean) {
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.trade_route.survey.inland_water")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        setSurveyPoint(data, count, point, ocean);
        data.setTradeRouteInt(SURVEY_POINT_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable(ocean
                        ? "message.village-quest.trade_route.survey.marked_ferry"
                        : "message.village-quest.trade_route.survey.marked",
                count + 1, point.x(), point.z()).withStyle(ocean
                        ? ChatFormatting.AQUA : ChatFormatting.GREEN), false);
        return true;
    }

    public static boolean finishRouteSurvey(ServerLevel world, ServerPlayer player, int requestedRouteIndex) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int routeIndex = activeSurveyIndex(data);
        if (routeIndex < 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.none")
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        if (requestedRouteIndex >= 0 && requestedRouteIndex != routeIndex) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.active", routeName(routeIndex))
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        int routeCount = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex >= routeCount) {
            clearSurveyDraft(data);
            QuestState.get(world.getServer()).setDirty();
            return false;
        }

        List<RouteSurveyPoint> draft = normalizedSurveyPoints(data, routeIndex);
        Component validationError = validateSurveyPath(world, data, routeIndex, draft);
        if (validationError != null) {
            player.sendSystemMessage(validationError.copy().withStyle(ChatFormatting.RED), false);
            return false;
        }
        setRouteWaypointsWithModes(data, routeIndex, draft);
        setRouteInt(data, routeIndex, "quality", 20);
        restoreSurveyPauseState(data, routeIndex);
        clearSurveyDraft(data);
        removeRuntime(world, new RouteKey(player.getUUID(), routeIndex));
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.finished",
                routeName(routeIndex), draft.size()).withStyle(ChatFormatting.GREEN), false);
        long ferryPoints = draft.stream().filter(RouteSurveyPoint::ocean).count();
        if (ferryPoints > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.trade_route.survey.ferry_installed", ferryPoints)
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return true;
    }

    public static boolean cancelRouteSurvey(ServerLevel world, ServerPlayer player, int requestedRouteIndex) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int routeIndex = activeSurveyIndex(data);
        if (routeIndex < 0) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.none")
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        if (requestedRouteIndex >= 0 && requestedRouteIndex != routeIndex) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.active", routeName(routeIndex))
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        restoreSurveyPauseState(data, routeIndex);
        clearSurveyDraft(data);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.cancelled", routeName(routeIndex))
                .withStyle(ChatFormatting.GRAY), false);
        return true;
    }

    public static boolean removeRoute(ServerLevel world, ServerPlayer player, int routeIndex) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUUID())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.route_invalid", count)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        int activeSurvey = activeSurveyIndex(data);
        if (activeSurvey >= 0) {
            restoreSurveyPauseState(data, activeSurvey);
            clearSurveyDraft(data);
        }
        removeOwnerRuntimes(world, player.getUUID());
        Map<String, Integer> savedInts = new HashMap<>(data.getTradeRouteIntState());
        Map<String, String> savedStrings = new HashMap<>(data.getTradeRouteStringState());
        Set<String> savedFlags = new HashSet<>(data.getTradeRouteFlags());
        clearRouteEntries(data);

        int targetIndex = 0;
        for (int sourceIndex = 0; sourceIndex < count; sourceIndex++) {
            if (sourceIndex == routeIndex) {
                continue;
            }
            copyRouteEntries(data, savedInts, savedStrings, savedFlags, sourceIndex, targetIndex);
            targetIndex++;
        }
        data.setTradeRouteInt(ROUTE_COUNT, count - 1);
        TradeGuildService.onRouteRemoved(world, player.getUUID(), routeIndex);
        QuestState.get(world.getServer()).setDirty();
        SurveyorCompassQuestService.selectRouteEventMode(world, player.getUUID());
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.removed",
                routeName(routeIndex), count - 1, routeCapacity(world, player.getUUID())).withStyle(ChatFormatting.GREEN), false);
        refreshUi(world, player);
        return true;
    }

    private static void toggleRoute(ServerLevel world, ServerPlayer player, PlayerQuestData data, int routeIndex) {
        if (activeSurveyIndex(data) == routeIndex) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.survey.active", routeName(routeIndex))
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        String stoppedKey = routeKey(routeIndex, STOPPED_SUFFIX.substring(1));
        boolean stopped = data.hasTradeRouteFlag(stoppedKey);
        data.setTradeRouteFlag(stoppedKey, !stopped);
        QuestState.get(world.getServer()).setDirty();
        if (!stopped) {
            discardRouteEntities(world, new RouteKey(player.getUUID(), routeIndex));
        }
    }

    public static boolean renameRoute(ServerLevel world, ServerPlayer player, int routeIndex, String requestedName) {
        if (world == null || player == null || !validRoute(world, player.getUUID(), routeIndex)) {
            return false;
        }
        String name = sanitizeRouteName(requestedName);
        if (name.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.rename.invalid")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        data.setTradeRouteString(routeKey(routeIndex, "name"), name);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.rename.success", name)
                .withStyle(ChatFormatting.GREEN), false);
        refreshUi(world, player);
        if (MAP_VIEWERS.contains(player.getUUID())) {
            ServerPlayNetworking.send(player, buildMapPayload(world, player.getUUID(),
                    Payloads.TradeRouteMapPayload.ACTION_UPDATE));
        }
        return true;
    }

    public static InteractionResult onEntityUse(ServerLevel world, ServerPlayer helper, Entity entity) {
        if (world == null || helper == null || entity == null) {
            return InteractionResult.PASS;
        }
        RouteKey key = ENTITY_ROUTES.get(entity.getUUID());
        if (key == null) {
            return InteractionResult.PASS;
        }
        PlayerQuestData ownerData = data(world, key.ownerId());
        TradeRouteEventType event = event(ownerData, key.routeIndex());
        if (event == null) {
            helper.sendSystemMessage(Component.translatable("message.village-quest.trade_route.caravan_greeting",
                    routeName(key.routeIndex())).withStyle(ChatFormatting.GOLD), false);
            return InteractionResult.SUCCESS;
        }
        if (!key.ownerId().equals(helper.getUUID())) {
            helper.sendSystemMessage(Component.translatable("message.village-quest.trade_route.event.owner_only")
                    .withStyle(ChatFormatting.YELLOW), false);
            return InteractionResult.SUCCESS;
        }

        boolean resolved = switch (event) {
            case BROKEN_WHEEL -> consumePair(helper, stack -> stack.is(Items.IRON_INGOT), 2,
                    TradeRouteService::isPlank, 8);
            case INJURED_PACK_ANIMAL -> consumePair(helper, stack -> stack.is(Items.HAY_BLOCK), 1,
                    stack -> stack.is(Items.WHEAT), 4);
            case WASHED_OUT_BRIDGE -> consumeInventory(helper, TradeRouteService::isPlank, 16);
            case HUNGRY_TRAVELERS -> consumeInventory(helper, stack -> stack.is(Items.BREAD), 8);
            case ROAD_TOLL -> CurrencyService.removeBalance(world, helper.getUUID(), 5L);
            case MISSING_COURIER -> entity instanceof CaravanMerchantEntity merchant && merchant.isCourier();
            case FALSE_DISTRESS -> startAmbush(world, helper, key, ownerData);
            case STORM_CAMP -> false;
            case SHATTERED_WAYSTONE -> consumePair(helper, stack -> stack.is(Items.STONE_BRICKS), 8,
                    stack -> stack.is(Items.AMETHYST_SHARD), 4);
            case SHRINE_PILGRIMS -> consumeInventory(helper, stack -> stack.is(Items.BREAD), 12);
            case RUNES_GONE_DARK -> consumePair(helper, stack -> stack.is(Items.GLOW_INK_SAC), 2,
                    stack -> stack.is(Items.LAPIS_LAZULI), 8);
        };

        if (event == TradeRouteEventType.FALSE_DISTRESS) {
            return InteractionResult.SUCCESS;
        }
        if (resolved) {
            resolveEvent(world, helper, key, ownerData, event);
        } else {
            helper.sendSystemMessage(event.help().copy().withStyle(ChatFormatting.YELLOW), false);
        }
        return InteractionResult.SUCCESS;
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (world == null || killedEntity == null) {
            return;
        }
        RouteKey key = ATTACKER_ROUTES.remove(killedEntity.getUUID());
        if (key == null) {
            return;
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            runtime.attackerIds.remove(killedEntity.getUUID());
        }
        boolean attackersRemain = ATTACKER_ROUTES.containsValue(key);
        if (!attackersRemain) {
            PlayerQuestData ownerData = data(world, key.ownerId());
            resolveEvent(world, player, key, ownerData, TradeRouteEventType.FALSE_DISTRESS);
        }
    }

    public static Component activeRouteTargetLabel(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            TradeRouteEventType event = event(data, i);
            if (event != null) {
                return event.label();
            }
        }
        return null;
    }

    public static BlockPos activeRouteTarget(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            if (event(data, i) != null) {
                CaravanRuntime runtime = ACTIVE_CARAVANS.get(new RouteKey(playerId, i));
                if (runtime != null && runtimeHasLivingMerchant(world, runtime) && runtime.lastActual != null) {
                    return runtime.lastActual;
                }
                return routePosition(world, data, i);
            }
        }
        return null;
    }

    public static void resetRoutes(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        data.clearTradeRoutes();
        removeOwnerRuntimes(world, playerId);
        QuestState.get(world.getServer()).setDirty();
    }

    public static void adminCreateTestNetwork(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        removeOwnerRuntimes(world, player.getUUID());
        PlayerQuestData data = data(world, player.getUUID());
        data.clearTradeRoutes();
        VillageProjectService.unlock(world, player.getUUID(), VillageProjectType.CARAVAN_YARD);

        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        int homeX = village == null ? player.getBlockX() : village.centerX();
        int homeZ = village == null ? player.getBlockZ() : village.centerZ();
        if (village == null) {
            bindPlayerYard(data, homeX, homeZ);
        } else {
            bindVillageHome(data, homeX, homeZ);
        }
        data.setTradeRouteInt(ROUTE_COUNT, MAX_ROUTES);

        int[][] destinations = {
                {homeX + 220, homeZ},
                {homeX - 180, homeZ + 140},
                {homeX + 120, homeZ - 200},
                {homeX - 230, homeZ - 110},
                {homeX + 190, homeZ + 180}
        };
        int[] progress = {3500, 4600, 4000, 2200, 6800};
        int[] qualities = {28, 82, 54, 67, 38};
        TradeRouteStatus[] statuses = {
                TradeRouteStatus.DANGEROUS,
                TradeRouteStatus.FLOURISHING,
                TradeRouteStatus.DANGEROUS,
                TradeRouteStatus.SECURED,
                TradeRouteStatus.DANGEROUS
        };
        for (int i = 0; i < MAX_ROUTES; i++) {
            setRouteInt(data, i, "x", destinations[i][0]);
            setRouteInt(data, i, "z", destinations[i][1]);
            setRouteInt(data, i, "progress", progress[i]);
            setRouteInt(data, i, "direction", 1);
            setRouteInt(data, i, "quality", qualities[i]);
            setRouteInt(data, i, "status", statuses[i].id());
            setRouteInt(data, i, "runs", i + 1);
            setRouteInt(data, i, "successes", i + 2);
            setRouteInt(data, i, "specialization", TradeRouteSpecialization.values()[i + 1].id());
        }
        setRouteInt(data, 0, "upgrades", TradeRouteUpgrade.REINFORCED_WHEELS.bit()
                | TradeRouteUpgrade.LANTERN_CREW.bit()
                | TradeRouteUpgrade.WEATHER_COVERS.bit()
                | TradeRouteUpgrade.ESCORTS.bit()
                | TradeRouteUpgrade.INSURANCE.bit()
                | TradeRouteUpgrade.TRADE_OFFICE.bit());
        setRouteInt(data, 1, "upgrades", TradeRouteUpgrade.REINFORCED_WHEELS.bit()
                | TradeRouteUpgrade.LANTERN_CREW.bit());
        data.setTradeRouteInt("guild_contracts_completed", 12);
        CurrencyService.addBalance(world, player.getUUID(), 300);
        setRouteWaypoints(data, 0, List.of(
                new RoutePoint(homeX + 70, homeZ + 34),
                new RoutePoint(homeX + 148, homeZ + 30)
        ));
        setRouteWaypoints(data, 1, List.of(
                new RoutePoint(homeX - 54, homeZ - 24),
                new RoutePoint(homeX - 118, homeZ + 52)
        ));
        setRouteWaypoints(data, 2, List.of(
                new RoutePoint(homeX + 24, homeZ - 76),
                new RoutePoint(homeX + 92, homeZ - 132)
        ));
        setRouteWaypoints(data, 3, List.of(
                new RoutePoint(homeX - 88, homeZ - 18),
                new RoutePoint(homeX - 150, homeZ - 96)
        ));
        setRouteWaypoints(data, 4, List.of(
                new RoutePoint(homeX + 52, homeZ + 88),
                new RoutePoint(homeX + 136, homeZ + 104)
        ));
        setRouteInt(data, 0, "event", TradeRouteEventType.BROKEN_WHEEL.id());
        setRouteInt(data, 0, "event_day", currentWorldDay(world));
        setRouteInt(data, 2, "event", TradeRouteEventType.FALSE_DISTRESS.id());
        setRouteInt(data, 2, "event_day", currentWorldDay(world));
        QuestState.get(world.getServer()).setDirty();
        grantLedger(world, player);
        giveTestWayfinder(player);
        openMap(world, player);
    }

    public static boolean adminSetTestEvent(ServerLevel world,
                                            ServerPlayer player,
                                            int routeIndex,
                                            String eventKey) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.route_invalid", count)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        TradeRouteEventType selected = "clear".equalsIgnoreCase(eventKey)
                ? null
                : TradeRouteEventType.byKey(eventKey);
        if (selected == null && !"clear".equalsIgnoreCase(eventKey)) {
            player.sendSystemMessage(Component.translatable("command.village-quest.questadmin.routes.testevent.invalid", eventKey)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        setRouteInt(data, routeIndex, "event", selected == null ? 0 : selected.id());
        setRouteInt(data, routeIndex, "event_day", selected == null ? 0 : currentWorldDay(world));
        setRouteInt(data, routeIndex, "event_progress", 0);
        removeRuntime(world, new RouteKey(player.getUUID(), routeIndex));
        QuestState.get(world.getServer()).setDirty();
        SurveyorCompassQuestService.selectRouteEventMode(world, player.getUUID());
        player.sendSystemMessage(Component.translatable("command.village-quest.questadmin.routes.testevent",
                routeName(routeIndex), selected == null
                        ? Component.translatable("text.village-quest.trade_route.event.none")
                        : selected.label()).withStyle(ChatFormatting.GREEN), false);
        if (selected != null) {
            player.sendSystemMessage(selected.help().copy().withStyle(ChatFormatting.YELLOW), false);
        }
        return true;
    }

    /** Clears every seeded incident for shrine-only test setups without changing normal route tests. */
    public static void adminClearAllTestEvents(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return;
        PlayerQuestData data = data(world, playerId);
        int count = Math.min(MAX_ROUTES, Math.max(0, data.getTradeRouteInt(ROUTE_COUNT)));
        for (int routeIndex = 0; routeIndex < count; routeIndex++) {
            setRouteInt(data, routeIndex, "event", 0);
            setRouteInt(data, routeIndex, "event_day", 0);
            setRouteInt(data, routeIndex, "event_progress", 0);
            removeRuntime(world, new RouteKey(playerId, routeIndex));
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static void tickRoute(ServerLevel world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        RouteKey key = new RouteKey(ownerId, routeIndex);
        if (isStopped(data, routeIndex)) {
            discardRouteEntities(world, key);
            return;
        }

        boolean mapOnly = VillageQuestServerConfig.get().caravanVisualMode()
                == VillageQuestServerConfig.CaravanVisualMode.MAP_ONLY;
        TradeRouteEventType currentEvent = event(data, routeIndex);
        if (mapOnly && currentEvent != null) {
            clearEventForMapOnlyMode(world, key, data);
            currentEvent = null;
        }
        if (currentEvent != null) {
            int eventDay = routeInt(data, routeIndex, "event_day");
            if (currentWorldDay(world) > eventDay + 2) {
                failEvent(world, key, data, currentEvent);
                currentEvent = null;
            } else if (currentEvent == TradeRouteEventType.STORM_CAMP) {
                tickStormCamp(world, key, data);
            } else if (currentEvent == TradeRouteEventType.FALSE_DISTRESS
                    && routeInt(data, key.routeIndex(), "event_progress") > 0) {
                tickAmbush(world, key, data);
            }
        }

        if (currentEvent == null) {
            advanceRoute(world, ownerId, data, routeIndex);
        }
        if (mapOnly) {
            if (ACTIVE_CARAVANS.containsKey(key)) {
                removeRuntime(world, key);
            }
        } else {
            materializeNearPlayers(world, key, data);
        }
    }

    private static void clearEventForMapOnlyMode(ServerLevel world, RouteKey key, PlayerQuestData data) {
        setRouteInt(data, key.routeIndex(), "event", 0);
        setRouteInt(data, key.routeIndex(), "event_day", 0);
        setRouteInt(data, key.routeIndex(), "event_progress", 0);
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            discardAttackers(world, runtime);
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static void advanceRoute(ServerLevel world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        RouteKey key = new RouteKey(ownerId, routeIndex);
        int progress = clampProgress(routeInt(data, routeIndex, "progress"));
        int direction = routeInt(data, routeIndex, "direction") < 0 ? -1 : 1;
        int oldProgress = progress;
        boolean wasFerry = ferryState(data, routeIndex, progress, direction).active();
        int proposedProgress = progress + direction * movementStep(data, routeIndex);
        FerryBoarding boarding = crossedFerryBoarding(
                data, routeIndex, progress, proposedProgress, direction);
        boolean boardingHeld = boarding != null && shouldWaitForPhysicalBoarding(world, key, boarding);
        if (boardingHeld) {
            progress = boarding.progress();
        } else {
            progress = proposedProgress;
        }
        boolean arrived = !boardingHeld && (progress >= PROGRESS_MAX || progress <= 0);
        if (arrived) {
            progress = progress >= PROGRESS_MAX ? PROGRESS_MAX : 0;
            direction *= -1;
            setRouteInt(data, routeIndex, "runs", routeInt(data, routeIndex, "runs") + 1);
            setRouteInt(data, routeIndex, "direction", direction);
            int patrolCycles = routeInt(data, routeIndex, "patrol_cycles");
            if (patrolCycles > 0) {
                setRouteInt(data, routeIndex, "patrol_cycles", patrolCycles - 1);
            }
            payArrival(world, ownerId, data, routeIndex);
        }
        setRouteInt(data, routeIndex, "progress", progress);
        boolean isFerry = ferryState(data, routeIndex, progress, direction).active();
        if (wasFerry != isFerry) {
            BlockPos soundPosition = routePosition(world, data, routeIndex, progress);
            world.playSound(null, soundPosition, SoundEvents.BOAT_PADDLE_WATER,
                    SoundSource.PLAYERS, 0.55f, isFerry ? 0.9f : 1.15f);
        }

        if (!arrived && crossedMidpoint(oldProgress, progress)) {
            maybeStartEvent(world, ownerId, data, routeIndex);
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static int movementStep(PlayerQuestData data, int routeIndex) {
        double distance = Math.max(96.0, routeDistance(data, routeIndex));
        double blocksPerSecond = routeBlocksPerSecond(data, routeIndex);
        return Math.max(1, (int) Math.round(blocksPerSecond * PROGRESS_MAX / distance));
    }

    private static double routeBlocksPerSecond(PlayerQuestData data, int routeIndex) {
        int quality = quality(data, routeIndex);
        double blocksPerSecond = 0.55 + quality * 0.0065;
        if (hasUpgrade(data, routeIndex, TradeRouteUpgrade.REINFORCED_WHEELS)) {
            blocksPerSecond *= 1.08;
        }
        return blocksPerSecond;
    }

    private static void maybeStartEvent(ServerLevel world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        if (VillageQuestServerConfig.get().caravanVisualMode()
                == VillageQuestServerConfig.CaravanVisualMode.MAP_ONLY
                || !hasCaravanYard(world, ownerId) || hasActiveEvent(data)
                || ferryState(data, routeIndex,
                clampProgress(routeInt(data, routeIndex, "progress")),
                routeInt(data, routeIndex, "direction") < 0 ? -1 : 1).active()) {
            return;
        }
        int runs = routeInt(data, routeIndex, "runs");
        int eventStamp = routeInt(data, routeIndex, "event_stamp");
        if (eventStamp == runs + 1) {
            return;
        }
        setRouteInt(data, routeIndex, "event_stamp", runs + 1);
        if (routeInt(data, routeIndex, "patrol_cycles") > 0) {
            return;
        }
        int quality = quality(data, routeIndex);
        int eventChance = Math.max(8, Math.min(40, 38 - quality / 4
                + (status(data, routeIndex) == TradeRouteStatus.DANGEROUS ? 8 : 0)
                - (status(data, routeIndex) == TradeRouteStatus.FLOURISHING ? 5 : 0)
                - (hasUpgrade(data, routeIndex, TradeRouteUpgrade.LANTERN_CREW) ? 4 : 0)
                - (hasUpgrade(data, routeIndex, TradeRouteUpgrade.WEATHER_COVERS) ? 3 : 0)
                - (hasUpgrade(data, routeIndex, TradeRouteUpgrade.ESCORTS) ? 6 : 0)
                - ProsperityService.roadWatchEventReduction(world, ownerId)));
        boolean tutorialEvent = !data.hasTradeRouteFlag(TUTORIAL_EVENT_SEEN);
        int roll = Math.floorMod(ownerId.hashCode() + routeIndex * 37 + runs * 17, 100);
        if (!tutorialEvent && roll >= eventChance) {
            return;
        }
        if (!tutorialEvent && data.getTradeRouteInt(WARDEN_CHARGES) > 0) {
            data.setTradeRouteInt(WARDEN_CHARGES, data.getTradeRouteInt(WARDEN_CHARGES) - 1);
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null && ClientPreferenceService.caravanEventNotifications(owner)) {
                owner.sendSystemMessage(Component.translatable("message.village-quest.roadwarden_horn.prevented",
                        routeName(routeIndex)).withStyle(ChatFormatting.GOLD), false);
            }
            return;
        }
        TradeRouteEventType[] events = VillageBondService.hasSigil(world, ownerId)
                ? TradeRouteEventType.values()
                : java.util.Arrays.copyOf(TradeRouteEventType.values(), 8);
        TradeRouteEventType selected = events[Math.floorMod(ownerId.hashCode() + routeIndex * 11 + runs * 5, events.length)];
        setRouteInt(data, routeIndex, "event", selected.id());
        setRouteInt(data, routeIndex, "event_day", currentWorldDay(world));
        setRouteInt(data, routeIndex, "event_progress", 0);
        data.setTradeRouteFlag(TUTORIAL_EVENT_SEEN, true);
        SurveyorCompassQuestService.selectRouteEventMode(world, ownerId);
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && ClientPreferenceService.caravanEventNotifications(owner)) {
            owner.sendSystemMessage(Component.translatable("message.village-quest.trade_route.event.started",
                    routeName(routeIndex), selected.label()).withStyle(ChatFormatting.RED), false);
            owner.sendSystemMessage(selected.help().copy().withStyle(ChatFormatting.YELLOW), false);
            world.playSound(null, owner.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.45f, 1.35f);
        }
    }

    private static void tickStormCamp(ServerLevel world, RouteKey key, PlayerQuestData data) {
        BlockPos target = runtimeInteractionTarget(world, key, data);
        ServerPlayer helper = nearestPlayer(world, target, EVENT_INTERACTION_RADIUS);
        if (helper == null) {
            return;
        }
        int seconds = routeInt(data, key.routeIndex(), "event_progress") + 1;
        setRouteInt(data, key.routeIndex(), "event_progress", seconds);
        QuestState.get(world.getServer()).setDirty();
        if (seconds >= STORM_CAMP_SECONDS) {
            resolveEvent(world, helper, key, data, TradeRouteEventType.STORM_CAMP);
        }
    }

    private static void tickAmbush(ServerLevel world, RouteKey key, PlayerQuestData data) {
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime == null) {
            setRouteInt(data, key.routeIndex(), "event_progress", 0);
            QuestState.get(world.getServer()).setDirty();
            return;
        }
        for (UUID attackerId : List.copyOf(runtime.attackerIds)) {
            Entity attacker = findEntity(world, attackerId);
            if (attacker == null || attacker.isRemoved() || !attacker.isAlive()) {
                runtime.attackerIds.remove(attackerId);
                ATTACKER_ROUTES.remove(attackerId);
            }
        }
        if (runtime.attackerIds.isEmpty()) {
            BlockPos target = runtime.lastActual == null ? runtime.lastExpected : runtime.lastActual;
            ServerPlayer helper = nearestPlayer(world, target, EVENT_INTERACTION_RADIUS * 2);
            if (helper == null) {
                helper = world.getServer().getPlayerList().getPlayer(key.ownerId());
            }
            resolveEvent(world, helper, key, data, TradeRouteEventType.FALSE_DISTRESS);
        }
    }

    private static void resolveEvent(ServerLevel world,
                                     ServerPlayer helper,
                                     RouteKey key,
                                     PlayerQuestData ownerData,
                                     TradeRouteEventType event) {
        if (event(ownerData, key.routeIndex()) != event) {
            return;
        }
        setRouteInt(ownerData, key.routeIndex(), "event", 0);
        setRouteInt(ownerData, key.routeIndex(), "event_day", 0);
        setRouteInt(ownerData, key.routeIndex(), "event_progress", 0);
        int successes = routeInt(ownerData, key.routeIndex(), "successes") + 1;
        setRouteInt(ownerData, key.routeIndex(), "successes", successes);
        setRouteInt(ownerData, key.routeIndex(), "status",
                successes >= 4 ? TradeRouteStatus.FLOURISHING.id() : TradeRouteStatus.SECURED.id());
        QuestState.get(world.getServer()).setDirty();

        if (helper != null) {
            int reward = eventReward(ownerData, key.routeIndex(), event);
            int reputation = Math.max(4, Math.min(10, reward / 2 + 2));
            CurrencyService.addBalance(world, helper.getUUID(), reward);
            ReputationService.add(world, helper.getUUID(), ReputationService.ReputationTrack.TRADE, reputation);
            helper.sendSystemMessage(Component.translatable("message.village-quest.trade_route.event.resolved",
                    event.label(), routeName(key.routeIndex())).withStyle(ChatFormatting.GREEN), false);
            world.playSound(null, helper.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.55f, 1.35f);
            refreshUi(world, helper);
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            discardAttackers(world, runtime);
            updateMerchantRoles(world, runtime, null, key.routeIndex());
        }
    }

    private static void failEvent(ServerLevel world, RouteKey key, PlayerQuestData data, TradeRouteEventType event) {
        setRouteInt(data, key.routeIndex(), "event", 0);
        setRouteInt(data, key.routeIndex(), "event_day", 0);
        setRouteInt(data, key.routeIndex(), "event_progress", 0);
        setRouteInt(data, key.routeIndex(), "failures", routeInt(data, key.routeIndex(), "failures") + 1);
        if (!hasUpgrade(data, key.routeIndex(), TradeRouteUpgrade.INSURANCE)) {
            setRouteInt(data, key.routeIndex(), "successes", Math.max(0, routeInt(data, key.routeIndex(), "successes") - 1));
        }
        setRouteInt(data, key.routeIndex(), "status", TradeRouteStatus.DANGEROUS.id());
        QuestState.get(world.getServer()).setDirty();
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(key.ownerId());
        if (owner != null && ClientPreferenceService.caravanEventNotifications(owner)) {
            owner.sendSystemMessage(Component.translatable("message.village-quest.trade_route.event.failed",
                    routeName(key.routeIndex()), event.label()).withStyle(ChatFormatting.RED), false);
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            discardAttackers(world, runtime);
        }
    }

    private static boolean startAmbush(ServerLevel world, ServerPlayer helper, RouteKey key, PlayerQuestData data) {
        if (routeInt(data, key.routeIndex(), "event_progress") > 0) {
            helper.sendSystemMessage(Component.translatable("message.village-quest.trade_route.event.false_distress.fight")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            TraitorEntity traitor = new TraitorEntity(ModEntities.TRAITOR, world);
            double angle = (Math.PI * 2.0 * i) / 3.0;
            double x = helper.getX() + Math.cos(angle) * 7.0;
            double z = helper.getZ() + Math.sin(angle) * 7.0;
            BlockPos surface = findNearbySafeSurface(world, (int) Math.floor(x), (int) Math.floor(z), 5);
            if (surface == null) {
                continue;
            }
            traitor.setPos(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
            traitor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            traitor.addTag(TAG_ROUTE_ATTACKER);
            traitor.addTag(ownerTag(key.ownerId()));
            traitor.addTag(routeTag(key.routeIndex()));
            if (world.noCollision(traitor) && world.addFreshEntity(traitor)) {
                traitor.setTarget(helper);
                runtime.attackerIds.add(traitor.getUUID());
                ATTACKER_ROUTES.put(traitor.getUUID(), key);
            }
        }
        if (runtime.attackerIds.isEmpty()) {
            helper.sendSystemMessage(TradeRouteEventType.FALSE_DISTRESS.help().copy()
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        setRouteInt(data, key.routeIndex(), "event_progress", 1);
        QuestState.get(world.getServer()).setDirty();
        helper.sendSystemMessage(Component.translatable("message.village-quest.trade_route.event.false_distress.ambush")
                .withStyle(ChatFormatting.RED), false);
        return false;
    }

    private static void materializeNearPlayers(ServerLevel world, RouteKey key, PlayerQuestData data) {
        int progress = clampProgress(routeInt(data, key.routeIndex(), "progress"));
        int direction = routeInt(data, key.routeIndex(), "direction") < 0 ? -1 : 1;
        if (ferryState(data, key.routeIndex(), progress, direction).active()) {
            if (ACTIVE_CARAVANS.containsKey(key)) {
                removeRuntime(world, key);
            }
            return;
        }
        BlockPos expected = routePosition(world, data, key.routeIndex());
        ServerPlayer observer = nearestPlayer(world, expected, MATERIALIZE_RADIUS);
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (observer == null) {
            BlockPos actual = runtime == null || runtime.lastActual == null ? expected : runtime.lastActual;
            if (runtime != null
                    && nearestPlayer(world, expected, DESPAWN_RADIUS) == null
                    && nearestPlayer(world, actual, DESPAWN_RADIUS) == null) {
                removeRuntime(world, key);
            }
            return;
        }
        if (!world.hasChunkAt(expected)) {
            return;
        }
        long retryAt = MATERIALIZATION_RETRY_AT.getOrDefault(key, 0L);
        if (world.getGameTime() < retryAt) {
            return;
        }
        if (runtime == null || !runtimeHasLivingMerchant(world, runtime)) {
            if (isMaterializationCrowded(world, key, data, expected)) {
                MATERIALIZATION_RETRY_AT.put(key, world.getGameTime() + 20L * 5L);
                return;
            }
            runtime = spawnCaravan(world, key, data, expected, observer);
            if (runtime == null) {
                MATERIALIZATION_RETRY_AT.put(key, world.getGameTime() + MATERIALIZATION_RETRY_TICKS);
                return;
            }
            ACTIVE_CARAVANS.put(key, runtime);
            MATERIALIZATION_RETRY_AT.remove(key);
        }
        runtime.lastExpected = expected;
        if (!ensureCompleteCaravan(world, key, runtime, data)) {
            removeRuntime(world, key);
            MATERIALIZATION_RETRY_AT.put(key, world.getGameTime() + MATERIALIZATION_RETRY_TICKS);
            return;
        }
        updateMerchantRoles(world, runtime, event(data, key.routeIndex()), key.routeIndex());
        if (!navigateCaravan(world, key, runtime, data, observer)) {
            return;
        }
        sampleRoadQuality(world, data, key.routeIndex(), expected);
    }

    private static CaravanRuntime spawnCaravan(ServerLevel world,
                                                RouteKey key,
                                                PlayerQuestData data,
                                                BlockPos expected,
                                                ServerPlayer observer) {
        CaravanRuntime runtime = new CaravanRuntime();
        TradeRouteEventType event = event(data, key.routeIndex());
        BlockPos anchor = findCaravanSurface(world, expected, CARAVAN_SPAWN_SEARCH_RADIUS);
        if (anchor == null && event != null && observer != null) {
            anchor = findCaravanSurface(world, observer.blockPosition(), 12);
        }
        if (anchor == null) {
            return null;
        }
        runtime.lastExpected = expected;
        runtime.lastActual = anchor;
        runtime.lastLeaderPosition = anchor;
        if (!spawnMissingMerchants(world, key, runtime, desiredMerchantCount(world, key.ownerId()), anchor)) {
            discardRuntimeEntities(world, runtime);
            return null;
        }
        updateMerchantRoles(world, runtime, event, key.routeIndex());
        return runtime;
    }

    private static boolean navigateCaravan(ServerLevel world,
                                           RouteKey key,
                                           CaravanRuntime runtime,
                                           PlayerQuestData data,
                                           ServerPlayer observer) {
        int routeIndex = key.routeIndex();
        TradeRouteEventType currentEvent = event(data, routeIndex);
        int progress = clampProgress(routeInt(data, routeIndex, "progress"));
        int direction = routeInt(data, routeIndex, "direction") < 0 ? -1 : 1;
        FerryBoarding boarding = currentEvent == null
                ? ferryBoardingAtProgress(data, routeIndex, progress, direction)
                : null;
        BlockPos target;
        if (currentEvent != null) {
            target = findCaravanSurface(world, runtime.lastExpected, 10);
            if (target == null) {
                target = runtime.lastExpected;
            }
        } else if (boarding != null) {
            target = resolveFerryDock(world, runtime, data, routeIndex, direction, boarding);
            if (target == null) {
                suspendPhysicalCaravan(world, key);
                return false;
            }
        } else {
            clearFerryDock(runtime);
            int lookAhead = Math.max(120, movementStep(data, routeIndex) * 22);
            int targetProgress = clampProgress(progress + direction * lookAhead);
            target = routePosition(world, data, routeIndex, targetProgress);
            BlockPos roadTarget = findNearbyRoadSurface(world, target, 8);
            if (roadTarget != null) {
                target = roadTarget;
            } else {
                BlockPos terrainTarget = findCaravanSurface(world, target, 5);
                if (terrainTarget != null) {
                    target = terrainTarget;
                }
            }
        }

        List<CaravanMerchantEntity> merchants = new ArrayList<>();
        for (UUID merchantId : List.copyOf(runtime.merchantIds)) {
            Entity entity = findEntity(world, merchantId);
            if (!(entity instanceof CaravanMerchantEntity merchant) || !merchant.isAlive() || merchant.isRemoved()) {
                runtime.merchantIds.remove(merchantId);
                runtime.lastMerchantPositions.remove(merchantId);
                ENTITY_ROUTES.remove(merchantId);
                continue;
            }
            merchant.setDespawnTicks(NPC_DESPAWN_TICKS);
            merchant.refreshEncounterControl(false);
            merchant.setRouteIndex(routeIndex);
            merchant.setLiveryIndex(routeLivery(data, routeIndex));
            merchants.add(merchant);
        }
        if (merchants.isEmpty()) {
            removeRuntime(world, key);
            MATERIALIZATION_RETRY_AT.put(key, world.getGameTime() + MATERIALIZATION_RETRY_TICKS);
            return false;
        }

        // Older materialized groups may already be standing on a canopy when a
        // player updates. Recover the full formation together before asking for
        // another path so they do not remain visible above the road network.
        boolean unsafeLeafSupport = merchants.stream().anyMatch(merchant ->
                world.getBlockState(merchant.blockPosition().below()).is(BlockTags.LEAVES));
        if (unsafeLeafSupport) {
            if (!recoverCaravan(world, key, runtime, data, observer, currentEvent != null)) {
                suspendPhysicalCaravan(world, key);
                return false;
            }
            return true;
        }

        CaravanMerchantEntity leader = merchants.getFirst();
        BlockPos leaderPosition = leader.blockPosition();
        runtime.lastActual = leaderPosition;
        double targetDistance = leaderPosition.distSqr(target);
        boolean groupReadyToBoard = boarding != null && targetDistance <= FERRY_BOARDING_DISTANCE_SQR;
        if (groupReadyToBoard) {
            for (CaravanMerchantEntity merchant : merchants) {
                if (merchant.blockPosition().distSqr(target) > FERRY_GROUP_READY_DISTANCE_SQR) {
                    groupReadyToBoard = false;
                    break;
                }
            }
        }
        if (groupReadyToBoard) {
            // The persistent route remains on the dry land node for this tick. Removing
            // the observed formation here lets the following tick enter the virtual sea
            // leg from the actual dock instead of making a stuck group pop offshore.
            removeRuntime(world, key);
            return false;
        }
        boolean pathRequested = true;
        if (targetDistance > 3.0 * 3.0) {
            pathRequested = leader.getNavigation().moveTo(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.88);
        } else {
            leader.getNavigation().stop();
        }

        double dx = target.getX() - leader.getX();
        double dz = target.getZ() - leader.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        double forwardX;
        double forwardZ;
        if (length < 0.25) {
            double yawRadians = Math.toRadians(leader.getYRot());
            forwardX = -Math.sin(yawRadians);
            forwardZ = Math.cos(yawRadians);
        } else {
            forwardX = dx / length;
            forwardZ = dz / length;
        }
        List<BlockPos> formationOccupied = new ArrayList<>();
        formationOccupied.add(leaderPosition);
        for (int ordinal = 1; ordinal < merchants.size(); ordinal++) {
            CaravanMerchantEntity merchant = merchants.get(ordinal);
            double side = ordinal % 2 == 0 ? 1.35 : -1.35;
            double behind = 2.25 + (ordinal - 1) * 1.55;
            double followX = leader.getX() - forwardX * behind - forwardZ * side;
            double followZ = leader.getZ() - forwardZ * behind + forwardX * side;
            BlockPos followSurface = findNearbySafeSurface(world,
                    (int) Math.floor(followX), (int) Math.floor(followZ), 2);
            if (followSurface != null) {
                merchant.getNavigation().moveTo(followSurface.getX() + 0.5,
                        followSurface.getY(), followSurface.getZ() + 0.5, 0.92);
            } else if (merchant.distanceToSqr(leader) > 2.15 * 2.15) {
                // Do not make every follower target the leader's exact feet. On narrow or
                // obstructed roads that old fallback visibly stacked the group into one NPC.
                merchant.getNavigation().moveTo(followX, leader.getY(), followZ, 0.82);
            } else {
                merchant.getNavigation().stop();
            }
            if (tooCloseToAny(merchant.blockPosition(), formationOccupied, 1.75)) {
                List<BlockPos> separationSlots = findFormationSlots(
                        world, leader.blockPosition(), 1, formationOccupied);
                if (!separationSlots.isEmpty()) {
                    BlockPos slot = separationSlots.getFirst();
                    merchant.getNavigation().moveTo(slot.getX() + 0.5,
                            slot.getY(), slot.getZ() + 0.5, 0.98);
                }
            }
            formationOccupied.add(merchant.blockPosition());
            if (merchant.distanceToSqr(leader)
                    > CARAVAN_SOFT_REGROUP_DISTANCE * (double) CARAVAN_SOFT_REGROUP_DISTANCE) {
                BlockPos regroup = findCaravanSurface(world, leader.blockPosition(), 4);
                if (regroup != null) {
                    merchant.getNavigation().moveTo(regroup.getX() + 0.5,
                            regroup.getY(), regroup.getZ() + 0.5, 1.08);
                    if (merchant.distanceToSqr(leader)
                            > CARAVAN_HARD_REGROUP_DISTANCE * (double) CARAVAN_HARD_REGROUP_DISTANCE
                            && !isRecoveryVisible(observer, merchant)) {
                        teleportMerchant(merchant, regroup);
                    }
                }
            }
        }

        if (leaderPosition.distSqr(runtime.lastExpected)
                > CARAVAN_MAX_ROUTE_DRIFT * (double) CARAVAN_MAX_ROUTE_DRIFT) {
            if (currentEvent != null || !isRecoveryVisible(observer, leader)) {
                if (!recoverCaravan(world, key, runtime, data, observer, currentEvent != null)) {
                    suspendPhysicalCaravan(world, key);
                    return false;
                }
                return true;
            }
        }

        if (runtime.recoveryGraceSeconds > 0) {
            runtime.recoveryGraceSeconds--;
            runtime.stuckSeconds = 0;
        } else if (runtime.lastLeaderPosition != null && targetDistance > CARAVAN_TARGET_DISTANCE_SQR) {
            double moved = leaderPosition.distSqr(runtime.lastLeaderPosition);
            if (!pathRequested || leader.getNavigation().isDone() || moved < CARAVAN_MIN_MOVEMENT_SQR) {
                runtime.stuckSeconds += pathRequested ? 1 : 2;
            } else {
                runtime.stuckSeconds = Math.max(0, runtime.stuckSeconds - 2);
            }
        } else {
            runtime.stuckSeconds = 0;
        }
        runtime.lastLeaderPosition = leaderPosition;
        for (CaravanMerchantEntity merchant : merchants) {
            runtime.lastMerchantPositions.put(merchant.getUUID(), merchant.blockPosition());
        }

        if (runtime.stuckSeconds >= CARAVAN_STUCK_SECONDS) {
            if (currentEvent == null && isRecoveryVisible(observer, leader)) {
                // Never snap a caravan while the correction would be visible. Keep the
                // physical group in place and continue ordinary path attempts until the
                // player has moved far enough away for an off-screen recovery.
                runtime.stuckSeconds = CARAVAN_STUCK_SECONDS - 2;
                return true;
            }
            boolean allowRecovery = currentEvent != null || runtime.recoveryCount < CARAVAN_MAX_RECOVERIES;
            if (!allowRecovery || !recoverCaravan(world, key, runtime, data, observer, currentEvent != null)) {
                suspendPhysicalCaravan(world, key);
                return false;
            }
        }
        return true;
    }

    private static boolean ensureCompleteCaravan(ServerLevel world,
                                                  RouteKey key,
                                                  CaravanRuntime runtime,
                                                  PlayerQuestData data) {
        int desired = desiredMerchantCount(world, key.ownerId());
        int living = livingMerchantCount(world, runtime);
        if (living >= desired) {
            return true;
        }
        BlockPos anchor = runtime.lastActual == null ? runtime.lastExpected : runtime.lastActual;
        if (anchor == null) {
            anchor = routePosition(world, data, key.routeIndex());
        }
        return spawnMissingMerchants(world, key, runtime, desired - living, anchor);
    }

    private static boolean spawnMissingMerchants(ServerLevel world,
                                                 RouteKey key,
                                                 CaravanRuntime runtime,
                                                 int amount,
                                                 BlockPos anchor) {
        if (amount <= 0) {
            return true;
        }
        List<BlockPos> occupied = new ArrayList<>();
        for (UUID merchantId : runtime.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                occupied.add(merchant.blockPosition());
            }
        }
        List<BlockPos> slots = findFormationSlots(world, anchor, amount, occupied);
        if (slots.size() < amount) {
            return false;
        }
        int spawned = 0;
        for (BlockPos spawn : slots) {
            CaravanMerchantEntity merchant = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
            merchant.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            merchant.setHealth(merchant.getMaxHealth());
            merchant.setDespawnTicks(NPC_DESPAWN_TICKS);
            merchant.addTag(TAG_ROUTE_CARAVAN);
            merchant.addTag(ownerTag(key.ownerId()));
            merchant.addTag(routeTag(key.routeIndex()));
            merchant.setRouteIndex(key.routeIndex());
            merchant.setLiveryIndex(routeLivery(data(world, key.ownerId()), key.routeIndex()));
            merchant.refreshEncounterControl(false);
            if (world.noCollision(merchant) && world.addFreshEntity(merchant)) {
                runtime.merchantIds.add(merchant.getUUID());
                runtime.lastMerchantPositions.put(merchant.getUUID(), spawn);
                ENTITY_ROUTES.put(merchant.getUUID(), key);
                spawned++;
            }
        }
        return spawned == amount;
    }

    private static List<BlockPos> findFormationSlots(ServerLevel world,
                                                     BlockPos anchor,
                                                     int amount,
                                                     List<BlockPos> occupied) {
        List<BlockPos> result = new ArrayList<>();
        if (world == null || anchor == null || amount <= 0) {
            return result;
        }
        List<BlockPos> unavailable = new ArrayList<>(occupied);
        for (int ring = 0; ring <= 4 && result.size() < amount; ring++) {
            for (int dx = -ring; dx <= ring && result.size() < amount; dx++) {
                for (int dz = -ring; dz <= ring && result.size() < amount; dz++) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos candidate = safeSurface(world, anchor.getX() + dx, anchor.getZ() + dz);
                    if (candidate == null || Math.abs(candidate.getY() - anchor.getY()) > 1
                            || tooCloseToAny(candidate, unavailable, 2.05)) {
                        continue;
                    }
                    result.add(candidate);
                    unavailable.add(candidate);
                }
            }
        }
        return result;
    }

    private static boolean tooCloseToAny(BlockPos candidate, List<BlockPos> positions, double distance) {
        double maxDistance = distance * distance;
        for (BlockPos position : positions) {
            double dx = candidate.getX() - position.getX();
            double dz = candidate.getZ() - position.getZ();
            if (dx * dx + dz * dz < maxDistance) {
                return true;
            }
        }
        return false;
    }

    private static boolean recoverCaravan(ServerLevel world,
                                           RouteKey key,
                                           CaravanRuntime runtime,
                                           PlayerQuestData data,
                                           ServerPlayer observer,
                                           boolean eventActive) {
        int progress = clampProgress(routeInt(data, key.routeIndex(), "progress"));
        int direction = routeInt(data, key.routeIndex(), "direction") < 0 ? -1 : 1;
        int progressStep = Math.max(80, (int) Math.round(14.0 * PROGRESS_MAX
                / Math.max(96.0, routeDistance(data, key.routeIndex()))));
        int[] offsets = {0, direction * progressStep, -direction * progressStep,
                direction * progressStep * 2, -direction * progressStep * 2};
        BlockPos anchor = null;
        for (int offset : offsets) {
            BlockPos routePoint = routePosition(world, data, key.routeIndex(), clampProgress(progress + offset));
            anchor = findCaravanSurface(world, routePoint, CARAVAN_SPAWN_SEARCH_RADIUS);
            if (anchor != null) {
                break;
            }
        }
        if (anchor == null && eventActive && observer != null) {
            anchor = findCaravanSurface(world, observer.blockPosition(), 12);
        }
        if (anchor == null) {
            return false;
        }
        List<CaravanMerchantEntity> merchants = livingMerchants(world, runtime);
        List<BlockPos> slots = findFormationSlots(world, anchor, merchants.size(), List.of());
        if (slots.size() < merchants.size()) {
            return false;
        }
        for (int i = 0; i < merchants.size(); i++) {
            teleportMerchant(merchants.get(i), slots.get(i));
            runtime.lastMerchantPositions.put(merchants.get(i).getUUID(), slots.get(i));
        }
        runtime.lastActual = slots.isEmpty() ? anchor : slots.getFirst();
        runtime.lastLeaderPosition = runtime.lastActual;
        runtime.stuckSeconds = 0;
        runtime.recoveryGraceSeconds = CARAVAN_RECOVERY_GRACE_SECONDS;
        runtime.recoveryCount++;
        return true;
    }

    private static void teleportMerchant(CaravanMerchantEntity merchant, BlockPos target) {
        merchant.getNavigation().stop();
        merchant.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    }

    private static void suspendPhysicalCaravan(ServerLevel world, RouteKey key) {
        removeRuntime(world, key);
        MATERIALIZATION_RETRY_AT.put(key, world.getGameTime() + MATERIALIZATION_RETRY_TICKS);
    }

    private static int desiredMerchantCount(ServerLevel world, UUID ownerId) {
        return switch (VillageQuestServerConfig.get().caravanVisualMode()) {
            case MAP_ONLY -> 0;
            case REDUCED -> 1;
            case FULL -> hasCaravanYard(world, ownerId) ? 3 : 2;
        };
    }

    private static int livingMerchantCount(ServerLevel world, CaravanRuntime runtime) {
        return livingMerchants(world, runtime).size();
    }

    private static List<CaravanMerchantEntity> livingMerchants(ServerLevel world, CaravanRuntime runtime) {
        List<CaravanMerchantEntity> living = new ArrayList<>();
        if (runtime == null) {
            return living;
        }
        for (UUID merchantId : runtime.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                living.add(merchant);
            }
        }
        return living;
    }

    private static boolean isMaterializationCrowded(ServerLevel world,
                                                    RouteKey key,
                                                    PlayerQuestData data,
                                                    BlockPos expected) {
        boolean eventActive = event(data, key.routeIndex()) != null;
        for (Map.Entry<RouteKey, CaravanRuntime> entry : List.copyOf(ACTIVE_CARAVANS.entrySet())) {
            if (entry.getKey().equals(key) || !runtimeHasLivingMerchant(world, entry.getValue())) {
                continue;
            }
            BlockPos otherPosition = entry.getValue().lastActual == null
                    ? entry.getValue().lastExpected
                    : entry.getValue().lastActual;
            if (otherPosition == null || otherPosition.distSqr(expected)
                    > CARAVAN_CROWD_RADIUS * (double) CARAVAN_CROWD_RADIUS) {
                continue;
            }
            PlayerQuestData otherData = data(world, entry.getKey().ownerId());
            boolean otherEvent = event(otherData, entry.getKey().routeIndex()) != null;
            if (eventActive && !otherEvent) {
                removeRuntime(world, entry.getKey());
                continue;
            }
            return true;
        }
        return false;
    }

    private static BlockPos runtimeInteractionTarget(ServerLevel world, RouteKey key, PlayerQuestData data) {
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null && runtimeHasLivingMerchant(world, runtime) && runtime.lastActual != null) {
            return runtime.lastActual;
        }
        return routePosition(world, data, key.routeIndex());
    }

    private static void updateMerchantRoles(ServerLevel world,
                                            CaravanRuntime runtime,
                                            TradeRouteEventType event,
                                            int routeIndex) {
        int ordinal = 0;
        for (UUID merchantId : runtime.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (!(entity instanceof CaravanMerchantEntity merchant)) {
                continue;
            }
            boolean courier = event == TradeRouteEventType.MISSING_COURIER && ordinal == runtime.merchantIds.size() - 1;
            merchant.setCourier(courier);
            merchant.setRouteIndex(routeIndex);
            String name = CARAVAN_NAMES[Math.floorMod(routeIndex * 2 + ordinal, CARAVAN_NAMES.length)];
            Component baseName = Component.translatable(courier
                    ? "entity.village-quest.route_courier"
                    : "entity.village-quest.route_merchant." + name);
            merchant.setCustomName(event != null && ordinal == 0
                    ? Component.translatable("entity.village-quest.route_merchant.event", baseName, event.label())
                    : baseName);
            merchant.setCustomNameVisible(courier || (event != null && ordinal == 0));
            ordinal++;
        }
    }

    private static void sampleRoadQuality(ServerLevel world, PlayerQuestData data, int routeIndex, BlockPos center) {
        int road = 0;
        int lit = 0;
        int samples = 0;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos surface = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                if (surface == null) {
                    continue;
                }
                samples++;
                if (isRoadBlock(world.getBlockState(surface.below()))) {
                    road++;
                }
                if (world.getBrightness(LightLayer.BLOCK, surface) >= 8) {
                    lit++;
                }
            }
        }
        if (samples == 0) {
            return;
        }
        int measured = Math.min(100, (road * 80 + lit * 20) / samples);
        int current = quality(data, routeIndex);
        int smoothed = Math.max(5, Math.min(100, (current * 7 + measured) / 8));
        if (smoothed != current) {
            setRouteInt(data, routeIndex, "quality", smoothed);
            QuestState.get(world.getServer()).setDirty();
        }
    }

    private static void payArrival(ServerLevel world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        TradeRouteStatus status = status(data, routeIndex);
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        int day = currentWorldDay(world);
        if (data.getTradeRouteInt(INCOME_DAY) != day) {
            data.setTradeRouteInt(INCOME_DAY, day);
            data.setTradeRouteInt(INCOME_TODAY, 0);
        }
        int dailyCap = hasCaravanYard(world, ownerId) ? 60 : 8;
        int remaining = Math.max(0, dailyCap - data.getTradeRouteInt(INCOME_TODAY));
        if (remaining <= 0) {
            TradeGuildService.onRouteArrival(world, ownerId, routeIndex);
            return;
        }
        double distance = routeDistance(data, routeIndex);
        double statusFactor = switch (status) {
            case FLOURISHING -> 1.25;
            case SECURED -> 1.0;
            case DANGEROUS, UNKNOWN -> 0.8;
        };
        double qualityFactor = 0.8 + quality(data, routeIndex) / 250.0;
        int distanceReward = 1 + Math.min(9, (int) Math.floor(distance / 300.0));
        int reward = Math.min(15, Math.max(1, (int) Math.round(distanceReward * statusFactor * qualityFactor)));
        reward = Math.min(reward, remaining);
        data.setTradeRouteInt(INCOME_TODAY, data.getTradeRouteInt(INCOME_TODAY) + reward);
        if (owner != null) {
            CurrencyService.addBalance(world, ownerId, reward);
        } else {
            int escrowCap = hasCaravanYard(world, ownerId) ? 30 : 8;
            data.setTradeRouteInt(ESCROW, Math.min(escrowCap, data.getTradeRouteInt(ESCROW) + reward));
        }
        setRouteInt(data, routeIndex, "earnings", routeInt(data, routeIndex, "earnings") + reward);
        TradeGuildService.onRouteArrival(world, ownerId, routeIndex);
        if (owner != null && ClientPreferenceService.caravanEventNotifications(owner)) {
            owner.sendSystemMessage(Component.translatable("message.village-quest.trade_route.arrived",
                    routeName(routeIndex), CurrencyService.formatDelta(reward)).withStyle(ChatFormatting.GRAY), false);
        }
    }

    public static int collectEscrow(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return 0;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int amount = Math.max(0, data.getTradeRouteInt(ESCROW));
        if (amount <= 0) {
            return 0;
        }
        data.setTradeRouteInt(ESCROW, 0);
        CurrencyService.addBalance(world, player.getUUID(), amount);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.escrow_collected",
                CurrencyService.formatDelta(amount)).withStyle(ChatFormatting.GREEN), false);
        return amount;
    }

    public static int escrow(ServerLevel world, UUID playerId) {
        return world == null || playerId == null ? 0 : Math.max(0, data(world, playerId).getTradeRouteInt(ESCROW));
    }

    public static int incomeToday(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0;
        }
        PlayerQuestData data = data(world, playerId);
        return data.getTradeRouteInt(INCOME_DAY) == currentWorldDay(world)
                ? Math.max(0, data.getTradeRouteInt(INCOME_TODAY)) : 0;
    }

    public static boolean useRoadwardenHorn(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return false;
        if (!hasRouteAccess(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.roadwarden_horn.no_network")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        int today = (int) de.quest.util.TimeUtil.currentDay();
        if (data.getTradeRouteInt(WARDEN_USE_DAY) != today) {
            data.setTradeRouteInt(WARDEN_USE_DAY, today);
            data.setTradeRouteInt(WARDEN_CHARGES, 1);
            QuestState.get(world.getServer()).setDirty();
            world.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.55f, 1.65f);
            player.sendSystemMessage(Component.translatable("message.village-quest.roadwarden_horn.armed")
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            Component target = activeRouteTargetLabel(world, player.getUUID());
            player.sendSystemMessage(target == null
                    ? Component.translatable("message.village-quest.roadwarden_horn.ready")
                    : Component.translatable("message.village-quest.roadwarden_horn.target", target), true);
        }
        SurveyorCompassQuestService.selectRouteEventMode(world, player.getUUID());
        return true;
    }

    public static int routeCount(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return 0;
        return Math.min(routeCapacity(world, playerId), data(world, playerId).getTradeRouteInt(ROUTE_COUNT));
    }

    public static boolean isRegisteredDestination(ServerLevel world, UUID playerId, int x, int z) {
        if (world == null || playerId == null) return false;
        PlayerQuestData data = data(world, playerId);
        int count = Math.min(MAX_ROUTES, Math.max(0, data.getTradeRouteInt(ROUTE_COUNT)));
        for (int route = 0; route < count; route++) {
            if (Math.abs(routeInt(data, route, "x") - x) <= 8
                    && Math.abs(routeInt(data, route, "z") - z) <= 8) return true;
        }
        return false;
    }

    public static boolean hasActiveSurvey(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && activeSurveyIndex(data(world, playerId)) >= 0;
    }

    public static boolean isNearHome(ServerLevel world, UUID playerId, BlockPos pos, int radius) {
        if (world == null || playerId == null || pos == null) return false;
        PlayerQuestData data = data(world, playerId);
        if (!hasHome(data)) return false;
        long dx = (long) pos.getX() - data.getTradeRouteInt(HOME_X);
        long dz = (long) pos.getZ() - data.getTradeRouteInt(HOME_Z);
        return dx * dx + dz * dz <= radius * (long) radius;
    }

    public static boolean isNearAnyNetworkAnchor(ServerLevel world, BlockPos pos, int radius) {
        if (world == null || pos == null) return false;
        int safeRadius = Math.max(0, radius);
        for (var entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            UUID playerId = entry.getKey();
            PlayerQuestData playerData = entry.getValue();
            if (isNearHome(world, playerId, pos, safeRadius)
                    || distanceToNearestRoute(world, playerId, pos) >= 0
                    && distanceToNearestRoute(world, playerId, pos) <= safeRadius) {
                return true;
            }
            int count = Math.min(MAX_ROUTES, Math.max(0, playerData.getTradeRouteInt(ROUTE_COUNT)));
            for (int route = 0; route < count; route++) {
                long dx = (long) routeInt(playerData, route, "x") - pos.getX();
                long dz = (long) routeInt(playerData, route, "z") - pos.getZ();
                if (dx * dx + dz * dz <= safeRadius * (long) safeRadius) return true;
            }
        }
        return false;
    }

    public static int completedGuildContracts(ServerLevel world, UUID playerId) {
        return world == null || playerId == null ? 0
                : Math.max(0, data(world, playerId).getTradeRouteInt("guild_contracts_completed"));
    }

    public static int totalRouteSuccesses(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return 0;
        PlayerQuestData data = data(world, playerId);
        int total = 0;
        for (int route = 0; route < Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT)); route++) {
            total += Math.max(0, routeInt(data, route, "successes"));
        }
        return total;
    }

    public static boolean hasActiveRouteEvent(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return false;
        PlayerQuestData data = data(world, playerId);
        for (int route = 0; route < routeCount(world, playerId); route++) {
            if (event(data, route) != null) return true;
        }
        return false;
    }

    public static int distanceToNearestRoute(ServerLevel world, UUID playerId, BlockPos pos) {
        if (world == null || playerId == null || pos == null) return -1;
        PlayerQuestData data = data(world, playerId);
        int count = Math.min(MAX_ROUTES, Math.max(0, data.getTradeRouteInt(ROUTE_COUNT)));
        if (count == 0) return -1;
        double best = Double.MAX_VALUE;
        for (int route = 0; route < count; route++) {
            List<RoutePoint> path = routePath(data, route);
            for (int point = 1; point < path.size(); point++) {
                best = Math.min(best, distanceToSegment(pos.getX(), pos.getZ(), path.get(point - 1), path.get(point)));
            }
        }
        return best == Double.MAX_VALUE ? -1 : (int) Math.round(best);
    }

    private static double distanceToSegment(double x, double z, RoutePoint from, RoutePoint to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= 0.0001) return Math.hypot(x - from.x(), z - from.z());
        double t = Math.max(0.0, Math.min(1.0, ((x - from.x()) * dx + (z - from.z()) * dz) / lengthSquared));
        return Math.hypot(x - (from.x() + dx * t), z - (from.z() + dz * t));
    }

    public static int routeQuality(ServerLevel world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex) ? quality(data(world, playerId), routeIndex) : 0;
    }

    public static int routeSuccesses(ServerLevel world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? Math.max(0, routeInt(data(world, playerId), routeIndex, "successes")) : 0;
    }

    public static int routeDistanceBlocks(ServerLevel world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? Math.max(0, (int) Math.round(routeDistance(data(world, playerId), routeIndex))) : 0;
    }

    public static Component routeDisplayName(ServerLevel world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? routeName(data(world, playerId), routeIndex)
                : routeName(routeIndex);
    }

    public static boolean hireRoadPatrol(ServerLevel world, ServerPlayer player, int routeIndex, int cycles) {
        if (world == null || player == null || !validRoute(world, player.getUUID(), routeIndex)) return false;
        PlayerQuestData data = data(world, player.getUUID());
        setRouteInt(data, routeIndex, "patrol_cycles",
                Math.max(routeInt(data, routeIndex, "patrol_cycles"), Math.max(1, cycles)));
        QuestState.get(world.getServer()).setDirty();
        refreshUi(world, player);
        return true;
    }

    public static boolean buySurveyReport(ServerLevel world, ServerPlayer player, int routeIndex, int improvement) {
        if (world == null || player == null || !validRoute(world, player.getUUID(), routeIndex)) return false;
        PlayerQuestData data = data(world, player.getUUID());
        setRouteInt(data, routeIndex, "quality", Math.min(100,
                quality(data, routeIndex) + Math.max(1, improvement)));
        QuestState.get(world.getServer()).setDirty();
        refreshUi(world, player);
        return true;
    }

    public static boolean emergencyRecall(ServerLevel world, ServerPlayer player, int routeIndex) {
        if (world == null || player == null || !validRoute(world, player.getUUID(), routeIndex)) return false;
        PlayerQuestData data = data(world, player.getUUID());
        int progress = clampProgress(routeInt(data, routeIndex, "progress"));
        int endpoint = progress < PROGRESS_MAX / 2 ? 0 : PROGRESS_MAX;
        setRouteInt(data, routeIndex, "progress", endpoint);
        setRouteInt(data, routeIndex, "direction", endpoint == 0 ? 1 : -1);
        setRouteInt(data, routeIndex, "event", 0);
        setRouteInt(data, routeIndex, "event_day", 0);
        setRouteInt(data, routeIndex, "event_progress", 0);
        removeRuntime(world, new RouteKey(player.getUUID(), routeIndex));
        QuestState.get(world.getServer()).setDirty();
        refreshUi(world, player);
        return true;
    }

    public static int routeLivery(ServerLevel world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? routeLivery(data(world, playerId), routeIndex)
                : Math.floorMod(routeIndex, MAX_ROUTES);
    }

    public static boolean setRouteLivery(ServerLevel world, ServerPlayer player, int routeIndex, int liveryIndex) {
        if (world == null || player == null || !validRoute(world, player.getUUID(), routeIndex)) return false;
        PlayerQuestData data = data(world, player.getUUID());
        int clamped = Math.max(0, Math.min(MAX_ROUTES - 1, liveryIndex));
        setRouteInt(data, routeIndex, "livery", clamped + 1);
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(new RouteKey(player.getUUID(), routeIndex));
        if (runtime != null) {
            for (CaravanMerchantEntity merchant : livingMerchants(world, runtime)) {
                merchant.setLiveryIndex(clamped);
            }
        }
        QuestState.get(world.getServer()).setDirty();
        refreshUi(world, player);
        player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.livery_applied",
                routeName(data, routeIndex)).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static TradeRouteSpecialization specialization(ServerLevel world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? TradeRouteSpecialization.fromId(routeInt(data(world, playerId), routeIndex, "specialization"))
                : TradeRouteSpecialization.GENERAL;
    }

    public static boolean specialize(ServerLevel world, ServerPlayer player, int routeIndex,
                                     TradeRouteSpecialization specialization) {
        if (world == null || player == null || specialization == null
                || !validRoute(world, player.getUUID(), routeIndex)) return false;
        PlayerQuestData data = data(world, player.getUUID());
        if (specialization(world, player.getUUID(), routeIndex) == specialization) return false;
        String chosenFlag = routeKey(routeIndex, "specialization_chosen");
        int cost = data.hasTradeRouteFlag(chosenFlag) ? 15 : 0;
        if (cost > 0 && !CurrencyService.removeBalance(world, player.getUUID(), cost)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.not_enough",
                    CurrencyService.formatBalance(cost)).withStyle(ChatFormatting.RED), false);
            return false;
        }
        setRouteInt(data, routeIndex, "specialization", specialization.id());
        data.setTradeRouteFlag(chosenFlag, true);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.specialized",
                routeName(routeIndex), specialization.label(), CurrencyService.formatBalance(cost))
                .withStyle(ChatFormatting.GREEN), false);
        refreshUi(world, player);
        return true;
    }

    public static boolean hasUpgrade(ServerLevel world, UUID playerId, int routeIndex, TradeRouteUpgrade upgrade) {
        return validRoute(world, playerId, routeIndex) && hasUpgrade(data(world, playerId), routeIndex, upgrade);
    }

    public static boolean buyUpgrade(ServerLevel world, ServerPlayer player, int routeIndex, TradeRouteUpgrade upgrade) {
        if (world == null || player == null || upgrade == null || !validRoute(world, player.getUUID(), routeIndex)) return false;
        if (TradeGuildService.guildRank(world, player.getUUID()) < upgrade.requiredGuildRank()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.rank_locked",
                    upgrade.requiredGuildRank()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (hasUpgrade(data, routeIndex, upgrade)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.upgrade_owned")
                    .withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        long actualCost = ProsperityService.routeUpgradePrice(world, player.getUUID(), upgrade.cost());
        if (!CurrencyService.removeBalance(world, player.getUUID(), actualCost)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.not_enough",
                    CurrencyService.formatBalance(actualCost)).withStyle(ChatFormatting.RED), false);
            return false;
        }
        int mask = routeInt(data, routeIndex, "upgrades") | upgrade.bit();
        setRouteInt(data, routeIndex, "upgrades", mask);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.trade_guild.upgrade_bought",
                upgrade.label(), routeName(routeIndex), CurrencyService.formatBalance(actualCost))
                .withStyle(ChatFormatting.GREEN), false);
        refreshUi(world, player);
        return true;
    }

    private static boolean validRoute(ServerLevel world, UUID playerId, int routeIndex) {
        return routeIndex >= 0 && routeIndex < routeCount(world, playerId);
    }

    private static boolean hasUpgrade(PlayerQuestData data, int routeIndex, TradeRouteUpgrade upgrade) {
        return upgrade != null && (routeInt(data, routeIndex, "upgrades") & upgrade.bit()) != 0;
    }

    private static Component upgradeSummary(PlayerQuestData data, int routeIndex) {
        List<String> names = new ArrayList<>();
        for (TradeRouteUpgrade upgrade : TradeRouteUpgrade.values()) {
            if (hasUpgrade(data, routeIndex, upgrade)) names.add(upgrade.label().getString());
        }
        return names.isEmpty()
                ? Component.translatable("text.village-quest.trade_guild.upgrades_none")
                : Component.literal(String.join(", ", names));
    }

    private static boolean hasActiveEvent(PlayerQuestData data) {
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            if (event(data, i) != null) {
                return true;
            }
        }
        return false;
    }

    private static int eventReward(PlayerQuestData data, int routeIndex, TradeRouteEventType event) {
        int difficulty = switch (event) {
            case HUNGRY_TRAVELERS, MISSING_COURIER -> 0;
            case BROKEN_WHEEL, INJURED_PACK_ANIMAL, ROAD_TOLL -> 1;
            case WASHED_OUT_BRIDGE, STORM_CAMP -> 2;
            case FALSE_DISTRESS -> 3;
            case SHATTERED_WAYSTONE, SHRINE_PILGRIMS -> 2;
            case RUNES_GONE_DARK -> 3;
        };
        return Math.max(6, Math.min(14, 5 + difficulty * 2
                + (int) Math.floor(routeDistance(data, routeIndex) / 650.0)));
    }

    private static Payloads.TradeRouteMapPayload buildMapPayload(ServerLevel world, UUID ownerId, int action) {
        PlayerQuestData data = data(world, ownerId);
        List<Payloads.TradeRouteNodeData> nodes = new ArrayList<>();
        List<Payloads.TradeRouteLineData> routes = new ArrayList<>();
        List<Payloads.TradeRouteCaravanData> caravans = new ArrayList<>();
        if (hasHome(data)) {
            boolean playerYard = isPlayerYard(data);
            nodes.add(new Payloads.TradeRouteNodeData(0,
                    Component.translatable(playerYard
                            ? "text.village-quest.trade_route.node.homestead"
                            : "text.village-quest.trade_route.node.caravan_yard"),
                    data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z), true, playerYard));
        }
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            nodes.add(new Payloads.TradeRouteNodeData(i + 1,
                    villageName(data, i),
                    routeInt(data, i, "x"), routeInt(data, i, "z"), false, false));
            TradeRouteEventType event = event(data, i);
            boolean surveying = activeSurveyIndex(data) == i;
            List<Payloads.TradeRoutePointData> mapWaypoints = (surveying
                    ? surveyPointsWithModes(data)
                    : routeWaypointsWithModes(data, i)).stream()
                    .map(point -> new Payloads.TradeRoutePointData(
                            point.point().x(), point.point().z(), point.ocean()))
                    .toList();
            routes.add(new Payloads.TradeRouteLineData(
                    i,
                    routeLivery(data, i),
                    routeName(data, i),
                    status(data, i).id(),
                    status(data, i).label(),
                    quality(data, i),
                    clampProgress(routeInt(data, i, "progress")),
                    routeInt(data, i, "direction") < 0,
                    isStopped(data, i),
                    surveying,
                    event == null ? Component.empty() : event.label(),
                    event == null ? Component.empty() : event.help(),
                    Math.max(0, routeInt(data, i, "earnings")),
                    specialization(world, ownerId, i).label(),
                    upgradeSummary(data, i),
                    mapWaypoints
            ));
            RouteKey key = new RouteKey(ownerId, i);
            int direction = routeInt(data, i, "direction") < 0 ? -1 : 1;
            FerryState ferry = ferryState(data, i,
                    clampProgress(routeInt(data, i, "progress")), direction);
            CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
            boolean materialized = runtime != null && runtimeHasLivingMerchant(world, runtime);
            boolean boarding = materialized && ferryBoardingAtProgress(data, i,
                    clampProgress(routeInt(data, i, "progress")), direction) != null;
            caravans.add(new Payloads.TradeRouteCaravanData(
                    i,
                    clampProgress(routeInt(data, i, "progress")),
                    direction < 0,
                    materialized,
                    boarding,
                    ferry.active(),
                    ferry.secondsRemaining()
            ));
        }
        return new Payloads.TradeRouteMapPayload(
                action,
                Component.translatable("screen.village-quest.trade_route.title"),
                Component.translatable("screen.village-quest.trade_route.summary_guild",
                        count, routeCapacity(world, ownerId),
                        TradeGuildService.rankLabel(TradeGuildService.guildRank(world, ownerId)),
                        incomeToday(world, ownerId)),
                List.copyOf(nodes),
                List.copyOf(routes),
                List.copyOf(caravans),
                VillageBondService.bondPayloads(world, ownerId),
                VillageBondService.shrinePayloads(world, ownerId, -1),
                VillageBondService.decorationPayloads(world, ownerId)
        );
    }

    private static void tickMapViewers(ServerLevel world, long gameTime) {
        if (gameTime % MAP_UPDATE_TICKS != 0L) {
            return;
        }
        for (UUID viewerId : List.copyOf(MAP_VIEWERS)) {
            ServerPlayer viewer = world.getServer().getPlayerList().getPlayer(viewerId);
            if (viewer == null || viewer.level() != world || !hasRouteAccess(world, viewerId)) {
                MAP_VIEWERS.remove(viewerId);
                continue;
            }
            ServerPlayNetworking.send(viewer, buildMapPayload(world, viewerId, Payloads.TradeRouteMapPayload.ACTION_UPDATE));
        }
        for (UUID viewerId : List.copyOf(MINIMAP_VIEWERS)) {
            ServerPlayer viewer = world.getServer().getPlayerList().getPlayer(viewerId);
            if (viewer == null || viewer.level() != world || !hasRouteAccess(world, viewerId)) {
                MINIMAP_VIEWERS.remove(viewerId);
                if (viewer != null) {
                    ServerPlayNetworking.send(viewer, buildMapPayload(world, viewerId,
                            Payloads.TradeRouteMapPayload.ACTION_MINIMAP_DISABLE));
                }
                continue;
            }
            ServerPlayNetworking.send(viewer, buildMapPayload(world, viewerId,
                    Payloads.TradeRouteMapPayload.ACTION_MINIMAP_UPDATE));
        }
    }

    private static void cleanupInactiveCaravans(ServerLevel world) {
        for (Map.Entry<RouteKey, CaravanRuntime> entry : List.copyOf(ACTIVE_CARAVANS.entrySet())) {
            if (!runtimeHasLivingMerchant(world, entry.getValue())) {
                removeRuntime(world, entry.getKey());
            }
        }
        for (Entity entity : allEntities(world)) {
            if (entity.entityTags().contains(TAG_ROUTE_CARAVAN)) {
                RouteKey key = ENTITY_ROUTES.get(entity.getUUID());
                CaravanRuntime runtime = key == null ? null : ACTIVE_CARAVANS.get(key);
                if (runtime != null && runtime.merchantIds.contains(entity.getUUID())) {
                    continue;
                }
                ENTITY_ROUTES.remove(entity.getUUID());
                entity.discard();
            } else if (entity.entityTags().contains(TAG_ROUTE_ATTACKER)) {
                RouteKey key = ATTACKER_ROUTES.get(entity.getUUID());
                CaravanRuntime runtime = key == null ? null : ACTIVE_CARAVANS.get(key);
                if (runtime != null && runtime.attackerIds.contains(entity.getUUID())) {
                    continue;
                }
                ATTACKER_ROUTES.remove(entity.getUUID());
                entity.discard();
            }
        }
        MATERIALIZATION_RETRY_AT.entrySet().removeIf(entry -> entry.getValue() <= world.getGameTime()
                && !ACTIVE_CARAVANS.containsKey(entry.getKey()));
    }

    private static void removeOwnerRuntimes(ServerLevel world, UUID ownerId) {
        MATERIALIZATION_RETRY_AT.keySet().removeIf(key -> key.ownerId().equals(ownerId));
        for (RouteKey key : List.copyOf(ACTIVE_CARAVANS.keySet())) {
            if (key.ownerId().equals(ownerId)) {
                removeRuntime(world, key);
            }
        }
        String ownerTag = ownerTag(ownerId);
        for (Entity entity : allEntities(world)) {
            if (entity.entityTags().contains(ownerTag)
                    && (entity.entityTags().contains(TAG_ROUTE_CARAVAN)
                    || entity.entityTags().contains(TAG_ROUTE_ATTACKER))) {
                ENTITY_ROUTES.remove(entity.getUUID());
                ATTACKER_ROUTES.remove(entity.getUUID());
                entity.discard();
            }
        }
    }

    private static void removeRuntime(ServerLevel world, RouteKey key) {
        MATERIALIZATION_RETRY_AT.remove(key);
        CaravanRuntime runtime = ACTIVE_CARAVANS.remove(key);
        if (runtime != null) {
            discardRuntimeEntities(world, runtime);
        }
    }

    private static void discardRouteEntities(ServerLevel world, RouteKey key) {
        removeRuntime(world, key);
        String owner = ownerTag(key.ownerId());
        String route = routeTag(key.routeIndex());
        for (Entity entity : allEntities(world)) {
            RouteKey mapped = ENTITY_ROUTES.get(entity.getUUID());
            if (mapped == null) {
                mapped = ATTACKER_ROUTES.get(entity.getUUID());
            }
            boolean tagged = entity.entityTags().contains(owner) && entity.entityTags().contains(route)
                    && (entity.entityTags().contains(TAG_ROUTE_CARAVAN)
                    || entity.entityTags().contains(TAG_ROUTE_ATTACKER));
            if (key.equals(mapped) || tagged) {
                ENTITY_ROUTES.remove(entity.getUUID());
                ATTACKER_ROUTES.remove(entity.getUUID());
                entity.discard();
            }
        }
    }

    private static boolean isRecoveryVisible(ServerPlayer observer, Entity entity) {
        return observer != null && entity != null && observer.distanceToSqr(entity)
                <= CARAVAN_VISIBLE_RECOVERY_RADIUS * (double) CARAVAN_VISIBLE_RECOVERY_RADIUS;
    }

    private static void discardRuntimeEntities(ServerLevel world, CaravanRuntime runtime) {
        for (UUID merchantId : runtime.merchantIds) {
            ENTITY_ROUTES.remove(merchantId);
            Entity entity = findEntity(world, merchantId);
            if (entity != null) {
                entity.discard();
            }
        }
        discardAttackers(world, runtime);
    }

    private static void discardAttackers(ServerLevel world, CaravanRuntime runtime) {
        for (UUID attackerId : List.copyOf(runtime.attackerIds)) {
            ATTACKER_ROUTES.remove(attackerId);
            Entity entity = findEntity(world, attackerId);
            if (entity != null) {
                entity.discard();
            }
        }
        runtime.attackerIds.clear();
    }

    private static boolean runtimeHasLivingMerchant(ServerLevel world, CaravanRuntime runtime) {
        if (runtime == null) {
            return false;
        }
        for (UUID merchantId : runtime.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos routePosition(ServerLevel world, PlayerQuestData data, int routeIndex) {
        return routePosition(world, data, routeIndex, clampProgress(routeInt(data, routeIndex, "progress")));
    }

    private static BlockPos routePosition(ServerLevel world, PlayerQuestData data, int routeIndex, int progress) {
        RoutePoint point = pointAlongRoute(data, routeIndex, progress);
        int x = point.x();
        int z = point.z();
        BlockPos probe = new BlockPos(x, 64, z);
        int y = world.hasChunkAt(probe)
                ? world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                : 64;
        return new BlockPos(x, y, z);
    }

    private static RoutePoint pointAlongRoute(PlayerQuestData data, int routeIndex, int progress) {
        List<RoutePoint> path = routePath(data, routeIndex);
        if (path.size() < 2) {
            return path.isEmpty() ? new RoutePoint(0, 0) : path.getFirst();
        }
        double totalDistance = pathDistance(path);
        if (totalDistance <= 0.0) {
            return path.getFirst();
        }
        double remaining = totalDistance * clampProgress(progress) / PROGRESS_MAX;
        for (int i = 1; i < path.size(); i++) {
            RoutePoint from = path.get(i - 1);
            RoutePoint to = path.get(i);
            double segmentDistance = from.distance(to);
            if (segmentDistance <= 0.0) {
                continue;
            }
            if (remaining <= segmentDistance) {
                double t = remaining / segmentDistance;
                return new RoutePoint(
                        (int) Math.round(from.x() + (to.x() - from.x()) * t),
                        (int) Math.round(from.z() + (to.z() - from.z()) * t)
                );
            }
            remaining -= segmentDistance;
        }
        return path.getLast();
    }

    private static double routeDistance(PlayerQuestData data, int routeIndex) {
        return pathDistance(routePath(data, routeIndex));
    }

    private static double pathDistance(List<RoutePoint> points) {
        double distance = 0.0;
        for (int i = 1; i < points.size(); i++) {
            distance += points.get(i - 1).distance(points.get(i));
        }
        return distance;
    }

    private static List<RoutePoint> routePath(PlayerQuestData data, int routeIndex) {
        return routePathWithModes(data, routeIndex).stream().map(RouteSurveyPoint::point).toList();
    }

    private static List<RouteSurveyPoint> routePathWithModes(PlayerQuestData data, int routeIndex) {
        List<RouteSurveyPoint> points = new ArrayList<>();
        points.add(new RouteSurveyPoint(new RoutePoint(
                data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z)), false));
        points.addAll(routeWaypointsWithModes(data, routeIndex));
        points.add(new RouteSurveyPoint(new RoutePoint(
                routeInt(data, routeIndex, "x"), routeInt(data, routeIndex, "z")), false));
        return points;
    }

    private static FerryState ferryState(PlayerQuestData data,
                                          int routeIndex,
                                          int progress,
                                          int direction) {
        List<RouteSurveyPoint> path = routePathWithModes(data, routeIndex);
        if (path.size() < 2) {
            return FerryState.NONE;
        }
        double totalDistance = pathDistance(path.stream().map(RouteSurveyPoint::point).toList());
        if (totalDistance <= 0.0) {
            return FerryState.NONE;
        }
        if (isLandNodeProgress(path, progress, totalDistance)) {
            return FerryState.NONE;
        }
        double traveled = totalDistance * clampProgress(progress) / PROGRESS_MAX;
        double cursor = 0.0;
        int activeSegment = -1;
        for (int segment = 1; segment < path.size(); segment++) {
            double length = path.get(segment - 1).point().distance(path.get(segment).point());
            if (traveled <= cursor + length || segment == path.size() - 1) {
                activeSegment = segment;
                break;
            }
            cursor += length;
        }
        if (activeSegment < 1 || !(path.get(activeSegment - 1).ocean() || path.get(activeSegment).ocean())) {
            return FerryState.NONE;
        }

        double remaining;
        if (direction >= 0) {
            double ferryEnd = cursor + path.get(activeSegment - 1).point().distance(path.get(activeSegment).point());
            for (int segment = activeSegment + 1; segment < path.size(); segment++) {
                if (!(path.get(segment - 1).ocean() || path.get(segment).ocean())) {
                    break;
                }
                ferryEnd += path.get(segment - 1).point().distance(path.get(segment).point());
            }
            remaining = Math.max(0.0, ferryEnd - traveled);
        } else {
            double ferryStart = cursor;
            for (int segment = activeSegment - 1; segment >= 1; segment--) {
                if (!(path.get(segment - 1).ocean() || path.get(segment).ocean())) {
                    break;
                }
                ferryStart -= path.get(segment - 1).point().distance(path.get(segment).point());
            }
            remaining = Math.max(0.0, traveled - ferryStart);
        }
        int seconds = Math.max(1, (int) Math.ceil(remaining / Math.max(0.1,
                routeBlocksPerSecond(data, routeIndex))));
        return new FerryState(true, seconds);
    }

    private static boolean shouldWaitForPhysicalBoarding(ServerLevel world,
                                                          RouteKey key,
                                                          FerryBoarding boarding) {
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime == null || !runtimeHasLivingMerchant(world, runtime)) {
            return false;
        }
        BlockPos dockProbe = new BlockPos(boarding.point().x(), 64, boarding.point().z());
        if (!world.hasChunkAt(dockProbe)) {
            return false;
        }
        BlockPos dock = new BlockPos(boarding.point().x(),
                world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        boarding.point().x(), boarding.point().z()),
                boarding.point().z());
        BlockPos actual = runtime.lastActual == null ? dock : runtime.lastActual;
        return nearestPlayer(world, dock, MATERIALIZE_RADIUS) != null
                || nearestPlayer(world, actual, MATERIALIZE_RADIUS) != null;
    }

    private static FerryBoarding crossedFerryBoarding(PlayerQuestData data,
                                                       int routeIndex,
                                                       int progress,
                                                       int proposedProgress,
                                                       int direction) {
        for (FerryBoarding boarding : ferryBoardings(data, routeIndex, direction)) {
            if (direction >= 0
                    ? progress <= boarding.progress() && proposedProgress > boarding.progress()
                    : progress >= boarding.progress() && proposedProgress < boarding.progress()) {
                return boarding;
            }
        }
        return null;
    }

    private static FerryBoarding ferryBoardingAtProgress(PlayerQuestData data,
                                                          int routeIndex,
                                                          int progress,
                                                          int direction) {
        for (FerryBoarding boarding : ferryBoardings(data, routeIndex, direction)) {
            if (boarding.progress() == clampProgress(progress)) {
                return boarding;
            }
        }
        return null;
    }

    private static List<FerryBoarding> ferryBoardings(PlayerQuestData data,
                                                       int routeIndex,
                                                       int direction) {
        List<RouteSurveyPoint> path = routePathWithModes(data, routeIndex);
        if (path.size() < 2) {
            return List.of();
        }
        double totalDistance = pathDistance(path.stream().map(RouteSurveyPoint::point).toList());
        if (totalDistance <= 0.0) {
            return List.of();
        }
        List<FerryBoarding> boardings = new ArrayList<>();
        double cumulative = 0.0;
        for (int node = 0; node < path.size(); node++) {
            RouteSurveyPoint point = path.get(node);
            if (!point.ocean()) {
                boolean departingFerry = direction >= 0
                        ? node < path.size() - 1 && isFerrySegment(path.get(node), path.get(node + 1))
                        : node > 0 && isFerrySegment(path.get(node - 1), path.get(node));
                if (departingFerry) {
                    boardings.add(new FerryBoarding(point.point(), progressAtDistance(cumulative, totalDistance)));
                }
            }
            if (node < path.size() - 1) {
                cumulative += point.point().distance(path.get(node + 1).point());
            }
        }
        return List.copyOf(boardings);
    }

    private static boolean isLandNodeProgress(List<RouteSurveyPoint> path,
                                              int progress,
                                              double totalDistance) {
        double cumulative = 0.0;
        for (int node = 0; node < path.size(); node++) {
            if (!path.get(node).ocean()
                    && progressAtDistance(cumulative, totalDistance) == clampProgress(progress)) {
                return true;
            }
            if (node < path.size() - 1) {
                cumulative += path.get(node).point().distance(path.get(node + 1).point());
            }
        }
        return false;
    }

    private static boolean isFerrySegment(RouteSurveyPoint from, RouteSurveyPoint to) {
        return from.ocean() || to.ocean();
    }

    private static int progressAtDistance(double distance, double totalDistance) {
        if (totalDistance <= 0.0) {
            return 0;
        }
        return clampProgress((int) Math.round(distance * PROGRESS_MAX / totalDistance));
    }

    private static BlockPos resolveFerryDock(ServerLevel world,
                                             CaravanRuntime runtime,
                                             PlayerQuestData data,
                                             int routeIndex,
                                             int direction,
                                             FerryBoarding boarding) {
        if (runtime.boardingAnchor != null
                && runtime.boardingProgress == boarding.progress()
                && runtime.boardingDirection == direction
                && safeSurface(world, runtime.boardingAnchor.getX(), runtime.boardingAnchor.getZ())
                != null
                && isStableCaravanSurface(world, runtime.boardingAnchor)) {
            return runtime.boardingAnchor;
        }
        BlockPos routeDock = routePosition(world, data, routeIndex, boarding.progress());
        BlockPos safeDock = findCaravanSurface(world, routeDock, FERRY_DOCK_SEARCH_RADIUS);
        if (safeDock == null) {
            clearFerryDock(runtime);
            return null;
        }
        runtime.boardingAnchor = safeDock;
        runtime.boardingProgress = boarding.progress();
        runtime.boardingDirection = direction;
        return runtime.boardingAnchor;
    }

    private static void clearFerryDock(CaravanRuntime runtime) {
        runtime.boardingAnchor = null;
        runtime.boardingProgress = -1;
        runtime.boardingDirection = 0;
    }

    private static BlockPos findNearbyRoadSurface(ServerLevel world, BlockPos center, int radius) {
        if (world == null || center == null || !world.hasChunkAt(center)) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                BlockPos surface = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                if (surface == null
                        || Math.abs(surface.getY() - center.getY()) > 4
                        || !isStableCaravanSurface(world, surface)
                        || !isRoadBlock(world.getBlockState(surface.below()))) {
                    continue;
                }
                double distance = surface.distSqr(center);
                if (distance < bestDistance) {
                    best = surface;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static BlockPos safeSurface(ServerLevel world, int x, int z) {
        BlockPos probe = new BlockPos(x, 64, z);
        if (!world.hasChunkAt(probe)) {
            return null;
        }
        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, topY, z);
        if (!world.getBlockState(feet).isAir()) {
            feet = feet.above();
        }
        BlockPos below = feet.below();
        BlockState belowState = world.getBlockState(below);
        if (!world.getBlockState(feet).isAir()
                || !world.getBlockState(feet.above()).isAir()
                || belowState.isAir()
                || !belowState.getFluidState().isEmpty()
                || belowState.is(BlockTags.LEAVES)
                || isDangerousSupport(belowState)
                || !belowState.isFaceSturdy(world, below, net.minecraft.core.Direction.UP)) {
            return null;
        }
        return feet;
    }

    private static BlockPos findCaravanSurface(ServerLevel world, BlockPos center, int radius) {
        if (world == null || center == null) {
            return null;
        }
        BlockPos fallback = null;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos candidate = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                    if (candidate == null || !isStableCaravanSurface(world, candidate)) {
                        continue;
                    }
                    if (isRoadBlock(world.getBlockState(candidate.below()))) {
                        return candidate;
                    }
                    if (fallback == null) {
                        fallback = candidate;
                    }
                }
            }
            if (fallback != null && ring >= 2) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean isStableCaravanSurface(ServerLevel world, BlockPos center) {
        if (center == null) {
            return false;
        }
        int stableNeighbors = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos neighbor = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                if (neighbor != null && Math.abs(neighbor.getY() - center.getY()) <= 1) {
                    stableNeighbors++;
                }
            }
        }
        return stableNeighbors >= 5;
    }

    private static boolean isDangerousSupport(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW);
    }

    private static BlockPos findNearbySafeSurface(ServerLevel world, int x, int z, int radius) {
        BlockPos center = safeSurface(world, x, z);
        if (center != null) {
            return center;
        }
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos candidate = safeSurface(world, x + dx, z + dz);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isRoadBlock(BlockState state) {
        return state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.SPRUCE_PLANKS)
                || state.is(Blocks.STONE_SLAB)
                || state.is(Blocks.COBBLESTONE_SLAB)
                || state.is(Blocks.OAK_SLAB)
                || state.is(Blocks.SPRUCE_SLAB);
    }

    private static boolean isPlank(ItemStack stack) {
        return stack.is(Items.OAK_PLANKS)
                || stack.is(Items.SPRUCE_PLANKS)
                || stack.is(Items.BIRCH_PLANKS)
                || stack.is(Items.JUNGLE_PLANKS)
                || stack.is(Items.ACACIA_PLANKS)
                || stack.is(Items.DARK_OAK_PLANKS)
                || stack.is(Items.MANGROVE_PLANKS)
                || stack.is(Items.CHERRY_PLANKS)
                || stack.is(Items.BAMBOO_PLANKS)
                || stack.is(Items.PALE_OAK_PLANKS);
    }

    private static boolean consumePair(ServerPlayer player,
                                       Predicate<ItemStack> first,
                                       int firstAmount,
                                       Predicate<ItemStack> second,
                                       int secondAmount) {
        if (countInventory(player, first) < firstAmount || countInventory(player, second) < secondAmount) {
            return false;
        }
        return consumeInventory(player, first, firstAmount) && consumeInventory(player, second, secondAmount);
    }

    private static int countInventory(ServerPlayer player, Predicate<ItemStack> matcher) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consumeInventory(ServerPlayer player, Predicate<ItemStack> matcher, int amount) {
        if (amount <= 0 || countInventory(player, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return remaining == 0;
    }

    public static void backfillUnlockedLedger(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUUID())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(LEDGER_GRANT_RECORDED)) {
            return;
        }
        boolean granted = grantLedger(world, player);
        if (granted) {
            player.sendSystemMessage(Component.translatable("message.village-quest.trade_route.provisional_unlocked")
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    private static boolean grantLedger(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.CARAVAN_LEDGER == null) {
            return false;
        }
        boolean alreadyPresent = hasLedger(player);
        giveLedger(player);
        PlayerQuestData data = data(world, player.getUUID());
        data.setMilestoneFlag(LEDGER_GRANT_RECORDED, true);
        QuestState.get(world.getServer()).setDirty();
        return !alreadyPresent;
    }

    private static boolean hasLedger(ServerPlayer player) {
        if (player == null || ModItems.CARAVAN_LEDGER == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.CARAVAN_LEDGER)) {
                return true;
            }
        }
        return false;
    }

    private static void giveLedger(ServerPlayer player) {
        if (player == null || ModItems.CARAVAN_LEDGER == null) {
            return;
        }
        if (hasLedger(player)) {
            return;
        }
        ServerLevel world = (ServerLevel) player.level();
        ItemStack ledger = GuildArchiveService.issueInitial(world, player, ArchiveItem.CARAVAN_LEDGER,
                new ItemStack(ModItems.CARAVAN_LEDGER));
        if (!player.getInventory().add(ledger)) {
            player.drop(ledger, false);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void giveTestWayfinder(ServerPlayer player) {
        if (player == null || ModItems.SURVEYORS_COMPASS == null) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.SURVEYORS_COMPASS)) {
                return;
            }
        }
        ItemStack compass = new ItemStack(ModItems.SURVEYORS_COMPASS);
        if (!player.getInventory().add(compass)) {
            player.drop(compass, false);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void bindVillageHome(PlayerQuestData data, int x, int z) {
        data.setTradeRouteInt(HOME_X, x);
        data.setTradeRouteInt(HOME_Z, z);
        data.setTradeRouteFlag(HOME_BOUND, true);
        data.setTradeRouteFlag(HOME_PLAYER_YARD, false);
    }

    private static void bindPlayerYard(PlayerQuestData data, int x, int z) {
        data.setTradeRouteInt(HOME_X, x);
        data.setTradeRouteInt(HOME_Z, z);
        data.setTradeRouteFlag(HOME_BOUND, true);
        data.setTradeRouteFlag(HOME_PLAYER_YARD, true);
    }

    private static int activeSurveyIndex(PlayerQuestData data) {
        int stored = data == null ? 0 : data.getTradeRouteInt(SURVEY_ROUTE);
        return stored <= 0 ? -1 : stored - 1;
    }

    private static List<RouteSurveyPoint> routeWaypointsWithModes(PlayerQuestData data, int routeIndex) {
        int count = Math.min(MAX_WAYPOINTS, Math.max(0, routeInt(data, routeIndex, "waypoint_count")));
        List<RouteSurveyPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new RouteSurveyPoint(new RoutePoint(
                    routeInt(data, routeIndex, "waypoint_" + i + "_x"),
                    routeInt(data, routeIndex, "waypoint_" + i + "_z")
            ), data.hasTradeRouteFlag(routeKey(routeIndex, "waypoint_" + i + "_ocean"))));
        }
        return points;
    }

    private static List<RoutePoint> routeWaypoints(PlayerQuestData data, int routeIndex) {
        return routeWaypointsWithModes(data, routeIndex).stream().map(RouteSurveyPoint::point).toList();
    }

    private static void setRouteWaypoints(PlayerQuestData data, int routeIndex, List<RoutePoint> points) {
        List<RouteSurveyPoint> routed = points == null ? List.of() : points.stream()
                .map(point -> new RouteSurveyPoint(point, false))
                .toList();
        setRouteWaypointsWithModes(data, routeIndex, routed);
    }

    private static void setRouteWaypointsWithModes(PlayerQuestData data,
                                                    int routeIndex,
                                                    List<RouteSurveyPoint> points) {
        String prefix = routeKey(routeIndex, "waypoint_");
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.startsWith(prefix)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        for (String flag : List.copyOf(data.getTradeRouteFlags())) {
            if (flag.startsWith(prefix)) {
                data.setTradeRouteFlag(flag, false);
            }
        }
        int count = Math.min(MAX_WAYPOINTS, points == null ? 0 : points.size());
        setRouteInt(data, routeIndex, "waypoint_count", count);
        for (int i = 0; i < count; i++) {
            RouteSurveyPoint routed = points.get(i);
            RoutePoint point = routed.point();
            setRouteInt(data, routeIndex, "waypoint_" + i + "_x", point.x());
            setRouteInt(data, routeIndex, "waypoint_" + i + "_z", point.z());
            data.setTradeRouteFlag(routeKey(routeIndex, "waypoint_" + i + "_ocean"), routed.ocean());
        }
    }

    private static List<RouteSurveyPoint> surveyPointsWithModes(PlayerQuestData data) {
        int count = Math.min(MAX_WAYPOINTS, Math.max(0, data.getTradeRouteInt(SURVEY_POINT_COUNT)));
        List<RouteSurveyPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(surveyPointWithMode(data, i));
        }
        return points;
    }

    private static List<RoutePoint> surveyPoints(PlayerQuestData data) {
        return surveyPointsWithModes(data).stream().map(RouteSurveyPoint::point).toList();
    }

    private static RouteSurveyPoint surveyPointWithMode(PlayerQuestData data, int pointIndex) {
        return new RouteSurveyPoint(new RoutePoint(
                data.getTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_x"),
                data.getTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_z")
        ), data.hasTradeRouteFlag(SURVEY_POINT_PREFIX + pointIndex + "_ocean"));
    }

    private static RoutePoint surveyPoint(PlayerQuestData data, int pointIndex) {
        return surveyPointWithMode(data, pointIndex).point();
    }

    private static void setSurveyPoint(PlayerQuestData data, int pointIndex, RoutePoint point, boolean ocean) {
        data.setTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_x", point.x());
        data.setTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_z", point.z());
        data.setTradeRouteFlag(SURVEY_POINT_PREFIX + pointIndex + "_ocean", ocean);
    }

    private static List<RouteSurveyPoint> normalizedSurveyPoints(PlayerQuestData data, int routeIndex) {
        RoutePoint home = new RoutePoint(data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z));
        RoutePoint destination = new RoutePoint(routeInt(data, routeIndex, "x"), routeInt(data, routeIndex, "z"));
        List<RouteSurveyPoint> normalized = new ArrayList<>();
        RoutePoint previous = home;
        for (RouteSurveyPoint routed : surveyPointsWithModes(data)) {
            RoutePoint point = routed.point();
            if (previous.distanceSquared(point) < 16.0) {
                continue;
            }
            normalized.add(routed);
            previous = point;
        }
        if (!normalized.isEmpty() && normalized.getLast().point().distanceSquared(destination) < 16.0) {
            normalized.removeLast();
        }
        return normalized;
    }

    private static boolean isWaterTravelPoint(ServerLevel world, BlockPos position) {
        if (world == null || position == null) {
            return false;
        }
        for (int offset = 0; offset <= 1; offset++) {
            if (world.getFluidState(position.below(offset)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static Component validateSurveyPath(ServerLevel world,
                                                PlayerQuestData data,
                                                int routeIndex,
                                                List<RouteSurveyPoint> waypoints) {
        List<RouteSurveyPoint> path = new ArrayList<>();
        path.add(new RouteSurveyPoint(new RoutePoint(
                data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z)), false));
        path.addAll(waypoints);
        path.add(new RouteSurveyPoint(new RoutePoint(
                routeInt(data, routeIndex, "x"), routeInt(data, routeIndex, "z")), false));

        for (int node = 0; node < path.size(); node++) {
            RouteSurveyPoint point = path.get(node);
            boolean ferryDock = !point.ocean()
                    && (node > 0 && isFerrySegment(path.get(node - 1), point)
                    || node < path.size() - 1 && isFerrySegment(point, path.get(node + 1)));
            if (!ferryDock) {
                continue;
            }
            BlockPos probe = new BlockPos(point.point().x(), 64, point.point().z());
            if (world.hasChunkAt(probe)
                    && findCaravanSurface(world, probe, FERRY_DOCK_SEARCH_RADIUS) == null) {
                return Component.translatable("message.village-quest.trade_route.survey.unsafe_dock");
            }
        }

        for (int segment = 1; segment < path.size(); segment++) {
            RouteSurveyPoint from = path.get(segment - 1);
            RouteSurveyPoint to = path.get(segment);
            boolean ferrySegment = from.ocean() || to.ocean();
            double distance = from.point().distance(to.point());
            int samples = Math.max(1, (int) Math.ceil(distance / 8.0));
            for (int sample = 0; sample <= samples; sample++) {
                double t = sample / (double) samples;
                int x = (int) Math.round(from.point().x() + (to.point().x() - from.point().x()) * t);
                int z = (int) Math.round(from.point().z() + (to.point().z() - from.point().z()) * t);
                BlockPos probe = new BlockPos(x, 64, z);
                if (!world.hasChunkAt(probe)) {
                    continue;
                }
                int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos surface = new BlockPos(x, topY - 1, z);
                BlockState surfaceState = world.getBlockState(surface);
                if (surfaceState.getFluidState().isEmpty()
                        && !surfaceState.getCollisionShape(world, surface).isEmpty()) {
                    // A constructed bridge remains a valid ordinary road even when
                    // a river or lake lies directly below it.
                    continue;
                }
                BlockPos water = null;
                for (int offset = 0; offset <= 2; offset++) {
                    BlockPos candidate = new BlockPos(x, topY - offset, z);
                    if (world.getFluidState(candidate).is(FluidTags.WATER)) {
                        water = candidate;
                        break;
                    }
                }
                if (water == null) {
                    continue;
                }
                boolean ocean = world.getBiome(water).is(BiomeTags.IS_OCEAN);
                if (!ocean) {
                    return Component.translatable("message.village-quest.trade_route.survey.inland_water");
                }
                if (!ferrySegment) {
                    return Component.translatable("message.village-quest.trade_route.survey.ocean_unmarked");
                }
            }
        }
        return null;
    }

    private static void restoreSurveyPauseState(PlayerQuestData data, int routeIndex) {
        data.setTradeRouteFlag(routeKey(routeIndex, STOPPED_SUFFIX.substring(1)),
                data.hasTradeRouteFlag(SURVEY_WAS_STOPPED));
    }

    private static void clearSurveyDraft(PlayerQuestData data) {
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.equals(SURVEY_ROUTE) || key.equals(SURVEY_POINT_COUNT) || key.startsWith(SURVEY_POINT_PREFIX)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        for (String flag : List.copyOf(data.getTradeRouteFlags())) {
            if (flag.startsWith(SURVEY_POINT_PREFIX)) {
                data.setTradeRouteFlag(flag, false);
            }
        }
        data.setTradeRouteFlag(SURVEY_WAS_STOPPED, false);
    }

    private static void clearRouteEntries(PlayerQuestData data) {
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.startsWith(ROUTE_PREFIX)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        for (String flag : List.copyOf(data.getTradeRouteFlags())) {
            if (flag.startsWith(ROUTE_PREFIX)) {
                data.setTradeRouteFlag(flag, false);
            }
        }
        for (String key : List.copyOf(data.getTradeRouteStringState().keySet())) {
            if (key.startsWith(ROUTE_PREFIX)) {
                data.setTradeRouteString(key, "");
            }
        }
    }

    private static void copyRouteEntries(PlayerQuestData data,
                                         Map<String, Integer> savedInts,
                                         Map<String, String> savedStrings,
                                         Set<String> savedFlags,
                                         int sourceIndex,
                                         int targetIndex) {
        String sourcePrefix = ROUTE_PREFIX + sourceIndex + "_";
        String targetPrefix = ROUTE_PREFIX + targetIndex + "_";
        for (Map.Entry<String, Integer> entry : savedInts.entrySet()) {
            if (entry.getKey().startsWith(sourcePrefix)) {
                data.setTradeRouteInt(targetPrefix + entry.getKey().substring(sourcePrefix.length()), entry.getValue());
            }
        }
        for (String flag : savedFlags) {
            if (flag.startsWith(sourcePrefix)) {
                data.setTradeRouteFlag(targetPrefix + flag.substring(sourcePrefix.length()), true);
            }
        }
        for (Map.Entry<String, String> entry : savedStrings.entrySet()) {
            if (entry.getKey().startsWith(sourcePrefix)) {
                data.setTradeRouteString(targetPrefix + entry.getKey().substring(sourcePrefix.length()), entry.getValue());
            }
        }
    }

    private static boolean hasHome(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(HOME_BOUND);
    }

    private static boolean isPlayerYard(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(HOME_PLAYER_YARD);
    }

    private static int quality(PlayerQuestData data, int routeIndex) {
        int quality = routeInt(data, routeIndex, "quality");
        return quality <= 0 ? 20 : Math.min(100, quality);
    }

    private static int routeLivery(PlayerQuestData data, int routeIndex) {
        int stored = routeInt(data, routeIndex, "livery");
        return stored <= 0 ? Math.floorMod(routeIndex, MAX_ROUTES)
                : Math.max(0, Math.min(MAX_ROUTES - 1, stored - 1));
    }

    private static TradeRouteStatus status(PlayerQuestData data, int routeIndex) {
        TradeRouteStatus status = TradeRouteStatus.byId(routeInt(data, routeIndex, "status"));
        return status == TradeRouteStatus.UNKNOWN ? TradeRouteStatus.DANGEROUS : status;
    }

    private static TradeRouteEventType event(PlayerQuestData data, int routeIndex) {
        return TradeRouteEventType.byId(routeInt(data, routeIndex, "event"));
    }

    private static boolean isStopped(PlayerQuestData data, int routeIndex) {
        return data.hasTradeRouteFlag(routeKey(routeIndex, STOPPED_SUFFIX.substring(1)));
    }

    private static int routeInt(PlayerQuestData data, int routeIndex, String suffix) {
        return data.getTradeRouteInt(routeKey(routeIndex, suffix));
    }

    private static void setRouteInt(PlayerQuestData data, int routeIndex, String suffix, int value) {
        data.setTradeRouteInt(routeKey(routeIndex, suffix), value);
    }

    private static String routeKey(int routeIndex, String suffix) {
        return ROUTE_PREFIX + routeIndex + "_" + suffix;
    }

    private static Component routeName(int routeIndex) {
        return Component.translatable("text.village-quest.trade_route.name", routeIndex + 1);
    }

    private static Component routeName(PlayerQuestData data, int routeIndex) {
        String custom = data == null ? "" : data.getTradeRouteString(routeKey(routeIndex, "name"));
        return custom.isBlank() ? routeName(routeIndex) : Component.literal(custom);
    }

    private static Component villageName(PlayerQuestData data, int routeIndex) {
        String custom = data == null ? "" : data.getTradeRouteString(routeKey(routeIndex, "name"));
        return custom.isBlank()
                ? Component.translatable("text.village-quest.trade_route.node.village", routeIndex + 1)
                : Component.literal(custom);
    }

    private static String sanitizeRouteName(String requestedName) {
        if (requestedName == null) {
            return "";
        }
        String clean = ChatFormatting.stripFormatting(requestedName).replaceAll("\\p{Cntrl}", "")
                .trim().replaceAll("\\s+", " ");
        if (clean.length() > 24) {
            clean = clean.substring(0, 24).trim();
        }
        return clean;
    }

    private static String routeTag(int routeIndex) {
        return TAG_ROUTE_INDEX_PREFIX + Math.max(0, Math.min(MAX_ROUTES - 1, routeIndex));
    }

    private static int clampProgress(int progress) {
        return Math.max(0, Math.min(PROGRESS_MAX, progress));
    }

    private static boolean crossedMidpoint(int previous, int current) {
        return (previous < PROGRESS_MAX / 2 && current >= PROGRESS_MAX / 2)
                || (previous > PROGRESS_MAX / 2 && current <= PROGRESS_MAX / 2);
    }

    private static int currentWorldDay(ServerLevel world) {
        return world == null ? 0 : (int) (world.getOverworldClockTime() / 24000L);
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static ServerPlayer nearestPlayer(ServerLevel world, BlockPos pos, int radius) {
        if (world == null || pos == null) {
            return null;
        }
        ServerPlayer best = null;
        double bestDistance = radius * (double) radius;
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            if (player.level() != world) {
                continue;
            }
            double distance = player.blockPosition().distSqr(pos);
            if (distance <= bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Entity findEntity(ServerLevel world, UUID entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        for (Entity entity : world.getAllEntities()) {
            if (entityId.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    private static List<Entity> allEntities(ServerLevel world) {
        List<Entity> entities = new ArrayList<>();
        if (world != null) {
            for (Entity entity : world.getAllEntities()) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private static String ownerTag(UUID ownerId) {
        return TAG_ROUTE_OWNER_PREFIX + ownerId;
    }

    private static void refreshUi(ServerLevel world, ServerPlayer player) {
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    private record RouteKey(UUID ownerId, int routeIndex) {}

    private record YardConfirmation(BlockPos position, long expiresAt) {}

    private record RouteSurveyPoint(RoutePoint point, boolean ocean) {}

    private record FerryBoarding(RoutePoint point, int progress) {}

    private record FerryState(boolean active, int secondsRemaining) {
        private static final FerryState NONE = new FerryState(false, 0);
    }

    private record RoutePoint(int x, int z) {
        private double distance(RoutePoint other) {
            return Math.sqrt(distanceSquared(other));
        }

        private double distanceSquared(RoutePoint other) {
            double dx = other.x - x;
            double dz = other.z - z;
            return dx * dx + dz * dz;
        }
    }

    private static final class CaravanRuntime {
        private final List<UUID> merchantIds = new ArrayList<>();
        private final Set<UUID> attackerIds = new HashSet<>();
        private final Map<UUID, BlockPos> lastMerchantPositions = new HashMap<>();
        private BlockPos lastExpected = BlockPos.ZERO;
        private BlockPos lastActual;
        private BlockPos lastLeaderPosition;
        private BlockPos boardingAnchor;
        private int boardingProgress = -1;
        private int boardingDirection;
        private int stuckSeconds;
        private int recoveryGraceSeconds;
        private int recoveryCount;
    }
}
