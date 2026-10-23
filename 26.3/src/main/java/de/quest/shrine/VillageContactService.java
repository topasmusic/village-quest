package de.quest.shrine;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Personal knowledge of villages before they become trade-route destinations.
 *
 * <p>A contact reuses the permanent village identity record, but deliberately
 * does not create a route, occupy a route slot, or enable caravan/economy state.</p>
 */
public final class VillageContactService {
    private static final String CONTACT_SUFFIX = "contact";

    private VillageContactService() {}

    public static ContactResult establish(ServerLevel world, UUID playerId,
                                          ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (world == null || playerId == null || marker == null) {
            return ContactResult.rejected();
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        String dimension = VillageBondService.dimensionKey(world);
        int previous = VillageBondService.findVillage(data, dimension, marker.centerX(), marker.centerZ());
        boolean newVillage = previous < 0;
        boolean unbound = previous >= 0 && data.getTradeRouteString(
                VillageBondService.villageKey(previous, "canonical_id")).isEmpty();
        VillageIdentityOrigin.Classification classification =
                VillageBondService.classifyDetailed(world, marker);
        ContactResult result = bindCanonical(LivingKingdomsState.get(world.getServer()), data, dimension,
                marker, new BlockPos(marker.centerX(), world.getSeaLevel(), marker.centerZ()), classification.type());
        if (newVillage && result.contact() != null) {
            data.setTradeRouteString(VillageBondService.villageKey(
                    result.contact().villageIndex(), "identity_origin"), classification.origin());
        }
        if (result.created() || (result.accepted() && unbound)) {
            QuestState.get(world.getServer()).setDirty();
        }
        if (result.contact() != null) de.quest.reputation.ProtectedVillageIndex.register(world,
                new de.quest.village.VillageLifeState.VillageKey(dimension, marker.centerX(), marker.centerZ()));
        return result;
    }

    static ContactResult bindCanonical(LivingKingdomsState state, PlayerQuestData data, String dimension,
                                        ShadowsTradeRoadEncounterService.VillageMarker marker,
                                        BlockPos arrival, VillageBondType type) {
        if (state == null || data == null || marker == null || type == null) return ContactResult.rejected();
        VillageKey detected;
        try { detected = new VillageKey(dimension, marker.centerX(), marker.centerZ()); }
        catch (IllegalArgumentException invalid) { return ContactResult.rejected(); }
        var known = state.byLegacy(detected);
        if (known.isEmpty()) known = state.registerGenerated(detected, arrival,
                defaultVillageName(marker.centerX(), marker.centerZ()));
        if (known.isEmpty()) return ContactResult.rejected();
        var village = known.orElseThrow();
        for (int i = 0; i < VillageBondService.historicalVillageCount(data); i++) {
            if (!village.id().toString().equals(data.getTradeRouteString(VillageBondService.villageKey(i, "canonical_id")))) continue;
            VillageKey previousKey = new VillageKey(dimension,
                    data.getTradeRouteInt(VillageBondService.villageKey(i, "x")),
                    data.getTradeRouteInt(VillageBondService.villageKey(i, "z")));
            if (!state.byLegacy(previousKey).map(v -> v.id().equals(village.id())).orElse(false)) {
                return ContactResult.rejected();
            }
            return bindIndex(data, i);
        }
        if (village.origin() == LivingKingdomsState.Origin.GENERATED) {
            int old = VillageBondService.historicalVillageIndex(data, dimension, marker.centerX(), marker.centerZ());
            if (old >= 0 && data.getTradeRouteString(VillageBondService.villageKey(old, "canonical_id")).isEmpty()) {
                VillageKey previousKey = new VillageKey(dimension,
                        data.getTradeRouteInt(VillageBondService.villageKey(old, "x")),
                        data.getTradeRouteInt(VillageBondService.villageKey(old, "z")));
                if (!state.recognizeLegacyAlias(village.id(), previousKey)) return ContactResult.rejected();
                int index = VillageBondService.ensureCanonicalVillageRecord(data, village.id(), dimension,
                        previousKey.anchorX(), previousKey.anchorZ(), type);
                return index < 0 ? ContactResult.rejected() : bindIndex(data, index);
            }
        }
        return establish(data, village, type);
    }

    private static ContactResult bindIndex(PlayerQuestData data, int index) {
        boolean created = !isContact(data, index);
        data.setTradeRouteFlag(contactKey(index), true);
        return new ContactResult(read(data, index), created);
    }

    private static String defaultVillageName(int x, int z) {
        String[] names = { "Talheim", "Steinbrunn", "Lindenau", "Falkenruh", "Morgenfeld", "Weidenfels",
                "Silberhain", "Birkental", "Rosenfurt", "Sonnenwacht", "Eichenried", "Quellenhof" };
        return names[Math.floorMod(31 * x + z, names.length)];
    }

    public static ContactResult establish(PlayerQuestData data, int x, int z, VillageBondType type) {
        return establish(data, "minecraft:overworld", x, z, type);
    }

    /** The shared immutable legacy key, not a movable council seat, binds personal history. */
    public static ContactResult establish(PlayerQuestData data, LivingKingdomsState.VillageView village,
                                          VillageBondType type) {
        if (village == null) return ContactResult.rejected();
        var key = village.legacyKey();
        int index = VillageBondService.ensureCanonicalVillageRecord(data, village.id(),
                key.dimension(), key.anchorX(), key.anchorZ(), type);
        if (index < 0) return ContactResult.rejected();
        boolean created = !isContact(data, index);
        data.setTradeRouteFlag(contactKey(index), true);
        return new ContactResult(read(data, index), created);
    }

    static ContactResult establish(PlayerQuestData data, String dimension,
                                   int x, int z, VillageBondType type) {
        if (data == null || type == null) {
            return ContactResult.rejected();
        }
        int index = VillageBondService.ensureVillageRecord(data, dimension, x, z, type);
        if (index < 0) {
            return ContactResult.rejected();
        }
        boolean created = !isContact(data, index);
        data.setTradeRouteFlag(contactKey(index), true);
        return new ContactResult(read(data, index), created);
    }

    public static int contactCount(PlayerQuestData data) {
        int result = 0;
        int villages = VillageBondService.historicalVillageCount(data);
        for (int index = 0; index < villages; index++) {
            if (isContact(data, index)) {
                result++;
            }
        }
        return result;
    }

    public static boolean isContact(PlayerQuestData data, int villageIndex) {
        return data != null
                && villageIndex >= 0
                && villageIndex < VillageBondService.historicalVillageCount(data)
                && data.hasTradeRouteFlag(contactKey(villageIndex));
    }

    static boolean shouldExposeInConnectedNetwork(PlayerQuestData data, int villageIndex,
                                                   boolean registeredDestination) {
        return !isContact(data, villageIndex) || registeredDestination;
    }

    public static VillageContact read(PlayerQuestData data, int villageIndex) {
        if (!isContact(data, villageIndex)) {
            return null;
        }
        return new VillageContact(
                villageIndex,
                data.getTradeRouteInt(VillageBondService.villageKey(villageIndex, "x")),
                data.getTradeRouteInt(VillageBondService.villageKey(villageIndex, "z")),
                VillageBondType.byId(Math.max(0,
                        data.getTradeRouteInt(VillageBondService.villageKey(villageIndex, "type")) - 1)));
    }

    /** Exact canonical contacts; historical generated contacts retain their center tolerance. */
    public static VillageContact findAt(PlayerQuestData data, int x, int z) {
        return findAt(data, "minecraft:overworld", x, z);
    }

    public static VillageContact findAt(PlayerQuestData data, String dimension, int x, int z) {
        if (data == null) return null;
        return read(data, VillageBondService.historicalVillageIndex(data, dimension, x, z));
    }

    private static String contactKey(int villageIndex) {
        return VillageBondService.villageKey(villageIndex, CONTACT_SUFFIX);
    }

    public record VillageContact(int villageIndex, int x, int z, VillageBondType type) {}

    public record ContactResult(VillageContact contact, boolean created) {
        static ContactResult rejected() {
            return new ContactResult(null, false);
        }

        public boolean accepted() {
            return contact != null;
        }
    }
}
