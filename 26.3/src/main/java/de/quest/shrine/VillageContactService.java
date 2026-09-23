package de.quest.shrine;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import java.util.UUID;
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
        ContactResult result = establish(data, VillageBondService.dimensionKey(world),
                marker.centerX(), marker.centerZ(),
                VillageBondService.classify(world, marker, playerId));
        if (result.created()) {
            QuestState.get(world.getServer()).setDirty();
        }
        return result;
    }

    public static ContactResult establish(PlayerQuestData data, int x, int z, VillageBondType type) {
        return establish(data, "minecraft:overworld", x, z, type);
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

    /** Finds a contact near a detected village center without creating route or network state. */
    public static VillageContact findAt(PlayerQuestData data, int x, int z) {
        if (data == null) return null;
        int villages = VillageBondService.historicalVillageCount(data);
        for (int index = 0; index < villages; index++) {
            VillageContact contact = read(data, index);
            if (contact != null && Math.abs((long) contact.x() - x) <= 8L
                    && Math.abs((long) contact.z() - z) <= 8L) {
                return contact;
            }
        }
        return null;
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
