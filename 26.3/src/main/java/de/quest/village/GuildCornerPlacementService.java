package de.quest.village;

import de.quest.VillageQuest;
import de.quest.caravan.TradeRouteService;
import de.quest.content.block.GuildNoticePostBlock;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.QuestState;
import de.quest.registry.ModBlocks;
import de.quest.shrine.VillageBondService;
import de.quest.util.NaturalSurfacePolicy;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Local-visit entry point for world-global Guild Corners.
 *
 * <p>No chunk is loaded by this service. Desert villages use the authored desert preset; every
 * other biome family currently shares the authored generic preset while retaining its classified
 * style in SavedData. A missing preset causes no reservation or terrain mutation. Every template
 * must contain exactly one Guild Notice Post and remain within the SavedData footprint limit.</p>
 */
public final class GuildCornerPlacementService {
    private static final long VISIT_CHECK_INTERVAL_TICKS = 40L;
    private static final long EXISTING_VILLAGE_INHABITED_TICKS = 72_000L;
    private static final int MAX_SURFACE_DELTA = 1;
    private static final int NETWORK_SAFETY_RADIUS = 8;
    private static final ConcurrentMap<UUID, Long> NEXT_VISIT_CHECK = new ConcurrentHashMap<>();

    private GuildCornerPlacementService() {}

