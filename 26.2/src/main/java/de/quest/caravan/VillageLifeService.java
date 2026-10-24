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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/** Event-driven population observations for already known settlements. */
public final class VillageLifeService {
    private static final long NEARBY_CHECK_INTERVAL_TICKS = 20L * 5L;
    private static final long NOTIFICATION_CHECK_INTERVAL_TICKS = 20L * 10L;
    private static final int MAX_OBSERVED_ADULTS = 64;
    private static final int MAX_BED_PATH_CHECKS = 12;
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
            Component village = de.quest.kingdom.KingdomVillageService.name(
                    de.quest.kingdom.LivingKingdomsState.get(server), key, villageLabel(data, x, z));
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
        if (observation.status()==VillageLifeState.Status.ACTIVE && loadedArea(world,marker)) {
            AdultEvidence adults=adultVillagers(world,marker); recordPopulation(world,marker,adults);
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
        var zones = de.quest.kingdom.KingdomVillageService.confirmedZones(world, marker);
        AABB area = de.quest.kingdom.KingdomVillageService.observationArea(world, marker, zones);
        boolean fullyLoaded = VillageInhabitantPolicy.classify(false,
                ((int) Math.floor(area.minX)) >> 4, ((int) Math.floor(area.maxX - 1)) >> 4,
                ((int) Math.floor(area.minZ)) >> 4, ((int) Math.floor(area.maxZ - 1)) >> 4,
                world::hasChunk) == VillageInhabitantPolicy.Status.ABANDONED;
        if (!fullyLoaded) return new ResettlementProgress(0, 0, false);
        AdultEvidence adults = adultVillagers(world, marker);
        recordPopulation(world,marker,adults);
        BedEvidence beds = assignedReachableBeds(world, adults.residents());
        return new ResettlementProgress(adults.residents().size(), beds.count(), adults.complete() && beds.fullyLoaded());
    }

    public record ResettlementProgress(int adults, int beds, boolean fullyLoaded) {}
    private static boolean loadedArea(ServerLevel world,ShadowsTradeRoadEncounterService.VillageMarker marker) {
        var zones=de.quest.kingdom.KingdomVillageService.confirmedZones(world,marker);
        var area=de.quest.kingdom.KingdomVillageService.observationArea(world,marker,zones);
        return VillageInhabitantPolicy.classify(false,((int)Math.floor(area.minX))>>4,((int)Math.floor(area.maxX-1))>>4,
                ((int)Math.floor(area.minZ))>>4,((int)Math.floor(area.maxZ-1))>>4,world::hasChunk)==VillageInhabitantPolicy.Status.ABANDONED;
    }
    private static void recordPopulation(ServerLevel world,ShadowsTradeRoadEncounterService.VillageMarker marker,AdultEvidence adults) {
        if (world.getServer()==null || !adults.complete()) return;
        var state=de.quest.kingdom.LivingKingdomsState.get(world.getServer());
        var key=new VillageLifeState.VillageKey(world.dimension().identifier().toString(),marker.centerX(),marker.centerZ());
        state.byLegacy(key).ifPresent(village -> state.observePopulation(village.id(),adults.residents().size(),true,world.getServer().overworld().getGameTime()));
    }

    private record AdultEvidence(List<Villager> residents,boolean complete) {}
    private static AdultEvidence adultVillagers(ServerLevel world,
            ShadowsTradeRoadEncounterService.VillageMarker marker) {
        var zones = de.quest.kingdom.KingdomVillageService.confirmedZones(world, marker);
        AABB area = de.quest.kingdom.KingdomVillageService.observationArea(world, marker, zones);
        var state=world.getServer()==null?null:de.quest.kingdom.LivingKingdomsState.get(world.getServer());
        String dimension=world.dimension().identifier().toString();
        var target=state==null?null:state.byLegacy(new VillageLifeState.VillageKey(dimension,marker.centerX(),marker.centerZ())).map(de.quest.kingdom.LivingKingdomsState.VillageView::id).orElse(null);
        List<Villager> adults = new ArrayList<>();
        int checked=0; boolean complete=state==null || !state.readOnly() && !state.founding().readOnly();
        for (Villager villager : world.getEntitiesOfClass(Villager.class, area,
                value -> value.isAlive() && !value.isRemoved() && !value.isBaby())) {
            if (checked++>=128) { complete=false; break; }
            if (!villager.isAlive() || villager.isRemoved() || villager.isBaby()
                    || !de.quest.kingdom.KingdomVillageService.withinConfirmedArea(zones, villager.blockPosition())) continue;
            var membership=de.quest.kingdom.VillagePopulationPolicy.classify(state,target,dimension,villager.getUUID(),villager.blockPosition());
            if (membership==de.quest.kingdom.VillagePopulationPolicy.Membership.UNKNOWN) { complete=false; continue; }
            if (membership!=de.quest.kingdom.VillagePopulationPolicy.Membership.INCLUDED) continue;
            adults.add(villager);
            if (adults.size() >= MAX_OBSERVED_ADULTS) break;
        }
        return new AdultEvidence(List.copyOf(adults),complete);
    }

