package de.quest.kingdom;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/** One physical resident belongs to at most one canonical settlement. Ambiguity is not depopulation. */
public final class VillagePopulationPolicy {
    private VillagePopulationPolicy() {}
    public enum Membership { INCLUDED, OTHER_VILLAGE, UNKNOWN }
    public static Membership classify(LivingKingdomsState state,UUID target,String dimension,UUID resident,BlockPos pos) {
        // Preserve legacy generated observations before an identity has been recorded.
        if (state==null) return Membership.INCLUDED;
        if (state.readOnly() || state.founding().readOnly() || resident==null || pos==null || dimension==null) return Membership.UNKNOWN;
        var owner=state.founding().residentOwner(resident);
        if (owner.isPresent()) {
            var village=state.village(owner.get());
            if (village.isEmpty()) return Membership.UNKNOWN;
            return owner.get().equals(target) && village.get().legacyKey().dimension().equals(dimension)
                    ?Membership.INCLUDED:Membership.OTHER_VILLAGE;
        }
        if (state.isAmbiguousAt(dimension,pos)) return Membership.UNKNOWN;
        var physical=state.resolveAt(dimension,pos);
        return physical.isPresent() && !physical.get().id().equals(target) ?Membership.OTHER_VILLAGE:Membership.INCLUDED;
    }
}
