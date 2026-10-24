package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Loaded-column policy only; never runs the native pathfinder or any entity/world tick. */
final class NativeNpcPathPolicyTest {
    @Test void excludesUnsafeShortcutsBeforeSearchButRetainsCertifiedDetourNeighbors() {
        var shortcut=new net.minecraft.world.level.pathfinder.Node(1,64,0);
        var detour=new net.minecraft.world.level.pathfinder.Node(0,64,1);
        var step=new net.minecraft.world.level.pathfinder.Node(0,65,0);
        var neighbors=new net.minecraft.world.level.pathfinder.Node[]{shortcut,detour,step};
        assertEquals(1,NativeNpcPathPolicy.certifiedNeighbors(neighbors,3,Set.of(detour.asBlockPos())));
        assertSame(detour,neighbors[0]); assertNull(neighbors[1]); assertNull(neighbors[2]);
        assertEquals(0,NativeNpcPathPolicy.certifiedNeighbors(neighbors,1,Set.of()));
    }
    @Test void patrolOrStaleFailuresAndTemporaryBudgetLimitsAreNotWorkplaceFailures() {
        assertFalse(NativeNpcPathPolicy.workplaceWarning(false,false,KingdomNpcNavigation.Failure.NO_PATH));
        assertFalse(NativeNpcPathPolicy.workplaceWarning(true,true,KingdomNpcNavigation.Failure.NO_PATH));
        assertFalse(NativeNpcPathPolicy.workplaceWarning(true,false,KingdomNpcNavigation.Failure.BUDGET));
        assertFalse(NativeNpcPathPolicy.workplaceWarning(true,false,KingdomNpcNavigation.Failure.UNKNOWN));
        assertTrue(NativeNpcPathPolicy.workplaceWarning(true,false,KingdomNpcNavigation.Failure.NO_PATH));
    }
    @Test void checksEveryColumnOfTheNativeRegionIncludingNegativeChunkEdges() {
        var visited=new HashSet<BlockPos>(); BlockPos from=new BlockPos(-1,64,-1);
        assertTrue(NativeNpcPathPolicy.loaded(from,8,16,(x,z) -> { visited.add(new BlockPos(x,0,z)); return true; }));
        assertTrue(visited.contains(new BlockPos(-2,0,-2))); assertTrue(visited.contains(new BlockPos(1,0,1)));
        assertEquals(16,visited.size());
        assertFalse(NativeNpcPathPolicy.loaded(from,8,16,(x,z) -> !(x==1 && z==1)));
    }
    @Test void rejectsUnboundedOrNonFiniteNativeRequestsBeforeAnyChunkProbe() {
        java.util.function.BiPredicate<Integer,Integer> forbidden=(x,z) -> { fail("Unexpected probe"); return false; };
        assertFalse(NativeNpcPathPolicy.loaded(BlockPos.ZERO,8,Float.NaN,forbidden));
        assertFalse(NativeNpcPathPolicy.loaded(BlockPos.ZERO,9,16,forbidden));
        assertFalse(NativeNpcPathPolicy.loaded(BlockPos.ZERO,8,128,forbidden));
    }
    @Test void sleepApproachMustBelongToTheCertifiedFloorAndBeWithinVanillaSleepRange() {
        BlockPos head=new BlockPos(4,64,4);
        assertEquals(head.west(),NativeNpcPathPolicy.sleepApproach(Set.of(head.west(),head.offset(-8,0,0)),head,BlockPos.ZERO).orElseThrow());
        assertTrue(NativeNpcPathPolicy.sleepApproach(Set.of(head.offset(3,0,0)),head,BlockPos.ZERO).isEmpty());
    }
}
