package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GuildNoticePostTeaserTest {
    @Test
    void naturalVillageGuidesFreshPlayerThroughCuratedIntroduction() {
        assertEquals(GuildNoticePostTeaser.Stage.FIRST_FAVOR_AVAILABLE,
                GuildNoticePostTeaser.classify(true, false, true, false, false,
                        false, false, false));
        assertEquals(GuildNoticePostTeaser.Stage.FIRST_FAVOR_ACTIVE,
                GuildNoticePostTeaser.classify(true, false, false, true, false,
                        false, false, false));
        assertEquals(GuildNoticePostTeaser.Stage.MEET_VILLAGERS,
                GuildNoticePostTeaser.classify(true, false, false, false, true,
                        false, false, false));
        assertEquals(GuildNoticePostTeaser.Stage.GREETING_PROGRESS,
                GuildNoticePostTeaser.classify(true, false, false, false, true,
                        true, false, true));
        assertEquals(GuildNoticePostTeaser.Stage.WELCOME_ELSEWHERE,
                GuildNoticePostTeaser.classify(true, false, false, false, true,
                        true, false, false));
    }

    @Test
    void recognizedVillageHomesteadAndUnknownLocationsStayDistinct() {
        assertEquals(GuildNoticePostTeaser.Stage.CONNECT_VILLAGE,
                GuildNoticePostTeaser.classify(true, false, false, false, true,
                        false, true, false));
        assertEquals(GuildNoticePostTeaser.Stage.HOMESTEAD,
                GuildNoticePostTeaser.classify(false, true, false, false, true,
                        false, true, false));
        assertEquals(GuildNoticePostTeaser.Stage.UNKNOWN,
                GuildNoticePostTeaser.classify(false, false, false, false, false,
                        false, false, false));
    }

    @Test
    void naturalVillageContextWinsOverAnOverlappingHomesteadMarker() {
        assertEquals(GuildNoticePostTeaser.Stage.FIRST_FAVOR_AVAILABLE,
                GuildNoticePostTeaser.classify(true, true, true, false, false,
                        false, false, false));
    }

    @Test
    void incompleteWelcomeTakesPriorityOverDormantContactStoryMessage() {
        assertEquals(true, GuildNoticePostTeaser.shouldUseIntroTeaser(false));
        assertEquals(false, GuildNoticePostTeaser.shouldUseIntroTeaser(true));
    }
}
