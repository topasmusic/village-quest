package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonParser;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.kingdom.BuildingScanner;
import de.quest.network.BuildingPayloads;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;

final class KingdomRelationsScreenTest {
    @Test void travelDetailsKeepAllLongNamesReachableWithinTheTextColumn() {
        var font=mock(Font.class); when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).codePointCount(0,((String)call.getArgument(0)).length())*12);
        String name="W".repeat(96); var lines=BuildingScreen.travelLines(font,List.of(name,name,name,"The market closes before the return trip."));
        assertTrue(lines.size()>12,"Long detail is paged rather than cropped at the footer");
        assertEquals(288,lines.stream().mapToLong(s -> s.chars().filter(c -> c=='W').count()).sum());
        assertTrue(lines.stream().allMatch(line -> font.width(line)*.8f<=350));
        assertTrue(String.join(" ",lines).contains("The market closes"));
    }
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private static void set(Object target,Class<?> type,String name,Object value) throws Exception {
        var f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    private static Object get(BuildingScreen screen,String name) throws Exception {
        var f=BuildingScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen);
    }
    private static void render(BuildingScreen screen,GuiGraphics graphics) throws Exception {
        var method=BuildingScreen.class.getDeclaredMethod("renderRelations",GuiGraphics.class,int.class,int.class,int.class,int.class);
        method.setAccessible(true); method.invoke(screen,graphics,92,60,-1,-1);
    }
    private static void click(BuildingScreen screen,int x,int y) {
        double scale=1/screen.responsiveDrag(1,448,366);
        assertTrue(screen.mouseClicked(new MouseButtonEvent(300+(x+92-300)*scale,
                225+(y+84-225)*scale,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false));
    }
    private static KeyEvent key(int value) { var event=mock(KeyEvent.class); when(event.key()).thenReturn(value); return event; }
    private static BuildingPayloads.View view(UUID session,long revision,List<BuildingPayloads.Partner> partners) {
        return new BuildingPayloads.View(session,revision,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,false,false,3,3,true,"Home",List.of(),List.of(),"",
                BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,new BuildingPayloads.Diplomacy(0,1,partners,true));
    }
    private static List<BuildingPayloads.Partner> partners() {
        var empty=new BuildingPayloads.Deal(-1,-1,0,false);
        return java.util.stream.IntStream.range(0,17).mapToObj(i -> new BuildingPayloads.Partner(UUID.randomUUID(),"Village "+i,0,
                List.of(empty,empty,empty),"none",false,new BuildingPayloads.Relation("acquainted","steady",List.of(),"none","none"))).toList();
    }
    @Test void emptyRelationshipsUseOneCardInsteadOfDrawingColumnsAcrossTheMessage() throws Exception {
        try(var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new BuildingScreen(view(UUID.randomUUID(),2,List.of()));
            var font=mock(Font.class);set(screen,Screen.class,"font",font);var graphics=mock(GuiGraphics.class);
            render(screen,graphics);
            // The old two column outlines cut straight through this full-width message.
            verify(graphics,never()).fill(eq(92+26),eq(60+96),eq(92+26+181),eq(60+96+190),anyInt());
            theme.verify(() -> VillageUiTheme.drawWrappedScaled(eq(graphics),eq(font),eq(Component.translatable("screen.village-quest.building.diplomacy_none").getString()),
                    eq(92+42),eq(60+136),eq(330),anyInt(),eq(.8f),eq(5)));
        }
    }
    @Test void graphListAndKeyboardShareSelectionAndReadingSendsNoAgreementMutation() throws Exception {
        try(var client=mockStatic(Minecraft.class); var network=mockStatic(ClientPlayNetworking.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var rows=partners(); var screen=new BuildingScreen(view(UUID.randomUUID(),2,rows)); screen.width=600;screen.height=450;
            var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*5);
            set(screen,Screen.class,"font",font);set(screen,BuildingScreen.class,"relationEditing",true);
            theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1));
            theme.when(() -> VillageUiTheme.wrapScaledLines(eq(font),anyString(),anyInt(),anyFloat())).thenCallRealMethod();
            var graphics=mock(GuiGraphics.class); render(screen,graphics);
            click(screen,180,80); assertEquals(4,get(screen,"partnerIndex"));
            render(screen,graphics); click(screen,280,49); assertEquals(true,get(screen,"relationList"));
            render(screen,graphics); click(screen,31,142); assertEquals(5,get(screen,"partnerIndex"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_TAB))); assertEquals(false,get(screen,"relationList"));
            assertEquals(5,get(screen,"partnerIndex"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_DOWN))); assertEquals(6,get(screen,"partnerIndex"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_PAGEDOWN))); assertEquals(8,get(screen,"partnerIndex"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_PAGEDOWN))); assertEquals(12,get(screen,"partnerIndex"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_PAGEDOWN))); assertEquals(16,get(screen,"partnerIndex"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN))); assertEquals(true,get(screen,"diplomacyEditing"));
            network.verifyNoInteractions();
        }
    }
    @Test void snapshotRefreshKeepsTheSameSelectedVillageRatherThanReassigningItsExplanation() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class)); var rows=partners(); var id=UUID.randomUUID();
            var screen=new BuildingScreen(view(id,2,rows)); set(screen,BuildingScreen.class,"partnerIndex",8);
            screen.updateData(view(id,3,rows.subList(2,rows.size())));
            assertEquals(6,get(screen,"partnerIndex"));
            screen.updateData(view(id,1,rows)); assertEquals(6,get(screen,"partnerIndex"));
        }
    }
    @Test void allThreeLocalesKeepTheLastRepairWordReachableOnExplanationPages() throws Exception {
        var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*7);
        for(String locale:List.of("en_us","de_de","es_es")) {
            try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")),StandardCharsets.UTF_8)) {
                var catalog=JsonParser.parseReader(reader).getAsJsonObject();
                StringBuilder detail=new StringBuilder();
                for(String cause:List.of("trade","supply","friendship","prepared","paused","delivery","aid","loyalty","reconciliation","attack"))
                    detail.append(String.format(catalog.get("screen.village-quest.building.relation_cause."+cause).getAsString(),"Home")).append('\n');
                detail.append(String.format(catalog.get("screen.village-quest.building.relation_repair.attack_freight").getAsString(),"Home","Grain"));
                var lines=BuildingScreen.relationLines(font,detail.toString());
                assertTrue(lines.size()>16,"Fixture must require an actual second explanation page");
                var reached=new ArrayList<String>();
                for(int page=0;page<(lines.size()+15)/16;page++) reached.addAll(lines.subList(page*16,Math.min(lines.size(),(page+1)*16)));
                assertEquals(detail.toString().replaceAll("\\s+"," ").trim(),String.join(" ",reached));
                assertTrue(lines.stream().allMatch(line -> font.width(line)<=160/.72f));
            }
        }
    }
}
