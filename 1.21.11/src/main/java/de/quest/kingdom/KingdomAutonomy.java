package de.quest.kingdom;

import java.util.*;
import net.minecraft.nbt.*;

/** Observed direct contact and committed pair receipts; no parallel freight or political consent. */
public final class KingdomAutonomy {
    public record View(int habit,KingdomDiplomacy.Type request) {}
    private static final long DAY=24000;
    private final LivingKingdomsState state;
    private final Map<String,CompoundTag> pairs=new HashMap<>();
    private final Map<String,Long> clocks=new HashMap<>();
    private Tag retained=new CompoundTag(); private boolean opaque;
    KingdomAutonomy(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly() || state.relations().readOnly() || state.diplomacy().readOnly(); }
    private static String key(UUID a,UUID b) { return a.compareTo(b)<0?a+":"+b:b+":"+a; }
    public Optional<View> between(UUID a,UUID b) {
        if(readOnly() || a==null || b==null || a.equals(b)) return Optional.empty();
        var row=pairs.get(key(a,b)); if(row==null) return Optional.empty();
        var request=row.contains("request")?KingdomDiplomacy.Type.valueOf(row.getStringOr("request","")):null;
        if(request!=null && state.diplomacy().between(a,b,request).filter(d -> d.status()==KingdomDiplomacy.Status.SIGNED).isPresent()) request=null;
        if(row.getIntOr("habit",0)==0 || strained(a,b) || row.getBooleanOr("requestSettled",false)) request=null;
        return Optional.of(new View(row.getIntOr("habit",0),request));
    }
    /** Native adapter proves current safe contact; true only when a new one-time mediation request was created. */
    boolean observe(UUID a,UUID b,long now,boolean connected,int leftPersonality,int rightPersonality,boolean shortage) {
        return observe(a,b,now,connected,leftPersonality,rightPersonality,shortage,40,20);
    }
    /** Bounded saved-route scheduler; no credit across restart or a missing minute of observations. */
    boolean observeRemote(UUID a,UUID b,long now,boolean connected,int leftPersonality,int rightPersonality,boolean shortage) {
        return observe(a,b,now,connected,leftPersonality,rightPersonality,shortage,1200,1200);
    }
    private boolean observe(UUID a,UUID b,long now,boolean connected,int leftPersonality,int rightPersonality,boolean shortage,long maxGap,long maxCredit) {
        if(readOnly() || a==null || b==null || a.equals(b) || now<0 || state.village(a).isEmpty() || state.village(b).isEmpty()
                || leftPersonality<0 || leftPersonality>3 || rightPersonality<0 || rightPersonality>3) return false;
        String key=key(a,b);
        if(!connected) { clocks.remove(key); return false; }
        if(!pairs.containsKey(key) && pairs.size()>=32768) return false;
        var row=pairs.computeIfAbsent(key,k -> {
            var r=new CompoundTag(); r.putString("left",(a.compareTo(b)<0?a:b).toString()); r.putString("right",(a.compareTo(b)<0?b:a).toString());
            r.putLong("ticks",0); r.putInt("habit",0); r.putInt("baseline",0); return r;
        });
        var previous=clocks.get(key);
        if(previous!=null && now<=previous) return false;
        clocks.put(key,now);
        if(previous==null || now<=previous || now-previous>maxGap) return false;
        if(row.getStringOr("request","").equals("SUPPLY") && !shortage && !row.getBooleanOr("requestSettled",false)) {
            row.putBoolean("requestSettled",true); state.setDirty();
        }
        long ticks=row.getLongOr("ticks",0)+Math.min(maxCredit,now-previous);
        if(ticks<DAY) { row.putLong("ticks",ticks); state.setDirty(); return false; }
        row.putLong("ticks",ticks-DAY);
        int deliveries=state.relations().between(a,b).map(KingdomRelations.View::deliveries).orElse(0);
        int habit=Math.max(0,Math.min(3,row.getIntOr("habit",0)+(deliveries>row.getIntOr("baseline",0)?1:-1)));
        row.putInt("habit",habit); row.putInt("baseline",deliveries); state.setDirty();
        if(strained(a,b)) return false;
        if(deliveries>=3 && habit>=(leftPersonality==2 || rightPersonality==2?3:2))
            state.diplomacy().autonomousTrade(a,b,leftPersonality==2 || rightPersonality==2?KingdomDiplomacy.Terms.CAUTIOUS:KingdomDiplomacy.Terms.BALANCED);
        if(habit==0 || row.contains("request")) return false;
        var request=shortage?KingdomDiplomacy.Type.SUPPLY:KingdomDiplomacy.Type.FRIENDSHIP;
        if(state.diplomacy().between(a,b,request).isPresent()) return false;
        row.putString("request",request.name()); return true;
    }
    private boolean strained(UUID a,UUID b) {
        return state.relations().between(a,b).filter(v -> v.loyaltyBroken() && !v.loyaltyRepaired()).isPresent()
                || state.diplomacy().contracts(a).stream().anyMatch(d -> (d.left().equals(b) || d.right().equals(b)) && d.status()==KingdomDiplomacy.Status.PAUSED);
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy(); clocks.clear();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("pairs") instanceof ListTag rows) || rows.size()>32768) { opaque=true; return; }
        try {
            for(var value:rows) {
                if(!(value instanceof CompoundTag row) || !(row.get("ticks") instanceof LongTag) || !(row.get("habit") instanceof IntTag)
                        || !(row.get("baseline") instanceof IntTag) || row.getLongOr("ticks",-1)<0 || row.getLongOr("ticks",DAY)>=DAY
                        || row.getIntOr("habit",-1)<0 || row.getIntOr("habit",4)>3 || row.getIntOr("baseline",-1)<0) throw new IllegalArgumentException();
                var a=UUID.fromString(row.getStringOr("left","")); var b=UUID.fromString(row.getStringOr("right",""));
                int receipts=state.relations().between(a,b).map(KingdomRelations.View::deliveries).orElse(0);
                if(a.compareTo(b)>=0 || state.village(a).isEmpty() || state.village(b).isEmpty() || row.getIntOr("baseline",0)>receipts) throw new IllegalArgumentException();
                if(row.contains("request") && (!(row.get("request") instanceof StringTag)
                        || KingdomDiplomacy.Type.valueOf(row.getStringOr("request",""))==KingdomDiplomacy.Type.TRADE)) throw new IllegalArgumentException();
                if(row.contains("requestSettled") && (!(row.get("requestSettled") instanceof ByteTag) || !row.getStringOr("request","").equals("SUPPLY"))) throw new IllegalArgumentException();
                if(pairs.putIfAbsent(key(a,b),row.copy())!=null) throw new IllegalArgumentException();
            }
        } catch(RuntimeException bad) { pairs.clear(); opaque=true; }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag r?r.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); pairs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> rows.add(e.getValue().copy()));
        root.put("pairs",rows); return root;
    }
}
