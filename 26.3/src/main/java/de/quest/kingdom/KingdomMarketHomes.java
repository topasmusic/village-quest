package de.quest.kingdom;

import de.quest.shrine.VillageBondType;
import java.util.*;
import net.minecraft.nbt.*;

/** Shared immutable specialization/source identity; personal discovery differences cannot reroll traders. */
public final class KingdomMarketHomes {
    public record Profile(UUID home,UUID owner,int index,VillageBondType type) {}
    private final LivingKingdomsState state;
    private final Map<UUID,Profile> profiles=new HashMap<>();
    private final Map<UUID,CompoundTag> payloads=new HashMap<>();
    private Tag retained=new CompoundTag(); private boolean opaque;
    KingdomMarketHomes(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly(); }
    public Optional<Profile> profile(UUID home) { return readOnly()?Optional.empty():Optional.ofNullable(profiles.get(home)); }
    /** Only the validated canonical contact adapter may propose a source. Binding is once-only. */
    boolean bind(Profile profile) {
        if(readOnly() || !valid(profile) || state.village(profile.home).isEmpty()) return false;
        var old=profiles.get(profile.home); if(old!=null) return old.equals(profile);
        if(profiles.size()>=16384) return false;
        profiles.put(profile.home,profile); state.setDirty(); return true;
    }
    private static boolean valid(Profile p) {
        return p!=null && p.home!=null && p.owner!=null && !p.home.equals(BuildingSessionPolicy.NONE)
                && !p.owner.equals(BuildingSessionPolicy.NONE) && p.index>=0 && p.index<1024 && p.type!=null;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("profiles") instanceof ListTag rows) || rows.size()>16384) { opaque=true; return; }
        try {
            for(var value:rows) {
                if(!(value instanceof CompoundTag tag) || !(tag.get("index") instanceof IntTag) || !(tag.get("type") instanceof StringTag)) throw new IllegalArgumentException();
                var profile=new Profile(UUID.fromString(tag.getStringOr("home","")),UUID.fromString(tag.getStringOr("owner","")),
                        tag.getIntOr("index",-1),VillageBondType.byName(tag.getStringOr("type","")));
                if(!valid(profile) || !profile.type.key().equals(tag.getStringOr("type","")) || state.village(profile.home).isEmpty()
                        || profiles.putIfAbsent(profile.home,profile)!=null) throw new IllegalArgumentException();
                payloads.put(profile.home,tag.copy());
            }
        } catch(RuntimeException malformed) { profiles.clear(); payloads.clear(); opaque=true; }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); profiles.values().stream().sorted(Comparator.comparing(Profile::home)).forEach(profile -> {
            var tag=payloads.getOrDefault(profile.home,new CompoundTag()).copy(); tag.putString("home",profile.home.toString());
            tag.putString("owner",profile.owner.toString()); tag.putInt("index",profile.index); tag.putString("type",profile.type.key()); rows.add(tag);
        }); root.put("profiles",rows); return root;
    }
}
