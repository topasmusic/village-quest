package de.quest.caravan;

import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.world.LightType;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;

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
    private static final int MATERIALIZATION_RETRY_TICKS = 20 * 15;
    private static final double CARAVAN_MIN_MOVEMENT_SQR = 0.35 * 0.35;
    private static final double CARAVAN_TARGET_DISTANCE_SQR = 6.0 * 6.0;
    private static final int NPC_DESPAWN_TICKS = 20 * 60 * 60;
    private static final int MAP_UPDATE_TICKS = 20;
    private static final int STORM_CAMP_SECONDS = 30;
    private static final String[] CARAVAN_NAMES = {"alda", "bram", "cira", "doran", "esme", "fenn"};

    private static final Map<RouteKey, CaravanRuntime> ACTIVE_CARAVANS = new HashMap<>();
    private static final Map<RouteKey, Long> MATERIALIZATION_RETRY_AT = new HashMap<>();
    private static final Map<UUID, RouteKey> ENTITY_ROUTES = new HashMap<>();
    private static final Map<UUID, RouteKey> ATTACKER_ROUTES = new HashMap<>();
    private static final Set<UUID> MAP_VIEWERS = new HashSet<>();
    private static final Set<UUID> MINIMAP_VIEWERS = new HashSet<>();

    private TradeRouteService() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerWorld world = server.getOverworld();
        long gameTime = world.getTime();
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
    }

    public static void despawnAll(ServerWorld world) {
        if (world != null) {
            for (CaravanRuntime runtime : ACTIVE_CARAVANS.values()) {
                discardRuntimeEntities(world, runtime);
            }
            for (Entity entity : allEntities(world)) {
                if (entity.getCommandTags().contains(TAG_ROUTE_CARAVAN)
                        || entity.getCommandTags().contains(TAG_ROUTE_ATTACKER)) {
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
        }
    }

    public static boolean hasCaravanYard(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.CARAVAN_YARD);
    }

    public static boolean hasRouteAccess(ServerWorld world, UUID playerId) {
        return world != null && playerId != null
                && (hasCaravanYard(world, playerId)
                || VillageProjectService.isUnlocked(world, playerId, VillageProjectType.MARKET_CHARTER));
    }

    public static int routeCapacity(ServerWorld world, UUID playerId) {
        return hasCaravanYard(world, playerId) ? MAX_ROUTES : hasRouteAccess(world, playerId) ? 1 : 0;
    }

    public static void initializeProvisionalNetwork(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUuid())) return;
        PlayerQuestData data = data(world, player.getUuid());
        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.getBlockPos());
        if (village != null && isInhabitedVillage(world, village)) {
            bindHome(data, village.centerX(), village.centerZ());
        }
        grantLedger(world, player);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.provisional_unlocked")
                .formatted(Formatting.GOLD), false);
    }

    public static void initializeCaravanYard(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.getBlockPos());
        if (village != null && isInhabitedVillage(world, village)) {
            bindHome(data, village.centerX(), village.centerZ());
        }
        grantLedger(world, player);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.yard_unlocked")
                .formatted(Formatting.GOLD), false);
    }

    public static ActionResult useLedger(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return ActionResult.PASS;
        }
        if (!hasRouteAccess(world, player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.locked")
                    .formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }
        collectEscrow(world, player);
        if (player.isSneaking()) {
            PlayerQuestData data = data(world, player.getUuid());
            if (activeSurveyIndex(data) >= 0) {
                markSurveyWaypoint(world, player);
            } else {
                registerCurrentVillage(world, player);
            }
        } else {
            openMap(world, player);
        }
        return ActionResult.SUCCESS;
    }

    public static boolean registerCurrentVillage(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUuid())) {
            return false;
        }
        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.getBlockPos());
        if (village == null) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.register.not_in_village")
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (!isInhabitedVillage(world, village)) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.register.abandoned")
                    .formatted(Formatting.RED), false);
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        if (!hasHome(data)) {
            bindHome(data, village.centerX(), village.centerZ());
            QuestState.get(world.getServer()).setDirty(true);
            player.sendMessage(Text.translatable("message.village-quest.trade_route.register.home_bound")
                    .formatted(Formatting.GOLD), false);
            return true;
        }
        if (data.getTradeRouteInt(HOME_X) == village.centerX() && data.getTradeRouteInt(HOME_Z) == village.centerZ()) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.register.home_same")
                    .formatted(Formatting.GRAY), false);
            return false;
        }

        int capacity = routeCapacity(world, player.getUuid());
        int count = Math.min(capacity, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            if (routeInt(data, i, "x") == village.centerX() && routeInt(data, i, "z") == village.centerZ()) {
                player.sendMessage(Text.translatable("message.village-quest.trade_route.register.duplicate")
                        .formatted(Formatting.GRAY), false);
                return false;
            }
        }
        if (count >= capacity) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.register.full", capacity)
                    .formatted(Formatting.RED), false);
            return false;
        }

        setRouteInt(data, count, "x", village.centerX());
        setRouteInt(data, count, "z", village.centerZ());
        setRouteInt(data, count, "progress", 0);
        setRouteInt(data, count, "direction", 1);
        setRouteInt(data, count, "quality", 20);
        setRouteInt(data, count, "status", TradeRouteStatus.DANGEROUS.id());
        data.setTradeRouteInt(ROUTE_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.register.success", count + 1)
                .formatted(Formatting.GREEN), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.1f);
        refreshUi(world, player);
        return true;
    }

    private static boolean isInhabitedVillage(ServerWorld world,
                                               ShadowsTradeRoadEncounterService.VillageMarker village) {
        if (world == null || village == null) {
            return false;
        }
        // Use the real structure footprint instead of a fixed center radius. Large CTOV
        // settlements can place their surviving villagers well beyond a vanilla-sized core.
        Box villageArea = new Box(
                village.minX() - 16.0, world.getBottomY(), village.minZ() - 16.0,
                village.maxX() + 17.0, world.getTopYInclusive(), village.maxZ() + 17.0);
        return !world.getEntitiesByClass(VillagerEntity.class, villageArea,
                villager -> villager.isAlive() && !villager.isRemoved()).isEmpty();
    }

    public static void openMap(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUuid())) {
            return;
        }
        collectEscrow(world, player);
        MAP_VIEWERS.add(player.getUuid());
        ServerPlayNetworking.send(player, buildMapPayload(world, player.getUuid(), Payloads.TradeRouteMapPayload.ACTION_OPEN));
    }

    public static void handleMapAction(ServerPlayerEntity player, Payloads.TradeRouteActionPayload payload) {
        if (player == null || payload == null || !(player.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        if (payload.action() == Payloads.TradeRouteActionPayload.ACTION_CLOSE) {
            MAP_VIEWERS.remove(player.getUuid());
            return;
        }
        if (payload.action() == Payloads.TradeRouteActionPayload.ACTION_MINIMAP_TOGGLE) {
            toggleMinimap(world, player);
            return;
        }
        if (!hasRouteAccess(world, player.getUuid())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
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
        if (MAP_VIEWERS.contains(player.getUuid())) {
            ServerPlayNetworking.send(player, buildMapPayload(world, player.getUuid(), Payloads.TradeRouteMapPayload.ACTION_UPDATE));
        }
    }

    public static boolean toggleMinimap(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        UUID playerId = player.getUuid();
        if (MINIMAP_VIEWERS.remove(playerId)) {
            ServerPlayNetworking.send(player, buildMapPayload(world, playerId,
                    Payloads.TradeRouteMapPayload.ACTION_MINIMAP_DISABLE));
            player.sendMessage(Text.translatable("message.village-quest.trade_route.minimap.disabled")
                    .formatted(Formatting.GRAY), true);
            return false;
        }
        if (!hasRouteAccess(world, playerId)) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.locked")
                    .formatted(Formatting.RED), true);
            return false;
        }
        if (player.getEntityWorld() != world) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.wrong_dimension")
                    .formatted(Formatting.RED), true);
            return false;
        }
        MINIMAP_VIEWERS.add(playerId);
        collectEscrow(world, player);
        ServerPlayNetworking.send(player, buildMapPayload(world, playerId,
                Payloads.TradeRouteMapPayload.ACTION_MINIMAP_ENABLE));
        player.sendMessage(Text.translatable("message.village-quest.trade_route.minimap.enabled")
                .formatted(Formatting.GOLD), true);
        return true;
    }

    public static boolean startRouteSurvey(ServerWorld world, ServerPlayerEntity player, int routeIndex) {
        if (world == null || player == null) {
            return false;
        }
        if (world != world.getServer().getOverworld() || player.getEntityWorld() != world) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.wrong_dimension")
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (!hasRouteAccess(world, player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.locked")
                    .formatted(Formatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.route_invalid", count)
                    .formatted(Formatting.RED), false);
            return false;
        }
        int active = activeSurveyIndex(data);
        if (active >= 0) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.active", routeName(active))
                    .formatted(Formatting.YELLOW), false);
            return active == routeIndex;
        }

        clearSurveyDraft(data);
        data.setTradeRouteInt(SURVEY_ROUTE, routeIndex + 1);
        data.setTradeRouteFlag(SURVEY_WAS_STOPPED, isStopped(data, routeIndex));
        data.setTradeRouteFlag(routeKey(routeIndex, STOPPED_SUFFIX.substring(1)), true);
        removeRuntime(world, new RouteKey(player.getUuid(), routeIndex));
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.started",
                routeName(routeIndex), MAX_WAYPOINTS).formatted(Formatting.GOLD), false);
        return true;
    }

    public static boolean markSurveyWaypoint(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null
                || world != world.getServer().getOverworld() || player.getEntityWorld() != world) {
            if (player != null) {
                player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.wrong_dimension")
                        .formatted(Formatting.RED), false);
            }
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int routeIndex = activeSurveyIndex(data);
        if (routeIndex < 0) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.none")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        int count = Math.min(MAX_WAYPOINTS, data.getTradeRouteInt(SURVEY_POINT_COUNT));
        if (count >= MAX_WAYPOINTS) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.full", MAX_WAYPOINTS)
                    .formatted(Formatting.RED), false);
            return false;
        }
        RoutePoint previous = count == 0
                ? new RoutePoint(data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z))
                : surveyPoint(data, count - 1);
        RoutePoint point = new RoutePoint(player.getBlockX(), player.getBlockZ());
        if (previous.distanceSquared(point) < 16.0) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.too_close")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        setSurveyPoint(data, count, point);
        data.setTradeRouteInt(SURVEY_POINT_COUNT, count + 1);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.marked",
                count + 1, point.x(), point.z()).formatted(Formatting.GREEN), false);
        return true;
    }

    public static boolean finishRouteSurvey(ServerWorld world, ServerPlayerEntity player, int requestedRouteIndex) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int routeIndex = activeSurveyIndex(data);
        if (routeIndex < 0) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.none")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        if (requestedRouteIndex >= 0 && requestedRouteIndex != routeIndex) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.active", routeName(routeIndex))
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        int routeCount = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex >= routeCount) {
            clearSurveyDraft(data);
            QuestState.get(world.getServer()).setDirty(true);
            return false;
        }

        List<RoutePoint> draft = normalizedSurveyPoints(data, routeIndex);
        setRouteWaypoints(data, routeIndex, draft);
        setRouteInt(data, routeIndex, "quality", 20);
        restoreSurveyPauseState(data, routeIndex);
        clearSurveyDraft(data);
        removeRuntime(world, new RouteKey(player.getUuid(), routeIndex));
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.finished",
                routeName(routeIndex), draft.size()).formatted(Formatting.GREEN), false);
        return true;
    }

    public static boolean cancelRouteSurvey(ServerWorld world, ServerPlayerEntity player, int requestedRouteIndex) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int routeIndex = activeSurveyIndex(data);
        if (routeIndex < 0) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.none")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        if (requestedRouteIndex >= 0 && requestedRouteIndex != routeIndex) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.active", routeName(routeIndex))
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        restoreSurveyPauseState(data, routeIndex);
        clearSurveyDraft(data);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.cancelled", routeName(routeIndex))
                .formatted(Formatting.GRAY), false);
        return true;
    }

    public static boolean removeRoute(ServerWorld world, ServerPlayerEntity player, int routeIndex) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUuid())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.route_invalid", count)
                    .formatted(Formatting.RED), false);
            return false;
        }

        int activeSurvey = activeSurveyIndex(data);
        if (activeSurvey >= 0) {
            restoreSurveyPauseState(data, activeSurvey);
            clearSurveyDraft(data);
        }
        removeOwnerRuntimes(world, player.getUuid());
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
        TradeGuildService.onRouteRemoved(world, player.getUuid(), routeIndex);
        QuestState.get(world.getServer()).setDirty(true);
        SurveyorCompassQuestService.selectRouteEventMode(world, player.getUuid());
        player.sendMessage(Text.translatable("message.village-quest.trade_route.removed",
                routeName(routeIndex), count - 1, routeCapacity(world, player.getUuid())).formatted(Formatting.GREEN), false);
        refreshUi(world, player);
        return true;
    }

    private static void toggleRoute(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, int routeIndex) {
        if (activeSurveyIndex(data) == routeIndex) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.survey.active", routeName(routeIndex))
                    .formatted(Formatting.YELLOW), false);
            return;
        }
        String stoppedKey = routeKey(routeIndex, STOPPED_SUFFIX.substring(1));
        boolean stopped = data.hasTradeRouteFlag(stoppedKey);
        data.setTradeRouteFlag(stoppedKey, !stopped);
        QuestState.get(world.getServer()).setDirty(true);
        if (!stopped) {
            discardRouteEntities(world, new RouteKey(player.getUuid(), routeIndex));
        }
    }

    public static boolean renameRoute(ServerWorld world, ServerPlayerEntity player, int routeIndex, String requestedName) {
        if (world == null || player == null || !validRoute(world, player.getUuid(), routeIndex)) {
            return false;
        }
        String name = sanitizeRouteName(requestedName);
        if (name.isEmpty()) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.rename.invalid")
                    .formatted(Formatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        data.setTradeRouteString(routeKey(routeIndex, "name"), name);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.rename.success", name)
                .formatted(Formatting.GREEN), false);
        refreshUi(world, player);
        if (MAP_VIEWERS.contains(player.getUuid())) {
            ServerPlayNetworking.send(player, buildMapPayload(world, player.getUuid(),
                    Payloads.TradeRouteMapPayload.ACTION_UPDATE));
        }
        return true;
    }

    public static ActionResult onEntityUse(ServerWorld world, ServerPlayerEntity helper, Entity entity) {
        if (world == null || helper == null || entity == null) {
            return ActionResult.PASS;
        }
        RouteKey key = ENTITY_ROUTES.get(entity.getUuid());
        if (key == null) {
            return ActionResult.PASS;
        }
        PlayerQuestData ownerData = data(world, key.ownerId());
        TradeRouteEventType event = event(ownerData, key.routeIndex());
        if (event == null) {
            helper.sendMessage(Text.translatable("message.village-quest.trade_route.caravan_greeting",
                    routeName(key.routeIndex())).formatted(Formatting.GOLD), false);
            return ActionResult.SUCCESS;
        }
        if (!key.ownerId().equals(helper.getUuid())) {
            helper.sendMessage(Text.translatable("message.village-quest.trade_route.event.owner_only")
                    .formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        boolean resolved = switch (event) {
            case BROKEN_WHEEL -> consumePair(helper, stack -> stack.isOf(Items.IRON_INGOT), 2,
                    TradeRouteService::isPlank, 8);
            case INJURED_PACK_ANIMAL -> consumePair(helper, stack -> stack.isOf(Items.HAY_BLOCK), 1,
                    stack -> stack.isOf(Items.WHEAT), 4);
            case WASHED_OUT_BRIDGE -> consumePlayerInventory(helper, TradeRouteService::isPlank, 16);
            case HUNGRY_TRAVELERS -> consumePlayerInventory(helper, stack -> stack.isOf(Items.BREAD), 8);
            case ROAD_TOLL -> CurrencyService.removeBalance(world, helper.getUuid(), 5L);
            case MISSING_COURIER -> entity instanceof CaravanMerchantEntity merchant && merchant.isCourier();
            case FALSE_DISTRESS -> startAmbush(world, helper, key, ownerData);
            case STORM_CAMP -> false;
        };

        if (event == TradeRouteEventType.FALSE_DISTRESS) {
            return ActionResult.SUCCESS;
        }
        if (resolved) {
            resolveEvent(world, helper, key, ownerData, event);
        } else {
            helper.sendMessage(event.help().copy().formatted(Formatting.YELLOW), false);
        }
        return ActionResult.SUCCESS;
    }

    public static void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        if (world == null || killedEntity == null) {
            return;
        }
        RouteKey key = ATTACKER_ROUTES.remove(killedEntity.getUuid());
        if (key == null) {
            return;
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            runtime.attackerIds.remove(killedEntity.getUuid());
        }
        boolean attackersRemain = ATTACKER_ROUTES.containsValue(key);
        if (!attackersRemain) {
            PlayerQuestData ownerData = data(world, key.ownerId());
            resolveEvent(world, player, key, ownerData, TradeRouteEventType.FALSE_DISTRESS);
        }
    }

    public static Text activeRouteTargetLabel(ServerWorld world, UUID playerId) {
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

    public static BlockPos activeRouteTarget(ServerWorld world, UUID playerId) {
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

    public static void resetRoutes(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        data.clearTradeRoutes();
        removeOwnerRuntimes(world, playerId);
        QuestState.get(world.getServer()).setDirty(true);
    }

    public static void adminCreateTestNetwork(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        removeOwnerRuntimes(world, player.getUuid());
        PlayerQuestData data = data(world, player.getUuid());
        data.clearTradeRoutes();
        VillageProjectService.unlock(world, player.getUuid(), VillageProjectType.CARAVAN_YARD);

        ShadowsTradeRoadEncounterService.VillageMarker village =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.getBlockPos());
        int homeX = village == null ? player.getBlockX() : village.centerX();
        int homeZ = village == null ? player.getBlockZ() : village.centerZ();
        bindHome(data, homeX, homeZ);
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
        CurrencyService.addBalance(world, player.getUuid(), 300);
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
        QuestState.get(world.getServer()).setDirty(true);
        grantLedger(world, player);
        giveTestWayfinder(player);
        openMap(world, player);
    }

    public static boolean adminSetTestEvent(ServerWorld world,
                                            ServerPlayerEntity player,
                                            int routeIndex,
                                            String eventKey) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        if (routeIndex < 0 || routeIndex >= count) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.route_invalid", count)
                    .formatted(Formatting.RED), false);
            return false;
        }
        TradeRouteEventType selected = "clear".equalsIgnoreCase(eventKey)
                ? null
                : TradeRouteEventType.byKey(eventKey);
        if (selected == null && !"clear".equalsIgnoreCase(eventKey)) {
            player.sendMessage(Text.translatable("command.village-quest.questadmin.routes.testevent.invalid", eventKey)
                    .formatted(Formatting.RED), false);
            return false;
        }
        setRouteInt(data, routeIndex, "event", selected == null ? 0 : selected.id());
        setRouteInt(data, routeIndex, "event_day", selected == null ? 0 : currentWorldDay(world));
        setRouteInt(data, routeIndex, "event_progress", 0);
        removeRuntime(world, new RouteKey(player.getUuid(), routeIndex));
        QuestState.get(world.getServer()).setDirty(true);
        SurveyorCompassQuestService.selectRouteEventMode(world, player.getUuid());
        player.sendMessage(Text.translatable("command.village-quest.questadmin.routes.testevent",
                routeName(routeIndex), selected == null
                        ? Text.translatable("text.village-quest.trade_route.event.none")
                        : selected.label()).formatted(Formatting.GREEN), false);
        if (selected != null) {
            player.sendMessage(selected.help().copy().formatted(Formatting.YELLOW), false);
        }
        return true;
    }

    private static void tickRoute(ServerWorld world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        RouteKey key = new RouteKey(ownerId, routeIndex);
        if (isStopped(data, routeIndex)) {
            discardRouteEntities(world, key);
            return;
        }

        TradeRouteEventType currentEvent = event(data, routeIndex);
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
        materializeNearPlayers(world, key, data);
    }

    private static void advanceRoute(ServerWorld world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        int progress = clampProgress(routeInt(data, routeIndex, "progress"));
        int direction = routeInt(data, routeIndex, "direction") < 0 ? -1 : 1;
        int oldProgress = progress;
        progress += direction * movementStep(data, routeIndex);
        boolean arrived = progress >= PROGRESS_MAX || progress <= 0;
        if (arrived) {
            progress = progress >= PROGRESS_MAX ? PROGRESS_MAX : 0;
            direction *= -1;
            setRouteInt(data, routeIndex, "runs", routeInt(data, routeIndex, "runs") + 1);
            setRouteInt(data, routeIndex, "direction", direction);
            payArrival(world, ownerId, data, routeIndex);
        }
        setRouteInt(data, routeIndex, "progress", progress);

        if (!arrived && crossedMidpoint(oldProgress, progress)) {
            maybeStartEvent(world, ownerId, data, routeIndex);
        }
        QuestState.get(world.getServer()).setDirty(true);
    }

    private static int movementStep(PlayerQuestData data, int routeIndex) {
        double distance = Math.max(96.0, routeDistance(data, routeIndex));
        int quality = quality(data, routeIndex);
        double blocksPerSecond = 0.55 + quality * 0.0065;
        if (hasUpgrade(data, routeIndex, TradeRouteUpgrade.REINFORCED_WHEELS)) {
            blocksPerSecond *= 1.08;
        }
        return Math.max(1, (int) Math.round(blocksPerSecond * PROGRESS_MAX / distance));
    }

    private static void maybeStartEvent(ServerWorld world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        if (!hasCaravanYard(world, ownerId) || hasActiveEvent(data)) return;
        int runs = routeInt(data, routeIndex, "runs");
        int eventStamp = routeInt(data, routeIndex, "event_stamp");
        if (eventStamp == runs + 1) {
            return;
        }
        setRouteInt(data, routeIndex, "event_stamp", runs + 1);
        int quality = quality(data, routeIndex);
        int eventChance = Math.max(8, Math.min(40, 38 - quality / 4
                + (status(data, routeIndex) == TradeRouteStatus.DANGEROUS ? 8 : 0)
                - (status(data, routeIndex) == TradeRouteStatus.FLOURISHING ? 5 : 0)
                - (hasUpgrade(data, routeIndex, TradeRouteUpgrade.LANTERN_CREW) ? 4 : 0)
                - (hasUpgrade(data, routeIndex, TradeRouteUpgrade.WEATHER_COVERS) ? 3 : 0)
                - (hasUpgrade(data, routeIndex, TradeRouteUpgrade.ESCORTS) ? 6 : 0)));
        boolean tutorialEvent = !data.hasTradeRouteFlag(TUTORIAL_EVENT_SEEN);
        int roll = Math.floorMod(ownerId.hashCode() + routeIndex * 37 + runs * 17, 100);
        if (!tutorialEvent && roll >= eventChance) {
            return;
        }
        if (!tutorialEvent && data.getTradeRouteInt(WARDEN_CHARGES) > 0) {
            data.setTradeRouteInt(WARDEN_CHARGES, data.getTradeRouteInt(WARDEN_CHARGES) - 1);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
            if (owner != null) owner.sendMessage(Text.translatable("message.village-quest.roadwarden_horn.prevented",
                    routeName(routeIndex)).formatted(Formatting.GOLD), false);
            return;
        }
        TradeRouteEventType[] events = TradeRouteEventType.values();
        TradeRouteEventType selected = events[Math.floorMod(ownerId.hashCode() + routeIndex * 11 + runs * 5, events.length)];
        setRouteInt(data, routeIndex, "event", selected.id());
        setRouteInt(data, routeIndex, "event_day", currentWorldDay(world));
        setRouteInt(data, routeIndex, "event_progress", 0);
        data.setTradeRouteFlag(TUTORIAL_EVENT_SEEN, true);
        SurveyorCompassQuestService.selectRouteEventMode(world, ownerId);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage(Text.translatable("message.village-quest.trade_route.event.started",
                    routeName(routeIndex), selected.label()).formatted(Formatting.RED), false);
            owner.sendMessage(selected.help().copy().formatted(Formatting.YELLOW), false);
            world.playSound(null, owner.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 0.45f, 1.35f);
        }
    }

    private static void tickStormCamp(ServerWorld world, RouteKey key, PlayerQuestData data) {
        BlockPos target = runtimeInteractionTarget(world, key, data);
        ServerPlayerEntity helper = nearestPlayer(world, target, EVENT_INTERACTION_RADIUS);
        if (helper == null) {
            return;
        }
        int seconds = routeInt(data, key.routeIndex(), "event_progress") + 1;
        setRouteInt(data, key.routeIndex(), "event_progress", seconds);
        QuestState.get(world.getServer()).setDirty(true);
        if (seconds >= STORM_CAMP_SECONDS) {
            resolveEvent(world, helper, key, data, TradeRouteEventType.STORM_CAMP);
        }
    }

    private static void tickAmbush(ServerWorld world, RouteKey key, PlayerQuestData data) {
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime == null) {
            setRouteInt(data, key.routeIndex(), "event_progress", 0);
            QuestState.get(world.getServer()).setDirty(true);
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
            ServerPlayerEntity helper = nearestPlayer(world, target, EVENT_INTERACTION_RADIUS * 2);
            if (helper == null) {
                helper = world.getServer().getPlayerManager().getPlayer(key.ownerId());
            }
            resolveEvent(world, helper, key, data, TradeRouteEventType.FALSE_DISTRESS);
        }
    }

    private static void resolveEvent(ServerWorld world,
                                     ServerPlayerEntity helper,
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
        QuestState.get(world.getServer()).setDirty(true);

        if (helper != null) {
            int reward = eventReward(ownerData, key.routeIndex(), event);
            int reputation = Math.max(4, Math.min(10, reward / 2 + 2));
            CurrencyService.addBalance(world, helper.getUuid(), reward);
            ReputationService.add(world, helper.getUuid(), ReputationService.ReputationTrack.TRADE, reputation);
            helper.sendMessage(Text.translatable("message.village-quest.trade_route.event.resolved",
                    event.label(), routeName(key.routeIndex())).formatted(Formatting.GREEN), false);
            world.playSound(null, helper.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.55f, 1.35f);
            refreshUi(world, helper);
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            discardAttackers(world, runtime);
            updateMerchantRoles(world, runtime, null, key.routeIndex());
        }
    }

    private static void failEvent(ServerWorld world, RouteKey key, PlayerQuestData data, TradeRouteEventType event) {
        setRouteInt(data, key.routeIndex(), "event", 0);
        setRouteInt(data, key.routeIndex(), "event_day", 0);
        setRouteInt(data, key.routeIndex(), "event_progress", 0);
        setRouteInt(data, key.routeIndex(), "failures", routeInt(data, key.routeIndex(), "failures") + 1);
        if (!hasUpgrade(data, key.routeIndex(), TradeRouteUpgrade.INSURANCE)) {
            setRouteInt(data, key.routeIndex(), "successes", Math.max(0, routeInt(data, key.routeIndex(), "successes") - 1));
        }
        setRouteInt(data, key.routeIndex(), "status", TradeRouteStatus.DANGEROUS.id());
        QuestState.get(world.getServer()).setDirty(true);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(key.ownerId());
        if (owner != null) {
            owner.sendMessage(Text.translatable("message.village-quest.trade_route.event.failed",
                    routeName(key.routeIndex()), event.label()).formatted(Formatting.RED), false);
        }
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null) {
            discardAttackers(world, runtime);
        }
    }

    private static boolean startAmbush(ServerWorld world, ServerPlayerEntity helper, RouteKey key, PlayerQuestData data) {
        if (routeInt(data, key.routeIndex(), "event_progress") > 0) {
            helper.sendMessage(Text.translatable("message.village-quest.trade_route.event.false_distress.fight")
                    .formatted(Formatting.RED), false);
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
            traitor.refreshPositionAndAngles(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            traitor.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            traitor.addCommandTag(TAG_ROUTE_ATTACKER);
            traitor.addCommandTag(ownerTag(key.ownerId()));
            traitor.addCommandTag(routeTag(key.routeIndex()));
            if (world.isSpaceEmpty(traitor) && world.spawnEntity(traitor)) {
                traitor.setTarget(helper);
                runtime.attackerIds.add(traitor.getUuid());
                ATTACKER_ROUTES.put(traitor.getUuid(), key);
            }
        }
        if (runtime.attackerIds.isEmpty()) {
            helper.sendMessage(TradeRouteEventType.FALSE_DISTRESS.help().copy()
                    .formatted(Formatting.YELLOW), false);
            return false;
        }
        setRouteInt(data, key.routeIndex(), "event_progress", 1);
        QuestState.get(world.getServer()).setDirty(true);
        helper.sendMessage(Text.translatable("message.village-quest.trade_route.event.false_distress.ambush")
                .formatted(Formatting.RED), false);
        return false;
    }

    private static void materializeNearPlayers(ServerWorld world, RouteKey key, PlayerQuestData data) {
        BlockPos expected = routePosition(world, data, key.routeIndex());
        ServerPlayerEntity observer = nearestPlayer(world, expected, MATERIALIZE_RADIUS);
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
        if (!isChunkLoaded(world, expected)) {
            return;
        }
        long retryAt = MATERIALIZATION_RETRY_AT.getOrDefault(key, 0L);
        if (world.getTime() < retryAt) {
            return;
        }
        if (runtime == null || !runtimeHasLivingMerchant(world, runtime)) {
            if (isMaterializationCrowded(world, key, data, expected)) {
                MATERIALIZATION_RETRY_AT.put(key, world.getTime() + 20L * 5L);
                return;
            }
            runtime = spawnCaravan(world, key, data, expected, observer);
            if (runtime == null) {
                MATERIALIZATION_RETRY_AT.put(key, world.getTime() + MATERIALIZATION_RETRY_TICKS);
                return;
            }
            ACTIVE_CARAVANS.put(key, runtime);
            MATERIALIZATION_RETRY_AT.remove(key);
        }
        runtime.lastExpected = expected;
        if (!ensureCompleteCaravan(world, key, runtime, data)) {
            removeRuntime(world, key);
            MATERIALIZATION_RETRY_AT.put(key, world.getTime() + MATERIALIZATION_RETRY_TICKS);
            return;
        }
        updateMerchantRoles(world, runtime, event(data, key.routeIndex()), key.routeIndex());
        if (!navigateCaravan(world, key, runtime, data, observer)) {
            return;
        }
        sampleRoadQuality(world, data, key.routeIndex(), expected);
    }

    private static CaravanRuntime spawnCaravan(ServerWorld world,
                                                RouteKey key,
                                                PlayerQuestData data,
                                                BlockPos expected,
                                                ServerPlayerEntity observer) {
        CaravanRuntime runtime = new CaravanRuntime();
        TradeRouteEventType event = event(data, key.routeIndex());
        BlockPos anchor = findCaravanSurface(world, expected, CARAVAN_SPAWN_SEARCH_RADIUS);
        if (anchor == null && event != null && observer != null) {
            anchor = findCaravanSurface(world, observer.getBlockPos(), 12);
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

    private static boolean navigateCaravan(ServerWorld world,
                                           RouteKey key,
                                           CaravanRuntime runtime,
                                           PlayerQuestData data,
                                           ServerPlayerEntity observer) {
        int routeIndex = key.routeIndex();
        TradeRouteEventType currentEvent = event(data, routeIndex);
        int progress = clampProgress(routeInt(data, routeIndex, "progress"));
        int direction = routeInt(data, routeIndex, "direction") < 0 ? -1 : 1;
        BlockPos target;
        if (currentEvent != null) {
            target = findCaravanSurface(world, runtime.lastExpected, 10);
            if (target == null) {
                target = runtime.lastExpected;
            }
        } else {
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
            merchants.add(merchant);
        }
        if (merchants.isEmpty()) {
            removeRuntime(world, key);
            MATERIALIZATION_RETRY_AT.put(key, world.getTime() + MATERIALIZATION_RETRY_TICKS);
            return false;
        }

        // Older materialized groups may already be standing on a canopy when a
        // player updates. Recover the full formation together before asking for
        // another path so they do not remain visible above the road network.
        boolean unsafeLeafSupport = merchants.stream().anyMatch(merchant ->
                world.getBlockState(merchant.getBlockPos().down()).isIn(BlockTags.LEAVES));
        if (unsafeLeafSupport) {
            if (!recoverCaravan(world, key, runtime, data, observer, currentEvent != null)) {
                suspendPhysicalCaravan(world, key);
                return false;
            }
            return true;
        }

        CaravanMerchantEntity leader = merchants.getFirst();
        BlockPos leaderPosition = leader.getBlockPos();
        runtime.lastActual = leaderPosition;
        double targetDistance = leaderPosition.getSquaredDistance(target);
        boolean pathRequested = true;
        if (targetDistance > 3.0 * 3.0) {
            pathRequested = leader.getNavigation().startMovingTo(
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
            double yawRadians = Math.toRadians(leader.getYaw());
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
                merchant.getNavigation().startMovingTo(followSurface.getX() + 0.5,
                        followSurface.getY(), followSurface.getZ() + 0.5, 0.92);
            } else if (merchant.squaredDistanceTo(leader) > 2.15 * 2.15) {
                // Do not make every follower target the leader's exact feet. On narrow or
                // obstructed roads that old fallback visibly stacked the group into one NPC.
                merchant.getNavigation().startMovingTo(followX, leader.getY(), followZ, 0.82);
            } else {
                merchant.getNavigation().stop();
            }
            if (tooCloseToAny(merchant.getBlockPos(), formationOccupied, 1.75)) {
                List<BlockPos> separationSlots = findFormationSlots(
                        world, leader.getBlockPos(), 1, formationOccupied);
                if (!separationSlots.isEmpty()) {
                    BlockPos slot = separationSlots.getFirst();
                    merchant.getNavigation().startMovingTo(slot.getX() + 0.5,
                            slot.getY(), slot.getZ() + 0.5, 0.98);
                }
            }
            formationOccupied.add(merchant.getBlockPos());
            if (merchant.squaredDistanceTo(leader)
                    > CARAVAN_SOFT_REGROUP_DISTANCE * (double) CARAVAN_SOFT_REGROUP_DISTANCE) {
                BlockPos regroup = findCaravanSurface(world, leader.getBlockPos(), 4);
                if (regroup != null) {
                    merchant.getNavigation().startMovingTo(regroup.getX() + 0.5,
                            regroup.getY(), regroup.getZ() + 0.5, 1.08);
                    if (merchant.squaredDistanceTo(leader)
                            > CARAVAN_HARD_REGROUP_DISTANCE * (double) CARAVAN_HARD_REGROUP_DISTANCE
                            && !isRecoveryVisible(observer, merchant)) {
                        teleportMerchant(merchant, regroup);
                    }
                }
            }
        }

        if (leaderPosition.getSquaredDistance(runtime.lastExpected)
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
            double moved = leaderPosition.getSquaredDistance(runtime.lastLeaderPosition);
            if (!pathRequested || leader.getNavigation().isIdle() || moved < CARAVAN_MIN_MOVEMENT_SQR) {
                runtime.stuckSeconds += pathRequested ? 1 : 2;
            } else {
                runtime.stuckSeconds = Math.max(0, runtime.stuckSeconds - 2);
            }
        } else {
            runtime.stuckSeconds = 0;
        }
        runtime.lastLeaderPosition = leaderPosition;
        for (CaravanMerchantEntity merchant : merchants) {
            runtime.lastMerchantPositions.put(merchant.getUuid(), merchant.getBlockPos());
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

    private static boolean ensureCompleteCaravan(ServerWorld world,
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

    private static boolean spawnMissingMerchants(ServerWorld world,
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
                occupied.add(merchant.getBlockPos());
            }
        }
        List<BlockPos> slots = findFormationSlots(world, anchor, amount, occupied);
        if (slots.size() < amount) {
            return false;
        }
        int spawned = 0;
        for (BlockPos spawn : slots) {
            CaravanMerchantEntity merchant = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
            merchant.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            merchant.setHealth(merchant.getMaxHealth());
            merchant.setDespawnTicks(NPC_DESPAWN_TICKS);
            merchant.addCommandTag(TAG_ROUTE_CARAVAN);
            merchant.addCommandTag(ownerTag(key.ownerId()));
            merchant.addCommandTag(routeTag(key.routeIndex()));
            merchant.setRouteIndex(key.routeIndex());
            merchant.refreshEncounterControl(false);
            if (world.isSpaceEmpty(merchant) && world.spawnEntity(merchant)) {
                runtime.merchantIds.add(merchant.getUuid());
                runtime.lastMerchantPositions.put(merchant.getUuid(), spawn);
                ENTITY_ROUTES.put(merchant.getUuid(), key);
                spawned++;
            }
        }
        return spawned == amount;
    }

    private static List<BlockPos> findFormationSlots(ServerWorld world,
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

    private static boolean recoverCaravan(ServerWorld world,
                                           RouteKey key,
                                           CaravanRuntime runtime,
                                           PlayerQuestData data,
                                           ServerPlayerEntity observer,
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
            anchor = findCaravanSurface(world, observer.getBlockPos(), 12);
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
            runtime.lastMerchantPositions.put(merchants.get(i).getUuid(), slots.get(i));
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
        merchant.refreshPositionAndAngles(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, merchant.getYaw(), merchant.getPitch());
    }

    private static void suspendPhysicalCaravan(ServerWorld world, RouteKey key) {
        removeRuntime(world, key);
        MATERIALIZATION_RETRY_AT.put(key, world.getTime() + MATERIALIZATION_RETRY_TICKS);
    }

    private static int desiredMerchantCount(ServerWorld world, UUID ownerId) {
        return hasCaravanYard(world, ownerId) ? 3 : 2;
    }

    private static int livingMerchantCount(ServerWorld world, CaravanRuntime runtime) {
        return livingMerchants(world, runtime).size();
    }

    private static List<CaravanMerchantEntity> livingMerchants(ServerWorld world, CaravanRuntime runtime) {
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

    private static boolean isMaterializationCrowded(ServerWorld world,
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
            if (otherPosition == null || otherPosition.getSquaredDistance(expected)
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

    private static BlockPos runtimeInteractionTarget(ServerWorld world, RouteKey key, PlayerQuestData data) {
        CaravanRuntime runtime = ACTIVE_CARAVANS.get(key);
        if (runtime != null && runtimeHasLivingMerchant(world, runtime) && runtime.lastActual != null) {
            return runtime.lastActual;
        }
        return routePosition(world, data, key.routeIndex());
    }

    private static void updateMerchantRoles(ServerWorld world,
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
            Text baseName = Text.translatable(courier
                    ? "entity.village-quest.route_courier"
                    : "entity.village-quest.route_merchant." + name);
            merchant.setCustomName(event != null && ordinal == 0
                    ? Text.translatable("entity.village-quest.route_merchant.event", baseName, event.label())
                    : baseName);
            merchant.setCustomNameVisible(courier || (event != null && ordinal == 0));
            ordinal++;
        }
    }

    private static void sampleRoadQuality(ServerWorld world, PlayerQuestData data, int routeIndex, BlockPos center) {
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
                if (isRoadBlock(world.getBlockState(surface.down()))) {
                    road++;
                }
                if (world.getLightLevel(LightType.BLOCK, surface) >= 8) {
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
            QuestState.get(world.getServer()).setDirty(true);
        }
    }

    private static void payArrival(ServerWorld world, UUID ownerId, PlayerQuestData data, int routeIndex) {
        TradeRouteStatus status = status(data, routeIndex);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
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
        if (owner != null) {
            owner.sendMessage(Text.translatable("message.village-quest.trade_route.arrived",
                    routeName(routeIndex), CurrencyService.formatDelta(reward)).formatted(Formatting.GRAY), false);
        }
    }

    public static int collectEscrow(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) return 0;
        PlayerQuestData data = data(world, player.getUuid());
        int amount = Math.max(0, data.getTradeRouteInt(ESCROW));
        if (amount <= 0) return 0;
        data.setTradeRouteInt(ESCROW, 0);
        CurrencyService.addBalance(world, player.getUuid(), amount);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_route.escrow_collected",
                CurrencyService.formatDelta(amount)).formatted(Formatting.GREEN), false);
        return amount;
    }

    public static int escrow(ServerWorld world, UUID playerId) {
        return world == null || playerId == null ? 0 : Math.max(0, data(world, playerId).getTradeRouteInt(ESCROW));
    }

    public static int incomeToday(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) return 0;
        PlayerQuestData data = data(world, playerId);
        return data.getTradeRouteInt(INCOME_DAY) == currentWorldDay(world)
                ? Math.max(0, data.getTradeRouteInt(INCOME_TODAY)) : 0;
    }

    public static boolean useRoadwardenHorn(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) return false;
        if (!hasRouteAccess(world, player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.roadwarden_horn.no_network")
                    .formatted(Formatting.GRAY), true);
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        int today = (int) de.quest.util.TimeUtil.currentDay();
        if (data.getTradeRouteInt(WARDEN_USE_DAY) != today) {
            data.setTradeRouteInt(WARDEN_USE_DAY, today);
            data.setTradeRouteInt(WARDEN_CHARGES, 1);
            QuestState.get(world.getServer()).setDirty(true);
            world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 0.55f, 1.65f);
            player.sendMessage(Text.translatable("message.village-quest.roadwarden_horn.armed")
                    .formatted(Formatting.GOLD), true);
        } else {
            Text target = activeRouteTargetLabel(world, player.getUuid());
            player.sendMessage(target == null
                    ? Text.translatable("message.village-quest.roadwarden_horn.ready")
                    : Text.translatable("message.village-quest.roadwarden_horn.target", target), true);
        }
        SurveyorCompassQuestService.selectRouteEventMode(world, player.getUuid());
        return true;
    }

    public static int routeCount(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) return 0;
        return Math.min(routeCapacity(world, playerId), data(world, playerId).getTradeRouteInt(ROUTE_COUNT));
    }

    public static int routeQuality(ServerWorld world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex) ? quality(data(world, playerId), routeIndex) : 0;
    }

    public static int routeSuccesses(ServerWorld world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? Math.max(0, routeInt(data(world, playerId), routeIndex, "successes")) : 0;
    }

    public static int routeDistanceBlocks(ServerWorld world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? Math.max(0, (int) Math.round(routeDistance(data(world, playerId), routeIndex))) : 0;
    }

    public static TradeRouteSpecialization specialization(ServerWorld world, UUID playerId, int routeIndex) {
        return validRoute(world, playerId, routeIndex)
                ? TradeRouteSpecialization.fromId(routeInt(data(world, playerId), routeIndex, "specialization"))
                : TradeRouteSpecialization.GENERAL;
    }

    public static boolean specialize(ServerWorld world, ServerPlayerEntity player, int routeIndex,
                                     TradeRouteSpecialization specialization) {
        if (world == null || player == null || specialization == null
                || !validRoute(world, player.getUuid(), routeIndex)) return false;
        PlayerQuestData data = data(world, player.getUuid());
        if (specialization(world, player.getUuid(), routeIndex) == specialization) return false;
        String chosenFlag = routeKey(routeIndex, "specialization_chosen");
        int cost = data.hasTradeRouteFlag(chosenFlag) ? 15 : 0;
        if (cost > 0 && !CurrencyService.removeBalance(world, player.getUuid(), cost)) {
            player.sendMessage(Text.translatable("message.village-quest.trade_guild.not_enough",
                    CurrencyService.formatBalance(cost)).formatted(Formatting.RED), false);
            return false;
        }
        setRouteInt(data, routeIndex, "specialization", specialization.id());
        data.setTradeRouteFlag(chosenFlag, true);
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_guild.specialized", routeName(routeIndex),
                specialization.label(), CurrencyService.formatBalance(cost)).formatted(Formatting.GREEN), false);
        refreshUi(world, player);
        return true;
    }

    public static boolean hasUpgrade(ServerWorld world, UUID playerId, int routeIndex, TradeRouteUpgrade upgrade) {
        return validRoute(world, playerId, routeIndex) && hasUpgrade(data(world, playerId), routeIndex, upgrade);
    }

    public static boolean buyUpgrade(ServerWorld world, ServerPlayerEntity player, int routeIndex, TradeRouteUpgrade upgrade) {
        if (world == null || player == null || upgrade == null || !validRoute(world, player.getUuid(), routeIndex)) return false;
        if (TradeGuildService.guildRank(world, player.getUuid()) < upgrade.requiredGuildRank()) {
            player.sendMessage(Text.translatable("message.village-quest.trade_guild.rank_locked", upgrade.requiredGuildRank())
                    .formatted(Formatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (hasUpgrade(data, routeIndex, upgrade)) {
            player.sendMessage(Text.translatable("message.village-quest.trade_guild.upgrade_owned").formatted(Formatting.GRAY), false);
            return false;
        }
        if (!CurrencyService.removeBalance(world, player.getUuid(), upgrade.cost())) {
            player.sendMessage(Text.translatable("message.village-quest.trade_guild.not_enough",
                    CurrencyService.formatBalance(upgrade.cost())).formatted(Formatting.RED), false);
            return false;
        }
        setRouteInt(data, routeIndex, "upgrades", routeInt(data, routeIndex, "upgrades") | upgrade.bit());
        QuestState.get(world.getServer()).setDirty(true);
        player.sendMessage(Text.translatable("message.village-quest.trade_guild.upgrade_bought", upgrade.label(),
                routeName(routeIndex), CurrencyService.formatBalance(upgrade.cost())).formatted(Formatting.GREEN), false);
        refreshUi(world, player);
        return true;
    }

    private static boolean validRoute(ServerWorld world, UUID playerId, int routeIndex) {
        return routeIndex >= 0 && routeIndex < routeCount(world, playerId);
    }

    private static boolean hasUpgrade(PlayerQuestData data, int routeIndex, TradeRouteUpgrade upgrade) {
        return upgrade != null && (routeInt(data, routeIndex, "upgrades") & upgrade.bit()) != 0;
    }

    private static Text upgradeSummary(PlayerQuestData data, int routeIndex) {
        List<String> names = new ArrayList<>();
        for (TradeRouteUpgrade upgrade : TradeRouteUpgrade.values()) {
            if (hasUpgrade(data, routeIndex, upgrade)) names.add(upgrade.label().getString());
        }
        return names.isEmpty() ? Text.translatable("text.village-quest.trade_guild.upgrades_none")
                : Text.literal(String.join(", ", names));
    }

    private static boolean hasActiveEvent(PlayerQuestData data) {
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) if (event(data, i) != null) return true;
        return false;
    }

    private static int eventReward(PlayerQuestData data, int routeIndex, TradeRouteEventType event) {
        int difficulty = switch (event) {
            case HUNGRY_TRAVELERS, MISSING_COURIER -> 0;
            case BROKEN_WHEEL, INJURED_PACK_ANIMAL, ROAD_TOLL -> 1;
            case WASHED_OUT_BRIDGE, STORM_CAMP -> 2;
            case FALSE_DISTRESS -> 3;
        };
        return Math.max(6, Math.min(14, 5 + difficulty * 2
                + (int) Math.floor(routeDistance(data, routeIndex) / 650.0)));
    }

    private static Payloads.TradeRouteMapPayload buildMapPayload(ServerWorld world, UUID ownerId, int action) {
        PlayerQuestData data = data(world, ownerId);
        List<Payloads.TradeRouteNodeData> nodes = new ArrayList<>();
        List<Payloads.TradeRouteLineData> routes = new ArrayList<>();
        List<Payloads.TradeRouteCaravanData> caravans = new ArrayList<>();
        if (hasHome(data)) {
            nodes.add(new Payloads.TradeRouteNodeData(0,
                    Text.translatable("text.village-quest.trade_route.node.caravan_yard"),
                    data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z), true));
        }
        int count = Math.min(MAX_ROUTES, data.getTradeRouteInt(ROUTE_COUNT));
        for (int i = 0; i < count; i++) {
            nodes.add(new Payloads.TradeRouteNodeData(i + 1,
                    villageName(data, i),
                    routeInt(data, i, "x"), routeInt(data, i, "z"), false));
            TradeRouteEventType event = event(data, i);
            boolean surveying = activeSurveyIndex(data) == i;
            List<Payloads.TradeRoutePointData> mapWaypoints = (surveying
                    ? surveyPoints(data)
                    : routeWaypoints(data, i)).stream()
                    .map(point -> new Payloads.TradeRoutePointData(point.x(), point.z()))
                    .toList();
            routes.add(new Payloads.TradeRouteLineData(
                    i,
                    routeName(data, i),
                    status(data, i).id(),
                    status(data, i).label(),
                    quality(data, i),
                    clampProgress(routeInt(data, i, "progress")),
                    routeInt(data, i, "direction") < 0,
                    isStopped(data, i),
                    surveying,
                    event == null ? Text.empty() : event.label(),
                    event == null ? Text.empty() : event.help(),
                    Math.max(0, routeInt(data, i, "earnings")),
                    specialization(world, ownerId, i).label(),
                    upgradeSummary(data, i),
                    mapWaypoints
            ));
            RouteKey key = new RouteKey(ownerId, i);
            caravans.add(new Payloads.TradeRouteCaravanData(
                    i,
                    clampProgress(routeInt(data, i, "progress")),
                    routeInt(data, i, "direction") < 0,
                    ACTIVE_CARAVANS.containsKey(key) && runtimeHasLivingMerchant(world, ACTIVE_CARAVANS.get(key))
            ));
        }
        return new Payloads.TradeRouteMapPayload(
                action,
                Text.translatable("screen.village-quest.trade_route.title"),
                Text.translatable("screen.village-quest.trade_route.summary_guild", count, routeCapacity(world, ownerId),
                        TradeGuildService.rankLabel(TradeGuildService.guildRank(world, ownerId)), incomeToday(world, ownerId)),
                List.copyOf(nodes),
                List.copyOf(routes),
                List.copyOf(caravans)
        );
    }

    private static void tickMapViewers(ServerWorld world, long gameTime) {
        if (gameTime % MAP_UPDATE_TICKS != 0L) {
            return;
        }
        for (UUID viewerId : List.copyOf(MAP_VIEWERS)) {
            ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
            if (viewer == null || viewer.getEntityWorld() != world || !hasRouteAccess(world, viewerId)) {
                MAP_VIEWERS.remove(viewerId);
                continue;
            }
            ServerPlayNetworking.send(viewer, buildMapPayload(world, viewerId, Payloads.TradeRouteMapPayload.ACTION_UPDATE));
        }
        for (UUID viewerId : List.copyOf(MINIMAP_VIEWERS)) {
            ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
            if (viewer == null || viewer.getEntityWorld() != world || !hasRouteAccess(world, viewerId)) {
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

    private static void cleanupInactiveCaravans(ServerWorld world) {
        for (Map.Entry<RouteKey, CaravanRuntime> entry : List.copyOf(ACTIVE_CARAVANS.entrySet())) {
            if (!runtimeHasLivingMerchant(world, entry.getValue())) {
                removeRuntime(world, entry.getKey());
            }
        }
        for (Entity entity : allEntities(world)) {
            if (entity.getCommandTags().contains(TAG_ROUTE_CARAVAN)) {
                RouteKey key = ENTITY_ROUTES.get(entity.getUuid());
                CaravanRuntime runtime = key == null ? null : ACTIVE_CARAVANS.get(key);
                if (runtime != null && runtime.merchantIds.contains(entity.getUuid())) {
                    continue;
                }
                ENTITY_ROUTES.remove(entity.getUuid());
                entity.discard();
            } else if (entity.getCommandTags().contains(TAG_ROUTE_ATTACKER)) {
                RouteKey key = ATTACKER_ROUTES.get(entity.getUuid());
                CaravanRuntime runtime = key == null ? null : ACTIVE_CARAVANS.get(key);
                if (runtime != null && runtime.attackerIds.contains(entity.getUuid())) {
                    continue;
                }
                ATTACKER_ROUTES.remove(entity.getUuid());
                entity.discard();
            }
        }
        MATERIALIZATION_RETRY_AT.entrySet().removeIf(entry -> entry.getValue() <= world.getTime()
                && !ACTIVE_CARAVANS.containsKey(entry.getKey()));
    }

    private static void removeOwnerRuntimes(ServerWorld world, UUID ownerId) {
        MATERIALIZATION_RETRY_AT.keySet().removeIf(key -> key.ownerId().equals(ownerId));
        for (RouteKey key : List.copyOf(ACTIVE_CARAVANS.keySet())) {
            if (key.ownerId().equals(ownerId)) {
                removeRuntime(world, key);
            }
        }
        String ownerTag = ownerTag(ownerId);
        for (Entity entity : allEntities(world)) {
            if (entity.getCommandTags().contains(ownerTag)
                    && (entity.getCommandTags().contains(TAG_ROUTE_CARAVAN)
                    || entity.getCommandTags().contains(TAG_ROUTE_ATTACKER))) {
                ENTITY_ROUTES.remove(entity.getUuid());
                ATTACKER_ROUTES.remove(entity.getUuid());
                entity.discard();
            }
        }
    }

    private static void removeRuntime(ServerWorld world, RouteKey key) {
        MATERIALIZATION_RETRY_AT.remove(key);
        CaravanRuntime runtime = ACTIVE_CARAVANS.remove(key);
        if (runtime != null) {
            discardRuntimeEntities(world, runtime);
        }
    }

    private static void discardRouteEntities(ServerWorld world, RouteKey key) {
        removeRuntime(world, key);
        String owner = ownerTag(key.ownerId());
        String route = routeTag(key.routeIndex());
        for (Entity entity : allEntities(world)) {
            RouteKey mapped = ENTITY_ROUTES.get(entity.getUuid());
            if (mapped == null) {
                mapped = ATTACKER_ROUTES.get(entity.getUuid());
            }
            boolean tagged = entity.getCommandTags().contains(owner) && entity.getCommandTags().contains(route)
                    && (entity.getCommandTags().contains(TAG_ROUTE_CARAVAN)
                    || entity.getCommandTags().contains(TAG_ROUTE_ATTACKER));
            if (key.equals(mapped) || tagged) {
                ENTITY_ROUTES.remove(entity.getUuid());
                ATTACKER_ROUTES.remove(entity.getUuid());
                entity.discard();
            }
        }
    }

    private static boolean isRecoveryVisible(ServerPlayerEntity observer, Entity entity) {
        return observer != null && entity != null && observer.squaredDistanceTo(entity)
                <= CARAVAN_VISIBLE_RECOVERY_RADIUS * (double) CARAVAN_VISIBLE_RECOVERY_RADIUS;
    }

    private static void discardRuntimeEntities(ServerWorld world, CaravanRuntime runtime) {
        for (UUID merchantId : runtime.merchantIds) {
            ENTITY_ROUTES.remove(merchantId);
            Entity entity = findEntity(world, merchantId);
            if (entity != null) {
                entity.discard();
            }
        }
        discardAttackers(world, runtime);
    }

    private static void discardAttackers(ServerWorld world, CaravanRuntime runtime) {
        for (UUID attackerId : List.copyOf(runtime.attackerIds)) {
            ATTACKER_ROUTES.remove(attackerId);
            Entity entity = findEntity(world, attackerId);
            if (entity != null) {
                entity.discard();
            }
        }
        runtime.attackerIds.clear();
    }

    private static boolean runtimeHasLivingMerchant(ServerWorld world, CaravanRuntime runtime) {
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

    private static BlockPos routePosition(ServerWorld world, PlayerQuestData data, int routeIndex) {
        return routePosition(world, data, routeIndex, clampProgress(routeInt(data, routeIndex, "progress")));
    }

    private static BlockPos routePosition(ServerWorld world, PlayerQuestData data, int routeIndex, int progress) {
        RoutePoint point = pointAlongRoute(data, routeIndex, progress);
        int x = point.x();
        int z = point.z();
        BlockPos probe = new BlockPos(x, 64, z);
        int y = isChunkLoaded(world, probe)
                ? world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)
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
        List<RoutePoint> points = new ArrayList<>();
        points.add(new RoutePoint(data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z)));
        points.addAll(routeWaypoints(data, routeIndex));
        points.add(new RoutePoint(routeInt(data, routeIndex, "x"), routeInt(data, routeIndex, "z")));
        return points;
    }

    private static BlockPos findNearbyRoadSurface(ServerWorld world, BlockPos center, int radius) {
        if (world == null || center == null || !isChunkLoaded(world, center)) {
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
                        || !isRoadBlock(world.getBlockState(surface.down()))) {
                    continue;
                }
                double distance = surface.getSquaredDistance(center);
                if (distance < bestDistance) {
                    best = surface;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static BlockPos safeSurface(ServerWorld world, int x, int z) {
        BlockPos probe = new BlockPos(x, 64, z);
        if (!isChunkLoaded(world, probe)) {
            return null;
        }
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, topY, z);
        if (!world.getBlockState(feet).isAir()) {
            feet = feet.up();
        }
        BlockPos below = feet.down();
        BlockState belowState = world.getBlockState(below);
        if (!world.getBlockState(feet).isAir()
                || !world.getBlockState(feet.up()).isAir()
                || belowState.isAir()
                || !belowState.getFluidState().isEmpty()
                || belowState.isIn(BlockTags.LEAVES)
                || isDangerousSupport(belowState)
                || !belowState.isSideSolidFullSquare(world, below, net.minecraft.util.math.Direction.UP)) {
            return null;
        }
        return feet;
    }

    private static boolean isChunkLoaded(ServerWorld world, BlockPos pos) {
        return world != null && pos != null && world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static BlockPos findCaravanSurface(ServerWorld world, BlockPos center, int radius) {
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
                    if (isRoadBlock(world.getBlockState(candidate.down()))) {
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

    private static boolean isStableCaravanSurface(ServerWorld world, BlockPos center) {
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
        return state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.POWDER_SNOW);
    }

    private static BlockPos findNearbySafeSurface(ServerWorld world, int x, int z, int radius) {
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
        return state.isOf(Blocks.DIRT_PATH)
                || state.isOf(Blocks.GRAVEL)
                || state.isOf(Blocks.COBBLESTONE)
                || state.isOf(Blocks.STONE_BRICKS)
                || state.isOf(Blocks.OAK_PLANKS)
                || state.isOf(Blocks.SPRUCE_PLANKS)
                || state.isOf(Blocks.STONE_SLAB)
                || state.isOf(Blocks.COBBLESTONE_SLAB)
                || state.isOf(Blocks.OAK_SLAB)
                || state.isOf(Blocks.SPRUCE_SLAB);
    }

    private static boolean isPlank(ItemStack stack) {
        return stack.isOf(Items.OAK_PLANKS)
                || stack.isOf(Items.SPRUCE_PLANKS)
                || stack.isOf(Items.BIRCH_PLANKS)
                || stack.isOf(Items.JUNGLE_PLANKS)
                || stack.isOf(Items.ACACIA_PLANKS)
                || stack.isOf(Items.DARK_OAK_PLANKS)
                || stack.isOf(Items.MANGROVE_PLANKS)
                || stack.isOf(Items.CHERRY_PLANKS)
                || stack.isOf(Items.BAMBOO_PLANKS)
                || stack.isOf(Items.PALE_OAK_PLANKS);
    }

    private static boolean consumePair(ServerPlayerEntity player,
                                       Predicate<ItemStack> first,
                                       int firstAmount,
                                       Predicate<ItemStack> second,
                                       int secondAmount) {
        if (countPlayerInventory(player, first) < firstAmount || countPlayerInventory(player, second) < secondAmount) {
            return false;
        }
        return consumePlayerInventory(player, first, firstAmount) && consumePlayerInventory(player, second, secondAmount);
    }

    private static int countPlayerInventory(ServerPlayerEntity player, Predicate<ItemStack> matcher) {
        int total = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consumePlayerInventory(ServerPlayerEntity player, Predicate<ItemStack> matcher, int amount) {
        if (amount <= 0 || countPlayerInventory(player, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.decrement(removed);
            remaining -= removed;
        }
        player.playerScreenHandler.sendContentUpdates();
        return remaining == 0;
    }

    public static void backfillUnlockedLedger(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasRouteAccess(world, player.getUuid())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(LEDGER_GRANT_RECORDED)) {
            return;
        }
        boolean granted = grantLedger(world, player);
        if (granted) {
            player.sendMessage(Text.translatable("message.village-quest.trade_route.provisional_unlocked")
                    .formatted(Formatting.GOLD), false);
        }
    }

    private static boolean grantLedger(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.CARAVAN_LEDGER == null) {
            return false;
        }
        boolean alreadyPresent = hasLedger(player);
        giveLedger(player);
        PlayerQuestData data = data(world, player.getUuid());
        data.setMilestoneFlag(LEDGER_GRANT_RECORDED, true);
        QuestState.get(world.getServer()).markDirty();
        return !alreadyPresent;
    }

    private static boolean hasLedger(ServerPlayerEntity player) {
        if (player == null || ModItems.CARAVAN_LEDGER == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.CARAVAN_LEDGER)) {
                return true;
            }
        }
        return false;
    }

    private static void giveLedger(ServerPlayerEntity player) {
        if (player == null || ModItems.CARAVAN_LEDGER == null) {
            return;
        }
        if (hasLedger(player)) {
            return;
        }
        ItemStack ledger = new ItemStack(ModItems.CARAVAN_LEDGER);
        if (!player.getInventory().insertStack(ledger)) {
            player.dropItem(ledger, false);
        }
        player.playerScreenHandler.sendContentUpdates();
    }

    private static void giveTestWayfinder(ServerPlayerEntity player) {
        if (player == null || ModItems.SURVEYORS_COMPASS == null) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.SURVEYORS_COMPASS)) {
                return;
            }
        }
        ItemStack compass = new ItemStack(ModItems.SURVEYORS_COMPASS);
        if (!player.getInventory().insertStack(compass)) {
            player.dropItem(compass, false);
        }
        player.playerScreenHandler.sendContentUpdates();
    }

    private static void bindHome(PlayerQuestData data, int x, int z) {
        data.setTradeRouteInt(HOME_X, x);
        data.setTradeRouteInt(HOME_Z, z);
        data.setTradeRouteFlag(HOME_BOUND, true);
    }

    private static int activeSurveyIndex(PlayerQuestData data) {
        int stored = data == null ? 0 : data.getTradeRouteInt(SURVEY_ROUTE);
        return stored <= 0 ? -1 : stored - 1;
    }

    private static List<RoutePoint> routeWaypoints(PlayerQuestData data, int routeIndex) {
        int count = Math.min(MAX_WAYPOINTS, Math.max(0, routeInt(data, routeIndex, "waypoint_count")));
        List<RoutePoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new RoutePoint(
                    routeInt(data, routeIndex, "waypoint_" + i + "_x"),
                    routeInt(data, routeIndex, "waypoint_" + i + "_z")
            ));
        }
        return points;
    }

    private static void setRouteWaypoints(PlayerQuestData data, int routeIndex, List<RoutePoint> points) {
        String prefix = routeKey(routeIndex, "waypoint_");
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.startsWith(prefix)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        int count = Math.min(MAX_WAYPOINTS, points == null ? 0 : points.size());
        setRouteInt(data, routeIndex, "waypoint_count", count);
        for (int i = 0; i < count; i++) {
            RoutePoint point = points.get(i);
            setRouteInt(data, routeIndex, "waypoint_" + i + "_x", point.x());
            setRouteInt(data, routeIndex, "waypoint_" + i + "_z", point.z());
        }
    }

    private static List<RoutePoint> surveyPoints(PlayerQuestData data) {
        int count = Math.min(MAX_WAYPOINTS, Math.max(0, data.getTradeRouteInt(SURVEY_POINT_COUNT)));
        List<RoutePoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(surveyPoint(data, i));
        }
        return points;
    }

    private static RoutePoint surveyPoint(PlayerQuestData data, int pointIndex) {
        return new RoutePoint(
                data.getTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_x"),
                data.getTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_z")
        );
    }

    private static void setSurveyPoint(PlayerQuestData data, int pointIndex, RoutePoint point) {
        data.setTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_x", point.x());
        data.setTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_z", point.z());
    }

    private static List<RoutePoint> normalizedSurveyPoints(PlayerQuestData data, int routeIndex) {
        RoutePoint home = new RoutePoint(data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z));
        RoutePoint destination = new RoutePoint(routeInt(data, routeIndex, "x"), routeInt(data, routeIndex, "z"));
        List<RoutePoint> normalized = new ArrayList<>();
        RoutePoint previous = home;
        for (RoutePoint point : surveyPoints(data)) {
            if (previous.distanceSquared(point) < 16.0) {
                continue;
            }
            normalized.add(point);
            previous = point;
        }
        if (!normalized.isEmpty() && normalized.getLast().distanceSquared(destination) < 16.0) {
            normalized.removeLast();
        }
        return normalized;
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

    private static int quality(PlayerQuestData data, int routeIndex) {
        int quality = routeInt(data, routeIndex, "quality");
        return quality <= 0 ? 20 : Math.min(100, quality);
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

    private static Text routeName(int routeIndex) {
        return Text.translatable("text.village-quest.trade_route.name", routeIndex + 1);
    }

    private static Text routeName(PlayerQuestData data, int routeIndex) {
        String custom = data == null ? "" : data.getTradeRouteString(routeKey(routeIndex, "name"));
        return custom.isBlank() ? routeName(routeIndex) : Text.literal(custom);
    }

    private static Text villageName(PlayerQuestData data, int routeIndex) {
        String custom = data == null ? "" : data.getTradeRouteString(routeKey(routeIndex, "name"));
        return custom.isBlank()
                ? Text.translatable("text.village-quest.trade_route.node.village", routeIndex + 1)
                : Text.literal(custom);
    }

    private static String sanitizeRouteName(String requestedName) {
        if (requestedName == null) {
            return "";
        }
        String clean = Formatting.strip(requestedName).replaceAll("\\p{Cntrl}", "")
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

    private static int currentWorldDay(ServerWorld world) {
        return world == null ? 0 : (int) (world.getTimeOfDay() / 24000L);
    }

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static ServerPlayerEntity nearestPlayer(ServerWorld world, BlockPos pos, int radius) {
        if (world == null || pos == null) {
            return null;
        }
        ServerPlayerEntity best = null;
        double bestDistance = radius * (double) radius;
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != world) {
                continue;
            }
            double distance = player.getBlockPos().getSquaredDistance(pos);
            if (distance <= bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Entity findEntity(ServerWorld world, UUID entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        for (Entity entity : world.iterateEntities()) {
            if (entityId.equals(entity.getUuid())) {
                return entity;
            }
        }
        return null;
    }

    private static List<Entity> allEntities(ServerWorld world) {
        List<Entity> entities = new ArrayList<>();
        if (world != null) {
            for (Entity entity : world.iterateEntities()) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private static String ownerTag(UUID ownerId) {
        return TAG_ROUTE_OWNER_PREFIX + ownerId;
    }

    private static void refreshUi(ServerWorld world, ServerPlayerEntity player) {
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    private record RouteKey(UUID ownerId, int routeIndex) {}

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
        private BlockPos lastExpected = BlockPos.ORIGIN;
        private BlockPos lastActual;
        private BlockPos lastLeaderPosition;
        private int stuckSeconds;
        private int recoveryGraceSeconds;
        private int recoveryCount;
    }
}
