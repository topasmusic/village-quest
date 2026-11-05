package de.quest.kingdom;

import java.util.OptionalLong;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;

/** A completed native save call is insufficient: verify the documents actually read from disk. */
public final class MarketSavedCheckpoint {
    public static final String PLAYER_PROOF="village-quest:market_transfer";
    private MarketSavedCheckpoint() {}
    public static boolean matches(UUID actor,CompoundTag expectedMarket,CompoundTag expectedWallet,CompoundTag expectedPlayer,
                                  CompoundTag savedMarket,CompoundTag savedWallet,CompoundTag savedPlayer) {
        if(actor==null || expectedMarket==null || expectedWallet==null || expectedPlayer==null || savedMarket==null || savedWallet==null || savedPlayer==null) return false;
        if(!(expectedMarket.get("markets") instanceof CompoundTag markets) || !markets.equals(savedMarket.get("markets"))) return false;
        var journal=expectedWallet.getCompoundOrEmpty("marketTransfers").get(actor.toString());
        if(!(journal instanceof CompoundTag j) || !j.equals(savedWallet.getCompoundOrEmpty("marketTransfers").get(actor.toString()))) return false;
        // Prepared recipients and target epochs share QuestState with the wallet; verify that context was also written.
        if(!java.util.Objects.equals(expectedWallet.getCompoundOrEmpty("questManager").get("tradeRouteStrings"),
                savedWallet.getCompoundOrEmpty("questManager").get("tradeRouteStrings"))) return false;
        var wallet=MarketTransferMarker.fromNbt(j.get("marker"));
        if(wallet.isEmpty() || !wallet.get().actor().equals(actor)) return false;
        var proof=expectedPlayer.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).get(PLAYER_PROOF);
        var inventory=MarketTransferMarker.fromNbt(proof);
        // This proves that the requested images were written. Only the coordinator decides their financial effect.
        if(inventory.isEmpty() || !wallet.get().sameTransfer(inventory.get())
                || !proof.equals(savedPlayer.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).get(PLAYER_PROOF))) return false;
        if(!(expectedPlayer.get("Inventory") instanceof ListTag items) || !items.equals(savedPlayer.get("Inventory"))) return false;
        var expectedBalance=balance(expectedWallet,actor); var savedBalance=balance(savedWallet,actor);
        return expectedBalance.isPresent() && savedBalance.isPresent() && expectedBalance.getAsLong()==savedBalance.getAsLong();
    }
    private static OptionalLong balance(CompoundTag root,UUID actor) {
        if(!(root.getCompoundOrEmpty("questManager").get("currencyBalance") instanceof ListTag rows)) return OptionalLong.empty();
        var owners=new java.util.HashSet<UUID>(); long balance=0;
        for(var value:rows) {
            if(!(value instanceof CompoundTag row) || !(row.get("v") instanceof LongTag number) || number.longValue()<0) return OptionalLong.empty();
            try {
                UUID owner=UUID.fromString(row.getStringOr("id","")); if(!owners.add(owner)) return OptionalLong.empty();
                if(owner.equals(actor)) balance=number.longValue();
            } catch(IllegalArgumentException invalid) { return OptionalLong.empty(); }
        }
        return OptionalLong.of(balance);
    }
}
