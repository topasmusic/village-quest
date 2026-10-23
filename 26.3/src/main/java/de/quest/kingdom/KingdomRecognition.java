package de.quest.kingdom;

import java.util.*;
import net.minecraft.nbt.*;

/** Shared earned recognition, independent of population, personal prestige and current building validity. */
public final class KingdomRecognition {
    public enum Evidence { COUNCIL, INN, MARKET, STOREHOUSE, VOICES, TRADE, FREIGHT, TWO_STANDS,
        THREE_STANDS, FOUR_STANDS, FIVE_STANDS, ACTIVE_VISIT, ROUTE_AGREEMENT, MEETING, AID, TWO_PAIRS, WORKSITE, POPULATION }
    private final LivingKingdomsState state;
    private final Map<UUID,CompoundTag> records=new HashMap<>();
    private Tag retained=new CompoundTag();
    private boolean opaque;
    KingdomRecognition(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly(); }
    public int level(UUID village) { return readOnly() || state.village(village).isEmpty()?0:records.getOrDefault(village,new CompoundTag()).getIntOr("level",0); }
    public long revision(UUID village) { return readOnly()?0:records.getOrDefault(village,new CompoundTag()).getLongOr("revision",0); }
    public int capacity(UUID village) { return Math.max(1,level(village)); }
    public static Set<Evidence> requirements(int project) {
        return switch(project) {
            case 0 -> Set.of(Evidence.POPULATION,Evidence.COUNCIL,Evidence.INN,Evidence.MARKET,Evidence.STOREHOUSE,Evidence.VOICES,Evidence.TRADE,Evidence.FREIGHT);
            case 1 -> Set.of(Evidence.COUNCIL,Evidence.INN,Evidence.MARKET,Evidence.STOREHOUSE,Evidence.TRADE,Evidence.FREIGHT,Evidence.TWO_STANDS,Evidence.ACTIVE_VISIT);
            case 2 -> Set.of(Evidence.COUNCIL,Evidence.THREE_STANDS,Evidence.ROUTE_AGREEMENT,Evidence.MEETING);
            case 3 -> Set.of(Evidence.COUNCIL,Evidence.FOUR_STANDS,Evidence.AID);
            case 4 -> Set.of(Evidence.COUNCIL,Evidence.INN,Evidence.MARKET,Evidence.STOREHOUSE,Evidence.FIVE_STANDS,Evidence.TWO_PAIRS,Evidence.WORKSITE);
            default -> Set.of();
        };
    }
    /** Package-only trusted service facts, never accepted from a client action or an item tooltip. */
    boolean complete(UUID actor,UUID village,long villageRevision,long expectedRevision,int project,Set<Evidence> facts) {
        if(readOnly() || project<0 || project>=5 || facts==null || !state.canManageVillage(actor,village)
                || state.village(village).filter(v -> v.revision()==villageRevision).isEmpty()
                || level(village)!=project || revision(village)!=expectedRevision || expectedRevision==Long.MAX_VALUE
                || !facts.containsAll(requirements(project)) || records.size()>=16384 && !records.containsKey(village)) return false;
        var tag=records.getOrDefault(village,new CompoundTag()).copy(); tag.putString("village",village.toString());
        tag.putInt("level",project+1); tag.putLong("revision",expectedRevision+1); tag.putString("actor"+project,actor.toString());
        records.put(village,tag); state.setDirty(); return true;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("villages") instanceof ListTag rows) || rows.size()>16384) { opaque=true; return; }
        try {
            for(var value:rows) {
                if(!(value instanceof CompoundTag tag) || !(tag.get("level") instanceof IntTag) || !(tag.get("revision") instanceof LongTag)) throw new IllegalArgumentException();
                var id=UUID.fromString(tag.getStringOr("village","")); int level=tag.getIntOr("level",0); long revision=tag.getLongOr("revision",-1);
                if(state.village(id).isEmpty() || level<1 || level>5 || revision<level || records.containsKey(id)) throw new IllegalArgumentException();
                for(int i=0;i<level;i++) if(UUID.fromString(tag.getStringOr("actor"+i,"")).equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException();
                records.put(id,tag.copy());
            }
        } catch(RuntimeException malformed) { records.clear(); opaque=true; }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag();
        root.putInt("schema",1); var rows=new ListTag(); records.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> rows.add(e.getValue().copy()));
        root.put("villages",rows); return root;
    }
}
