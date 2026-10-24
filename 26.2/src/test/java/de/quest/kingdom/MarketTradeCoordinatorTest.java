package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Three actual independently serialized NBT documents. No player entity, world or game simulation. */
final class MarketTradeCoordinatorTest {
    private static final UUID TOWN=UUID.randomUUID(),OWNER=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    private static final BlockPos POS=new BlockPos(0,64,0);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void components() { Items.WHEAT.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build()); }
    private static RegistryAccess access() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static final class Proof implements MarketTradeCoordinator.PlayerProof {
        Tag value;
        final Map<UUID,MarketWalletJournal> originals=new java.util.HashMap<>();
        public Tag get() { return value==null?null:value.copy(); }
        public void set(CompoundTag next) { value=next.copy(); }
        public MarketWalletJournal originalWallet(UUID owner) { return originals.get(owner); }
    }
    private static final class Fixture {
        LivingKingdomsState kingdoms=new LivingKingdomsState(); QuestState quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        SimpleContainer bag=new SimpleContainer(36); Proof proof=new Proof(); KingdomMarketLedger.Quote quote;
        Fixture() {
            quests.getPlayerData(ACTOR).setCurrencyBalance(100);
            kingdoms.registerCustom(TOWN,OWNER,VillageKey.overworld(0,0),POS,"Market").orElseThrow();
            var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(POS.north()));
            kingdoms.anchors().bind(OWNER,TOWN,0,UUID.randomUUID(),-1,POS,Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0);
            var person=kingdoms.people().ensure(TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow().id();
            var goods=new KingdomMarketLedger.Goods("grain",Identifier.withDefaultNamespace("wheat"),32,2,4,4,1);
            var visit=kingdoms.markets().start(TOWN,List.of(new KingdomMarketLedger.TraderPlan(person,TOWN,List.of(goods))),0).orElseThrow();
            quote=kingdoms.markets().quote(ACTOR,visit.id(),person,"grain",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),0).orElseThrow();
        }
        MarketTradeCoordinator coordinator(Shelf shelf) {
            return new MarketTradeCoordinator(kingdoms.markets(),ACTOR,quests.getPlayerData(ACTOR),bag,proof,access(),shelf::persist,
                    marker -> CurrencyService.applyMarketBalance(quests.getPlayerData(ACTOR),ACTOR,marker));
        }
        MarketTradeCoordinator coordinator(Shelf shelf,java.util.function.Consumer<KingdomMarketLedger.Quote> prepared) {
            return new MarketTradeCoordinator(kingdoms.markets(),ACTOR,quests.getPlayerData(ACTOR),bag,proof,access(),shelf::persist,
                    marker -> CurrencyService.applyMarketBalance(quests.getPlayerData(ACTOR),ACTOR,marker),prepared);
        }
        MarketInventoryPlan plan() { return MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),quote.side(),quote.quantity()).orElseThrow(); }
        long balance() { return quests.getPlayerData(ACTOR).getCurrencyBalance(); }
        int stock() { return kingdoms.markets().visit(TOWN).orElseThrow().traders().getFirst().goods().getFirst().stock(); }
    }
    private static final class Shelf {
        Fixture f; CompoundTag kingdoms,quests,player; int calls,failCall=-1,writes;
        Shelf(Fixture f) { this.f=f; save(3); }
        boolean persist() { calls++; if(calls==failCall) { save(writes); return false; } save(3); return true; }
        void save(int count) {
            if(count>0) kingdoms=LivingKingdomsState.toNbt(f.kingdoms).copy();
            if(count>1) quests=((CompoundTag)QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,f.quests).getOrThrow()).copy();
            if(count>2) {
                player=new CompoundTag(); if(f.proof.value!=null) player.put("marker",f.proof.value.copy()); var items=new ListTag();
                var ops=RegistryOps.create(NbtOps.INSTANCE,access()); for(int i=0;i<36;i++) items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,f.bag.getItem(i)).getOrThrow());
                player.put("inventory",items);
            }
        }
        void reload() {
            f.kingdoms=LivingKingdomsState.fromNbt(kingdoms.copy()); f.quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,quests.copy()).getOrThrow();
            f.proof=new Proof(); f.proof.value=player.get("marker")==null?null:player.get("marker").copy(); f.bag=new SimpleContainer(36);
            var ops=RegistryOps.create(NbtOps.INSTANCE,access()); var items=player.getListOrEmpty("inventory");
            for(int i=0;i<36;i++) f.bag.setItem(i,ItemStack.OPTIONAL_CODEC.parse(ops,items.get(i)).getOrThrow());
            calls=0; failCall=-1;
        }
    }
    @Test void successfulPurchaseAndRepeatedReceiptDebitAndDeliverExactlyOnceAcrossAllDocuments() {
        var f=new Fixture(); var shelf=new Shelf(f); var plan=f.plan();
        assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).execute(f.quote,plan,0));
        assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount()); assertEquals(1,f.stock());
        assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).execute(f.quote,plan,0));
        assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount());
        shelf.reload(); assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).reconcile(f.quote.id()));
        assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount()); assertTrue(f.kingdoms.markets().pending(ACTOR).isEmpty());
    }
    @Test void failedPreparationSaveCannotTouchMoneyOrInventoryAndMissingProofStaysHeldAfterReload() {
        for(int writes=1;writes<=2;writes++) {
            var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=1; shelf.writes=writes;
            assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
            assertEquals(100,f.balance()); assertTrue(f.bag.getItem(0).isEmpty());
            shelf.reload(); assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).reconcile(f.quote.id()));
            assertEquals(100,f.balance()); assertTrue(f.bag.getItem(0).isEmpty()); assertEquals(1,f.stock());
        }
    }
    @Test void crashAfterOnlyWalletAfterImageKeepsMixedStateAndNeverBlindlyDeliversOrRefunds() {
        var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=2; shelf.writes=2;
        assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
        shelf.reload(); assertEquals(96,f.balance()); assertTrue(f.bag.getItem(0).isEmpty());
        assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).reconcile(f.quote.id()));
        assertEquals(96,f.balance()); assertTrue(f.bag.getItem(0).isEmpty()); assertEquals(1,f.stock());
    }
    @Test void bothSavedBeforeImagesPermitCancellationAndSingleStockReturnAfterInterruptedApplySave() {
        var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=2; shelf.writes=1;
        assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
        shelf.reload(); assertEquals(MarketTradeCoordinator.Result.CANCELLED,f.coordinator(shelf).reconcile(f.quote.id()));
        assertEquals(100,f.balance()); assertTrue(f.bag.getItem(0).isEmpty()); assertEquals(2,f.stock());
        assertEquals(MarketTradeCoordinator.Result.CANCELLED,f.coordinator(shelf).reconcile(f.quote.id())); assertEquals(2,f.stock());
    }
    @Test void partialTerminalAcksWithBothAppliedImagesCanFinishWithoutReplayingTheTrade() {
        for(int writes=1;writes<=2;writes++) {
            var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=3; shelf.writes=writes;
            assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
            shelf.reload(); assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).reconcile(f.quote.id()));
            assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount()); assertEquals(1,f.stock());
        }
    }
    @Test void unknownPlayerProofAndOrphanWalletCannotBeReplacedByAFreshTrade() {
        var f=new Fixture(); var shelf=new Shelf(f); f.proof.value=net.minecraft.nbt.StringTag.valueOf("future proof");
        assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
        assertEquals(100,f.balance()); assertEquals(2,f.stock()); assertEquals("future proof",f.proof.value.asString().orElseThrow());
    }
    private static MarketTransferMarker foreignProof(Fixture f,UUID original,MarketTransferMarker.Stage stage) {
        var transfer=new CompoundTag(); transfer.putString("id",UUID.randomUUID().toString());transfer.putString("actor",original.toString());
        transfer.putString("visit",UUID.randomUUID().toString());transfer.putString("trader",f.quote.trader().toString());transfer.putString("sku","grain");
        transfer.putLong("price",4);transfer.putLong("beforeBalance",100);transfer.putLong("afterBalance",96);transfer.put("plan",f.plan().toNbt(access()));
        return new MarketTransferMarker(transfer,stage);
    }
    @Test void copiedTerminalProofFromAnotherUuidWithExactOriginalJournalAllowsOnlyTheNewPlayersTrade() {
        for(var stage:List.of(MarketTransferMarker.Stage.COMMITTED,MarketTransferMarker.Stage.CANCELLED)) {
            var f=new Fixture();var original=UUID.randomUUID();var marker=foreignProof(f,original,stage);
            var journalTag=new CompoundTag();journalTag.putInt("schema",1);journalTag.put("marker",marker.toNbt());
            var journal=MarketWalletJournal.fromNbt(journalTag);f.proof.originals.put(original,journal);f.proof.set(marker.toNbt());
            var before=journal.toNbt();var shelf=new Shelf(f);
            assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).execute(f.quote,f.plan(),0));
            assertEquals(before,journal.toNbt(),"Original owner's evidence must remain unchanged");
            assertEquals(96,f.balance());assertEquals(32,f.bag.getItem(0).getCount());assertEquals(1,f.stock());
            assertEquals(ACTOR,MarketTransferMarker.fromNbt(f.proof.get()).orElseThrow().actor());
            assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).reconcile(f.quote.id()));
            assertEquals(96,f.balance());assertEquals(32,f.bag.getItem(0).getCount());
        }
    }
    @Test void copiedForeignProofWithMissingUnfinishedOrDifferentOriginalJournalRemainsUntouched() {
        for(int mode=0;mode<6;mode++) {
            var f=new Fixture();var original=UUID.randomUUID();var marker=foreignProof(f,original,MarketTransferMarker.Stage.COMMITTED);
            f.proof.set(marker.toNbt());
            if(mode>0) {
                var journalTag=new CompoundTag();journalTag.putInt("schema",1);
                journalTag.put("marker",mode==1?marker.withStage(MarketTransferMarker.Stage.AFTER).toNbt():
                        mode==2?foreignProof(f,original,MarketTransferMarker.Stage.COMMITTED).toNbt():marker.toNbt());
                f.proof.originals.put(original,MarketWalletJournal.fromNbt(journalTag));
                if(mode==3) f.proof.set(marker.withStage(MarketTransferMarker.Stage.BEFORE).toNbt());
                if(mode==4) {
                    var foreignQuote=f.kingdoms.markets().quote(original,f.quote.visit(),f.quote.trader(),"grain",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),0).orElseThrow();
                    assertTrue(f.kingdoms.markets().prepareNative(foreignQuote,original,100,f.bag,f.plan(),access(),0).isPresent());
                }
                if(mode==5) {journalTag.putInt("schema",2);f.proof.originals.put(original,MarketWalletJournal.fromNbt(journalTag));}
            }
            var before=f.proof.get();var shelf=new Shelf(f);
            assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
            assertEquals(before,f.proof.get());assertEquals(100,f.balance());assertTrue(f.bag.getItem(0).isEmpty());
            assertEquals(mode==4?1:2,f.stock());assertEquals(0,shelf.calls);
        }
    }
    @Test void acknowledgedReceiptReplayRequiresNoAdditionalSaveOrSideEffects() {
        var f=new Fixture(); var shelf=new Shelf(f); var plan=f.plan();
        assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).execute(f.quote,plan,0));
        int writes=shelf.calls; shelf.failCall=writes+1;
        assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).execute(f.quote,plan,0));
        assertEquals(writes,shelf.calls); assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount()); assertEquals(1,f.stock());
    }
    @Test void anotherActorAndForgedQuoteRevisionOrPriceCannotReserveStockOrReachPersistence() {
        var f=new Fixture(); var shelf=new Shelf(f); UUID other=UUID.randomUUID();
        f.quests.getPlayerData(other).setCurrencyBalance(100);
        var wrongActor=new MarketTradeCoordinator(f.kingdoms.markets(),other,f.quests.getPlayerData(other),f.bag,f.proof,access(),shelf::persist,
                marker -> { fail("A foreign actor must never reach wallet mutation"); return false; });
        assertEquals(MarketTradeCoordinator.Result.REJECTED,wrongActor.execute(f.quote,f.plan(),0));
        var q=f.quote;
        for(var changed:List.of(
                new KingdomMarketLedger.Quote(q.id(),q.actor(),q.visit(),q.trader(),q.sku(),q.side(),q.lots(),q.quantity(),q.price()+1,q.revision(),q.needRevision(),q.expires()),
                new KingdomMarketLedger.Quote(q.id(),q.actor(),q.visit(),q.trader(),q.sku(),q.side(),q.lots(),q.quantity(),q.price(),q.revision()+1,q.needRevision(),q.expires())))
            assertEquals(MarketTradeCoordinator.Result.REJECTED,f.coordinator(shelf).execute(changed,f.plan(),0));
        assertEquals(0,shelf.calls); assertEquals(100,f.balance()); assertEquals(100,f.quests.getPlayerData(other).getCurrencyBalance());
        assertTrue(f.bag.getItem(0).isEmpty()); assertEquals(2,f.stock()); assertNull(f.proof.get());
        assertTrue(f.kingdoms.markets().receipt(q.id()).isEmpty());
    }
    @Test void independentlySavedForeignReceiptActorVisitTraderOrSpendProofStaysHeldWithoutRepair() {
        for(String field:List.of("id","actor","visit","trader","price")) {
            var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=2; shelf.writes=3;
            assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
            var changed=assertInstanceOf(CompoundTag.class,f.proof.get()); var transfer=changed.getCompoundOrEmpty("transfer");
            if(field.equals("price")) { transfer.putLong("price",3); transfer.putLong("afterBalance",97); }
            else transfer.putString(field,UUID.randomUUID().toString());
            assertTrue(MarketTransferMarker.fromNbt(changed).isPresent(),"The foreign proof is structurally valid: "+field);
            f.proof.set(changed); shelf.save(3); shelf.reload();
            var before=LivingKingdomsState.toNbt(f.kingdoms); var wallet=f.quests.getPlayerData(ACTOR).marketWallet().marker().orElseThrow().toNbt();
            for(int repeat=0;repeat<2;repeat++) assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).reconcile(f.quote.id()),field);
            assertEquals(0,shelf.calls); assertEquals(before,LivingKingdomsState.toNbt(f.kingdoms));
            assertEquals(wallet,f.quests.getPlayerData(ACTOR).marketWallet().marker().orElseThrow().toNbt()); assertEquals(changed,f.proof.get());
            assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount()); assertEquals(1,f.stock());
            assertFalse(f.kingdoms.markets().nativeAcknowledged(f.quote.id()));
        }
    }
    @Test void changedBalanceOrInventoryAfterSavedApplyCannotBeGuessedAsACompletedTrade() {
        for(boolean changeMoney:List.of(true,false)) {
            var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=2; shelf.writes=3;
            assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).execute(f.quote,f.plan(),0));
            shelf.reload();
            if(changeMoney) f.quests.getPlayerData(ACTOR).setCurrencyBalance(95); else f.bag.getItem(0).shrink(1);
            shelf.save(3); shelf.reload(); var before=LivingKingdomsState.toNbt(f.kingdoms);
            for(int repeat=0;repeat<2;repeat++) assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf).reconcile(f.quote.id()));
            assertEquals(before,LivingKingdomsState.toNbt(f.kingdoms)); assertEquals(changeMoney?95:96,f.balance());
            assertEquals(changeMoney?32:31,f.bag.getItem(0).getCount()); assertEquals(1,f.stock());
            assertFalse(f.kingdoms.markets().nativeAcknowledged(f.quote.id()));
        }
    }
    @Test void preparedStoryContextIsWrittenBeforeAnyInventoryOrMoneyMutationAndNotRecreatedOnReplay() {
        var f=new Fixture(); var shelf=new Shelf(f); shelf.failCall=1; shelf.writes=3;
        var called=new int[1];
        java.util.function.Consumer<KingdomMarketLedger.Quote> prepared=quote -> {
            called[0]++; assertTrue(f.bag.getItem(0).isEmpty()); assertEquals(100,f.balance());
            f.quests.getPlayerData(ACTOR).setTradeRouteString("kingdoms.market.story.proof",quote.id().toString());
        };
        assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf,prepared).execute(f.quote,f.plan(),0));
        shelf.reload(); assertEquals(1,called[0]);
        assertEquals(f.quote.id().toString(),f.quests.getPlayerData(ACTOR).getTradeRouteString("kingdoms.market.story.proof"));
        assertEquals(MarketTradeCoordinator.Result.CANCELLED,f.coordinator(shelf,prepared).reconcile(f.quote.id()));
        assertEquals(1,called[0]); assertEquals(100,f.balance()); assertTrue(f.bag.getItem(0).isEmpty());
    }
    @Test void onlyAnAcknowledgedRealPaidReceiptCountsOnceAndAReplayCannotFollowTargetRebinding() {
        var f=new Fixture(); var shelf=new Shelf(f); KingdomStoryProgress.bind(f.quests.getPlayerData(ACTOR),TOWN,false);
        java.util.function.Consumer<KingdomMarketLedger.Quote> prepared=q ->
                assertTrue(KingdomMarketStoryProof.prepare(f.quests,ACTOR,q.id(),TOWN,List.of(ACTOR)));
        assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf,prepared).execute(f.quote,f.plan(),0));
        assertTrue(f.kingdoms.markets().nativeAcknowledged(f.quote.id()));
        assertEquals(Set.of(ACTOR),KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()));
        assertTrue(KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()).isEmpty());
        var data=f.quests.getPlayerData(ACTOR); KingdomStoryProgress.bind(data,new UUID(50,50),true); KingdomStoryProgress.bind(data,TOWN,true);
        assertTrue(KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()).isEmpty());
        assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount());
    }
    @Test void aHeldThenCancelledNativeTradeCannotCreateAStoryDeedAcrossRestart() {
        var f=new Fixture(); var shelf=new Shelf(f); KingdomStoryProgress.bind(f.quests.getPlayerData(ACTOR),TOWN,false);
        shelf.failCall=1; shelf.writes=3;
        assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf,q ->
                assertTrue(KingdomMarketStoryProof.prepare(f.quests,ACTOR,q.id(),TOWN,List.of(ACTOR)))).execute(f.quote,f.plan(),0));
        assertTrue(KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()).isEmpty());
        shelf.reload(); assertEquals(MarketTradeCoordinator.Result.CANCELLED,f.coordinator(shelf).reconcile(f.quote.id()));
        assertTrue(KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()).isEmpty());
        assertFalse(KingdomStoryProgress.has(f.quests.getPlayerData(ACTOR),KingdomStoryProgress.Deed.MARKET_TRADE));
    }
    @Test void aPreparedStoryReceiptCanFinishAfterPartialTerminalSavingWithoutRepeatingPayment() {
        for(int writes=1;writes<=2;writes++) {
            var f=new Fixture(); var shelf=new Shelf(f); KingdomStoryProgress.bind(f.quests.getPlayerData(ACTOR),TOWN,false);
            shelf.failCall=3; shelf.writes=writes;
            assertEquals(MarketTradeCoordinator.Result.HELD,f.coordinator(shelf,q ->
                    assertTrue(KingdomMarketStoryProof.prepare(f.quests,ACTOR,q.id(),TOWN,List.of(ACTOR)))).execute(f.quote,f.plan(),0));
            assertTrue(KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()).isEmpty());
            shelf.reload(); assertEquals(MarketTradeCoordinator.Result.COMMITTED,f.coordinator(shelf).reconcile(f.quote.id()));
            assertEquals(Set.of(ACTOR),KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()));
            assertTrue(KingdomMarketStoryProof.completeNative(f.quests,f.kingdoms.markets(),ACTOR,f.quote.id()).isEmpty());
            assertEquals(96,f.balance()); assertEquals(32,f.bag.getItem(0).getCount()); assertEquals(1,f.stock());
        }
    }
}
