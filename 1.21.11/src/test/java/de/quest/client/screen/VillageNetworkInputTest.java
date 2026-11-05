package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.network.Payloads;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;

/** Real hit testing and report pagination; no world, renderer, server or simulated gameplay. */
final class VillageNetworkInputTest {
    @Test void readOnlyMapCannotSendRouteMutationsEvenWithRegisteredRoutes() throws Exception {
        try(var client=mockStatic(Minecraft.class); var network=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var route=mock(Payloads.TradeRouteLineData.class); when(route.routeIndex()).thenReturn(0);
            var payload=new Payloads.TradeRouteMapPayload(0,Component.empty(),Component.empty(),List.of(),List.of(route),List.of(),List.of(),List.of(),List.of(),false);
            var screen=new TradeRouteMapScreen(payload); screen.width=600; screen.height=450;
            click(screen,150,73,600,450);
            screen.handleMouseClicked(new MouseButtonEvent(84+218,72+141,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            network.verify(() -> UiTestTour.send(any()),never());
        }
    }
    @Test void disconnectedActiveVillageDoesNotClaimItWasPreviouslyTrading() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var bond=new Payloads.TradeRouteBondData(0,10,20,Component.literal("Home"),Component.literal("Known"),Component.empty(),0,
                    Component.empty(),Component.empty(),0,0,0,false,Component.empty(),List.of());
            var screen=new TradeRouteMapScreen(new Payloads.TradeRouteMapPayload(0,Component.empty(),Component.empty(),List.of(),List.of(),List.of(),List.of(bond),List.of(),List.of()));
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*5);
            var ff=Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(screen,font);
            for(var entry:Map.of("villageX",10,"villageZ",20).entrySet()) { var f=TradeRouteMapScreen.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(screen,entry.getValue()); }
            String report=String.join(" ",screen.villageLines());
            assertFalse(report.contains("screen.village-quest.trade_route.bond_historical_detail"));
            assertTrue(report.contains("screen.village-quest.trade_route.bond_not_connected"));
        }
    }
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private static Object field(Object target,String name) throws Exception {
        var f=TradeRouteMapScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void click(TradeRouteMapScreen screen,int x,int y,int w,int h) {
        float scale=.96096f*Math.max(.25f,Math.min(1f,Math.min(w*.82f/432,h*.86f/326)));
        double px=w/2f+(x-216)*scale,py=h/2f+(y-163)*scale;
        assertTrue(screen.mouseClicked(new MouseButtonEvent(px,py,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false));
    }
    @Test void secondColumnAndLastPageOpenTheVisibleVillageAtSmallAndLargeGuiScales() throws Exception {
        for(int[] size:List.of(new int[]{600,450},new int[]{320,240})) {
            try(var client=mockStatic(Minecraft.class); var network=mockStatic(UiTestTour.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var details=new ArrayList<Component>();
                for(int i=0;i<20;i++) details.add(Component.literal("Saved contract "+i));
                details.add(Component.literal("W".repeat(96)+" final-repair-word"));
                var bonds=new ArrayList<Payloads.TradeRouteBondData>();
                for(int i=0;i<9;i++) bonds.add(new Payloads.TradeRouteBondData(i,100+i,200,Component.literal("Village "+i),
                        Component.literal("Trusted"),Component.literal("Bread"),3,Component.literal("Stable"),Component.literal("Food"),40,1,0,true,Component.literal("Contact"),details));
                var payload=new Payloads.TradeRouteMapPayload(0,Component.empty(),Component.empty(),List.of(),List.of(),List.of(),bonds,List.of(),List.of());
                var screen=new TradeRouteMapScreen(payload); screen.width=size[0]; screen.height=size[1];
                var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).codePointCount(0,((String)c.getArgument(0)).length())*7);
                var f=Screen.class.getDeclaredField("font"); f.setAccessible(true); f.set(screen,font);
                click(screen,240,56,size[0],size[1]); // village tab
                click(screen,240,88,size[0],size[1]); // top of second column, village 4
                assertTrue((boolean)field(screen,"villageOpen")); assertEquals(104,field(screen,"villageX"));
                var lines=screen.villageLines();
                assertTrue(String.join(" ",lines).contains("final-repair-word"));
                assertEquals(96,lines.stream().flatMapToInt(String::chars).filter(c -> c=='W').count());
                assertTrue(lines.stream().allMatch(s -> font.width(s)*.8f<=350));
                assertTrue(lines.size()>13);
                screen.onClose(); assertFalse((boolean)field(screen,"villageOpen"));
                click(screen,380,262,size[0],size[1]); // next village page
                click(screen,55,88,size[0],size[1]);
                assertEquals(108,field(screen,"villageX"));
                network.verify(() -> UiTestTour.send(any()),never());
                network.verify(() -> UiTestTour.command(anyString()),never());
            }
        }
    }
}
