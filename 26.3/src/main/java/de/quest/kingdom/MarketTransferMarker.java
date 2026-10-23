package de.quest.kingdom;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

/** Same immutable transfer identity in the wallet document and the player inventory document. */
public final class MarketTransferMarker {
    public enum Stage { BEFORE,AFTER,COMMITTED,CANCELLED }
    private final CompoundTag transfer;
    private final Stage stage;
    private final CompoundTag retained;
    MarketTransferMarker(CompoundTag transfer,Stage stage) { this(transfer,stage,new CompoundTag()); }
    private MarketTransferMarker(CompoundTag transfer,Stage stage,CompoundTag retained) {
        this.transfer=transfer.copy(); this.stage=stage; this.retained=retained.copy();
    }
    public Stage stage() { return stage; }
    public UUID actor() { return UUID.fromString(transfer.getStringOr("actor","")); }
    public UUID receipt() { return UUID.fromString(transfer.getStringOr("id","")); }
    public long beforeBalance() { return transfer.getLongOr("beforeBalance",-1); }
    public long afterBalance() { return transfer.getLongOr("afterBalance",-1); }
    public boolean sameTransfer(MarketTransferMarker other) { return other!=null && transfer.equals(other.transfer); }
    public MarketTransferMarker withStage(Stage next) { return new MarketTransferMarker(transfer,java.util.Objects.requireNonNull(next),retained); }
    boolean matches(CompoundTag expected) { return transfer.equals(expected); }
    public CompoundTag toNbt() {
        var root=retained.copy(); root.putInt("schema",1); root.putString("stage",stage.name()); root.put("transfer",transfer.copy()); return root;
    }
    public static Optional<MarketTransferMarker> fromNbt(Tag payload) {
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag)
                || root.getIntOr("schema",-1)!=1 || !(root.get("transfer") instanceof CompoundTag transfer)) return Optional.empty();
        try {
            for(String key:java.util.List.of("id","actor","visit","trader")) if(UUID.fromString(transfer.getStringOr(key,"")).equals(BuildingSessionPolicy.NONE)) return Optional.empty();
            if(!transfer.getStringOr("sku","").matches("[a-z0-9_]{1,32}")) return Optional.empty();
            for(String key:java.util.List.of("price","beforeBalance","afterBalance")) if(!(transfer.get(key) instanceof LongTag value) || value.longValue()<0) return Optional.empty();
            long price=transfer.getLongOr("price",0),before=transfer.getLongOr("beforeBalance",-1),after=transfer.getLongOr("afterBalance",-1);
            if(price<1 || price>128_000_000_000L || !(transfer.get("plan") instanceof CompoundTag plan)
                    || !(plan.get("schema") instanceof IntTag) || plan.getIntOr("schema",-1)!=1
                    || !(plan.get("quantity") instanceof IntTag) || plan.getIntOr("quantity",0)<1 || plan.getIntOr("quantity",0)>128
                    || !(plan.get("commodity") instanceof CompoundTag)) return Optional.empty();
            String side=plan.getStringOr("side","");
            if(side.equals("BUY")?before<price || after!=before-price:!side.equals("SELL") || before>Long.MAX_VALUE-price || after!=before+price) return Optional.empty();
            for(String key:java.util.List.of("before","after")) {
                if(!(plan.get(key) instanceof ListTag slots) || slots.size()!=36) return Optional.empty();
                for(var slot:slots) if(!(slot instanceof CompoundTag)) return Optional.empty();
            }
            if(!(plan.get("limits") instanceof ListTag limits) || limits.size()!=36) return Optional.empty();
            for(var limit:limits) if(!(limit instanceof IntTag value) || value.intValue()<0 || value.intValue()>64) return Optional.empty();
            return Optional.of(new MarketTransferMarker(transfer,Stage.valueOf(root.getStringOr("stage","")),root));
        }
        catch(IllegalArgumentException invalid) { return Optional.empty(); }
    }
}
