package de.quest.kingdom;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Explicit child places of one official market. Saved configuration is never loaded building evidence. */
public final class KingdomMarketPlaces {
    private final LivingKingdomsState state;
    private final Map<UUID,Market> markets=new HashMap<>();
    private Tag retained=new CompoundTag();
    private boolean opaque;
    KingdomMarketPlaces(LivingKingdomsState state) { this.state=state; }
    public enum Result { APPLIED,UNCHANGED,DENIED,STALE,NO_EVIDENCE,CONFLICT,INVALID,READ_ONLY }
    public record Place(int slot,long anchorRevision,BlockPos position,BlockPos counter,BlockPos container) {
        public Place { position=position.immutable(); counter=counter.immutable(); container=container.immutable(); }
    }
    public boolean readOnly() { return opaque || state.readOnly(); }
    public long revision(UUID anchor) { var market=markets.get(anchor); return market==null?0:market.revision; }
    public List<Place> assigned(UUID anchor) {
        var market=markets.get(anchor); return readOnly() || market==null?List.of():List.copyOf(market.places.values());
    }
    public List<Place> usable(UUID anchor,long now) {
        var view=state.anchors().anchor(anchor).orElse(null);
        if(readOnly() || view==null || view.role()!=BuildingScanner.Role.MARKET || !state.anchors().mayContinue(anchor,now)) return List.of();
        var report=state.anchors().report(anchor).orElse(null);
        return assigned(anchor).stream().filter(p -> p.anchorRevision==view.revision() && evidenced(anchor,p,report)).toList();
    }
    /** Bounded proposals from the certified local floor; never auto-assigns or reads world blocks. */
    public List<Place> candidates(UUID anchor,long now) {
        var view=state.anchors().anchor(anchor).orElse(null);
        if(readOnly() || view==null || view.role()!=BuildingScanner.Role.MARKET || !state.anchors().maySpawn(anchor,now)) return List.of();
        var report=state.anchors().report(anchor).orElse(null); if(report==null) return List.of();
        var counters=report.stations().getOrDefault(BuildingScanner.Feature.STALL,Set.of());
        var boxes=report.stations().getOrDefault(BuildingScanner.Feature.CONTAINER,Set.of()); var result=new ArrayList<Place>();
        var floor=report.walkable().stream().sorted(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(view.position())).thenComparingLong(BlockPos::asLong)).toList();
        for(var position:floor) {
            var localCounters=new ArrayList<BlockPos>(); var localBoxes=new ArrayList<BlockPos>();
            for(int dy=-1;dy<=1;dy++) for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                int distance=Math.abs(dx)+Math.abs(dz); if(distance>2) continue;
                var p=position.offset(dx,dy,dz);
                if(distance<=1 && counters.contains(p)) localCounters.add(p);
                if(boxes.contains(p)) localBoxes.add(p);
            }
            for(var counter:localCounters) for(var box:localBoxes) {
                var place=new Place(0,view.revision(),position,counter,box);
                if(evidenced(anchor,place,report)) { result.add(place); if(result.size()==32) return List.copyOf(result); }
            }
        }
        return List.copyOf(result);
    }
    public Result assign(UUID actor,UUID town,long townRevision,UUID anchor,long anchorRevision,long revision,
                         int slot,BlockPos position,BlockPos counter,BlockPos container,long now) {
        Result access=access(actor,town,townRevision,anchor,anchorRevision,revision);
        if(access!=null) return access;
        if(slot<0 || slot>=5 || !valid(position) || !valid(counter) || !valid(container)) return Result.INVALID;
        var place=new Place(slot,anchorRevision,position,counter,container);
        var oldPlace=assigned(anchor).stream().filter(p -> p.slot()==slot).findFirst().orElse(null);
        var report=state.anchors().report(anchor).orElse(null);
        boolean sameSavedPlace=oldPlace!=null && oldPlace.position().equals(position) && oldPlace.counter().equals(counter) && oldPlace.container().equals(container);
        if(!sameSavedPlace && (report==null || !report.stations().getOrDefault(BuildingScanner.Feature.STALL,Set.of()).contains(counter))) return Result.NO_EVIDENCE;
        if(!state.anchors().maySpawn(anchor,now) || !evidenced(anchor,place,state.anchors().report(anchor).orElse(null))) return Result.NO_EVIDENCE;
        var view=state.anchors().anchor(anchor).orElseThrow();
        for(var entry:markets.entrySet()) {
            var other=state.anchors().anchor(entry.getKey()).orElse(null);
            if(other==null || !other.dimension().equals(view.dimension())) continue;
            for(var old:entry.getValue().places.values()) {
                if(entry.getKey().equals(anchor) && old.slot==slot) continue;
                if(overlap(old,place)) return Result.CONFLICT;
            }
        }
        var market=markets.computeIfAbsent(anchor,ignored -> new Market());
        if(place.equals(market.places.get(slot))) return Result.UNCHANGED;
        market.places.put(slot,place); market.revision++; state.setDirty(); return Result.APPLIED;
    }
    public Result remove(UUID actor,UUID town,long townRevision,UUID anchor,long anchorRevision,long revision,int slot) {
        Result access=access(actor,town,townRevision,anchor,anchorRevision,revision); if(access!=null) return access;
        if(slot<0 || slot>=5) return Result.INVALID;
        var market=markets.get(anchor); if(market==null || !market.places.containsKey(slot)) return Result.UNCHANGED;
        market.places.remove(slot); market.revision++; state.setDirty(); return Result.APPLIED;
    }
    private Result access(UUID actor,UUID town,long townRevision,UUID anchor,long anchorRevision,long revision) {
        if(readOnly()) return Result.READ_ONLY;
        var village=state.village(town).orElse(null); var view=state.anchors().official(town,BuildingScanner.Role.MARKET).orElse(null);
        if(village==null || !state.canManageVillage(actor,town)) return Result.DENIED;
        if(view==null || !view.id().equals(anchor)) return Result.INVALID;
        if(village.revision()!=townRevision || view.revision()!=anchorRevision || revision!=revision(anchor)
                || revision<0 || revision==Long.MAX_VALUE) return Result.STALE;
        if(!markets.containsKey(anchor) && markets.size()>=16384) return Result.INVALID;
        return null;
    }
    private boolean evidenced(UUID anchor,Place place,BuildingScanner.Report report) {
        var view=state.anchors().anchor(anchor).orElse(null);
        if(view==null || report==null || report.status()!=BuildingScanner.Status.VALID || report.role()!=BuildingScanner.Role.MARKET
                || !report.walkable().contains(place.position) || place.counter.equals(place.container)
                || place.position.equals(place.counter) || place.position.equals(place.container)
                || !near(place.position,place.counter) || !nearContainer(place.position,place.container)
                || !report.stations().getOrDefault(BuildingScanner.Feature.COUNTER,Set.of()).contains(place.counter)
                || !report.stations().getOrDefault(BuildingScanner.Feature.CONTAINER,Set.of()).contains(place.container)) return false;
        return state.anchors().stationOwner(view.dimension(),place.counter).filter(anchor::equals).isPresent()
                && state.anchors().stationOwner(view.dimension(),place.container).filter(anchor::equals).isPresent();
    }
    private static boolean near(BlockPos a,BlockPos b) {
        return Math.abs((long)a.getY()-b.getY())<=1 && Math.abs((long)a.getX()-b.getX())+Math.abs((long)a.getZ()-b.getZ())<=1;
    }
    private static boolean nearContainer(BlockPos a,BlockPos b) {
        return Math.abs((long)a.getY()-b.getY())<=1 && Math.abs((long)a.getX()-b.getX())+Math.abs((long)a.getZ()-b.getZ())<=2;
    }
    private static boolean overlap(Place a,Place b) {
        var occupied=Set.of(a.position,a.counter,a.container);
        return occupied.contains(b.position) || occupied.contains(b.counter) || occupied.contains(b.container);
    }
    private static boolean valid(BlockPos p) {
        return p!=null && Math.abs((long)p.getX())<=30_000_000 && Math.abs((long)p.getZ())<=30_000_000 && Math.abs((long)p.getY())<=4096;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("markets") instanceof ListTag rows) || rows.size()>16384) { opaque=true; return; }
        try {
            for(var row:rows) {
                if(!(row instanceof CompoundTag tag) || !(tag.get("revision") instanceof LongTag)
                        || !(tag.get("places") instanceof ListTag places) || places.size()>5) throw new IllegalArgumentException();
                var id=UUID.fromString(tag.getStringOr("anchor","")); var market=new Market(); market.retained=tag.copy();
                market.revision=tag.getLongOr("revision",-1);
                if(market.revision<0 || id.equals(BuildingSessionPolicy.NONE) || state.anchors().anchor(id).filter(a -> a.role()==BuildingScanner.Role.MARKET).isEmpty()
                        || markets.containsKey(id)) throw new IllegalArgumentException();
                for(var value:places) {
                    if(!(value instanceof CompoundTag p) || !(p.get("slot") instanceof IntTag) || !(p.get("anchorRevision") instanceof LongTag)) throw new IllegalArgumentException();
                    int slot=p.getIntOr("slot",-1); long revision=p.getLongOr("anchorRevision",-1);
                    var place=new Place(slot,revision,pos(p,"position"),pos(p,"counter"),pos(p,"container"));
                    if(slot<0 || slot>=5 || revision<0 || place.position.equals(place.counter) || place.position.equals(place.container)
                            || place.counter.equals(place.container) || !near(place.position,place.counter) || !nearContainer(place.position,place.container)
                            || market.places.values().stream().anyMatch(old -> overlap(old,place)) || market.places.putIfAbsent(slot,place)!=null) throw new IllegalArgumentException();
                    market.payloads.put(slot,p.copy());
                }
                markets.put(id,market);
            }
            var occupied=new HashSet<String>();
            for(var entry:markets.entrySet()) {
                String dimension=state.anchors().anchor(entry.getKey()).orElseThrow().dimension();
                for(var place:entry.getValue().places.values()) for(var pos:List.of(place.position,place.counter,place.container))
                    if(!occupied.add(dimension+"/"+pos.getX()+"/"+pos.getY()+"/"+pos.getZ())) throw new IllegalArgumentException();
            }
        } catch(RuntimeException malformed) { markets.clear(); opaque=true; }
    }
    private static BlockPos pos(CompoundTag root,String key) {
        var p=root.getCompoundOrEmpty(key);
        if(!(p.get("x") instanceof IntTag) || !(p.get("y") instanceof IntTag) || !(p.get("z") instanceof IntTag)) throw new IllegalArgumentException();
        var result=new BlockPos(p.getIntOr("x",0),p.getIntOr("y",0),p.getIntOr("z",0));
        if(!valid(result)) throw new IllegalArgumentException(); return result;
    }
    private static void putPos(CompoundTag root,String key,BlockPos pos) {
        var p=root.getCompoundOrEmpty(key).copy(); p.putInt("x",pos.getX()); p.putInt("y",pos.getY()); p.putInt("z",pos.getZ()); root.put(key,p);
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); markets.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var market=entry.getValue(); var row=market.retained.copy(); row.putString("anchor",entry.getKey().toString()); row.putLong("revision",market.revision);
            var places=new ListTag(); market.places.forEach((slot,p) -> {
                var tag=market.payloads.getOrDefault(slot,new CompoundTag()).copy(); tag.putInt("slot",slot); tag.putLong("anchorRevision",p.anchorRevision);
                putPos(tag,"position",p.position); putPos(tag,"counter",p.counter); putPos(tag,"container",p.container); places.add(tag);
            }); row.put("places",places); rows.add(row);
        }); root.put("markets",rows); return root;
    }
    private static final class Market {
        long revision; CompoundTag retained=new CompoundTag();
        final TreeMap<Integer,Place> places=new TreeMap<>(); final Map<Integer,CompoundTag> payloads=new HashMap<>();
    }
}
