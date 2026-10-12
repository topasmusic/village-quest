package de.quest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class OptimizedTextureResourceTest {
    private static final String TEXTURES = "assets/village-quest/textures/";

    @Test
    void journalWoodSilhouetteStartsAtWoodInsteadOfBlackExteriorPixels() throws IOException {
        for (String name : List.of("journal_board", "trust_board")) {
            String resource = TEXTURES + "gui/" + name + ".png";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream);
                BufferedImage image = ImageIO.read(stream);
                for (int x = 34; x < image.getWidth() - 34; x++) {
                    if (x < 137 || x >= 280) assertFirstVisiblePixelIsNotBlack(image, x, true);
                    assertFirstVisiblePixelIsNotBlack(image, x, false);
                }
            }
        }
    }

    private static void assertFirstVisiblePixelIsNotBlack(BufferedImage image, int x, boolean top) {
        for (int step = 0; step < 21; step++) {
            int y = top ? step : image.getHeight() - 1 - step;
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) == 0) continue;
            assertTrue((argb >>> 16 & 255) >= 32 || (argb >>> 8 & 255) >= 32 || (argb & 255) >= 32,
                    "Black exterior fringe at " + x + "," + y);
            return;
        }
        throw new AssertionError("Wooden rail missing at column " + x);
    }

    @Test
    void woodenFrameJoinsDoNotExposeTheWorldBehindThePanel() throws IOException {
        record Frame(String name, int side, int top, int bottom, int silhouetteInset) {}
        for (Frame frame : List.of(
                new Frame("journal_board", 20, 30, 21, 10),
                new Frame("trust_board", 20, 30, 21, 10),
                new Frame("guild_notice_board_frame", 8, 14, 13, 7),
                new Frame("guild_atlas_frame", 14, 27, 16, 9),
                new Frame("wayshrine_board", 25, 49, 14, 14),
                new Frame("trade_route_board", 24, 34, 14, 11),
                new Frame("pilgrim_board_v2", 15, 24, 19, 9))) {
            String resource = TEXTURES + "gui/" + frame.name() + ".png";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, () -> "Missing texture: " + resource);
                BufferedImage image = ImageIO.read(stream);
                assertNotNull(image, () -> "Unreadable PNG: " + resource);
                // The trimmed outer silhouette and map opening stay transparent.
                // Inside that silhouette the wooden ring must still cover every join.
                for (int y = frame.silhouetteInset(); y < image.getHeight() - frame.silhouetteInset(); y++) {
                    for (int x = frame.silhouetteInset(); x < image.getWidth() - frame.silhouetteInset(); x++) {
                        if (x < frame.side() || x >= image.getWidth() - frame.side()
                                || y < frame.top() || y >= image.getHeight() - frame.bottom()) {
                            assertEquals(255, image.getRGB(x, y) >>> 24,
                                    resource + " exposes a transparent frame join at " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void atlasBackgroundsUseOptimizedRgbDimensions() throws IOException {
        for (String name : List.of("guild_charters_map", "guild_path_map", "guild_trust_roster")) {
            assertPng(TEXTURES + "gui/" + name + ".png", 1184, 592, false);
        }
    }

    @Test
    void everySurveyorsCompassFrameUsesOptimizedRgbaDimensions() throws IOException {
        Path itemTextures = Path.of(System.getProperty("user.dir"), "src", "main", "resources",
                "assets", "village-quest", "textures", "item");
        try (var files = Files.list(itemTextures)) {
            assertEquals(33L, files
                    .filter(path -> path.getFileName().toString().matches("surveyors_compass(?:_\\d{2})?\\.png"))
                    .count(), "Surveyor's Compass frame count changed");
        }

        assertPng(TEXTURES + "item/surveyors_compass.png", 128, 128, true);
        for (int frame = 0; frame < 32; frame++) {
            assertPng(TEXTURES + "item/surveyors_compass_%02d.png".formatted(frame), 128, 128, true);
        }
    }

    @Test
    void largePaintingAndNewGuiArtUseTheirRuntimeDimensions() throws IOException {
        assertPng(TEXTURES + "painting/apiary_charter_plaque.png", 256, 256, true);
        assertPng(TEXTURES + "gui/guild_notice_board_frame.png", 416, 234, true);
        assertCleanTransparentFrame(TEXTURES + "gui/guild_notice_board_frame.png");
        assertPng(TEXTURES + "gui/guild_notice_board_inner.png", 400, 207, false);
        assertPng(TEXTURES + "gui/charters/village_ledger.png", 32, 32, true);
        for (String icon : List.of("farming", "crafting", "animals", "trade", "road_warden")) {
            assertPng(TEXTURES + "gui/trust/" + icon + ".png", 32, 32, true);
        }
    }

    private static void assertPng(String resource, int width, int height, boolean alpha) throws IOException {
        ClassLoader resources = OptimizedTextureResourceTest.class.getClassLoader();
        try (InputStream stream = resources.getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing texture: " + resource);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, () -> "Unreadable PNG: " + resource);
            assertEquals(width, image.getWidth(), () -> "Wrong width: " + resource);
            assertEquals(height, image.getHeight(), () -> "Wrong height: " + resource);
            assertEquals(alpha, image.getColorModel().hasAlpha(), () -> "Wrong alpha mode: " + resource);
            assertEquals(3, image.getColorModel().getNumColorComponents(),
                    () -> "Texture is not RGB/RGBA: " + resource);
            assertFalse(image.getColorModel() instanceof IndexColorModel,
                    () -> "Texture was palette-quantized: " + resource);
            assertTrue(image.getColorModel().getPixelSize() >= (alpha ? 32 : 24),
                    () -> "Texture color depth was reduced: " + resource);
        }
    }

    private static void assertCleanTransparentFrame(String resource) throws IOException {
        ClassLoader resources = OptimizedTextureResourceTest.class.getClassLoader();
        try (InputStream stream = resources.getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing texture: " + resource);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, () -> "Unreadable PNG: " + resource);
            int transparent = 0;
            int brightNeutralOpaque = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = argb >>> 24;
                    if (alpha == 0) {
                        transparent++;
                        continue;
                    }
                    int red = argb >>> 16 & 0xFF;
                    int green = argb >>> 8 & 0xFF;
                    int blue = argb & 0xFF;
                    int max = Math.max(red, Math.max(green, blue));
                    int min = Math.min(red, Math.min(green, blue));
                    if (max - min <= 20 && min >= 78) {
                        brightNeutralOpaque++;
                    }
                }
            }
            int pixels = image.getWidth() * image.getHeight();
            assertTrue(transparent >= Math.round(pixels * 0.80f),
                    () -> "GUI frame lost its open transparent interior: " + resource);
            assertEquals(0, brightNeutralOpaque,
                    () -> "GUI frame contains a bright neutral matte seam: " + resource);
        }
    }
}
