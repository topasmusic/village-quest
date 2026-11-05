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
        int storyContext = source.indexOf("GuildTownService.createNoticePostStoryContext");
        int introGate = source.indexOf("!VillageWelcomeService.isCompleted(data) || resolution == null", storyContext);
        int introSnapshot = source.indexOf("GuildNoticePostTeaser.classify", introGate);
        int storySnapshot = source.indexOf("GuildTownService.noticePostUiState", introSnapshot);
        assertTrue(storyContext >= 0);
        assertTrue(introGate > storyContext);
        assertTrue(introSnapshot > introGate);
        assertTrue(storySnapshot > introSnapshot);
    }

    @Test
    void teaserReadsContextWithoutInventingContactOrRouteState() throws Exception {
        String source = boardSource();
        int teaserStart = source.indexOf("private static void sendJourney");
        int teaserEnd = source.indexOf("\n    static void handleJourneyAction", teaserStart);
        String teaser = source.substring(teaserStart, teaserEnd);

        assertTrue(teaser.contains("currentVillage(world, pos)"));
        assertTrue(teaser.contains("TradeRouteService.isNearPlayerYard"));
        assertTrue(teaser.contains("new VillageNetworkPayloads.NoticeJourneyPayload"));
        assertFalse(teaser.contains("VillageContactService.establish"));
        assertFalse(teaser.contains("setTradeRoute"));
        assertFalse(teaser.contains("bindPlayerYard"));
    }

    private static String boardSource() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "de", "quest", "shrine",
                "VillageNoticeBoardService.java"));
    }
}
