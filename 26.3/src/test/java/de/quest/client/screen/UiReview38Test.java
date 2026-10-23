package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.VillageUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class UiReview38Test {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    private record Rect(int x,int y,int w,int h) {
        boolean overlaps(Rect other) {return x<other.x+other.w && other.x<x+w && y<other.y+other.h && other.y<y+h;}
    }
    @Test void routeJournalButtonCannotBeOverpaintedByFooterPaper()throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);
            var surface=mockStatic(de.quest.client.ui.SurfaceMapRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("routes","network"));
            screen.width=700;screen.height=440;
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
            var buttons=new ArrayList<Rect>();
            String journal=Component.translatable("screen.village-quest.trade_route.back").getString();
            theme.when(()->VillageUiTheme.drawButton(eq(g),eq(font),anyInt(),anyInt(),anyInt(),anyInt(),eq(journal),anyBoolean(),anyBoolean(),anyBoolean()))
                    .thenAnswer(a->{buttons.add(new Rect(a.getArgument(2),a.getArgument(3),a.getArgument(4),a.getArgument(5)));return null;});
            theme.when(()->VillageUiTheme.drawPaper(eq(g),anyInt(),anyInt(),anyInt(),anyInt()))
                    .thenAnswer(a->{var paper=new Rect(a.getArgument(1),a.getArgument(2),a.getArgument(3),a.getArgument(4));
                        for(var button:buttons)assertFalse(paper.overlaps(button),"Later footer paper covers the Journal target");return null;});
            screen.render(g,0,0,0);assertEquals(1,buttons.size());
        }
    }
    @Test void sharedInteriorHasEqualTopAndBottomMargins() {
        try(var family=mockStatic(JournalV3Visuals.class,CALLS_REAL_METHODS);var theme=mockStatic(VillageUiTheme.class)) {
            var g=mock(GuiGraphics.class);
            var y=org.mockito.ArgumentCaptor.forClass(Integer.class);var h=org.mockito.ArgumentCaptor.forClass(Integer.class);
            family.when(()->JournalV3Visuals.paper(eq(g),anyInt(),y.capture(),anyInt(),h.capture())).thenAnswer(a->null);
            JournalV3Visuals.insetBoard(g,0,0,560,315);
            assertEquals(y.getValue(),315-y.getValue()-h.getValue(),"Paper must be vertically centered inside identical rails");
        }
    }
    @Test void dispatchNativeCoinHeightClearsTheOfferCardBottom()throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);
            var assets=mockStatic(de.quest.client.ui.KingdomUiAssets.class,CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var text=Component.literal("Oakfield");
            var data=new de.quest.network.VillageNetworkPayloads.RegionalDispatchPayload(0,64,0,text,text,text,
                    net.minecraft.world.item.ItemStack.EMPTY,0,false,true,java.util.List.of(
                    new de.quest.network.VillageNetworkPayloads.RegionalDispatchOfferData(10000,text,
                    net.minecraft.world.item.ItemStack.EMPTY,8,4,8)));
            var screen=new RegionalDispatchScreen(data);screen.width=700;screen.height=440;
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
            var cards=new ArrayList<Rect>();
            theme.when(()->VillageUiTheme.drawCard(eq(g),anyInt(),anyInt(),eq(292),eq(46),anyBoolean(),anyBoolean()))
                    .thenAnswer(a->{cards.add(new Rect(a.getArgument(1),a.getArgument(2),a.getArgument(3),a.getArgument(4)));return null;});
            assets.when(()->de.quest.client.ui.KingdomUiAssets.moneyCentered(eq(g),eq(font),eq(8L),anyInt(),anyInt(),eq(70),eq(false)))
                    .thenAnswer(a->{int y=a.getArgument(4);assertEquals(1,cards.size());
                        // Native coin rendering is 16px at .8 scale, up to 12.8 UI units high.
                        assertTrue(y+13<=cards.getFirst().y()+cards.getFirst().h()-2,
                                "Native reward coin must retain at least two pixels above the frame");return null;});
            screen.render(g,0,0,0);
            assets.verify(()->de.quest.client.ui.KingdomUiAssets.moneyCentered(eq(g),eq(font),eq(8L),anyInt(),anyInt(),eq(70),eq(false)));
        }
    }
}
