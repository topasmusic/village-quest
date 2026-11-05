package de.quest.kingdom;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Logical people survive changing buildings, bodies, promotion and unloaded chunks. */
public final class KingdomPersonRegistry {
    public static final int BODY_LIMIT=32;
    private static final int MAX_PEOPLE=131_072;
    private static final long RETURN_DAY=24_000, ABSENCE_GRACE=600, MAX_GAP=100;
    private final LivingKingdomsState state;
    private final Map<UUID,Person> people=new HashMap<>();
    private final Map<Slot,UUID> slots=new HashMap<>();
    private final Map<UUID,UUID> bodies=new HashMap<>();
    private final Set<UUID> loaded=new HashSet<>(),pending=new HashSet<>();
    private final Set<UUID> reservedIds=new HashSet<>(),reservedVillages=new HashSet<>(),reservedBodies=new HashSet<>();
    private final List<CompoundTag> quarantined=new ArrayList<>();
    private Tag retained=new CompoundTag();
    private boolean readOnly;
    private final KingdomBedRegistry beds;
    KingdomPersonRegistry(LivingKingdomsState state) { this.state=state; beds=new KingdomBedRegistry(state,this); }
    public KingdomBedRegistry beds() { return beds; }
    public enum Role {
        LEADER(BuildingScanner.Role.COUNCIL), INNKEEPER(BuildingScanner.Role.INN),
        STOREKEEPER(BuildingScanner.Role.STOREHOUSE), MARKET_TRADER(BuildingScanner.Role.MARKET);
        public final BuildingScanner.Role anchor;
        Role(BuildingScanner.Role anchor) { this.anchor=anchor; }
    }
    public enum Presence { DORMANT, PENDING, PRESENT, DEAD, VISITING }
    public record View(UUID id,UUID village,Role role,int slot,long revision,long generation,UUID body,BlockPos position,
                       long appearanceSeed,String name,boolean mayor,Presence presence,long returnTicks,UUID visit) {
        public int guest() { return role==Role.INNKEEPER && slot>0?slot-1:-1; }
        public net.minecraft.network.chat.Component displayName() { return guest()<0?net.minecraft.network.chat.Component.literal(name):de.quest.caravan.NpcNames.guest(de.quest.caravan.NpcNames.GUESTS.get(guest())); }
        public boolean female() { return (appearanceSeed&1L)!=0; }
        public int variant() { return Math.floorMod(appearanceSeed>>>1,3); }
        public int personality() { return Math.floorMod(appearanceSeed>>>3,4); }
    }
    public record Ticket(UUID person,long generation,UUID body) {}
    private record Slot(UUID village,Role role,int index) {}
    public boolean readOnly() { return readOnly || state.readOnly(); }
    public int loadedBodies() { return loaded.size()+pending.size(); }
    /** Bounded runtime body index, not a traversal of all saved villages or people. */
    public List<View> loadedPeople() {
        return loaded.stream().map(bodies::get).map(people::get).filter(person -> !blocked(person)).map(Person::view).toList();
    }
    /** Saved identity lookup; reading old conversation evidence must not create or require a loaded body. */
    public Optional<View> staff(UUID village,Role role) {
        return Optional.ofNullable(slots.get(new Slot(village,role,0))).flatMap(this::person);
    }
    public Optional<View> staffGuest(UUID village,int guest) {
        return guest<0 || guest>3?Optional.empty():Optional.ofNullable(slots.get(new Slot(village,Role.INNKEEPER,guest+1))).flatMap(this::person);
    }
    private boolean blocked(Person person) {
        return person==null || person.revision==Long.MAX_VALUE || readOnly() || reservedIds.contains(person.id) || reservedVillages.contains(person.slot.village)
                || person.body!=null && reservedBodies.contains(person.body) || state.village(person.slot.village).isEmpty();
    }
    public Optional<View> person(UUID id) { var person=people.get(id); return blocked(person)?Optional.empty():Optional.of(person.view()); }
    public List<View> visitors(UUID village) {
        return people.values().stream().filter(p -> !blocked(p) && village.equals(p.visit)).map(Person::view)
                .sorted(java.util.Comparator.comparing(View::id)).toList();
    }
    public Optional<View> ensure(UUID village,Role role,int slot) {
        if (readOnly() || village==null || role==null || !validSlot(role,slot) || state.village(village).isEmpty() || reservedVillages.contains(village)) return Optional.empty();
        var key=new Slot(village,role,slot); UUID existing=slots.get(key);
        if (existing!=null) return person(existing);
        UUID id=UUID.nameUUIDFromBytes(("village-quest:person/"+village+"/"+role+"/"+slot).getBytes(StandardCharsets.UTF_8));
        if (reservedIds.contains(id) || people.containsKey(id) || people.size()+quarantined.size()>=MAX_PEOPLE) return Optional.empty();
        var person=new Person(id,key,state.village(village).orElseThrow().arrival()); people.put(id,person); slots.put(key,id);
        state.setDirty(); return Optional.of(person.view());
    }
    public boolean promote(UUID id,long revision) {
        var person=people.get(id);
        if (!mutable(person,revision) || person.slot.role!=Role.LEADER) return false;
        if (!person.mayor) { person.mayor=true; changed(person); } return true;
    }
    public Optional<Ticket> reserve(UUID id,long revision,UUID body,BlockPos position,long now) {
        var person=people.get(id);
        if (!mutable(person,revision) || body==null || body.equals(BuildingSessionPolicy.NONE) || !validPosition(position)
                || person.presence!=Presence.DORMANT && person.presence!=Presence.VISITING || person.body!=null || person.slot.role==Role.INNKEEPER && person.slot.index>0 && state.tavernGuests().active(person.slot.village)!=person.slot.index-1 || person.generation==Long.MAX_VALUE
                || bodies.containsKey(body) || reservedBodies.contains(body) || loadedBodies()>=BODY_LIMIT) return Optional.empty();
        var anchor=state.anchors().official(person.visit==null?person.slot.village:person.visit,person.slot.role.anchor);
        if (anchor.isEmpty() || !state.anchors().maySpawn(anchor.get().id(),now)
                || state.anchors().report(anchor.get().id()).filter(report -> report.walkable().contains(position)).isEmpty()) return Optional.empty();
        if(person.slot.role==Role.MARKET_TRADER && state.marketDays().appearancePlace(person.visit==null?person.slot.village:person.visit,id,now)
                .filter(place -> place.position().equals(position)).isEmpty()) return Optional.empty();
        person.body=body; person.position=position.immutable(); person.generation++; person.presence=person.visit==null?Presence.PENDING:Presence.VISITING;
        person.lastObserved=-1; person.absentTicks=0; bodies.put(body,id); pending.add(body); changed(person);
        return Optional.of(new Ticket(id,person.generation,body));
    }
    public boolean commit(Ticket ticket) {
        var person=matching(ticket.person,ticket.generation,ticket.body);
        if (person==null) return false;
        if(person.presence==Presence.VISITING && pending.remove(ticket.body)) { loaded.add(ticket.body); changed(person); return true; }
        if(person.presence==Presence.VISITING && loaded.contains(ticket.body)) return true;
        if (person.presence==Presence.PRESENT) return true;
        if (person.presence!=Presence.PENDING) return false;
        person.presence=Presence.PRESENT; pending.remove(ticket.body); loaded.add(ticket.body); changed(person); return true;
    }
    public boolean abort(Ticket ticket) {
        var person=matching(ticket.person,ticket.generation,ticket.body);
        if (person==null || person.presence!=Presence.PENDING && !pending.contains(ticket.body)) return false;
        clearBody(person); person.presence=person.visit==null?Presence.DORMANT:Presence.VISITING; changed(person); return true;
    }
    public boolean markLoaded(UUID id,long generation,UUID body) {
        var person=matching(id,generation,body);
        if (person==null || person.presence==Presence.DEAD || !loaded.contains(body) && !pending.contains(body) && loadedBodies()>=BODY_LIMIT) return false;
        pending.remove(body); loaded.add(body); person.lastObserved=-1; person.absentTicks=0;
        if (person.presence==Presence.DORMANT || person.presence==Presence.PENDING) { person.presence=Presence.PRESENT; changed(person); }
        return true;
    }
    public boolean available(UUID id,long generation,UUID body) {
        var person=matching(id,generation,body);
        return person!=null && loaded.contains(body) && (person.presence==Presence.PRESENT || person.presence==Presence.VISITING);
    }
    public boolean arrival(Ticket ticket) {
        var person=matching(ticket.person,ticket.generation,ticket.body);
        if(person==null || person.arrivalNoted || !available(ticket.person,ticket.generation,ticket.body)) return false;
        person.arrivalNoted=true; changed(person); return true;
    }
    public boolean unloaded(UUID id,long generation,UUID body,BlockPos position) {
        var person=matching(id,generation,body); if (person==null || !validPosition(position)) return false;
        loaded.remove(body); pending.remove(body); person.position=position.immutable(); person.lastObserved=-1;
        if (person.presence!=Presence.VISITING) person.presence=Presence.DORMANT;
        changed(person); return true;
    }
    public boolean died(UUID id,long generation,UUID body) {
        var person=matching(id,generation,body); if (person==null) return false;
        clearBody(person); person.presence=person.slot.role==Role.INNKEEPER && person.slot.index>0?Presence.DORMANT:Presence.DEAD; person.returnTicks=0; person.visit=null; person.lastObserved=-1;
        changed(person); return true;
    }
    /** Intentional removal releases the current generation without inventing a death or ending a visit. */
    public boolean dismiss(UUID id,long generation,UUID body) {
        var person=matching(id,generation,body);
        if (person==null || person.presence==Presence.VISITING) return false;
        clearBody(person); person.presence=Presence.DORMANT; person.returnTicks=0;
        changed(person); return true;
    }
    boolean pauseVisit(UUID id,long generation,UUID body) {
        var person=matching(id,generation,body);
        if(person==null || person.presence!=Presence.VISITING) return false;
        clearBody(person); changed(person); return true;
    }
    /** Only actual loaded near observations advance time; restart and unknown gaps cannot count. */
    public void observeActive(UUID id,long tick,boolean confirmedLoaded) {
        var person=people.get(id); if (blocked(person) || tick<0 || person.revision==Long.MAX_VALUE) return;
        if (!confirmedLoaded) { person.lastObserved=-1; return; }
        long delta=person.lastObserved<0 || tick<person.lastObserved || tick-person.lastObserved>MAX_GAP?0:tick-person.lastObserved;
        person.lastObserved=tick;
        if (person.presence==Presence.DEAD && delta>0) {
            person.returnTicks=Math.min(RETURN_DAY,person.returnTicks+delta);
            if (person.returnTicks==RETURN_DAY) { person.presence=Presence.DORMANT; changed(person); }
            else state.setDirty();
        }
    }
    /** A controller may reconcile an orphan only after repeatedly loaded, genuinely empty last-position evidence. */
    public void observeAbsent(UUID id,long tick,boolean confirmedAbsent) {
        var person=people.get(id);
        if (blocked(person) || person.body==null || loaded.contains(person.body) || person.presence==Presence.DEAD || tick<0) return;
        if (!confirmedAbsent) { person.lastObserved=-1; return; }
        long delta=person.lastObserved<0 || tick<person.lastObserved || tick-person.lastObserved>MAX_GAP?0:tick-person.lastObserved;
        person.lastObserved=tick; person.absentTicks=Math.min(ABSENCE_GRACE,person.absentTicks+delta);
        if (person.absentTicks==ABSENCE_GRACE && person.revision<Long.MAX_VALUE) {
            clearBody(person); person.presence=person.visit==null?Presence.DORMANT:Presence.VISITING; changed(person);
        }
    }
    public boolean beginVisit(UUID id,long revision,UUID destination) {
        return beginVisit(id,revision,destination,false);
    }
    private boolean beginVisit(UUID id,long revision,UUID destination,boolean market) {
        var person=people.get(id);
        if (!mutable(person,revision) || person.slot.role!=Role.LEADER && !(market && person.slot.role==Role.MARKET_TRADER)
                || person.presence!=Presence.PRESENT && person.presence!=Presence.DORMANT
                || destination==null || destination.equals(person.slot.village) || state.village(destination).isEmpty()
                || !state.village(destination).orElseThrow().legacyKey().dimension().equals(state.village(person.slot.village).orElseThrow().legacyKey().dimension())) return false;
        person.visit=destination; person.presence=Presence.VISITING; changed(person); return true;
    }
    boolean depart(UUID id,long revision,UUID destination) {
        if(revision>=Long.MAX_VALUE-2 || !beginVisit(id,revision,destination,true)) return false;
        // Release the former generation before virtual travel. An old chunk body can never reactivate.
        var person=people.get(id); clearBody(person); changed(person); return true;
    }
    public boolean endVisit(UUID id,long revision,boolean returnedBody) {
        var person=people.get(id); if (!mutable(person,revision) || person.presence!=Presence.VISITING) return false;
        person.visit=null;
        if (!returnedBody) clearBody(person);
        person.presence=person.body!=null && loaded.contains(person.body)?Presence.PRESENT:Presence.DORMANT;
        changed(person); return true;
    }
    private Person matching(UUID id,long generation,UUID body) {
        var person=people.get(id);
        return blocked(person) || body==null || !body.equals(person.body) || generation!=person.generation?null:person;
    }
    private boolean mutable(Person person,long revision) { return !blocked(person) && revision==person.revision && revision<Long.MAX_VALUE; }
    private void changed(Person person) { person.revision++; state.setDirty(); }
    private void clearBody(Person person) {
        beds.releasePerson(person.id);
        if (person.body!=null) { bodies.remove(person.body); loaded.remove(person.body); pending.remove(person.body); }
        person.body=null; person.absentTicks=0; person.lastObserved=-1;
    }
    private static boolean validSlot(Role role,int slot) { return slot>=0 && slot<(role==Role.MARKET_TRADER || role==Role.INNKEEPER?5:1); }
    private static boolean validPosition(BlockPos pos) {
        return pos!=null && Math.abs((long)pos.getX())<=30_000_000 && Math.abs((long)pos.getZ())<=30_000_000 && Math.abs((long)pos.getY())<=4096;
    }
    void load(Tag payload) {
        if (payload==null) return; retained=payload.copy();
        if (!(payload instanceof CompoundTag root) || root.getIntOr("schema",0)!=1 || !(root.get("records") instanceof ListTag records)
                || records.size()>MAX_PEOPLE) { readOnly=true; return; }
        for (var record:records) {
            if (!(record instanceof CompoundTag tag)) { readOnly=true; return; }
            try {
                UUID id=UUID.fromString(tag.getStringOr("id","")),village=UUID.fromString(tag.getStringOr("village",""));
                var role=Role.valueOf(tag.getStringOr("role","")); int slot=tag.getIntOr("slot",-1);
                if (id.equals(BuildingSessionPolicy.NONE) || !validSlot(role,slot) || state.village(village).isEmpty()) throw new IllegalArgumentException("Invalid identity");
                var person=new Person(id,new Slot(village,role,slot),new BlockPos(tag.getIntOr("x",Integer.MIN_VALUE),tag.getIntOr("y",Integer.MIN_VALUE),tag.getIntOr("z",Integer.MIN_VALUE)));
                person.retained=tag.copy(); person.seed=tag.getLongOr("seed",person.seed); person.name=tag.getStringOr("name",person.name);
                person.revision=tag.getLongOr("revision",-1); person.generation=tag.getLongOr("generation",-1);
                person.presence=Presence.valueOf(tag.getStringOr("presence","")); person.mayor=tag.getBooleanOr("mayor",false);
                person.arrivalNoted=tag.getBooleanOr("arrivalNoted",false);
                person.returnTicks=tag.getLongOr("returnTicks",0); person.body=optionalId(tag,"body"); person.visit=optionalId(tag,"visit");
                if (!validPosition(person.position) || person.revision<0 || person.generation<0 || person.returnTicks<0 || person.returnTicks>RETURN_DAY
                        || person.name.isBlank() || person.name.codePointCount(0,person.name.length())>48 || person.name.codePoints().anyMatch(Character::isISOControl)
                        || people.containsKey(id) || slots.containsKey(person.slot) || person.body!=null && (person.generation==0 || bodies.containsKey(person.body))
                        || person.presence==Presence.DEAD && person.body!=null || person.mayor && role!=Role.LEADER
                        || (person.presence==Presence.VISITING)!=(person.visit!=null)
                        || (person.presence==Presence.PRESENT || person.presence==Presence.PENDING) && person.body==null
                        || person.visit!=null && (person.slot.role!=Role.LEADER && person.slot.role!=Role.MARKET_TRADER || state.village(person.visit).isEmpty()
                            || !state.village(person.visit).orElseThrow().legacyKey().dimension().equals(state.village(village).orElseThrow().legacyKey().dimension()))) throw new IllegalArgumentException("Invalid person record");
                people.put(id,person); slots.put(person.slot,id); if (person.body!=null) bodies.put(person.body,id);
            } catch (IllegalArgumentException bad) { quarantine(tag); }
        }
        beds.load(root.get("beds"));
    }
    private static UUID optionalId(CompoundTag tag,String key) {
        if (!tag.contains(key)) return null;
        UUID id=UUID.fromString(tag.getStringOr(key,"")); if (id.equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException("Invalid UUID"); return id;
    }
    private void quarantine(CompoundTag tag) {
        quarantined.add(tag.copy());
        try { reservedIds.add(UUID.fromString(tag.getStringOr("id",""))); } catch(IllegalArgumentException ignored) { }
        try { reservedVillages.add(UUID.fromString(tag.getStringOr("village",""))); } catch(IllegalArgumentException ignored) { readOnly=true; }
        try { UUID body=optionalId(tag,"body"); if (body!=null) reservedBodies.add(body); } catch(IllegalArgumentException ignored) { }
    }
    Tag toNbt() {
        if (readOnly) return retained.copy();
        var root=retained instanceof CompoundTag compound?compound.copy():new CompoundTag(); root.putInt("schema",1);
        var records=new ListTag(); people.values().stream().sorted((a,b) -> a.id.compareTo(b.id)).forEach(person -> {
            var tag=person.retained.copy(); tag.putString("id",person.id.toString()); tag.putString("village",person.slot.village.toString());
            tag.putString("role",person.slot.role.name()); tag.putInt("slot",person.slot.index); tag.putLong("seed",person.seed); tag.putString("name",person.name);
            tag.putInt("x",person.position.getX()); tag.putInt("y",person.position.getY()); tag.putInt("z",person.position.getZ());
            tag.putLong("revision",person.revision); tag.putLong("generation",person.generation); tag.putString("presence",person.presence.name());
            tag.putBoolean("mayor",person.mayor); tag.putLong("returnTicks",person.returnTicks);
            tag.putBoolean("arrivalNoted",person.arrivalNoted);
            if (person.body!=null) tag.putString("body",person.body.toString()); else tag.remove("body");
            if (person.visit!=null) tag.putString("visit",person.visit.toString()); else tag.remove("visit");
            records.add(tag);
        }); quarantined.forEach(tag -> records.add(tag.copy())); root.put("records",records); root.put("beds",beds.toNbt()); return root;
    }
    private static final class Person {
        final UUID id; final Slot slot;
        UUID body,visit; BlockPos position; long revision,generation,seed,returnTicks,lastObserved=-1,absentTicks;
        String name; boolean mayor,arrivalNoted; Presence presence=Presence.DORMANT; CompoundTag retained=new CompoundTag();
        Person(UUID id,Slot slot,BlockPos position) {
            this.id=id; this.slot=slot; this.position=position.immutable(); seed=id.getMostSignificantBits()^id.getLeastSignificantBits();
            var names=(seed&1L)!=0?de.quest.caravan.NpcNames.VILLAGE_FEMALE:de.quest.caravan.NpcNames.VILLAGE_MALE;
            name=slot.role==Role.INNKEEPER && slot.index>0?List.of("Lutte","Simon","Marius","Raphael").get(slot.index-1):names.get(Math.floorMod(seed>>>1,names.size()));
        }
        View view() { return new View(id,slot.village,slot.role,slot.index,revision,generation,body,position,seed,name,mayor,presence,returnTicks,visit); }
    }
}
