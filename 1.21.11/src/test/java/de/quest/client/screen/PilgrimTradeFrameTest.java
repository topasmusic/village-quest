package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class PilgrimTradeFrameTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void innerCardsHaveCompleteBoundsAndGoodsControlIsAboveViewport() throws Exception {
        try (var client = mockStatic(Minecraft.class); var theme = mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen = new PilgrimTradeScreen(new PilgrimTradeScreen.PilgrimTradeData(1,Component.literal("Pilgrim"),0,1000,List.of(),List.of()));
            var font = mock(Font.class); var field = Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen,font);
            var graphics = mock(GuiGraphics.class); when(graphics.pose()).thenReturn(new Matrix3x2fStack(16));
            var frames = new ArrayList<int[]>();
            theme.when(() -> VillageUiTheme.drawCard(eq(graphics),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean(),anyBoolean()))
                    .thenAnswer(call -> { frames.add(new int[]{call.getArgument(1),call.getArgument(2),call.getArgument(3),call.getArgument(4)}); return null; });
            var board = PilgrimTradeScreen.class.getDeclaredMethod("drawBoard",GuiGraphics.class,int.class,int.class); board.setAccessible(true); board.invoke(screen,graphics,0,0);
            assertEquals(3,frames.size(),"Both list and both detail areas need complete runtime card frames");
            for (var frame : frames) { assertTrue(frame[0] >= 16); assertTrue(frame[0] + frame[2] <= 536); assertTrue(frame[1] + frame[3] <= 270); }
            var header = PilgrimTradeScreen.class.getDeclaredMethod("drawHeader",GuiGraphics.class,int.class,int.class,int.class,int.class); header.setAccessible(true); header.invoke(screen,graphics,0,0,0,0);
            var headingY = org.mockito.ArgumentCaptor.forClass(Integer.class);
            theme.verify(()->VillageUiTheme.drawNavigationTab(eq(graphics),eq(font),anyInt(),headingY.capture(),eq(112),eq(18),
                    eq(Component.translatable("screen.village-quest.pilgrim.goods").getString()),eq(true),anyBoolean(),eq(true)));
            assertTrue(headingY.getAllValues().stream().allMatch(y -> y + 18 < frames.getFirst()[1]),"Goods navigation must clear the content viewport");
        }
    }
}
