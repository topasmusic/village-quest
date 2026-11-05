package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.guildtown.GuildTownAfterstoryLedger;
import de.quest.guildtown.GuildTownProgress;
import de.quest.guildtown.GuildTownStory;
import de.quest.shrine.VillageRequestType;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildTownAfterstoryLedgerTest {
    private static final UUID PLAYER = UUID.fromString("981997ab-045d-48e2-af2a-0a1f5c25b6c1");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void oneVillageFollowUpRetainsItsOfferAfterSaveAndCannotPayTwice() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        assertTrue(GuildTownAfterstoryLedger.begin(data, 3, VillageRequestType.FORGE_IRON));
        assertFalse(GuildTownAfterstoryLedger.begin(data, 3, VillageRequestType.FORGE_COAL));
        assertEquals(GuildTownAfterstoryLedger.State.ACTIVE,
                GuildTownAfterstoryLedger.state(data, 3));
        assertEquals(VillageRequestType.FORGE_IRON,
                GuildTownAfterstoryLedger.request(reload(state), 3));
        assertEquals(GuildTownAfterstoryLedger.State.NOT_STARTED,
                GuildTownAfterstoryLedger.state(reload(state), 4));
        assertTrue(GuildTownAfterstoryLedger.complete(data, 3));
        assertFalse(GuildTownAfterstoryLedger.complete(data, 3));
        assertEquals(GuildTownAfterstoryLedger.State.COMPLETE,
                GuildTownAfterstoryLedger.state(reload(state), 3));
    }

    @Test
    void completedStoryUnlocksOnlyItsOriginalHistoricalVillage() {
        PlayerQuestData data = QuestState.fromNbt(new CompoundTag()).getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.beginStory(data, GuildTownStory.SPARKS_FOR_THE_ROAD, 3,
                GuildTownProgress.PREVENTIVE));
        assertTrue(GuildTownProgress.markStoryReady(data, GuildTownStory.SPARKS_FOR_THE_ROAD));
        assertTrue(GuildTownProgress.completeStory(data, GuildTownStory.SPARKS_FOR_THE_ROAD));
        assertTrue(GuildTownProgress.storyCompletedAt(data, GuildTownStory.SPARKS_FOR_THE_ROAD, 3));
        assertFalse(GuildTownProgress.storyCompletedAt(data, GuildTownStory.SPARKS_FOR_THE_ROAD, 4));
    }

    private static PlayerQuestData reload(QuestState state) {
        return QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
    }
}
