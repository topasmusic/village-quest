package de.quest.client.ui;

import net.minecraft.client.texture.NativeImage;
import de.quest.VillageQuest;
import de.quest.client.config.VillageQuestClientConfig;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.Heightmap;
import net.minecraft.block.MapColor;
import net.minecraft.util.WorldSavePath;

/**
 * Turns already-loaded MinecraftClient terrain into a compact illustrated guild map.
 *
 * <p>The renderer never loads chunks. It remembers samples the player has seen,
 * groups them into a restrained fantasy-map palette, adds height shading, shoreline
 * ink, and small deterministic terrain glyphs. This keeps the result readable like
 * the concept art without pretending that an unrelated painted background is the
 * player's world.</p>
 */
public final class SurfaceMapRenderer {
    private enum Terrain {
        UNKNOWN,
        WATER,
        GRASS,
        FOREST,
        SAND,
        BADLANDS,
        ROCK,
        MOUNTAIN,
        SNOW
    }

    private static final int MAX_REMEMBERED_SAMPLES = 120_000;
    private static final int CACHE_MAGIC = 0x56514D50; // VQMP
    private static final int CACHE_VERSION = 1;
    private static final int TILE_CELLS = 32;
    private static final int MAX_LOADED_TILE_MARKERS = 512;
    private static final long CACHE_FLUSH_TICKS = 20L * 15L;
    private static final SurfaceTexture SCREEN_TEXTURE = new SurfaceTexture(
            Identifier.of(VillageQuest.MOD_ID, "surface_map_screen"));
    private static final SurfaceTexture HUD_TEXTURE = new SurfaceTexture(
            Identifier.of(VillageQuest.MOD_ID, "surface_map_hud"));
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
    private static Raster hudRaster;
    private static ClientWorld memoryLevel;
    private static Path cacheDirectory;
    private static long lastCacheFlushTick;
    private static CompletableFuture<Void> cacheWrites = CompletableFuture.completedFuture(null);

    private SurfaceMapRenderer() {}

    public static void drawScreen(DrawContext graphics, int x, int y, int width, int height,
                                  int minX, int maxX, int minZ, int maxZ) {
        drawScreen(graphics, x, y, width, height, minX, maxX, minZ, maxZ, true);
    }

    public static void drawScreen(DrawContext graphics, int x, int y, int width, int height,
                                  int minX, int maxX, int minZ, int maxZ,
                                  boolean refreshTerrain) {
        screenRaster = raster(MinecraftClient.getInstance(), screenRaster, minX, maxX, minZ, maxZ,
                scaledSamples(Math.max(72, width / 2)), scaledSamples(Math.max(42, height / 2)),
                50L, refreshTerrain);
        draw(graphics, SCREEN_TEXTURE, screenRaster, x, y, width, height,
                minX, maxX, minZ, maxZ, true);
    }

    public static void drawHud(DrawContext graphics, int x, int y, int width, int height,
                               int minX, int maxX, int minZ, int maxZ) {
        hudRaster = raster(MinecraftClient.getInstance(), hudRaster, minX, maxX, minZ, maxZ,
                scaledSamples(Math.max(42, width / 2)), scaledSamples(Math.max(24, height / 2)),
                25L, true);
        draw(graphics, HUD_TEXTURE, hudRaster, x, y, width, height,
                minX, maxX, minZ, maxZ, false);
    }

    public static void invalidateScreen() {
        screenRaster = null;
    }

    public static void clear() {
        scheduleCacheWrites();
        screenRaster = null;
        hudRaster = null;
        memoryLevel = null;
        cacheDirectory = null;
        SURFACE_MEMORY.clear();
        LOADED_TILES.clear();
        DIRTY_TILES.clear();
        SCREEN_TEXTURE.release();
        HUD_TEXTURE.release();
    }

    private static Raster raster(MinecraftClient client, Raster existing,
                                 int minX, int maxX, int minZ, int maxZ,
                                 int sampleWidth, int sampleHeight, long maxAge,
                                 boolean refreshTerrain) {
        ClientWorld level = client.world;
        if (level == null) {
            return null;
        }
        ensureWorld(client, level);
        if (!refreshTerrain && existing != null && existing.level == level) {
            return existing;
        }
        long now = level.getTime();
        if (now - lastCacheFlushTick >= CACHE_FLUSH_TICKS) {
            scheduleCacheWrites();
            lastCacheFlushTick = now;
        }
        int cellSize = sampleCellSize(minX, maxX, minZ, maxZ, sampleWidth, sampleHeight);
        int gridSize = sampleGridSize();
        if (existing != null && existing.matches(level, minX, maxX, minZ, maxZ, cellSize, gridSize)
                && now - existing.sampledAt < maxAge) {
            return existing;
        }
        int originCellX = Math.floorDiv(minX, cellSize) - 1;
        int originCellZ = Math.floorDiv(minZ, cellSize) - 1;
        int endCellX = Math.floorDiv(maxX - 1, cellSize) + 1;
        int endCellZ = Math.floorDiv(maxZ - 1, cellSize) + 1;
        int rasterWidth = endCellX - originCellX + 1;
        int rasterHeight = endCellZ - originCellZ + 1;
        Sample[] samples = new Sample[rasterWidth * rasterHeight];
        for (int row = 0; row < rasterHeight; row++) {
            int worldZ = fixedSampleCoordinate(originCellZ + row, cellSize);
            for (int column = 0; column < rasterWidth; column++) {
                int worldX = fixedSampleCoordinate(originCellX + column, cellSize);
                samples[row * rasterWidth + column] = sample(level, worldX, worldZ);
            }
        }
        return new Raster(level, minX, maxX, minZ, maxZ, originCellX, originCellZ,
                cellSize, gridSize, rasterWidth, rasterHeight, samples, now);
    }

