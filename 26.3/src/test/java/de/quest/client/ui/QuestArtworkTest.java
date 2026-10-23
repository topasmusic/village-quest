package de.quest.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class QuestArtworkTest {
    @Test void frozenRewardKeysUseTheSameFamilyAsTheAcceptedQuest() {
        assertEquals(QuestArtwork.Motif.FORGE, QuestArtwork.forQuest("daily:SMITH_SMELTING", null).orElseThrow());
        assertEquals(QuestArtwork.Motif.BAKEHOUSE, QuestArtwork.forQuest("weekly:BAKEHOUSE_STOCK", null).orElseThrow());
        assertTrue(QuestArtwork.forQuest("daily:EXTERNAL", null).isEmpty());
    }
    @Test void dailyAndWeeklyShareCraftFamiliesWithoutLocalizedWordMatching() {
        assertEquals(QuestArtwork.Motif.BAKEHOUSE, motif("daily.wheat"));
        assertEquals(QuestArtwork.Motif.BAKEHOUSE, motif("weekly.bakehouse"));
        assertEquals(QuestArtwork.Motif.FORGE, motif("daily.smelt"));
        assertEquals(QuestArtwork.Motif.FORGE, motif("weekly.smith"));
        for (String localized : List.of("Backstubenhilfe", "Bakehouse Help", "Ayuda en la panadería")) {
            assertTrue(QuestArtwork.forComponent(Component.literal(localized)).isEmpty());
        }
    }

    @Test void titleSurvivesTranslatableWrappersAndStyledSiblings() {
        var title = Component.translatable("quest.village-quest.daily.wood.title");
        assertEquals(QuestArtwork.Motif.WORKSHOP, QuestArtwork.forComponent(
                Component.translatable("screen.village-quest.journal.next", title)).orElseThrow());
        assertEquals(QuestArtwork.Motif.WORKSHOP, QuestArtwork.forComponent(
                Component.literal("Quest: ").append(title)).orElseThrow());
    }

    @Test void realContractIdentityWinsOverMisleadingNamesOrDisplayFallbacks() {
        assertEquals(QuestArtwork.Motif.WATCH, QuestArtwork.forQuest("wool_before_rain",
                Component.translatable("quest.village-quest.daily.wool.title")).orElseThrow());
        assertEquals(QuestArtwork.Motif.WATCH, motif("pilgrim.contract.quench_for_the_hall"));
        assertEquals(QuestArtwork.Motif.WORKSHOP, QuestArtwork.forQuest("daily_first_WOODCUTTING",
                Component.empty()).orElseThrow());
        assertEquals(QuestArtwork.Motif.NETHER, motif("pilgrim.contract.ash_on_the_pass"));
        assertEquals(QuestArtwork.Motif.END, motif("pilgrim.contract.stillness_beyond_the_gate"));
    }

    @Test void storyChapterDoesNotShowAnOperatingForgeOrSpoilUnknownContent() {
        var quiet = motif("story.silent_forge.chapter_2");
        assertEquals(QuestArtwork.Motif.SILENT_FORGE, quiet);
        assertTrue(quiet.hasIllustration());
        assertEquals(QuestArtwork.Motif.FAILING_HARVEST, motif("story.failing_harvest"));
        assertTrue(QuestArtwork.forComponent(Component.translatable(
                "quest.village-quest.story.external_story.chapter_1.title")).isEmpty());
        assertTrue(QuestArtwork.forQuest(null, null).isEmpty());
    }

    @Test void everyMotifHasRealSmallAndSeparateLargePngs() throws IOException {
        for (var motif : QuestArtwork.Motif.values()) {
            assertTrue(motif.hasIllustration(), motif + " has unfinished artwork");
            var small = load(motif.smallTexture().getPath());
            var large = load(motif.largeTexture().getPath());
            assertEquals(64, small.getWidth()); assertEquals(24, small.getHeight());
            assertEquals(192, large.getWidth()); assertEquals(72, large.getHeight());
            boolean distinct = false;
            for (int y = 0; y < 72; y++) for (int x = 0; x < 192; x++) {
                distinct |= large.getRGB(x, y) != small.getRGB(x / 3, y / 3);
            }
            assertTrue(distinct, motif + " needs a separately authored preview, not a 3x copy");
        }
    }

    private static QuestArtwork.Motif motif(String suffix) {
        return QuestArtwork.forComponent(Component.translatable(
                "quest.village-quest." + suffix + ".title")).orElseThrow();
    }

    private static java.awt.image.BufferedImage load(String path) throws IOException {
        try (var in = QuestArtworkTest.class.getResourceAsStream("/assets/village-quest/" + path)) {
            assertNotNull(in, path);
            var image = ImageIO.read(in);
            assertNotNull(image, path);
            return image;
        }
    }
}
