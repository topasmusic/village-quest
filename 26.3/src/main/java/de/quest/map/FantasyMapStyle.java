package de.quest.map;

/** Pure pixel cartography of observed cells. No seed prediction or world queries. */
public final class FantasyMapStyle {
    // Ordinals deliberately retain the v1 on-disk surface cache contract.
    public enum Terrain { UNKNOWN, WATER, GRASS, FOREST, SAND, BADLANDS, ROCK, MOUNTAIN, SNOW }
    public record Cell(Terrain terrain, int height, int sourceColor, int worldX, int worldZ) {}
    public static final int PIXELS = 8;
    public static final int COAST = 0xFF8EAE9B;

    private FantasyMapStyle() {}

    public static int[] paint(Cell cell, Cell west, Cell north, Cell east, Cell south,
                              int cellSize, boolean detail, float opacity) {
        int[] pixels = new int[PIXELS * PIXELS];
        paintInto(pixels, 0, PIXELS, cell, west, north, east, south, cellSize, detail, opacity);
        return pixels;
    }

    public static void paintInto(int[] pixels, int offset, int stride, Cell cell,
                                 Cell west, Cell north, Cell east, Cell south,
                                 int cellSize, boolean detail, float opacity) {
        Terrain terrain = cell.terrain;
        int phase = hash(cell.worldX, cell.worldZ);
        int lightWest = known(west) ? cell.height - west.height : 0;
        int lightEast = known(east) ? east.height - cell.height : lightWest;
        int lightNorth = known(north) ? cell.height - north.height : 0;
        int lightSouth = known(south) ? south.height - cell.height : lightNorth;
        int base = base(terrain);
        // Keep some observed material colour without letting leaf green erase the ink palette.
        if (cell.sourceColor != 0 && known(cell) && terrain != Terrain.WATER)
            base = blend(base, cell.sourceColor, 12);
        for (int y = 0; y < PIXELS; y++) {
            for (int x = 0; x < PIXELS; x++) {
                int grain = Math.floorMod(hash(cell.worldX * 4 + x / 2, cell.worldZ * 4 + y / 2), 3) - 1;
                int texture = FantasyMapAtlas.ground(terrain,
                        Math.floorDiv(cell.worldX, Math.max(1, cellSize)) * 4 + x / 2,
                        Math.floorDiv(cell.worldZ, Math.max(1, cellSize)) * 4 + y / 2,
                        land(west) || land(north) || land(east) || land(south));
                int textureAmount = terrain == Terrain.WATER ? 7 : terrain == Terrain.SAND ? 30
                        : terrain == Terrain.GRASS ? 8 : terrain == Terrain.FOREST ? 10 : 18;
                int ground = texture == 0 ? base : blend(base, texture, textureAmount);
                // Calm, feathered ground transitions instead of an eight-pixel terrain quilt.
                ground = feather(ground, terrain, west.terrain, x);
                ground = feather(ground, terrain, east.terrain, 7 - x);
                ground = feather(ground, terrain, north.terrain, y);
                ground = feather(ground, terrain, south.terrain, 7 - y);
                // A continuous lit slope, not one solid lightness square per sample.
                int relief = terrain == Terrain.WATER || terrain == Terrain.UNKNOWN ? 0
                        : Math.clamp((lightWest * (7 - x) + lightEast * x
                        + lightNorth * (7 - y) + lightSouth * y) / 5, -26, 22);
                int value = adjust(ground, relief + grain);
                if (terrain == Terrain.WATER) {
                    if ((land(west) && x == 0) || (land(east) && x == 7)
                            || (land(north) && y == 0) || (land(south) && y == 7)) value = COAST;
                    else if ((land(west) && x == 1) || (land(east) && x == 6)
                            || (land(north) && y == 1) || (land(south) && y == 6)) value = blend(ground, COAST, 55);
                    else {
                        int px = Math.floorDiv(cell.worldX, Math.max(1, cellSize)) * 8 + x;
                        int py = Math.floorDiv(cell.worldZ, Math.max(1, cellSize)) * 8 + y;
                        int wave = hash(Math.floorDiv(px, 23), Math.floorDiv(py, 13));
                        int wy = 2 + Math.floorMod(wave, 9), wx = 2 + Math.floorMod(wave >>> 8, 11);
                        if (Math.floorMod(py, 13) == wy && Math.floorMod(px, 23) >= wx
                                && Math.floorMod(px, 23) < wx + 3 + (wave >>> 16 & 3)
                                && (wave & 3) == 0) value = 0xFF528EAB;
                    }
                }
                pixels[offset + y * stride + x] = alpha(value, opacity);
            }
        }
        if (!detail || terrain == Terrain.UNKNOWN || FantasyMapAtlas.available()) return;
        boolean near = cellSize <= 32;
        boolean medium = cellSize <= 128;
        if (terrain == Terrain.FOREST && (near || medium && (phase & 3) == 0)) {
            int centre = 3 + (phase & 1);
            int top = 1 + (phase >>> 2 & 1);
            dot(pixels, offset, stride, centre, 7, 0x6C522F, opacity);
            for (int y = top; y <= 6; y++) {
                int radius = Math.min(3, (y + 1) / 2);
                for (int x = centre - radius; x <= centre + radius; x++) {
                    int ink = x == centre - radius || y == 6 ? 0x294E37
                            : x < centre ? 0x7B9B42 : x == centre ? 0x4F7A3B : 0x38683A;
                    dot(pixels, offset, stride, x, y, ink, opacity);
                }
            }
            dot(pixels, offset, stride, centre - 1, top + 1, 0xA3B757, opacity);
        } else if ((terrain == Terrain.MOUNTAIN || terrain == Terrain.ROCK || terrain == Terrain.SNOW)
                && medium && (terrain != Terrain.ROCK || (phase & 1) == 0)) {
            int peak = 3 + (phase & 1);
            for (int y = 1; y <= 6; y++) {
                int radius = Math.min(3, (y + 1) / 2);
                for (int x = Math.max(0, peak - radius); x <= Math.min(7, peak + radius); x++) {
                    int ink = x == peak - radius || y == 6 ? 0x656E60
                            : x < peak ? 0xC4C3A7 : x == peak ? 0xA4AA94 : 0x7B887A;
                    if (terrain == Terrain.SNOW && y < 4) ink = x <= peak ? 0xF0EDD7 : 0xBCCFD0;
                    dot(pixels, offset, stride, x, y, ink, opacity);
                }
            }
        } else if (near && terrain == Terrain.GRASS && (phase & 3) == 0) {
            dot(pixels, offset, stride, 2, 5, 0x7B9245, opacity);
            dot(pixels, offset, stride, 3, 4, 0xB4BF6C, opacity);
            dot(pixels, offset, stride, 4, 5, 0x7B9245, opacity);
        } else if (near && terrain == Terrain.SAND && (phase & 1) == 0) {
            for (int x = 2; x <= 5; x++) dot(pixels, offset, stride, x, 4, 0xC3A96D, opacity);
        } else if (near && terrain == Terrain.BADLANDS) {
            for (int x = 1; x <= 6; x++) dot(pixels, offset, stride, x, 4 + (x / 3 & 1), 0x9D6946, opacity);
        }
    }

