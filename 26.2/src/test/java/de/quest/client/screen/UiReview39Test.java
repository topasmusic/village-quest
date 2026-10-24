package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.*;

final class UiReview39Test {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private record Rect(int x,int y,int w,int h) {
        boolean overlaps(Rect r) {return x<r.x+r.w && r.x<x+w && y<r.y+r.h && r.y<y+h;}
    }
    private static Font attachFont(Screen screen)throws Exception {
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
        var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);return font;
    }
    private static MouseButtonEvent pointer(Screen s,double x,double y) {
        float scale=.96096f*Math.max(.25f,Math.min(1f,Math.min(s.width*.82f/560,s.height*.86f/315)));
        return new MouseButtonEvent(s.width/2.0+(x-s.width/2.0)*scale,
                s.height/2.0+(y-s.height/2.0)*scale,new MouseButtonInfo(0,0));
    }
    @Test void dispatchRewardHasItsOwnColumnBeforeTheSendButton()throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);
            var art=mockStatic(JournalV3Visuals.class);var assets=mockStatic(KingdomUiAssets.class,CALLS_REAL_METHODS);
            var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var text=Component.literal("Oakfield");
            var data=new VillageNetworkPayloads.RegionalDispatchPayload(0,64,0,text,text,text,ItemStack.EMPTY,0,false,true,
                    List.of(new VillageNetworkPayloads.RegionalDispatchOfferData(10000,text,ItemStack.EMPTY,8,8,8)));
            var screen=new RegionalDispatchScreen(data);screen.width=700;screen.height=440;var font=attachFont(screen);
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
            var coins=new ArrayList<Rect>();var buttons=new ArrayList<Rect>();
            assets.when(()->KingdomUiAssets.moneyCentered(eq(g),eq(font),eq(8L),anyInt(),anyInt(),eq(70),eq(false)))
                    .thenAnswer(a->{coins.add(new Rect(a.getArgument(3),a.getArgument(4),70,13));return null;});
            String send=Component.translatable("screen.village-quest.dispatch.send").getString();
            theme.when(()->VillageUiTheme.drawButton(eq(g),eq(font),anyInt(),anyInt(),eq(62),eq(20),eq(send),eq(true),anyBoolean(),eq(true),eq(VillageUiTheme.ButtonTone.OAK)))
                    .thenAnswer(a->{buttons.add(new Rect(a.getArgument(2),a.getArgument(3),62,20));return null;});
            screen.render(g,0,0,0);
            assertEquals(1,coins.size());assertEquals(1,buttons.size());assertFalse(coins.getFirst().overlaps(buttons.getFirst()));
            // End-to-end paging and dispatch actions are covered by NoticeDispatchNavigationTest.
            assertTrue(coins.getFirst().x()+coins.getFirst().w()<=buttons.getFirst().x(),
                    "Reward amount must finish before the separate Send column");
        }
    }
    @Test void partyButtonClearsTheQuestDetailCard()throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);
            var art=mockStatic(JournalV3Visuals.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("questmaster","available"));
            screen.width=700;screen.height=440;var font=attachFont(screen);
            var dataField=QuestMasterScreen.class.getDeclaredField("data");dataField.setAccessible(true);
            ((QuestMasterScreen)screen).updateData((QuestMasterScreen.QuestMasterData)dataField.get(screen));
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
            var party=new ArrayList<Rect>();var cards=new ArrayList<Rect>();
            theme.when(()->VillageUiTheme.drawButton(eq(g),eq(font),anyInt(),anyInt(),eq(48),eq(15),anyString(),anyBoolean(),anyBoolean(),anyBoolean()))
                    .thenAnswer(a->{party.add(new Rect(a.getArgument(2),a.getArgument(3),48,15));return null;});
            art.when(()->JournalV3Visuals.paper(eq(g),anyInt(),anyInt(),eq(235),anyInt()))
                    .thenAnswer(a->{cards.add(new Rect(a.getArgument(1),a.getArgument(2),235,a.getArgument(4)));return null;});
            screen.render(g,0,0,0);assertEquals(1,party.size());assertFalse(cards.isEmpty());
            for(var card:cards)assertFalse(party.getFirst().overlaps(card),"Party control must not cover quest title or status");
        }
    }
}
