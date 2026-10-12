package de.quest.data;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class QuestOfferNoticePersistenceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void combinedAnnouncementKeepsAllThreeExistingNoticeTokensAcrossSaveAndReload() {
        UUID id = UUID.randomUUID();
        QuestState state = QuestState.fromNbt(new CompoundTag());
        var player = state.getPlayerData(id);
        player.setDailyOfferNoticeDay(123);
        player.setWeeklyOfferNoticeCycle(45);
        player.setStoryOfferNoticeKey("archive:2");
        var restored = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(id);
        assertEquals(123, restored.getDailyOfferNoticeDay());
        assertEquals(45, restored.getWeeklyOfferNoticeCycle());
        assertEquals("archive:2", restored.getStoryOfferNoticeKey());
    }
}
