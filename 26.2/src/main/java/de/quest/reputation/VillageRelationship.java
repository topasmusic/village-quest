package de.quest.reputation;

import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.shrine.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Transient, read-only presentation of existing personal village records. */
public final class VillageRelationship {
    private VillageRelationship() {}
    public record Hints(boolean enabled, boolean available, int conflict, Component nextBond, Component repair) {
        public Hints { if (conflict < 0 || conflict > 2) throw new IllegalArgumentException("Invalid conflict status"); }
    }
    public static final Hints UNKNOWN = new Hints(true, false, 0, Component.empty(), Component.empty());
    public static Component text(String key, Object... args) {
        return Component.translatable("screen.village-quest.relationship." + key, args);
    }
    public static Hints project(PlayerQuestData data, int index, VillageKey key, UUID actor,
                                LivingKingdomsState kingdoms, boolean enabled) {
        if (index != exactIndex(data,key)) index = -1;
        var bond = VillageBondProgression.snapshot(data, index);
        Component next = index < 0 ? text("bond_unavailable") : switch (bond.level()) {
            case KNOWN -> bond.tradePathAvailable()
                    ? text("bond_trade", bond.remainingRequestsToTrusted(), bond.remainingTradesToTrusted())
                    : text("bond_first", bond.remainingRequestsToTrusted());
            case TRUSTED -> text("bond_allied", Math.max(0, VillageBondProgression.ALLIED_REQUESTS - bond.actualCompletions()));
            case ALLIED -> text("bond_max");
        };
        // Resolve exact dimension/anchor and verify the stored canonical identity if present.
        var village = kingdoms.byLegacy(key).orElse(null);
        String canonical = index < 0 ? "" : data.getTradeRouteString(VillageBondService.villageKey(index, "canonical_id"));
        boolean match = village != null && (canonical.isEmpty() || canonical.equals(village.id().toString()));
        var personal = match ? kingdoms.relations().personal(actor, village.id()) : null;
        int conflict = personal == null || !personal.available() ? 0 : personal.distrust() ? 2 : 1;
        Component repair = conflict == 2 ? (personal.repairNeed().equals("none") ? text("conflict_pending") : text("conflict_repair", Component.translatable("text.village-quest.village_network.need." + personal.repairNeed()))) : Component.empty();
        return new Hints(enabled, index >= 0 && data.socialReputation().writable(), conflict, next, repair);
    }
    public static int exactIndex(PlayerQuestData data, VillageKey key) {
        for (int i=0; i<VillageBondService.historicalVillageCount(data); i++) {
            String dim=data.getTradeRouteString(VillageBondService.villageKey(i,"dimension"));
            if(dim.isBlank()) dim="minecraft:overworld";
            if(dim.equals(key.dimension()) && data.getTradeRouteInt(VillageBondService.villageKey(i,"x"))==key.anchorX()
                    && data.getTradeRouteInt(VillageBondService.villageKey(i,"z"))==key.anchorZ()) return i;
        }
        return -1;
    }
    public static void write(RegistryFriendlyByteBuf buf, Hints hints) {
        buf.writeBoolean(hints.enabled()); buf.writeBoolean(hints.available()); buf.writeVarInt(hints.conflict());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,hints.nextBond());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,hints.repair());
    }
    public static Hints read(RegistryFriendlyByteBuf buf) {
        return new Hints(buf.readBoolean(),buf.readBoolean(),buf.readVarInt(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
    }
}
