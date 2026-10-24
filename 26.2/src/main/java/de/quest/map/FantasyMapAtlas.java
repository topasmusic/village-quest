package de.quest.map;

import de.quest.map.FantasyMapStyle.Cell;
import de.quest.map.FantasyMapStyle.Terrain;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Small bundled atlases, decoded once; all subsequent map work is integer pixel work. */
public final class FantasyMapAtlas {
    @FunctionalInterface public interface Cells { Cell at(int x, int z); }
    private static final int[] MOTIFS = read("fantasy-motifs-v1.png", 128);
    private static final int[] GROUNDS = read("fantasy-grounds-v1.png", 64);
    private static final int[] TREES = read("fantasy-trees-v2.png", 128);
    private FantasyMapAtlas() {}

    private static int[] read(String name, int height) {
        String path = "/assets/village-quest/textures/gui/map/" + name;
        try (InputStream stream = FantasyMapAtlas.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            var image = ImageIO.read(stream);
            if (image == null || image.getWidth() != 128 || image.getHeight() != height) return null;
            return image.getRGB(0, 0, 128, height, null, 0, 128);
        } catch (IOException ignored) {
            return null; // The deterministic ink painter remains a safe fallback.
        }
    }

    public static boolean available() { return MOTIFS != null && GROUNDS != null && TREES != null; }

    public static int ground(Terrain terrain, int x, int y, boolean shallow) {
        if (GROUNDS == null || terrain == Terrain.UNKNOWN) return 0;
        int index = switch (terrain) {
            case WATER -> shallow ? 1 : 0; case SAND -> 2; case GRASS -> 3;
            case FOREST -> 4; case ROCK, MOUNTAIN -> 5; case SNOW -> 6; case BADLANDS -> 7;
            default -> 3;
        };
        return GROUNDS[(index / 4 * 32 + Math.floorMod(y, 32)) * 128
                + index % 4 * 32 + Math.floorMod(x, 32)];
    }

    /** Motifs are clipped to observed terrain, including every coastline/unknown boundary. */
    public static void overlay(int[] pixels, int width, int height, int originX, int originZ,
                               int cellSize, Cells cells) {
        if (MOTIFS == null || cellSize > 128) return;
        int cellWidth = width / 8, cellHeight = height / 8;
        Terrain[] mask = new Terrain[cellWidth * cellHeight];
        for (int z = 0; z < cellHeight; z++) for (int x = 0; x < cellWidth; x++)
            mask[z * cellWidth + x] = cells.at(originX + x, originZ + z).terrain();
        for (int family = 0; family < 2; family++) {
        int step = family == 0 ? 2 : 3;
        int reach = family == 0 ? 6 : 10;
        for (int z = Math.floorDiv(originZ - reach, step) * step; z < originZ + cellHeight + reach; z += step) {
            for (int x = Math.floorDiv(originX - reach, step) * step; x < originX + cellWidth + reach; x += step) {
                Cell anchor = cells.at(x, z);
                Terrain terrain = anchor.terrain();
                if ((terrain == Terrain.FOREST) != (family == 0)) continue;
                if (terrain != Terrain.FOREST && terrain != Terrain.ROCK
                        && terrain != Terrain.MOUNTAIN && terrain != Terrain.SNOW) continue;
                int hash = hash(anchor.worldX(), anchor.worldZ());
                if (terrain == Terrain.FOREST) {
                    int neighbours = 0;
                    for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                        if (cells.at(x + dx, z + dz).terrain() == Terrain.FOREST) neighbours++;
                    // Light woodland margins, dense interiors and stable irregular clearings.
                    int density = 20 + neighbours * 6;
                    int grove = hash(Math.floorDiv(x, 10), Math.floorDiv(z, 10));
                    density += (grove & 3) == 0 ? -42 : (grove & 3) == 1 ? -17 : 10;
                    if (Math.floorMod(hash >>> 5, 100) >= density) continue;
                } else {
                    // Let the observed high ground form a few dominant ridge masses.
                    // The common halo keeps this selection identical across tile seams.
                    int higher = 0;
                    for (int dz = -2; dz <= 2; dz += 2) for (int dx = -2; dx <= 2; dx += 2)
                        if (cells.at(x + dx, z + dz).terrain() != Terrain.UNKNOWN
                                && cells.at(x + dx, z + dz).height() > anchor.height() + 3) higher++;
                    if (higher > 3 || Math.floorMod(hash >>> 5, 100) >= 48) continue;
                }
                int size = terrain == Terrain.FOREST ? (cellSize <= 32 ? 22 + Math.floorMod(hash, 11) : 17 + (hash & 3))
                        : cellSize <= 32 ? 46 + Math.clamp((anchor.height() - 90) / 4, 0, 18) : 30 + (hash & 7);
                int sprite = terrain == Terrain.FOREST ? Math.floorMod(hash, 16)
                        : terrain == Terrain.SNOW ? 12 + (hash & 3) : 8 + (hash & 3);
                int jitter = terrain == Terrain.FOREST ? 15 : 19;
                int dx = (x - originX) * 8 + 4 - size / 2 + Math.floorMod(hash >>> 8, jitter) - jitter / 2;
                int dy = (z - originZ) * 8 + 7 - size * 85 / 100 + Math.floorMod(hash >>> 16, jitter) - jitter / 2;
                // A crown can overhang any observed dry land; its trunk anchor still requires forest.
                // Reject whole crowns at water/unknown, identically across tile seams.
                if (terrain == Terrain.FOREST && TREES != null) {
                    boolean fits = true;
                    for (int py = 0; py < size && fits; py++) for (int px = 0; px < size; px++) {
                        int source = TREES[(sprite / 4 * 32 + py * 32 / size) * 128
                                + sprite % 4 * 32 + px * 32 / size];
                        if ((source >>> 24) == 0) continue;
                        Terrain under = cells.at(originX + Math.floorDiv(dx + px, 8),
                                originZ + Math.floorDiv(dy + py, 8)).terrain();
                        if (under == Terrain.UNKNOWN || under == Terrain.WATER) { fits = false; break; }
                    }
                    if (!fits) continue;
                }
                // Soft world-fixed contact shadows bind sprites to the ground. Never
                // shade water or unexplored land, and retain the HUD's alpha channel.
                int cx = dx + size / 2 + 3, cy = dy + size * 4 / 5 + 2;
                int rx = Math.max(5, size / 2), ry = Math.max(3, size / 7);
                for (int sy = Math.max(0, cy - ry); sy < Math.min(height, cy + ry + 1); sy++)
                    for (int sx = Math.max(0, cx - rx); sx < Math.min(width, cx + rx + 1); sx++) {
                        Terrain observed = mask[sy / 8 * cellWidth + sx / 8];
                        if (observed == Terrain.UNKNOWN || observed == Terrain.WATER) continue;
                        double distance = (sx - cx) * (sx - cx) / (double) (rx * rx)
                                + (sy - cy) * (sy - cy) / (double) (ry * ry);
                        if (distance >= 1) continue;
                        int i = sy * width + sx, old = pixels[i], dark = (int) (17 * (1 - distance));
                        int color = old & 0xFF000000;
                        for (int shift = 0; shift <= 16; shift += 8)
                            color |= Math.max(0, (old >>> shift & 255) - dark) << shift;
                        pixels[i] = color;
                    }
                for (int py = Math.max(0, -dy); py < Math.min(size, height - dy); py++) {
                    for (int px = Math.max(0, -dx); px < Math.min(size, width - dx); px++) {
                        int targetX = dx + px, targetY = dy + py;
                        Terrain observed = mask[targetY / 8 * cellWidth + targetX / 8];
                        if (observed != terrain && !(terrain == Terrain.FOREST && observed != Terrain.UNKNOWN && observed != Terrain.WATER)
                                && !(terrain != Terrain.FOREST && observed == Terrain.GRASS)
                                && !(terrain == Terrain.SNOW && (observed == Terrain.ROCK || observed == Terrain.MOUNTAIN))
                                && !(terrain == Terrain.MOUNTAIN && observed == Terrain.ROCK)
                                && !(terrain == Terrain.ROCK && observed == Terrain.MOUNTAIN)) continue;
                        int[] atlas = terrain == Terrain.FOREST && TREES != null ? TREES : MOTIFS;
                        int source = atlas[(sprite / 4 * 32 + py * 32 / size) * 128
                                + sprite % 4 * 32 + px * 32 / size];
                        int alpha = source >>> 24;
                        if (alpha == 0) continue;
                        int index = targetY * width + targetX, previous = pixels[index];
                        int blended = previous & 0xFF000000;
                        for (int shift = 0; shift <= 16; shift += 8)
                            blended |= ((((source >>> shift) & 255) * alpha
                                    + ((previous >>> shift) & 255) * (255 - alpha)) / 255) << shift;
                        pixels[index] = blended;
                    }
                }
            }
        }
        }
    }