    private static Sample sample(ClientWorld level, int worldX, int worldZ) {
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

    private static Sample sampleLoaded(ClientWorld level, int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        if (!level.isChunkLoaded(chunkX, chunkZ)) {
            return unknown(worldX, worldZ);
        }
        Chunk chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return unknown(worldX, worldZ);
        }
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        int topY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, localX, localZ) - 1;
        if (topY < level.getBottomY()) {
            return unknown(worldX, worldZ);
        }
        BlockPos.Mutable pos = new BlockPos.Mutable(worldX, topY, worldZ);
        BlockState state = chunk.getBlockState(pos);
        MapColor color = MapColor.CLEAR;
        for (int depth = 0; depth < 10 && pos.getY() >= level.getBottomY(); depth++, pos.move(0, -1, 0)) {
            state = chunk.getBlockState(pos);
            color = state.getMapColor(level, pos);
            if (color != MapColor.CLEAR) {
                break;
            }
        }
        if (color == MapColor.CLEAR) {
            return unknown(worldX, worldZ);
        }
        int rgb = color.getRenderColor(MapColor.Brightness.NORMAL);
        return new Sample(classify(state, rgb, topY), topY, rgb, worldX, worldZ);
    }

    private static Terrain classify(BlockState state, int rgb, int height) {
        if (!state.getFluidState().isEmpty() || state.isOf(Blocks.WATER) || state.isOf(Blocks.ICE)) {
            return Terrain.WATER;
        }
        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)
                || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)) {
            return Terrain.SNOW;
        }
        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.SANDSTONE)) {
            return Terrain.SAND;
        }
        if (state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.RED_SANDSTONE)
                || state.isOf(Blocks.TERRACOTTA)) {
            return Terrain.BADLANDS;
        }
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        if (height >= 128 && red + green + blue > 540) {
            return Terrain.SNOW;
        }
        if (height >= 112) {
            return Terrain.MOUNTAIN;
        }
        if (state.isOf(Blocks.OAK_LEAVES) || state.isOf(Blocks.SPRUCE_LEAVES)
                || state.isOf(Blocks.BIRCH_LEAVES) || state.isOf(Blocks.JUNGLE_LEAVES)
                || state.isOf(Blocks.ACACIA_LEAVES) || state.isOf(Blocks.DARK_OAK_LEAVES)
                || state.isOf(Blocks.MANGROVE_LEAVES) || state.isOf(Blocks.CHERRY_LEAVES)
                || state.isOf(Blocks.AZALEA_LEAVES) || state.isOf(Blocks.FLOWERING_AZALEA_LEAVES)) {
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

    private static void draw(DrawContext graphics, SurfaceTexture texture, Raster raster,
                             int x, int y, int width, int height,
                             int minX, int maxX, int minZ, int maxZ, boolean decorate) {
        float opacity = decorate ? 1.0f : VillageQuestClientConfig.get().minimapOpacity();
        graphics.fill(x, y, x + width, y + height, withOpacity(0xFFD8B978, opacity));
        if (raster == null) {
            return;
        }
        texture.upload(raster, width, height, decorate);
        double requestedWidth = Math.max(1.0, maxX - minX);
        double requestedHeight = Math.max(1.0, maxZ - minZ);
        int drawX = x + (int) Math.round((raster.minX - minX) / requestedWidth * width);
        int drawY = y + (int) Math.round((raster.minZ - minZ) / requestedHeight * height);
        graphics.drawTexture(RenderPipelines.GUI_TEXTURED, texture.id, drawX, drawY, 0.0f, 0.0f,
                width, height, width, height);
    }

    private static void paint(NativeImage image, Raster raster, int width, int height,
                              boolean decorate) {
        float opacity = decorate ? 1.0f : VillageQuestClientConfig.get().minimapOpacity();
        image.fillRect(0, 0, width, height, withOpacity(0xFFD8B978, opacity));
        double worldWidth = Math.max(1.0, raster.maxX - raster.minX);
        double worldHeight = Math.max(1.0, raster.maxZ - raster.minZ);
        for (int row = 0; row < raster.height; row++) {
            int cellZ = raster.originCellZ + row;
            int y0 = projectedCoordinate((long) cellZ * raster.cellSize,
                    raster.minZ, worldHeight, height);
            int y1 = projectedCoordinate((long) (cellZ + 1) * raster.cellSize,
                    raster.minZ, worldHeight, height);
            int clippedY0 = Math.max(0, y0);
            int clippedY1 = Math.min(height, y1);
            if (clippedY1 <= clippedY0) {
                continue;
            }
            for (int column = 0; column < raster.width; column++) {
                int cellX = raster.originCellX + column;
                int x0 = projectedCoordinate((long) cellX * raster.cellSize,
                        raster.minX, worldWidth, width);
                int x1 = projectedCoordinate((long) (cellX + 1) * raster.cellSize,
                        raster.minX, worldWidth, width);
                int clippedX0 = Math.max(0, x0);
                int clippedX1 = Math.min(width, x1);
                if (clippedX1 <= clippedX0) {
                    continue;
                }
                Sample sample = raster.at(column, row);
                int shade = slopeShade(raster, column, row, sample.height);
                int color = palette(sample, shade, hash(sample.worldX, sample.worldZ));
                image.fillRect(clippedX0, clippedY0, clippedX1 - clippedX0, clippedY1 - clippedY0,
                        withOpacity(color, opacity));
                if (sample.terrain == Terrain.WATER && touchesLand(raster, column, row)) {
                    image.fillRect(clippedX0, clippedY0, clippedX1 - clippedX0, 1,
                            withOpacity(0xFFB7C59A, opacity));
                }
            }
        }
        if (decorate) {
            drawTerrainGlyphs(image, raster, width, height);
        }
    }

    private static void drawTerrainGlyphs(NativeImage image, Raster raster,
                                          int width, int height) {
        double worldWidth = Math.max(1.0, raster.maxX - raster.minX);
        double worldHeight = Math.max(1.0, raster.maxZ - raster.minZ);
        for (int row = 1; row < raster.height - 1; row++) {
            for (int column = 1; column < raster.width - 1; column++) {
                if (Math.floorMod(raster.originCellX + column, 2) != 0
                        || Math.floorMod(raster.originCellZ + row, 2) != 0) {
                    continue;
                }
                Sample sample = raster.at(column, row);
                int hash = hash(sample.worldX * 7, sample.worldZ * 11);
                int px = projectedCoordinate(sample.worldX, raster.minX, worldWidth, width);
                int py = projectedCoordinate(sample.worldZ, raster.minZ, worldHeight, height);
                if (sample.terrain == Terrain.FOREST && Math.floorMod(hash, 9) == 0) {
                    fillSafe(image, px, py - 1, 1, 3, 0xFF31532D);
                    fillSafe(image, px - 1, py, 3, 1, 0xFF4D7D3C);
                } else if ((sample.terrain == Terrain.MOUNTAIN || sample.terrain == Terrain.ROCK)
                        && Math.floorMod(hash, 17) == 0) {
                    fillSafe(image, px, py - 1, 1, 2, 0xFF6B6657);
                    fillSafe(image, px - 1, py + 1, 3, 1, 0xFF746D5C);
                } else if (sample.terrain == Terrain.WATER && Math.floorMod(hash, 19) == 0) {
                    fillSafe(image, px - 1, py, 3, 1, 0xFF72B7BB);
                }
            }
        }
    }

    private static void fillSafe(NativeImage image, int x, int y, int width, int height, int color) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(image.getWidth(), x + width);
        int y1 = Math.min(image.getHeight(), y + height);
        if (x1 > x0 && y1 > y0) {
            image.fillRect(x0, y0, x1 - x0, y1 - y0, color);
        }
    }

    private static int slopeShade(Raster raster, int x, int y, int height) {
        int west = raster.at(Math.max(0, x - 1), y).height;
        int north = raster.at(x, Math.max(0, y - 1)).height;
        return Math.max(-18, Math.min(18, (height - west) * 2 + (height - north)));
    }

    private static int palette(Sample sample, int shade, int hash) {
        int base = switch (sample.terrain) {
            case WATER -> 0xFF4E9AA8;
            case GRASS -> 0xFF86A64C;
            case FOREST -> 0xFF47743A;
            case SAND -> 0xFFC9AE68;
            case BADLANDS -> 0xFFB77046;
            case ROCK -> 0xFF837B68;
            case MOUNTAIN -> 0xFF777665;
            case SNOW -> 0xFFD8D7C1;
            case UNKNOWN -> 0xFFD7BC82;
        };
        int texture = Math.floorMod(hash, 9) - 4;
        return adjust(base, shade + texture);
    }

    private static int adjust(int color, int amount) {
        int r = clamp(((color >>> 16) & 0xFF) + amount);
        int g = clamp(((color >>> 8) & 0xFF) + amount);
        int b = clamp((color & 0xFF) + amount / 2);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round((color >>> 24) * opacity)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static boolean touchesLand(Raster raster, int x, int y) {
        return raster.at(Math.max(0, x - 1), y).terrain != Terrain.WATER
                || raster.at(Math.min(raster.width - 1, x + 1), y).terrain != Terrain.WATER
                || raster.at(x, Math.max(0, y - 1)).terrain != Terrain.WATER
                || raster.at(x, Math.min(raster.height - 1, y + 1)).terrain != Terrain.WATER;
    }

    private static int hash(int x, int z) {
        int value = x * 734_287_67 ^ z * 912_931;
        value ^= value >>> 13;
        value *= 1_274_126_177;
        return value ^ value >>> 16;
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

    private static void ensureWorld(MinecraftClient client, ClientWorld level) {
        if (memoryLevel == level) {
            return;
        }
        scheduleCacheWrites();
        memoryLevel = level;
        SURFACE_MEMORY.clear();
        LOADED_TILES.clear();
        DIRTY_TILES.clear();
        lastCacheFlushTick = level.getTime();
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
        if (LOADED_TILES.put(tileKey, Boolean.TRUE) != null) {
            return;
        }
        Path file = tilePath(cacheDirectory, tileX(worldX), tileZ(worldZ));
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_VERSION) {
                return;
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
                SURFACE_MEMORY.put(surfaceKey(x, z), new Sample(terrain, height, sourceColor, x, z));
            }
        } catch (EOFException exception) {
            VillageQuest.LOGGER.warn("Ignoring truncated Village Quest map tile {}", file);
        } catch (IOException exception) {
            VillageQuest.LOGGER.warn("Could not read Village Quest map tile {}", file, exception);
        }
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

    private static Path cacheDirectory(MinecraftClient client, ClientWorld level) {
        String source;
        if (client.getCurrentServerEntry() != null) {
            source = "server-" + client.getCurrentServerEntry().address;
        } else if (client.getServer() != null) {
            source = "world-" + client.getServer().getSaveProperties().getLevelName()
                    + "-" + client.getServer().getSavePath(WorldSavePath.ROOT)
                    .toAbsolutePath().normalize();
        } else {
            source = "local-world";
        }
        String dimension = level.getRegistryKey().getValue().toString();
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
        return Math.floorDiv(worldX, sampleGridSize() * TILE_CELLS);
    }

    private static int tileZ(int worldZ) {
        return Math.floorDiv(worldZ, sampleGridSize() * TILE_CELLS);
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

    private record Sample(Terrain terrain, int height, int sourceColor, int worldX, int worldZ) {}

    private static final class SurfaceTexture {
        private final Identifier id;
        private NativeImageBackedTexture texture;
        private Raster uploadedRaster;
        private int width;
        private int height;
        private boolean decorated;

        private SurfaceTexture(Identifier id) {
            this.id = id;
        }

        private void upload(Raster raster, int requestedWidth, int requestedHeight,
                            boolean requestedDecorated) {
            if (texture == null || width != requestedWidth || height != requestedHeight) {
                release();
                width = requestedWidth;
                height = requestedHeight;
                texture = new NativeImageBackedTexture(() -> id.toString(), width, height, false);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            }
            if (uploadedRaster == raster && decorated == requestedDecorated) {
                return;
            }
            NativeImage pixels = texture.getImage();
            if (pixels == null) {
                return;
            }
            paint(pixels, raster, width, height, requestedDecorated);
            texture.upload();
            uploadedRaster = raster;
            decorated = requestedDecorated;
        }

        private void release() {
            if (texture != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            }
            texture = null;
            uploadedRaster = null;
            width = 0;
            height = 0;
        }
    }

    private record Raster(
            ClientWorld level,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int originCellX,
            int originCellZ,
            int cellSize,
            int gridSize,
            int width,
            int height,
            Sample[] samples,
            long sampledAt
    ) {
        private Sample at(int x, int y) {
            return samples[Math.max(0, Math.min(height - 1, y)) * width
                    + Math.max(0, Math.min(width - 1, x))];
        }

        private boolean matches(ClientWorld otherLevel, int otherMinX, int otherMaxX,
                                int otherMinZ, int otherMaxZ, int otherCellSize, int otherGridSize) {
            return level == otherLevel && minX == otherMinX && maxX == otherMaxX
                    && minZ == otherMinZ && maxZ == otherMaxZ
                    && cellSize == otherCellSize && gridSize == otherGridSize;
        }
    }
}
