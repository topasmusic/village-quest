package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.SocialReputationData;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SocialReputationPersistenceTest {
    private static final UUID PLAYER = UUID.fromString("71a84590-53d5-446e-8505-72f9caadf680");
    private static final VillageKey VILLAGE = VillageKey.overworld(128, -224);

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void oldSaveStartsNeutralAndKeepsExistingProgressAndCrewNames() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData player = state.getPlayerData(PLAYER);
        player.setReputation("crafting", 200);
        player.setTradeRouteString("route_0_crew_master_name", "Mara Oakridge");
        player.setStoryFlag("bond_allied", true);
        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(0, loaded.socialReputation().guildTrust());
        assertNull(loaded.socialReputation().activeCase());
        assertEquals(200, loaded.getReputation("crafting"));
        assertEquals("Mara Oakridge", loaded.getTradeRouteString("route_0_crew_master_name"));
        assertTrue(loaded.hasStoryFlag("bond_allied"));
    }

    @Test void casePartialAidTimerAndReceiptSurviveTheRealWorldSaveCodec() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        SocialReputationData data = state.getPlayerData(PLAYER).socialReputation();
        UUID receipt = UUID.randomUUID();
        data.setGuildTrust(-60); data.setLocalTrust(VILLAGE, -70);
        data.openCase(UUID.randomUUID(), Offence.CREW_KILL, VILLAGE, 25);
        data.recordAid(MaterialOption.WHEAT, 16);
        data.advanceOnlineTicks(600);
        assertTrue(data.markReceipt(BenefitKind.CONVOY, receipt));
        state.advanceSocialServerTick();
        state.protectedVillages().upsert(VILLAGE, 71);
        QuestState loaded = QuestState.fromNbt(QuestState.toNbt(state));
        SocialReputationData saved = loaded.getPlayerData(PLAYER).socialReputation();
        assertEquals(-60, saved.guildTrust());
        assertEquals(-70, saved.localTrust(VILLAGE));
        assertEquals(16, saved.activeCase().delivered(MaterialOption.WHEAT));
        assertEquals(35_400, saved.activeCase().onlineTicksRemaining());
        assertFalse(saved.markReceipt(BenefitKind.CONVOY, receipt));
        assertEquals(1, loaded.socialServerTick());
        assertEquals(VILLAGE, loaded.protectedVillages().resolve("minecraft:overworld", new net.minecraft.core.BlockPos(128, 71, -224)).orElseThrow());
    }

    @Test void futureAndDamagedSchemasRemainUntouchedAndCannotEarnOrSpend() {
        CompoundTag future = new CompoundTag(); future.putInt("schema", 99); future.putString("futureField", "keep-me");
        SocialReputationData saved = SocialReputationData.fromNbt(future);
        assertFalse(saved.writable());
        saved.setGuildTrust(60);
        assertFalse(saved.markReceipt(BenefitKind.DAILY, UUID.randomUUID()));
        assertEquals(future, saved.toNbt());
        CompoundTag damaged = new CompoundTag(); damaged.putInt("schema", 1); damaged.putString("guildTrust", "not-an-int");
        SocialReputationData quarantined = SocialReputationData.fromNbt(damaged);
        assertFalse(quarantined.writable());
        assertEquals(damaged, quarantined.toNbt());
    }

    @Test void wrongReceiptContainerCannotSilentlyClearTheAntiReplayLedger() {
        CompoundTag damaged = new SocialReputationData().toNbt();
        damaged.putString("receipts", "damaged-list");
        SocialReputationData loaded = SocialReputationData.fromNbt(damaged);
        assertFalse(loaded.writable());
        assertEquals(damaged, loaded.toNbt());
    }

    @Test void malformedDailyCounterCannotResetTheEarnedPointLimit() {
        CompoundTag damaged = new SocialReputationData().toNbt(); damaged.putString("guildUsed", "eight");
        assertFalse(SocialReputationData.fromNbt(damaged).writable());
    }
}
