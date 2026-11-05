package de.quest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.CaravanRole;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class CaravanRoleTextureTest {
    @Test
    void crewKeepsTopasMusicHeadAndBeardTransitionForEachLivery() throws IOException {
        for (String livery : new String[]{"burgundy", "forest", "neutral", "ochre", "violet"}) {
            String headSource = "assets/village-quest/skin-head-sources/caravan_" + livery + "_head.png";
            try (var sourceStream = getClass().getClassLoader().getResourceAsStream(headSource)) {
                assertNotNull(sourceStream, "Missing TopasMusic head source: " + headSource);
                BufferedImage source = ImageIO.read(sourceStream);
                assertNotNull(source, "Unreadable TopasMusic head source: " + headSource);
                for (CaravanRole role : CaravanRole.values()) {
                    String name = "assets/village-quest/textures/entity/caravan_" + livery + "_"
                            + role.name().toLowerCase(java.util.Locale.ROOT) + ".png";
                    try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
                        assertNotNull(stream, "Missing crew texture: " + name);
                        BufferedImage crew = ImageIO.read(stream);
                        assertNotNull(crew, "Unreadable crew texture: " + name);
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 64; x++) {
                                assertEquals(source.getRGB(x, y), crew.getRGB(x, y),
                                        name + " head UV at " + x + "," + y);
                            }
                        }
                        for (int x = 20; x < 28; x++) {
                            assertEquals(source.getRGB(x, 20), crew.getRGB(x, 20),
                                    name + " front beard extension at " + x);
                        }
                    }
                }
            }
        }
    }

    @Test
    void supersededFullCaravanTexturesAreAbsent() {
        for (String name : new String[]{"caravan.png", "caravan_burgundy.png",
                "caravan_forest.png", "caravan_ochre.png", "caravan_violet.png"}) {
            String path = "assets/village-quest/textures/entity/" + name;
            assertNull(getClass().getClassLoader().getResource(path), path);
        }
    }

    @Test
    void allLiveryRoleTexturesHaveCompleteWideModelUvFaces() throws IOException {
        for (String livery : new String[]{"burgundy", "forest", "neutral", "ochre", "violet"}) {
            for (CaravanRole role : CaravanRole.values()) {
                String name = "assets/village-quest/textures/entity/caravan_" + livery + "_"
                        + role.name().toLowerCase(java.util.Locale.ROOT) + ".png";
                try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
                    assertNotNull(stream, "Missing crew texture: " + name);
                    BufferedImage image = ImageIO.read(stream);
                    assertNotNull(image, "Unreadable crew texture: " + name);
                    assertEquals(64, image.getWidth(), name);
                    assertEquals(64, image.getHeight(), name);
                    assertTrue(image.getColorModel().hasAlpha(), name);
                    for (int[] point : new int[][]{
                            {8, 8}, {20, 20}, {44, 20}, {36, 52}, {4, 20}, {20, 52}}) {
                        assertEquals(255, image.getRGB(point[0], point[1]) >>> 24,
                                "Transparent base UV face: " + name);
                    }
                }
            }
        }
    }
}
