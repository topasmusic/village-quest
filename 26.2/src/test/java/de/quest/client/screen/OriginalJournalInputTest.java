package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.network.Payloads;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class OriginalJournalInputTest {
    @org.junit.jupiter.api.BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    private static JournalScreenData data() {
        var c=Component.literal("Saved status");
        var village=new Payloads.NetworkVillageData(42,Component.literal("Rivermead"),c,c,"stable",c,1,2,3,false,UUID.randomUUID(),
                List.of(c),new Payloads.JournalVillageStats(4,2,1,3));
        return new JournalScreenData(10,5,2,1,13,0,0,0,0,0,
                false,false,false,false,false,false,
                false,c,c,false,c,c,false,c,c,false,c,c,false,c,c,
                false,false,false,false,false,false,false,false,List.of(),c,
                new Payloads.NetworkSummaryData(1,2,3,c,c,c),List.of(village),List.of(c));
    }
    private static Object get(JournalScreen s,String name) throws Exception {var f=JournalScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(s);}
    private static void click(JournalScreen s,int x,int y) {
        double scale=1/s.responsiveDrag(1,520,294);
        assertTrue(s.mouseClicked(new MouseButtonEvent(s.width/2.0+(40+x-s.width/2.0)*scale,
                s.height/2.0+(78+y-s.height/2.0)*scale,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false));
    }
    @Test void originalTabsOpenCardsAndTrustWithoutTheDashboard() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            var mc=mock(Minecraft.class);when(mc.getSoundManager()).thenReturn(mock(net.minecraft.client.sounds.SoundManager.class));
            client.when(Minecraft::getInstance).thenReturn(mc);
            var screen=new JournalScreen(data());screen.width=600;screen.height=450;
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*5);
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            assertEquals("OVERVIEW",get(screen,"section").toString());
            click(screen,60,215);assertEquals("GUIDE",get(screen,"section").toString());
            click(screen,60,118);assertEquals("NETWORK",get(screen,"section").toString());
            assertEquals("network_next",get(screen,"expandedCardId"));
            click(screen,240,50);assertEquals(1,get(screen,"networkPage"));
            click(screen,335,50);assertEquals(2,get(screen,"networkPage"));
            click(screen,60,185);assertEquals("ATLAS",get(screen,"section").toString());
            tour.verify(() -> UiTestTour.send(any()),never());
        }
    }
    @Test void originalVillageCardsKeepTheSaved26DetailsAndCanScroll() throws Exception {
        var card=new JournalContentBuilder(data()).networkCards().stream().filter(c->c.id().equals("network_village_42")).findFirst().orElseThrow();
        assertEquals("Rivermead",card.title().getString());
        assertTrue(card.details().stream().anyMatch(c->c.getString().equals("Saved status")),"2.6 last-known details must be retained");
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);var drawing=mockStatic(de.quest.client.ui.VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            drawing.when(() -> de.quest.client.ui.VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenCallRealMethod();
            var screen=new JournalScreen(data());screen.width=600;screen.height=450;
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).length()*5);
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            screen.selectTourSection("network");
            var expanded=JournalScreen.class.getDeclaredField("expandedCardId");expanded.setAccessible(true);expanded.set(screen,"network_village_42");
            var draw=JournalScreen.class.getDeclaredMethod("drawCards",de.quest.client.compat.GuiGraphics.class,int.class,int.class,int.class,int.class);draw.setAccessible(true);
            draw.invoke(screen,mock(de.quest.client.compat.GuiGraphics.class),40,78,0,0);
            assertTrue((int)get(screen,"scrollMax")>0);
            assertTrue(screen.mouseScrolled(40+150,78+100,0,-1));
            assertTrue((int)get(screen,"scrollOffset")>0);
        }
    }
}
