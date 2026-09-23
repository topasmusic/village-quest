package de.quest.questmaster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GuildPathPayloadBuilderTest {
    @Test
    void noticeBoardNodeFollowsWelcomeThenFirstCommission() {
        assertEquals(0, GuildPathPayloadBuilder.noticeBoardStage(false, 0, false));
        assertEquals(1, GuildPathPayloadBuilder.noticeBoardStage(true, 0, false));
        assertEquals(2, GuildPathPayloadBuilder.noticeBoardStage(true, 1, false));
    }

    @Test
    void oldVisibleVillageRequestProgressRemainsCompleted() {
        assertEquals(2, GuildPathPayloadBuilder.noticeBoardStage(false, 0, true));
    }

    @Test
    void malformedCommissionProgressCannotUnlockBeforeWelcome() {
        assertEquals(0, GuildPathPayloadBuilder.noticeBoardStage(false, Integer.MAX_VALUE, false));
        assertEquals(1, GuildPathPayloadBuilder.noticeBoardStage(true, -1, false));
    }
}
