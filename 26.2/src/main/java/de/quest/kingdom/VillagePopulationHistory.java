package de.quest.kingdom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Last safe adult observation, not a second physical village status or a recurring founding gate. */
public final class VillagePopulationHistory {
    private int adults=-1,peak;
    private long tick=-1;
    private Tag retained=new CompoundTag();
    private boolean readOnly;
    boolean observe(int adults,boolean confirmed,long tick) {
        if (readOnly || !confirmed || adults<0 || adults>64 || tick<0 || tick<this.tick) return false;
        if (adults==this.adults && tick==this.tick) return false;
        this.adults=adults; this.tick=tick; peak=Math.max(peak,adults); return true;
    }
    public View view() { return new View(adults,tick,peak>=5,peak>=10,readOnly); }
    public record View(int adults,long tick,boolean reachedFive,boolean reachedTen,boolean readOnly) {}
    static VillagePopulationHistory fromNbt(Tag payload) {
        var history=new VillagePopulationHistory();
        if (payload==null) return history;
        history.retained=payload.copy();
        if (!(payload instanceof CompoundTag tag) || tag.getIntOr("schema",0)!=1) { history.readOnly=true; return history; }
        int adults=tag.getIntOr("adults",-2),peak=tag.getIntOr("peak",-1); long tick=tag.getLongOr("tick",-2);
        if (adults< -1 || adults>64 || peak<Math.max(0,adults) || peak>64 || tick< -1 || (adults==-1)!=(tick==-1)) {
            history.readOnly=true; return history;
        }
        history.adults=adults; history.peak=peak; history.tick=tick; return history;
    }
    Tag toNbt() {
        if (readOnly) return retained.copy();
        var tag=retained instanceof CompoundTag compound?compound.copy():new CompoundTag();
        tag.putInt("schema",1); tag.putInt("adults",adults); tag.putInt("peak",peak); tag.putLong("tick",tick); return tag;
    }
}
