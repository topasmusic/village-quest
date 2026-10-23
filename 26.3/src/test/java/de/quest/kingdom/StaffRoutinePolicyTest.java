package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class StaffRoutinePolicyTest {
    @Test void storekeeperPatrolStaysOnCertifiedNearbyFloorAndHasStableStops() {
        BlockPos anchor=new BlockPos(0,64,0);
        Set<BlockPos> floor=Set.of(anchor.north(),anchor.east(4),anchor.east(5),anchor.above(3));
        for(long tick=0;tick<1600;tick+=160) {
            var stop=StaffRoutinePolicy.patrol(floor,anchor,12,tick).orElseThrow();
            assertTrue(stop.equals(anchor.north()) || stop.equals(anchor.east(4)));
            assertEquals(stop,StaffRoutinePolicy.patrol(floor,anchor,12,tick).orElseThrow());
        }
    }
    @Test void fleeingPrefersCoveredSafetyAndNeverSelectsAFloorCloserToTheAttacker() {
        BlockPos person=new BlockPos(0,64,0),threat=person.west(2),shelter=person.east(4),outside=person.east(8);
        assertEquals(shelter,StaffRoutinePolicy.shelter(Set.of(shelter,threat),Set.of(outside),person,threat).orElseThrow());
        assertEquals(outside,StaffRoutinePolicy.shelter(Set.of(threat),Set.of(outside),person,threat).orElseThrow());
        assertTrue(StaffRoutinePolicy.shelter(Set.of(threat),Set.of(person),person,threat).isEmpty());
    }
    @Test void onlyTheStorekeeperCarriesANightTorch() {
        for(var role:KingdomPersonRegistry.Role.values()) {
            assertEquals(role==KingdomPersonRegistry.Role.STOREKEEPER,StaffRoutinePolicy.torch(role,18000));
            assertFalse(StaffRoutinePolicy.torch(role,6000));
        }
    }
}
