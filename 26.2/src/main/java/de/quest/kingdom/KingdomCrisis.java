package de.quest.kingdom;

import de.quest.village.VillageNeed;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Item;

/** One private mediation at a time. Only explicit choices suspend promises; elapsed time never accuses a player. */
public final class KingdomCrisis {
    public enum Phase { OPEN, AID, WITHDRAWN, RECONCILING, READY, RESOLVED, LAPSED }
    public enum Choice { SUPPORT, KEEP_HOME, APOLOGIZE, REOPEN, DEFER }
    public enum Kind { SUPPLY, LOYALTY }
    public record View(UUID id,long revision,UUID host,UUID other,VillageNeed need,int cycle,int personality,
                       boolean competing,Phase phase,int remaining,int heard,int delivery,Kind kind) {}
    private final LivingKingdomsState state;
    private final Map<UUID,CompoundTag> records=new HashMap<>();
    private Tag retained=new CompoundTag(); private boolean opaque;
    KingdomCrisis(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly() || state.diplomacy().readOnly() || state.relations().readOnly(); }
    public Optional<View> current(UUID actor) {
        var tag=readOnly()?null:records.get(actor);
        return tag==null || !(tag.get("current") instanceof CompoundTag current)?Optional.empty():Optional.of(view(current));
    }
    public List<VillageNeed> requestedNeeds(UUID actor) {
        return current(actor).filter(v -> v.phase==Phase.AID || v.phase==Phase.RECONCILING && v.personality==1)
                .map(v -> List.of(v.need)).orElse(List.of());
    }
    private static View view(CompoundTag t) {
        return new View(UUID.fromString(t.getStringOr("id","")),t.getLongOr("revision",0),UUID.fromString(t.getStringOr("host","")),
                UUID.fromString(t.getStringOr("other","")),VillageNeed.valueOf(t.getStringOr("need","")),t.getIntOr("cycle",0),
                t.getIntOr("personality",0),t.getBooleanOr("competing",false),Phase.valueOf(t.getStringOr("phase","")),
                t.getIntOr("remaining",0),t.getIntOr("heard",0),t.getIntOr("delivery",0),Kind.valueOf(t.getStringOr("kind","SUPPLY")));
    }
    private static boolean terminal(Phase phase) { return phase==Phase.RESOLVED || phase==Phase.LAPSED; }
    Optional<View> startLoyalty(UUID actor,UUID host,UUID other,VillageNeed need,int cycle,int personality) {
        var old=records.get(actor);
        if(old!=null && old.contains("lastLoyalty") && old.getLongOr("activity",0)-old.getLongOr("lastLoyalty",0)<192000) return Optional.empty();
        if(state.diplomacy().between(host,other,KingdomDiplomacy.Type.FRIENDSHIP)
                .filter(d -> d.status()==KingdomDiplomacy.Status.SIGNED && state.diplomacy().met(d)).isEmpty()) return Optional.empty();
        var started=start(actor,host,other,need,cycle,personality,true);
        if(started.isEmpty()) return started;
        change(actor,t -> t.putString("kind",Kind.LOYALTY.name()));
        var row=records.get(actor); row.putLong("lastLoyalty",row.getLongOr("activity",0)); return current(actor);
    }
    Optional<View> start(UUID actor,UUID host,UUID other,VillageNeed need,int cycle,int personality,boolean competing) {
        if(readOnly() || actor==null || host==null || other==null || host.equals(other) || need==null || cycle<0 || personality<0 || personality>3
                || !state.canManageVillage(actor,host) || current(actor).filter(v -> !terminal(v.phase)).isPresent()
                || state.diplomacy().between(host,other,KingdomDiplomacy.Type.SUPPLY).filter(d -> d.status()==KingdomDiplomacy.Status.SIGNED).isEmpty()) return Optional.empty();
        var previous=records.get(actor); if(previous==null && records.size()>=16384) return Optional.empty();
        var row=previous==null?new CompoundTag():previous.copy();
        var seen=row.get("seen") instanceof CompoundTag saved?saved.copy():new CompoundTag();
        String source=host+":"+other;
        long activity=row.getLongOr("activity",0);
        if(seen.contains(source) && cycle<=seen.getIntOr(source,-1) || !seen.contains(source) && seen.size()>=1024
                || row.contains("lastCase") && activity-row.getLongOr("lastCase",0)<48000) return Optional.empty();
        var tag=new CompoundTag(); tag.putString("id",UUID.nameUUIDFromBytes((actor+":"+source+":"+cycle).getBytes(StandardCharsets.UTF_8)).toString());
        tag.putLong("revision",0); tag.putString("host",host.toString()); tag.putString("other",other.toString()); tag.putString("need",need.name());
        tag.putInt("cycle",cycle); tag.putInt("personality",personality); tag.putBoolean("competing",competing); tag.putString("phase",Phase.OPEN.name());
        int days=need.villageType()==de.quest.shrine.VillageBondType.GRANARY || need==VillageNeed.PASTURE_FODDER?1:2;
        tag.putInt("remaining",days*24000); tag.putInt("heard",0); tag.putInt("delivery",0);
        seen.putInt(source,cycle); row.put("seen",seen); row.putLong("activity",activity); row.putLong("lastCase",activity); row.put("current",tag);
        records.put(actor,row); state.setDirty(); return Optional.of(view(tag));
    }
    /** Native scheduler supplies at most one observed second; no saved clock or wall-time catch-up. */
    void advance(UUID actor,int ticks,boolean eligible) {
        if(readOnly() || !eligible || ticks<1 || ticks>20 || !records.containsKey(actor)) return;
        var row=records.get(actor); long activity=row.getLongOr("activity",0);
        row.putLong("activity",activity>Long.MAX_VALUE-ticks?Long.MAX_VALUE:activity+ticks);
        var current=current(actor);
        if(current.filter(v -> v.phase==Phase.OPEN || v.phase==Phase.AID).isPresent()) {
            var v=current.get(); int remaining=Math.max(0,v.remaining-ticks);
            change(actor,t -> { t.putInt("remaining",remaining); if(remaining==0) t.putString("phase",Phase.LAPSED.name()); });
        }
        state.setDirty();
    }
    boolean choose(UUID actor,UUID id,long revision,Choice choice) {
        var current=current(actor); if(current.isEmpty() || choice==null) return false; var v=current.get();
        if(!v.id.equals(id) || v.revision!=revision || revision==Long.MAX_VALUE || !state.canManageVillage(actor,v.host)) return false;
        Phase next;
        switch(choice) {
            case SUPPORT -> { if(v.phase!=Phase.OPEN || v.kind==Kind.LOYALTY && v.heard!=3) return false; next=Phase.AID; }
            case KEEP_HOME -> {
                if(v.kind==Kind.LOYALTY && (v.heard!=3 || !state.relations().canRemember(v.host,v.other)
                        || !state.diplomacy().canPause(actor,v.host,v.other,KingdomDiplomacy.Type.FRIENDSHIP))) return false;
                if(v.phase!=Phase.OPEN || !state.diplomacy().pause(actor,v.host,v.other,v.id)) return false;
                if(v.kind==Kind.LOYALTY) {
                    state.diplomacy().pause(actor,v.host,v.other,v.id,KingdomDiplomacy.Type.FRIENDSHIP);
                    state.relations().remember(v.host,v.other,v.id,"loyalty");
                }
                next=Phase.WITHDRAWN;
            }
            case APOLOGIZE -> { if(v.phase!=Phase.WITHDRAWN || (v.heard&2)==0) return false; next=Phase.RECONCILING; }
            case REOPEN -> {
                if(v.kind==Kind.LOYALTY && (!state.relations().canRemember(v.host,v.other)
                        || !state.diplomacy().canReopen(actor,v.host,v.other,v.id,KingdomDiplomacy.Type.FRIENDSHIP))) return false;
                if(v.phase!=Phase.READY || !state.diplomacy().reopen(actor,v.host,v.other,v.id)) return false;
                if(v.kind==Kind.LOYALTY) {
                    state.diplomacy().reopen(actor,v.host,v.other,v.id,KingdomDiplomacy.Type.FRIENDSHIP);
                    state.relations().remember(v.host,v.other,v.id,"reconciliation");
                }
                next=Phase.RESOLVED;
            }
            case DEFER -> { if(v.phase!=Phase.OPEN) return false; next=Phase.LAPSED; }
            default -> { return false; }
        }
        return change(actor,t -> {
            t.putString("phase",next.name()); if(next==Phase.WITHDRAWN || next==Phase.RECONCILING) t.putInt("heard",0);
            if(next==Phase.RECONCILING) {
                t.putLong("meetingRevision",state.diplomacy().between(v.host,v.other,KingdomDiplomacy.Type.FRIENDSHIP).map(KingdomDiplomacy.View::revision).orElse(-1L));
                t.putInt("repairSequence",state.relations().latestReceipt(v.host,v.other,actor)); t.putInt("delivery",0);
            }
        });
    }
    long meetingBaseline(UUID actor) {
        return current(actor).isEmpty()?-1:((CompoundTag)records.get(actor).get("current")).getLongOr("meetingRevision",-1);
    }
    private int repairSequence(UUID actor) { return ((CompoundTag)records.get(actor).get("current")).getIntOr("repairSequence",0); }
    boolean freshRepairDelivery(UUID actor) {
        return current(actor).filter(v -> v.phase==Phase.RECONCILING && state.relations().latestReceipt(v.host,v.other,actor)>repairSequence(actor)).isPresent();
    }
    boolean hear(UUID actor,UUID village) {
        var current=current(actor); if(current.isEmpty() || terminal(current.get().phase)) return false; var v=current.get();
        int bit=v.host.equals(village)?1:v.other.equals(village)?2:0;
        if(bit==0 || (v.heard&bit)!=0) return false;
        boolean changed=change(actor,t -> t.putInt("heard",v.heard|bit)); finishAid(actor); finishFreightRepair(actor); return changed;
    }
    boolean delivered(UUID actor,UUID home,UUID destination,int sequence,Item item) {
        var current=current(actor); if(current.isEmpty()) return false; var v=current.get();
        if(v.phase!=Phase.AID && (v.phase!=Phase.RECONCILING || v.personality!=1 || sequence<=repairSequence(actor))
                || !v.host.equals(home) || !v.other.equals(destination) || item==null || !v.need.matches(item)
                || sequence<=0 || !state.relations().hasReceipt(home,destination,actor,sequence)) return false;
        boolean changed=change(actor,t -> t.putInt("delivery",sequence)); finishAid(actor); finishFreightRepair(actor); return changed;
    }
    private void finishFreightRepair(UUID actor) {
        var current=current(actor);
        if(current.filter(v -> v.phase==Phase.RECONCILING && v.personality==1 && v.delivery>repairSequence(actor)
                && state.relations().hasReceipt(v.host,v.other,actor,v.delivery)).isPresent()) ready(actor,true);
    }
    private void finishAid(UUID actor) {
        var current=current(actor); if(current.isEmpty()) return; var v=current.get();
        if(v.phase==Phase.AID && v.heard==3 && v.delivery>0 && state.relations().aid(v.host,v.other,actor,v.delivery))
            change(actor,t -> t.putString("phase",Phase.RESOLVED.name()));
    }
    /** Trusted adapter supplies fresh condition/route/meeting evidence for this person's chosen repair. */
    boolean ready(UUID actor,boolean realTaskSatisfied) {
        var current=current(actor);
        return realTaskSatisfied && current.filter(v -> v.phase==Phase.RECONCILING && v.heard==3).isPresent()
                && change(actor,t -> t.putString("phase",Phase.READY.name()));
    }
    boolean settleNaturally(UUID actor) {
        return current(actor).filter(v -> v.phase==Phase.OPEN || v.phase==Phase.AID).isPresent()
                && change(actor,t -> t.putString("phase",Phase.RESOLVED.name()));
    }
    private boolean change(UUID actor,java.util.function.Consumer<CompoundTag> edit) {
        var current=current(actor); if(current.isEmpty() || current.get().revision==Long.MAX_VALUE) return false;
        var row=records.get(actor); var tag=((CompoundTag)row.get("current")).copy(); edit.accept(tag);
        tag.putLong("revision",current.get().revision+1); row.put("current",tag); state.setDirty(); return true;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("actors") instanceof ListTag rows) || rows.size()>16384) { opaque=true; return; }
        try {
            for(var value:rows) {
                if(!(value instanceof CompoundTag row) || !(row.get("activity") instanceof LongTag) || !(row.get("lastCase") instanceof LongTag)
                        || !(row.get("seen") instanceof CompoundTag seen) || seen.size()>1024 || !(row.get("current") instanceof CompoundTag tag)) throw new IllegalArgumentException();
                var actor=UUID.fromString(row.getStringOr("actor","")); var v=view(tag);
                for(String field:List.of("cycle","personality","remaining","heard","delivery")) if(!(tag.get(field) instanceof IntTag)) throw new IllegalArgumentException();
                if(!(tag.get("revision") instanceof LongTag) || !(tag.get("competing") instanceof ByteTag) || actor.equals(BuildingSessionPolicy.NONE)
                        || v.id.equals(BuildingSessionPolicy.NONE) || v.host.equals(v.other) || state.village(v.host).isEmpty() || state.village(v.other).isEmpty()
                        || v.revision<0 || v.cycle<0 || v.personality<0 || v.personality>3 || v.remaining<0 || v.remaining>48000
                        || v.heard<0 || v.heard>3 || v.delivery<0 || row.getLongOr("activity",-1)<0 || row.getLongOr("lastCase",-1)<0
                        || row.getLongOr("lastCase",0)>row.getLongOr("activity",0)) throw new IllegalArgumentException();
                if(tag.contains("kind") && !(tag.get("kind") instanceof StringTag) || v.kind==Kind.LOYALTY && !v.competing) throw new IllegalArgumentException();
                if(row.contains("lastLoyalty") && (!(row.get("lastLoyalty") instanceof LongTag) || row.getLongOr("lastLoyalty",-1)<0
                        || row.getLongOr("lastLoyalty",0)>row.getLongOr("activity",0))) throw new IllegalArgumentException();
                if(tag.contains("meetingRevision") && (!(tag.get("meetingRevision") instanceof LongTag) || tag.getLongOr("meetingRevision",-2)<-1)) throw new IllegalArgumentException();
                if((v.phase==Phase.RECONCILING || v.phase==Phase.READY) && (!tag.contains("meetingRevision") || !(tag.get("repairSequence") instanceof IntTag))
                        || tag.contains("repairSequence") && (!(tag.get("repairSequence") instanceof IntTag) || tag.getIntOr("repairSequence",-1)<0)
                        || v.delivery>0 && !state.relations().hasReceipt(v.host,v.other,actor,v.delivery)) throw new IllegalArgumentException();
                for(String key:seen.keySet()) {
                    if(!key.matches("[0-9a-fA-F-]{36}:[0-9a-fA-F-]{36}") || !(seen.get(key) instanceof IntTag) || seen.getIntOr(key,-1)<0) throw new IllegalArgumentException();
                    String[] pair=key.split(":"); UUID.fromString(pair[0]); UUID.fromString(pair[1]);
                }
                if(seen.getIntOr(v.host+":"+v.other,-1)<v.cycle || records.putIfAbsent(actor,row.copy())!=null) throw new IllegalArgumentException();
            }
        } catch(RuntimeException malformed) { opaque=true; records.clear(); }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); records.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var tag=entry.getValue().copy(); tag.putString("actor",entry.getKey().toString()); rows.add(tag);
        }); root.put("actors",rows); return root;
    }
}
