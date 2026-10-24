package de.quest.kingdom;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;

/** Receipt proof stored in the existing QuestState document with the existing currency balance. */
public final class MarketWalletJournal {
    private Tag retained=new CompoundTag();
    private MarketTransferMarker marker;
    private boolean readOnly;
    public boolean readOnly() { return readOnly; }
    public Optional<MarketTransferMarker> marker() { return Optional.ofNullable(marker); }
    public boolean begin(UUID actor,MarketTransferMarker before,long currentBalance) {
        if(readOnly || before==null || !before.actor().equals(actor) || before.stage()!=MarketTransferMarker.Stage.BEFORE
                || before.beforeBalance()!=currentBalance) return false;
        if(marker!=null) {
            if(marker.receipt().equals(before.receipt())) return marker.stage()==MarketTransferMarker.Stage.BEFORE && marker.sameTransfer(before);
            if(marker.stage()!=MarketTransferMarker.Stage.COMMITTED && marker.stage()!=MarketTransferMarker.Stage.CANCELLED) return false;
        }
        marker=before; return true;
    }
    public boolean advance(MarketTransferMarker next) {
        if(readOnly || marker==null || next==null || !marker.sameTransfer(next)) return false;
        var old=marker.stage(); var stage=next.stage();
        if(old==stage) return true;
        if(!(old==MarketTransferMarker.Stage.BEFORE && (stage==MarketTransferMarker.Stage.AFTER || stage==MarketTransferMarker.Stage.CANCELLED)
                || old==MarketTransferMarker.Stage.AFTER && stage==MarketTransferMarker.Stage.COMMITTED)) return false;
        marker=marker.withStage(stage); return true;
    }
    public Tag toNbt() {
        if(readOnly) return retained.copy(); var root=retained instanceof CompoundTag c?c.copy():new CompoundTag();
        root.putInt("schema",1); if(marker!=null) root.put("marker",marker.toNbt()); return root;
    }
    public static MarketWalletJournal fromNbt(Tag raw) {
        var result=new MarketWalletJournal(); if(raw==null) return result;
        result.retained=raw.copy();
        if(!(raw instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",-1)!=1) { result.readOnly=true; return result; }
        if(root.contains("marker")) {
            result.marker=MarketTransferMarker.fromNbt(root.get("marker")).orElse(null);
            if(result.marker==null) result.readOnly=true;
        }
        return result;
    }
}
