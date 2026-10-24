package de.quest.kingdom;

import java.util.*;
import net.minecraft.nbt.*;

/** One durable daily draw and one bounded loaded-time visit per village. No offline catch-up. */
public final class KingdomTavernGuests {
    private final LivingKingdomsState state;
    private final Map<UUID,Record> records=new HashMap<>();
    private Tag retained=new CompoundTag();
    private boolean opaque;
    private static final class Record {
        CompoundTag retained=new CompoundTag();
        long day=-1,nextClock,lastTick=-1,graceUntil=-1;
        int active=-1,last=-1,remaining,warmup;
        UUID inn; long warmupTick=-1;
    }
    KingdomTavernGuests(LivingKingdomsState state) { this.state=state; }
    public long visitToken(UUID village) { var r=records.get(village); return r==null?-1:r.day; }
    public boolean readOnly() { return opaque || state.readOnly() || state.people().readOnly(); }
    public int active(UUID village) { var r=records.get(village); return readOnly() || r==null?-1:r.active; }
    /** Only continuous loaded valid-inn observations count towards the two-day opening period. */
    void observeInn(UUID village,UUID inn,long now,boolean valid,boolean loaded) {
        if(readOnly() || inn==null || now<0 || state.village(village).isEmpty()) return;
        if(!records.containsKey(village) && (!valid || !loaded || records.size()>=16384)) return;
        var r=records.computeIfAbsent(village,id -> new Record());
        if(!inn.equals(r.inn)) { r.inn=inn; r.warmup=0; r.warmupTick=-1; state.setDirty(); }
        if(!loaded) { r.warmupTick=-1; return; }
        if(!valid) {
            if(r.warmup!=0) { r.warmup=0; state.setDirty(); }
            r.warmupTick=-1; return;
        }
        long delta=r.warmupTick<0 || now<r.warmupTick || now-r.warmupTick>100?0:now-r.warmupTick;
        r.warmupTick=now;
        if(delta>0 && r.warmup<48000) { r.warmup=Math.min(48000,r.warmup+(int)delta); state.setDirty(); }
    }
    boolean ready(UUID village,UUID inn) {
        var r=records.get(village); return !readOnly() && r!=null && inn!=null && inn.equals(r.inn) && r.warmup>=48000;
    }
    public boolean ready(UUID village) {
        return state.anchors().official(village,BuildingScanner.Role.INN).filter(a -> a.phase()==BuildingOperation.Phase.ACTIVE && ready(village,a.id())).isPresent();
    }
    boolean draw(UUID village,long clock,boolean eligible,java.util.random.RandomGenerator random) {
        if(readOnly() || !eligible || clock<0 || state.village(village).isEmpty()) return false;
        if(!records.containsKey(village) && records.size()>=16384) return false;
        var r=records.computeIfAbsent(village,id -> new Record()); long day=clock/24000;
        if(r.active>=0 || day<=r.day) return false;
        r.day=day; state.setDirty();
        if(clock<r.nextClock || random.nextInt(10)!=0) return false;
        int choice=random.nextInt(r.last<0?4:3); if(r.last>=0 && choice>=r.last) choice++;
        r.active=choice; r.remaining=7200+random.nextInt(4801); r.lastTick=-1; r.graceUntil=-1;
        state.setDirty(); return true;
    }
    void observe(UUID village,long now,boolean loaded) {
        var r=records.get(village); if(readOnly() || r==null || r.active<0) return;
        long delta=!loaded || r.lastTick<0 || now<r.lastTick || now-r.lastTick>100?0:now-r.lastTick;
        r.lastTick=loaded?now:-1;
        if(delta>0) { r.remaining=Math.max(0,r.remaining-(int)delta); state.setDirty(); }
    }
    boolean expired(UUID village,long now) {
        var r=records.get(village); return r!=null && r.active>=0 && r.remaining==0 && now>=r.graceUntil;
    }
    void dialogue(UUID village,long now) {
        var r=records.get(village); if(!readOnly() && r!=null && r.active>=0 && r.remaining>0) r.graceUntil=now+100;
    }
    void end(UUID village,long clock) {
        var r=records.get(village); if(readOnly() || r==null || r.active<0 || clock<0) return;
        r.last=r.active; r.active=-1; r.remaining=0; r.lastTick=-1; r.graceUntil=-1;
        r.nextClock=Math.max(r.nextClock,clock>Long.MAX_VALUE-48000?Long.MAX_VALUE:clock+48000);
        state.setDirty();
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || root.getIntOr("schema",0)!=1 || !(root.get("records") instanceof ListTag rows) || rows.size()>16384) { opaque=true; return; }
        for(var value:rows) try {
            if(!(value instanceof CompoundTag t)) throw new IllegalArgumentException();
            UUID village=UUID.fromString(t.getStringOr("village","")); var r=new Record(); r.retained=t.copy();
            r.day=t.getLongOr("day",-2); r.nextClock=t.getLongOr("nextClock",-1); r.active=t.getIntOr("active",-2);
            r.last=t.getIntOr("last",-2); r.remaining=t.getIntOr("remaining",-1);
            r.inn=t.contains("inn")?UUID.fromString(t.getStringOr("inn","")):null; r.warmup=t.getIntOr("warmup",0);
            if(records.containsKey(village) || state.village(village).isEmpty() || r.day< -1 || r.nextClock<0
                    || r.active< -1 || r.active>3 || r.last< -1 || r.last>3 || r.remaining<0 || r.remaining>12000
                    || r.active<0 && r.remaining!=0 || r.warmup<0 || r.warmup>48000 || r.warmup>0 && r.inn==null) throw new IllegalArgumentException();
            records.put(village,r);
        } catch(IllegalArgumentException bad) { opaque=true; }
    }
    Tag toNbt() {
        if(opaque) return retained.copy();
        var root=retained instanceof CompoundTag c?c.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); records.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            var r=e.getValue(); var t=r.retained.copy(); t.putString("village",e.getKey().toString()); t.putLong("day",r.day);
            t.putLong("nextClock",r.nextClock); t.putInt("active",r.active); t.putInt("last",r.last); t.putInt("remaining",r.remaining); t.putInt("warmup",r.warmup);
            if(r.inn!=null) t.putString("inn",r.inn.toString()); else t.remove("inn"); rows.add(t);
        }); root.put("records",rows); return root;
    }
}
