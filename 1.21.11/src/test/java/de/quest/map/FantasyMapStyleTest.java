package de.quest.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FantasyMapStyleTest {
    private static final FantasyMapStyle.Cell WATER = cell(FantasyMapStyle.Terrain.WATER, 63);
    private static final FantasyMapStyle.Cell LAND = cell(FantasyMapStyle.Terrain.GRASS, 70);
    private static final FantasyMapStyle.Cell UNKNOWN = cell(FantasyMapStyle.Terrain.UNKNOWN, 63);

    private static FantasyMapStyle.Cell cell(FantasyMapStyle.Terrain terrain, int height) {
        return new FantasyMapStyle.Cell(terrain, height, 0, -18, 26);
    }

    private static int[] paint(FantasyMapStyle.Cell cell, FantasyMapStyle.Cell west,
                               FantasyMapStyle.Cell north, int scale, boolean detail, float opacity) {
        return FantasyMapStyle.paint(cell, west, north, UNKNOWN, UNKNOWN, scale, detail, opacity);
    }

    @Test void unknownNeighboursDoNotInventCoastlines() {
        assertArrayEquals(paint(WATER, UNKNOWN, UNKNOWN, 4, false, 1),
                paint(WATER, WATER, WATER, 4, false, 1));
        assertFalse(Arrays.equals(paint(WATER, LAND, UNKNOWN, 4, false, 1),
                paint(WATER, WATER, WATER, 4, false, 1)));
    }

    @Test void coastInkFollowsTheActualSide() {
        int[] coast = paint(WATER, LAND, UNKNOWN, 4, false, 1);
        assertEquals(FantasyMapStyle.COAST, coast[3 * 8]);
        assertNotEquals(FantasyMapStyle.COAST, coast[3 * 8 + 7]);
    }

    @Test void worldAnchoredForestAndReliefHaveRealPixelDetailAndZoomLod() {
        for (var terrain : new FantasyMapStyle.Terrain[]{FantasyMapStyle.Terrain.FOREST,
                FantasyMapStyle.Terrain.MOUNTAIN, FantasyMapStyle.Terrain.SNOW}) {
            var sample = cell(terrain, 140);
            int[] near = paint(sample, sample, sample, 4, true, 1);
            assertTrue(Arrays.stream(near).distinct().count() >= 4);
            assertArrayEquals(near, paint(sample, sample, sample, 4, true, 1));
            assertFalse(Arrays.equals(near, paint(sample, sample, sample, 256, true, 1)));
        }
    }

    @Test void unknownRemainsPaperAndEveryPixelHonoursHudOpacity() {
        int[] pixels = paint(UNKNOWN, LAND, LAND, 4, true, .4f);
        assertEquals(64, pixels.length);
        for (int pixel : pixels) assertEquals(102, pixel >>> 24);
        assertArrayEquals(pixels, paint(UNKNOWN, UNKNOWN, UNKNOWN, 4, false, .4f));
    }

    @Test void cliffShadingIgnoresUnexploredHeightAndUsesObservedRelief() {
        assertArrayEquals(paint(LAND, UNKNOWN, UNKNOWN, 4, false, 1),
                paint(LAND, LAND, LAND, 4, false, 1));
        assertFalse(Arrays.equals(paint(LAND, cell(FantasyMapStyle.Terrain.GRASS, 90), LAND, 4, false, 1),
                paint(LAND, LAND, LAND, 4, false, 1)));
    }
}
