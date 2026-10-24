package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BuildingConfirmationTest {
    private static final UUID ACTOR = UUID.randomUUID(), TOWN = UUID.randomUUID(), COUNCIL = UUID.randomUUID();
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void councilRelocationCommitsZoneAndPhysicalArrivalTogetherWhileKeepingLegacyKeyAndAnchorId() {
        var state = state(); long villageRevision = state.village(TOWN).orElseThrow().revision();
        assertEquals(KingdomAnchorRegistry.Result.APPLIED, state.confirmBuilding(ACTOR, TOWN, villageRevision, COUNCIL, 0,
                POS.offset(14, 0, 0), Direction.SOUTH, BuildingScanner.Range.BASIC, report(BuildingScanner.Role.COUNCIL, POS.offset(14, 0, 2)), 10, 10));
        assertEquals(POS.offset(14, 0, 0), state.village(TOWN).orElseThrow().arrival());
        assertEquals(VillageKey.overworld(0, 0), state.village(TOWN).orElseThrow().legacyKey());
        assertEquals(COUNCIL, state.anchors().official(TOWN, BuildingScanner.Role.COUNCIL).orElseThrow().id());
        assertTrue(state.anchors().at("minecraft:overworld", POS).isEmpty());
        assertTrue(state.anchors().near("minecraft:overworld", POS, 8, 8).isEmpty());
        assertEquals(COUNCIL, state.anchors().near("minecraft:overworld", POS.offset(14, 0, 0), 8, 8).getFirst().id());
        assertTrue(state.nearVillages("minecraft:overworld", POS, 8, 8).isEmpty());
        assertEquals(TOWN, state.nearVillages("minecraft:overworld", POS.offset(14, 0, 0), 8, 8).getFirst().id());
        assertTrue(state.zones(TOWN).stream().anyMatch(z -> z.contains(POS.offset(14, 0, 0))));
    }
    @Test void conflictingConfirmationCannotExpandAreaOrMoveTheVillageAsAPartialSideEffect() {
        var state = state(); UUID neighbour = UUID.randomUUID(), inn = UUID.randomUUID();
        BlockPos pos = POS.offset(16, 0, 0), bed = POS.offset(10, 0, 2);
        state.registerCustom(neighbour, ACTOR, VillageKey.overworld(16, 0), pos, "Other").orElseThrow();
        state.anchors().bind(ACTOR, neighbour, 0, inn, -1, pos, Direction.NORTH, BuildingScanner.Range.BASIC,
                report(BuildingScanner.Role.INN, bed), 0, 0);
        var before = LivingKingdomsState.toNbt(state);
        assertEquals(KingdomAnchorRegistry.Result.STATION_CONFLICT, state.confirmBuilding(ACTOR, TOWN,
                state.village(TOWN).orElseThrow().revision(), UUID.randomUUID(), -1, POS.offset(8, 0, 0), Direction.SOUTH,
                BuildingScanner.Range.BASIC, report(BuildingScanner.Role.INN, bed), 10, 10));
        assertEquals(before, LivingKingdomsState.toNbt(state));
    }
    @Test void cachedLoadedOperationDoesNotRefreshTheActualCompletedSpawnReport() {
        var state = state(); assertTrue(state.anchors().maySpawn(COUNCIL, 0));
        assertTrue(state.anchors().maintainCached(COUNCIL, 0, 0, 120));
        assertTrue(state.anchors().operational(COUNCIL, 120)); assertFalse(state.anchors().maySpawn(COUNCIL, 120));
        state.anchors().invalidateEvidence(COUNCIL);
        assertFalse(state.anchors().operational(COUNCIL, 120)); assertFalse(state.anchors().maySpawn(COUNCIL, 120));
    }
    @Test void councilNameAndRegistrationCommitTogetherWithoutReplacingVillageIdentity() {
        var state=state();
        var result=state.confirmBuilding(ACTOR,TOWN,1,COUNCIL,0,POS,Direction.NORTH,BuildingScanner.Range.BASIC,
                report(BuildingScanner.Role.COUNCIL,POS.offset(0,0,-2)),10,10,"Rivermead");
        assertEquals(KingdomAnchorRegistry.Result.UNCHANGED,result);
        assertEquals("Rivermead",state.village(TOWN).orElseThrow().name());
        assertEquals(COUNCIL,state.anchors().official(TOWN,BuildingScanner.Role.COUNCIL).orElseThrow().id());
        var before=LivingKingdomsState.toNbt(state);
        assertEquals(KingdomAnchorRegistry.Result.INVALID,state.confirmBuilding(ACTOR,TOWN,state.village(TOWN).orElseThrow().revision(),COUNCIL,0,
                POS.offset(14,0,0),Direction.SOUTH,BuildingScanner.Range.BASIC,report(BuildingScanner.Role.COUNCIL,POS.offset(14,0,-2)),20,20,"bad§name"));
        assertEquals(before,LivingKingdomsState.toNbt(state));
        assertEquals(KingdomAnchorRegistry.Result.UNAUTHORIZED,state.confirmBuilding(UUID.randomUUID(),TOWN,state.village(TOWN).orElseThrow().revision(),COUNCIL,0,
                POS,Direction.NORTH,BuildingScanner.Range.BASIC,report(BuildingScanner.Role.COUNCIL,POS.offset(0,0,-2)),20,20,"Stolen"));
        assertEquals(before,LivingKingdomsState.toNbt(state));
    }
    @Test void savedBindingDistinguishesCurrentBuildingPendingAreaAndAnotherLocation() {
        var state=state();
        assertEquals(de.quest.network.BuildingPayloads.Binding.REGISTERED,KingdomBuildingService.binding(state,"minecraft:overworld",POS,BuildingScanner.Role.COUNCIL,Direction.NORTH,BuildingScanner.Range.BASIC,TOWN));
        assertEquals(de.quest.network.BuildingPayloads.Binding.CHANGED,KingdomBuildingService.binding(state,"minecraft:overworld",POS,BuildingScanner.Role.COUNCIL,Direction.NORTH,BuildingScanner.Range.EXTENDED,TOWN));
        assertEquals(de.quest.network.BuildingPayloads.Binding.ELSEWHERE,KingdomBuildingService.binding(state,"minecraft:overworld",POS.offset(1,0,0),BuildingScanner.Role.COUNCIL,Direction.NORTH,BuildingScanner.Range.BASIC,TOWN));
        state.anchors().detach(ACTOR,COUNCIL,0);
        assertEquals(de.quest.network.BuildingPayloads.Binding.ELSEWHERE,KingdomBuildingService.binding(state,"minecraft:overworld",POS,BuildingScanner.Role.COUNCIL,Direction.NORTH,BuildingScanner.Range.BASIC,TOWN));
    }
    @Test void firstGeneratedCouncilGetsChosenNameWithoutCreatingAnotherVillageOrLosingLegacyAlias() {
        var state=new LivingKingdomsState();var home=state.registerGenerated(VillageKey.overworld(0,0),POS,"Generated").orElseThrow();
        assertTrue(state.recognizeMediator(home.id(),ACTOR,true));
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.confirmBuilding(ACTOR,home.id(),state.village(home.id()).orElseThrow().revision(),UUID.randomUUID(),-1,
                POS.offset(1,0,0),Direction.NORTH,BuildingScanner.Range.BASIC,report(BuildingScanner.Role.COUNCIL,POS.offset(1,0,-2)),10,10,"Rivermead"));
        assertEquals(home.id(),state.byLegacy(VillageKey.overworld(0,0)).orElseThrow().id());
        assertEquals("Rivermead",state.village(home.id()).orElseThrow().name());
        assertEquals(home.id(),state.resolveAt("minecraft:overworld",POS.offset(1,0,0)).orElseThrow().id());
        assertEquals(1,state.villages().size());
    }
    private static LivingKingdomsState state() {
        var state = new LivingKingdomsState(); state.registerCustom(TOWN, ACTOR, VillageKey.overworld(0, 0), POS, "Home").orElseThrow();
        state.confirmZone(ACTOR, TOWN, 0, new LivingKingdomsState.Zone(-8, 8, 60, 68, -8, 8));
        state.anchors().bind(ACTOR, TOWN, 1, COUNCIL, -1, POS, Direction.NORTH, BuildingScanner.Range.BASIC,
                report(BuildingScanner.Role.COUNCIL, POS.offset(0, 0, -2)), 0, 0); return state;
    }
    private static BuildingScanner.Report report(BuildingScanner.Role role, BlockPos point) {
        return new BuildingScanner.Report(role, BuildingScanner.Status.VALID, Map.of(),
                Map.of(role == BuildingScanner.Role.COUNCIL ? BuildingScanner.Feature.BOOKS : BuildingScanner.Feature.BED, Set.of(point)), Set.of());
    }
}
