package de.quest.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import de.quest.VillageQuest;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    private static final Sample UNKNOWN_SAMPLE = new Sample(Terrain.UNKNOWN, 63, 0);
    private static final int MAX_REMEMBERED_SAMPLES = 120_000;
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
    private static Raster screenRaster;
    private static Raster hudRaster;
    private static ClientLevel memoryLevel;

    private SurfaceMapRenderer() {}

    public static void drawScreen(GuiGraphics graphics, int x, int y, int width, int height,
                                  int minX, int maxX, int minZ, int maxZ) {
        drawScreen(graphics, x, y, width, height, minX, maxX, minZ, maxZ, true);
    }

    public static void drawScreen(GuiGraphics graphics, int x, int y, int width, int height,
                                  int minX, int maxX, int minZ, int maxZ,
                                  boolean refreshTerrain) {
        screenRaster = raster(Minecraft.getInstance(), screenRaster, minX, maxX, minZ, maxZ,
                Math.max(72, width / 2), Math.max(42, height / 2), 50L, refreshTerrain);
        draw(graphics, SCREEN_TEXTURE, screenRaster, x, y, width, height,
                minX, maxX, minZ, maxZ, true);
    }

    public static void drawHud(GuiGraphics graphics, int x, int y, int width, int height,
                               int minX, int maxX, int minZ, int maxZ) {
        hudRaster = raster(Minecraft.getInstance(), hudRaster, minX, maxX, minZ, maxZ,
                Math.max(42, width / 2), Math.max(24, height / 2), 25L, true);
        draw(graphics, HUD_TEXTURE, hudRaster, x, y, width, height,
                minX, maxX, minZ, maxZ, false);
    }

    public static void invalidateScreen() {
        screenRaster = null;
    }

    public static void clear() {
        screenRaster = null;
        hudRaster = null;
        memoryLevel = null;
        SURFACE_MEMORY.clear();
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
        if (!refreshTerrain && existing != null && existing.level == level
                && existing.width == sampleWidth && existing.height == sampleHeight) {
            return existing;
        }
        long now = level.getGameTime();
        if (existing != null && existing.matches(level, minX, maxX, minZ, maxZ, sampleWidth, sampleHeight)
                && now - existing.sampledAt < maxAge) {
            return existing;
        }
        Sample[] samples = new Sample[sampleWidth * sampleHeight];
        for (int row = 0; row < sampleHeight; row++) {
            double zFactor = sampleHeight <= 1 ? 0.5 : (row + 0.5) / sampleHeight;
            int worldZ = minZ + (int) Math.floor(zFactor * Math.max(1, maxZ - minZ));
            for (int column = 0; column < sampleWidth; column++) {
                double xFactor = sampleWidth <= 1 ? 0.5 : (column + 0.5) / sampleWidth;
                int worldX = minX + (int) Math.floor(xFactor * Math.max(1, maxX - minX));
                samples[row * sampleWidth + column] = sample(level, worldX, worldZ);
            }
        }
        return new Raster(level, minX, maxX, minZ, maxZ, sampleWidth, sampleHeight, samples, now);
    }

    private static Sample sample(ClientLevel level, int worldX, int worldZ) {
        if (memoryLevel != level) {
            memoryLevel = level;
            SURFACE_MEMORY.clear();
        }
        long key = ((long) worldX << 32) ^ (worldZ & 0xFFFF_FFFFL);
        Sample live = sampleLoaded(level, worldX, worldZ);
        if (live.terrain != Terrain.UNKNOWN) {
            SURFACE_MEMORY.put(key, live);
            return live;
        }
        return SURFACE_MEMORY.getOrDefault(key, UNKNOWN_SAMPLE);
    }

    private static Sample sampleLoaded(ClientLevel level, int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        if (!level.hasChunk(chunkX, chunkZ)) {
            return UNKNOWN_SAMPLE;
        }
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return UNKNOWN_SAMPLE;
        }
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
        if (topY < level.getMinY()) {
            return UNKNOWN_SAMPLE;
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
            return UNKNOWN_SAMPLE;
        }
        int rgb = color.calculateARGBColor(MapColor.Brightness.NORMAL);
        return new Sample(classify(state, rgb, topY), topY, rgb);
    }

    private static Terrain classify(BlockState state, int rgb, int height) {
        if (!state.getFluidState().isEmpty() || state.is(Blocks.WATER) || state.is(Blocks.ICE)) {
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
        graphics.fill(x, y, x + width, y + height, 0xFFD8B978);
        if (raster == null) {
            return;
        }
        texture.upload(raster, width, height, decorate);
        double requestedWidth = Math.max(1.0, maxX - minX);
        double requestedHeight = Math.max(1.0, maxZ - minZ);
        int drawX = x + (int) Math.round((raster.minX - minX) / requestedWidth * width);
        int drawY = y + (int) Math.round((raster.minZ - minZ) / requestedHeight * height);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id, drawX, drawY, 0.0f, 0.0f,
                width, height, width, height);
    }

    private static void paint(NativeImage image, Raster raster, int width, int height,
                              boolean decorate) {
        image.fillRect(0, 0, width, height, 0xFFD8B978);
        for (int row = 0; row < raster.height; row++) {
            int y0 = row * height / raster.height;
            int y1 = Math.max(y0 + 1, (row + 1) * height / raster.height);
            for (int column = 0; column < raster.width; column++) {
                int x0 = column * width / raster.width;
                int x1 = Math.max(x0 + 1, (column + 1) * width / raster.width);
                Sample sample = raster.at(column, row);
                int shade = slopeShade(raster, column, row, sample.height);
                int color = palette(sample, shade, hash(raster.minX + column, raster.minZ + row));
                image.fillRect(x0, y0, x1 - x0, y1 - y0, color);
                if (sample.terrain == Terrain.WATER && touchesLand(raster, column, row)) {
                    image.fillRect(x0, y0, x1 - x0, 1, 0xFFB7C59A);
                }
            }
        }
        if (decorate) {
            drawTerrainGlyphs(image, raster, width, height);
        }
    }

    private static void drawTerrainGlyphs(NativeImage image, Raster raster,
                                          int width, int height) {
        for (int row = 1; row < raster.height - 1; row += 2) {
            for (int column = 1; column < raster.width - 1; column += 2) {
                Sample sample = raster.at(column, row);
                int hash = hash(raster.minX + column * 7, raster.minZ + row * 11);
                int px = column * width / raster.width;
                int py = row * height / raster.height;
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

    private record Sample(Terrain terrain, int height, int sourceColor) {}

    private static final class SurfaceTexture {
        private final Identifier id;
        private DynamicTexture texture;
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
                texture = new DynamicTexture(() -> id.toString(), width, height, false);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }
            if (uploadedRaster == raster && decorated == requestedDecorated) {
                return;
            }
            NativeImage pixels = texture.getPixels();
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
                Minecraft.getInstance().getTextureManager().release(id);
            }
            texture = null;
            uploadedRaster = null;
            width = 0;
            height = 0;
        }
    }

    private record Raster(
            ClientLevel level,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int width,
            int height,
            Sample[] samples,
            long sampledAt
    ) {
        private Sample at(int x, int y) {
            return samples[Math.max(0, Math.min(height - 1, y)) * width
                    + Math.max(0, Math.min(width - 1, x))];
        }

        private boolean matches(ClientLevel otherLevel, int otherMinX, int otherMaxX,
                                int otherMinZ, int otherMaxZ, int otherWidth, int otherHeight) {
            return level == otherLevel && minX == otherMinX && maxX == otherMaxX
                    && minZ == otherMinZ && maxZ == otherMaxZ
                    && width == otherWidth && height == otherHeight;
        }
    }
}
