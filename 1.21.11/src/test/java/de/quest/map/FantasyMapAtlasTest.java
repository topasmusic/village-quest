package de.quest.map;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.map.FantasyMapStyle.Cell;
import de.quest.map.FantasyMapStyle.Terrain;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FantasyMapAtlasTest {
    private static Cell fixture(int x, int z) {
        return new Cell(x >= 0 && x < 16 && z >= 0 && z < 16 ? Terrain.FOREST : Terrain.UNKNOWN,
                70, 0, x * 4 + 2, z * 4 + 2);
    }
    @Test void shippedAtlasesLoadAndAllKnownGroundKindsHavePixels() {
        assertTrue(FantasyMapAtlas.available());
        for (Terrain terrain : Terrain.values())
            assertEquals(terrain == Terrain.UNKNOWN, FantasyMapAtlas.ground(terrain, -17, 9, false) == 0);
    }

    @Test void forestArtNeverPaintsUnknownOrWaterAndKeepsHudAlpha() {
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0x66557733);
        FantasyMapAtlas.overlay(pixels, 128, 128, 0, 0, 4, (x, z) -> new Cell(
                x < 4 ? Terrain.FOREST : x < 8 ? Terrain.WATER : Terrain.UNKNOWN, 70, 0, x * 4 + 2, z * 4 + 2));
        assertTrue(Arrays.stream(pixels).anyMatch(pixel -> pixel != 0x66557733));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertEquals(0x66, pixels[y * 128 + x] >>> 24);
            if (x >= 32) assertEquals(0x66557733, pixels[y * 128 + x]);
        }
    }

    @Test void adjacentPaintTilesMatchTheSameWorldRegionRenderedTogether() {
        int[] combined = new int[256 * 128]; Arrays.fill(combined, 0xFF708030);
        int[] first = new int[128 * 128], second = new int[128 * 128];
        Arrays.fill(first, 0xFF708030); Arrays.fill(second, 0xFF708030);
        FantasyMapAtlas.overlay(combined, 256, 128, -16, -8, 4, FantasyMapAtlasTest::fixture);
        FantasyMapAtlas.overlay(first, 128, 128, -16, -8, 4, FantasyMapAtlasTest::fixture);
        FantasyMapAtlas.overlay(second, 128, 128, 0, -8, 4, FantasyMapAtlasTest::fixture);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 256; x++)
            assertEquals(combined[y * 256 + x], x < 128 ? first[y * 128 + x] : second[y * 128 + x - 128]);
    }

    @Test void distantZoomOmitsTheLargeMotifs() {
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0xFF708030);
        int[] original = pixels.clone();
        FantasyMapAtlas.overlay(pixels, 128, 128, 0, 0, 256, FantasyMapAtlasTest::fixture);
        assertArrayEquals(original, pixels);
    }

    @Test void reconstructedCoastsHaveMatchingTileSeamsAndNeverGuessAcrossUnknownLand() {
        FantasyMapAtlas.Cells coast = (x, z) -> new Cell(x + z < 0 ? Terrain.WATER : Terrain.SAND, 63, 0, x * 4, z * 4);
        int[] combined = new int[256 * 128], first = new int[128 * 128], second = new int[128 * 128];
        Arrays.fill(combined, 0x66557733); Arrays.fill(first, 0x66557733); Arrays.fill(second, 0x66557733);
        FantasyMapAtlas.shoreline(combined, 256, 128, -16, -8, coast);
        FantasyMapAtlas.shoreline(first, 128, 128, -16, -8, coast);
        FantasyMapAtlas.shoreline(second, 128, 128, 0, -8, coast);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 256; x++) {
            assertEquals(combined[y * 256 + x], x < 128 ? first[y * 128 + x] : second[y * 128 + x - 128]);
            assertEquals(0x66, combined[y * 256 + x] >>> 24);
        }
        assertTrue(Arrays.stream(combined).distinct().count() >= 4);
        int[] unknown = new int[128 * 128]; Arrays.fill(unknown, 0x66557733);
        FantasyMapAtlas.shoreline(unknown, 128, 128, 0, 0, (x, z) -> new Cell(
                x < 0 ? Terrain.WATER : Terrain.UNKNOWN, 63, 0, x * 4, z * 4));
        for (int pixel : unknown) assertEquals(0x66557733, pixel);
    }
    @Test void narrowForestStripDoesNotLeaveClippedTreeFragments() {
        int[] pixels=new int[128*128]; Arrays.fill(pixels,0xFF708030);
        int[] before=pixels.clone();
        for(int strip=-32;strip<32;strip++) {
            Arrays.fill(pixels,0xFF708030); final int column=strip;
            FantasyMapAtlas.overlay(pixels,128,128,strip-8,-8,4,(x,z) -> new Cell(
                    x==column?Terrain.FOREST:Terrain.WATER,70,0,x*4+2,z*4+2));
            assertArrayEquals(before,pixels,"A tree that cannot fit must be omitted as a whole, including its shadow");
        }
    }
    @Test void mixedObservedLandKeepsPlentifulTreesAndMatchesTileSeams() {
        FantasyMapAtlas.Cells forest=(x,z) -> new Cell(Terrain.FOREST,70,0,x*4+2,z*4+2);
        FantasyMapAtlas.Cells mixed=(x,z) -> new Cell(Math.floorMod(x+z,2)==0?Terrain.FOREST:Terrain.SAND,70,0,x*4+2,z*4+2);
        int[] dense=new int[256*128],patchy=new int[256*128],left=new int[128*128],right=new int[128*128];
        for(int[] image:new int[][]{dense,patchy,left,right})Arrays.fill(image,0xFF708030);
        FantasyMapAtlas.overlay(dense,256,128,-16,-8,4,forest);
        FantasyMapAtlas.overlay(patchy,256,128,-16,-8,4,mixed);
        FantasyMapAtlas.overlay(left,128,128,-16,-8,4,mixed);
        FantasyMapAtlas.overlay(right,128,128,0,-8,4,mixed);
        long wooded=Arrays.stream(dense).filter(p -> p!=0xFF708030).count();
        long mixedWooded=Arrays.stream(patchy).filter(p -> p!=0xFF708030).count();
        assertTrue(wooded>1000);
        assertTrue(mixedWooded>wooded/4,"Known land texture boundaries must not erase woodland");
        for(int y=0;y<128;y++)for(int x=0;x<256;x++)
            assertEquals(patchy[y*256+x],x<128?left[y*128+x]:right[y*128+x-128]);
    }
}
