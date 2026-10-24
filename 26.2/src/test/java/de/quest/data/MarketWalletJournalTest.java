package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.economy.CurrencyService;
import de.quest.kingdom.MarketTransferMarker;
import de.quest.kingdom.MarketWalletJournal;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** Real wallet document round trips, without Minecraft runtime or inventory entities. */
final class MarketWalletJournalTest {
    private static final UUID OWNER=UUID.randomUUID();
    private static MarketTransferMarker marker() {
        var transfer=new CompoundTag(); transfer.putString("id",UUID.randomUUID().toString()); transfer.putString("actor",OWNER.toString());
        transfer.putString("visit",UUID.randomUUID().toString()); transfer.putString("trader",UUID.randomUUID().toString());
        transfer.putString("sku","grain"); transfer.putLong("price",4); transfer.putLong("beforeBalance",100); transfer.putLong("afterBalance",96);
        var plan=new CompoundTag(); plan.putInt("schema",1); plan.putString("side","BUY"); plan.putInt("quantity",32);
        var item=new CompoundTag(); item.putString("id","minecraft:wheat"); item.putInt("count",1); plan.put("commodity",item);
        var before=new ListTag(); var after=new ListTag(); var limits=new ListTag();
        for(int i=0;i<36;i++) { before.add(new CompoundTag()); after.add(i==0?item.copy():new CompoundTag()); limits.add(IntTag.valueOf(64)); }
        after.getCompoundOrEmpty(0).putInt("count",32); plan.put("before",before); plan.put("after",after); plan.put("limits",limits); transfer.put("plan",plan);
        var root=new CompoundTag(); root.putInt("schema",1); root.putString("stage","BEFORE"); root.put("transfer",transfer);
        return MarketTransferMarker.fromNbt(root).orElseThrow();
    }
    @Test void walletAndReceiptStageSaveTogetherAndReplayNeverDebitsTwice() {
        var state=QuestState.fromNbt(new CompoundTag()); var data=state.getPlayerData(OWNER); data.setCurrencyBalance(100); var m=marker();
        assertTrue(data.marketWallet().begin(OWNER,m,100));
        assertTrue(CurrencyService.applyMarketBalance(data,OWNER,m.withStage(MarketTransferMarker.Stage.AFTER)));
        var restored=QuestState.fromNbt(QuestState.toNbt(state)); var wallet=restored.getPlayerData(OWNER);
        assertEquals(96,wallet.getCurrencyBalance()); assertEquals(MarketTransferMarker.Stage.AFTER,wallet.marketWallet().marker().orElseThrow().stage());
        assertFalse(CurrencyService.applyMarketBalance(wallet,OWNER,m.withStage(MarketTransferMarker.Stage.AFTER)));
        assertEquals(96,wallet.getCurrencyBalance());
    }
    @Test void staleBalanceOrForeignOwnerCannotAdvanceProofOrChangeMoney() {
        var data=new PlayerQuestData(); data.setCurrencyBalance(100); var m=marker(); assertTrue(data.marketWallet().begin(OWNER,m,100));
        data.setCurrencyBalance(99);
        assertFalse(CurrencyService.applyMarketBalance(data,OWNER,m.withStage(MarketTransferMarker.Stage.AFTER)));
        assertEquals(MarketTransferMarker.Stage.BEFORE,data.marketWallet().marker().orElseThrow().stage()); assertEquals(99,data.getCurrencyBalance());
        data.setCurrencyBalance(100);
        assertFalse(CurrencyService.applyMarketBalance(data,UUID.randomUUID(),m.withStage(MarketTransferMarker.Stage.AFTER)));
        assertEquals(100,data.getCurrencyBalance());
    }
    @Test void unresolvedJournalCannotBeOverwrittenByAnotherReceiptAndOnlyValidTerminalCanReleaseIt() {
        var journal=new MarketWalletJournal(); var m=marker(); assertTrue(journal.begin(OWNER,m,100));
        assertFalse(journal.begin(OWNER,marker(),100)); assertFalse(journal.advance(m.withStage(MarketTransferMarker.Stage.COMMITTED)));
        assertTrue(journal.advance(m.withStage(MarketTransferMarker.Stage.CANCELLED)));
        assertFalse(journal.advance(m.withStage(MarketTransferMarker.Stage.AFTER)));
        assertTrue(journal.begin(OWNER,marker(),100));
    }
    @Test void futureMalformedAndNonCompoundPlayerRowsRemainOpaqueAcrossQuestSave() {
        var m=marker(); var journal=new MarketWalletJournal(); assertTrue(journal.begin(OWNER,m,100));
        var future=assertInstanceOf(CompoundTag.class,journal.toNbt()); future.putInt("schema",99); future.putString("unknown","retain");
        for(var raw:java.util.List.of(future,StringTag.valueOf("future journal"))) {
            var root=new CompoundTag(); var owners=new CompoundTag(); owners.put(OWNER.toString(),raw.copy()); root.put("marketTransfers",owners);
            var state=QuestState.fromNbt(root); assertTrue(state.getPlayerData(OWNER).marketWallet().readOnly());
            assertFalse(state.getPlayerData(OWNER).marketWallet().begin(OWNER,m,100));
            assertEquals(raw,QuestState.toNbt(state).getCompoundOrEmpty("marketTransfers").get(OWNER.toString()));
        }
    }
    @Test void malformedGlobalJournalCannotBecomeAFreshEmptyWalletAfterReload() {
        var root=new CompoundTag(); root.put("marketTransfers",StringTag.valueOf("future envelope")); var state=QuestState.fromNbt(root);
        assertTrue(state.getPlayerData(OWNER).marketWallet().readOnly());
        assertEquals(root.get("marketTransfers"),QuestState.toNbt(state).get("marketTransfers"));
    }
    @Test void administrativeQuestResetRetainsTransactionIdentityInsteadOfEnablingReplay() {
        var state=QuestState.fromNbt(new CompoundTag()); var m=marker(); var data=state.getPlayerData(OWNER); data.setCurrencyBalance(100);
        assertTrue(data.marketWallet().begin(OWNER,m,100)); state.resetAllProgress();
        assertEquals(m.toNbt(),state.getPlayerData(OWNER).marketWallet().marker().orElseThrow().toNbt());
        assertFalse(CurrencyService.applyMarketBalance(state.getPlayerData(OWNER),OWNER,m.withStage(MarketTransferMarker.Stage.AFTER)));
    }
    @Test void unknownJournalAndMarkerMetadataSurvivesNormalStageChanges() {
        var journal=new MarketWalletJournal(); var m=marker(); journal.begin(OWNER,m,100); var root=assertInstanceOf(CompoundTag.class,journal.toNbt());
        root.putString("unknownJournal","keep"); root.getCompoundOrEmpty("marker").putString("unknownMarker","keep");
        var restored=MarketWalletJournal.fromNbt(root); var loaded=restored.marker().orElseThrow();
        assertTrue(restored.advance(loaded.withStage(MarketTransferMarker.Stage.AFTER)));
        var saved=assertInstanceOf(CompoundTag.class,restored.toNbt());
        assertEquals("keep",saved.getStringOr("unknownJournal",""));
        assertEquals("keep",saved.getCompoundOrEmpty("marker").getStringOr("unknownMarker",""));
    }
}
