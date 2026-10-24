package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.*;
import de.quest.kingdom.BuildingScanner;
import de.quest.network.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;

final class Feedback48ScreenTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    private static void set(Object object,Class<?> type,String name,Object value) throws Exception {
        var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    @Test void inFlightTradeRetainsButtonArtworkWhileControlsRemainLocked() throws Exception {
        try(var client=mockStatic(Minecraft.class);var family=mockStatic(JournalV3Visuals.class);var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=(MarketScreen)UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("market","goods"));
            var method=MarketScreen.class.getDeclaredMethod("tradeButton",GuiGraphics.class,int.class,int.class,boolean.class,int.class,int.class,Runnable.class);
            method.setAccessible(true);var g=mock(GuiGraphics.class);var action=mock(Runnable.class);
            method.invoke(screen,g,20,20,true,21,21,action);
            set(screen,MarketScreen.class,"waiting",true);method.invoke(screen,g,20,20,true,21,21,action);
            family.verify(() -> JournalV3Visuals.action(eq(g),any(),eq(""),eq(20),eq(20),eq(56),eq(24),eq(true)),times(2));
            var controlsField=MarketScreen.class.getDeclaredField("controls");controlsField.setAccessible(true);
            var controls=(List<?>)controlsField.get(screen);var enabled=controls.getFirst().getClass().getDeclaredMethod("enabled");enabled.setAccessible(true);
            assertTrue((boolean)enabled.invoke(controls.getFirst()));assertFalse((boolean)enabled.invoke(controls.getLast()));
            verifyNoInteractions(action);
        }
    }
    @Test void outgoingOfferShowsSavedStateAndOnlyDifferentTermsOrUnknownRepliesCanBeResent() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            art.when(() -> KingdomUiAssets.closeBounds(anyInt(),anyInt(),anyInt(),anyInt())).thenCallRealMethod();
            for(int mode=0;mode<4;mode++) {
                theme.clearInvocations();
                var deal=new BuildingPayloads.Deal(0,0,0,false,mode==2?"unknown":"ready",0,0,"none");
                var partner=new BuildingPayloads.Partner(UUID.randomUUID(),"Other",0,List.of(deal,deal,deal),"none",false);
                var data=new BuildingPayloads.View(UUID.randomUUID(),1,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                        BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,true,false,3,3,true,"Home",List.of(),List.of(),"",
                        BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,new BuildingPayloads.Diplomacy(0,1,List.of(partner),false));
                var screen=new BuildingScreen(data);if(mode==1)set(screen,BuildingScreen.class,"dealTerms",1);
                if(mode==3)set(screen,BuildingScreen.class,"offerPendingRevision",1L);
                var render=BuildingScreen.class.getDeclaredMethod("renderDiplomacy",GuiGraphics.class,int.class,int.class,int.class,int.class);
                render.setAccessible(true);render.invoke(screen,mock(GuiGraphics.class),0,0,-1,-1);
                String key=switch(mode){case 1->"change";case 2->"retry";case 3->"sending";default->"sent";};
                String label=Component.translatable("screen.village-quest.building.diplomacy_offer_"+key).getString();boolean active=mode==1||mode==2;
                theme.verify(() -> VillageUiTheme.drawButton(any(),any(),eq(30),eq(291),eq(170),eq(18),eq(label),eq(active),anyBoolean(),eq(false)));
            }
        }
    }
    @Test void unrelatedViewRevisionDoesNotAcknowledgeAnInFlightDiplomacyOffer() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new BuildingScreen(offerView(1));set(screen,BuildingScreen.class,"offerPendingRevision",1L);
            set(screen,BuildingScreen.class,"offerPendingPartner",new UUID(7,7));
            set(screen,BuildingScreen.class,"offerPendingDeal",offerView(1).diplomacy().partners().getFirst().deals().getFirst());
            screen.updateData(offerView(2));
            var field=BuildingScreen.class.getDeclaredField("offerPendingRevision");field.setAccessible(true);
            assertEquals(1L,field.getLong(screen));
            screen.updateData(offerView(3,1));
            assertEquals(-1L,field.getLong(screen),"The selected deal's actual answer restores input");
        }
    }
    @Test void unansweredOfferEventuallyShowsSafeCloseInsteadOfResending() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            art.when(() -> KingdomUiAssets.closeBounds(anyInt(),anyInt(),anyInt(),anyInt())).thenCallRealMethod();
            var screen=new BuildingScreen(offerView(1));set(screen,BuildingScreen.class,"offerPendingRevision",1L);
            for(int i=0;i<220;i++) screen.tick();
            var render=BuildingScreen.class.getDeclaredMethod("renderDiplomacy",GuiGraphics.class,int.class,int.class,int.class,int.class);
            render.setAccessible(true);render.invoke(screen,mock(GuiGraphics.class),0,0,-1,-1);
            var label=Component.translatable("screen.village-quest.building.diplomacy_offer_reopen").getString();
            theme.verify(() -> VillageUiTheme.drawButton(any(),any(),eq(30),eq(291),eq(170),eq(18),eq(label),eq(true),anyBoolean(),eq(false)));
        }
    }
    private static BuildingPayloads.View offerView(long revision) {
        return offerView(revision,0);
    }
    private static BuildingPayloads.View offerView(long revision,long dealRevision) {
        var deal=new BuildingPayloads.Deal(dealRevision,0,0,false,"ready",0,0,"none");
        var partner=new BuildingPayloads.Partner(new UUID(7,7),"Other",0,List.of(deal,deal,deal),"none",false);
        return new BuildingPayloads.View(new UUID(1,1),revision,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                BuildingScanner.Status.VALID,new UUID(2,2),0,new UUID(3,3),0,true,false,3,3,true,"Home",List.of(),List.of(),"",
                BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,new BuildingPayloads.Diplomacy(0,1,List.of(partner),false));
    }
    @Test void rewardWindowIsFifteenPercentSmallerAndPointerTransformUsesThatSameScale() {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var data=new RewardPayloads.View(UUID.randomUUID(),"daily:SMITH_SMELTING",List.of(new RewardPayloads.Choice("supplies",List.of(Component.literal("Supplies")))),"supplies");
            var reward=new VanillaRewardScreen(data,null);var normal=new CompatScreen(Component.empty()){};
            reward.width=normal.width=960;reward.height=normal.height=540;
            assertEquals(.8736f*1.10f,normal.panelSizeFactor(),.00001f);
            assertEquals(normal.panelSizeFactor()*.85f,reward.panelSizeFactor(),.00001f);
            assertEquals(normal.responsiveDrag(1,416,254)/.85,reward.responsiveDrag(1,416,254),.00001);
            double scale=1/reward.responsiveDrag(1,416,254),target=600;
            assertEquals((int)target,reward.responsiveMouseX(480+(target-480)*scale,416,254));
        }
    }
}
