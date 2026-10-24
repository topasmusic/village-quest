package de.quest.kingdom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/** Persisted exclusive HOME leases for our own people. Native adapters alone acquire/release Vanilla POI tickets. */
public final class KingdomBedRegistry {
    private static final int MAX_LEASES=16_384;
    private final LivingKingdomsState state;
    private final KingdomPersonRegistry people;
    private final Map<UUID,Lease> leases=new HashMap<>();
    private final Map<UUID,UUID> owners=new HashMap<>();
    private final Map<Place,UUID> places=new HashMap<>();
    private final java.util.LinkedHashSet<UUID> releasingIds=new java.util.LinkedHashSet<>();
    private Tag retained=new CompoundTag();
    private boolean readOnly;
    KingdomBedRegistry(LivingKingdomsState state,KingdomPersonRegistry people) { this.state=state; this.people=people; }
    public enum Permission { AVAILABLE,RESERVED,UNKNOWN }
    public record LeaseView(UUID id,UUID owner,UUID body,long generation,UUID inn,String dimension,
                            BlockPos foot,Direction facing,Identifier block,boolean releasing) {
        public BlockPos head() { return foot.relative(facing); }
    }
    private record Place(String dimension,BlockPos pos) {}
    public boolean readOnly() { return readOnly || people.readOnly(); }
    public Optional<LeaseView> lease(UUID owner) {
        var lease=leases.get(owners.get(owner));
        return readOnly() || lease==null?Optional.empty():Optional.of(lease.view());
    }
    public Optional<UUID> owner(String dimension,BlockPos pos) {
        var lease=leases.get(places.get(new Place(dimension,pos)));
        return readOnly() || lease==null?Optional.empty():Optional.of(lease.owner);
    }
    public List<LeaseView> releasing(int limit) {
        if(readOnly() || limit<=0) return List.of();
        var result=new java.util.ArrayList<LeaseView>();
        for(int i=0,n=Math.min(Math.min(32,limit),releasingIds.size());i<n;i++) {
            UUID id=releasingIds.getFirst(); releasingIds.remove(id); releasingIds.add(id);
            var lease=leases.get(id); if(lease!=null) result.add(lease.view());
        }
        return List.copyOf(result);
    }
    /** An innkeeper's own held bed still fulfils this inn's BED requirement while sleeping or unloaded. */
    public Permission permission(UUID inn,String dimension,BlockPos pos) {
        if(readOnly()) return Permission.UNKNOWN;
        var lease=leases.get(places.get(new Place(dimension,pos))); if(lease==null) return Permission.AVAILABLE;
        var person=people.person(lease.owner);
        return !lease.releasing && lease.inn.equals(inn) && person.filter(view -> view.role()==KingdomPersonRegistry.Role.INNKEEPER).isPresent()
                ?Permission.AVAILABLE:Permission.RESERVED;
    }
    /** Called only after the loaded native adapter successfully acquires the exact free HOME POI. */
    public Optional<LeaseView> claim(KingdomPersonRegistry.Ticket ticket,UUID inn,BlockPos foot,Direction facing,Identifier block,long now) {
        if(readOnly() || ticket==null || inn==null || !validPosition(foot) || facing==null || !facing.getAxis().isHorizontal() || block==null
                || !people.available(ticket.person(),ticket.generation(),ticket.body()) || state.founding().readOnly()) return Optional.empty();
        var person=people.person(ticket.person()).orElseThrow();
        if(person.role()==KingdomPersonRegistry.Role.STOREKEEPER || !inn.equals(person.village()) && !inn.equals(person.visit())) return Optional.empty();
        var previous=lease(ticket.person());
        if(previous.isPresent()) {
            var existing=previous.get();
            return !existing.releasing() && existing.body().equals(ticket.body()) && existing.generation()==ticket.generation()
                    && existing.inn().equals(inn) && existing.foot().equals(foot) && existing.facing()==facing && existing.block().equals(block)?previous:Optional.empty();
        }
        var anchor=state.anchors().official(inn,BuildingScanner.Role.INN);
        if(anchor.isEmpty() || !state.anchors().maySpawn(anchor.get().id(),now)
                || state.anchors().report(anchor.get().id()).filter(report -> report.stations().getOrDefault(BuildingScanner.Feature.BED,java.util.Set.of()).contains(foot)).isEmpty()) return Optional.empty();
        String dimension=anchor.get().dimension(); var head=foot.relative(facing);
        if(!validPosition(head) || leases.size()>=MAX_LEASES || places.containsKey(new Place(dimension,foot)) || places.containsKey(new Place(dimension,head))
                || state.founding().bedOwner(dimension,foot).isPresent() || state.founding().bedOwner(dimension,head).isPresent()) return Optional.empty();
        var lease=new Lease(UUID.randomUUID(),ticket.person(),ticket.body(),ticket.generation(),inn,dimension,foot,facing,block);
        leases.put(lease.id,lease); owners.put(lease.owner,lease.id); index(lease); state.setDirty(); return Optional.of(lease.view());
    }
    public boolean release(UUID leaseId) {
        var lease=leases.get(leaseId); if(readOnly() || lease==null) return false;
        if(!lease.releasing) { lease.releasing=true; releasingIds.add(lease.id); state.setDirty(); } return true;
    }
    void releasePerson(UUID owner) { lease(owner).ifPresent(lease -> release(lease.id())); }
    /** The native adapter must establish loaded cleanup or a foreign owner before calling this. */
    public boolean completeRelease(UUID leaseId) {
        var lease=leases.get(leaseId); if(readOnly() || lease==null || !lease.releasing) return false;
        leases.remove(leaseId); owners.remove(lease.owner,leaseId);
        releasingIds.remove(leaseId);
        places.remove(new Place(lease.dimension,lease.foot),leaseId); places.remove(new Place(lease.dimension,lease.foot.relative(lease.facing)),leaseId);
        state.setDirty(); return true;
    }
    private void index(Lease lease) {
        places.put(new Place(lease.dimension,lease.foot),lease.id); places.put(new Place(lease.dimension,lease.foot.relative(lease.facing)),lease.id);
    }
    private static boolean validPosition(BlockPos pos) {
        return pos!=null && Math.abs((long)pos.getX())<=30_000_000 && Math.abs((long)pos.getZ())<=30_000_000 && Math.abs((long)pos.getY())<=4096;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("records") instanceof ListTag records) || records.size()>MAX_LEASES) { readOnly=true; return; }
        try {
            for(var row:records) {
                if(!(row instanceof CompoundTag tag)) throw new IllegalArgumentException("Bad bed row");
                UUID id=uuid(tag,"id"),owner=uuid(tag,"owner"),body=uuid(tag,"body"),inn=uuid(tag,"inn");
                if(!(tag.get("generation") instanceof LongTag) || !(tag.get("x") instanceof IntTag) || !(tag.get("y") instanceof IntTag)
                        || !(tag.get("z") instanceof IntTag)) throw new IllegalArgumentException("Bad lease types");
                long generation=tag.getLongOr("generation",0); var facing=Direction.valueOf(tag.getStringOr("facing",""));
                var block=Identifier.tryParse(tag.getStringOr("block","")); String dimension=tag.getStringOr("dimension","");
                var foot=new BlockPos(tag.getIntOr("x",0),tag.getIntOr("y",0),tag.getIntOr("z",0));
                if(generation<=0 || block==null || !facing.getAxis().isHorizontal() || !validPosition(foot) || !validPosition(foot.relative(facing))
                        || state.village(inn).filter(v -> v.legacyKey().dimension().equals(dimension)).isEmpty() || people.person(owner).isEmpty()
                        || owners.containsKey(owner) || leases.containsKey(id) || places.containsKey(new Place(dimension,foot))
                        || places.containsKey(new Place(dimension,foot.relative(facing)))) throw new IllegalArgumentException("Invalid bed lease");
                var lease=new Lease(id,owner,body,generation,inn,dimension,foot,facing,block); lease.retained=tag.copy();
                lease.releasing=tag.getBooleanOr("releasing",false); leases.put(id,lease); owners.put(owner,id); index(lease);
                if(lease.releasing) releasingIds.add(id);
            }
        } catch(IllegalArgumentException bad) { readOnly=true; }
    }
    private static UUID uuid(CompoundTag tag,String key) {
        var id=UUID.fromString(tag.getStringOr(key,"")); if(id.equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException("Zero lease identity"); return id;
    }
    Tag toNbt() {
        if(readOnly) return retained.copy();
        var root=retained instanceof CompoundTag compound?compound.copy():new CompoundTag(); root.putInt("schema",1); var records=new ListTag();
        leases.values().stream().sorted(java.util.Comparator.comparing(lease -> lease.id)).forEach(lease -> {
            var tag=lease.retained.copy(); tag.putString("id",lease.id.toString()); tag.putString("owner",lease.owner.toString()); tag.putString("body",lease.body.toString());
            tag.putLong("generation",lease.generation); tag.putString("inn",lease.inn.toString()); tag.putString("dimension",lease.dimension);
            tag.putInt("x",lease.foot.getX()); tag.putInt("y",lease.foot.getY()); tag.putInt("z",lease.foot.getZ());
            tag.putString("facing",lease.facing.name()); tag.putString("block",lease.block.toString()); tag.putBoolean("releasing",lease.releasing); records.add(tag);
        }); root.put("records",records); return root;
    }
    private static final class Lease {
        final UUID id,owner,body,inn; final long generation; final String dimension; final BlockPos foot; final Direction facing; final Identifier block;
        boolean releasing; CompoundTag retained=new CompoundTag();
        Lease(UUID id,UUID owner,UUID body,long generation,UUID inn,String dimension,BlockPos foot,Direction facing,Identifier block) {
            this.id=id; this.owner=owner; this.body=body; this.generation=generation; this.inn=inn; this.dimension=dimension;
            this.foot=foot.immutable(); this.facing=facing; this.block=block;
        }
        LeaseView view() { return new LeaseView(id,owner,body,generation,inn,dimension,foot,facing,block,releasing); }
    }
}
