package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.RewardPayloads;
import de.quest.network.VillageNetworkPayloads;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class QuestDialogInteractionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static Object field(Object o, String n) throws Exception {
        var f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);
    }
    private static void set(Object o, String n, Object value) throws Exception {
        var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value);
    }
    private static Font font(Screen screen) throws Exception {
        var f=mock(Font.class); when(f.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
        var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,f);return f;
    }
    @Test void openPartyDrawerOwnsScrollingAndClippedQuestRowsCannotBeClickedThroughTheFooter() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            var mc=mock(Minecraft.class);when(mc.getSoundManager()).thenReturn(mock(net.minecraft.client.sounds.SoundManager.class));
            client.when(Minecraft::getInstance).thenReturn(mc);
            var c=Component.literal("Quest");
            var entries=java.util.stream.IntStream.range(0,8).mapToObj(i -> new QuestMasterScreen.EntryView("e"+i,"daily",c,c,c,
                    true,Component.empty(),List.of(),List.of(),List.of(),0,c,true,0,Component.empty(),false,false)).toList();
            var candidates=java.util.stream.IntStream.range(0,8).mapToObj(i -> new QuestMasterScreen.PartyCandidateView("p"+i,c,c,true)).toList();
            var data=new QuestMasterScreen.QuestMasterData(1,c,List.of(new QuestMasterScreen.CategoryView("daily",c,8)),entries,
                    new QuestMasterScreen.PartyView(true,true,c,List.of(),candidates),0);
            var screen=new QuestMasterScreen(data);screen.width=600;screen.height=450;font(screen);screen.updateData(data);
            set(screen,"partyDrawerOpen",true);set(screen,"detailScrollMax",100);
            var p=pointer(screen,370,140,560,315);assertTrue(screen.mouseScrolled(p.x(),p.y(),0,-1));
            assertEquals(1,field(screen,"partyCandidateScrollIndex"),"Visible drawer must receive the wheel before hidden quest details");
            assertEquals(0,field(screen,"detailScrollOffset"));
            for(int i=0;i<30;i++) screen.mouseScrolled(p.x(),p.y(),0,-1);
            assertTrue(screen.mouseClicked(pointer(screen,370,193,560,315),false));
            tour.verify(() -> UiTestTour.send(new de.quest.network.Payloads.QuestMasterPartyActionPayload(1,
                    de.quest.network.Payloads.QuestMasterPartyActionPayload.ACTION_INVITE,"p7")));
            assertTrue(screen.mouseClicked(pointer(screen,370,245,560,315),false));
            tour.verify(() -> UiTestTour.send(new de.quest.network.Payloads.QuestMasterPartyActionPayload(1,
                    de.quest.network.Payloads.QuestMasterPartyActionPayload.ACTION_DISBAND,"")));
            set(screen,"partyDrawerOpen",false);String before=(String)field(screen,"selectedEntryId");
            // Fourth card is clipped at y258; y274 is outside the rendered list.
            assertFalse(screen.mouseClicked(pointer(screen,150,274,560,315),false));
            assertEquals(before,field(screen,"selectedEntryId"));
        }
    }
    private static MouseButtonEvent pointer(Screen s,int x,int y,int w,int h) {
        double scale=1/((CompatScreen)s).responsiveDrag(1,w,h);
        return new MouseButtonEvent(s.width/2.0+(x-w/2.0)*scale,s.height/2.0+(y-h/2.0)*scale,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
    }
    @Test void noticeDescriptionCanReachItsLastLineWithoutMovingTheActionFooter() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for(int[] size:new int[][]{{600,450},{320,200}}) {
                var data=new VillageNetworkPayloads.NoticeJourneyPayload(1,64,3,0,Component.literal("Village"),
                        Component.literal("Welcome"),Component.literal("long description ".repeat(60)+"LASTLINE"),
                        0,3,0,0,false,ItemStack.EMPTY,0,null,false);
                var screen=new NoticeJourneyScreen(data); screen.width=size[0];screen.height=size[1];var font=font(screen);
                var graphics=mock(GuiGraphics.class);when(graphics.pose()).thenReturn(new Matrix3x2fStack(32));
                when(graphics.unwrap()).thenReturn(mock(GuiGraphicsExtractor.class));
                theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1));
                screen.render(graphics,0,0,0);
                var p=pointer(screen,165,151,560,315);
                int max=(int)field(screen,"descriptionScrollMax");assertTrue(max>0);
                for(int i=0;i<max;i++) assertTrue(screen.mouseScrolled(p.x(),p.y(),0,-1));
                assertTrue(screen.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_HOME,0,0)));
                assertEquals(0,field(screen,"descriptionScroll"));
                assertTrue(screen.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_END,0,0)));
                assertEquals(max,field(screen,"descriptionScroll"));
                theme.clearInvocations();screen.render(graphics,0,0,0);
                theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),contains("LASTLINE"),anyFloat(),anyFloat(),anyInt(),anyFloat()));
                var action=NoticeJourneyLayout.buttons(data.stage(),false).stream().filter(b -> b.action()==VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH).findFirst().orElseThrow();
                assertFalse(action.primary(),"Manual refresh stays clickable as a secondary control");
                var refresh=pointer(screen,action.x()+8,action.y()+8,560,315);assertTrue(screen.mouseClicked(refresh,false));
                tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(1,64,3,
                        VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH)),atLeastOnce());
            }
        }
    }
    @Test void rewardTabsResetPagesIgnoreGapsAndConfirmTheFrozenChoiceOnce() throws Exception {
        try(var client=mockStatic(Minecraft.class);var network=mockStatic(ClientPlayNetworking.class);var tour=mockStatic(UiTestTour.class)) {
            var mc=mock(Minecraft.class);var gui=mock(Gui.class);var gf=Minecraft.class.getDeclaredField("gui");gf.setAccessible(true);gf.set(mc,gui);
            client.when(Minecraft::getInstance).thenReturn(mc);
            var data=new RewardPayloads.View(UUID.randomUUID(),"daily:SMITH_SMELTING",List.of(
                    new RewardPayloads.Choice("supplies",List.of(Component.literal("text"))),
                    new RewardPayloads.Choice("coins",List.of(Component.literal("text")))),"supplies");
            for(int[] size:new int[][]{{600,450},{320,200}}) {
                var screen=new VanillaRewardScreen(data,null);screen.width=size[0];screen.height=size[1];font(screen);
                var pg=VanillaRewardScreen.class.getDeclaredField("page");pg.setAccessible(true);pg.setInt(screen,2);
                // First tab ends at x267; its four-pixel gap must not select a choice.
                assertFalse(screen.mouseClicked(pointer(screen,269,88,416,264),false));
                assertTrue(screen.mouseClicked(pointer(screen,300,88,416,264),false));
                assertEquals(1,field(screen,"selected"));assertEquals(0,field(screen,"page"));
                var confirm=pointer(screen,270,237,416,264);
                assertTrue(screen.mouseClicked(confirm,false));assertFalse(screen.mouseClicked(confirm,false));
            }
            tour.verify(() -> UiTestTour.send(new RewardPayloads.Action(data.session(),"coins")),times(2));
        }
    }
}
