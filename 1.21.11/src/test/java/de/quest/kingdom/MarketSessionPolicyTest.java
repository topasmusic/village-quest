package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MarketSessionPolicyTest {
    private static final UUID ACTOR=new UUID(1,1),TRADER=new UUID(2,2),BODY=new UUID(3,3),VISIT=new UUID(4,4);
    @Test void privateActorDimensionBodyGenerationAndSessionMustAllMatch() {
        var session=session(); session.offer(List.of(quote(new UUID(5,5),ACTOR,TRADER,VISIT,220)));
        assertEquals(MarketSessionPolicy.Access.ALLOWED,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,20,session.revision()));
        assertEquals(MarketSessionPolicy.Access.INVALID,access(session,TRADER,session.id(),"minecraft:overworld",BODY,3,20,session.revision()));
        assertEquals(MarketSessionPolicy.Access.INVALID,access(session,ACTOR,VISIT,"minecraft:overworld",BODY,3,20,session.revision()));
        assertEquals(MarketSessionPolicy.Access.INVALID,access(session,ACTOR,session.id(),"minecraft:the_nether",BODY,3,20,session.revision()));
        assertEquals(MarketSessionPolicy.Access.INVALID,access(session,ACTOR,session.id(),"minecraft:overworld",TRADER,3,20,session.revision()));
        assertEquals(MarketSessionPolicy.Access.INVALID,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,4,20,session.revision()));
    }
    @Test void oldViewAndClockRewindCannotTradeAndSessionExpiresWithoutRenewingItsVisit() {
        var session=session(); long old=session.revision(); session.offer(List.of());
        assertEquals(MarketSessionPolicy.Access.STALE,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,20,old));
        assertEquals(MarketSessionPolicy.Access.INVALID,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,19,session.revision()));
        assertEquals(MarketSessionPolicy.Access.ALLOWED,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,1220,session.revision()));
        assertEquals(MarketSessionPolicy.Access.EXPIRED,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,1221,session.revision()));
        session.offer(List.of());
        assertEquals(VISIT,session.visit());
        assertEquals(MarketSessionPolicy.Access.EXPIRED,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,1221,session.revision()));
    }
    @Test void displayedQuoteCanBeConsumedOnlyOnceAndNeverSubstitutedByAnotherActorOrVisit() {
        var session=session(); var quote=quote(new UUID(5,5),ACTOR,TRADER,VISIT,220); session.offer(List.of(quote));
        long revision=session.revision();
        assertTrue(take(session,revision,VISIT,30).isEmpty());
        assertEquals(revision,session.revision());
        assertEquals(quote,take(session,revision,quote.id(),30).orElseThrow());
        assertTrue(take(session,revision,quote.id(),30).isEmpty());
        assertThrows(IllegalArgumentException.class,() -> session.offer(List.of(quote(new UUID(6,6),TRADER,TRADER,VISIT,220))));
        assertThrows(IllegalArgumentException.class,() -> session.offer(List.of(quote(new UUID(6,6),ACTOR,BODY,VISIT,220))));
        assertThrows(IllegalArgumentException.class,() -> session.offer(List.of(quote(new UUID(6,6),ACTOR,TRADER,BODY,220))));
    }
    @Test void expiredQuoteAndQuantityChangeCannotExecuteAnEarlierDisplayedPrice() {
        var session=session(); var quote=quote(new UUID(5,5),ACTOR,TRADER,VISIT,40); session.offer(List.of(quote));
        assertTrue(take(session,session.revision(),quote.id(),41).isEmpty());
        session.offer(List.of(quote)); long revision=session.revision(); session.setLots(2);
        assertEquals(2,session.lots()); assertTrue(take(session,revision,quote.id(),30).isEmpty());
        assertThrows(IllegalArgumentException.class,() -> session.setLots(3));
        session.close(); assertEquals(MarketSessionPolicy.Access.INVALID,access(session,ACTOR,session.id(),"minecraft:overworld",BODY,3,30,session.revision()));
    }
    @Test void unboundedDuplicateOrMalformedQuoteSetsDoNotBecomeAvailable() {
        var session=session(); var quote=quote(new UUID(5,5),ACTOR,TRADER,VISIT,220);
        assertThrows(IllegalArgumentException.class,() -> session.offer(List.of(quote,quote)));
        assertThrows(IllegalArgumentException.class,() -> session.offer(java.util.Collections.nCopies(17,quote)));
        assertThrows(IllegalArgumentException.class,() -> session.offer(List.of(quote(new UUID(6,6),ACTOR,TRADER,VISIT,19))));
        assertTrue(take(session,session.revision(),quote.id(),30).isEmpty());
    }
    private static MarketSessionPolicy session() { return new MarketSessionPolicy(ACTOR,"minecraft:overworld",TRADER,BODY,3,VISIT,20); }
    private static MarketSessionPolicy.Access access(MarketSessionPolicy s,UUID actor,UUID id,String dim,UUID body,long generation,long tick,long rev) {
        return s.validate(actor,id,dim,body,generation,tick,rev);
    }
    private static java.util.Optional<KingdomMarketLedger.Quote> take(MarketSessionPolicy s,long revision,UUID quote,long tick) {
        return s.take(ACTOR,s.id(),"minecraft:overworld",BODY,3,tick,revision,quote);
    }
    private static KingdomMarketLedger.Quote quote(UUID id,UUID actor,UUID trader,UUID visit,long expires) {
        return new KingdomMarketLedger.Quote(id,actor,visit,trader,"iron_ingot",KingdomMarketLedger.Side.BUY,1,8,9,0,0,expires);
    }
}
