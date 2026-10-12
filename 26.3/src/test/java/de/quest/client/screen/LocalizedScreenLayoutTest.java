package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

final class LocalizedScreenLayoutTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static void font(Screen screen, Font font) throws Exception {
        var field = Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen, font);
        var height = Font.class.getDeclaredField("lineHeight"); height.setAccessible(true); height.setInt(font,9);
    }
    private static Object invoke(Object screen, String name, Class<?>[] types, Object... args) throws Exception {
        var method = screen.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(screen, args);
    }
    private static GuiGraphics graphics() {
        var graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new Matrix3x2fStack(32));
        return graphics;
    }
    private static PilgrimTradeScreen pilgrim(String title, String description) {
        var contract = new PilgrimTradeScreen.PilgrimContractView("fangs_by_the_hedgerow", Component.literal(title),
                Component.literal("Available"), List.of(Component.literal(description)), List.of(), List.of(),
                Component.literal("Accept"), true, ItemStack.EMPTY);
        return new PilgrimTradeScreen(new PilgrimTradeScreen.PilgrimTradeData(-1, Component.empty(), 0, 1200,
                List.of(), List.of(contract)));
    }
    @Test void germanAndEnglishContractSelectorClearsBothHeaderCardRims() throws Exception {
        try (var client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for (String title : List.of("Fangs by the Hedgerow", "Fangzähne am Heckenrand")) {
                var screen = pilgrim(title, "");
                var font = mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*5);
                font(screen, font);
                var graphics = graphics();
                int top = (int) invoke(screen, "getContractOptionsY", new Class<?>[]{int.class}, 0);
                assertTrue(top >= 46 + 6, "Selector must clear the top rim");
                assertTrue(top + 20 <= 46 + 34 - 6, "Selector must clear the bottom rim too");
                invoke(screen, "drawContractOptions", new Class<?>[]{GuiGraphics.class,int.class,int.class,int.class,int.class}, graphics,0,0,0,0);
                assertEquals(0, invoke(screen, "getHoveredContractIndex", new Class<?>[]{int.class,int.class,int.class,int.class}, 180,top+8,0,0));
            }
        }
    }
    @Test void pilgrimOnlyDrawsCompleteDescriptionLinesInsideItsScrollableBody() throws Exception {
        try (var client = mockStatic(Minecraft.class); var theme = mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen = pilgrim("Fangs", "One two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty twentyone twentytwo twentythree twentyfour twentyfive twentysix");
            var font = mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*7);
            font(screen, font); var graphics = graphics();
            invoke(screen, "drawContractDetail", new Class<?>[]{GuiGraphics.class,int.class,int.class,int.class,int.class}, graphics,0,0,0,0);
            var top = ArgumentCaptor.forClass(Integer.class); var bottom = ArgumentCaptor.forClass(Integer.class);
            verify(graphics).enableScissor(anyInt(),top.capture(),anyInt(),bottom.capture());
            var labels = ArgumentCaptor.forClass(String.class); var y = ArgumentCaptor.forClass(Integer.class);
            verify(graphics,atLeastOnce()).drawString(eq(font),labels.capture(),anyInt(),y.capture(),anyInt(),eq(false));
            for (int i=0;i<labels.getAllValues().size();i++) {
                if (labels.getAllValues().get(i).equals("Fangs") || labels.getAllValues().get(i).equals("Accept")) continue;
                float scale = labels.getAllValues().get(i).equals("Available") ? .82f : .85f;
                int actualTop = Math.round(y.getAllValues().get(i)*scale);
                assertTrue(actualTop >= top.getValue(), "A top-clipped line must wait until scrolled fully into view");
                assertTrue(actualTop + Math.round((font.lineHeight+2)*scale) <= bottom.getValue(), "No half glyphs at the bottom edge");
            }
        }
    }
    @Test void atlasDisplaysEveryWordOfLongProjectEffectsAndChronicles() throws Exception {
        try (var client = mockStatic(Minecraft.class); var theme = mockStatic(VillageUiTheme.class, CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen = new JournalScreen(null);
            var font = mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
            font(screen,font); var graphics = graphics();
            Class<?> nodeType = Class.forName(JournalScreen.class.getName()+"$AtlasNode");
            Class<?> landmarkType = Class.forName(JournalScreen.class.getName()+"$Landmark");
            var ctor = nodeType.getDeclaredConstructor(String.class,ItemStack.class,Component.class,Component.class,Component.class,int.class,landmarkType);
            ctor.setAccessible(true);
            String effect = "Manage five routes, survey real detours, watch caravans, respond to incidents and follow the entire village network on its live map.";
            String chronicle = "A permanent caravan yard turns the village into the heart of a living road network and keeps its people connected.";
            var node = ctor.newInstance("caravan_yard",ItemStack.EMPTY,Component.literal("Caravan Yard"),Component.literal(effect),Component.literal(chronicle),0,null);
            var hovered = JournalScreen.class.getDeclaredField("atlasHoveredNode"); hovered.setAccessible(true); hovered.setInt(screen,0);
            var placementType = Class.forName(JournalScreen.class.getName()+"$AtlasDetailPlacement");
            var place = placementType.getDeclaredConstructor(int.class,int.class); place.setAccessible(true);
            invoke(screen,"drawAtlasDetail",new Class<?>[]{GuiGraphics.class,List.class,placementType},graphics,List.of(node),place.newInstance(80,52));
            var text = ArgumentCaptor.forClass(String.class);
            var x = ArgumentCaptor.forClass(Float.class); var y = ArgumentCaptor.forClass(Float.class);
            var scale = ArgumentCaptor.forClass(Float.class);
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),text.capture(),x.capture(),y.capture(),anyInt(),scale.capture()),atLeastOnce());
            var effectLines = new ArrayList<String>(); var chronicleLines = new ArrayList<String>();
            for (int i=0;i<text.getAllValues().size();i++) {
                if (scale.getAllValues().get(i)==.44f) effectLines.add(text.getAllValues().get(i));
                if (scale.getAllValues().get(i)==.42f) chronicleLines.add(text.getAllValues().get(i));
            }
            assertEquals(effect,String.join(" ",effectLines),"Project effect must not end in ellipsis");
            assertEquals(chronicle,String.join(" ",chronicleLines),"Chronicle must not end in ellipsis");
        }
    }
    @Test void firstFavorSubtitleUsesTheAvailableHeaderWidth() throws Exception {
        try (var client = mockStatic(Minecraft.class); var theme = mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            String subtitle = "Wähle deinen ersten Gefallen";
            var entry = new QuestMasterScreen.EntryView("first","daily",Component.literal("Backstubenhilfe"),
                    Component.literal(subtitle),Component.literal("Verfügbar"),false,Component.empty(),
                    List.of(),List.of(),List.of(),0,Component.empty(),false,0,Component.empty(),false,false);
            var screen = new QuestMasterScreen(new QuestMasterScreen.QuestMasterData(-1,Component.empty(),List.of(),List.of(entry),null,0));
            var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
            font(screen,font); var graphics=graphics();
            invoke(screen,"drawDetailHeader",new Class<?>[]{GuiGraphics.class,int.class,int.class,QuestMasterScreen.EntryView.class},graphics,0,0,entry);
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),eq(subtitle),eq(12f),eq(21f),anyInt(),eq(.68f)));
        }
    }
    @Test void everyLocalizedProjectCardFitsAboveTheAtlasFooter() throws Exception {
        try (var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new JournalScreen(null);
            var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
            font(screen,font);
            Class<?> nodeType=Class.forName(JournalScreen.class.getName()+"$AtlasNode");
            Class<?> landmarkType=Class.forName(JournalScreen.class.getName()+"$Landmark");
            var ctor=nodeType.getDeclaredConstructor(String.class,ItemStack.class,Component.class,Component.class,Component.class,int.class,landmarkType);
            ctor.setAccessible(true);
            for (String locale:List.of("en_us","de_de","es_es")) {
                try (var stream=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")) {
                    assertNotNull(stream);
                    var catalog=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    for (var entry:catalog.entrySet()) {
                        if (!entry.getKey().startsWith("quest.village-quest.project.") || !entry.getKey().endsWith(".effect")) continue;
                        String prefix=entry.getKey().substring(0,entry.getKey().length()-7);
                        for (String body:List.of(".description",".memory")) {
                            var node=ctor.newInstance(prefix,ItemStack.EMPTY,Component.literal(catalog.get(prefix+".title").getAsString()),
                                    Component.literal(entry.getValue().getAsString()),Component.literal(catalog.get(prefix+body).getAsString()),0,null);
                            int height=(int)invoke(screen,"atlasDetailHeight",new Class<?>[]{nodeType},node);
                            assertTrue(52+height<=234-32,locale+" "+prefix+body+" overlaps the Atlas footer: "+height);
                        }
                    }
                }
            }
        }
    }
    @Test void lastPilgrimDescriptionLineIsReachableAtMaximumScroll() throws Exception {
        try (var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=pilgrim("Fangs","long description ".repeat(80)+"LASTLINE");
            var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*7);
            font(screen,font);
            var graphics=graphics();
            var args=new Class<?>[]{GuiGraphics.class,int.class,int.class,int.class,int.class};
            invoke(screen,"drawContractDetail",args,graphics,0,0,0,0);
            var maximum=PilgrimTradeScreen.class.getDeclaredField("contractScrollMax"); maximum.setAccessible(true);
            var offset=PilgrimTradeScreen.class.getDeclaredField("contractScrollOffset"); offset.setAccessible(true);
            assertTrue(maximum.getInt(screen)>0);
            offset.setInt(screen,maximum.getInt(screen));
            clearInvocations(graphics);
            invoke(screen,"drawContractDetail",args,graphics,0,0,0,0);
            var labels=ArgumentCaptor.forClass(String.class);
            verify(graphics,atLeastOnce()).drawString(eq(font),labels.capture(),anyInt(),anyInt(),anyInt(),eq(false));
            assertTrue(labels.getAllValues().stream().anyMatch(text -> text.contains("LASTLINE")),"Final line must be fully visible at maximum scroll");
        }
    }

    @Test void everyLocalizedPilgrimContractReachesItsFinalLineAtNarrowAndWideFontMetrics() throws Exception {
        try (var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for (String locale:List.of("en_us","de_de","es_es")) {
                try (var stream=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")) {
                    assertNotNull(stream);
                    var catalog=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    int checked=0;
                    for (var entry:catalog.entrySet()) {
                        String key=entry.getKey();
                        if (!key.startsWith("quest.village-quest.pilgrim.contract.") || !key.endsWith(".offer.1")) continue;
                        String prefix=key.substring(0,key.length()-8);
                        String title=catalog.get(prefix+".title").getAsString();
                        String body=entry.getValue().getAsString()+" "+catalog.get(prefix+".offer.2").getAsString()+" QA_FINAL_LINE";
                        for (int width:List.of(5,7,9)) {
                            var screen=pilgrim(title,body);
                            var font=mock(Font.class);
                            when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length()*width);
                            font(screen,font);
                            var graphics=graphics();
                            var args=new Class<?>[]{GuiGraphics.class,int.class,int.class,int.class,int.class};
                            invoke(screen,"drawContractDetail",args,graphics,0,0,0,0);
                            var maximum=PilgrimTradeScreen.class.getDeclaredField("contractScrollMax"); maximum.setAccessible(true);
                            var offset=PilgrimTradeScreen.class.getDeclaredField("contractScrollOffset"); offset.setAccessible(true);
                            offset.setInt(screen,maximum.getInt(screen));
                            clearInvocations(graphics);
                            invoke(screen,"drawContractDetail",args,graphics,0,0,0,0);
                            var labels=ArgumentCaptor.forClass(String.class);
                            verify(graphics,atLeastOnce()).drawString(eq(font),labels.capture(),anyInt(),anyInt(),anyInt(),eq(false));
                            assertTrue(labels.getAllValues().stream().anyMatch(text -> text.contains("QA_FINAL_LINE")),
                                    locale+" "+key+" font width="+width+": final line unreachable");
                        }
                        checked++;
                    }
                    assertTrue(checked>0,"The locale fixture must actually cover Pilgrim contracts");
                }
            }
        }
    }
}
