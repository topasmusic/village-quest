package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.commands.UiTestTourSteps;
import de.quest.network.Payloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

final class MapFamilyInputTest {
    private static Object get(Object screen,String name) throws Exception {
        var field=screen.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(screen);
    }
    private static void set(Object screen,String name,Object value) throws Exception {
        var field=screen.getClass().getDeclaredField(name);field.setAccessible(true);field.set(screen,value);
    }
    @Test void guildNodeDetailsPersistAndTheirLastLineScrollsWithoutDraggingTheMap() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var node=new Payloads.GuildPathNodeData("ledger",ItemStack.EMPTY,Component.literal("Guild ledger"),
                    Component.literal("Ability ".repeat(50)),Component.literal("Requirement ".repeat(50)+"END_MARKER"),1);
            var screen=new GuildPathScreen(new Payloads.GuildPathPayload(-1,240,List.of(node)));
            screen.width=600;screen.height=400;
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
            var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
            theme.when(() -> VillageUiTheme.wrapScaledLines(eq(font),anyString(),anyInt(),anyFloat())).thenCallRealMethod();
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(12));
            screen.render(g,0,0,0);screen.mouseMoved(1,1);assertEquals(0,get(screen,"selected"));
            screen.updateData(new Payloads.GuildPathPayload(-1,220,List.of(node)));assertEquals(0,get(screen,"selected"));
            int detailTop=(screen.height-(int)get(screen,"HEIGHT"))/2+(int)get(screen,"MAP_Y");
            int detailBottom=detailTop+(int)get(screen,"DETAIL_HEIGHT")-8;
            assertTrue(screen.mouseClicked(new MouseButtonEvent(400,200,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false));
            assertFalse((boolean)get(screen,"mapDragging"));
            for(int i=0;i<100;i++) assertTrue(screen.mouseScrolled(400,200,0,-1));
            screen.render(g,0,0,0);assertTrue((int)get(screen,"detailScroll")>0);
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(g),eq(font),argThat(s -> s!=null && s.endsWith("END_MARKER")),
                    anyFloat(),floatThat(y -> y>=detailTop+58 && y+8<=detailBottom),anyInt(),eq(.88f)));
            tour.verify(() -> UiTestTour.send(any()),never());
        }
    }
    @Test void wayshrineRenameDoesNotScrollTheCoveredMapOrDestinationList() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=(WayshrineScreen)UiTestTourScreens.create(new UiTestTourSteps.Step("wayshrine","owner"));
            screen.width=600;screen.height=400;set(screen,"renaming",true);
            var zoom=get(screen,"zoomLevel");var scroll=get(screen,"listScroll");
            assertTrue(screen.mouseScrolled(200,200,0,1));assertTrue(screen.mouseScrolled(410,200,0,-1));
            assertEquals(zoom,get(screen,"zoomLevel"));assertEquals(scroll,get(screen,"listScroll"));
            tour.verify(() -> UiTestTour.send(any()),never());
        }
    }
}
