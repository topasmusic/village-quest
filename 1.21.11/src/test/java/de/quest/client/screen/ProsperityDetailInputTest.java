package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

final class ProsperityDetailInputTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"services","collection"})
    void lastDetailLineCanBeReachedWithoutMovingTheFixedAction(String section) throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var entry=new Payloads.EconomyEntryData("service:test","collection",Component.literal("Title"),Component.literal("Status"),
                    List.of(Component.literal("Long description ".repeat(80)),Component.literal("FINAL LINE")),1,Component.literal("Action"),true,false);
            var screen=new ProsperityScreen(new Payloads.EconomyPayload(0,10,List.of(),List.of(
                    new Payloads.EconomySectionData(section,Component.literal("Services"),"collection",List.of(entry)))));
            screen.width=600;screen.height=400;
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            theme.when(() -> VillageUiTheme.wrapScaledLines(eq(font),anyString(),anyInt(),anyFloat())).thenCallRealMethod();
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(8));
            for(int i=0;i<200;i++) assertTrue(screen.mouseScrolled(380,215,0,-1));
            screen.render(g,0,0,0);
            var scroll=ProsperityScreen.class.getDeclaredField("detailScroll");scroll.setAccessible(true);assertTrue(scroll.getInt(screen)>0);
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(g),eq(font),eq("FINAL LINE"),anyFloat(),
                    floatThat(y -> y>=189 && y<(section.equals("collection")?240:262)),anyInt(),anyFloat()));
            theme.verify(() -> VillageUiTheme.drawButton(eq(g),eq(font),eq(344),eq(section.equals("collection")?245:267),eq(194),eq(18),eq("Action"),eq(true),anyBoolean(),eq(true)));
        }
    }
}
