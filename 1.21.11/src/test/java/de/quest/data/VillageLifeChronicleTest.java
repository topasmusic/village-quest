package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.VillageLifeChronicle;
import de.quest.guildtown.GuildTownProgress;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageLifeChronicleTest {
    private static final UUID PLAYER = UUID.fromString("71a84590-53d5-446e-8505-72f9caadf680");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void abandonmentAndRecoveryRecordOncePerCycleAcrossReload() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        VillageLifeState.VillageKey village = VillageLifeState.VillageKey.overworld(128, -224);

        assertTrue(VillageLifeChronicle.record(data, PLAYER, village, 1, false, 200));
        assertFalse(VillageLifeChronicle.record(data, PLAYER, village, 1, false, 300));
        assertTrue(VillageLifeChronicle.record(data, PLAYER, village, 1, true, 500));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertFalse(VillageLifeChronicle.record(loaded, PLAYER, village, 1, true, 600));
        assertTrue(VillageLifeChronicle.record(loaded, PLAYER, village, 2, false, 800));
        assertEquals(3, GuildTownProgress.personalChronicle(loaded).size());
        assertEquals(128, GuildTownProgress.personalChronicle(loaded).getFirst().village().x());
    }
}
