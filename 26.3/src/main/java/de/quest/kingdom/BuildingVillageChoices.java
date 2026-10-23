package de.quest.kingdom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Context must come from the server's native structure lookup, never from a payload. */
final class BuildingVillageChoices {
    private BuildingVillageChoices() {}
    static List<LivingKingdomsState.VillageView> offer(LivingKingdomsState state,String dimension,BlockPos desk,UUID nativeContext) {
        return offer(state,dimension,desk,nativeContext,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC);
    }
    static List<LivingKingdomsState.VillageView> offer(LivingKingdomsState state,String dimension,BlockPos desk,UUID nativeContext,
            BuildingScanner.Role role,BuildingScanner.Range range) {
        var choices=new LinkedHashMap<UUID,LivingKingdomsState.VillageView>();
        if (dimension==null || desk==null) return List.of();
        var bound=state.anchors().at(dimension,desk).filter(a -> a.role()==role).flatMap(a -> state.village(a.villageId()));
        if(bound.isPresent()) return List.of(bound.get());
        state.village(nativeContext).filter(v -> v.origin()==LivingKingdomsState.Origin.GENERATED
                && v.legacyKey().dimension().equals(dimension)).ifPresent(v -> choices.put(v.id(),v));
        var area=new LivingKingdomsState.Zone(desk.getX()-range.horizontal,desk.getX()+range.horizontal,
                desk.getY()-range.vertical,desk.getY()+range.vertical,desk.getZ()-range.horizontal,desk.getZ()+range.horizontal);
        for (var village:state.nearVillages(dimension,desk,128,128)) {
            if (choices.size()==8) break;
            if(state.zones(village.id()).stream().anyMatch(z -> z.contains(desk) || z.touches(area)))
                choices.putIfAbsent(village.id(),village);
        }
        return List.copyOf(choices.values());
    }
}
