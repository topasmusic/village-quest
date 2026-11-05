package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class QuestMasterRefreshLayoutTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int constant(String name) throws Exception {
        var field = QuestMasterScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static Font font(Screen screen, int glyphWidth) throws Exception {
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * glyphWidth);
        var height = Font.class.getDeclaredField("lineHeight");
        height.setAccessible(true);
        height.setInt(font, 9);
        var field = Screen.class.getDeclaredField("font");
        field.setAccessible(true);
        field.set(screen, font);
        return font;
    }

    private static QuestMasterScreen.EntryView entry(String id, String category, String title,
                                                     String subtitle, String party) {
        return new QuestMasterScreen.EntryView(id, category, Component.literal(title),
                Component.literal(subtitle), Component.literal("Available"), true, Component.literal(party),
                List.of(), List.of(), List.of(), 0, Component.empty(), false,
                0, Component.empty(), false, false);
    }

    private static QuestMasterScreen.QuestMasterData specialData(int first, int count) {
        var entries = IntStream.range(first, first + count)
                .mapToObj(i -> entry("special_" + i, "special", "Special " + i, "Relic", "")).toList();
        return new QuestMasterScreen.QuestMasterData(-1, Component.literal("Questmaster"),
                List.of(new QuestMasterScreen.CategoryView("special", Component.literal("Special"), count)),
                entries, null, 0);
    }

    private static MouseButtonEvent pointer(QuestMasterScreen screen, int x, int y) {
        double scale = 1 / screen.responsiveDrag(1, 560, 315);
        return new MouseButtonEvent(screen.width / 2.0 + (x - 280) * scale,
                screen.height / 2.0 + (y - 157.5) * scale,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
    }

    @Test void localizedTwoLineTitlesClearSoloLabelAndScrollableBody() throws Exception {
        try (var client = mockStatic(Minecraft.class);
             var theme = mockStatic(VillageUiTheme.class, CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for (String locale : List.of("en_us", "de_de", "es_es")) {
                try (var stream = getClass().getResourceAsStream("/assets/village-quest/lang/" + locale + ".json")) {
                    assertNotNull(stream);
                    var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                    String title = catalog.get("quest.village-quest.daily.pet_collar.title").getAsString();
                    String category = catalog.get("screen.village-quest.questmaster.category.daily").getAsString();
                    String solo = catalog.get("screen.village-quest.questmaster.party.status.solo").getAsString();
                    var entry = entry("daily_main", "daily", title, category, solo);
                    for (int width : new int[]{4, 6, 8}) {
                        var screen = new QuestMasterScreen(specialData(0, 1));
                        var font = font(screen, width);
                        var graphics = mock(GuiGraphics.class);
                        when(graphics.pose()).thenReturn(new Matrix3x2fStack(32));
                        var draw = QuestMasterScreen.class.getDeclaredMethod("drawDetailHeader", GuiGraphics.class,
                                int.class, int.class, QuestMasterScreen.EntryView.class);
                        draw.setAccessible(true);
                        theme.clearInvocations();
                        draw.invoke(screen, graphics, 0, 0, entry);
                        var labels = ArgumentCaptor.forClass(String.class);
                        var ys = ArgumentCaptor.forClass(Float.class);
                        var scales = ArgumentCaptor.forClass(Float.class);
                        theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics), eq(font), labels.capture(),
                                anyFloat(), ys.capture(), anyInt(), scales.capture()), atLeastOnce());
                        float titleBottom = 0;
                        float subtitleY = -1;
                        int titleLines = 0;
                        for (int i = 0; i < labels.getAllValues().size(); i++) {
                            float scale = scales.getAllValues().get(i);
                            float y = ys.getAllValues().get(i);
                            if (Math.abs(scale - 1.08f) < .001f) {
                                titleBottom = Math.max(titleBottom, y + font.lineHeight * scale);
                                titleLines++;
                            }
                            if (Math.abs(scale - .78f) < .001f) subtitleY = y;
                        }
                        assertTrue(subtitleY >= titleBottom + 3, locale + " title overlaps Daily / Solo");
                        assertTrue(subtitleY + font.lineHeight * .78f + 1 <= constant("DETAIL_HEADER_HEIGHT"),
                                locale + " subtitle reaches the scrollable body");
                        if (width >= 6) assertEquals(2, titleLines, "Exercise the reported two-line heading");
                        assertEquals(260, constant("DETAIL_BODY_Y") + constant("DETAIL_BODY_HEIGHT"),
                                "More header space must retain the footer boundary");
                    }
                }
            }
        }
    }

    @Test void scrollingSpecialsSurvivesRepeatedFreshSnapshotsAndStillSelectsVisibleCards() throws Exception {
        try (var client = mockStatic(Minecraft.class)) {
            var minecraft = mock(Minecraft.class);
            when(minecraft.getSoundManager()).thenReturn(mock(net.minecraft.client.sounds.SoundManager.class));
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            for (int[] size : new int[][]{{600, 450}, {320, 200}}) {
                var screen = new QuestMasterScreen(specialData(0, 12));
                screen.width = size[0];
                screen.height = size[1];
                font(screen, 6);
                screen.updateData(specialData(0, 12));
                var mouse = pointer(screen, 160, 150);
                for (int i = 0; i < 16; i++) {
                    assertTrue(screen.mouseScrolled(mouse.x(), mouse.y(), 0, -1));
                    int offset = (int) field(screen, "entryListScrollOffset");
                    assertTrue(offset > 0);
                    screen.updateData(specialData(0, 12));
                    assertEquals(offset, field(screen, "entryListScrollOffset"), "Refresh must retain manual scroll");
                    assertEquals("special_0", field(screen, "selectedEntryId"), "Browsing must not change selection");
                }
                int offset = (int) field(screen, "entryListScrollOffset");
                int clickedIndex = (150 - 66 + offset) / 55;
                assertTrue(screen.mouseClicked(mouse, false));
                assertEquals("special_" + clickedIndex, field(screen, "selectedEntryId"));
            }
        }
    }

    @Test void shrinkingListsClampScrollAndRemovedSelectionsRevealTheirReplacement() throws Exception {
        try (var client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen = new QuestMasterScreen(specialData(0, 12));
            screen.width = 600;
            screen.height = 450;
            font(screen, 6);
            screen.updateData(specialData(0, 12));
            var mouse = pointer(screen, 160, 150);
            for (int i = 0; i < 40; i++) screen.mouseScrolled(mouse.x(), mouse.y(), 0, -1);
            screen.updateData(specialData(0, 6));
            assertTrue((int) field(screen, "entryListScrollOffset") > 0);
            assertEquals(field(screen, "entryListScrollMax"), field(screen, "entryListScrollOffset"));
            screen.updateData(specialData(1, 6));
            assertEquals("special_1", field(screen, "selectedEntryId"));
            assertEquals(0, field(screen, "entryListScrollOffset"));
            screen.updateData(specialData(0, 0));
            assertEquals("", field(screen, "selectedEntryId"));
            assertEquals(0, field(screen, "entryListScrollOffset"));
        }
    }
}
