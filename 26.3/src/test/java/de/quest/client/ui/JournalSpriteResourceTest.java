package de.quest.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

final class JournalSpriteResourceTest {
    @Test void semanticFamilyIconsResolveToPackagedTexturesIncludingPilgrimRumor() {
        var graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new Matrix3x2fStack(12));
        doAnswer(call -> {
            Identifier texture = call.getArgument(1);
            try (var stream = getClass().getClassLoader().getResourceAsStream(
                    "assets/" + texture.toString().replace(':', '/'))) {
                assertNotNull(stream, "Unpackaged UI sprite: " + texture);
            }
            return null;
        }).when(graphics).blit(any(), any(), anyInt(), anyInt(), anyFloat(), anyFloat(),
                anyInt(), anyInt(), anyInt(), anyInt());
        for (String name : new String[]{"book", "scroll", "special", "market", "dispatch", "caravan", "wayshrine", "guildpath"}) {
            JournalV3Visuals.sprite(graphics, name, 0, 0, 24, 24);
        }
    }
}
