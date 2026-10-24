package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.ui.VillageUiTheme;
import de.quest.config.WorldQuestSettings;
import de.quest.network.QuestSetupPayloads;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

final class QuestSetupInputTest {
    @Test void liveMinimapOverridesBothConflictingDefaultsAndSurvivesSaveUpdateAndReconnect() throws Exception {
        try (var client = mockStatic(net.minecraft.client.Minecraft.class);
             var configs = mockStatic(VillageQuestClientConfig.class);
             var network = mockStatic(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.class)) {
            client.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mock(net.minecraft.client.Minecraft.class));
            var config = mock(VillageQuestClientConfig.class);
            configs.when(VillageQuestClientConfig::get).thenReturn(config);
            when(config.savePresentation(any())).thenReturn(true);
            network.when(() -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(QuestSetupPayloads.Save.ID)).thenReturn(true);
            var personal = QuestSetupScreen.class.getDeclaredField("personal"); personal.setAccessible(true);
            var save = QuestSetupScreen.class.getDeclaredMethod("save"); save.setAccessible(true);
            for (boolean live : new boolean[]{true, false}) {
                when(config.presentation()).thenReturn(new VillageQuestClientConfig.Presentation(true, !live, true, true, true, true));
                var view = new QuestSetupPayloads.View(UUID.randomUUID(), false, false, WorldQuestSettings.recommended(), true, live, 0);
                var screen = new QuestSetupScreen(view);
                assertEquals(live, ((boolean[])personal.get(screen))[1], "Open reflects live server state, not the saved default");
                save.invoke(screen);
                network.verify(() -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new QuestSetupPayloads.Save(view.session(), true, false, view.settings(), true, live)));
                var updated = new QuestSetupPayloads.View(UUID.randomUUID(), false, false, view.settings(), true, !live, 0);
                screen.update(updated);
                assertEquals(!live, ((boolean[])personal.get(screen))[1], "Server update replaces the prior session state");
                assertEquals(!live, ((boolean[])personal.get(new QuestSetupScreen(updated)))[1], "Reopened/reconnected screen uses its new server session");
            }
        }
    }
    @Test void responsiveWorldControlsAreReadOnlyForMembersAndPersonalControlsStillWork() throws Exception {
        try (var client = mockStatic(net.minecraft.client.Minecraft.class); var theme = mockStatic(VillageUiTheme.class)) {
            client.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mock(net.minecraft.client.Minecraft.class));
            for (int width : new int[]{420, 900}) for (boolean editable : new boolean[]{false, true}) {
                var original = WorldQuestSettings.recommended();
                var screen = spy(new QuestSetupScreen(new QuestSetupPayloads.View(UUID.randomUUID(), true, editable, original, true, false, 0)));
                screen.width = width; screen.height = width * 3 / 5;
                var font = mock(Font.class); when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length() * 6);
                var field = Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen, font);
                var g = mock(GuiGraphics.class); when(g.pose()).thenReturn(new Matrix3x2fStack(16));
                doThrow(new IllegalStateException("Can only blur once per frame")).when(screen).renderBackground(any(), anyInt(), anyInt(), anyFloat());
                screen.render(g, -100, -100, 0);
                verify(screen, never()).renderBackground(any(), anyInt(), anyInt(), anyFloat());
                click(screen, 70, 120);
                var settings = QuestSetupScreen.class.getDeclaredField("settings"); settings.setAccessible(true);
                assertEquals(editable, ((WorldQuestSettings)settings.get(screen)).minecraftCalendar());
                click(screen, 320, 109);
                var personal = QuestSetupScreen.class.getDeclaredField("personal"); personal.setAccessible(true);
                assertFalse(((boolean[])personal.get(screen))[0], "Member presentation remains editable at both panel scales");
            }
        }
    }
    private static void click(QuestSetupScreen screen, int x, int y) {
        double scale = 1 / screen.responsiveDrag(1, 560, 344);
        double mx = screen.width / 2.0 + (x - 280) * scale;
        double my = screen.height / 2.0 + (y - 172) * scale;
        screen.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }
}