    /** Bounded online-player visit check; it never scans the world or loads a chunk. */
    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel world)) continue;
            long gameTime = world.getGameTime();
            long nextCheck = NEXT_VISIT_CHECK.getOrDefault(player.getUUID(), 0L);
            if (gameTime < nextCheck) continue;
            NEXT_VISIT_CHECK.put(player.getUUID(), gameTime + VISIT_CHECK_INTERVAL_TICKS);
            ShadowsTradeRoadEncounterService.VillageMarker marker =
                    ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
            if (marker != null) processLocalVillageVisit(world, marker, gameTime);
        }
    }

    public static void onLocalVillageVisit(ServerLevel world, ServerPlayer player,
                                           ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (world == null || player == null || marker == null) return;
        long gameTime = world.getGameTime();
        long nextCheck = NEXT_VISIT_CHECK.getOrDefault(player.getUUID(), 0L);
        if (gameTime < nextCheck) return;
        NEXT_VISIT_CHECK.put(player.getUUID(), gameTime + VISIT_CHECK_INTERVAL_TICKS);
        processLocalVillageVisit(world, marker, gameTime);
    }

    private static void processLocalVillageVisit(ServerLevel world,
                                                 ShadowsTradeRoadEncounterService.VillageMarker marker,
                                                 long gameTime) {
        GuildCornerLandmarkState state = GuildCornerLandmarkState.get(world.getServer());
        GuildCornerLandmarkState.VillageKey key = new GuildCornerLandmarkState.VillageKey(
                GuildCornerLandmarkState.dimensionKey(world), marker.centerX(), marker.centerZ());
        GuildCornerLandmarkState.LandmarkSnapshot existing = state.snapshot(key).orElse(null);
        if (existing != null && !state.canAttemptAutomaticPlacement(key, gameTime)) return;

        TemplateHandle template = loadTemplate(world, existing == null
                ? classifyStyle(world, marker.centerX(), marker.centerZ()) : existing.style());
        if (template == null) return;

        if (existing != null) {
            retryReservedPlacement(world, state, existing, template, gameTime);
            return;
        }
        boolean edited = isExistingOrEditedVillage(world, marker);
        Site site = findSite(world, marker, template);
        GuildCornerPlacementPolicy.VisitContext context = new GuildCornerPlacementPolicy.VisitContext(
                true, site != null, edited, false, site != null, false, true);
        if (GuildCornerPlacementPolicy.decide(null, context)
                != GuildCornerPlacementPolicy.Decision.RESERVE_AND_PLACE_AUTOMATICALLY) {
            return;
        }

        GuildCornerLandmarkState.ReservationResult reservation = state.reserve(key, site.postPos(),
                site.facing(), template.style(), site.footprint());
        if (!reservation.created()) return;
        placeReserved(world, state, reservation.landmark(), template, site, gameTime);
    }

    /**
     * Explicit local retrofit/recovery path. It never repairs a generated or player-removed corner,
     * never falls back to another biome preset, and makes no reservation while the preset is absent.
     */
    public static ManualPlacementResult requestManualPlacement(ServerLevel world, ServerPlayer player,
                                                               ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (world == null || player == null || marker == null) return ManualPlacementResult.NOT_IN_VILLAGE;
        GuildCornerLandmarkState state = GuildCornerLandmarkState.get(world.getServer());
        GuildCornerLandmarkState.VillageKey key = new GuildCornerLandmarkState.VillageKey(
                GuildCornerLandmarkState.dimensionKey(world), marker.centerX(), marker.centerZ());
        GuildCornerLandmarkState.LandmarkSnapshot existing = state.snapshot(key).orElse(null);
        if (existing != null && existing.status() == GuildCornerLandmarkState.LandmarkStatus.REMOVED_BY_PLAYER) {
            return ManualPlacementResult.REMOVED_BY_PLAYER;
        }
        if (existing != null && existing.status() == GuildCornerLandmarkState.LandmarkStatus.GENERATED) {
            return ManualPlacementResult.ALREADY_GENERATED;
        }
        GuildCornerLandmarkState.CornerStyle style = existing == null
                ? classifyStyle(world, marker.centerX(), marker.centerZ()) : existing.style();
        TemplateHandle template = loadTemplate(world, style);
        if (template == null) return ManualPlacementResult.MISSING_OR_INVALID_PRESET;
        long gameTime = world.getGameTime();
        if (existing != null) {
            GuildCornerLandmarkState.Footprint footprint = existing.footprint();
            if (!horizontalChunksLoaded(world, footprint.minX(), footprint.minZ(),
                    footprint.maxX(), footprint.maxZ())) {
                return ManualPlacementResult.AREA_NOT_LOADED;
            }
            retryReservedPlacement(world, state, existing, template, gameTime);
            GuildCornerLandmarkState.LandmarkSnapshot updated = state.snapshot(key).orElse(existing);
            return updated.status() == GuildCornerLandmarkState.LandmarkStatus.GENERATED
                    ? ManualPlacementResult.PLACED : ManualPlacementResult.SITE_UNSAFE;
        }

        Site site = findSite(world, marker, template);
        if (site == null) {
            return hasLoadedCandidateFootprint(world, marker, template)
                    ? ManualPlacementResult.SITE_UNSAFE : ManualPlacementResult.AREA_NOT_LOADED;
        }
        GuildCornerLandmarkState.ReservationResult reservation = state.reserve(key, site.postPos(),
                site.facing(), template.style(), site.footprint());
        if (!reservation.created()) return ManualPlacementResult.SITE_UNSAFE;
        placeReserved(world, state, reservation.landmark(), template, site, gameTime);
        return state.snapshot(key).map(snapshot -> snapshot.status() == GuildCornerLandmarkState.LandmarkStatus.GENERATED
                ? ManualPlacementResult.PLACED : ManualPlacementResult.SITE_UNSAFE)
                .orElse(ManualPlacementResult.SITE_UNSAFE);
    }

    public static void handleDisconnect(UUID playerId) {
        if (playerId != null) NEXT_VISIT_CHECK.remove(playerId);
    }

    public static void resetRuntimeState() {
        NEXT_VISIT_CHECK.clear();
    }

    static GuildCornerLandmarkState.CornerStyle classifyStyleName(String biomeId) {
        String path = biomeId == null ? "" : biomeId.toLowerCase(Locale.ROOT);
        if (path.contains("cherry")) return GuildCornerLandmarkState.CornerStyle.CHERRY;
        if (path.contains("mangrove") || path.contains("swamp")) return GuildCornerLandmarkState.CornerStyle.SWAMP;
        if (path.contains("jungle") || path.contains("bamboo")) return GuildCornerLandmarkState.CornerStyle.JUNGLE;
        if (path.contains("savanna")) return GuildCornerLandmarkState.CornerStyle.SAVANNA;
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen")) {
            return GuildCornerLandmarkState.CornerStyle.SNOWY;
        }
        if (path.contains("taiga") || path.contains("pine") || path.contains("spruce")) {
            return GuildCornerLandmarkState.CornerStyle.TAIGA;
        }
        if (path.contains("desert") || path.contains("badlands") || path.contains("mesa")) {
            return GuildCornerLandmarkState.CornerStyle.DESERT;
        }
        if (path.contains("plains") || path.contains("meadow") || path.contains("forest")) {
            return GuildCornerLandmarkState.CornerStyle.PLAINS;
        }
        return GuildCornerLandmarkState.CornerStyle.GENERIC;
    }

    private static void retryReservedPlacement(ServerLevel world, GuildCornerLandmarkState state,
                                               GuildCornerLandmarkState.LandmarkSnapshot existing,
                                               TemplateHandle template, long gameTime) {
        Site site = siteFromReservation(template, existing);
        if (site == null || !existing.footprint().equals(site.footprint())) {
            state.requireManualPlacement(existing.key());
            return;
        }
        GuildCornerLandmarkState.Footprint footprint = site.footprint();
        if (!horizontalChunksLoaded(world, footprint.minX(), footprint.minZ(),
                footprint.maxX(), footprint.maxZ())) {
            state.scheduleRetry(existing.key(), gameTime);
            return;
        }
        if (!validateSite(world, site)) {
            state.requireManualPlacement(existing.key());
            return;
        }
        placeReserved(world, state, existing, template, site, gameTime);
    }

    private static void placeReserved(ServerLevel world, GuildCornerLandmarkState state,
                                      GuildCornerLandmarkState.LandmarkSnapshot landmark,
                                      TemplateHandle template, Site site, long gameTime) {
        if (!validateSite(world, site) || !validTemplate(template.template())) {
            state.requireManualPlacement(landmark.key());
            return;
        }
        Map<BlockPos, BlockState> before = snapshot(world, site.footprint());
        boolean placed = template.template().placeInWorld(world, site.origin(), site.origin(),
                site.settings(), world.getRandom(), 3);
        BlockState post = world.getBlockState(site.postPos());
        boolean validPost = post.is(ModBlocks.GUILD_NOTICE_POST)
                && post.hasProperty(GuildNoticePostBlock.FACING)
                && post.getValue(GuildNoticePostBlock.FACING) == direction(site.facing());
        if (finalizePlacement(placed, validPost, () -> restore(world, before))) {
            state.markGenerated(landmark.key());
            return;
        }
        state.requireManualPlacement(landmark.key());
        VillageQuest.LOGGER.warn("Guild Corner placement failed at {} for village {},{}; kept for manual recovery",
                site.origin(), landmark.key().anchorX(), landmark.key().anchorZ());
    }

    private static Site findSite(ServerLevel world,
                                 ShadowsTradeRoadEncounterService.VillageMarker marker,
                                 TemplateHandle template) {
        int width = template.template().getSize().getX();
        int depth = template.template().getSize().getZ();
        Site inside = findBestSite(world, template, GuildCornerCandidateSearch.inside(
                marker.minX(), marker.maxX(), marker.minZ(), marker.maxZ(), width, depth));
        if (inside != null) return inside;
        return findBestSite(world, template, GuildCornerCandidateSearch.around(
                marker.minX(), marker.maxX(), marker.minZ(), marker.maxZ(), width, depth));
    }

    private static Site findBestSite(ServerLevel world, TemplateHandle template,
                                     List<GuildCornerCandidateSearch.Candidate> candidates) {
        Site best = null;
        for (GuildCornerCandidateSearch.Candidate candidate : candidates) {
            Site site = createSite(world, template, candidate.centerX(), candidate.centerZ(), candidate.facing());
            if (site == null || !validateSite(world, site)) continue;
            if (best == null || isBetterSurfaceFit(site.surfaceDelta(), best.surfaceDelta())) {
                best = site;
                if (site.surfaceDelta() == 0) return site;
            }
        }
        return best;
    }

    private static Site siteFromReservation(TemplateHandle template,
                                            GuildCornerLandmarkState.LandmarkSnapshot landmark) {
        StructurePlaceSettings settings = settings(landmark.facing());
        List<StructureTemplate.StructureBlockInfo> relativePosts = template.template()
                .filterBlocks(BlockPos.ZERO, settings, ModBlocks.GUILD_NOTICE_POST);
        if (relativePosts.size() != 1) return null;
        BlockPos origin = landmark.cornerPos().subtract(relativePosts.getFirst().pos());
        BoundingBox box = template.template().getBoundingBox(settings, origin);
        return site(template, landmark.facing(), origin, settings, box, landmark.cornerPos(), 0);
    }

    private static Site createSite(ServerLevel world, TemplateHandle template, int centerX, int centerZ,
                                   GuildCornerLandmarkState.CornerFacing facing) {
        StructurePlaceSettings settings = settings(facing);
        BoundingBox zeroBox = template.template().getBoundingBox(settings, BlockPos.ZERO);
        int width = zeroBox.getXSpan();
        int depth = zeroBox.getZSpan();
        int minX = centerX - width / 2;
        int minZ = centerZ - depth / 2;
        if (!horizontalChunksLoaded(world, minX, minZ, minX + width - 1, minZ + depth - 1)) return null;

        int minSurfaceY = Integer.MAX_VALUE;
        int maxSurfaceY = Integer.MIN_VALUE;
        for (int x = minX; x < minX + width; x++) {
            for (int z = minZ; z < minZ + depth; z++) {
                int surfaceY = supportingSurfaceY(world, x, z);
                if (surfaceY < world.getMinY()) return null;
                minSurfaceY = Math.min(minSurfaceY, surfaceY);
                maxSurfaceY = Math.max(maxSurfaceY, surfaceY);
            }
        }
        int surfaceDelta = maxSurfaceY - minSurfaceY;
        if (surfaceDelta > MAX_SURFACE_DELTA) return null;
        int originY = surfaceReplacementOriginY(minSurfaceY, zeroBox.minY());
        BlockPos origin = new BlockPos(minX - zeroBox.minX(), originY, minZ - zeroBox.minZ());
        BoundingBox box = template.template().getBoundingBox(settings, origin);
        List<StructureTemplate.StructureBlockInfo> posts = template.template()
                .filterBlocks(origin, settings, ModBlocks.GUILD_NOTICE_POST);
        if (posts.size() != 1) return null;
        return site(template, facing, origin, settings, box, posts.getFirst().pos(), surfaceDelta);
    }

    private static Site site(TemplateHandle template, GuildCornerLandmarkState.CornerFacing facing,
                             BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
                             BlockPos postPos, int surfaceDelta) {
        try {
            GuildCornerLandmarkState.Footprint footprint = new GuildCornerLandmarkState.Footprint(
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            Set<BlockPos> preserved = new HashSet<>();
            for (StructureTemplate.StructureBlockInfo info : template.template()
                    .filterBlocks(origin, settings, Blocks.STRUCTURE_VOID)) preserved.add(info.pos().immutable());
            return new Site(template, facing, origin.immutable(), settings, footprint, postPos.immutable(),
                    Set.copyOf(preserved), surfaceDelta);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean validateSite(ServerLevel world, Site site) {
        GuildCornerLandmarkState.Footprint footprint = site.footprint();
        if (!horizontalChunksLoaded(world, footprint.minX(), footprint.minZ(),
                footprint.maxX(), footprint.maxZ())) return false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                int surfaceY = supportingSurfaceY(world, x, z);
                if (surfaceY < world.getMinY()) return false;
                minSurface = Math.min(minSurface, surfaceY);
                maxSurface = Math.max(maxSurface, surfaceY);
                cursor.set(x, surfaceY, z);
                BlockState surface = world.getBlockState(cursor);
                boolean preservesSurface = preservesLocalSurface(site.preserved(), x, surfaceY, z);
                // A nearby Structure Void cannot protect a player-made block that will actually be overwritten.
                if (isHumanTrace(surface) && !site.preserved().contains(cursor)) return false;
                if (!surfaceCellAllowed(preservesSurface, isNaturalSurface(surface),
                        world.getBlockEntity(cursor) != null, isHumanTrace(surface))) return false;
                for (int y = surfaceY + 1; y <= footprint.maxY(); y++) {
                    cursor.set(x, y, z);
                    if (site.preserved().contains(cursor)
                            || y == surfaceY && preservesSurface) continue;
                    BlockState state = world.getBlockState(cursor);
                    if (!state.canBeReplaced() || world.getBlockEntity(cursor) != null) return false;
                }
            }
        }
        if (maxSurface - minSurface > MAX_SURFACE_DELTA) return false;
        BlockPos center = new BlockPos((footprint.minX() + footprint.maxX()) / 2,
                maxSurface, (footprint.minZ() + footprint.maxZ()) / 2);
        return footprintWithinBuildHeight(footprint.minY(), footprint.maxY(), world.getMinY(), world.getMaxY())
                && world.getWorldBorder().isWithinBounds(new BlockPos(footprint.minX(), center.getY(), footprint.minZ()))
                && world.getWorldBorder().isWithinBounds(new BlockPos(footprint.minX(), center.getY(), footprint.maxZ()))
                && world.getWorldBorder().isWithinBounds(new BlockPos(footprint.maxX(), center.getY(), footprint.minZ()))
                && world.getWorldBorder().isWithinBounds(new BlockPos(footprint.maxX(), center.getY(), footprint.maxZ()))
                && !QuestState.get(world.getServer()).isTerrainModified(center, 1)
                && !TradeRouteService.isNearAnyNetworkAnchor(world, center, NETWORK_SAFETY_RADIUS)
                && !VillageBondService.isNearAnyBondAnchor(world, center, NETWORK_SAFETY_RADIUS)
                && !hasHumanTrace(world, site);
    }

    private static boolean isExistingOrEditedVillage(ServerLevel world,
                                                      ShadowsTradeRoadEncounterService.VillageMarker marker) {
        QuestState state = QuestState.get(world.getServer());
        BlockPos center = new BlockPos(marker.centerX(), world.getSeaLevel(), marker.centerZ());
        if (state.isTerrainModified(center, Math.max(marker.maxX() - marker.minX(),
                marker.maxZ() - marker.minZ()) / 32 + 1)) return true;
        int minChunkX = marker.minX() >> 4;
        int maxChunkX = marker.maxX() >> 4;
        int minChunkZ = marker.minZ() >> 4;
        int maxChunkZ = marker.maxZ() >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                var chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk != null && chunk.getInhabitedTime() >= EXISTING_VILLAGE_INHABITED_TICKS) return true;
            }
        }
        return false;
    }

    private static boolean hasLoadedCandidateFootprint(ServerLevel world,
                                                       ShadowsTradeRoadEncounterService.VillageMarker marker,
                                                       TemplateHandle template) {
        int width = template.template().getSize().getX();
        int depth = template.template().getSize().getZ();
        for (GuildCornerCandidateSearch.Candidate candidate : GuildCornerCandidateSearch.insideThenAround(
                marker.minX(), marker.maxX(), marker.minZ(), marker.maxZ(), width, depth)) {
            StructurePlaceSettings settings = settings(candidate.facing());
            BoundingBox zeroBox = template.template().getBoundingBox(settings, BlockPos.ZERO);
            int minX = candidate.centerX() - zeroBox.getXSpan() / 2;
            int minZ = candidate.centerZ() - zeroBox.getZSpan() / 2;
            if (horizontalChunksLoaded(world, minX, minZ,
                    minX + zeroBox.getXSpan() - 1, minZ + zeroBox.getZSpan() - 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean horizontalChunksLoaded(ServerLevel world, int minX, int minZ, int maxX, int maxZ) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (world.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
            }
        }
        return true;
    }

    private static boolean hasHumanTrace(ServerLevel world, Site site) {
        GuildCornerLandmarkState.Footprint footprint = site.footprint();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                int surfaceY = supportingSurfaceY(world, x, z);
                if (surfaceY < world.getMinY()) return true;
                for (int y = surfaceY - 1; y <= Math.min(surfaceY + 4, footprint.maxY()); y++) {
                    cursor.set(x, y, z);
                    if (site.preserved().contains(cursor)
                            || y == surfaceY && preservesLocalSurface(site.preserved(), x, y, z)) continue;
                    if (world.getBlockEntity(cursor) != null || isHumanTrace(world.getBlockState(cursor))) return true;
                }
            }
        }
        return false;
    }

    private static int supportingSurfaceY(ServerLevel world, int x, int z) {
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        while (y >= world.getMinY()) {
            cursor.setY(y);
            if (!isNonSupportingSurfaceCover(world.getBlockState(cursor))) return y;
            y--;
        }
        return world.getMinY() - 1;
    }

    static boolean isNonSupportingSurfaceCover(BlockState state) {
        return state != null && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && (state.isAir() || state.canBeReplaced());
    }

    static boolean isBetterSurfaceFit(int candidateDelta, int currentBestDelta) {
        return candidateDelta >= 0 && candidateDelta < currentBestDelta;
    }

    private static boolean isNaturalSurface(BlockState state) {
        return NaturalSurfacePolicy.isStructurallyEligible(state);
    }

    private static boolean isHumanTrace(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.LOGS) && state.hasProperty(RotatedPillarBlock.AXIS)
                && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("planks") || path.contains("fence") || path.contains("door")
                || path.contains("trapdoor") || path.contains("bed") || path.contains("chest")
                || path.contains("barrel") || path.contains("crafting_table") || path.contains("furnace")
                || path.contains("farmland") || path.contains("dirt_path") || path.contains("rail")
                || path.contains("redstone") || path.contains("torch") || path.contains("lantern")
                || path.contains("glass") || path.contains("stone_brick") || path.contains("copper")
                || path.contains("concrete") || path.endsWith("_wall") || path.endsWith("_stairs")
                || path.endsWith("_slab") || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    private static GuildCornerLandmarkState.CornerStyle classifyStyle(ServerLevel world, int x, int z) {
        String biome = world.getBiome(new BlockPos(x, world.getSeaLevel(), z)).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("");
        return classifyStyleName(biome);
    }

    private static TemplateHandle loadTemplate(ServerLevel world, GuildCornerLandmarkState.CornerStyle style) {
        GuildCornerLandmarkState.CornerStyle resourceStyle = templateResourceStyle(style);
        Identifier id = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,
                "guild_corner/" + resourceStyle.name().toLowerCase(Locale.ROOT));
        Optional<StructureTemplate> loaded = world.getStructureTemplateManager().get(id);
        return loaded.isPresent() && validTemplate(loaded.get())
                ? new TemplateHandle(style, loaded.get()) : null;
    }

    static GuildCornerLandmarkState.CornerStyle templateResourceStyle(
            GuildCornerLandmarkState.CornerStyle classifiedStyle) {
        return classifiedStyle == GuildCornerLandmarkState.CornerStyle.DESERT
                ? GuildCornerLandmarkState.CornerStyle.DESERT
                : GuildCornerLandmarkState.CornerStyle.GENERIC;
    }

    private static boolean validTemplate(StructureTemplate template) {
        Vec3i size = template.getSize();
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
                || size.getX() > 16 || size.getY() > 16 || size.getZ() > 16) return false;
        CompoundTag encoded = template.save(new CompoundTag());
        return GuildCornerTemplateContract.isValid(encoded, GuildCornerPlacementService::paletteStateHasBlockEntity)
                && template.filterBlocks(BlockPos.ZERO, settings(GuildCornerLandmarkState.CornerFacing.NORTH),
                ModBlocks.GUILD_NOTICE_POST).size() == 1;
    }

    private static boolean paletteStateHasBlockEntity(String blockName) {
        try {
            int separator = blockName.indexOf(':');
            Identifier id = separator < 0 ? Identifier.withDefaultNamespace(blockName)
                    : Identifier.fromNamespaceAndPath(blockName.substring(0, separator), blockName.substring(separator + 1));
            var block = BuiltInRegistries.BLOCK.getValue(id);
            return block == null || block == Blocks.AIR && !"minecraft:air".equals(blockName)
                    || block.defaultBlockState().hasBlockEntity();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    static boolean footprintWithinBuildHeight(int minY, int maxY, int worldMinY, int worldMaxY) {
        return minY >= worldMinY && maxY < worldMaxY && minY <= maxY;
    }

    static int surfaceReplacementOriginY(int lowestSurfaceBlockY, int rotatedTemplateMinY) {
        return lowestSurfaceBlockY - rotatedTemplateMinY;
    }

    static boolean surfaceCellAllowed(boolean structureVoid, boolean naturalSurface,
                                      boolean blockEntity, boolean humanTrace) {
        return !blockEntity && (structureVoid || naturalSurface && !humanTrace);
    }

    static boolean preservesLocalSurface(Set<BlockPos> preserved, int x, int surfaceY, int z) {
        if (preserved == null || preserved.isEmpty()) return false;
        for (BlockPos position : preserved) {
            if (position.getX() == x && position.getZ() == z
                    && Math.abs(position.getY() - surfaceY) <= MAX_SURFACE_DELTA) return true;
        }
        return false;
    }

    static boolean finalizePlacement(boolean placed, boolean validPost, Runnable rollback) {
        if (placed && validPost) return true;
        rollback.run();
        return false;
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel world, GuildCornerLandmarkState.Footprint footprint) {
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(footprint.minX(), footprint.minY(), footprint.minZ(),
                footprint.maxX(), footprint.maxY(), footprint.maxZ())) {
            before.put(pos.immutable(), world.getBlockState(pos));
        }
        return before;
    }

    private static void restore(ServerLevel world, Map<BlockPos, BlockState> before) {
        before.forEach((pos, block) -> world.setBlock(pos, block, 3));
    }

    private static StructurePlaceSettings settings(GuildCornerLandmarkState.CornerFacing facing) {
        return new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation(facing))
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    private static Rotation rotation(GuildCornerLandmarkState.CornerFacing facing) {
        return switch (facing) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    private static Direction direction(GuildCornerLandmarkState.CornerFacing facing) {
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }

    private record TemplateHandle(GuildCornerLandmarkState.CornerStyle style,
                                  StructureTemplate template) {}

    private record Site(TemplateHandle template, GuildCornerLandmarkState.CornerFacing facing,
                        BlockPos origin, StructurePlaceSettings settings,
                        GuildCornerLandmarkState.Footprint footprint, BlockPos postPos,
                        Set<BlockPos> preserved, int surfaceDelta) {}

    public enum ManualPlacementResult {
        PLACED("placed"),
        ALREADY_GENERATED("already_generated"),
        REMOVED_BY_PLAYER("removed_by_player"),
        MISSING_OR_INVALID_PRESET("missing_preset"),
        AREA_NOT_LOADED("area_not_loaded"),
        SITE_UNSAFE("site_unsafe"),
        NOT_IN_VILLAGE("not_in_village");

        private final String key;
        ManualPlacementResult(String key) { this.key = key; }
        public String translationKey() { return "message.village-quest.guild_corner.manual." + key; }
    }
}
