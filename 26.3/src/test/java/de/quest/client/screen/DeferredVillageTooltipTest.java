package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

final class DeferredVillageTooltipTest {
    @Test void vqTextHintsUsePaperAtPhysicalPointerAfterThePanelTransformEnds() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
            when(font.split(any(Component.class),anyInt())).thenReturn(List.of(FormattedCharSequence.forward("Full hint",Style.EMPTY)));
            var extractor=mock(GuiGraphicsExtractor.class);var pose=new Matrix3x2fStack(8);
            when(extractor.pose()).thenReturn(pose);when(extractor.guiWidth()).thenReturn(320);when(extractor.guiHeight()).thenReturn(240);
            var screen=new CompatScreen(Component.empty()) {
                @Override public void render(GuiGraphics g,int mx,int my,float delta) {
                    g.pose().pushMatrix();g.pose().scale(.5f,.5f);
                    g.setTooltipForNextFrame(font,Component.literal("Full hint"),9999,9999);
                    g.pose().popMatrix();
                }
            };
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            screen.width=320;screen.height=240;
            theme.when(() -> VillageUiTheme.drawPaper(any(),anyInt(),anyInt(),anyInt(),anyInt())).thenAnswer(c -> {
                assertEquals(1,pose.m00(),.0001);assertEquals(1,pose.m11(),.0001);
                int x=c.getArgument(1),y=c.getArgument(2),w=c.getArgument(3),h=c.getArgument(4);
                assertTrue(x>=4&&x+w<=316);assertTrue(y>=4&&y+h<=236);return null;
            });
            screen.extractRenderState(extractor,300,230,0);
            verify(extractor,never()).setTooltipForNextFrame(any(Font.class),any(Component.class),anyInt(),anyInt());
            theme.verify(() -> VillageUiTheme.drawPaper(any(),anyInt(),anyInt(),anyInt(),anyInt()),times(1));
            theme.verify(() -> VillageUiTheme.drawStringScaled(any(),eq(font),eq("Full hint"),anyFloat(),anyFloat(),eq(VillageUiTheme.INK),anyFloat()));
        }
    }
}
