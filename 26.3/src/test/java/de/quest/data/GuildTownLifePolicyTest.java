package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.guildtown.GuildTownCommission;
import de.quest.guildtown.GuildTownLifePolicy;
import de.quest.guildtown.GuildTownProgress;
import de.quest.guildtown.GuildTownStory;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildTownLifePolicyTest {
    private static final UUID OWNER = UUID.fromString("eb334086-e634-44f9-a74c-5d396d36824b");
    private static final UUID HELPER = UUID.fromString("50d4a5f0-a112-4367-a94f-f1d453d18da7");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void affectedLocalStoryPausesWithoutLosingProgressAcrossReload() {
        QuestState quests = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = quests.getPlayerData(OWNER);
        data.setTradeRouteInt("bond_village_count", 1);
        data.setTradeRouteInt("bond_village_0_x", 128);
        data.setTradeRouteInt("bond_village_0_z", -224);
        assertTrue(GuildTownProgress.beginStory(data, GuildTownStory.SHARED_TABLE,
                0, GuildTownProgress.RECOVERY));
        GuildTownProgress.addStoryProgress(data, GuildTownStory.SHARED_TABLE, 1, 7, 24);

        assertFalse(GuildTownLifePolicy.pauseAffected(quests, data,
                VillageLifeState.VillageKey.overworld(500, 500)).storyPaused());
        assertTrue(GuildTownLifePolicy.pauseAffected(quests, data,
                VillageLifeState.VillageKey.overworld(128, -224)).storyPaused());
        assertFalse(GuildTownLifePolicy.pauseAffected(quests, data,
                VillageLifeState.VillageKey.overworld(128, -224)).storyPaused());

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(quests)).getPlayerData(OWNER);
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.storyState(loaded, GuildTownStory.SHARED_TABLE));
        assertEquals(7, GuildTownProgress.storyProgress(loaded, GuildTownStory.SHARED_TABLE, 1));
    }

    @Test
    void guildHelperCommissionUsesOwnerQualifiedVillageIdentity() {
        QuestState quests = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData helper = quests.getPlayerData(HELPER);
        GuildTownCommission commission = GuildTownCommission.REINFORCED_PLOUGHS;
        assertTrue(GuildTownProgress.beginCommission(helper, commission,
                new GuildTownProgress.VillageIdentity(OWNER, 128, -224),
                new GuildTownProgress.VillageIdentity(OWNER, 640, 96)));
        GuildTownProgress.addCommissionProgress(helper, commission, 1, 5, commission.firstTarget());

        assertTrue(GuildTownLifePolicy.pauseAffected(quests, helper,
                VillageLifeState.VillageKey.overworld(640, 96)).commissionPaused());
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.commissionState(helper, commission));
        assertEquals(5, GuildTownProgress.commissionProgress(helper, commission, 1));
    }
}
