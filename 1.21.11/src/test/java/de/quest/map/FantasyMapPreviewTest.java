package de.quest.map;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.map.FantasyMapStyle.Cell;
import de.quest.map.FantasyMapStyle.Terrain;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Offline synthetic terrain fixture, rendered by the production pixel painter. */
class FantasyMapPreviewTest {
    private static Cell fixture(int x, int z) {
        double coast = Math.sin(z / 13.0) * 6 + 21;
        double ridge = Math.sin(x / 15.0) + Math.cos(z / 12.0);
        Terrain terrain = x < coast || Math.abs(x - 63 - 9 * Math.sin(z / 12.0)) < 1.8 ? Terrain.WATER
                : x < coast + 2 ? Terrain.SAND : ridge > 1.55 ? Terrain.SNOW
                : ridge > 1.15 ? Terrain.MOUNTAIN : Math.sin(x / 7.0) + Math.cos(z / 9.0) > .2
                ? Terrain.FOREST : Terrain.GRASS;
        if (x > 91 && z < 16) terrain = Terrain.UNKNOWN;
        return new Cell(terrain, terrain == Terrain.WATER ? 63 : 70 + (int) (Math.max(0, ridge) * 45), 0, x * 4 + 2, z * 4 + 2);
    }
    @Test void exportSyntheticTerrainAtThreeDetailLevels() throws Exception {
        Path directory = Path.of("build/reports/fantasy-map");
        Files.createDirectories(directory);
        for (int scale : new int[]{4, 64, 256}) {
            BufferedImage image = new BufferedImage(104 * 8, 60 * 8, BufferedImage.TYPE_INT_ARGB);
            for (int z = 0; z < 60; z++) for (int x = 0; x < 104; x++) {
                int[] pixels = FantasyMapStyle.paint(fixture(x, z), fixture(x - 1, z), fixture(x, z - 1),
                        fixture(x + 1, z), fixture(x, z + 1), scale, true, 1);
                image.setRGB(x * 8, z * 8, 8, 8, pixels, 0, 8);
            }
            int[] atlasPixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            FantasyMapAtlas.shoreline(atlasPixels, image.getWidth(), image.getHeight(), 0, 0, FantasyMapPreviewTest::fixture);
            FantasyMapAtlas.overlay(atlasPixels, image.getWidth(), image.getHeight(), 0, 0, scale, FantasyMapPreviewTest::fixture);
            image.setRGB(0, 0, image.getWidth(), image.getHeight(), atlasPixels, 0, image.getWidth());
            Path output = directory.resolve("asset-terrain-detail-" + scale + ".png");
            assertTrue(ImageIO.write(image, "PNG", output.toFile()));
            assertTrue(Files.size(output) > 10000);
        }
    }

    @Test void exportTheRouteMapsActualViewportAspect() throws Exception {
        int width = 104 * 8, height = 45 * 8;
        int[] pixels = new int[width * height];
        for (int z = 0; z < 45; z++) for (int x = 0; x < 104; x++)
            FantasyMapStyle.paintInto(pixels, z * 8 * width + x * 8, width, fixture(x, z),
                    fixture(x - 1, z), fixture(x, z - 1), fixture(x + 1, z), fixture(x, z + 1), 4, true, 1);
        FantasyMapAtlas.shoreline(pixels, width, height, 0, 0, FantasyMapPreviewTest::fixture);
        FantasyMapAtlas.overlay(pixels, width, height, 0, 0, 4, FantasyMapPreviewTest::fixture);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        Path output = Path.of("build/reports/fantasy-map/native-aspect-near.png");
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(image, "PNG", output.toFile()));
    }
}
