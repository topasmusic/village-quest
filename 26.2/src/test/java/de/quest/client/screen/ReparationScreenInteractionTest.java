package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.ReputationPayloads;
import de.quest.network.ReputationPayloads.*;
import de.quest.reputation.ReputationViewService.*;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ReparationScreenInteractionTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : List.of(net.minecraft.world.item.Items.WHEAT, net.minecraft.world.item.Items.OAK_PLANKS,
                net.minecraft.world.item.Items.IRON_INGOT)) item.builtInRegistryHolder().bindComponents(components);
    }
    private MockedStatic<Minecraft> client;
    @BeforeEach void client() {
        client = mockStatic(Minecraft.class);
        client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
    }
    @AfterEach void closeClient() { client.close(); }

    private static InteractionPayload snapshot(boolean preview) {
        var active = new CaseView(UUID.randomUUID(), false, 1, 0, 0, 0,
                List.of(new AidView(0, 0, 32, 64)));
        var view = new View(2, 7, true, true, -20, 0, 4, false, false, 100,
                0, 0, List.of(), List.of(), active);
        return new InteractionPayload(UUID.randomUUID(), preview, true, view, List.of(), "");
    }

    @Test void liveAidDeskMustNotPauseTheServerClockNeededForSubmission() {
        assertFalse(new ReparationScreen(snapshot(false), null).isPauseScreen(),
                "Issue #11: pausing the integrated server stalls the four-tick request cooldown and aid timer");
    }

    @Test void carriedWheatCanBeSubmittedAndAlternativeAidChosenAtBothGuiSizes() throws Exception {
        for (int[] size : new int[][]{{600, 450}, {320, 200}}) {
            var data = snapshot(false);
            var screen = prepared(data, size);
            try (var theme = mockStatic(VillageUiTheme.class); var network = mockStatic(ClientPlayNetworking.class)) {
                theme.when(() -> VillageUiTheme.ellipsize(any(), anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
                drawAmends(screen);
                assertTrue(screen.mouseClicked(pointer(screen, 300, 240), false));
                network.verify(() -> ClientPlayNetworking.send(new ActionPayload(data.session(), data.view().reparation().id(),
                        7, ActionPayload.SUBMIT, 0, 0)));
                assertTrue(screen.mouseClicked(pointer(screen, 330, 198), false));
                network.verify(() -> ClientPlayNetworking.send(new ActionPayload(data.session(), data.view().reparation().id(),
                        7, ActionPayload.CHOOSE, 1, 0)));
            }
        }
    }

    @Test void previewCannotSendAidOrConsumeAnything() throws Exception {
        var screen = prepared(snapshot(true), new int[]{600, 450});
        try (var theme = mockStatic(VillageUiTheme.class); var network = mockStatic(ClientPlayNetworking.class)) {
            theme.when(() -> VillageUiTheme.ellipsize(any(), anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
            drawAmends(screen);
            screen.mouseClicked(pointer(screen, 300, 240), false);
            network.verifyNoInteractions();
        }
    }

    private static ReparationScreen prepared(InteractionPayload data, int[] size) throws Exception {
        var screen = new ReparationScreen(data, null);
        screen.width = size[0]; screen.height = size[1];
        var field = Screen.class.getDeclaredField("font"); field.setAccessible(true);
        field.set(screen, mock(Font.class));
        return screen;
    }
    private static void drawAmends(ReparationScreen screen) throws Exception {
        var method = ReparationScreen.class.getDeclaredMethod("amends", GuiGraphics.class,
                int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(screen, mock(GuiGraphics.class), (screen.width - 560) / 2, (screen.height - 315) / 2, -1, -1);
    }
    private static MouseButtonEvent pointer(ReparationScreen screen, int x, int y) {
        double scale = 1 / screen.responsiveDrag(1, 560, 315);
        int left = (screen.width - 560) / 2, top = (screen.height - 315) / 2;
        return new MouseButtonEvent(screen.width / 2.0 + (left + x - screen.width / 2.0) * scale,
                screen.height / 2.0 + (top + y - screen.height / 2.0) * scale,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
    }
}
