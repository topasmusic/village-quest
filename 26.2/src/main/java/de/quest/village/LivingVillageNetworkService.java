package de.quest.village;

import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import de.quest.util.TimeUtil;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

/** Server-authoritative bridge between compatible 2.2 village bonds and 2.3 conditions. */
public final class LivingVillageNetworkService {
    static final int MATCHING_DELIVERY_SUPPORT = 24;
    static final int GENERAL_DELIVERY_SUPPORT = 14;

    private LivingVillageNetworkService() {}

    public static LivingVillageNetworkState.VillageSnapshot ensureVillage(
            ServerLevel world, UUID ownerId, int index, int x, int z, VillageBondType type) {
        if (world == null || ownerId == null) return null;
        return LivingVillageNetworkState.get(world.getServer()).ensureVillage(ownerId, index, x, z, type);
    }

    public static LivingVillageNetworkState.SupportResult recordNoticeDelivery(
            ServerLevel world, UUID ownerId, int index, int x, int z,
            VillageBondType type, VillageRequestType request) {
        if (world == null || ownerId == null) {
            return new LivingVillageNetworkState.SupportResult(null, false, 0);
        }
        LivingVillageNetworkState state = LivingVillageNetworkState.get(world.getServer());
        LivingVillageNetworkState.VillageSnapshot current = state.ensureVillage(ownerId, index, x, z, type);
        int support = deliverySupport(current.need(), request);
        return state.addSupport(ownerId, index, support, TimeUtil.currentDay());
    }

    public static LivingVillageNetworkState.SupportResult recordNoticeDelivery(
            ServerLevel world, UUID ownerId, int index, int x, int z,
            VillageBondType type, VillageRequestType request, int generatedSupport) {
        if (world == null || ownerId == null) {
            return new LivingVillageNetworkState.SupportResult(null, false, 0);
        }
        LivingVillageNetworkState state = LivingVillageNetworkState.get(world.getServer());
        state.ensureVillage(ownerId, index, x, z, type);
        return state.addSupport(ownerId, index, Math.max(1, generatedSupport), TimeUtil.currentDay());
    }

    public static LivingVillageNetworkState.RouteResult recordRouteArrival(
            ServerLevel world, UUID ownerId, int index, Item cargo, boolean suppliedFreight,
            int supportBonus, int energyBonus, boolean energyEnabled) {
        if (world == null || ownerId == null || index < 0) {
            return new LivingVillageNetworkState.RouteResult(null, 0, false);
        }
        LivingVillageNetworkState state = LivingVillageNetworkState.get(world.getServer());
        LivingVillageNetworkState.VillageSnapshot current = state.snapshot(ownerId, index).orElse(null);
        if (current == null) return new LivingVillageNetworkState.RouteResult(null, 0, false);
        boolean matchingFreight = suppliedFreight && current.need().matches(cargo);
        int support = suppliedFreight ? (matchingFreight ? 20 : 12) : 5;
        int energy = energyEnabled ? 1 + (matchingFreight ? 1 : 0) + Math.max(0, energyBonus) : 0;
        return state.recordRouteArrival(ownerId, index, support + Math.max(0, supportBonus),
                energy, TimeUtil.currentDay());
    }

    public static LivingVillageNetworkState.VillageSnapshot recordRouteFailure(
            ServerLevel world, UUID ownerId, int index, int strain) {
        if (world == null || ownerId == null || index < 0) return null;
        return LivingVillageNetworkState.get(world.getServer()).addStrain(
                ownerId, index, Math.max(1, strain), TimeUtil.currentDay());
    }

    /** Cosmetic, bounded prestige title; rank five is the cap. */
    public static Component honorLabel(int rank) {
        return Component.translatable("text.village-quest.village_network.honor."
                + Math.max(1, Math.min(5, rank)));
    }

    public static int deliverySupport(VillageNeed need, VillageRequestType request) {
        return need != null && need.matches(request)
                ? MATCHING_DELIVERY_SUPPORT
                : GENERAL_DELIVERY_SUPPORT;
    }
}