    private record BedEvidence(int count, boolean fullyLoaded) {}

    /** Reads Vanilla HOME evidence only; never assigns beds or modifies a villager's Brain. */
    private static BedEvidence assignedReachableBeds(ServerLevel world, List<Villager> villagers) {
        Set<BlockPos> beds = new HashSet<>();
        var budget=world.getServer()==null?null:de.quest.kingdom.KingdomBuildingService.workBudget(world.getServer());
        int pathChecks = 0;
        boolean fullyLoaded = true;
        for (Villager villager : villagers) {
            var assigned = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (assigned.isEmpty() || !assigned.get().dimension().equals(world.dimension())) continue;
            BlockPos home = assigned.get().pos();
            BlockPos origin = villager.blockPosition();
            if (Math.abs((long) home.getX() - origin.getX()) > 32
                    || Math.abs((long) home.getZ() - origin.getZ()) > 32
                    || Math.abs((long) home.getY() - origin.getY()) > 16) continue;
            // Include a narrow local navigation halo before requesting a path.
            // UNKNOWN is distinct from an inaccessible bed and grants no new evidence.
            boolean corridorLoaded = true;
            for (int x = (Math.min(home.getX(), origin.getX()) - 8) >> 4;
                 x <= (Math.max(home.getX(), origin.getX()) + 8) >> 4; x++) {
                for (int z = (Math.min(home.getZ(), origin.getZ()) - 8) >> 4;
                     z <= (Math.max(home.getZ(), origin.getZ()) + 8) >> 4; z++) {
                    if (!world.hasChunk(x, z)) corridorLoaded = false;
                }
            }
            if (!corridorLoaded) { fullyLoaded = false; continue; }
            if (budget!=null && !budget.takeBlock()) { fullyLoaded=false; break; }
            var block = world.getBlockState(home);
            if (!block.is(BlockTags.BEDS) || !block.hasProperty(BedBlock.PART)
                    || !block.hasProperty(BedBlock.FACING)) continue;
            BlockPos foot = block.getValue(BedBlock.PART) == BedPart.FOOT ? home
                    : home.relative(block.getValue(BedBlock.FACING).getOpposite());
            if (!world.hasChunk(foot.getX() >> 4, foot.getZ() >> 4)) {
                fullyLoaded = false;
                continue;
            }
            if (budget!=null && !foot.equals(home) && !budget.takeBlock()) { fullyLoaded=false; break; }
            var footState = foot.equals(home)?block:world.getBlockState(foot);
            if (!footState.is(block.getBlock()) || !footState.hasProperty(BedBlock.PART)
                    || footState.getValue(BedBlock.PART) != BedPart.FOOT || beds.contains(foot)
                    || !footState.getFluidState().isEmpty() || footState.getValue(BedBlock.FACING)!=block.getValue(BedBlock.FACING)) continue;
            BlockPos head=foot.relative(footState.getValue(BedBlock.FACING));
            if (!world.hasChunkAt(head)) { fullyLoaded=false; continue; }
            if (budget!=null && !head.equals(home) && !budget.takeBlock()) { fullyLoaded=false; break; }
            var headState=head.equals(home)?block:world.getBlockState(head);
            if (!headState.is(footState.getBlock()) || !headState.hasProperty(BedBlock.PART)
                    || headState.getValue(BedBlock.PART)!=BedPart.HEAD || !headState.getFluidState().isEmpty()
                    || headState.getValue(BedBlock.FACING)!=footState.getValue(BedBlock.FACING)) continue;
            if (pathChecks++ >= MAX_BED_PATH_CHECKS || budget!=null && !budget.takePaths(1)) {
                fullyLoaded = false; // Bounded incomplete evidence cannot reject recovery.
                break;
            }
            var path = villager.getNavigation().createPath(home, 0);
            if (path != null && path.canReach()) beds.add(foot.immutable());
            if (beds.size() >= 3) break;
        }
        return new BedEvidence(beds.size(), fullyLoaded);
    }
}
