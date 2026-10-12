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
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : List.of(Items.LANTERN, Items.CREEPER_HEAD, Items.ZOMBIE_HEAD, Items.SPIDER_EYE,
                Items.BLAZE_ROD, Items.GHAST_TEAR, Items.SHULKER_SHELL, Items.CHISELED_BOOKSHELF, Items.CAMPFIRE))
            item.builtInRegistryHolder().bindComponents(components);
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
            assertEquals(90, body.invoke(screen, 0));
            assertEquals(count - 1, hovered.invoke(screen, 168, 65, 0, 0));
            assertEquals(count == 1 ? 0 : 1, hovered.invoke(screen, 282, 65, 0, 0));
            assertEquals(-1, hovered.invoke(screen, 180, 95, 0, 0));
        }
    }

    @Test void activePrimaryButtonsStillGiveWarmHoverFeedback() {
        var graphics = mock(de.quest.client.compat.GuiGraphics.class);
        when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        de.quest.client.ui.VillageUiTheme.drawButton(graphics, font, 10, 20, 90, 14,
                "Update progress", true, true, true);
        verify(graphics).fill(eq(12), eq(22), eq(98), eq(32), anyInt());
        clearInvocations(graphics);
        de.quest.client.ui.VillageUiTheme.drawButton(graphics, font, 10, 20, 90, 14,
                "Update progress", true, false, true);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        de.quest.client.ui.VillageUiTheme.drawButton(graphics, font, 10, 20, 90, 14,
                "Update progress", false, true, true);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
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
        verify(graphics).drawString(eq(font), anyString(), anyInt(), eq(92), anyInt(), eq(false));
    }
}
