package de.quest.client.screen;

import de.quest.commands.UiTestTourSteps;
import de.quest.network.Payloads;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class UiReviewRegressionTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        // Production binds data-driven item defaults before opening any screen.
        // Fixtures select requests from several item pools. Bind their defaults in this
        // test itself, independent of whether another render test happened to run first.
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private MockedStatic<Minecraft> client;
    @BeforeEach void provideClientForScreenConstruction() {
        Minecraft instance = mock(Minecraft.class);
        client = mockStatic(Minecraft.class);
        client.when(Minecraft::getInstance).thenReturn(instance);
    }
    @AfterEach void releaseClient() { client.close(); }

    @Test void prosperityTourUsesExistingSectionAndEntryIcons() throws Exception {
        Field field = ProsperityScreen.class.getDeclaredField("data");
        field.setAccessible(true);
        for (String state : List.of("upgrades", "services", "collection")) {
            var screen = UiTestTourScreens.create(new UiTestTourSteps.Step("prosperity", state));
            var payload = (Payloads.EconomyPayload) field.get(screen);
            assertEquals(List.of("prosperity", "commission", "services", "collection", "statistics"),
                    payload.sections().stream().map(Payloads.EconomySectionData::sectionId).toList());
            Field selected = ProsperityScreen.class.getDeclaredField("sectionIndex");
            selected.setAccessible(true);
            assertEquals(state.equals("upgrades") ? "prosperity" : state,
                    payload.sections().get(selected.getInt(screen)).sectionId());
            for (var section : payload.sections()) {
                assertIcon(section.iconName());
                for (var entry : section.entries()) assertIcon(entry.iconName());
            }
        }
    }

    @Test void pilgrimContractBodyDoesNotMoveIntoTheFooterWhenMoreContractsArrive() throws Exception {
        Method body = PilgrimTradeScreen.class.getDeclaredMethod("getContractBodyTop", int.class);
        Method hovered = PilgrimTradeScreen.class.getDeclaredMethod("getHoveredContractIndex", int.class, int.class, int.class, int.class);
        body.setAccessible(true); hovered.setAccessible(true);
        var contract = new PilgrimTradeScreen.PilgrimContractView("test", Component.literal("A very long contract title"),
                Component.empty(), List.of(), List.of(), List.of(), Component.empty(), false, ItemStack.EMPTY);
        for (int count : new int[] {1, 2, 8}) {
            var screen = new PilgrimTradeScreen(new PilgrimTradeScreen.PilgrimTradeData(-1, Component.empty(), 0, 1200,
                    List.of(), java.util.Collections.nCopies(count, contract)));
            assertEquals(105, body.invoke(screen, 0));
            assertEquals(count - 1, hovered.invoke(screen, 278, 74, 0, 0));
            assertEquals(count == 1 ? 0 : 1, hovered.invoke(screen, 514, 74, 0, 0));
            assertEquals(-1, hovered.invoke(screen, 300, 130, 0, 0));
        }
    }

    @Test void activePrimaryButtonsStillGiveWarmHoverFeedback() {
        var graphics = mock(de.quest.client.compat.GuiGraphics.class);
        when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        de.quest.client.ui.VillageUiTheme.drawButton(graphics, font, 10, 20, 90, 14,
                "Update progress", true, true, true);
        verify(graphics).fill(12, 22, 98, 32, 0x33FFC66D);
        clearInvocations(graphics);
        de.quest.client.ui.VillageUiTheme.drawButton(graphics, font, 10, 20, 90, 14,
                "Update progress", true, false, true);
        verify(graphics).fill(13, 23, 97, 31, 0x66365E59);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0x33FFC66D));
        clearInvocations(graphics);
        de.quest.client.ui.VillageUiTheme.drawButton(graphics, font, 10, 20, 90, 14,
                "Update progress", false, true, true);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0x33FFC66D));
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0x66365E59));
        verify(graphics).fill(11,21,99,33,0xFFE4CFAB);
    }

    private static void assertIcon(String name) {
        assertNotNull(UiReviewRegressionTest.class.getResource("/assets/village-quest/textures/gui/prosperity/" + name + ".png"), name);
    }

    @Test void lanHostCanOpenPartyDrawerForServerApprovedQuest() throws Exception {
        var instance = Minecraft.getInstance();
        when(instance.isLocalServer()).thenReturn(true);
        var screen = new QuestMasterScreen(new QuestMasterScreen.QuestMasterData(-1, Component.empty(),
                List.of(), List.of(), null, 0));
        Field minecraft = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("minecraft");
        minecraft.setAccessible(true); minecraft.set(screen, instance);
        Method available = QuestMasterScreen.class.getDeclaredMethod("isPartyDrawerAvailable", QuestMasterScreen.EntryView.class);
        available.setAccessible(true);
        for (boolean shareable : new boolean[] {true, false}) {
            var entry = new QuestMasterScreen.EntryView("test", "daily", Component.empty(), Component.empty(),
                    Component.empty(), shareable, Component.empty(), List.of(), List.of(), List.of(),
                    0, Component.empty(), false, 0, Component.empty(), false, false);
            assertEquals(shareable, available.invoke(screen, entry));
        }
        assertEquals(false, available.invoke(screen, new Object[] {null}));
    }

    @Test void pilgrimTourUsesProductionPricesAndContractInsteadOfThreeCrownWheatPlaceholder() throws Exception {
        var screen = UiTestTourScreens.create(new UiTestTourSteps.Step("pilgrim", "contract"));
        Field field = PilgrimTradeScreen.class.getDeclaredField("data"); field.setAccessible(true);
        var data = (PilgrimTradeScreen.PilgrimTradeData) field.get(screen);
        assertEquals(de.quest.shop.ShopService.offer("decor_chiseled_bookshelf").price(), data.offers().getFirst().price());
        assertEquals("fangs_by_the_hedgerow", data.contracts().getFirst().contractId());
        assertEquals(de.quest.pilgrim.PilgrimContractService.previewHedgerowContract().rewardLines(), data.contracts().getFirst().rewardLines());
    }

    @Test void renameInputAndHeadingUseSingleUnshadowedInk() throws Exception {
        var screen = (WayshrineScreen) UiTestTourScreens.create(new UiTestTourSteps.Step("wayshrine", "owner"));
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        Field fontField = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");
        fontField.setAccessible(true); fontField.set(screen, font);
        screen.init();
        Field inputField = WayshrineScreen.class.getDeclaredField("nameField"); inputField.setAccessible(true);
        Field shadow = net.minecraft.client.gui.components.EditBox.class.getDeclaredField("textShadow"); shadow.setAccessible(true);
        assertFalse(shadow.getBoolean(inputField.get(screen)));
        var graphics = mock(de.quest.client.compat.GuiGraphics.class);
        when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
        Method overlay = WayshrineScreen.class.getDeclaredMethod("drawRenameOverlay", de.quest.client.compat.GuiGraphics.class,
                int.class, int.class, int.class, int.class);
        overlay.setAccessible(true); overlay.invoke(screen, graphics, 0, 0, 0, 0);
        verify(graphics, never()).drawCenteredString(any(), anyString(), anyInt(), anyInt(), anyInt());
        verify(graphics).drawString(eq(font), anyString(), anyInt(), eq(85), anyInt(), eq(false));
    }

    @Test void realTitlePathResolvesDispatchAndWayshrineToPackagedIcons() {
        var graphics=mock(de.quest.client.compat.GuiGraphics.class);
        when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
        var font=mock(net.minecraft.client.gui.Font.class);
        when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
        for(String semantic:List.of("dispatch","wayshrine")) {
            var id=de.quest.client.ui.VillageUiTheme.icon(semantic);
            assertNotNull(getClass().getClassLoader().getResource("assets/"+id.getNamespace()+"/"+id.getPath()),
                    "The real drawTitle icon lookup must have packaged pixels: "+id);
            de.quest.client.ui.VillageUiTheme.drawTitle(graphics,font,"Title",semantic,20,10,220);
        }
    }

    @Test void numericObjectiveRowsPreserveAmountsAndRejectMalformedOrCompoundComponents() {
        var objective=Component.translatable("quest.village-quest.daily.wheat.progress",3,25,2,7);
        var rows=de.quest.client.ui.QuestObjectiveRows.rows(objective);
        assertEquals(2,rows.size());assertEquals(3,rows.getFirst().current());assertEquals(25,rows.getFirst().target());
        assertEquals(12,rows.getFirst().filledPixels(100));
        assertEquals(100,new de.quest.client.ui.QuestObjectiveRows.Row(Component.empty(),Items.WHEAT,Integer.MAX_VALUE,1).filledPixels(100));
        for(var invalid:List.of(Component.literal("Wheat 3/25"),
                Component.translatable("quest.village-quest.daily.wheat.progress","3",25,2,7),
                Component.translatable("quest.village-quest.daily.wheat.progress",-1,25,2,7),
                objective.copy().append("custom suffix"))) assertTrue(de.quest.client.ui.QuestObjectiveRows.rows(invalid).isEmpty());
    }
    @Test void shrineLocationNameUsesKnownArgumentsAndPreservesCustomNames() {
        Component location=Component.literal("Archive");
        var shrine=new Payloads.TradeRouteShrineData(5,0,0,0,Component.translatable("text.village-quest.wayshrine.village",6,location),false,4,1,2,600);
        assertSame(location,WayshrineScreen.destinationName(shrine));
        Component custom=Component.literal("Wayshrine 6 – My home");
        var named=new Payloads.TradeRouteShrineData(5,0,0,0,custom,false,4,1,2,600);
        assertSame(custom,WayshrineScreen.destinationName(named));
    }
    @Test void progressRefreshIsSecondaryWhileDeliveryRemainsPrimary() {
        for(int stage:List.of(de.quest.network.VillageNetworkPayloads.NoticeJourneyPayload.ACTIVE,
                de.quest.network.VillageNetworkPayloads.NoticeJourneyPayload.READY)) {
            var main=NoticeJourneyLayout.buttons(stage,true).getFirst();
            assertEquals(stage==de.quest.network.VillageNetworkPayloads.NoticeJourneyPayload.READY,main.primary());
        }
    }

    @Test void declaredArtworkContentExcludesTheBakedInPaleRows() throws Exception {
        for(var motif:List.of(de.quest.client.ui.QuestArtwork.Motif.ARCHIVE,de.quest.client.ui.QuestArtwork.Motif.PASTURE,
                de.quest.client.ui.QuestArtwork.Motif.MARKET,de.quest.client.ui.QuestArtwork.Motif.FISHING,
                de.quest.client.ui.QuestArtwork.Motif.MINE,de.quest.client.ui.QuestArtwork.Motif.COMPANION,
                de.quest.client.ui.QuestArtwork.Motif.FAILING_HARVEST,de.quest.client.ui.QuestArtwork.Motif.FIELDS)) {
            for(var texture:List.of(motif.largeTexture(),motif.smallTexture())) {
                var area=de.quest.client.ui.QuestArtwork.contentArea(texture);
                try(var input=getClass().getClassLoader().getResourceAsStream("assets/"+texture.getNamespace()+"/"+texture.getPath())) {
                    var im=javax.imageio.ImageIO.read(input);int light=0;
                    assertEquals(im.getWidth(),area.textureWidth());assertEquals(im.getHeight(),area.textureHeight());
                    for(int x=area.x();x<area.x()+area.width();x++) {
                        int rgb=im.getRGB(x,area.y());
                        if((rgb>>16&255)>205 && (rgb>>8&255)>205 && (rgb&255)>205)light++;
                    }
                    assertTrue(light<area.width()*.95,"Pale source matting still reaches content: "+texture);
                }
            }
        }
    }
    @Test void actualNoticeAndJourneyRenderingUseTheVerifiedArchiveCrop() throws Exception {
        try(var theme=mockStatic(de.quest.client.ui.VillageUiTheme.class);
            var art=mockStatic(de.quest.client.ui.KingdomUiAssets.class);
            var tour=mockStatic(UiTestTour.class)) {
            art.when(()->de.quest.client.ui.KingdomUiAssets.closeBounds(anyInt(),anyInt(),anyInt(),anyInt())).thenCallRealMethod();
            theme.when(()->de.quest.client.ui.VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenAnswer(a->a.getArgument(1));
            var font=mock(net.minecraft.client.gui.Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            for(var step:List.of(new UiTestTourSteps.Step("noticeboard","known"),new UiTestTourSteps.Step("noticejourney","archive"))) {
                var screen=UiTestTourScreens.create(step);screen.width=700;screen.height=440;
                Field f=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
                var g=mock(de.quest.client.compat.GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
                theme.clearInvocations();screen.render(g,0,0,0);
                theme.verify(()->de.quest.client.ui.VillageUiTheme.blitRegionScaled(eq(g),eq(de.quest.client.ui.QuestArtwork.Motif.ARCHIVE.largeTexture()),
                        anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),intThat(y->y>=6),anyInt(),anyInt(),eq(192),eq(72)),atLeastOnce());
            }
        }
    }
    @Test void travelAlternativesKeepActualCostsAndUseSingularOnlyForOneCharge() {
        for(int charges:List.of(0,1,2)) {
            var content=(net.minecraft.network.chat.contents.TranslatableContents)WayshrineScreen.travelCosts(73L,charges).getContents();
            assertEquals(charges==1?"screen.village-quest.ui_review.travel_alternative_single":"screen.village-quest.ui_review.travel_alternatives",content.getKey());
            assertEquals(73L,content.getArgs()[0]);assertEquals(charges,content.getArgs()[1]);
        }
    }

    @Test void alliedNoticeBoardDrawsItsFinalBenefitOnlyOnce() throws Exception {
        try(var theme=mockStatic(de.quest.client.ui.VillageUiTheme.class)) {
            theme.when(()->de.quest.client.ui.VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenAnswer(a->a.getArgument(1));
            var screen=(GuildNoticeBoardScreen)UiTestTourScreens.create(new UiTestTourSteps.Step("noticeboard","allied"));
            var f=GuildNoticeBoardScreen.class.getDeclaredField("data");f.setAccessible(true);
            var payload=(de.quest.network.VillageNetworkPayloads.NoticeBoardPayload)f.get(screen);
            assertEquals(0,payload.nextThreshold());
            var font=mock(net.minecraft.client.gui.Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            var ff=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(screen,font);
            var g=mock(de.quest.client.compat.GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
            var draw=GuildNoticeBoardScreen.class.getDeclaredMethod("drawBondPath",de.quest.client.compat.GuiGraphics.class,int.class,int.class,int.class,int.class);
            draw.setAccessible(true);draw.invoke(screen,g,0,0,0,0);
            theme.verify(()->de.quest.client.ui.VillageUiTheme.drawStringScaled(eq(g),eq(font),eq(payload.nextPerk().getString()),
                    anyFloat(),anyFloat(),anyInt(),anyFloat()),times(1));
        }
    }
}
