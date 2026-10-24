package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.*;
import de.quest.commands.UiTestTourSteps;
import de.quest.network.MarketPayloads;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.*;
import net.minecraft.world.item.*;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class MarketPresentationTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:List.of(Items.BOOK,Items.BARREL,Items.OAK_FENCE,Items.HAY_BLOCK,Items.WHEAT)) item.builtInRegistryHolder().bindComponents(
                net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                        .set(net.minecraft.core.component.DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.translatable(item.getDescriptionId())).build());
    }
    @Test void newMarketVariantsAndJournalDashboardsLeadTheIsolatedTour() {
        assertEquals(List.of(new UiTestTourSteps.Step("market","goods"),new UiTestTourSteps.Step("market","limited"),
                new UiTestTourSteps.Step("market","details"),new UiTestTourSteps.Step("journal","overview"),
                new UiTestTourSteps.Step("journal","network")),UiTestTourSteps.ALL.subList(0,5));
        try(var client=mockStatic(Minecraft.class)){client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for(var step:UiTestTourSteps.ALL.subList(0,5)) assertNotNull(UiTestTourScreens.create(step));}
    }
    @Test void previewCannotSendTradesRefreshOrClosePackets() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);var network=mockStatic(ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));tour.when(UiTestTour::active).thenReturn(true);
            var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.getFirst());
            var action=MarketScreen.class.getDeclaredMethod("action",MarketPayloads.Kind.class,UUID.class,int.class);action.setAccessible(true);
            for(var kind:MarketPayloads.Kind.values()) action.invoke(screen,kind,new UUID(1,1),1);
            screen.onClose();network.verifyNoInteractions();
        }
    }
    @Test void ownedCountUsesOnlyMatchingMainInventoryGoods() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            var mc=mock(Minecraft.class);client.when(Minecraft::getInstance).thenReturn(mc);
            var player=mock(net.minecraft.client.player.LocalPlayer.class);mc.player=player;
            var inventory=mock(net.minecraft.world.entity.player.Inventory.class);when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
            when(inventory.getItem(0)).thenReturn(new ItemStack(Items.WHEAT,32));
            var named=new ItemStack(Items.WHEAT,64);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Reserved"));
            when(inventory.getItem(1)).thenReturn(named);when(inventory.getItem(35)).thenReturn(new ItemStack(Items.WHEAT,8));
            when(inventory.getItem(36)).thenReturn(new ItemStack(Items.WHEAT,64));
            var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.getFirst());
            var count=MarketScreen.class.getDeclaredMethod("owned",ItemStack.class);count.setAccessible(true);
            assertEquals(40,count.invoke(screen,new ItemStack(Items.WHEAT)));
        }
    }
    @Test void eightOffersKeepFixedSizeAndSecondPageOpensTheVisibleGoodsDetails() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
            art.when(() -> KingdomUiAssets.closeBounds(anyInt(),anyInt(),anyInt(),anyInt())).thenCallRealMethod();
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.getFirst());
            var field=MarketScreen.class.getDeclaredField("data");field.setAccessible(true);var data=(MarketPayloads.View)field.get(screen);
            var rows=new ArrayList<MarketPayloads.Row>();var original=data.rows().getFirst();
            for(int i=0;i<8;i++) rows.add(new MarketPayloads.Row("goods_"+i,original.item(),original.lot(),original.stock(),original.demand(),original.adjustment(),original.buyQuote(),original.buyPrice(),original.sellQuote(),original.sellPrice()));
            assertTrue(screen.updateData(new MarketPayloads.View(data.session(),1,false,true,data.trader(),data.home(),data.market(),1,data.balance(),data.budget(),data.factor(),data.discount(),rows,data.feedback())));
            var height=MarketScreen.class.getDeclaredField("H");height.setAccessible(true);
            assertEquals(315,height.getInt(screen),"more goods must not shrink the whole UI");
            theme.when(() -> VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a->List.of((String)a.getArgument(1)));
            screen.width=600;screen.height=400;
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(8));
            screen.render(g,0,0,0);
            assertTrue(screen.mouseClicked(point(screen,234,266,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
            var page=MarketScreen.class.getDeclaredField("goodsPage");page.setAccessible(true);assertEquals(1,page.getInt(screen));
            assertTrue(screen.mouseClicked(point(screen,100,240,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
            var detail=MarketScreen.class.getDeclaredField("detail");detail.setAccessible(true);assertEquals(7,detail.getInt(screen));

            screen.render(g,0,0,0);
            for(int i=0;i<3;i++) assertTrue(screen.mouseClicked(point(screen,160,266,
                    new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
            assertEquals(0,page.getInt(screen),"rapid previous clicks before rendering must stay on the first page");
            var mc=Minecraft.getInstance();var guiField=Minecraft.class.getDeclaredField("gui");guiField.setAccessible(true);guiField.set(mc,mock(net.minecraft.client.gui.Gui.class));
            screen.onClose();assertEquals(-1,detail.getInt(screen));
        }
    }
    @Test void fixedQuantityTradeAndRefreshRemainClickableAtReducedGuiSize() throws Exception {
        for(int[] size:new int[][]{{600,400},{320,240}}) {
            try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);var network=mockStatic(ClientPlayNetworking.class);
                var drawing=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                art.when(() -> KingdomUiAssets.closeBounds(anyInt(),anyInt(),anyInt(),anyInt())).thenCallRealMethod();
                // Build the real preview model, then exercise it as a normal live screen.
                var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.getFirst());
                var dataField=MarketScreen.class.getDeclaredField("data");dataField.setAccessible(true);var view=(MarketPayloads.View)dataField.get(screen);
                network.when(() -> ClientPlayNetworking.canSend(MarketPayloads.Action.ID)).thenReturn(true);
                drawing.when(() -> VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a->List.of((String)a.getArgument(1)));
                screen.width=size[0];screen.height=size[1];var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(8));
                screen.render(g,0,0,0);
                var controls=MarketScreen.class.getDeclaredField("controls");controls.setAccessible(true);
                assertEquals(12,((List<?>)controls.get(screen)).size(),"close, eight trades, price pagination and refresh; no quantity selectors");
                float scale=.96096f*Math.max(.25f,Math.min(1f,Math.min(size[0]*.82f/560,size[1]*.86f/315)));
                double x=size[0]/2.0+(265-280)*scale,y=size[1]/2.0+(145-157.5)*scale;
                assertTrue(screen.mouseClicked(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
                network.verify(() -> ClientPlayNetworking.send(new MarketPayloads.Action(view.session(),view.revision(),MarketPayloads.Kind.TRADE,new UUID(1,1),1)));
                screen.updateData(view);screen.render(g,0,0,0);
                x=size[0]/2.0+(470-280)*scale;y=size[1]/2.0+(289-157.5)*scale;
                assertTrue(screen.mouseClicked(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
                network.verify(() -> ClientPlayNetworking.send(new MarketPayloads.Action(view.session(),view.revision(),MarketPayloads.Kind.REFRESH,MarketPayloads.NONE,1)));
            }
        }
    }
    @Test void inlinePricePaginationReachesTheFinalExplanationWithoutSendingATrade() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);var network=mockStatic(ClientPlayNetworking.class);
            var theme=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            theme.when(()->VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a->List.of((String)a.getArgument(1)));
            var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.getFirst());screen.width=800;screen.height=500;
            var font=mock(net.minecraft.client.gui.Font.class);var f=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(8));screen.render(g,0,0,0);
            assertTrue(screen.mouseClicked(point(screen,512,266,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
            var page=MarketScreen.class.getDeclaredField("detailPage");page.setAccessible(true);assertEquals(1,page.getInt(screen));
            for(int i=0;i<20;i++) {
                screen.render(g,0,0,0);
                int before=page.getInt(screen);
                assertTrue(screen.mouseClicked(point(screen,512,266,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
                if(page.getInt(screen)==before)break;
                assertTrue(i<19,"Price explanation pagination must terminate");
            }
            theme.clearInvocations();screen.render(g,0,0,0);
            String last=net.minecraft.network.chat.Component.translatable("screen.village-quest.market.price_limit").getString();
            theme.verify(()->VillageUiTheme.drawStringScaled(eq(g),eq(font),eq(last),anyFloat(),anyFloat(),anyInt(),eq(.82f)));
            network.verifyNoInteractions();
        }
    }
    @Test void completedTradeFeedbackSurvivesTheNextUnaffordableOffer() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);
            var theme=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            theme.when(()->VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a->List.of((String)a.getArgument(1)));
            var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.getFirst());
            var f=MarketScreen.class.getDeclaredField("data");f.setAccessible(true);var d=(MarketPayloads.View)f.get(screen);
            screen.updateData(new MarketPayloads.View(d.session(),1,false,true,d.trader(),d.home(),d.market(),d.lots(),0,d.budget(),d.factor(),d.discount(),d.rows(),MarketPayloads.Feedback.COMMITTED));
            screen.selectTourDetails();screen.width=800;screen.height=500;
            var font=mock(net.minecraft.client.gui.Font.class);var ff=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(screen,font);
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(8));
            screen.render(g,0,0,0);
            String message=net.minecraft.network.chat.Component.translatable("screen.village-quest.market.feedback.committed").getString();
            theme.verify(()->VillageUiTheme.ellipsize(eq(font),eq(message),eq(400)));
        }
    }    private static MouseButtonEvent point(MarketScreen s,int x,int y,MouseButtonInfo info) {
        float scale=.96096f*Math.max(.25f,Math.min(1f,Math.min(s.width*.82f/560,s.height*.86f/315)));
        return new MouseButtonEvent(s.width/2.0+(x-280)*scale,s.height/2.0+(y-157.5)*scale,info);
    }}
