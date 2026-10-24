package de.quest.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import de.quest.VillageQuest;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.map.SurfaceMapProjection;
import de.quest.map.FantasyMapStyle;
import de.quest.map.FantasyMapAtlas;
import de.quest.map.FantasyMapStyle.Cell;
import de.quest.map.FantasyMapStyle.Terrain;
import de.quest.map.MapWorkBudget;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Turns already-loaded Minecraft terrain into a compact illustrated guild map.
 *
 * <p>The renderer never loads chunks. It remembers samples the player has seen,
 * groups them into a restrained fantasy-map palette, adds height shading, shoreline
 * ink, and small deterministic terrain glyphs. This keeps the result readable like
 * the concept art without pretending that an unrelated painted background is the
 * player's world.</p>
 */
public final class SurfaceMapRenderer {
    private static final MapWorkBudget WORK = new MapWorkBudget();
    private static final int PAINT_TILE_CELLS = 16;
    private static final int PAINT_TILE_PIXELS = PAINT_TILE_CELLS * FantasyMapStyle.PIXELS;
    private static final int MAX_PAINT_TILES = 128; // 8 MiB of ARGB data, shared by screen/HUD.
    private static final Map<PaintKey, PaintedTile> PAINT_TILES = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<PaintKey, PaintedTile> entry) {
            return size() > MAX_PAINT_TILES;
        }
    };
    private static final Map<Long, CompletableFuture<List<Sample>>> TILE_READS = new LinkedHashMap<>();
    private static final int MAX_REMEMBERED_SAMPLES = 120_000;
    private static final int CACHE_MAGIC = 0x56514D50; // VQMP
    private static final int CACHE_VERSION = 1;
    private static final int TILE_CELLS = 32;
    private static final int MAX_LOADED_TILE_MARKERS = 512;
    private static final long CACHE_FLUSH_TICKS = 20L * 15L;
    private static final SurfaceTexture SCREEN_TEXTURE = new SurfaceTexture(
            Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "surface_map_screen"));
    private static final SurfaceTexture HUD_TEXTURE = new SurfaceTexture(
            Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "surface_map_hud"));
    private static final Map<Long, Sample> SURFACE_MEMORY = new LinkedHashMap<>(24_576, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Sample> eldest) {
            return size() > MAX_REMEMBERED_SAMPLES;
        }
    };
    private static final Map<Long, Boolean> LOADED_TILES = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > MAX_LOADED_TILE_MARKERS;
        }
    };
    private static final Set<Long> DIRTY_TILES = new HashSet<>();
    private static Raster screenRaster;
    private static Raster tourRaster;
    private static final SurfaceTexture TOUR_TEXTURE = new SurfaceTexture(
            Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "surface_map_tour"));
    private static Raster hudRaster;
    private static ClientLevel memoryLevel;
    private static int memoryGrid;
    private static boolean memoryPersistent;
    private static Path cacheDirectory;
    private static long lastCacheFlushTick;
    private static CompletableFuture<Void> cacheWrites = CompletableFuture.completedFuture(null);

    private SurfaceMapRenderer() {}

    public static void drawScreen(GuiGraphics graphics, int x, int y, int width, int height,
                                  int minX, int maxX, int minZ, int maxZ) {
        drawScreen(graphics, x, y, width, height, minX, maxX, minZ, maxZ, true);
    }

    public static void drawScreen(GuiGraphics graphics, int x, int y, int width, int height,
                                  int minX, int maxX, int minZ, int maxZ,
                                  boolean refreshTerrain) {
        if (de.quest.client.screen.UiTestTour.active()) {
            drawTour(graphics, x, y, width, height, minX, maxX, minZ, maxZ);
            return;
        }
        screenRaster = raster(Minecraft.getInstance(), screenRaster, minX, maxX, minZ, maxZ,
                Math.min(96, scaledSamples(Math.max(48, width / 2))), Math.min(64, scaledSamples(Math.max(28, height / 2))),
                50L, refreshTerrain);
        draw(graphics, SCREEN_TEXTURE, screenRaster, x, y, width, height,
                minX, maxX, minZ, maxZ, true);
    }

    public static void drawHud(GuiGraphics graphics, int x, int y, int width, int height,
                               int minX, int maxX, int minZ, int maxZ) {
        hudRaster = raster(Minecraft.getInstance(), hudRaster, minX, maxX, minZ, maxZ,
                Math.min(64, scaledSamples(Math.max(32, width / 2))), Math.min(40, scaledSamples(Math.max(24, height / 2))),
                25L, true);
        draw(graphics, HUD_TEXTURE, hudRaster, x, y, width, height,
                minX, maxX, minZ, maxZ, false);
    }

    public static void invalidateScreen() {
        screenRaster = null;
    }

    private static void drawTour(GuiGraphics graphics, int x, int y, int width, int height,
                                 int minX, int maxX, int minZ, int maxZ) {
        int cellSize = sampleCellSize(minX, maxX, minZ, maxZ, 96, 54);
        WORK.begin(System.nanoTime() / 50_000_000L);
        if (tourRaster == null || !tourRaster.covers(null, minX, maxX, minZ, maxZ, cellSize, sampleGridSize())) {
            var coverage = SurfaceMapProjection.tileCoverage(minX, maxX, minZ, maxZ, cellSize);
            Sample[] samples = new Sample[coverage.cellWidth() * coverage.cellHeight()];
            for (int row = 0; row < coverage.cellHeight(); row++) {
                int wz = fixedSampleCoordinate(coverage.originCellZ() + row, cellSize);
                for (int col = 0; col < coverage.cellWidth(); col++) {
                    int wx = fixedSampleCoordinate(coverage.originCellX() + col, cellSize);
                    double land = Math.sin(wx / 110.0) + Math.cos(wz / 90.0);
                    double ridge = Math.sin(wx / 75.0) - Math.cos(wz / 120.0);
                    Terrain terrain = land < -.35 || Math.abs(wx - 90 * Math.sin(wz / 130.0)) < cellSize ? Terrain.WATER
                            : land < -.10 ? Terrain.SAND : ridge > 1.6 ? Terrain.SNOW
                            : ridge > 1.3 ? Terrain.MOUNTAIN : land > .65 ? Terrain.FOREST : Terrain.GRASS;
                    int color = switch (terrain) {
                        case WATER -> 0x4E99AC; case SAND -> 0xD7C18C; case FOREST -> 0x5A7949; default -> 0x8CAC56;
                    };
                    samples[row * coverage.cellWidth() + col] = new Sample(terrain, terrain == Terrain.WATER ? 62
                            : 70 + (int) (Math.max(0, ridge) * 45), color, wx, wz);
                }
            }
            tourRaster = new Raster(null, coverage.minX(), coverage.maxX(), coverage.minZ(), coverage.maxZ(),
                    coverage.originCellX(), coverage.originCellZ(), cellSize, sampleGridSize(),
                    coverage.cellWidth(), coverage.cellHeight(), samples, 0);
        }
        draw(graphics, TOUR_TEXTURE, tourRaster, x, y, width, height, minX, maxX, minZ, maxZ, true);
    }

    public static void clearTour() {
        tourRaster = null;
        TOUR_TEXTURE.release();
    }

    public static void clear() {
        clearTour();
        scheduleCacheWrites();
        screenRaster = null;
        hudRaster = null;
        memoryLevel = null;
        cacheDirectory = null;
        SURFACE_MEMORY.clear();
        LOADED_TILES.clear();
        DIRTY_TILES.clear();
        TILE_READS.clear();
        PAINT_TILES.clear();
        SCREEN_TEXTURE.release();
        HUD_TEXTURE.release();
    }

    private static Raster raster(Minecraft client, Raster existing,
                                 int minX, int maxX, int minZ, int maxZ,
                                 int sampleWidth, int sampleHeight, long maxAge,
                                 boolean refreshTerrain) {
        ClientLevel level = client.level;
        if (level == null) {
            return null;
        }
        ensureWorld(client, level);
        long now = System.nanoTime() / 50_000_000L;
        // Refreshes and budgets must both advance while a singleplayer screen pauses the world.
        WORK.begin(now);
        applyTileReads();
        if (!refreshTerrain && existing != null && existing.level == level) {
            return existing;
        }
        if (now - lastCacheFlushTick >= CACHE_FLUSH_TICKS) {
            scheduleCacheWrites();
            lastCacheFlushTick = now;
        }
        int cellSize = sampleCellSize(minX, maxX, minZ, maxZ, sampleWidth, sampleHeight);
        int gridSize = sampleGridSize();
        if (existing != null && existing.covers(level, minX, maxX, minZ, maxZ, cellSize, gridSize)) {
            refreshRaster(existing, now, maxAge);
            return existing;
        }
        SurfaceMapProjection.Coverage coverage = SurfaceMapProjection.tileCoverage(
                minX, maxX, minZ, maxZ, cellSize);
        int originCellX = coverage.originCellX();
        int originCellZ = coverage.originCellZ();
        int rasterWidth = coverage.cellWidth();
        int rasterHeight = coverage.cellHeight();
        Sample[] samples = new Sample[rasterWidth * rasterHeight];
        for (int row = 0; row < rasterHeight; row++) {
            int worldZ = fixedSampleCoordinate(originCellZ + row, cellSize);
            for (int column = 0; column < rasterWidth; column++) {
                int worldX = fixedSampleCoordinate(originCellX + column, cellSize);
                samples[row * rasterWidth + column] = SURFACE_MEMORY.getOrDefault(surfaceKey(worldX, worldZ), unknown(worldX, worldZ));
            }
        }
        Raster created = new Raster(level, coverage.minX(), coverage.maxX(), coverage.minZ(), coverage.maxZ(),
                originCellX, originCellZ,
                cellSize, gridSize, rasterWidth, rasterHeight, samples, now);
        refreshRaster(created, now, maxAge);
        return created;
    }

    private static void refreshRaster(Raster raster, long now, long maxAge) {
        if (raster.cursor >= raster.order.length) {
            if (now - raster.sampledAt < maxAge) return;
            raster.cursor = 0;
        }
        while (raster.cursor < raster.order.length && WORK.sample()) {
            int index = raster.order[raster.cursor++];
            Sample previous = raster.samples[index];
            Sample next = sample(raster.level, previous.worldX, previous.worldZ);
            if (!next.equals(previous)) {
                raster.samples[index] = next;
                raster.dirtyCell(index % raster.width, index / raster.width);
            }
        }
        if (raster.cursor == raster.order.length) raster.sampledAt = now;
    }

    private static Sample sample(ClientLevel level, int worldX, int worldZ) {
        long key = ((long) worldX << 32) ^ (worldZ & 0xFFFF_FFFFL);
        Sample live = sampleLoaded(level, worldX, worldZ);
        if (live.terrain != Terrain.UNKNOWN) {
            Sample previous = SURFACE_MEMORY.put(key, live);
            if (!live.equals(previous)) {
                DIRTY_TILES.add(tileKey(worldX, worldZ));
            }
            return live;
        }
        Sample remembered = SURFACE_MEMORY.get(key);
        if (remembered != null) {
            return remembered;
        }
        loadTile(worldX, worldZ);
        return SURFACE_MEMORY.getOrDefault(key, unknown(worldX, worldZ));
    }

    private static Sample sampleLoaded(ClientLevel level, int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        if (!level.hasChunk(chunkX, chunkZ)) {
            return unknown(worldX, worldZ);
        }
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return unknown(worldX, worldZ);
        }
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        // ChunkAccess already returns the top occupied block, not the first air
        // block above it. Subtracting again skips thin surfaces over air/void.
        int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        if (topY < level.getMinY()) {
            return unknown(worldX, worldZ);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(worldX, topY, worldZ);
        BlockState state = chunk.getBlockState(pos);
        MapColor color = MapColor.NONE;
        for (int depth = 0; depth < 10 && pos.getY() >= level.getMinY(); depth++, pos.move(0, -1, 0)) {
            state = chunk.getBlockState(pos);
            color = state.getMapColor(level, pos);
            if (color != MapColor.NONE) {
                break;
            }
        }
        if (color == MapColor.NONE) {
            return unknown(worldX, worldZ);
        }
        int rgb = color.calculateARGBColor(MapColor.Brightness.NORMAL);
        return new Sample(classify(state, rgb, topY), topY, rgb, worldX, worldZ);
    }

    private static Terrain classify(BlockState state, int rgb, int height) {
        if (state.is(Blocks.WATER) || state.is(Blocks.ICE)
                || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            return Terrain.WATER;
        }
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return Terrain.SNOW;
        }
        if (state.is(Blocks.SAND) || state.is(Blocks.SANDSTONE)) {
            return Terrain.SAND;
        }
        if (state.is(Blocks.RED_SAND) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.TERRACOTTA)) {
            return Terrain.BADLANDS;
        }
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) return Terrain.FOREST;
        if (height >= 128 && red + green + blue > 540) {
            return Terrain.SNOW;
        }
        if (height >= 112) {
            return Terrain.MOUNTAIN;
        }
        if (state.is(Blocks.OAK_LEAVES) || state.is(Blocks.SPRUCE_LEAVES)
                || state.is(Blocks.BIRCH_LEAVES) || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.ACACIA_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.MANGROVE_LEAVES) || state.is(Blocks.CHERRY_LEAVES)
                || state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            return Terrain.FOREST;
        }
        if (red > green + 28 && red > blue + 45) {
            return Terrain.BADLANDS;
        }
        if (Math.abs(red - green) < 22 && Math.abs(green - blue) < 22 && red < 175) {
            return Terrain.ROCK;
        }
        return Terrain.GRASS;
    }

    private static void draw(GuiGraphics graphics, SurfaceTexture texture, Raster raster,
                             int x, int y, int width, int height,
                             int minX, int maxX, int minZ, int maxZ, boolean decorate) {
        float opacity = decorate ? 1.0f : VillageQuestClientConfig.get().minimapOpacity();
        graphics.fill(x, y, x + width, y + height, withOpacity(0xFFD8B978, opacity));
        if (raster == null) {
            return;
        }
        int textureWidth = raster.width * SurfaceMapProjection.PIXELS_PER_CELL;
        int textureHeight = raster.height * SurfaceMapProjection.PIXELS_PER_CELL;
        texture.upload(raster, textureWidth, textureHeight, decorate);
        Raster displayed = texture.displayedRaster;
        if (displayed == null) return;
        // While an upload is throttled, project the still-visible GPU data at its old origin.
        raster = displayed;
        double u0 = SurfaceMapProjection.textureCoordinate(minX, raster.minX, raster.cellSize);
        double v0 = SurfaceMapProjection.textureCoordinate(minZ, raster.minZ, raster.cellSize);
        double u1 = SurfaceMapProjection.textureCoordinate(maxX, raster.minX, raster.cellSize);
        double v1 = SurfaceMapProjection.textureCoordinate(maxZ, raster.minZ, raster.cellSize);
        drawTextureRegion(graphics, texture.id, x, y, width, height,
                u0, v0, u1, v1, texture.width, texture.height);
    }

    private static void drawTextureRegion(GuiGraphics graphics, Identifier texture,
                                          int x, int y, int width, int height,
                                          double u0, double v0, double u1, double v1,
                                          int textureWidth, int textureHeight) {
        double fullSourceWidth = Math.max(0.0001D, u1 - u0);
        double fullSourceHeight = Math.max(0.0001D, v1 - v0);
        double clippedU0 = Math.max(0.0D, u0);
        double clippedV0 = Math.max(0.0D, v0);
        double clippedU1 = Math.min(textureWidth, u1);
        double clippedV1 = Math.min(textureHeight, v1);
        if (clippedU1 <= clippedU0 || clippedV1 <= clippedV0) {
            return;
        }
        int drawX0 = x + (int) Math.round((clippedU0 - u0) / fullSourceWidth * width);
        int drawY0 = y + (int) Math.round((clippedV0 - v0) / fullSourceHeight * height);
        int drawX1 = x + (int) Math.round((clippedU1 - u0) / fullSourceWidth * width);
        int drawY1 = y + (int) Math.round((clippedV1 - v0) / fullSourceHeight * height);
        int sourceWidth = Math.max(1, (int) Math.round(clippedU1 - clippedU0));
        int sourceHeight = Math.max(1, (int) Math.round(clippedV1 - clippedV0));
        int drawWidth = Math.max(1, drawX1 - drawX0);
        int drawHeight = Math.max(1, drawY1 - drawY0);
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(drawX0, drawY0);
        matrices.scale(drawWidth / (float) sourceWidth, drawHeight / (float) sourceHeight);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0,
                (float) clippedU0, (float) clippedV0,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
        matrices.popMatrix();
    }

    private static int withOpacity(int color, float opacity) {
        return Math.clamp(Math.round((color >>> 24) * opacity), 0, 255) << 24 | color & 0xFFFFFF;
    }

    private record PaintKey(int x, int z, int cellSize, int gridSize, boolean detail, int alpha) {}
    private record PaintedTile(long fingerprint, int[] pixels) {}

    private static PaintedTile paintTile(Raster raster, PaintKey key) {
        return paintTile(raster, key, false);
    }

    private static PaintedTile paintTile(Raster raster, PaintKey key, boolean cachedOnly) {
        int cellX = key.x * PAINT_TILE_CELLS;
        int cellZ = key.z * PAINT_TILE_CELLS;
        // Include the halo: neighbouring height/coast changes invalidate this tile too.
        long fingerprint = 0xcbf29ce484222325L;
        for (int z = -12; z < PAINT_TILE_CELLS + 12; z++) {
            for (int x = -12; x < PAINT_TILE_CELLS + 12; x++) {
                Sample sample = raster.atWorldCell(cellX + x, cellZ + z);
                fingerprint = (fingerprint ^ sample.hashCode()) * 0x100000001b3L;
            }
        }
        PaintedTile cached = PAINT_TILES.get(key);
        if (cached != null && cached.fingerprint == fingerprint) return cached;
        if (cachedOnly) return null;
        int[] pixels = new int[PAINT_TILE_PIXELS * PAINT_TILE_PIXELS];
        float opacity = key.alpha / 255f;
        for (int z = 0; z < PAINT_TILE_CELLS; z++) {
            for (int x = 0; x < PAINT_TILE_CELLS; x++) {
                int wx = cellX + x, wz = cellZ + z;
                FantasyMapStyle.paintInto(pixels, z * 8 * PAINT_TILE_PIXELS + x * 8, PAINT_TILE_PIXELS,
                        raster.atWorldCell(wx, wz).cell(), raster.atWorldCell(wx - 1, wz).cell(),
                        raster.atWorldCell(wx, wz - 1).cell(), raster.atWorldCell(wx + 1, wz).cell(),
                        raster.atWorldCell(wx, wz + 1).cell(), key.cellSize, key.detail, opacity);
            }
        }
        FantasyMapAtlas.shoreline(pixels, PAINT_TILE_PIXELS, PAINT_TILE_PIXELS,
                cellX, cellZ, (x, z) -> raster.atWorldCell(x, z).cell());
        if (key.detail) FantasyMapAtlas.overlay(pixels, PAINT_TILE_PIXELS, PAINT_TILE_PIXELS,
                cellX, cellZ, key.cellSize, (x, z) -> raster.atWorldCell(x, z).cell());
        PaintedTile painted = new PaintedTile(fingerprint, pixels);
        PAINT_TILES.put(key, painted);
        return painted;
    }
    private static int scaledSamples(int base) {
        return Math.max(24, Math.round(base * VillageQuestClientConfig.get().mapQuality().sampleScale()));
    }

    private static int sampleGridSize() {
        return switch (VillageQuestClientConfig.get().mapQuality()) {
            case LOW -> 8;
            case BALANCED -> 4;
            case HIGH -> 2;
        };
    }

    private static int sampleCellSize(int minX, int maxX, int minZ, int maxZ,
                                      int targetWidth, int targetHeight) {
        long worldWidth = Math.max(1L, (long) maxX - minX);
        long worldHeight = Math.max(1L, (long) maxZ - minZ);
        long cellSize = sampleGridSize();
        while ((worldWidth > Math.max(1, targetWidth) * cellSize
                || worldHeight > Math.max(1, targetHeight) * cellSize)
                && cellSize <= 1_048_576L) {
            cellSize *= 2L;
        }
        return (int) cellSize;
    }

    private static int fixedSampleCoordinate(int cellIndex, int cellSize) {
        long cellStart = (long) cellIndex * cellSize;
        long center = cellStart + (cellSize - 1L) / 2L;
        return alignedSampleCoordinate((int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, center)));
    }

    private static int projectedCoordinate(long worldCoordinate, int minimum,
                                           double worldSpan, int pixels) {
        return (int) Math.round((worldCoordinate - minimum) / worldSpan * pixels);
    }

    private static int alignedSampleCoordinate(int coordinate) {
        int grid = sampleGridSize();
        return Math.floorDiv(coordinate, grid) * grid + grid / 2;
    }

    private static Sample unknown(int worldX, int worldZ) {
        return new Sample(Terrain.UNKNOWN, 63, 0, worldX, worldZ);
    }

    private static void ensureWorld(Minecraft client, ClientLevel level) {
        boolean persistent = VillageQuestClientConfig.get().persistentMapCache();
        if (memoryLevel == level && memoryGrid == sampleGridSize() && memoryPersistent == persistent) {
            return;
        }
        scheduleCacheWrites();
        memoryLevel = level;
        memoryGrid = sampleGridSize();
        memoryPersistent = persistent;
        screenRaster = null;
        hudRaster = null;
        PAINT_TILES.clear();
        TILE_READS.clear();
        SCREEN_TEXTURE.displayedRaster = null;
        HUD_TEXTURE.displayedRaster = null;
        SURFACE_MEMORY.clear();
        LOADED_TILES.clear();
        DIRTY_TILES.clear();
        lastCacheFlushTick = System.nanoTime() / 50_000_000L;
        cacheDirectory = VillageQuestClientConfig.get().persistentMapCache()
                ? cacheDirectory(client, level)
                : null;
        if (cacheDirectory != null) {
            try {
                Files.createDirectories(cacheDirectory);
            } catch (IOException exception) {
                VillageQuest.LOGGER.warn("Could not create Village Quest map cache {}", cacheDirectory, exception);
                cacheDirectory = null;
            }
        }
        Path root = cacheRoot();
        int retention = VillageQuestClientConfig.get().mapCacheRetentionDays();
        long maximumBytes = VillageQuestClientConfig.get().mapCacheMaxSizeMb() * 1024L * 1024L;
        cacheWrites = cacheWrites.handle((ignored, failure) -> null)
                .thenRunAsync(() -> pruneCache(root, retention, maximumBytes));
    }

    private static void loadTile(int worldX, int worldZ) {
        if (cacheDirectory == null) {
            return;
        }
        long tileKey = tileKey(worldX, worldZ);
        if (LOADED_TILES.containsKey(tileKey) || TILE_READS.containsKey(tileKey)
                || TILE_READS.size() >= 8 || !WORK.diskRead()) {
            return;
        }
        LOADED_TILES.put(tileKey, Boolean.TRUE);
        Path file = tilePath(cacheDirectory, tileX(worldX), tileZ(worldZ));
        TILE_READS.put(tileKey, CompletableFuture.supplyAsync(() -> readTile(file)));
    }

    private static void applyTileReads() {
        var iterator = TILE_READS.entrySet().iterator();
        int applied = 0;
        while (iterator.hasNext() && applied < 2) {
            var entry = iterator.next();
            if (!entry.getValue().isDone()) continue;
            List<Sample> samples = entry.getValue().getNow(List.of());
            for (Sample sample : samples) SURFACE_MEMORY.putIfAbsent(surfaceKey(sample.worldX, sample.worldZ), sample);
            // The first sweep may have finished before this asynchronous disk read.
            if (!samples.isEmpty()) {
                if (screenRaster != null) screenRaster.cursor = 0;
                if (hudRaster != null) hudRaster.cursor = 0;
            }
            iterator.remove();
            applied++;
        }
    }

    private static List<Sample> readTile(Path file) {
        List<Sample> samples = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return samples;
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_VERSION) {
                return samples;
            }
            int count = Math.max(0, Math.min(TILE_CELLS * TILE_CELLS, input.readInt()));
            for (int index = 0; index < count; index++) {
                int x = input.readInt();
                int z = input.readInt();
                int terrainId = input.readUnsignedByte();
                int height = input.readInt();
                int sourceColor = input.readInt();
                Terrain terrain = terrainId >= 0 && terrainId < Terrain.values().length
                        ? Terrain.values()[terrainId]
                        : Terrain.UNKNOWN;
                samples.add(new Sample(terrain, height, sourceColor, x, z));
            }
        } catch (EOFException exception) {
            VillageQuest.LOGGER.warn("Ignoring truncated Village Quest map tile {}", file);
        } catch (IOException exception) {
            VillageQuest.LOGGER.warn("Could not read Village Quest map tile {}", file, exception);
        }
        return samples;
    }

    private static void scheduleCacheWrites() {
        if (cacheDirectory == null || DIRTY_TILES.isEmpty()
                || !VillageQuestClientConfig.get().persistentMapCache()) {
            return;
        }
        Path directory = cacheDirectory;
        Set<Long> dirty = Set.copyOf(DIRTY_TILES);
        DIRTY_TILES.removeAll(dirty);
        Map<Long, Map<Long, Sample>> snapshots = new HashMap<>();
        for (long tile : dirty) {
            snapshots.put(tile, new HashMap<>());
        }
        for (Map.Entry<Long, Sample> entry : SURFACE_MEMORY.entrySet()) {
            Sample sample = entry.getValue();
            long tile = tileKey(sample.worldX, sample.worldZ);
            Map<Long, Sample> snapshot = snapshots.get(tile);
            if (snapshot != null && sample.terrain != Terrain.UNKNOWN) {
                snapshot.put(entry.getKey(), sample);
            }
        }
        cacheWrites = cacheWrites.handle((ignored, failure) -> null).thenRunAsync(() -> {
            for (Map.Entry<Long, Map<Long, Sample>> entry : snapshots.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                mergeAndWriteTile(tilePath(directory, x, z), entry.getValue());
            }
        });
    }

    private static void mergeAndWriteTile(Path file, Map<Long, Sample> updates) {
        Map<Long, Sample> merged = new HashMap<>();
        if (Files.isRegularFile(file)) {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if (input.readInt() == CACHE_MAGIC && input.readInt() == CACHE_VERSION) {
                    int count = Math.max(0, Math.min(TILE_CELLS * TILE_CELLS, input.readInt()));
                    for (int index = 0; index < count; index++) {
                        int x = input.readInt();
                        int z = input.readInt();
                        int terrainId = input.readUnsignedByte();
                        int height = input.readInt();
                        int sourceColor = input.readInt();
                        Terrain terrain = terrainId >= 0 && terrainId < Terrain.values().length
                                ? Terrain.values()[terrainId]
                                : Terrain.UNKNOWN;
                        merged.put(surfaceKey(x, z), new Sample(terrain, height, sourceColor, x, z));
                    }
                }
            } catch (IOException ignored) {
                merged.clear();
            }
        }
        merged.putAll(updates);
        if (merged.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(CACHE_MAGIC);
                output.writeInt(CACHE_VERSION);
                output.writeInt(merged.size());
                for (Sample sample : merged.values()) {
                    output.writeInt(sample.worldX);
                    output.writeInt(sample.worldZ);
                    output.writeByte(sample.terrain.ordinal());
                    output.writeInt(sample.height);
                    output.writeInt(sample.sourceColor);
                }
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            VillageQuest.LOGGER.warn("Could not write Village Quest map tile {}", file, exception);
        }
    }

    private static Path cacheDirectory(Minecraft client, ClientLevel level) {
        String source;
        if (client.getCurrentServer() != null) {
            source = "server-" + client.getCurrentServer().ip;
        } else if (client.getSingleplayerServer() != null) {
            source = "world-" + client.getSingleplayerServer().getWorldData().getLevelName()
                    + "-" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize();
        } else {
            source = "local-world";
        }
        String dimension = level.dimension().identifier().toString();
        String folder = safeName(source) + "-" + digest(source).substring(0, 12);
        return cacheRoot().resolve(folder).resolve(safeName(dimension))
                .resolve("grid-" + sampleGridSize());
    }

    private static Path cacheRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("village-quest").resolve("map-cache");
    }

    private static void pruneCache(Path root, int retentionDays, long maximumBytes) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try {
            List<Path> files;
            try (var stream = Files.walk(root)) {
                files = stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".vqm"))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, retentionDays)));
            for (Path file : List.copyOf(files)) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                    files.remove(file);
                }
            }
            files.sort(Comparator.comparingLong(SurfaceMapRenderer::lastModified));
            long total = 0L;
            for (Path file : files) {
                total += size(file);
            }
            for (Path file : files) {
                if (total <= maximumBytes) break;
                long size = size(file);
                Files.deleteIfExists(file);
                total -= size;
            }
        } catch (IOException exception) {
            VillageQuest.LOGGER.warn("Could not prune Village Quest map cache {}", root, exception);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static int tileX(int worldX) {
        return Math.floorDiv(worldX, (memoryGrid > 0 ? memoryGrid : sampleGridSize()) * TILE_CELLS);
    }

    private static int tileZ(int worldZ) {
        return Math.floorDiv(worldZ, (memoryGrid > 0 ? memoryGrid : sampleGridSize()) * TILE_CELLS);
    }

    private static long tileKey(int worldX, int worldZ) {
        return ((long) tileX(worldX) << 32) ^ (tileZ(worldZ) & 0xFFFF_FFFFL);
    }

    private static long surfaceKey(int worldX, int worldZ) {
        return ((long) worldX << 32) ^ (worldZ & 0xFFFF_FFFFL);
    }

    private static Path tilePath(Path directory, int tileX, int tileZ) {
        return directory.resolve("tile_" + tileX + "_" + tileZ + ".vqm");
    }

    private static String safeName(String raw) {
        String safe = raw == null ? "unknown" : raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "unknown" : safe.substring(0, Math.min(48, safe.length()));
    }

    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Sample(Terrain terrain, int height, int sourceColor, int worldX, int worldZ) {
        Cell cell() { return new Cell(terrain, height, sourceColor, worldX, worldZ); }
    }

    private static final class SurfaceTexture {
        private final Identifier id;
        private DynamicTexture texture;
        private Raster uploadedRaster;
        private Raster displayedRaster;
        private int width, height;
        private boolean decorated;
        private int alpha = -1;
        private boolean pendingUpload;
        private boolean[] knownGround;

        private SurfaceTexture(Identifier id) { this.id = id; }

        private void upload(Raster raster, int requestedWidth, int requestedHeight, boolean detail) {
            if (requestedWidth > 1024 || requestedHeight > 768) return;
            int requestedAlpha = detail ? 255 : Math.round(255 * VillageQuestClientConfig.get().minimapOpacity());
            Raster previous = uploadedRaster;
            boolean sameContext = previous != null && previous.level == raster.level
                    && previous.gridSize == raster.gridSize && decorated == detail && alpha == requestedAlpha;
            boolean resize = texture == null || width != requestedWidth || height != requestedHeight;
            boolean reservedUpload = false;
            // Keep the already-published GPU image until its resized replacement can
            // be uploaded in the same call. A different world/quality is never a fallback.
            if (resize && sameContext && displayedRaster != null) {
                if (!WORK.upload()) return;
                reservedUpload = true;
            }
            if (!sameContext) displayedRaster = null;
            int previousWidth = width, previousHeight = height;
            boolean[] previousGround = knownGround;
            int[] retained = null;
            // A viewport is not a new world. Preserve the CPU image, including partially
            // completed tiles, before clearing/reallocating its backing texture.
            if (sameContext && previous != raster
                    && texture != null && texture.getPixels() != null) {
                retained = new int[width * height];
                NativeImage old = texture.getPixels();
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                    retained[y * width + x] = old.getPixel(x, y);
            }
            if (texture == null || width != requestedWidth || height != requestedHeight) {
                release();
                width = requestedWidth;
                height = requestedHeight;
                texture = new DynamicTexture(() -> id.toString(), width, height, false);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }
            NativeImage pixels = texture.getPixels();
            if (pixels == null) return;
            if (uploadedRaster != raster || decorated != detail || alpha != requestedAlpha) {
                pixels.fillRect(0, 0, width, height, withOpacity(0xFFD8B978, requestedAlpha / 255f));
                knownGround=new boolean[raster.samples.length];
                // Publish known ground everywhere, not parchment holes in tiles waiting for expensive motifs.
                // This uses only samples already observed; unknown territory remains parchment.
                for(int row=0;row<raster.height;row++) for(int col=0;col<raster.width;col++) {
                    var sample=raster.samples[row*raster.width+col];
                    if(sample.terrain!=Terrain.UNKNOWN) {
                        pixels.fillRect(col*8,row*8,8,8,FantasyMapStyle.groundPreview(sample.cell(),requestedAlpha/255f));
                        knownGround[row*raster.width+col]=true;
                    }
                }
                if (retained != null) {
                    for (int y = 0; y < height; y++) {
                        long sy = Math.floorDiv(((long) raster.minZ - previous.minZ) * 8
                                + (long) y * raster.cellSize, previous.cellSize);
                        if (sy < 0 || sy >= previousHeight) continue;
                        for (int x = 0; x < width; x++) {
                            long sx = Math.floorDiv(((long) raster.minX - previous.minX) * 8
                                    + (long) x * raster.cellSize, previous.cellSize);
                            if (sx >= 0 && sx < previousWidth
                                    && raster.samples[(y/8)*raster.width+x/8].terrain!=Terrain.UNKNOWN
                                    && previousGround!=null && previousGround[((int)sy/8)*previous.width+(int)sx/8]
                                    && previous.samples[((int)sy/8)*previous.width+(int)sx/8].terrain!=Terrain.UNKNOWN)
                                pixels.setPixel(x, y, retained[(int) sy * previousWidth + (int) sx]);
                        }
                    }
                }
                raster.dirtyAll();
                uploadedRaster = raster;
                decorated = detail;
                alpha = requestedAlpha;
                pendingUpload = true;
            }
            // Sampling mutates a Raster in place. Publish newly known ground even when detail paint is throttled.
            for(int i=0;i<raster.samples.length;i++) {
                var sample=raster.samples[i];boolean known=sample.terrain!=Terrain.UNKNOWN;
                if(knownGround[i]!=known) {
                    pixels.fillRect((i%raster.width)*8,(i/raster.width)*8,8,8,known?
                            FantasyMapStyle.groundPreview(sample.cell(),requestedAlpha/255f):withOpacity(0xFFD8B978,requestedAlpha/255f));
                    knownGround[i]=known;pendingUpload=true;
                }
            }
            var iterator = raster.dirtyPaintTiles.iterator();
            while (iterator.hasNext()) {
                long tile = iterator.next();
                int tx = (int) (tile >> 32), tz = (int) tile;
                PaintKey key = new PaintKey(tx, tz, raster.cellSize, raster.gridSize, detail, alpha);
                PaintedTile painted = paintTile(raster, key, true);
                // Cache copies are bounded by the viewport. Only expensive new painting
                // spends the shared work budget; cached terrain must not blink away.
                if (painted == null) {
                    if (!WORK.tile()) continue;
                    painted = paintTile(raster, key);
                }
                int dx = (tx * PAINT_TILE_CELLS - raster.originCellX) * 8;
                int dy = (tz * PAINT_TILE_CELLS - raster.originCellZ) * 8;
                for (int y = Math.max(0, -dy); y < Math.min(PAINT_TILE_PIXELS, height - dy); y++) {
                    for (int x = Math.max(0, -dx); x < Math.min(PAINT_TILE_PIXELS, width - dx); x++) {
                        pixels.setPixel(dx + x, dy + y, painted.pixels[y * PAINT_TILE_PIXELS + x]);
                    }
                }
                iterator.remove();
                pendingUpload = true;
            }
            if (pendingUpload && (reservedUpload || WORK.upload())) {
                texture.upload();
                displayedRaster = raster;
                pendingUpload = false;
            }
        }

        private void release() {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
            uploadedRaster = null;
            displayedRaster = null;
            width = height = 0;
            pendingUpload = false;
            knownGround = null;
        }
    }

    private static final class Raster {
        final ClientLevel level;
        final int minX, maxX, minZ, maxZ, originCellX, originCellZ, cellSize, gridSize, width, height;
        final Sample[] samples;
        final int[] order;
        final Set<Long> dirtyPaintTiles = new LinkedHashSet<>();
        long sampledAt;
        int cursor;

        Raster(ClientLevel level, int minX, int maxX, int minZ, int maxZ,
               int originCellX, int originCellZ, int cellSize, int gridSize,
               int width, int height, Sample[] samples, long sampledAt) {
            this.level = level;
            this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
            this.originCellX = originCellX; this.originCellZ = originCellZ;
            this.cellSize = cellSize; this.gridSize = gridSize;
            this.width = width; this.height = height; this.samples = samples; this.sampledAt = sampledAt;
            // Visible centre first, then the buffered margin. Reused for each refresh sweep.
            Integer[] indices = new Integer[samples.length];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            Arrays.sort(indices, Comparator.comparingInt(i -> Math.max(
                    Math.abs((i % width) * 2 - width), Math.abs((i / width) * 2 - height))));
            order = new int[indices.length];
            for (int i = 0; i < indices.length; i++) order[i] = indices[i];
            dirtyAll();
        }

        Sample atWorldCell(int x, int z) {
            int col = x - originCellX, row = z - originCellZ;
            if (col >= 0 && col < width && row >= 0 && row < height) return samples[row * width + col];
            int wx = fixedSampleCoordinate(x, cellSize), wz = fixedSampleCoordinate(z, cellSize);
            return SURFACE_MEMORY.getOrDefault(surfaceKey(wx, wz), unknown(wx, wz));
        }

        void dirtyCell(int x, int z) {
            int wx = originCellX + x, wz = originCellZ + z;
            markTile(wx, wz); markTile(wx - 1, wz); markTile(wx + 1, wz);
            markTile(wx, wz - 1); markTile(wx, wz + 1);
            // Include motif reach and the local density neighbourhood.
            for (int dz = -12; dz <= 12; dz += 12) for (int dx = -12; dx <= 12; dx += 12)
                markTile(wx + dx, wz + dz);
        }

        void markTile(int x, int z) {
            int tx = Math.floorDiv(x, PAINT_TILE_CELLS), tz = Math.floorDiv(z, PAINT_TILE_CELLS);
            if (tx * PAINT_TILE_CELLS >= originCellX + width || (tx + 1) * PAINT_TILE_CELLS <= originCellX
                    || tz * PAINT_TILE_CELLS >= originCellZ + height || (tz + 1) * PAINT_TILE_CELLS <= originCellZ) return;
            dirtyPaintTiles.add((long) tx << 32 ^ tz & 0xFFFF_FFFFL);
        }

        void dirtyAll() {
            for (int z = 0; z < height; z += PAINT_TILE_CELLS) {
                for (int x = 0; x < width; x += PAINT_TILE_CELLS) markTile(originCellX + x, originCellZ + z);
                markTile(originCellX + width - 1, originCellZ + z);
            }
            for (int x = 0; x < width; x += PAINT_TILE_CELLS) markTile(originCellX + x, originCellZ + height - 1);
            markTile(originCellX + width - 1, originCellZ + height - 1);
            var ordered = new ArrayList<>(dirtyPaintTiles);
            ordered.sort(Comparator.comparingLong(tile -> {
                long tx = tile >> 32, tz = (int) (long) tile;
                return Math.max(Math.abs((tx * 16 + 8 - originCellX) * 2 - width),
                        Math.abs((tz * 16 + 8 - originCellZ) * 2 - height));
            }));
            dirtyPaintTiles.clear();
            dirtyPaintTiles.addAll(ordered);
        }

        boolean covers(ClientLevel otherLevel, int otherMinX, int otherMaxX,
                       int otherMinZ, int otherMaxZ, int otherCellSize, int otherGridSize) {
            return level == otherLevel && otherMinX >= minX && otherMaxX <= maxX
                    && otherMinZ >= minZ && otherMaxZ <= maxZ && cellSize == otherCellSize && gridSize == otherGridSize;
        }
    }
}
