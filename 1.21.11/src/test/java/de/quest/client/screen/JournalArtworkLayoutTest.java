package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.client.ui.VillageUiTheme;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class JournalArtworkLayoutTest {
    @Test void longUnbrokenLocalizedWordsStayInsideTheNextActionTextColumn() {
        var graphics=mock(de.quest.client.compat.GuiGraphics.class);
        when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));
        var font=mock(Font.class);
        when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
        VillageUiTheme.drawWrappedScaled(graphics,font,
                "Wiedergutmachungsunterstuetzungsverpflichtungen",0,0,84,0xFF2D1B12,.78f,3);
        var text=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(graphics,atLeastOnce()).drawString(eq(font),text.capture(),eq(0),eq(0),anyInt(),eq(false));
        assertTrue(text.getAllValues().stream().allMatch(s -> font.width(s)*.78f<=84),
                "A single word must not escape into another column; full text stays available in the tooltip");
    }
    @Test void nextActionUsesActualNestedQuestTitleAndAppropriateStoryArtwork() {
        var card = card("overview_next", Component.literal("Next step"),
                Component.translatable("quest.village-quest.daily.wheat.title"));
        assertTrue(JournalScreen.artworkFor(card).isPresent());
        assertTrue(JournalScreen.cardHeadingInset(card) >= 12 + 64 + 8);
        var quiet = card("story_silent_forge", Component.literal("Story"), Component.empty());
        assertEquals(de.quest.client.ui.QuestArtwork.Motif.SILENT_FORGE,
                JournalScreen.artworkFor(quiet).orElseThrow(), "A cold-forge story must use its own state image");
        assertTrue(JournalScreen.cardHeadingInset(quiet) >= 12 + 64 + 8);
        var unknown = card("story_external", Component.literal("Unknown story"), Component.empty());
        assertTrue(JournalScreen.artworkFor(unknown).isEmpty(), "Unknown stories must not borrow an unrelated image");
        assertEquals(20, JournalScreen.cardHeadingInset(unknown));
    }

    @Test void illustratedOverviewWrapsAndMeasuresTheSameReducedHeaderWidth() throws Exception {
        try (var client = mockStatic(Minecraft.class); var theme = mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            theme.when(() -> VillageUiTheme.ellipsize(any(), anyString(), anyInt())).thenCallRealMethod();
            var screen = new JournalScreen(null);
            var font = mock(Font.class);
            when(font.width(anyString())).thenAnswer(c -> ((String) c.getArgument(0)).length() * 6);
            var field = Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen, font);
            var title = Component.translatableWithFallback("quest.village-quest.daily.wheat.title",
                    "A long quest name that must wrap inside the illustrated heading");
            var card = card("overview_next", Component.literal("Next"), title);
            Method measure = JournalScreen.class.getDeclaredMethod("collapsedCardHeight", JournalCard.class, int.class);
            measure.setAccessible(true);
            int height = (int) measure.invoke(screen, card, 298);
            assertTrue(height > 34, "Long localized title needs additional measured rows");
            assertTrue(height < 100, "Thumbnail must not force an unbounded heading");
            Method draw = JournalScreen.class.getDeclaredMethod("drawCard", de.quest.client.compat.GuiGraphics.class,
                    JournalCard.class, int.class, int.class, int.class, int.class, int.class, int.class);
            draw.setAccessible(true);
            draw.invoke(screen, mock(de.quest.client.compat.GuiGraphics.class), card, 0, 0, 298, height, -1, -1);
            theme.verify(() -> VillageUiTheme.blitRegionScaled(any(), eq(de.quest.client.ui.QuestArtwork.Motif.BAKEHOUSE.smallTexture()),
                    eq(12), eq(5), eq(64), eq(24),eq(0),eq(0),eq(64),eq(24),eq(64),eq(24)));
        }
    }

    private static JournalCard card(String id, Component title, Component subtitle) {
        return new JournalCard(id, title, subtitle, List.of(), 0, -1);
    }
}
