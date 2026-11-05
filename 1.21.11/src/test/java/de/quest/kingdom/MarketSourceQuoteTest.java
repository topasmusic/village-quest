package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import de.quest.village.VillageLifeState.VillageKey;
import de.quest.village.VillageNeed;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The production quote entry reads the state-owned source cache; no server/world or player entities. */
final class MarketSourceQuoteTest {
    private static final Identifier WHEAT=Identifier.withDefaultNamespace("wheat");
    private static final UUID ACTOR=UUID.randomUUID();
    private record Fixture(LivingKingdomsState state,UUID town,UUID visit,UUID trader) {}
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void coldSourceCacheHoldsNewBuybacksWhileOrdinaryPurchasesRemainAvailable() {
        var f=fixture(); var saved=LivingKingdomsState.toNbt(f.state);
        assertTrue(quote(f,KingdomMarketLedger.Side.SELL).isEmpty());
        assertTrue(quote(f,KingdomMarketLedger.Side.BUY).isPresent());
        assertEquals(saved,LivingKingdomsState.toNbt(f.state));
    }
    @Test void newBuybackUsesTheActualCheapestSourceRatherThanOnlyTheOfferFloor() {
        var f=fixture(); sources(f.state,3);
        assertEquals(1,quote(f,KingdomMarketLedger.Side.SELL).orElseThrow().price());
        sources(f.state,1);
        assertTrue(f.state.marketSources().ready()); assertTrue(quote(f,KingdomMarketLedger.Side.SELL).isEmpty());
    }
    @Test void invalidationRejectsStaleQuotesAndKeepsExistingReservedReceiptsReplayable() {
        var f=fixture(); sources(f.state,3); var ledger=f.state.markets();
        var reservedQuote=quote(f,KingdomMarketLedger.Side.SELL).orElseThrow();
        var receipt=ledger.prepare(reservedQuote,ACTOR,10,64,true,0).orElseThrow();
        var stale=ledger.quote(UUID.randomUUID(),f.visit,f.trader,"grain",KingdomMarketLedger.Side.SELL,1,0,0,0).orElseThrow();
        var saved=LivingKingdomsState.toNbt(f.state);
        assertTrue(f.state.marketSources().invalidate());
        assertEquals(saved,LivingKingdomsState.toNbt(f.state));
        assertTrue(ledger.prepare(stale,stale.actor(),10,64,true,0).isEmpty());
        assertTrue(quote(f,KingdomMarketLedger.Side.SELL).isEmpty());
        assertTrue(ledger.prepare(reservedQuote,ACTOR,receipt.afterBalance(),receipt.afterItems(),true,0).isPresent());
        assertTrue(ledger.commit(receipt.id(),receipt.afterBalance(),receipt.afterItems()));
    }
    @Test void saveReloadCannotRestoreAFreshSourceProof() {
        var f=fixture(); sources(f.state,3);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        assertFalse(restored.marketSources().ready());
        assertTrue(restored.markets().quote(ACTOR,f.visit,f.trader,"grain",KingdomMarketLedger.Side.SELL,1,0,0,0).isEmpty());
    }
    @Test void nativePrepareChecksThePriceEvenIfAnInternalCallerOmittedSourceBounds() {
        var f=fixture(); sources(f.state,3); var ledger=f.state.markets();
        var unsafe=ledger.quote(ACTOR,f.visit,f.trader,"grain",KingdomMarketLedger.Side.SELL,1,0,0,List.of(),0).orElseThrow();
        assertEquals(2,unsafe.price()); assertFalse(ledger.sourceAllows(unsafe));
        var safe=quote(f,KingdomMarketLedger.Side.SELL).orElseThrow();
        assertEquals(1,safe.price()); assertTrue(ledger.sourceAllows(safe));
        f.state.marketSources().invalidate(); assertFalse(ledger.sourceAllows(safe));
    }
    @Test void displayedHomeDemandIsReadOnlyAndReflectsSharedAcceptedGoods() {
        var f=fixture(); sources(f.state,3); var ledger=f.state.markets();
        var before=LivingKingdomsState.toNbt(f.state);
        assertEquals(4,ledger.remainingDemand(f.visit,f.trader,"grain"));
        assertEquals(0,ledger.remainingDemand(f.visit,f.trader,"missing"));
        assertEquals(before,LivingKingdomsState.toNbt(f.state));
        var quote=quote(f,KingdomMarketLedger.Side.SELL).orElseThrow();
        var receipt=ledger.prepare(quote,ACTOR,10,64,true,0).orElseThrow();
        assertEquals(3,ledger.remainingDemand(f.visit,f.trader,"grain"));
        assertFalse(ledger.observeNeed(f.town,VillageNeed.GRANARY_PANTRY,1));
        assertEquals(3,ledger.remainingDemand(f.visit,f.trader,"grain"));
        assertTrue(ledger.commit(receipt.id(),receipt.afterBalance(),receipt.afterItems()));
        assertTrue(ledger.observeNeed(f.town,VillageNeed.GRANARY_PANTRY,1));
        assertEquals(0,ledger.remainingDemand(f.visit,f.trader,"grain"));
    }
    @Test void privateQuoteRefreshDoesNotInvalidateAnotherActorsDisplayedPurchaseOrChangeSavedState() {
        var f=fixture(); var ledger=f.state.markets(); var other=UUID.randomUUID();
        var mine=quote(f,KingdomMarketLedger.Side.BUY).orElseThrow();
        var theirs=ledger.quote(other,f.visit,f.trader,"grain",KingdomMarketLedger.Side.BUY,1,0,0,0).orElseThrow();
        var saved=LivingKingdomsState.toNbt(f.state); ledger.discardQuotes(ACTOR);
        assertEquals(saved,LivingKingdomsState.toNbt(f.state));
        assertTrue(ledger.prepare(mine,ACTOR,10,0,true,0).isEmpty());
        assertTrue(ledger.prepare(theirs,other,10,0,true,0).isPresent());
    }
    private static java.util.Optional<KingdomMarketLedger.Quote> quote(Fixture f,KingdomMarketLedger.Side side) {
        return f.state.markets().quote(ACTOR,f.visit,f.trader,"grain",side,1,0,0,0);
    }
    private static void sources(LivingKingdomsState state,long price) {
        assertTrue(state.marketSources().rebuild(() -> MarketSourceClosure.build(
                List.of(new MarketAcquisitionBounds.Acquisition(price,List.of(new MarketAcquisitionBounds.Goods(WHEAT,64)))),
                new NativeMarketRecipeRules.Reading(true,List.of()),item -> new NativeMarketRecipeRules.Reading(true,List.of()))));
    }
    private static Fixture fixture() {
        var state=new LivingKingdomsState(); var town=UUID.randomUUID(); var owner=UUID.randomUUID(); var pos=new BlockPos(0,64,0);
        state.registerCustom(town,owner,VillageKey.overworld(0,0),pos,"Market").orElseThrow();
        var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(pos.north()));
        state.anchors().bind(owner,town,0,UUID.randomUUID(),-1,pos,Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0);
        var trader=state.people().ensure(town,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow().id();
        state.markets().observeNeed(town,VillageNeed.GRANARY_SEED_RESERVES,0);
        var goods=new KingdomMarketLedger.Goods("grain",WHEAT,32,2,4,4,2);
        var visit=state.markets().start(town,List.of(new KingdomMarketLedger.TraderPlan(trader,town,List.of(goods))),0).orElseThrow();
        return new Fixture(state,town,visit.id(),trader);
    }
}
