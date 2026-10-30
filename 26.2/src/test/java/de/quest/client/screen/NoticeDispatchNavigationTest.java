package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.ReputationPayloads;
import de.quest.network.VillageNetworkPayloads;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;

final class NoticeDispatchNavigationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static VillageNetworkPayloads.RegionalDispatchPayload dispatch() {
        return new VillageNetworkPayloads.RegionalDispatchPayload(12,64,34,
                Component.literal("Village Contact"), Component.literal("Use a Notice Post in a connected village"),
                Component.empty(), ItemStack.EMPTY,0,false,false,List.of());
    }
    private static NoticeJourneyScreen board() {
        return new NoticeJourneyScreen(new VillageNetworkPayloads.NoticeJourneyPayload(12,64,34,0,
                Component.literal("Village Contact"), Component.literal("Welcome"), Component.literal("Meet villagers"),
                0,3,0,0,false,ItemStack.EMPTY,0,null,false));
    }
    private static void size(Screen screen) { screen.width=512; screen.height=300; }
    @Test void storyObjectiveLabelsFollowStableStoryIdentityInsteadOfDisplayOrder() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var key=NoticeJourneyScreen.class.getDeclaredMethod("objectiveKey",boolean.class);key.setAccessible(true);
            for(var story:de.quest.guildtown.GuildTownStory.values()) {
                var s=new NoticeJourneyScreen(new VillageNetworkPayloads.NoticeJourneyPayload(0,64,0,2,
                    Component.empty(),story.title(),Component.empty(),2,24,0,1,false,ItemStack.EMPTY,0,null,true));
                for(boolean first:new boolean[]{true,false})assertEquals("screen.village-quest.notice_journey.objective."+story.key()+"."+(first?"first":"second"),key.invoke(s,first));
            }
            assertEquals("screen.village-quest.notice_journey.objective_one",key.invoke(board(),true),"Unknown literal titles must retain the neutral fallback");
        }
    }
    @Test void everyLocalStoryRetainsItsObjectiveAndRulesBeforeAndAfterAcceptance() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var method=NoticeJourneyScreen.class.getDeclaredMethod("activeInstruction");method.setAccessible(true);
            for(var story:de.quest.guildtown.GuildTownStory.values()) {
                for(int stage:new int[]{VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE,
                        VillageNetworkPayloads.NoticeJourneyPayload.ACTIVE,VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE,
                        VillageNetworkPayloads.NoticeJourneyPayload.READY}) {
                    var screen=new NoticeJourneyScreen(new VillageNetworkPayloads.NoticeJourneyPayload(0,64,0,stage,
                            Component.empty(),story.title(),Component.literal("STATE DETAIL"),0,24,0,1,false,ItemStack.EMPTY,0,null,true));
                    String text=(String)method.invoke(screen);
                    assertTrue(text.contains("instruction."+story.key()+".objective"),text);
                    assertTrue(text.contains("instruction."+story.key()+".rules"),text);
                    assertTrue(text.contains("STATE DETAIL"),text);
                }
            }
        }
    }

    private static MouseButtonEvent click(int x,int y) {
        return new MouseButtonEvent(x,y,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
    }
    private static MouseButtonEvent panelClick(CompatScreen screen,int x,int y) {
        try {
            var scale=CompatScreen.class.getDeclaredMethod("responsivePanelScale",int.class,int.class);scale.setAccessible(true);
            float value=(float)scale.invoke(screen,560,315);
            int left=(screen.width-560)/2,top=(screen.height-315)/2;
            return click(Math.round(screen.width/2f+(left+x-screen.width/2f)*value),
                    Math.round(screen.height/2f+(top+y-screen.height/2f)*value));
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    @Test void introButtonsUseTheDisplayedEpisodeAndNeverSendOrdinaryDelivery() {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var token=new VillageNetworkPayloads.IntroToken(3,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),1);
            for(int active:new int[]{-1,2}) {
                var offers=java.util.stream.IntStream.range(0,3).mapToObj(i -> new VillageNetworkPayloads.NoticeBoardOfferData(
                        i,Component.literal("Task "+i),ItemStack.EMPTY,8,i==2 ? 8 : 0,2,0,false,i==2)).toList();
                var payload=new VillageNetworkPayloads.NoticeBoardPayload(12,64,34,Component.empty(),Component.empty(),Component.empty(),
                        Component.empty(),-1,Component.empty(),ItemStack.EMPTY,8,0,2,0,0,0,2,Component.empty(),Component.empty(),true,false,
                        offers,Component.empty(),Component.empty(),new VillageNetworkPayloads.IntroBoardData(token,active,false,Component.empty()));
                var screen=new GuildNoticeBoardScreen(payload);size(screen);tour.clearInvocations();
                assertTrue(screen.mouseClicked(panelClick(screen,300,265),false));
                int action=active<0 ? VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_ACCEPT : VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_DELIVER;
                int id=active<0 ? 0 : 2;
                tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeBoardActionPayload(12,64,34,action,id,token)));
                if(active>=0) {
                    assertTrue(screen.mouseClicked(panelClick(screen,300,289),false));
                    tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeBoardActionPayload(12,64,34,4,2,token)));
                }
                tour.verify(() -> UiTestTour.click(anyDouble(),anyDouble(),anyInt(),anyInt()),times(active<0 ? 1 : 2));
                tour.verifyNoMoreInteractions();
            }
        }
    }
    @Test void dispatchRoundTripKeepsTrustReachableAndTheSingleplayerCooldownRunning() {
        try (var client=mockStatic(Minecraft.class); var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var first=board(); size(first);
            assertTrue(first.mouseClicked(panelClick(first,NoticeJourneyLayout.buttons(0,false).stream().filter(b->b.action()==NoticeJourneyLayout.TRUST).findFirst().orElseThrow().x()+8,272),false));
            tour.verify(() -> UiTestTour.send(new ReputationPayloads.OpenPayload(0,new BlockPos(12,64,34),0)));
            tour.clearInvocations();
            assertTrue(first.mouseClicked(panelClick(first,190,272),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                    VillageNetworkPayloads.NoticeJourneyActionPayload.DISPATCH)));
            var dispatch=new RegionalDispatchScreen(dispatch()); size(dispatch);
            assertTrue(dispatch.mouseClicked(panelClick(dispatch,50,272),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                    VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH)));
            var returned=board(); size(returned);
            tour.clearInvocations();
            assertTrue(returned.mouseClicked(panelClick(returned,350,272),false));
            tour.verify(() -> UiTestTour.send(new ReputationPayloads.OpenPayload(0,new BlockPos(12,64,34),0)));
            assertAll("Server ticks must advance between Trust clicks in singleplayer",
                    () -> assertFalse(first.isPauseScreen()),
                    () -> assertFalse(dispatch.isPauseScreen()),
                    () -> assertFalse(returned.isPauseScreen()));
        }
    }
    @Test void unavailableDispatchUsesTheWelcomeCardAndReadableBody() throws Exception {
        try (var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new RegionalDispatchScreen(dispatch()); size(screen);
            var font=mock(Font.class); when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length()*5);
            var field=Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen,font);
            var graphics=mock(GuiGraphics.class); when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(16)); when(graphics.unwrap()).thenReturn(mock(GuiGraphicsExtractor.class));
            theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(call -> call.getArgument(1));
            theme.when(() -> VillageUiTheme.wrapScaledLines(eq(font),anyString(),anyInt(),anyFloat())).thenCallRealMethod();
            screen.render(graphics,0,0,0);
            theme.verify(() -> VillageUiTheme.drawWrappedScaled(eq(graphics),eq(font),
                    eq("Use a Notice Post in a connected village"),anyInt(),anyInt(),intThat(w->w>=250),anyInt(),eq(.9f),intThat(n->n>=8)));
        }
    }
    @Test void repeatedTrustDispatchRoundTripsKeepActionsAndPositionAtDifferentViewportSizes() throws Exception {
        try (var client=mockStatic(Minecraft.class); var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for (int[] viewport:List.of(new int[]{512,300},new int[]{854,480},new int[]{1280,720})) {
                for (int round=0;round<4;round++) {
                    var board=board(); board.width=viewport[0]; board.height=viewport[1];
                    int x=(viewport[0]-NoticeJourneyLayout.WIDTH)/2, y=(viewport[1]-NoticeJourneyLayout.HEIGHT)/2;
                    tour.clearInvocations();
                    assertTrue(board.mouseClicked(panelClick(board,350,272),false));
                    tour.verify(() -> UiTestTour.send(new ReputationPayloads.OpenPayload(0,new BlockPos(12,64,34),0)));
                    assertTrue(board.mouseClicked(panelClick(board,190,272),false));
                    tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                            VillageNetworkPayloads.NoticeJourneyActionPayload.DISPATCH)));
                    var screen=new RegionalDispatchScreen(dispatch()); screen.width=viewport[0]; screen.height=viewport[1];
                    assertTrue(screen.mouseClicked(panelClick(screen,50,272),false));
                    tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                            VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH)));
                    var returned=board(); returned.width=viewport[0]; returned.height=viewport[1];
                    tour.clearInvocations();
                    assertTrue(returned.mouseClicked(panelClick(returned,350,272),false));
                    tour.verify(() -> UiTestTour.send(new ReputationPayloads.OpenPayload(0,new BlockPos(12,64,34),0)));
                    assertFalse(board.isPauseScreen());
                    assertFalse(screen.isPauseScreen());
                }
            }
        }
    }
    @Test void lastOfferAndClaimKeepTheirActionsInTheCompactPage() {
        try (var client=mockStatic(Minecraft.class); var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var offers=java.util.stream.IntStream.range(0,4).mapToObj(i ->
                    new VillageNetworkPayloads.RegionalDispatchOfferData(i,Component.literal("Village "+i),
                            ItemStack.EMPTY,8,8,5)).toList();
            var data=new VillageNetworkPayloads.RegionalDispatchPayload(12,64,34,Component.literal("Village"),
                    Component.literal("Ready"),Component.empty(),ItemStack.EMPTY,0,false,true,offers);
            var screen=new RegionalDispatchScreen(data); size(screen);
            var pointer=panelClick(screen,490,213);
            assertTrue(screen.mouseScrolled(pointer.x(),pointer.y(),0,-1),"Last offer remains reachable by scrolling");
            assertTrue(screen.mouseClicked(panelClick(screen,490,213),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(12,64,34,
                    VillageNetworkPayloads.RegionalDispatchActionPayload.START,3)));
            tour.clearInvocations();
            screen.updateData(new VillageNetworkPayloads.RegionalDispatchPayload(12,64,34,Component.literal("Village"),
                    Component.literal("Returned"),Component.literal("Route"),ItemStack.EMPTY,0,true,false,offers));
            assertTrue(screen.mouseClicked(panelClick(screen,270,265),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(12,64,34,
                    VillageNetworkPayloads.RegionalDispatchActionPayload.CLAIM,-1)));
            tour.clearInvocations();
            screen.updateData(new VillageNetworkPayloads.RegionalDispatchPayload(12,64,34,Component.literal("Village"),
                    Component.literal("Ready"),Component.empty(),ItemStack.EMPTY,0,false,true,
                    List.of(new VillageNetworkPayloads.RegionalDispatchOfferData(0,Component.literal("Village"),ItemStack.EMPTY,8,7,5))));
            screen.mouseClicked(panelClick(screen,490,125),false);
            tour.verify(() -> UiTestTour.send(any(net.minecraft.network.protocol.common.custom.CustomPacketPayload.class)),never());
        }
    }
}
