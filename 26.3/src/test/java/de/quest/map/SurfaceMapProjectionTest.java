package de.quest.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SurfaceMapProjectionTest {
    @Test
    void coverageIsWorldAlignedAndBufferedBeyondTheViewport() {
        SurfaceMapProjection.Coverage coverage = SurfaceMapProjection.bufferedCoverage(
                103, 503, -217, -10, 4);
        assertEquals(0, Math.floorMod(coverage.minX(), 4));
        assertEquals(0, Math.floorMod(coverage.minZ(), 4));
        assertTrue(coverage.contains(103, 503, -217, -10));
        assertTrue(coverage.minX() < 103);
        assertTrue(coverage.maxX() > 503);
    }

    @Test
    void theSameWorldPointKeepsItsPixelPhaseAcrossRasterRebuilds() {
        SurfaceMapProjection.Coverage first = SurfaceMapProjection.bufferedCoverage(
                0, 400, 0, 200, 4);
        SurfaceMapProjection.Coverage shifted = SurfaceMapProjection.bufferedCoverage(
                250, 650, 100, 300, 4);
        int worldX = 320;
        double firstRelative = SurfaceMapProjection.textureCoordinate(worldX, first.minX(), 4)
                - SurfaceMapProjection.textureCoordinate(250, first.minX(), 4);
        double shiftedRelative = SurfaceMapProjection.textureCoordinate(worldX, shifted.minX(), 4)
                - SurfaceMapProjection.textureCoordinate(250, shifted.minX(), 4);
        assertEquals(firstRelative, shiftedRelative, 0.00001D);
    }

    @Test
    void panningInsideTheBufferDoesNotRequireANewRaster() {
        SurfaceMapProjection.Coverage coverage = SurfaceMapProjection.bufferedCoverage(
                -200, 200, -100, 100, 4);
        assertTrue(coverage.contains(-50, 350, -20, 180));
    }
}
