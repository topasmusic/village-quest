package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.RewardPayloads;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class VanillaRewardScreenRenderTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void rewardPanelDoesNotBlurAgainAfterNativeScreenBackground() throws Exception {
        var data=new RewardPayloads.View(UUID.randomUUID(),"daily:SMITH_SMELTING",
                List.of(new RewardPayloads.Choice("supplies",List.of(Component.literal("16 iron ingots"))),
                        new RewardPayloads.Choice("coins",List.of(Component.literal("12 Silvermarks")))),"supplies");
        try (var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=spy(new VanillaRewardScreen(data,null));
            screen.width=512; screen.height=274;
            var font=mock(Font.class); var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
            var extractor=mock(GuiGraphicsExtractor.class);
            when(extractor.pose()).thenReturn(new Matrix3x2fStack(16));
            var frame=new GuiRenderState();
            // Model Minecraft's native background pass using the actual single-blur render-state guard.
            doAnswer(call -> { frame.blurBeforeThisStratum(); return null; }).when(screen).extractBackground(any(GuiGraphicsExtractor.class),anyInt(),anyInt(),anyFloat());
            doAnswer(call -> { frame.blurBeforeThisStratum(); return null; }).when(screen).renderBackground(any(GuiGraphics.class),anyInt(),anyInt(),anyFloat());
            try (var theme=mockStatic(VillageUiTheme.class); var tour=mockStatic(UiTestTour.class)) {
                assertDoesNotThrow(() -> screen.extractRenderStateWithTooltipAndSubtitles(extractor,256,137,0));
                String titleText=screen.getTitle().getString();
                theme.verify(() -> VillageUiTheme.drawTitle(any(GuiGraphics.class), eq(font),
                        eq(titleText), eq("quests"), anyInt(), anyInt(), eq(288)));
                verify(screen).extractBackground(any(GuiGraphicsExtractor.class),anyInt(),anyInt(),anyFloat());
                verify(screen,never()).renderBackground(any(GuiGraphics.class),anyInt(),anyInt(),anyFloat());
            }
        }
    }
}