    private static boolean known(Cell cell) { return cell.terrain != Terrain.UNKNOWN; }
    /** Cheap observed terrain while detailed tiles wait for their shared painting budget. */
    public static int groundPreview(Cell cell,float opacity) {
        int color=base(cell.terrain);
        if(cell.sourceColor!=0 && known(cell) && cell.terrain!=Terrain.WATER) color=blend(color,cell.sourceColor,12);
        return Math.clamp(Math.round(255*opacity),0,255)<<24 | color;
    }
    private static int base(Terrain terrain) {
        return switch (terrain) {
            case UNKNOWN -> 0xD9C18C; case WATER -> 0x337FA9; case GRASS -> 0x91AA51;
            case FOREST -> 0x7D994C; case SAND -> 0xD8BF7C; case BADLANDS -> 0xBA8050;
            case ROCK -> 0x9A957C; case MOUNTAIN -> 0x898977; case SNOW -> 0xDADFCF;
        };
    }
    private static int feather(int color, Terrain terrain, Terrain neighbour, int distance) {
        if (terrain == neighbour || terrain == Terrain.UNKNOWN || neighbour == Terrain.UNKNOWN
                || terrain == Terrain.WATER || neighbour == Terrain.WATER || distance > 3) return color;
        return blend(color, base(neighbour), 50 - distance * 15);
    }
    private static boolean land(Cell cell) { return known(cell) && cell.terrain != Terrain.WATER; }
    private static void dot(int[] pixels, int offset, int stride, int x, int y, int color, float opacity) {
        pixels[offset + y * stride + x] = alpha(color, opacity);
    }
    private static int alpha(int color, float opacity) {
        return Math.clamp(Math.round(255 * opacity), 0, 255) << 24 | color & 0xFFFFFF;
    }
    private static int blend(int a, int b, int percent) {
        int color = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            color |= ((((a >>> shift) & 255) * (100 - percent) + ((b >>> shift) & 255) * percent) / 100) << shift;
        return color;
    }
    private static int adjust(int color, int amount) {
        return 0xFF000000 | Math.clamp((color >>> 16 & 255) + amount, 0, 255) << 16
                | Math.clamp((color >>> 8 & 255) + amount, 0, 255) << 8
                | Math.clamp((color & 255) + amount, 0, 255);
    }
    private static int hash(int x, int z) {
        int value = x * 73_428_767 ^ z * 912_931;
        value = (value ^ value >>> 13) * 1_274_126_177;
        return value ^ value >>> 16;
    }
}
