package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual parser, pagination and pointer handling; transport/drawing only are stubbed. */
final class AdminJournalInputTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion();Bootstrap.bootStrap(); }
    @Test void oversizedCommandLabelsRemainCompleteAndKeepTheirActionOnEveryWrappedPart() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            String label="Inspect "+"LongIdentifier".repeat(50)+" final";
            var screen=new AdminJournalScreen(List.of("[[CMD:vq admin inspect]]"+label));
            var font=font();set(screen,Screen.class,"font",font);screen.init();
            var text=new StringBuilder();int rows=0;
            for(var page:(List<?>)get(screen,"pages")) for(var line:(List<?>)page) {
                int width=0;
                for(var segment:(List<?>)get(line,"segments")) {
                    String part=(String)get(segment,"text");text.append(part);width+=font.width(part);
                    assertEquals("vq admin inspect",get(segment,"actionValue"));
                }
                assertTrue(width*.8<=364,"Label exceeded paper width");rows++;
            }
            assertTrue(rows>1);assertEquals(label,text.toString());
        }
    }

    @Test void pageTurnInvalidatesOldCommandTargetsBeforeAnotherRenderAtBothGuiSizes() throws Exception {
        for(int[] size:new int[][]{{600,400},{320,240}}) {
            try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var tour=mockStatic(UiTestTour.class)) {
                var mc=mock(Minecraft.class,RETURNS_DEEP_STUBS);mc.player=mock(LocalPlayer.class);
                client.when(Minecraft::getInstance).thenReturn(mc);
                var screen=new AdminJournalScreen(List.of("[[CMD:vq admin first]]First","<<PAGE>>","[[CMD:vq admin second]]Second"));
                screen.width=size[0];screen.height=size[1];set(screen,Screen.class,"font",font());screen.init();
                var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(8));screen.render(g,0,0,0);
                assertTrue(screen.mouseClicked(click(size,38,73),false));
                tour.verify(() -> UiTestTour.command("vq admin first"),times(1));
                assertTrue(screen.mouseClicked(click(size,366,260),false));
                screen.mouseClicked(click(size,38,73),false);
                tour.verify(() -> UiTestTour.command("vq admin first"),times(1));
                tour.verify(() -> UiTestTour.command("vq admin second"),never());
                screen.render(g,0,0,0);assertTrue(screen.mouseClicked(click(size,38,73),false));
                tour.verify(() -> UiTestTour.command("vq admin second"),times(1));
            }
        }
    }
    private static Font font() {
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).codePointCount(0,((String)c.getArgument(0)).length())*6);return font;
    }
    private static MouseButtonEvent click(int[] size,int x,int y) {
        float scale=.96096f*Math.max(.25f,Math.min(1f,Math.min(size[0]*.82f/416,size[1]*.86f/290)));
        var e=mock(MouseButtonEvent.class);when(e.button()).thenReturn(InputConstants.MOUSE_BUTTON_LEFT);
        when(e.x()).thenReturn(size[0]/2.0+(x-208)*scale);when(e.y()).thenReturn(size[1]/2.0+(y-145)*scale);return e;
    }
    private static Object get(Object o,String name) throws Exception { var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o); }
    private static void set(Object o,Class<?> owner,String name,Object value) throws Exception { var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(o,value); }
}
