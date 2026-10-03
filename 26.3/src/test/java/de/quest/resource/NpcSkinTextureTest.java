package de.quest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class NpcSkinTextureTest {
    @Test
    void activeNpcsUseTheApprovedTopasMusicHeadArt() throws IOException {
        for (String npc : new String[]{"pilgrim", "quest_master", "traitor"}) {
            String headPath = "assets/village-quest/skin-head-sources/" + npc + "_head.png";
            String skinPath = "assets/village-quest/textures/entity/" + npc + ".png";
            try (var headStream = getClass().getClassLoader().getResourceAsStream(headPath);
                 var skinStream = getClass().getClassLoader().getResourceAsStream(skinPath)) {
                assertNotNull(headStream, headPath);
                assertNotNull(skinStream, skinPath);
                BufferedImage head = ImageIO.read(headStream);
                BufferedImage skin = ImageIO.read(skinStream);
                assertNotNull(head, headPath);
                assertNotNull(skin, skinPath);
                assertEquals(64, skin.getWidth(), skinPath);
                assertEquals(64, skin.getHeight(), skinPath);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 64; x++) {
                        assertEquals(head.getRGB(x, y), skin.getRGB(x, y),
                                skinPath + " head UV at " + x + "," + y);
                    }
                }
            }
        }
    }
}
