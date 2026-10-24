package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Real saved registries and reports only; no world, entities or simulated gameplay. */
final class KingdomMarketPlacesTest {
    static final UUID TOWN=UUID.randomUUID(),OWNER=UUID.randomUUID(),ANCHOR=UUID.randomUUID();
    static final BlockPos POS=new BlockPos(0,64,0);
    static BlockPos stand(int i) { return new BlockPos(i-2,64,-1); }
    static BlockPos counter(int i) { return stand(i).north(); }
    static BlockPos container(int i) { return stand(i).above().north(); }
    static BuildingScanner.Report report() {
        var walk=new HashSet<BlockPos>(); var counters=new HashSet<BlockPos>(); var boxes=new HashSet<BlockPos>();
        for(int i=0;i<5;i++) { walk.add(stand(i)); counters.add(counter(i)); boxes.add(container(i)); }
        return new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),
                Map.of(BuildingScanner.Feature.COUNTER,counters,BuildingScanner.Feature.STALL,counters,BuildingScanner.Feature.LIGHT,Set.of(POS.above(2)),BuildingScanner.Feature.CONTAINER,boxes),walk);
    }
    static LivingKingdomsState state(long now) {
        var state=new LivingKingdomsState(); state.registerCustom(TOWN,OWNER,VillageKey.overworld(0,0),POS,"Market town").orElseThrow();
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(OWNER,TOWN,0,ANCHOR,-1,POS,Direction.NORTH,
                BuildingScanner.Range.BASIC,report(),now,now)); return state;
    }
    static KingdomMarketPlaces.Result assign(LivingKingdomsState state,int i,long now) {
        return state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,state.marketPlaces().revision(ANCHOR),i,stand(i),counter(i),container(i),now);
    }
    @Test void fiveExplicitPlacesPersistButNeverBecomeFreshEvidenceAfterRestart() {
        var state=state(0); assertTrue(state.marketPlaces().usable(ANCHOR,0).isEmpty());
        for(int i=0;i<5;i++) assertEquals(KingdomMarketPlaces.Result.APPLIED,assign(state,i,0));
        assertEquals(5,state.marketPlaces().usable(ANCHOR,0).size());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(5,restored.marketPlaces().assigned(ANCHOR).size()); assertTrue(restored.marketPlaces().usable(ANCHOR,0).isEmpty());
        restored.anchors().observe(ANCHOR,0,report(),10);
        assertEquals(5,restored.marketPlaces().usable(ANCHOR,10).size());
    }
    @Test void staleUnauthorizedAndDisconnectedAssignmentsCannotMutateTheSharedPlaces() {
        var state=state(0); assertEquals(KingdomMarketPlaces.Result.APPLIED,assign(state,0,0));
        var saved=LivingKingdomsState.toNbt(state);
        assertEquals(KingdomMarketPlaces.Result.DENIED,state.marketPlaces().assign(UUID.randomUUID(),TOWN,0,ANCHOR,0,1,1,stand(1),counter(1),container(1),0));
        assertEquals(KingdomMarketPlaces.Result.STALE,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,0,1,stand(1),counter(1),container(1),0));
        assertEquals(KingdomMarketPlaces.Result.NO_EVIDENCE,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,1,1,new BlockPos(25,64,25),counter(1),container(1),0));
        assertEquals(KingdomMarketPlaces.Result.NO_EVIDENCE,assign(state,1,41));
        assertEquals(saved,LivingKingdomsState.toNbt(state));
    }
    @Test void positionsCountersAndContainersAreExclusiveAndRemovedEvidenceIsNeutral() {
        var state=state(0); assign(state,0,0);
        assertEquals(KingdomMarketPlaces.Result.CONFLICT,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,1,1,stand(0),counter(0),container(0),0));
        var unknown=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.UNKNOWN,Map.of(),Map.of(),Set.of());
        state.anchors().observe(ANCHOR,0,unknown,10);
        assertTrue(state.marketPlaces().usable(ANCHOR,10).isEmpty()); assertEquals(1,state.marketPlaces().assigned(ANCHOR).size());
        state.anchors().observe(ANCHOR,0,report(),20); assertEquals(1,state.marketPlaces().usable(ANCHOR,20).size());
        assertEquals(KingdomMarketPlaces.Result.APPLIED,state.marketPlaces().remove(OWNER,TOWN,0,ANCHOR,0,1,0));
        assertTrue(state.marketPlaces().assigned(ANCHOR).isEmpty());
    }
    @Test void movedOfficialRequiresExplicitReassignmentAndOpaqueDataSurvives() {
        var state=state(0); assign(state,0,0);
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(OWNER,TOWN,0,ANCHOR,0,POS.east(),Direction.NORTH,BuildingScanner.Range.BASIC,report(),10,10));
        assertTrue(state.marketPlaces().usable(ANCHOR,10).isEmpty()); assertEquals(1,state.marketPlaces().assigned(ANCHOR).size());
        var saved=LivingKingdomsState.toNbt(state); var future=new CompoundTag(); future.putInt("schema",99); future.putString("opaque","keep"); saved.put("marketPlaces",future);
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.marketPlaces().readOnly());
        assertEquals(future,LivingKingdomsState.toNbt(restored).get("marketPlaces"));
    }
    @Test void legacySavedPlaceCanBeRecertifiedAfterAnchorRevisionButCannotCreateNewGenericCounters() {
        var state=state(0);assign(state,0,0);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        var fresh=report();var stations=new java.util.EnumMap<BuildingScanner.Feature,Set<BlockPos>>(BuildingScanner.Feature.class);
        stations.putAll(fresh.stations());stations.remove(BuildingScanner.Feature.STALL);
        var legacy=new BuildingScanner.Report(fresh.role(),fresh.status(),fresh.checks(),stations,fresh.walkable());
        restored.anchors().observe(ANCHOR,0,legacy,10);
        assertEquals(1,restored.marketPlaces().usable(ANCHOR,10).size());
        assertTrue(restored.marketPlaces().candidates(ANCHOR,10).isEmpty());
        assertEquals(KingdomMarketPlaces.Result.NO_EVIDENCE,assign(restored,1,10));
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,restored.anchors().bind(OWNER,TOWN,0,ANCHOR,0,POS.east(),Direction.NORTH,BuildingScanner.Range.BASIC,legacy,10,10));
        assertEquals(KingdomMarketPlaces.Result.APPLIED,restored.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,1,1,0,stand(0),counter(0),container(0),10));
        assertEquals(1,restored.marketPlaces().usable(ANCHOR,10).size());
    }
    @Test void candidateSelectionDoesNotAssignAnythingAndHasOnlyLocalDistinctResources() {
        var state=state(0); var candidates=state.marketPlaces().candidates(ANCHOR,0);
        assertFalse(candidates.isEmpty()); assertTrue(candidates.size()<=32);
        assertTrue(state.marketPlaces().assigned(ANCHOR).isEmpty());
        var chosen=candidates.getFirst();
        assertEquals(KingdomMarketPlaces.Result.APPLIED,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,0,0,
                chosen.position(),chosen.counter(),chosen.container(),0));
        assertTrue(state.marketPlaces().candidates(ANCHOR,41).isEmpty());
    }
    @Test void sideBySideStallAndBarrelWorkWithFrontStandingSpaceAndNoNearbyLamp() {
        var state=state(0); var standing=stand(0);var stall=standing.north();var barrel=stall.east();
        var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),
                Map.of(BuildingScanner.Feature.STALL,Set.of(stall),BuildingScanner.Feature.COUNTER,Set.of(stall),
                        BuildingScanner.Feature.CONTAINER,Set.of(barrel),BuildingScanner.Feature.LIGHT,Set.of(POS.east(8))),Set.of(standing));
        state.anchors().observe(ANCHOR,0,report,10);
        var chosen=state.marketPlaces().candidates(ANCHOR,10).getFirst();
        assertEquals(stall,chosen.counter());assertEquals(barrel,chosen.container());
        assertEquals(KingdomMarketPlaces.Result.APPLIED,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,0,0,
                standing,stall,barrel,10));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertFalse(restored.marketPlaces().readOnly());assertEquals(1,restored.marketPlaces().assigned(ANCHOR).size());
        restored.anchors().observe(ANCHOR,0,report,20);assertEquals(1,restored.marketPlaces().usable(ANCHOR,20).size());
        assertEquals(KingdomMarketPlaces.Result.CONFLICT,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,1,1,
                standing,stall,barrel,10));
        assertEquals(KingdomMarketPlaces.Result.NO_EVIDENCE,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,1,1,
                standing,stall,barrel.east(3),10));
    }
    @Test void fiveStallsAroundDeskRemainIndependentlyAssignableAndSurviveReload() {
        var state=state(0);var stalls=new HashSet<BlockPos>();var barrels=new HashSet<BlockPos>();var floor=new HashSet<BlockPos>();
        for(int x:new int[]{-6,-3,0,3,6}) { var stall=POS.offset(x,0,-3);stalls.add(stall);barrels.add(stall.east());floor.add(stall.south()); }
        var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),
                Map.of(BuildingScanner.Feature.STALL,stalls,BuildingScanner.Feature.COUNTER,stalls,
                        BuildingScanner.Feature.CONTAINER,barrels,BuildingScanner.Feature.LIGHT,Set.of(POS.above(3))),floor);
        state.anchors().observe(ANCHOR,0,report,10);int slot=0;
        for(int x:new int[]{-6,-3,0,3,6}) {
            var counter=POS.offset(x,0,-3);var chosen=state.marketPlaces().candidates(ANCHOR,10).stream()
                    .filter(p -> p.counter().equals(counter) && p.container().equals(counter.east()) && p.position().equals(counter.south())).findFirst().orElseThrow();
            assertEquals(KingdomMarketPlaces.Result.APPLIED,state.marketPlaces().assign(OWNER,TOWN,0,ANCHOR,0,state.marketPlaces().revision(ANCHOR),slot++,
                    chosen.position(),chosen.counter(),chosen.container(),10));
        }
        assertEquals(5,state.marketPlaces().usable(ANCHOR,10).size());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));assertFalse(restored.marketPlaces().readOnly());
        assertEquals(5,restored.marketPlaces().assigned(ANCHOR).size());restored.anchors().observe(ANCHOR,0,report,20);
        assertEquals(5,restored.marketPlaces().usable(ANCHOR,20).size());
    }
}
