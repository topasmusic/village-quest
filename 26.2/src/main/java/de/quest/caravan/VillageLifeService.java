package de.quest.caravan;

import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.guildtown.GuildTownLifePolicy;
import de.quest.shrine.VillageBondService;
import de.quest.village.VillageLifeState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/** Event-driven population observations for already known settlements. */
public final class VillageLifeService {
    private static final long NEARBY_CHECK_INTERVAL_TICKS = 20L * 5L;
    private static final long NOTIFICATION_CHECK_INTERVAL_TICKS = 20L * 10L;
    private static final Map<VillageLifeState.VillageKey, Long> NEXT_NEARBY_CHECK = new HashMap<>();
    private static final Map<java.util.UUID, Long> NEXT_NOTIFICATION_CHECK = new HashMap<>();

    private VillageLifeService() {}

    public static void resetRuntimeState() {
        NEXT_NEARBY_CHECK.clear();
        NEXT_NOTIFICATION_CHECK.clear();
    }

    /** Delivers saved abandonment and restoration events to known players, including after login. */
    public static void notifyPending(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return;
        long now = server.overworld().getGameTime();
        if (now < NEXT_NOTIFICATION_CHECK.getOrDefault(player.getUUID(), 0L)) return;
        NEXT_NOTIFICATION_CHECK.put(player.getUUID(), now + NOTIFICATION_CHECK_INTERVAL_TICKS);
        QuestState quests = QuestState.get(server);
        PlayerQuestData data = quests.getPlayerData(player.getUUID());
        VillageLifeState life = VillageLifeState.get(server);
        for (int index = 0; index < VillageBondService.historicalVillageCount(data); index++) {
            String base = VillageBondService.villageKey(index, "");
            String dimension = data.getTradeRouteString(base + "dimension");
            if (dimension.isBlank()) dimension = "minecraft:overworld";
            int x = data.getTradeRouteInt(base + "x");
            int z = data.getTradeRouteInt(base + "z");
            VillageLifeState.VillageKey key;
            try {
                key = new VillageLifeState.VillageKey(dimension, x, z);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            int cycle = life.abandonmentCycle(key);
            if (cycle == 0) continue;
            String abandonedKey = base + "abandon_notified_cycle";
            String restoredKey = base + "restored_notified_cycle";
            VillageLifeState.Status status = life.status(key);
            Component village = villageLabel(data, x, z);
            if (status != VillageLifeState.Status.ACTIVE
                    && data.getTradeRouteInt(abandonedKey) < cycle) {
                String message = status == VillageLifeState.Status.RECOVERING
                        ? "message.village-quest.village_life.recovering"
                        : affectedRoute(data, x, z)
                        ? "message.village-quest.village_life.abandoned_route"
                        : "message.village-quest.village_life.abandoned";
                player.sendSystemMessage(Component.translatable(message, village)
                        .withStyle(ChatFormatting.GOLD), false);
                data.setTradeRouteInt(abandonedKey, cycle);
                quests.setDirty();
            } else if (status == VillageLifeState.Status.ACTIVE
                    && data.getTradeRouteInt(restoredKey) < cycle) {
                player.sendSystemMessage(Component.translatable(
                        "message.village-quest.village_life.restored", village)
                        .withStyle(ChatFormatting.GREEN), false);
                data.setTradeRouteInt(restoredKey, cycle);
                quests.setDirty();
            }
        }
    }

    private static Component villageLabel(PlayerQuestData data, int x, int z) {
        for (int route = 0; route < data.getTradeRouteInt("route_count"); route++) {
            if (TradeRouteData.routeInt(data, route, "x") == x
                    && TradeRouteData.routeInt(data, route, "z") == z) {
                return TradeRouteData.villageName(data, route);
            }
        }
        return Component.translatable("text.village-quest.village_life.settlement", x, z);
    }

    private static boolean affectedRoute(PlayerQuestData data, int x, int z) {
        int routes = data.getTradeRouteInt("route_count");
        if (routes <= 0) return false;
        if (TradeRouteData.hasHome(data) && !TradeRouteData.isPlayerYard(data)
                && data.getTradeRouteInt("home_x") == x
                && data.getTradeRouteInt("home_z") == z) return true;
        for (int route = 0; route < routes; route++) {
            if (TradeRouteData.routeInt(data, route, "x") == x
                    && TradeRouteData.routeInt(data, route, "z") == z) return true;
        }
        return false;
    }

    public static void observeNearby(ServerLevel world, ServerPlayer player,
                                     ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (world == null || player == null || marker == null) return;
        String dimension = world.dimension().identifier().toString();
        VillageLifeState.VillageKey key = new VillageLifeState.VillageKey(
                dimension, marker.centerX(), marker.centerZ());
        long gameTime = world.getGameTime();
        if (gameTime < NEXT_NEARBY_CHECK.getOrDefault(key, 0L)) return;
        boolean known = false;
        for (PlayerQuestData data : QuestState.get(world.getServer()).getPlayersView().values()) {
            if (VillageBondService.historicalVillageIndex(data, dimension,
                    marker.centerX(), marker.centerZ()) >= 0) {
                known = true;
                break;
            }
        }
        if (!known) return;
        NEXT_NEARBY_CHECK.put(key, gameTime + NEARBY_CHECK_INTERVAL_TICKS);
        observe(world, marker);
    }

    public static VillageLifeState.Observation observe(ServerLevel world,
            ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (world == null || marker == null) {
            return new VillageLifeState.Observation(VillageLifeState.Status.ACTIVE, false);
        }
        VillageLifeState.VillageKey key = new VillageLifeState.VillageKey(
                world.dimension().identifier().toString(), marker.centerX(), marker.centerZ());
        VillageInhabitantPolicy.Status inhabitants =
                TradeRouteService.villageInhabitantStatus(world, marker);
        VillageLifeState.Evidence evidence = switch (inhabitants) {
            case INHABITED -> VillageLifeState.Evidence.INHABITED;
            case ABANDONED -> VillageLifeState.Evidence.EMPTY_LOADED;
            case UNKNOWN -> VillageLifeState.Evidence.UNKNOWN;
        };
        VillageLifeState life = VillageLifeState.get(world.getServer());
        VillageLifeState.Observation observation = life.observe(key, evidence, world.getGameTime());
        if (observation.becameAbandoned()) {
            recordChronicleTransition(world, key, life.abandonmentCycle(key), false);
        }
        if (observation.status() != VillageLifeState.Status.ACTIVE) {
            ResettlementProgress progress = resettlementProgress(world, marker);
            VillageLifeState.RecoveryObservation recovery = life.observeResettlement(
                    key, progress.adults(), progress.beds(),
                    progress.fullyLoaded(), world.getGameTime());
            if (recovery.becameActive()) {
                recordChronicleTransition(world, key, life.abandonmentCycle(key), true);
            }
        }
        return observation;
    }

    private static void recordChronicleTransition(ServerLevel world,
                                                   VillageLifeState.VillageKey village,
                                                   int cycle, boolean restored) {
        QuestState quests = QuestState.get(world.getServer());
        if (restored) de.quest.reputation.ResettlementSupportService.onRestored(world.getServer(), village, cycle);
        for (Map.Entry<java.util.UUID, PlayerQuestData> entry : quests.getPlayersView().entrySet()) {
            TradeGuildService.onSettlementChanged(world, entry.getKey());
            if (!restored && GuildTownLifePolicy.pauseAffected(quests, entry.getValue(), village).changed()) {
                quests.setDirty();
            }
            if (VillageBondService.historicalVillageIndex(entry.getValue(), village.dimension(),
                    village.anchorX(), village.anchorZ()) < 0) {
                continue;
            }
            if (VillageLifeChronicle.record(entry.getValue(), entry.getKey(), village,
                    cycle, restored, world.getGameTime())) {
                quests.setDirty();
            }
        }
    }

    public static ResettlementProgress resettlementProgress(ServerLevel world,
            ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (world == null || marker == null) return new ResettlementProgress(0, 0, false);
        boolean fullyLoaded = VillageInhabitantPolicy.classify(false,
                (marker.minX() - 16) >> 4, (marker.maxX() + 16) >> 4,
                (marker.minZ() - 16) >> 4, (marker.maxZ() + 16) >> 4,
                world::hasChunk) == VillageInhabitantPolicy.Status.ABANDONED;
        if (!fullyLoaded) return new ResettlementProgress(0, 0, false);
        List<Villager> adults = adultVillagers(world, marker);
        return new ResettlementProgress(adults.size(),
                adults.isEmpty() ? 0 : nearbyBeds(world, adults), true);
    }

    public record ResettlementProgress(int adults, int beds, boolean fullyLoaded) {}

    private static List<Villager> adultVillagers(ServerLevel world,
            ShadowsTradeRoadEncounterService.VillageMarker marker) {
        AABB area = new AABB(marker.minX() - 16.0, world.getMinY(), marker.minZ() - 16.0,
                marker.maxX() + 17.0, world.getMaxY(), marker.maxZ() + 17.0);
        List<Villager> adults = new ArrayList<>();
        for (Villager villager : world.getEntitiesOfClass(Villager.class, area,
                value -> value.isAlive() && !value.isRemoved() && !value.isBaby())) {
            adults.add(villager);
            if (adults.size() >= 2) break;
        }
        return adults;
    }

    /** Searches a small loaded area around each settler, without loading new chunks. */
    private static int nearbyBeds(ServerLevel world, List<Villager> villagers) {
        Set<BlockPos> beds = new HashSet<>();
        for (Villager villager : villagers) {
            BlockPos origin = villager.blockPosition();
            int minY = Math.max(world.getMinY(), origin.getY() - 8);
            int maxY = Math.min(world.getMaxY() - 1, origin.getY() + 8);
            for (int x = origin.getX() - 16; x <= origin.getX() + 16; x++) {
                for (int z = origin.getZ() - 16; z <= origin.getZ() + 16; z++) {
                    if (!world.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var block = world.getBlockState(pos);
                        if (block.is(BlockTags.BEDS)
                                && block.getValue(BedBlock.PART) == BedPart.FOOT) {
                            beds.add(pos);
                            if (beds.size() >= 2) return 2;
                        }
                    }
                }
            }
        }
        return beds.size();
    }
}
