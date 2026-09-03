package de.quest.map;

/** Pure world-to-raster projection shared by the client renderer and regression tests. */
public final class SurfaceMapProjection {
    public static final int PIXELS_PER_CELL = 4;

    private SurfaceMapProjection() {}

    public static Coverage bufferedCoverage(int minX, int maxX, int minZ, int maxZ, int cellSize) {
        int safeCellSize = Math.max(1, cellSize);
        long spanX = Math.max(1L, (long) maxX - minX);
        long spanZ = Math.max(1L, (long) maxZ - minZ);
        long marginX = Math.max(safeCellSize, (spanX + 1L) / 2L);
        long marginZ = Math.max(safeCellSize, (spanZ + 1L) / 2L);
        int originCellX = floorCell(clampToInt((long) minX - marginX), safeCellSize) - 1;
        int originCellZ = floorCell(clampToInt((long) minZ - marginZ), safeCellSize) - 1;
        int endCellX = ceilCell(clampToInt((long) maxX + marginX), safeCellSize) + 1;
        int endCellZ = ceilCell(clampToInt((long) maxZ + marginZ), safeCellSize) + 1;
        return new Coverage(originCellX, originCellZ,
                Math.max(1, endCellX - originCellX),
                Math.max(1, endCellZ - originCellZ), safeCellSize);
    }

    public static double textureCoordinate(int worldCoordinate, int worldMinimum, int cellSize) {
        return (worldCoordinate - (double) worldMinimum) / Math.max(1, cellSize) * PIXELS_PER_CELL;
    }

    private static int floorCell(int coordinate, int cellSize) {
        return Math.floorDiv(coordinate, cellSize);
    }

    private static int ceilCell(int coordinate, int cellSize) {
        return -Math.floorDiv(-coordinate, cellSize);
    }

    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    public record Coverage(int originCellX, int originCellZ, int cellWidth, int cellHeight, int cellSize) {
        public int minX() {
            return clampToInt((long) originCellX * cellSize);
        }

        public int minZ() {
            return clampToInt((long) originCellZ * cellSize);
        }

        public int maxX() {
            return clampToInt((long) (originCellX + cellWidth) * cellSize);
        }

        public int maxZ() {
            return clampToInt((long) (originCellZ + cellHeight) * cellSize);
        }

        public boolean contains(int requestedMinX, int requestedMaxX,
                                int requestedMinZ, int requestedMaxZ) {
            return requestedMinX >= minX() && requestedMaxX <= maxX()
                    && requestedMinZ >= minZ() && requestedMaxZ <= maxZ();
        }
    }
}
