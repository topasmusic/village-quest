package de.quest.kingdom;

import java.util.*;
import net.minecraft.nbt.*;

/** Shared pair memory. Freight sequences come only from committed existing guild deliveries. */
public final class KingdomRelations {
    public record View(UUID left,UUID right,long revision,int deliveries,String cause,
                       boolean aidRemembered,boolean loyaltyBroken,boolean loyaltyRepaired,boolean reconciliationRemembered) {}
    /** Server callers must use the recipient's authenticated actor, never a client-supplied UUID. */
    public record Personal(boolean available,boolean distrust,String repairNeed) {}
    private final LivingKingdomsState state;
    private final Map<String,CompoundTag> pairs=new HashMap<>();
    private final Map<String,CompoundTag> attacks=new HashMap<>();
    private Tag retained=new CompoundTag(); private boolean opaque;
    KingdomRelations(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly(); }
    public Personal personal(UUID actor,UUID village) {
        if(readOnly() || actor==null || village==null || state.village(village).isEmpty()) return new Personal(false,false,"none");
        boolean distrust=distrusts(actor,village);
        return new Personal(true,distrust,distrust?attackRepairNeed(actor,village).map(de.quest.village.VillageNeed::key).orElse("none"):"none");
    }
    public boolean distrusts(UUID actor,UUID village) {
        var row=actor==null || village==null?null:attacks.get(actor+":"+village);
        return !readOnly() && row!=null && row.getIntOr("refusals",0)>=3;
    }
    boolean attackSeen(UUID actor,UUID village,UUID incident) {
        var row=actor==null || village==null?null:attacks.get(actor+":"+village);
        return readOnly() || row!=null && row.get("seen") instanceof CompoundTag seen && seen.contains(incident.toString());
    }
    boolean refusedAttack(UUID actor,UUID village,UUID incident) { return attack(actor,village,incident,false); }
    boolean helpedAttack(UUID actor,UUID village,UUID incident) { return attack(actor,village,incident,true); }
    private int latestVillageReceipt(UUID actor,UUID village) {
        return pairs.values().stream().filter(t -> village.toString().equals(t.getStringOr("left","")) || village.toString().equals(t.getStringOr("right","")))
                .mapToInt(t -> t.get("receipts") instanceof CompoundTag receipts?receipts.getIntOr(actor.toString(),0):0).max().orElse(0);
    }
    boolean apologizeAttack(UUID actor,UUID village,de.quest.village.VillageNeed need) {
        if(readOnly() || !distrusts(actor,village) || need==null) return false;
        var row=attacks.get(actor+":"+village);
        if(row.contains("repairNeed")) return true;
        row.putString("repairNeed",need.name()); row.putInt("repairBaseline",latestVillageReceipt(actor,village)); state.setDirty(); return true;
    }
    public List<de.quest.village.VillageNeed> attackRepairNeeds(UUID actor) {
        if(readOnly() || actor==null) return List.of();
        return attacks.values().stream().filter(r -> actor.toString().equals(r.getStringOr("actor","")) && r.getIntOr("refusals",0)==3 && r.contains("repairNeed"))
                .sorted(Comparator.comparing(r -> r.getStringOr("village",""))).map(r -> de.quest.village.VillageNeed.valueOf(r.getStringOr("repairNeed",""))).distinct().limit(3).toList();
    }
    java.util.Optional<de.quest.village.VillageNeed> attackRepairNeed(UUID actor,UUID village) {
        var row=actor==null || village==null?null:attacks.get(actor+":"+village);
        return readOnly() || row==null || !row.contains("repairNeed")?Optional.empty():Optional.of(de.quest.village.VillageNeed.valueOf(row.getStringOr("repairNeed","")));
    }
    boolean repairAttackFreight(UUID actor,UUID home,UUID destination,int sequence,net.minecraft.world.item.Item item) {
        if(readOnly() || !distrusts(actor,destination) || item==null || !hasReceipt(home,destination,actor,sequence)) return false;
        var row=attacks.get(actor+":"+destination);
        if(!row.contains("repairNeed") || sequence<=row.getIntOr("repairBaseline",Integer.MAX_VALUE)
                || !de.quest.village.VillageNeed.valueOf(row.getStringOr("repairNeed","")).matches(item)) return false;
        row.putInt("refusals",0); row.putInt("repairDone",sequence); row.putString("repairHome",home.toString()); state.setDirty(); return true;
    }
    /** Only the native fresh-warning decision / successful existing-ambush adapter calls this. No money or arrivals. */
    private boolean attack(UUID actor,UUID village,UUID incident,boolean help) {
        if(readOnly() || actor==null || village==null || incident==null || actor.equals(BuildingSessionPolicy.NONE)
                || incident.equals(BuildingSessionPolicy.NONE) || state.village(village).isEmpty()) return false;
        String key=actor+":"+village; var previous=attacks.get(key);
        if(previous==null && attacks.size()>=16384) return false;
        var row=previous==null?new CompoundTag():previous.copy();
        var seen=row.get("seen") instanceof CompoundTag stored?stored.copy():new CompoundTag();
        if(seen.contains(incident.toString()) || seen.size()>=1024) return false;
        seen.putBoolean(incident.toString(),help); row.put("seen",seen); row.putString("actor",actor.toString()); row.putString("village",village.toString());
        for(String field:List.of("repairNeed","repairBaseline","repairDone","repairHome")) row.remove(field);
        row.putInt("refusals",help?0:Math.min(3,row.getIntOr("refusals",0)+1));
        row.putString("last",incident.toString()); row.putBoolean("helped",help); attacks.put(key,row); state.setDirty(); return true;
    }
    boolean hasReceipt(UUID a,UUID b,UUID actor,int sequence) {
        if(readOnly() || a==null || b==null || actor==null || sequence<=0) return false;
        var tag=pairs.get(key(a,b));
        return tag!=null && tag.get("receipts") instanceof CompoundTag receipts && receipts.getIntOr(actor.toString(),0)>=sequence;
    }
    int latestReceipt(UUID a,UUID b,UUID actor) {
        if(readOnly() || a==null || b==null || actor==null) return 0;
        var tag=pairs.get(key(a,b));
        return tag!=null && tag.get("receipts") instanceof CompoundTag receipts?receipts.getIntOr(actor.toString(),0):0;
    }
    boolean aid(UUID a,UUID b,UUID actor,int sequence) {
        if(!hasReceipt(a,b,actor,sequence)) return false; var tag=pairs.get(key(a,b));
        if(tag.getBooleanOr("aid",false)) return true;
        if(tag.getLongOr("revision",0)==Long.MAX_VALUE) return false;
        tag.putBoolean("aid",true); tag.putString("cause","aid"); tag.putLong("revision",tag.getLongOr("revision",0)+1); state.setDirty(); return true;
    }
    public boolean hasAid(UUID village) {
        return !readOnly() && village!=null && pairs.values().stream().anyMatch(tag -> tag.getBooleanOr("aid",false)
                && (village.toString().equals(tag.getStringOr("left","")) || village.toString().equals(tag.getStringOr("right",""))));
    }
    private static String key(UUID a,UUID b) { return a.compareTo(b)<0?a+":"+b:b+":"+a; }
    boolean canRemember(UUID a,UUID b) {
        if(readOnly() || a==null || b==null || a.equals(b) || state.village(a).isEmpty() || state.village(b).isEmpty()) return false;
        var old=pairs.get(key(a,b)); return old==null?pairs.size()<32768:old.getLongOr("revision",0)<Long.MAX_VALUE;
    }
    boolean remember(UUID a,UUID b,UUID event,String cause) {
        if(!canRemember(a,b) || event==null || event.equals(BuildingSessionPolicy.NONE) || !Set.of("loyalty","reconciliation").contains(cause)) return false;
        var tag=pairs.getOrDefault(key(a,b),new CompoundTag()).copy();
        tag.putString("left",(a.compareTo(b)<0?a:b).toString()); tag.putString("right",(a.compareTo(b)<0?b:a).toString());
        if(!tag.contains("receipts")) tag.put("receipts",new CompoundTag());
        if(!tag.contains("deliveries")) tag.putInt("deliveries",0);
        tag.putString(cause+"Case",event.toString()); tag.putString("cause",cause); tag.putLong("revision",tag.getLongOr("revision",0)+1);
        pairs.put(key(a,b),tag); state.setDirty(); return true;
    }
    public Optional<View> between(UUID a,UUID b) {
        if(readOnly() || a==null || b==null) return Optional.empty();
        var tag=pairs.get(key(a,b));
        return tag==null?Optional.empty():Optional.of(new View(UUID.fromString(tag.getStringOr("left","")),
                UUID.fromString(tag.getStringOr("right","")),tag.getLongOr("revision",0),tag.getIntOr("deliveries",0),tag.getStringOr("cause","delivery"),
                tag.getBooleanOr("aid",false),tag.contains("loyaltyCase"),tag.contains("loyaltyCase")
                    && tag.getStringOr("loyaltyCase","").equals(tag.getStringOr("reconciliationCase","")),tag.contains("reconciliationCase")));
    }
    boolean delivered(UUID a,UUID b,UUID owner,UUID connection,int sequence) {
        if(readOnly() || a==null || b==null || a.equals(b) || owner==null || owner.equals(BuildingSessionPolicy.NONE)
                || connection==null || connection.equals(BuildingSessionPolicy.NONE) || sequence<=0
                || state.village(a).isEmpty() || state.village(b).isEmpty()) return false;
        String key=key(a,b); var old=pairs.get(key);
        if(old==null && pairs.size()>=32768) return false;
        var tag=old==null?new CompoundTag():old.copy();
        var receipts=tag.get("receipts") instanceof CompoundTag saved?saved.copy():new CompoundTag();
        String source=owner.toString();
        if(sequence<=receipts.getIntOr(source,0) || !receipts.contains(source) && receipts.size()>=1024
                || tag.getLongOr("revision",0)==Long.MAX_VALUE) return false;
        receipts.putInt(source,sequence); tag.put("receipts",receipts);
        tag.putString("left",(a.compareTo(b)<0?a:b).toString()); tag.putString("right",(a.compareTo(b)<0?b:a).toString());
        tag.putLong("revision",tag.getLongOr("revision",0)+1); tag.putInt("deliveries",Math.min(1000000,tag.getIntOr("deliveries",0)+1));
        tag.putString("cause","delivery"); tag.putString("connection",connection.toString());
        pairs.put(key,tag); state.setDirty(); return true;
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("pairs") instanceof ListTag rows) || rows.size()>32768) { opaque=true; return; }
        try {
            if(root.contains("attacks")) {
                if(!(root.get("attacks") instanceof ListTag history) || history.size()>16384) throw new IllegalArgumentException();
                for(var value:history) {
                    if(!(value instanceof CompoundTag row) || !(row.get("refusals") instanceof IntTag) || !(row.get("helped") instanceof ByteTag)
                            || !(row.get("seen") instanceof CompoundTag seen) || seen.isEmpty() || seen.size()>1024) throw new IllegalArgumentException();
                    var actor=UUID.fromString(row.getStringOr("actor","")); var village=UUID.fromString(row.getStringOr("village",""));
                    var last=UUID.fromString(row.getStringOr("last",""));
                    if(actor.equals(BuildingSessionPolicy.NONE) || state.village(village).isEmpty() || row.getIntOr("refusals",-1)<0 || row.getIntOr("refusals",-1)>3
                            || !seen.contains(last.toString()) || row.getBooleanOr("helped",false) && row.getIntOr("refusals",0)!=0
                            || seen.keySet().stream().filter(id -> !seen.getBooleanOr(id,false)).count()<row.getIntOr("refusals",0)) throw new IllegalArgumentException();
                    for(String id:seen.keySet()) if(UUID.fromString(id).equals(BuildingSessionPolicy.NONE) || !(seen.get(id) instanceof ByteTag)) throw new IllegalArgumentException();
                    if(row.contains("repairNeed")!=row.contains("repairBaseline") || row.contains("repairDone")!=row.contains("repairHome")) throw new IllegalArgumentException();
                    if(row.contains("repairNeed")) {
                        de.quest.village.VillageNeed.valueOf(row.getStringOr("repairNeed",""));
                        if(!(row.get("repairNeed") instanceof StringTag) || !(row.get("repairBaseline") instanceof IntTag)
                                || row.getIntOr("repairBaseline",-1)<0 || row.getBooleanOr("helped",false)
                                || !row.contains("repairDone") && row.getIntOr("refusals",0)!=3) throw new IllegalArgumentException();
                    }
                    if(row.contains("repairDone") && (!(row.get("repairDone") instanceof IntTag) || !row.contains("repairNeed")
                            || row.getIntOr("refusals",-1)!=0 || row.getIntOr("repairDone",0)<=row.getIntOr("repairBaseline",0))) throw new IllegalArgumentException();
                    if(seen.getBooleanOr(last.toString(),false)!=row.getBooleanOr("helped",false) || attacks.putIfAbsent(actor+":"+village,row.copy())!=null) throw new IllegalArgumentException();
                }
            }
            for(var row:rows) {
                if(!(row instanceof CompoundTag tag) || !(tag.get("revision") instanceof LongTag) || !(tag.get("deliveries") instanceof IntTag)
                        || !(tag.get("receipts") instanceof CompoundTag receipts) || receipts.size()>1024) throw new IllegalArgumentException();
                var a=UUID.fromString(tag.getStringOr("left","")); var b=UUID.fromString(tag.getStringOr("right",""));
                if(tag.contains("connection") && UUID.fromString(tag.getStringOr("connection","")).equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException();
                int deliveries=tag.getIntOr("deliveries",-1); String cause=tag.getStringOr("cause","");
                if(a.compareTo(b)>=0 || state.village(a).isEmpty() || state.village(b).isEmpty()
                        || tag.getLongOr("revision",-1)<1 || deliveries<0 || deliveries>1000000
                        || deliveries>0 && (!tag.contains("connection") || receipts.isEmpty()) || deliveries==0 && !receipts.isEmpty()
                        || !Set.of("delivery","aid","loyalty","reconciliation").contains(cause)
                        || Set.of("delivery","aid").contains(cause) && deliveries==0
                        || Set.of("loyalty","reconciliation").contains(cause) && !tag.contains(cause+"Case")
                        || tag.contains("aid") && !(tag.get("aid") instanceof ByteTag)
                        || tag.getStringOr("cause","").equals("aid") && !tag.getBooleanOr("aid",false)) throw new IllegalArgumentException();
                for(String source:receipts.keySet()) if(UUID.fromString(source).equals(BuildingSessionPolicy.NONE)
                        || !(receipts.get(source) instanceof IntTag) || receipts.getIntOr(source,0)<=0) throw new IllegalArgumentException();
                for(String field:List.of("loyaltyCase","reconciliationCase")) if(tag.contains(field)
                        && (!(tag.get(field) instanceof StringTag) || UUID.fromString(tag.getStringOr(field,"")).equals(BuildingSessionPolicy.NONE))) throw new IllegalArgumentException();
                if(pairs.putIfAbsent(key(a,b),tag.copy())!=null) throw new IllegalArgumentException();
            }
            for(var row:attacks.values()) if(row.contains("repairDone") && !hasReceipt(UUID.fromString(row.getStringOr("repairHome","")),
                    UUID.fromString(row.getStringOr("village","")),UUID.fromString(row.getStringOr("actor","")),row.getIntOr("repairDone",0))) throw new IllegalArgumentException();
        } catch(RuntimeException malformed) { opaque=true; pairs.clear(); attacks.clear(); }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); pairs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> rows.add(e.getValue().copy()));
        root.put("pairs",rows);
        var history=new ListTag(); attacks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> history.add(e.getValue().copy()));
        root.put("attacks",history); return root;
    }
}
