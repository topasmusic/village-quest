package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

final class ReputationMigrationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void previousTwoFiveQuestAndRouteFieldsRoundTripWithoutRetroactiveRewards() {
        UUID owner = UUID.randomUUID(); var state = QuestState.fromNbt(new CompoundTag()); var player = state.getPlayerData(owner);
        player.setTradeRouteFlag("guild_intro.first_daily_migration_checked", true); player.setTradeRouteFlag("guild_intro.first_daily_completed", true);
        player.setCurrencyBalance(912); player.setReputation("crafting", 201); player.setStoryInt("chapter_progress", 4);
        player.setStoryFlag("forge_done", true); player.setTradeRouteInt("route_count", 5);
        for (int i = 0; i < 5; i++) { player.setTradeRouteString("route_" + i + "_crew_master_name", "Mara " + i); player.setTradeRouteInt("route_" + i + "_village_x", 200 + i * 100); }
        player.setTradeRouteInt("guild_refund_grain", 12); player.setTradeRouteString("dispatch_id", UUID.randomUUID().toString());
        var old = QuestState.toNbt(state); var manager = old.getCompoundOrEmpty("questManager"); manager.remove("socialReputation");
        old.remove("socialServerTick"); old.remove("protectedVillages");
        var loaded = QuestState.fromNbt(old); var saved = QuestState.toNbt(loaded).getCompoundOrEmpty("questManager"); saved.remove("socialReputation");
        assertEquals(manager, saved);
        assertEquals(0, loaded.getPlayerData(owner).socialReputation().guildTrust());
        assertTrue(loaded.getPlayerData(owner).socialReputation().history().isEmpty());
    }
    @Test void corruptRootSocialContainerAndUnknownIndexArePreservedWithoutOpeningNewRewardEligibility() {
        UUID owner = UUID.randomUUID(); var root = QuestState.toNbt(QuestState.fromNbt(new CompoundTag()));
        root.getCompoundOrEmpty("questManager").putString("socialReputation", "damaged-but-preserve");
        var future = new CompoundTag(); future.putInt("schema", 99); future.putString("future", "keep-index"); root.put("protectedVillages", future);
        var loaded = QuestState.fromNbt(root); var social = loaded.getPlayerData(owner).socialReputation();
        assertFalse(social.writable()); assertFalse(social.markReceipt(BenefitKind.CONVOY, UUID.randomUUID()));
        var saved = QuestState.toNbt(loaded);
        assertEquals("damaged-but-preserve", saved.getCompoundOrEmpty("questManager").getStringOr("socialReputation", ""));
        assertEquals(future, saved.getCompoundOrEmpty("protectedVillages"));
    }
    @Test void malformedCaseAndResetMetadataCannotRemoveSanctionsOrResetPointCaps() {
        var data = new SocialReputationData(); data.openCase(UUID.randomUUID(), Offence.CREW_KILL, VillageKey.overworld(0, 0), 10);
        for (String field : List.of("major", "aid", "lastOffence")) {
            var saved = data.toNbt(); saved.getCompoundOrEmpty("case").putString(field, "damaged");
            var loaded = SocialReputationData.fromNbt(saved); assertFalse(loaded.writable(), field); assertEquals(saved, loaded.toNbt());
        }
        for (String field : List.of("resetToken", "revision", "probation")) {
            var saved = data.toNbt(); saved.putString(field, "damaged");
            var loaded = SocialReputationData.fromNbt(saved); assertFalse(loaded.writable(), field); assertEquals(saved, loaded.toNbt());
        }
    }
    @Test void malformedGlobalVillageIndexIsPreservedWithoutInventingAnchors() {
        var root = QuestState.toNbt(QuestState.fromNbt(new CompoundTag()));
        root.putString("protectedVillages", "preserve-malformed-index");
        var loaded = QuestState.fromNbt(root);
        assertTrue(loaded.protectedVillages().resolve("minecraft:overworld", new net.minecraft.core.BlockPos(0, 64, 0)).isEmpty());
        assertEquals(root.get("protectedVillages"), QuestState.toNbt(loaded).get("protectedVillages"));
    }
}
