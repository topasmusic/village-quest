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
    private static MouseButtonEvent click(int x,int y) {
        return new MouseButtonEvent(x,y,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
    }
    @Test void dispatchRoundTripKeepsTrustReachableAndTheSingleplayerCooldownRunning() {
        try (var client=mockStatic(Minecraft.class); var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var first=board(); size(first);
            assertTrue(first.mouseClicked(click(348,226),false));
            tour.verify(() -> UiTestTour.send(new ReputationPayloads.OpenPayload(0,new BlockPos(12,64,34),0)));
            tour.clearInvocations();
            assertTrue(first.mouseClicked(click(250,226),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                    VillageNetworkPayloads.NoticeJourneyActionPayload.DISPATCH)));
            var dispatch=new RegionalDispatchScreen(dispatch()); size(dispatch);
            // Locate Back using the actual authored footer for either the old or new screen.
            try {
                var height=RegionalDispatchScreen.class.getDeclaredField("HEIGHT"); height.setAccessible(true);
                int panelHeight=height.getInt(null);
                assertTrue(dispatch.mouseClicked(click(100,(300-panelHeight)/2+panelHeight-14),false));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                    VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH)));
            var returned=board(); size(returned);
            tour.clearInvocations();
            assertTrue(returned.mouseClicked(click(348,226),false));
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
            var graphics=mock(GuiGraphics.class); when(graphics.unwrap()).thenReturn(mock(GuiGraphicsExtractor.class));
            theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(call -> call.getArgument(1));
            screen.render(graphics,0,0,0);
            theme.verify(() -> VillageUiTheme.drawCard(graphics,94,96,324,98,false,false));
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),
                    eq("Use a Notice Post in a connected village"),eq(104f),eq(129f),anyInt(),eq(.85f)));
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
                    assertTrue(board.mouseClicked(click(x+272,y+169),false));
                    tour.verify(() -> UiTestTour.send(new ReputationPayloads.OpenPayload(0,new BlockPos(12,64,34),0)));
                    assertTrue(board.mouseClicked(click(x+174,y+169),false));
                    tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                            VillageNetworkPayloads.NoticeJourneyActionPayload.DISPATCH)));
                    var screen=new RegionalDispatchScreen(dispatch()); screen.width=viewport[0]; screen.height=viewport[1];
                    assertTrue(screen.mouseClicked(click(x+24,y+169),false));
                    tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(12,64,34,
                            VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH)));
                    var returned=board(); returned.width=viewport[0]; returned.height=viewport[1];
                    tour.clearInvocations();
                    assertTrue(returned.mouseClicked(click(x+272,y+169),false));
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
            assertTrue(screen.mouseClicked(click(370,197),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(12,64,34,
                    VillageNetworkPayloads.RegionalDispatchActionPayload.START,3)));
            tour.clearInvocations();
            screen.updateData(new VillageNetworkPayloads.RegionalDispatchPayload(12,64,34,Component.literal("Village"),
                    Component.literal("Returned"),Component.literal("Route"),ItemStack.EMPTY,0,true,false,offers));
            assertTrue(screen.mouseClicked(click(220,226),false));
            tour.verify(() -> UiTestTour.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(12,64,34,
                    VillageNetworkPayloads.RegionalDispatchActionPayload.CLAIM,-1)));
            tour.clearInvocations();
            screen.updateData(new VillageNetworkPayloads.RegionalDispatchPayload(12,64,34,Component.literal("Village"),
                    Component.literal("Ready"),Component.empty(),ItemStack.EMPTY,0,false,true,
                    List.of(new VillageNetworkPayloads.RegionalDispatchOfferData(0,Component.literal("Village"),ItemStack.EMPTY,8,7,5))));
            screen.mouseClicked(click(370,137),false);
            tour.verify(() -> UiTestTour.send(any(net.minecraft.network.protocol.common.custom.CustomPacketPayload.class)),never());
        }
    }
}
