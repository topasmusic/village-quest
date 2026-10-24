package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.network.Payloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class JournalV3NavigationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    private static Object field(JournalScreen s,String name)throws Exception {
        var f=JournalScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(s);
    }
    private static JournalScreen screen(int w,int h)throws Exception {
        var c=Component.literal("Saved live quest");
        var d=new JournalScreenData(10,5,2,5,13,0,0,0,0,0,false,false,false,false,false,false,
            true,c,c,false,c,c,false,c,c,false,c,c,false,c,c,false,false,false,false,false,false,false,false,
            List.of(),Component.literal("Village"),new Payloads.NetworkSummaryData(0,0,0,c,c,c),List.of(),List.of());
        var s=new JournalScreen(d);s.width=w;s.height=h;
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
        var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(s,font);return s;
    }
    private static void click(JournalScreen s,int x,int y)throws Exception {
        int w=(int)field(s,"WINDOW_WIDTH"),h=(int)field(s,"WINDOW_HEIGHT");
        double scale=1/((CompatScreen)s).responsiveDrag(1,w,h);
        assertTrue(s.mouseClicked(new MouseButtonEvent(s.width/2.0+(x-w/2.0)*scale,
            s.height/2.0+(y-h/2.0)*scale,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false),"click "+x+","+y+" at "+s.width+"x"+s.height);
    }
    @Test void sixLabeledDestinationsAndFourHomeShortcutsUseRealExistingPages()throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            var mc=mock(Minecraft.class);when(mc.getSoundManager()).thenReturn(mock(net.minecraft.client.sounds.SoundManager.class));
            client.when(Minecraft::getInstance).thenReturn(mc);
            for(var size:new int[][]{{800,500},{480,270},{320,200}}) {
                var s=screen(size[0],size[1]);
                assertTrue((int)field(s,"TAB_WIDTH")>=68,"V3 navigation needs space for icon AND translated label");
                click(s,60,152);assertEquals("NETWORK",field(s,"section").toString());assertEquals(1,field(s,"networkPage"));
                click(s,60,55);assertEquals("OVERVIEW",field(s,"section").toString());
                click(s,200,161);assertEquals("ATLAS",field(s,"section").toString(),"Discoveries opens the live atlas");
                click(s,60,55);click(s,327,161);assertEquals("NETWORK",field(s,"section").toString());assertEquals(1,field(s,"networkPage"));
                click(s,60,55);click(s,410,248);assertEquals(2,field(s,"networkPage"),"Chronicle opens existing history data");
            }
            tour.verify(()->UiTestTour.send(any()),never());
        }
    }
}
