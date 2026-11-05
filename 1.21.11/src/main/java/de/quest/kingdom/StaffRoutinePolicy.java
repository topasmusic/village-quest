package de.quest.kingdom;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Work and safety destinations from already certified floors, never a new combat or pathfinding engine. */
final class StaffRoutinePolicy {
    private StaffRoutinePolicy() {}
    static Optional<BlockPos> patrol(Set<BlockPos> floor,BlockPos anchor,long seed,long now) {
        var stops=floor.stream().filter(pos -> Math.abs(pos.getX()-anchor.getX())<=4 && Math.abs(pos.getZ()-anchor.getZ())<=4
                && Math.abs(pos.getY()-anchor.getY())<=2).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        if(stops.isEmpty()) return Optional.empty();
        return Optional.of(stops.get(Math.floorMod(seed+Math.floorDiv(now,160),stops.size())));
    }
    static Optional<BlockPos> shelter(Set<BlockPos> covered,Set<BlockPos> other,BlockPos person,BlockPos threat) {
        var preferred=away(covered,person,threat);
        return preferred.isPresent()?preferred:away(other,person,threat);
    }
    private static Optional<BlockPos> away(Set<BlockPos> floor,BlockPos person,BlockPos threat) {
        return floor.stream().filter(pos -> pos.distSqr(person)<=24*24 && pos.distSqr(threat)>person.distSqr(threat)+4)
                .max(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(threat)).thenComparingLong(BlockPos::asLong));
    }
    static boolean torch(KingdomPersonRegistry.Role role,long clock) {
        long time=Math.floorMod(clock,24000);
        return role==KingdomPersonRegistry.Role.STOREKEEPER && time>=13000 && time<23000;
    }
}
