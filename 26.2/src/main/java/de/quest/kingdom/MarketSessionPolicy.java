package de.quest.kingdom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Ephemeral private offers only. The native caller must revalidate live proximity and anchor evidence. */
public final class MarketSessionPolicy {
    private static final UUID NONE=new UUID(0,0);
    private final UUID id=UUID.randomUUID(),owner,trader,body,visit;
    private final String dimension;
    private final long generation,opened;
    private long revision,lastObserved;
    private int lots=1;
    private boolean closed;
    private Map<UUID,KingdomMarketLedger.Quote> offered=Map.of();
    public enum Access { ALLOWED,INVALID,EXPIRED,STALE }
    public MarketSessionPolicy(UUID owner,String dimension,UUID trader,UUID body,long generation,UUID visit,long tick) {
        if(!identity(owner) || !identity(trader) || !identity(body) || !identity(visit) || dimension==null
                || dimension.isBlank() || dimension.length()>128 || generation<0 || tick<0) throw new IllegalArgumentException("Invalid market session");
        this.owner=owner; this.dimension=dimension; this.trader=trader; this.body=body;
        this.generation=generation; this.visit=visit; opened=tick; lastObserved=tick;
    }
    public Access validate(UUID actor,UUID session,String dimension,UUID body,long generation,long tick,long expectedRevision) {
        if(closed || !owner.equals(actor) || !id.equals(session) || !this.dimension.equals(dimension)
                || !this.body.equals(body) || this.generation!=generation || tick<lastObserved) return Access.INVALID;
        lastObserved=tick;
        if(tick-opened>1200) return Access.EXPIRED;
        return expectedRevision==revision?Access.ALLOWED:Access.STALE;
    }
    public void offer(List<KingdomMarketLedger.Quote> quotes) {
        if(closed) throw new IllegalStateException("Closed market session");
        if(quotes==null || quotes.size()>16) throw new IllegalArgumentException("Unbounded private offers");
        var next=new HashMap<UUID,KingdomMarketLedger.Quote>();
        for(var quote:quotes) {
            if(quote==null || !identity(quote.id()) || !owner.equals(quote.actor()) || !trader.equals(quote.trader())
                    || !visit.equals(quote.visit()) || quote.side()==null || quote.sku()==null || !quote.sku().matches("[a-z0-9_]{1,32}")
                    || quote.lots()!=lots || quote.quantity()<1 || quote.quantity()>128 || quote.price()<1 || quote.price()>3_000_000_000L
                    || quote.revision()<0 || quote.needRevision()<0 || quote.expires()<opened || next.putIfAbsent(quote.id(),quote)!=null)
                throw new IllegalArgumentException("Invalid private quote");
        }
        advance(); offered=Map.copyOf(next);
    }
    /** Consume the displayed revision before any wallet/inventory work, so duplicate clicks cannot repeat it. */
    public Optional<KingdomMarketLedger.Quote> take(UUID actor,UUID session,String dimension,UUID body,long generation,
                                                 long tick,long expectedRevision,UUID quoteId) {
        if(validate(actor,session,dimension,body,generation,tick,expectedRevision)!=Access.ALLOWED) return Optional.empty();
        var quote=offered.get(quoteId); if(quote==null || tick>quote.expires()) return Optional.empty();
        advance(); return Optional.of(quote);
    }
    public void setLots(int lots) {
        if(lots<1 || lots>2) throw new IllegalArgumentException("Invalid market quantity");
        if(closed) throw new IllegalStateException("Closed market session");
        if(this.lots!=lots) { advance(); this.lots=lots; }
    }
    private void advance() {
        if(revision==Long.MAX_VALUE) { close(); throw new IllegalStateException("Exhausted market session"); }
        revision++; offered=Map.of();
    }
    private static boolean identity(UUID id) { return id!=null && !id.equals(NONE); }
    public void close() { closed=true; offered=Map.of(); }
    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public UUID trader() { return trader; }
    public UUID body() { return body; }
    public UUID visit() { return visit; }
    public String dimension() { return dimension; }
    public long generation() { return generation; }
    public long revision() { return revision; }
    public int lots() { return lots; }
}