    /** Reconstruct a coast between known sample centres instead of tracing square cell edges. */
    public static void shoreline(int[] pixels, int width, int height, int originX, int originZ, Cells cells) {
        int columns = width / 8 + 2, rows = height / 8 + 2;
        Terrain[] terrain = new Terrain[columns * rows];
        for (int z = 0; z < rows; z++) for (int x = 0; x < columns; x++)
            terrain[z * columns + x] = cells.at(originX + x - 1, originZ + z - 1).terrain();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int col = Math.floorDiv(x - 4, 8) + 1, row = Math.floorDiv(y - 4, 8) + 1;
            Terrain a = terrain[row * columns + col], b = terrain[row * columns + col + 1];
            Terrain c = terrain[(row + 1) * columns + col], d = terrain[(row + 1) * columns + col + 1];
            if (a == Terrain.UNKNOWN || b == Terrain.UNKNOWN || c == Terrain.UNKNOWN || d == Terrain.UNKNOWN) continue;
            int fx = Math.floorMod(x - 4, 8), fy = Math.floorMod(y - 4, 8);
            int water = ((a == Terrain.WATER ? (8 - fx) * (8 - fy) : 0)
                    + (b == Terrain.WATER ? fx * (8 - fy) : 0)
                    + (c == Terrain.WATER ? (8 - fx) * fy : 0)
                    + (d == Terrain.WATER ? fx * fy : 0)) * 4;
            if (water == 0 || water == 256) continue;
            int previous = pixels[y * width + x], color;
            // Blend the observed shore continuously. Discrete sand/aqua bands made
            // every narrow river look like a bright, equally wide outlined canal.
            if (water >= 128) color = mix(0x8AAFA8, 0x337FA9, (water - 128) * 100 / 128);
            else {
                Terrain current = terrain[(y / 8 + 1) * columns + x / 8 + 1];
                int land = current == Terrain.WATER ? 0xA0AC7E : previous & 0xFFFFFF;
                color = mix(land, 0xAAB699, water * 100 / 128);
            }
            pixels[y * width + x] = previous & 0xFF000000 | color;
        }
    }

    private static int mix(int a, int b, int percent) {
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= ((((a >>> shift) & 255) * (100 - percent) + ((b >>> shift) & 255) * percent) / 100) << shift;
        return result;
    }

    private static int hash(int x, int z) {
        int hash = x * 73_428_767 ^ z * 912_931;
        return (hash ^ hash >>> 13) * 1_274_126_177;
    }
}
