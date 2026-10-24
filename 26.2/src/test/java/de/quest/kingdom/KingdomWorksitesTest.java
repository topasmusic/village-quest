package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class KingdomWorksitesTest {
    private record Fixture(LivingKingdomsState state,UUID town,UUID actor,UUID anchor) {}
    private static Fixture fixture() {
        var s=new LivingKingdomsState(); var town=UUID.randomUUID(); var actor=UUID.randomUUID(); var anchor=UUID.randomUUID();
        s.registerCustom(town,actor,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        assertEquals(LivingKingdomsState.Mutation.APPLIED,s.confirmZone(actor,town,0,new LivingKingdomsState.Zone(-8,8,60,68,-8,8)));
        var report=new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,Map.of(),
                Map.of(BuildingScanner.Feature.LIGHT,Set.of(new BlockPos(0,65,0))),Set.of(new BlockPos(0,64,0)));
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,s.anchors().bind(actor,town,1,anchor,-1,new BlockPos(0,64,0),Direction.NORTH,
                BuildingScanner.Range.BASIC,report,0,0));
        return new Fixture(s,town,actor,anchor);
    }
    private static NativeFoundingObserver.Evidence evidence(BlockPos job,boolean loaded) {
        return new NativeFoundingObserver.Evidence(1,List.of(new VillageFoundingRegistry.Resident(UUID.randomUUID(),new BlockPos(2,64,0),job)),loaded,2);
    }
    @Test void actualAssignedReachableWorksiteSuppliesTheFifthProjectWithoutPopulationLevelGates() {
        var f=fixture();
        assertTrue(f.state.worksites().observe(f.town,1,f.anchor,0,0,evidence(new BlockPos(3,64,0),true)));
        assertTrue(f.state.worksites().valid(f.town,0));
        assertTrue(KingdomRecognitionService.facts(f.state,new de.quest.data.PlayerQuestData(),f.town,0).contains(KingdomRecognition.Evidence.WORKSITE));
        assertFalse(LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state)).worksites().valid(f.town,0),"Restart cannot create current physical evidence");
    }
    @Test void unloadedMissingOutOfVillageAndAmbiguousWorkstationsCannotSupplyEvidence() {
        var f=fixture(); var w=f.state.worksites();
        assertFalse(w.observe(f.town,1,f.anchor,0,0,evidence(null,true)));
        assertFalse(w.observe(f.town,1,f.anchor,0,0,evidence(new BlockPos(3,64,0),false)));
        assertFalse(w.observe(f.town,1,f.anchor,0,0,evidence(new BlockPos(40,64,0),true)));
        var neighbour=UUID.randomUUID(); f.state.registerCustom(neighbour,f.actor,VillageKey.overworld(10,0),new BlockPos(10,64,0),"Neighbour").orElseThrow();
        f.state.confirmZone(f.actor,neighbour,0,new LivingKingdomsState.Zone(3,14,60,68,-8,8));
        assertFalse(w.observe(f.town,1,f.anchor,0,0,evidence(new BlockPos(3,64,0),true)));
    }
    @Test void revisionTimeAndRealBlockChangesInvalidateTheLocalProof() {
        var f=fixture(); var w=f.state.worksites();
        assertTrue(w.observe(f.town,1,f.anchor,0,0,evidence(new BlockPos(3,64,0),true)));
        var report=f.state.anchors().report(f.anchor).orElseThrow();
        f.state.anchors().observe(f.anchor,0,report,101);
        assertFalse(w.valid(f.town,101));
        assertTrue(w.observe(f.town,1,f.anchor,0,101,evidence(new BlockPos(3,64,0),true)));
        assertFalse(w.valid(f.town,100),"Clock rollback is not fresh evidence");
        w.invalidateAt("minecraft:overworld",new BlockPos(3,63,0)); assertFalse(w.valid(f.town,101));
        assertTrue(w.observe(f.town,1,f.anchor,0,101,evidence(new BlockPos(3,64,0),true)));
        f.state.rename(f.actor,f.town,1,"Renamed"); assertFalse(w.valid(f.town,101));
        assertFalse(w.observe(f.town,1,f.anchor,0,101,evidence(new BlockPos(3,64,0),true)));
    }
}
