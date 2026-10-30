package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import java.util.UUID;
import org.junit.jupiter.api.*;

final class PersonalRestartDataTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void onlySelectedPlayerStartsFreshAndOldStoredToolsNeverRegainTheirGeneration() {
        var state = QuestState.fromNbt(new CompoundTag());
        var owner = UUID.randomUUID(); var other = UUID.randomUUID();
        var data = state.getPlayerData(owner);
        data.setCurrencyBalance(700);
        data.setDailyChoice(DailyQuestService.DailyQuestType.WOODCUTTING);
        data.setAcceptedDay(100);
        data.setLastRewardDay(99);
        data.setDailyInt("logs", 20);
        data.setTradeRouteInt("route_count", 4);
        data.setTradeRouteInt("network_escrow", 80);
        data.setTradeRouteInt("archive.gen.merchant_seal", 5);
        data.setTradeRouteInt("friends.lutte.generation", 9);
        data.setTradeRouteInt("friends.simon.stage", 2);
        data.setStoryChapterProgress(StoryArcType.THE_EMPTY_CARAVAN.id(), 4);
        var untouched = state.getPlayerData(other);
        untouched.setCurrencyBalance(43);
        untouched.setTradeRouteInt("route_count", 2);
        state.restartPersonalProgress(owner);
        var fresh = state.getPlayerData(owner);
        assertEquals(2, fresh.progressGeneration());
        assertNotSame(data, fresh);
        assertSame(untouched, state.getPlayerData(other));
        assertEquals(43, untouched.getCurrencyBalance());
        assertEquals(2, untouched.getTradeRouteInt("route_count"));
        assertEquals(0, fresh.getCurrencyBalance());
        assertEquals(0, fresh.getTradeRouteInt("route_count"));
        assertEquals(0, fresh.getTradeRouteInt("network_escrow"));
        assertEquals(0, fresh.getTradeRouteInt("friends.simon.stage"));
        assertFalse(fresh.hasActiveDailySlot());
        assertEquals(99, fresh.getLastRewardDay());
        assertEquals(6, fresh.getTradeRouteInt("archive.gen.merchant_seal"));
        assertEquals(10, fresh.getTradeRouteInt("friends.lutte.generation"));
        assertTrue(PersonalResetReceipts.friendPaid(fresh, "simon", 1));
        assertTrue(PersonalResetReceipts.storyPaid(fresh, StoryArcType.THE_EMPTY_CARAVAN, 3));
        state = QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(6, state.getPlayerData(owner).getTradeRouteInt("archive.gen.merchant_seal"));
        state.restartPersonalProgress(owner);
        assertEquals(3, state.getPlayerData(owner).progressGeneration());
        assertEquals(7, state.getPlayerData(owner).getTradeRouteInt("archive.gen.merchant_seal"));
        assertTrue(PersonalResetReceipts.storyPaid(state.getPlayerData(owner), StoryArcType.THE_EMPTY_CARAVAN, 3));
    }
    @Test void unknownRewardDataRefusesResetWithoutReplacingAnyPlayerState() {
        var state = QuestState.fromNbt(new CompoundTag()); var id = UUID.randomUUID();
        var old = state.getPlayerData(id); old.setCurrencyBalance(300);
        var unknown = new CompoundTag(); unknown.putInt("schema", 900);
        old.loadVanillaRewards(unknown);
        assertThrows(IllegalStateException.class, () -> state.restartPersonalProgress(id));
        assertSame(old, state.getPlayerData(id));
        assertEquals(300, old.getCurrencyBalance());
        assertEquals(unknown, old.vanillaRewards().toNbt());
    }
    @Test void preparingRestartDoesNotWriteLegacyReceiptsIntoTheLivePlayer() {
        var state = QuestState.fromNbt(new CompoundTag()); var id = UUID.randomUUID();
        var old = state.getPlayerData(id);
        old.setStoryChapterProgress(StoryArcType.THE_EMPTY_CARAVAN.id(), 4);
        old.setTradeRouteInt("friends.simon.stage", 2);
        var before = QuestState.toNbt(state);
        var prepared = old.personalRestart();
        assertEquals(before, QuestState.toNbt(state), "Preflight must be detached even if later inventory planning refuses");
        assertTrue(PersonalResetReceipts.storyPaid(prepared, StoryArcType.THE_EMPTY_CARAVAN, 3));
        assertTrue(PersonalResetReceipts.friendPaid(prepared, "simon", 1));
        assertNotSame(old.marketWallet(), prepared.marketWallet(), "Prepared financial evidence must not alias the live wallet");
    }
    @Test void pendingDeliveryAndEarnedChoiceRefuseBeforeReplacingOrCapturingReceipts() {
        for (String queue : new String[]{"pending", "earned"}) {
            var state = QuestState.fromNbt(new CompoundTag()); var id = UUID.randomUUID();
            var old = state.getPlayerData(id); old.setCurrencyBalance(88);
            old.setStoryChapterProgress(StoryArcType.THE_EMPTY_CARAVAN.id(), 3);
            var reward = old.vanillaRewards().toNbt(); var entries = new net.minecraft.nbt.ListTag();
            var entry = new CompoundTag(); entry.putString("instance", "earned-one");
            if (queue.equals("earned")) {
                var choices = new net.minecraft.nbt.ListTag(); var choice = new CompoundTag();
                choice.putString("option", "materials"); choices.add(choice); entry.put("choices", choices);
            }
            entries.add(entry); reward.put(queue, entries); old.loadVanillaRewards(reward);
            var before = QuestState.toNbt(state);
            assertThrows(IllegalStateException.class, () -> state.restartPersonalProgress(id), queue);
            assertSame(old, state.getPlayerData(id)); assertEquals(before, QuestState.toNbt(state));
        }
    }
    @Test void suppliedFreightAndUncollectedOrMalformedRefundsMustBeSettledFirst() {
        for (int scenario=0;scenario<4;scenario++) {
            var state=QuestState.fromNbt(new CompoundTag()); var id=UUID.randomUUID();var old=state.getPlayerData(id);
            old.setCurrencyBalance(55);
            if(scenario==0) old.setTradeRouteFlag("guild_contract_supplied",true);
            if(scenario==1) assertTrue(de.quest.caravan.TradeContractRefundLedger.queue(old,1,32));
            if(scenario==2) old.setTradeRouteInt("guild_contract_refund_0_type",999);
            if(scenario==3) old.setTradeRouteString("guild_contract_refund_0_type","unknown");
            var before=QuestState.toNbt(state);
            assertThrows(IllegalStateException.class,()->state.restartPersonalProgress(id));
            assertEquals(before,QuestState.toNbt(state));assertSame(old,state.getPlayerData(id));
        }
    }
    @Test void unknownSocialReputationIsPreservedInsteadOfResettingIntoEligibility() {
        var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var old=state.getPlayerData(id);
        var foreign=new CompoundTag();foreign.putInt("schema",999);old.loadSocialReputation(foreign);
        var before=QuestState.toNbt(state);
        assertThrows(IllegalStateException.class,()->state.restartPersonalProgress(id));
        assertEquals(before,QuestState.toNbt(state));assertSame(old,state.getPlayerData(id));
    }
}
