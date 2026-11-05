package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class BuildingWatchIndexTest {
    private static final String DIM = "minecraft:overworld";
    private static final BlockPos POS = new BlockPos(15, 64, 15);
    @Test void routineRefreshKeepsLastCompletedDisplayWhileAuthorityStillRequiresFreshEvidence() {
        var index=new BuildingWatchIndex();var id=UUID.randomUUID();index.watch(id,spec(DIM,POS));
        assertTrue(index.display(id,0).isEmpty());long version=index.revision(id);assertTrue(index.publish(id,version,report(),10));
        for(long tick:new long[]{50,51,100,200,400,1600,1610}) {
            assertEquals(BuildingScanner.Status.VALID,index.display(id,tick).orElseThrow().report().status());
            if(tick>50) assertTrue(index.fresh(id,tick).isEmpty(),"Displaying old evidence must not authorize a mutation");
        }
        assertTrue(index.display(id,1611).isEmpty());
        index.dirty(DIM,POS);assertTrue(index.display(id,100).isEmpty());assertFalse(index.publish(id,version,report(),101));
        var invalid=new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.INVALID,Map.of(),Map.of(),Set.of());
        assertTrue(index.publish(id,index.revision(id),invalid,102));assertEquals(BuildingScanner.Status.INVALID,index.display(id,102).orElseThrow().report().status());
        index.unloaded(id);assertTrue(index.display(id,103).isEmpty());
    }
    @Test void dirtyIndexRespectsDimensionHeightAndExactBoundedScanSpace() {
        var index = new BuildingWatchIndex(); UUID id = UUID.randomUUID(), upper = UUID.randomUUID(), nether = UUID.randomUUID();
        index.watch(id, spec(DIM, POS)); index.watch(upper, spec(DIM, POS.above(20))); index.watch(nether, spec("minecraft:the_nether", POS));
        long revision = index.revision(id), upstairsRevision = index.revision(upper), netherRevision = index.revision(nether);
        assertEquals(Set.of(id), index.dirty(DIM, POS.offset(8, 4, 8)));
        assertTrue(index.revision(id) > revision); assertEquals(upstairsRevision, index.revision(upper)); assertEquals(netherRevision, index.revision(nether));
        assertTrue(index.dirty(DIM, POS.offset(9, 0, 0)).isEmpty());
    }
    @Test void changedRoomCannotPublishAnOldIncrementalSnapshotOrReuseItsSpawnEvidence() {
        var index = new BuildingWatchIndex(); var id = UUID.randomUUID(); index.watch(id, spec(DIM, POS));
        long revision = index.revision(id); assertTrue(index.publish(id, revision, report(), 10));
        assertTrue(index.fresh(id, 50).isPresent()); assertTrue(index.fresh(id, 51).isEmpty());
        index.dirty(DIM, POS.relative(Direction.NORTH));
        assertTrue(index.cached(id, 51, 1000).isEmpty()); assertFalse(index.publish(id, revision, report(), 52));
        assertTrue(index.publish(id, index.revision(id), report(), 53));
    }
    @Test void unloadAndReconfigurationInvalidateEvidenceAndRemoveOldSpatialIndexEntries() {
        var index = new BuildingWatchIndex(); var id = UUID.randomUUID(); index.watch(id, spec(DIM, POS));
        index.publish(id, index.revision(id), report(), 10); index.unloaded(id);
        assertTrue(index.fresh(id, 10).isEmpty());
        index.watch(id, spec(DIM, POS.offset(100, 0, 0)));
        assertTrue(index.dirty(DIM, POS).isEmpty()); assertEquals(Set.of(id), index.dirty(DIM, POS.offset(100, 0, 0)));
        index.forget(id); assertTrue(index.dirty(DIM, POS.offset(100, 0, 0)).isEmpty());
    }
    @Test void watchCapacityAndBackwardOrWrongRoleReportsAreRejected() {
        var index = new BuildingWatchIndex(); var id = UUID.randomUUID(); index.watch(id, spec(DIM, POS));
        assertTrue(index.publish(id, index.revision(id), report(), 100)); assertFalse(index.publish(id, index.revision(id), report(), 90));
        var wrong = new BuildingScanner.Report(BuildingScanner.Role.MARKET, BuildingScanner.Status.VALID, Map.of(), Map.of(), Set.of());
        assertFalse(index.publish(id, index.revision(id), wrong, 110));
        for (int n = 1; n < 256; n++) assertTrue(index.watch(UUID.randomUUID(), spec(DIM, POS.offset(n * 20, 0, 0))));
        assertFalse(index.watch(UUID.randomUUID(), spec(DIM, POS.offset(6000, 0, 0))));
    }
    private static BuildingWatchIndex.Spec spec(String dim, BlockPos pos) {
        return new BuildingWatchIndex.Spec(dim, pos, Direction.NORTH, BuildingScanner.Role.COUNCIL, BuildingScanner.Range.BASIC, 0);
    }
    private static BuildingScanner.Report report() {
        return new BuildingScanner.Report(BuildingScanner.Role.COUNCIL, BuildingScanner.Status.VALID, Map.of(), Map.of(), Set.of());
    }
}
