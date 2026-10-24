package de.quest.kingdom;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/** Server-state-owned derived cache; the native adapter must invoke it on the server thread. Never serialized. */
public final class MarketSourceCache {
    private final Runnable invalidateQuotes;
    private MarketSourceClosure.Snapshot current;
    private long revision;
    public MarketSourceCache(Runnable invalidateQuotes) { this.invalidateQuotes=Objects.requireNonNull(invalidateQuotes); }
    public long revision() { return revision; }
    public boolean ready() { return current!=null && current.complete(); }
    public MarketPricePolicy.Source source(Identifier item) {
        return ready() ? current.bounds().source(item) : new MarketPricePolicy.Source(0,1);
    }
    public boolean invalidate() {
        current=null;
        boolean available=revision<Long.MAX_VALUE;
        if(available) revision++;
        try { invalidateQuotes.run(); return available; }
        catch(RuntimeException failed) { return false; }
    }
    public boolean rebuild(Supplier<MarketSourceClosure.Snapshot> builder) {
        long ticket=revision==Long.MAX_VALUE ? Long.MAX_VALUE : revision+1;
        if(!invalidate() || ticket!=revision) return false;
        try {
            var next=Objects.requireNonNull(builder).get();
            if(ticket!=revision || next==null || !next.complete()) return false;
            current=next; return true;
        } catch(RuntimeException failed) { return false; }
    }
}
