package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Derived cache lifecycle only; native data reload and gameplay remain separate. */
final class MarketSourceCacheTest {
    private static final Identifier WHEAT=Identifier.withDefaultNamespace("wheat");
    @Test void coldAndInvalidatedCachesHoldUntilACompleteSourceSnapshotIsBuilt() {
        var invalidations=new AtomicInteger(); var cache=new MarketSourceCache(invalidations::incrementAndGet);
        assertFalse(cache.ready()); assertEquals(0,cache.source(WHEAT).price());
        assertTrue(cache.rebuild(() -> snapshot(5))); assertTrue(cache.ready()); assertEquals(1,invalidations.get());
        long old=cache.revision(); assertTrue(cache.invalidate());
        assertTrue(cache.revision()>old); assertFalse(cache.ready()); assertEquals(0,cache.source(WHEAT).price());
    }
    @Test void quotesAreInvalidatedAndOldPricesHeldBeforeAnyBuilderWork() {
        var invalidations=new AtomicInteger(); var cache=new MarketSourceCache(invalidations::incrementAndGet);
        cache.rebuild(() -> snapshot(5));
        assertTrue(cache.rebuild(() -> {
            assertEquals(2,invalidations.get()); assertFalse(cache.ready()); assertEquals(0,cache.source(WHEAT).price()); return snapshot(2);
        }));
        assertTrue(cache.source(WHEAT).price()>0);
    }
    @Test void failedOrIncompleteRebuildCannotKeepAnOlderHigherPrice() {
        var cache=new MarketSourceCache(() -> {}); cache.rebuild(() -> snapshot(5));
        assertFalse(cache.rebuild(() -> { throw new IllegalArgumentException("Missing live source"); }));
        assertFalse(cache.ready()); assertEquals(0,cache.source(WHEAT).price());
        var incomplete=MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(5,List.of(new MarketAcquisitionBounds.Goods(WHEAT,1)))),
                new NativeMarketRecipeRules.Reading(false,List.of()),item -> null);
        assertFalse(cache.rebuild(() -> incomplete)); assertFalse(cache.ready());
        assertFalse(cache.rebuild(() -> null)); assertFalse(cache.ready());
    }
    @Test void aNewerReentrantRebuildCannotBeOverwrittenByAnOlderBuilder() {
        var cache=new MarketSourceCache(() -> {});
        assertFalse(cache.rebuild(() -> { assertTrue(cache.rebuild(() -> snapshot(2))); return snapshot(20); }));
        assertTrue(cache.ready()); assertEquals(snapshot(2).bounds().source(WHEAT),cache.source(WHEAT));
    }
    @Test void aFailedQuoteInvalidationCannotPublishNewPricesOrRunItsBuilder() {
        var cache=new MarketSourceCache(() -> { throw new IllegalStateException("Invalidation failed"); });
        assertFalse(cache.rebuild(() -> { fail("Builder must not run after a failed quote invalidation"); return snapshot(5); }));
        assertFalse(cache.ready()); assertEquals(0,cache.source(WHEAT).price());
    }
    @Test void aRebuildDuringQuoteInvalidationCannotBeOverwrittenByItsOuterRequest() {
        var invalidations=new AtomicInteger(); var holder=new MarketSourceCache[1];
        holder[0]=new MarketSourceCache(() -> {
            if(invalidations.incrementAndGet()==1) assertTrue(holder[0].rebuild(() -> snapshot(2)));
        });
        assertFalse(holder[0].rebuild(() -> snapshot(20)));
        assertTrue(holder[0].ready()); assertEquals(snapshot(2).bounds().source(WHEAT),holder[0].source(WHEAT));
    }
    private static MarketSourceClosure.Snapshot snapshot(long price) {
        return MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(price,List.of(new MarketAcquisitionBounds.Goods(WHEAT,1)))),
                new NativeMarketRecipeRules.Reading(true,List.of()),item -> new NativeMarketRecipeRules.Reading(true,List.of()));
    }
}
