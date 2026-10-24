package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class QuestMasterTimerLayoutTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void localizedDailyAndWeeklyTimersAndHoverStayInsideTheParchment() throws Exception {
        try (var client = mockStatic(Minecraft.class);
             var theme = mockStatic(VillageUiTheme.class, CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var draw = QuestMasterScreen.class.getDeclaredMethod("drawFooter", GuiGraphics.class,
                    int.class, int.class, int.class, int.class);
            draw.setAccessible(true);
            boolean exercisedTwoLines = false;
            for (String locale : List.of("en_us", "de_de", "es_es")) {
                try (var stream = getClass().getResourceAsStream("/assets/village-quest/lang/" + locale + ".json")) {
                    assertNotNull(stream);
                    var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String category : List.of("daily", "weekly")) {
                        String key = "screen.village-quest.questmaster." + category
                                + (category.equals("daily") ? ".next_reset" : ".next_reset_in");
                        String timer = String.format(catalog.get(key).getAsString(), "6.25 Minecraft days (Overworld)");
                        var entry = new QuestMasterScreen.EntryView(category + "_main", category,
                                Component.literal("Complete"), Component.empty(),
                                Component.translatable("screen.village-quest.questmaster.status.completed"),
                                false, Component.empty(), List.of(), List.of(Component.literal(timer)), List.of(),
                                0, Component.empty(), false, 0, Component.empty(), false, false);
                        for (int[] size : new int[][]{{600, 450}, {320, 200}}) {
                            for (int glyphWidth : new int[]{4, 6, 8}) {
                                var screen = new QuestMasterScreen(new QuestMasterScreen.QuestMasterData(-1,
                                        Component.literal("Questmaster"), List.of(new QuestMasterScreen.CategoryView(
                                        category, Component.literal(category), 1)), List.of(entry), null, 0));
                                screen.width = size[0]; screen.height = size[1];
                                screen.selectTourCategory(category);
                                var font = mock(Font.class);
                                when(font.width(anyString())).thenAnswer(c -> ((String) c.getArgument(0)).length() * glyphWidth);
                                var height = Font.class.getDeclaredField("lineHeight"); height.setAccessible(true); height.setInt(font, 9);
                                var fontField = Screen.class.getDeclaredField("font"); fontField.setAccessible(true); fontField.set(screen, font);
                                var graphics = mock(GuiGraphics.class);
                                when(graphics.pose()).thenReturn(new Matrix3x2fStack(32));
                                int left = (screen.width - 560) / 2, top = (screen.height - 315) / 2;
                                theme.clearInvocations();
                                draw.invoke(screen, graphics, left, top, left + 120, top + 268);
                                var ys = ArgumentCaptor.forClass(Float.class);
                                var scales = ArgumentCaptor.forClass(Float.class);
                                theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics), eq(font), anyString(),
                                        anyFloat(), ys.capture(), anyInt(), scales.capture()), atLeastOnce());
                                exercisedTwoLines |= ys.getAllValues().size() == 2;
                                for (int i = 0; i < ys.getAllValues().size(); i++) {
                                    float y = ys.getAllValues().get(i) - top;
                                    assertTrue(y >= 262, "Timer must clear the list ending at258");
                                    assertTrue(y + 9 * scales.getAllValues().get(i) <= 278,
                                            locale + " " + category + " timer leaves parchment ending at282");
                                }
                                verify(graphics).setTooltipForNextFrame(eq(font), argThat((Component text) -> timer.equals(text.getString())),
                                        eq(left + 120), eq(top + 268));
                                clearInvocations(graphics);
                                draw.invoke(screen, graphics, left, top, left + 120, top + 290);
                                verify(graphics, never()).setTooltipForNextFrame(any(Font.class), any(Component.class), anyInt(), anyInt());
                            }
                        }
                    }
                }
            }
            assertTrue(exercisedTwoLines, "Exercise wrapped timers, not just one short line");
        }
    }
}
