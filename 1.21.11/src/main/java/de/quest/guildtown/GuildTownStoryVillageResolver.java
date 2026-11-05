package de.quest.guildtown;

import de.quest.caravan.TradeRouteService;
import de.quest.data.PlayerQuestData;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import de.quest.village.VillageCondition;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Resolves villages that are valid only for local GuildTown history and stories. */
final class GuildTownStoryVillageResolver {
    private GuildTownStoryVillageResolver() {}

    static StoryVillage current(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        if (world == null || player == null || data == null) return null;
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (marker == null) return null;

        boolean registeredDestination = TradeRouteService.isRegisteredDestination(
                world, player.getUUID(), marker.centerX(), marker.centerZ());
        VillageBondService.VillageBondView route = registeredDestination
                ? VillageBondService.inspectCurrentVillage(world, player, false) : null;
        return current(data, marker, registeredDestination, route);
    }

    static StoryVillage current(PlayerQuestData data,
                                ShadowsTradeRoadEncounterService.VillageMarker marker,
                                boolean registeredDestination,
                                VillageBondService.VillageBondView connectedRoute) {
        if (data == null || marker == null) return null;

        // A registered destination remains route-authoritative and may not silently
        // downgrade to an unrelated historical contact if its live view is invalid.
        if (registeredDestination) return fromRoute(connectedRoute);

        // A contact is historical/local knowledge only. This path must never create network state.
        return fromContact(VillageContactService.findAt(data, marker.centerX(), marker.centerZ()));
    }

    static StoryVillage byIndex(ServerLevel world, UUID owner, PlayerQuestData data, int index) {
        if (world == null || owner == null || data == null || index < 0) return null;
        for (VillageBondService.VillageBondView route : VillageBondService.villages(world, owner)) {
            if (route.index() == index) return fromRoute(route);
        }
        return fromContact(VillageContactService.read(data, index));
    }

    static StoryVillage fromContact(VillageContactService.VillageContact contact) {
        return contact == null ? null : new StoryVillage(
                contact.villageIndex(), contact.x(), contact.z(), contact.type(), null, false);
    }

    static StoryVillage fromRoute(VillageBondService.VillageBondView route) {
        return route == null ? null : new StoryVillage(
                route.index(), route.x(), route.z(), route.type(), route.network().condition(), true);
    }

    record StoryVillage(int index, int x, int z, VillageBondType type,
                        VillageCondition condition, boolean connectedRoute) {
        boolean needsRecovery() {
            return condition == VillageCondition.CRISIS || condition == VillageCondition.STRAINED;
        }
    }
}
