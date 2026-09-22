package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GuildNoticePostTeaserFlowContractTest {
    @Test
    void incompleteWelcomeTeaserPrecedesDormantContactStoryResolution() throws Exception {
        String source = boardSource();
        int missingRouteBranch = source.indexOf("if (view == null)");
        int introGate = source.indexOf("GuildNoticePostTeaser.shouldUseIntroTeaser", missingRouteBranch);
        int storyContext = source.indexOf("GuildTownService.createNoticePostStoryContext", missingRouteBranch);
        int storyResolver = source.indexOf("GuildTownService.NoticePostStoryResolution resolution = use", storyContext);
        int storyPresentation = source.indexOf("GuildTownService.presentNoticePostStory", storyResolver);

        assertTrue(missingRouteBranch >= 0);
        assertTrue(introGate > missingRouteBranch);
        assertTrue(storyContext > introGate);
        assertTrue(storyResolver > storyContext);
        assertTrue(storyPresentation > storyResolver);
    }

    @Test
    void teaserReadsContextWithoutInventingContactOrRouteState() throws Exception {
        String source = boardSource();
        int teaserStart = source.indexOf("private static void sendContextTeaser");
        int teaserEnd = source.indexOf("\n    private static void send(", teaserStart);
        String teaser = source.substring(teaserStart, teaserEnd);

        assertTrue(teaser.contains("currentVillage(world, pos)"));
        assertTrue(teaser.contains("TradeRouteService.isNearPlayerYard"));
        assertTrue(teaser.contains("new ClickEvent.RunCommand(\"/vq questmaster\")"));
        assertFalse(teaser.contains("VillageContactService.establish"));
        assertFalse(teaser.contains("setTradeRoute"));
        assertFalse(teaser.contains("bindPlayerYard"));
    }

    private static String boardSource() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "de", "quest", "shrine",
                "VillageNoticeBoardService.java"));
    }
}
