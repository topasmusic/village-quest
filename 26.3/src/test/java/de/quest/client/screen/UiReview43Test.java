package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.commands.UiTestTourSteps;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class UiReview43Test {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static Font font(Screen screen) throws Exception {
        var font=mock(Font.class);
        when(font.width(anyString())).thenAnswer(a -> ((String)a.getArgument(0)).length()*6);
        var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
        return font;
    }
    @Test void journeyPhaseAppearsOnceAndDistinctPageLabelsRemainOnEveryDetailPage() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=(BuildingScreen)UiTestTourScreens.create(new UiTestTourSteps.Step("building","travel"));
            font(screen);
            var visible=new ArrayList<String>();
            theme.when(() -> VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenAnswer(a -> a.getArgument(1));
            theme.when(() -> VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a -> List.of((String)a.getArgument(1)));
            theme.when(() -> VillageUiTheme.drawStringScaled(any(),any(),anyString(),anyFloat(),anyFloat(),anyInt(),anyFloat()))
                    .thenAnswer(a -> {visible.add(a.getArgument(2));return null;});
            theme.when(() -> VillageUiTheme.drawWrappedScaled(any(),any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),anyFloat(),anyInt()))
                    .thenAnswer(a -> {visible.add(a.getArgument(2));return null;});
            var render=BuildingScreen.class.getDeclaredMethod("renderMarketTravel",GuiGraphics.class,int.class,int.class,int.class,int.class);
            render.setAccessible(true);
            for(int page=0;page<2;page++) {
                visible.clear();render.invoke(screen,mock(GuiGraphics.class),0,0,-1,-1);
                assertEquals(1,visible.stream().filter(s -> s.startsWith("screen.village-quest.building.travel_phase.")).count());
                assertTrue(visible.contains("screen.village-quest.building.travel_detail_pages"));
                assertTrue(visible.contains("screen.village-quest.building.travel_trader_page"));
                var next=mock(KeyEvent.class);when(next.key()).thenReturn(com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN);
                assertTrue(screen.keyPressed(next));
            }
        }
    }
}
