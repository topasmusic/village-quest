package de.quest.kingdom;

import java.util.*;
import net.minecraft.nbt.*;

/** At most one saved roster per observed clock day. Ledger alone owns stock, money and receipts. */
public final class KingdomMarketDayPlan {
    private final LivingKingdomsState state;
    private final Map<UUID,Day> days=new HashMap<>();
    private Tag retained=new CompoundTag(); private boolean opaque;
    KingdomMarketDayPlan(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly() || state.markets().readOnly() || state.marketPlaces().readOnly() || state.marketHomes().readOnly() || state.marketTravel().readOnly(); }
    boolean plannedToday(UUID host,long clock) {
        var day=days.get(host); return clock>=0 && day!=null && day.date==clock/24000;
    }
    boolean booked(UUID person,long date) { return days.values().stream().anyMatch(d -> d.date>=date && d.slots.containsKey(person)); }
    boolean hasCurrentSlot(UUID host,UUID person,long now) {
        var day=days.get(host); if(readOnly() || day==null || state.markets().visit(host).filter(v -> v.id().equals(day.visit)).isEmpty()) return false;
        return state.anchors().official(host,BuildingScanner.Role.MARKET).filter(a -> a.id().equals(day.anchor)).isPresent()
                && state.marketPlaces().usable(day.anchor,now).stream().anyMatch(p -> Objects.equals(day.slots.get(person),p.slot()));
    }
    public Optional<KingdomMarketPlaces.Place> place(UUID host,UUID person,long now) {
        return place(host,person,now,false);
    }
    Optional<KingdomMarketPlaces.Place> appearancePlace(UUID host,UUID person,long now) { return place(host,person,now,true); }
    private Optional<KingdomMarketPlaces.Place> place(UUID host,UUID person,long now,boolean appearance) {
        var day=days.get(host); var visit=state.markets().visit(host).orElse(null);
        boolean evening=appearance && state.marketTravel().evening(host,person);
        if(readOnly() || day==null || visit==null || !visit.open() && !evening || !visit.id().equals(day.visit)) return Optional.empty();
        var p=state.people().person(person).orElse(null);
        if(p==null || !(p.visit()==null?p.village():p.visit()).equals(host) || !hasCurrentSlot(host,person,now) || !evening && !state.marketTravel().mayTrade(host,person,now)) return Optional.empty();
        var slot=day.slots.get(person); if(slot==null) return Optional.empty();
        return state.marketPlaces().usable(day.anchor,now).stream().filter(place -> place.slot()==slot).findFirst();
    }
    /** Unknown evidence pauses trade; only known schedule/place changes may remove a loaded body. */
    boolean mayDismiss(UUID host,UUID person,long clock,long now) {
        if(state.anchors().knownDetached(host,BuildingScanner.Role.MARKET)) return true;
        if(Math.floorMod(clock,24000)>=12000 && !state.marketTravel().evening(host,person)) return true;
        var anchor=state.anchors().official(host,BuildingScanner.Role.MARKET);
        return anchor.isPresent() && (state.anchors().mayContinue(anchor.get().id(),now)
                || anchor.get().phase()==BuildingOperation.Phase.PAUSED) && appearancePlace(host,person,now).isEmpty();
    }
    public Optional<KingdomMarketLedger.VisitView> observe(UUID host,long clock,long now,int earnedCapacity,
                                                        List<KingdomMarketLedger.TraderPlan> planned,boolean confirmedLoaded) {
        return observe(host,clock,now,earnedCapacity,planned,confirmedLoaded,List.of());
    }
    Optional<KingdomMarketLedger.VisitView> observe(UUID host,long clock,long now,int earnedCapacity,
            List<KingdomMarketLedger.TraderPlan> planned,boolean confirmedLoaded,List<de.quest.caravan.KingdomRouteConnections.Connection> routes) {
        if(readOnly() || clock<0 || now<0 || earnedCapacity<1 || earnedCapacity>5 || planned==null || planned.size()>5
                || planned.stream().anyMatch(Objects::isNull) || state.village(host).isEmpty()) return Optional.empty();
        var ledger=state.markets(); ledger.observe(host,now,confirmedLoaded);
        state.marketTravel().observe(host,clock,now,confirmedLoaded,routes);
        long date=clock/24000, time=clock%24000;
        var anchor=state.anchors().official(host,BuildingScanner.Role.MARKET).orElse(null); var day=days.get(host);
        if(!confirmedLoaded || time<1000 || time>=12000 || anchor==null || !state.anchors().mayContinue(anchor.id(),now)
                || day!=null && date<day.date) { ledger.close(host); return Optional.empty(); }
        var places=state.marketPlaces().usable(anchor.id(),now); var current=ledger.visit(host).orElse(null);
        if(places.isEmpty()) { ledger.close(host); return Optional.empty(); }
        if(day!=null && date==day.date) {
            if(current==null || !current.id().equals(day.visit)) return Optional.empty();
            boolean usable=places.stream().anyMatch(p -> day.slots.containsValue(p.slot()));
            if(!usable) { ledger.close(host); return Optional.empty(); }
            if(!current.open()) ledger.resume(host);
            return ledger.visit(host).filter(KingdomMarketLedger.VisitView::open);
        }
        // Old/manual visits may be safely reconciled but are never treated as a fresh daily budget.
        if(current!=null) ledger.close(host);
        if(state.marketTravel().at(host).stream().anyMatch(j -> j.phase()!=KingdomMarketTravel.Phase.HOME)) return Optional.empty();
        var selected=planned.stream().filter(p -> state.marketTravel().mayPlan(host,p,date,routes)).limit(Math.min(earnedCapacity,places.size())).toList();
        if(selected.isEmpty() || days.size()>=16384 && day==null) return Optional.empty();
        var started=ledger.start(host,selected,now); if(started.isEmpty()) return Optional.empty();
        state.marketTravel().book(host,started.get().id(),date,selected,routes);
        var next=new Day(date,anchor.id(),started.get().id());
        for(int i=0;i<selected.size();i++) next.slots.put(selected.get(i).person(),places.get(i).slot());
        days.put(host,next); state.setDirty(); return started;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("days") instanceof ListTag rows) || rows.size()>16384) { opaque=true; return; }
        try {
            for(var value:rows) {
                if(!(value instanceof CompoundTag row) || !(row.get("date") instanceof LongTag)
                        || !(row.get("slots") instanceof ListTag slots) || slots.isEmpty() || slots.size()>5) throw new IllegalArgumentException();
                var host=UUID.fromString(row.getStringOr("host","")); var anchor=UUID.fromString(row.getStringOr("anchor",""));
                var visit=UUID.fromString(row.getStringOr("visit","")); long date=row.getLongOr("date",-1);
                var current=state.markets().visit(host).orElseThrow();
                if(date<0 || !current.id().equals(visit) || !state.anchors().official(host,BuildingScanner.Role.MARKET)
                        .filter(a -> a.id().equals(anchor)).isPresent() || days.containsKey(host)) throw new IllegalArgumentException();
                var day=new Day(date,anchor,visit); day.retained=row.copy(); var indices=new HashSet<Integer>();
                for(var element:slots) {
                    if(!(element instanceof CompoundTag tag) || !(tag.get("slot") instanceof IntTag)) throw new IllegalArgumentException();
                    int slot=tag.getIntOr("slot",-1); var person=UUID.fromString(tag.getStringOr("person",""));
                    if(slot<0 || slot>=5 || !indices.add(slot) || day.slots.putIfAbsent(person,slot)!=null
                            || current.traders().stream().noneMatch(t -> t.person().equals(person))) throw new IllegalArgumentException();
                    day.payloads.put(person,tag.copy());
                }
                if(day.slots.size()!=current.traders().size()) throw new IllegalArgumentException(); days.put(host,day);
            }
        } catch(RuntimeException malformed) { days.clear(); opaque=true; }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); days.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var day=entry.getValue(); var row=day.retained.copy(); row.putString("host",entry.getKey().toString()); row.putLong("date",day.date);
            row.putString("anchor",day.anchor.toString()); row.putString("visit",day.visit.toString());
            var slots=new ListTag(); day.slots.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(slot -> {
                var tag=day.payloads.getOrDefault(slot.getKey(),new CompoundTag()).copy(); tag.putString("person",slot.getKey().toString()); tag.putInt("slot",slot.getValue()); slots.add(tag);
            }); row.put("slots",slots); rows.add(row);
        }); root.put("days",rows); return root;
    }
    private static final class Day {
        final long date; final UUID anchor,visit; CompoundTag retained=new CompoundTag();
        final Map<UUID,Integer> slots=new LinkedHashMap<>(); final Map<UUID,CompoundTag> payloads=new HashMap<>();
        Day(long date,UUID anchor,UUID visit) { this.date=date; this.anchor=anchor; this.visit=visit; }
    }
}
