package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import de.quest.village.LivingVillageNetworkState.VillageSnapshot;
import de.quest.village.VillageCondition;
import de.quest.village.VillageLifeState.VillageKey;
import de.quest.village.VillageNeed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real shared quotas and NBT persistence; no worlds or player entities. */
final class MarketHomeDemandTest {
    private static final UUID HOME=UUID.randomUUID(),OWNER=UUID.randomUUID(),OTHER=UUID.randomUUID();
    private static final BlockPos POS=new BlockPos(0,64,0);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void ownerOrIndexChangesCannotRenewAnExhaustedHomeQuota() {
        var state=state(); var ledger=state.markets();
        assertTrue(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,0)));
        exhaust(state);
        assertFalse(ledger.observeHomeNeed(HOME,OTHER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,2,2)));
        assertFalse(ledger.observeHomeNeed(HOME,OWNER,snapshot(1,0,0,VillageNeed.GRANARY_SEED_RESERVES,2,2)));
        assertFalse(ledger.observeNeed(HOME,VillageNeed.GRANARY_SEED_RESERVES,2));
        assertTrue(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,5)));
        assertTrue(sell(state).isEmpty());
    }

    @Test void reloadRequiresFreshConfirmationAndPreservesAcceptedGoods() {
        var state=state(); state.markets().observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,0));
        exhaust(state);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(OWNER,restored.markets().homeNeedSource(HOME).orElseThrow().owner());
        assertTrue(sell(restored).isEmpty());
        assertTrue(restored.markets().observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,1)));
        assertTrue(sell(restored).isEmpty());
        assertTrue(restored.markets().observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,2,2)));
        assertTrue(sell(restored).isPresent());
    }

    @Test void aChangedSourceCannotExecuteAnAlreadyIssuedSaleQuote() {
        var state=state(); var ledger=state.markets();
        ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,2));
        start(state);
        var quote=sell(state).orElseThrow();
        assertFalse(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,10,0,VillageNeed.GRANARY_SEED_RESERVES,0,3)));
        assertTrue(ledger.prepare(quote,OWNER,0,32,true,0).isEmpty());
        assertFalse(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,1)));
        assertTrue(sell(state).isEmpty());
        assertTrue(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,3)));
        assertTrue(sell(state).isPresent());
    }

    @Test void sourceTypeChangeOrBackwardCycleCannotCreateAFreshQuota() {
        var state=state(); var ledger=state.markets();
        ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,4,4));
        assertFalse(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.FORGE_FUEL_AND_ORE,5,5)));
        assertFalse(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,6)));
        assertFalse(ledger.observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_PANTRY,4,6)));
    }

    @Test void malformedOrFutureBindingsStayOpaqueAndUnknownMetadataSurvives() {
        var state=state(); state.markets().observeHomeNeed(HOME,OWNER,snapshot(0,0,0,VillageNeed.GRANARY_SEED_RESERVES,0,0));
        var saved=LivingKingdomsState.toNbt(state);
        var row=saved.getCompoundOrEmpty("markets").getListOrEmpty("needs").getCompoundOrEmpty(0);
        row.getCompoundOrEmpty("source").putString("futureNote","keep");
        assertEquals("keep",LivingKingdomsState.toNbt(LivingKingdomsState.fromNbt(saved)).getCompoundOrEmpty("markets")
                .getListOrEmpty("needs").getCompoundOrEmpty(0).getCompoundOrEmpty("source").getStringOr("futureNote",""));
        var future=new CompoundTag(); future.putInt("schema",99);
        for(var source:List.of(future,StringTag.valueOf("foreign source"))) {
            var broken=saved.copy(); broken.getCompoundOrEmpty("markets").getListOrEmpty("needs").getCompoundOrEmpty(0).put("source",source.copy());
            var original=broken.getCompoundOrEmpty("markets").copy(); var restored=LivingKingdomsState.fromNbt(broken);
            assertTrue(restored.markets().readOnly());
            assertEquals(original,LivingKingdomsState.toNbt(restored).get("markets"));
        }
    }

    @Test void nativeAdapterReadsTheExistingFounderRowAndNeverCreatesAMissingSource() {
        var state=state(); var quests=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var network=de.quest.village.LivingVillageNetworkState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var contact=de.quest.shrine.VillageContactService.establish(quests.getPlayerData(OWNER),state.village(HOME).orElseThrow(),VillageBondType.GRANARY);
        network.ensureVillage(OWNER,contact.contact().villageIndex(),0,0,VillageBondType.GRANARY);
        assertTrue(KingdomMarketDemandService.observe(state,quests,network,OTHER,HOME));
        assertEquals(OWNER,state.markets().homeNeedSource(HOME).orElseThrow().owner());
        network.removeOwner(OWNER);
        assertFalse(KingdomMarketDemandService.observe(state,quests,network,OTHER,HOME));
        assertTrue(network.snapshot(OWNER,0).isEmpty());
        assertFalse(quests.getPlayersView().containsKey(OTHER));
    }

    @Test void futureNetworkSchemaCannotBeCoercedIntoANewMarketQuota() {
        var state=state(); var quests=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var network=de.quest.village.LivingVillageNetworkState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        de.quest.shrine.VillageContactService.establish(quests.getPlayerData(OWNER),state.village(HOME).orElseThrow(),VillageBondType.GRANARY);
        network.ensureVillage(OWNER,0,0,0,VillageBondType.GRANARY);
        var saved=(CompoundTag)de.quest.village.LivingVillageNetworkState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,network).getOrThrow();
        saved.putInt("schemaVersion",99);
        var future=de.quest.village.LivingVillageNetworkState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,saved).getOrThrow();
        assertFalse(KingdomMarketDemandService.observe(state,quests,future,OTHER,HOME));
        var rewritten=de.quest.village.LivingVillageNetworkState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,future).getOrThrow();
        var again=de.quest.village.LivingVillageNetworkState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,rewritten).getOrThrow();
        assertFalse(KingdomMarketDemandService.observe(state,quests,again,OTHER,HOME));
    }

    @Test void consumedForeignFreightAdvancesThePinnedSourceRatherThanRetargetingOrRewardingIt() {
        var state=state(); var quests=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var network=de.quest.village.LivingVillageNetworkState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var contact=de.quest.shrine.VillageContactService.establish(quests.getPlayerData(OWNER),state.village(HOME).orElseThrow(),VillageBondType.GRANARY);
        int index=contact.contact().villageIndex(); var before=network.ensureVillage(OWNER,index,0,0,VillageBondType.GRANARY);
        network.adminSetVillageSupport(OWNER,index,90); var renown=network.network(OWNER);
        var item=before.need()==VillageNeed.GRANARY_PANTRY?net.minecraft.world.item.Items.BREAD:net.minecraft.world.item.Items.WHEAT;
        assertTrue(KingdomFreightService.sharedDemand(state,quests,network,OTHER,HOME,7,item,10));
        var after=network.marketSnapshot(OWNER,index).orElseThrow(); assertEquals(1,after.cyclesCompleted()); assertEquals(55,after.support());
        assertEquals(OWNER,state.markets().homeNeedSource(HOME).orElseThrow().owner());
        assertEquals(index,state.markets().homeNeedSource(HOME).orElseThrow().index());
        assertEquals(renown,network.network(OWNER)); assertEquals(0,after.routeArrivals()); assertEquals(0,after.energyProgress());
        assertFalse(KingdomFreightService.sharedDemand(state,quests,network,OTHER,HOME,7,item,11));
        assertEquals(after,network.marketSnapshot(OWNER,index).orElseThrow());
        network.removeOwner(OWNER);
        assertFalse(KingdomFreightService.sharedDemand(state,quests,network,OTHER,HOME,8,item,12));
        assertTrue(network.snapshot(OWNER,index).isEmpty());
    }

    private static VillageSnapshot snapshot(int index,int x,int z,VillageNeed need,int cycles,int revision) {
        return new VillageSnapshot(index,x,z,need.villageType(),need,55,VillageCondition.fromSupport(55),cycles,0,0,0,0,revision);
    }
    private static void exhaust(LivingKingdomsState state) {
        start(state);
        for(int i=0;i<4;i++) { var quote=sell(state).orElseThrow(); var receipt=state.markets().prepare(quote,OWNER,0,32,true,0).orElseThrow();
            assertTrue(state.markets().commit(receipt.id(),receipt.afterBalance(),receipt.afterItems())); }
    }
    private static void start(LivingKingdomsState state) {
        var plans=java.util.stream.IntStream.range(0,2).mapToObj(slot -> {
            var person=state.people().ensure(HOME,KingdomPersonRegistry.Role.MARKET_TRADER,slot).orElseThrow();
            return new KingdomMarketLedger.TraderPlan(person.id(),HOME,List.of(
                new KingdomMarketLedger.Goods("grain",Identifier.withDefaultNamespace("wheat"),32,4,4,4,1)));
        }).toList();
        state.markets().start(HOME,plans,0).orElseThrow();
    }
    private static java.util.Optional<KingdomMarketLedger.Quote> sell(LivingKingdomsState state) {
        var visit=state.markets().visit(HOME); if(visit.isEmpty()) return java.util.Optional.empty();
        var trader=visit.get().traders().stream().filter(t -> t.goods().get(0).bought()<4).findFirst().orElseThrow();
        return state.markets().quote(OWNER,visit.get().id(),trader.person(),"grain",KingdomMarketLedger.Side.SELL,1,0,0,List.of(),0);
    }
    private static LivingKingdomsState state() {
        var state=new LivingKingdomsState(); state.registerCustom(HOME,OWNER,VillageKey.overworld(0,0),POS,"Home").orElseThrow();
        var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(POS.north()));
        state.anchors().bind(OWNER,HOME,0,UUID.randomUUID(),-1,POS,Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0); return state;
    }
}
