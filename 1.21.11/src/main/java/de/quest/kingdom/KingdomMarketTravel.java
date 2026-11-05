package de.quest.kingdom;

import de.quest.caravan.KingdomRouteConnections.Connection;
import java.util.*;
import net.minecraft.nbt.*;

/** Exclusive journeys of existing people. Never pays freight or creates goods, needs or money. */
public final class KingdomMarketTravel {
    public enum Phase { OUTBOUND, STAY, EVENING, RETURNING, HOME }
    public record View(UUID person,UUID home,UUID host,UUID visit,UUID route,Phase phase,long remaining,boolean paused,boolean late) {}
    private final LivingKingdomsState state;
    private final Map<UUID,Journey> journeys=new HashMap<>();
    private final Map<UUID,Booking> bookings=new HashMap<>();
    private final List<KingdomPersonRegistry.View> retired=new ArrayList<>();
    private Tag retained=new CompoundTag(); private boolean opaque;
    KingdomMarketTravel(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly() || state.people().readOnly(); }
    public Optional<View> journey(UUID person) { return readOnly()?Optional.empty():Optional.ofNullable(journeys.get(person)).map(Journey::view); }
    public List<View> at(UUID host) { return readOnly()?List.of():journeys.values().stream().filter(j -> j.host.equals(host)).sorted(Comparator.comparing(j -> j.person)).map(Journey::view).toList(); }
    List<KingdomPersonRegistry.View> takeRetired() { var result=List.copyOf(retired); retired.clear(); return result; }
    /** Abort only on authoritative deletion, never on missing chunks, inactive routes or unknown observations. */
    void recoverLostRoutes(UUID host,java.util.function.BiPredicate<UUID,UUID> permanentlyMissing) {
        if(readOnly() || host==null || permanentlyMissing==null) return;
        for(var j:journeys.values()) if(j.host.equals(host) && j.phase!=Phase.HOME && permanentlyMissing.test(j.owner,j.route)) {
            var p=state.people().person(j.person).orElse(null);
            if(p==null || !host.equals(p.visit())) continue;
            if(state.people().endVisit(p.id(),p.revision(),false)) {
                if(p.body()!=null) retired.add(p);
                j.phase=Phase.HOME; j.elapsed=0; j.last=-1; j.paused=true;
                state.markets().invalidateQuotes(); state.setDirty();
            }
        }
    }
    boolean canPlan(UUID host,UUID person,long date) {
        if(readOnly() || date<0 || state.people().person(person).isEmpty() || state.village(host).isEmpty()
                || !bookings.containsKey(person) && bookings.size()>=81920) return false;
        var p=state.people().person(person).orElseThrow(); var b=bookings.get(person); var j=journeys.get(person);
        return (b==null || date>b.date && b.elapsed>=6000) && !state.marketDays().booked(person,date) && (j==null || j.phase==Phase.HOME) && p.visit()==null
                && (p.presence()==KingdomPersonRegistry.Presence.PRESENT || p.presence()==KingdomPersonRegistry.Presence.DORMANT)
                && p.revision()<Long.MAX_VALUE-3;
    }
    boolean permits(UUID home,UUID host) {
        return !readOnly() && !state.diplomacy().readOnly() && (state.diplomacy().between(home,host,KingdomDiplomacy.Type.TRADE)
                .filter(d -> d.status()==KingdomDiplomacy.Status.SIGNED).isPresent()
                || state.diplomacy().between(home,host,KingdomDiplomacy.Type.FRIENDSHIP)
                .filter(d -> d.status()==KingdomDiplomacy.Status.SIGNED && state.diplomacy().met(d)).isPresent());
    }
    Optional<Connection> route(UUID home,UUID host,List<Connection> routes) {
        return routes.stream().filter(c -> c.active() && c.joins(home,host))
                .sorted(Comparator.comparing(Connection::owner).thenComparing(Connection::id)).findFirst();
    }
    boolean mayPlan(UUID host,KingdomMarketLedger.TraderPlan plan,long date,List<Connection> routes) {
        return canPlan(host,plan.person(),date) && state.people().person(plan.person()).filter(p -> p.village().equals(plan.home()))
                .flatMap(p -> state.village(p.village())).filter(home -> home.legacyKey().dimension().equals(state.village(host).orElseThrow().legacyKey().dimension())).isPresent()
                && (host.equals(plan.home()) || permits(plan.home(),host) && route(plan.home(),host,routes).isPresent());
    }
    void book(UUID host,UUID visit,long date,List<KingdomMarketLedger.TraderPlan> plans,List<Connection> routes) {
        for(var plan:plans) {
            bookings.put(plan.person(),new Booking(host,date));
            if(host.equals(plan.home())) { journeys.remove(plan.person()); continue; }
            var p=state.people().person(plan.person()).orElseThrow(); var route=route(plan.home(),host,routes).orElseThrow();
            var home=state.village(plan.home()).orElseThrow(); var target=state.village(host).orElseThrow();
            boolean late=Math.floorMod(p.appearanceSeed()+date,8)==0;
            double distance=Math.max(Math.sqrt(home.arrival().distSqr(target.arrival())),route.distance());
            long duration=Math.max(200,Math.min(4000,(long)Math.ceil(distance*2*(150-Math.max(0,Math.min(100,route.quality())))/100)))+(late?400:0);
            if(!state.people().depart(p.id(),p.revision(),host)) throw new IllegalStateException("Validated market departure changed on server thread");
            if(p.body()!=null) retired.add(p);
            journeys.put(p.id(),new Journey(p.id(),p.village(),host,visit,route.owner(),route.id(),date,duration,late));
        }
        state.setDirty();
    }
    /** A closed/unknown observation resets the clock baseline, never the journey or its budget. */
    void observe(UUID host,long clock,long now,boolean confirmed,List<Connection> routes) {
        if(readOnly() || clock<0 || now<0) return;
        for(var b:bookings.values()) if(b.host.equals(host)) {
            long delta=confirmed && b.last>=0 && now>=b.last && now-b.last<=100?now-b.last:0;
            b.last=confirmed?now:-1;
            if(delta>0 && b.elapsed<6000) { b.elapsed=Math.min(6000,b.elapsed+delta); state.setDirty(); }
        }
        for(var j:journeys.values()) if(j.host.equals(host) && j.phase!=Phase.HOME) {
            var p=state.people().person(j.person).orElse(null);
            if(p==null) { j.last=-1; continue; }
            if(p.presence()==KingdomPersonRegistry.Presence.DEAD) { j.phase=Phase.HOME; j.last=-1; state.setDirty(); continue; }
            var visit=state.markets().visit(host).orElse(null);
            boolean evening=clock/24000>j.date || clock/24000==j.date && clock%24000>=12000;
            boolean current=visit!=null && visit.id().equals(j.visit);
            boolean direct=routes.stream().anyMatch(c -> c.owner().equals(j.owner) && c.id().equals(j.route) && c.active() && c.joins(j.home,j.host));
            boolean agreement=permits(j.home,j.host);
            boolean ready=confirmed && current && direct && (agreement || j.phase==Phase.RETURNING || evening) && clock/24000>=j.date;
            boolean conversation=agreement && evening && clock/24000==j.date && clock%24000<13000 && j.late
                    && (j.phase==Phase.STAY || j.phase==Phase.EVENING);
            if(ready && conversation && j.phase==Phase.STAY) { j.phase=Phase.EVENING; state.setDirty(); }
            if(ready && evening && !conversation && j.phase!=Phase.RETURNING) {
                if(p.body()!=null) { retired.add(p); state.people().pauseVisit(p.id(),p.generation(),p.body()); }
                j.phase=Phase.RETURNING; j.elapsed=0; j.last=-1; state.setDirty();
            }
            long delta=ready && j.last>=0 && now>=j.last && now-j.last<=100?now-j.last:0;
            if(j.paused==ready) { j.paused=!ready; state.setDirty(); }
            j.last=ready?now:-1;
            if(delta==0 || j.phase==Phase.STAY || j.phase==Phase.EVENING) continue;
            j.elapsed=Math.min(j.duration,j.elapsed+delta); state.setDirty();
            if(j.elapsed==j.duration) {
                if(j.phase==Phase.OUTBOUND) j.phase=Phase.STAY;
                else if(j.phase==Phase.RETURNING && state.people().endVisit(p.id(),p.revision(),false)) j.phase=Phase.HOME;
                j.elapsed=0; j.last=-1;
            }
        }
    }
    /** Used at both quote and prepare boundaries; already reserved receipts retain their reconciliation path. */
    boolean mayTrade(UUID host,UUID person,long now) {
        if(readOnly()) return false; var j=journeys.get(person);
        return j==null || j.phase==Phase.HOME && host.equals(j.home)
                || j.host.equals(host) && j.phase==Phase.STAY && !j.paused
                    && state.marketDays().hasCurrentSlot(host,person,now)
                    && state.people().person(person).filter(p -> host.equals(p.visit())).isPresent();
    }
    boolean evening(UUID host,UUID person) {
        var j=readOnly()?null:journeys.get(person);
        return j!=null && j.host.equals(host) && j.phase==Phase.EVENING && !j.paused;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("bookings") instanceof ListTag bs) || bs.size()>16384*5
                || !(root.get("journeys") instanceof ListTag rows) || rows.size()>16384*5) { opaque=true; return; }
        try {
            for(var value:bs) {
                if(!(value instanceof CompoundTag tag) || !(tag.get("date") instanceof LongTag)) throw new IllegalArgumentException();
                var person=id(tag,"person"); var host=id(tag,"host"); long date=tag.getLongOr("date",-1);
                long elapsed=tag.getLongOr("elapsed",-1);
                var b=new Booking(host,date); b.elapsed=elapsed; b.payload=tag.copy();
                if(date<0 || !(tag.get("elapsed") instanceof LongTag) || elapsed<0 || elapsed>6000 || state.people().person(person).isEmpty()
                        || state.village(host).isEmpty() || bookings.putIfAbsent(person,b)!=null) throw new IllegalArgumentException();
            }
            for(var value:rows) {
                if(!(value instanceof CompoundTag tag) || !(tag.get("duration") instanceof LongTag) || !(tag.get("elapsed") instanceof LongTag)) throw new IllegalArgumentException();
                var person=id(tag,"person"); var home=id(tag,"home"); var host=id(tag,"host"); var booking=bookings.get(person);
                long date=tag.getLongOr("date",-1),duration=tag.getLongOr("duration",-1),elapsed=tag.getLongOr("elapsed",-1);
                var j=new Journey(person,home,host,id(tag,"visit"),id(tag,"owner"),id(tag,"route"),date,duration,tag.getBooleanOr("late",false));
                j.phase=Phase.valueOf(tag.getStringOr("phase","")); j.elapsed=elapsed; j.payload=tag.copy(); j.paused=true;
                var p=state.people().person(person).orElseThrow();
                if(home.equals(host) || !home.equals(p.village()) || duration<200 || duration>4400 || elapsed<0 || elapsed>duration || date<0
                        || booking==null || !booking.host.equals(host) || booking.date!=date || state.village(host).isEmpty()
                        || j.phase!=Phase.HOME && p.presence()!=KingdomPersonRegistry.Presence.DEAD && !host.equals(p.visit())
                        || j.phase!=Phase.HOME && !state.markets().visit(host).filter(v -> v.id().equals(j.visit) && v.traders().stream().anyMatch(t -> t.person().equals(person) && t.home().equals(home))).isPresent()
                        || journeys.putIfAbsent(person,j)!=null) throw new IllegalArgumentException();
            }
        } catch(RuntimeException malformed) { journeys.clear(); bookings.clear(); opaque=true; }
    }
    private static UUID id(CompoundTag tag,String name) { var id=UUID.fromString(tag.getStringOr(name,"")); if(id.equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException(); return id; }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var bs=new ListTag(); bookings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            var tag=e.getValue().payload.copy(); tag.putString("person",e.getKey().toString()); tag.putString("host",e.getValue().host.toString()); tag.putLong("date",e.getValue().date); tag.putLong("elapsed",e.getValue().elapsed); bs.add(tag);
        }); root.put("bookings",bs);
        var rows=new ListTag(); journeys.values().stream().sorted(Comparator.comparing(j -> j.person)).forEach(j -> {
            var tag=j.payload.copy(); tag.putString("person",j.person.toString()); tag.putString("home",j.home.toString()); tag.putString("host",j.host.toString());
            tag.putString("visit",j.visit.toString()); tag.putString("owner",j.owner.toString()); tag.putString("route",j.route.toString());
            tag.putLong("date",j.date); tag.putLong("duration",j.duration); tag.putLong("elapsed",j.elapsed); tag.putString("phase",j.phase.name()); tag.putBoolean("late",j.late); rows.add(tag);
        }); root.put("journeys",rows); return root;
    }
    private static final class Booking {
        final UUID host; final long date; long elapsed,last=-1; CompoundTag payload=new CompoundTag();
        Booking(UUID host,long date) { this.host=host; this.date=date; }
    }
    private static final class Journey {
        final UUID person,home,host,visit,owner,route; final long date,duration; final boolean late;
        long elapsed,last=-1; boolean paused=true; Phase phase=Phase.OUTBOUND; CompoundTag payload=new CompoundTag();
        Journey(UUID person,UUID home,UUID host,UUID visit,UUID owner,UUID route,long date,long duration,boolean late) {
            this.person=person; this.home=home; this.host=host; this.visit=visit; this.owner=owner; this.route=route; this.date=date; this.duration=duration; this.late=late;
        }
        View view() { return new View(person,home,host,visit,route,phase,phase==Phase.STAY||phase==Phase.EVENING||phase==Phase.HOME?0:duration-elapsed,paused,late); }
    }
}
