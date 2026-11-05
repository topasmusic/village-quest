package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageFoundingRegistryTest {
    private static final UUID ACTOR = UUID.randomUUID(), TOWN = UUID.randomUUID(), NEIGHBOR = UUID.randomUUID();
    private static final String DIMENSION = "minecraft:overworld";
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void foundingNeedsThreeDistinctAdultsAndBedsWithAnActualAssignedWorkplace() {
        var state = state(); var evidence = residents();
        assertEquals(VillageFoundingRegistry.Result.INSUFFICIENT, state.founding().claim(ACTOR, TOWN, 0,
                evidence.subList(0, 2), true, true));
        var unemployed = evidence.stream().map(r -> new VillageFoundingRegistry.Resident(r.id(), r.bed(), null)).toList();
        assertEquals(VillageFoundingRegistry.Result.INSUFFICIENT,
                state.founding().claim(ACTOR, TOWN, 0, unemployed, true, true));
        assertEquals(VillageFoundingRegistry.Result.APPLIED, state.founding().claim(ACTOR, TOWN, 0, evidence, true, true));
    }

    @Test void sameResidentOrTwoHalvesOfOneBedCannotFillThreeFoundingPlaces() {
        var state = state(); var a = residents();
        assertEquals(VillageFoundingRegistry.Result.INSUFFICIENT, state.founding().claim(ACTOR, TOWN, 0,
                List.of(a.get(0), a.get(0), a.get(1)), true, true));
        assertEquals(VillageFoundingRegistry.Result.INSUFFICIENT, state.founding().claim(ACTOR, TOWN, 0,
                List.of(a.get(0), a.get(1), new VillageFoundingRegistry.Resident(a.get(2).id(), a.get(0).bed(), null)), true, true));
    }

    @Test void unknownInvalidCouncilForeignAndStaleCannotClaimEvidence() {
        var state = state();
        assertEquals(VillageFoundingRegistry.Result.UNKNOWN, state.founding().claim(ACTOR, TOWN, 0, residents(), false, true));
        assertEquals(VillageFoundingRegistry.Result.INSUFFICIENT, state.founding().claim(ACTOR, TOWN, 0, residents(), true, false));
        assertEquals(VillageFoundingRegistry.Result.UNAUTHORIZED, state.founding().claim(UUID.randomUUID(), TOWN, 0, residents(), true, true));
        assertEquals(VillageFoundingRegistry.Result.STALE, state.founding().claim(ACTOR, TOWN, 1, residents(), true, true));
        assertTrue(state.founding().residents(TOWN).isEmpty());
    }

    @Test void nearbyVillageCannotBorrowPersistedFoundingResidentsBedsOrWorkplace() {
        var state = state(); var evidence = residents();
        state.founding().claim(ACTOR, TOWN, 0, evidence, true, true);
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(VillageFoundingRegistry.Result.CONFLICT,
                restored.founding().claim(ACTOR, NEIGHBOR, 0, evidence, true, true));
        var differentResidentsSameBeds = evidence.stream().map(r -> new VillageFoundingRegistry.Resident(UUID.randomUUID(), r.bed(), r.job())).toList();
        assertEquals(VillageFoundingRegistry.Result.CONFLICT,
                restored.founding().claim(ACTOR, NEIGHBOR, 0, differentResidentsSameBeds, true, true));
        var sameJob = List.of(new VillageFoundingRegistry.Resident(UUID.randomUUID(), new BlockPos(5, 64, 1), evidence.getFirst().job()),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(), new BlockPos(5, 64, 3), null),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(), new BlockPos(5, 64, 5), null));
        assertEquals(VillageFoundingRegistry.Result.CONFLICT,
                restored.founding().claim(ACTOR, NEIGHBOR, 0, sameJob, true, true));
    }

    @Test void proofIsIdempotentAndResidentsMayBeReleasedWithCurrentVillageAuthority() {
        var state = state(); var evidence = residents();
        assertEquals(VillageFoundingRegistry.Result.APPLIED, state.founding().claim(ACTOR, TOWN, 0, evidence, true, true));
        assertEquals(VillageFoundingRegistry.Result.UNCHANGED, state.founding().claim(ACTOR, TOWN, 0, evidence, true, true));
        assertEquals(3, state.founding().residents(TOWN).size());
        assertEquals(VillageFoundingRegistry.Result.APPLIED, state.founding().release(ACTOR, TOWN, 0));
        assertEquals(VillageFoundingRegistry.Result.APPLIED, state.founding().claim(ACTOR, NEIGHBOR, 0, evidence, true, true));
    }

    @Test void futureProofPayloadStaysOpaqueAndCannotAllocateResidents() {
        var state = state(); var root = LivingKingdomsState.toNbt(state);
        var opaque = new CompoundTag(); opaque.putInt("schema", 99); opaque.putString("future", "keep"); root.put("founding", opaque);
        var restored = LivingKingdomsState.fromNbt(root);
        assertEquals(VillageFoundingRegistry.Result.READ_ONLY, restored.founding().claim(ACTOR, TOWN, 0, residents(), true, true));
        assertEquals(opaque, LivingKingdomsState.toNbt(restored).getCompoundOrEmpty("founding"));
    }

    @Test void movingCouncilKeepsTheHistoricalExclusiveProofReadable() {
        var state = state(); var evidence = residents(); state.founding().claim(ACTOR, TOWN, 0, evidence, true, true);
        state.moveCouncil(ACTOR, TOWN, 0, new BlockPos(100, 64, 0));
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertFalse(restored.founding().readOnly()); assertEquals(3, restored.founding().residents(TOWN).size());
        assertEquals(TOWN, restored.founding().bedOwner(DIMENSION, evidence.getFirst().bed()).orElseThrow());
    }

    @Test void innCannotTakeAFoundingResidentsBedAndCouncilDoesNotClaimUnneededNearbyBeds() {
        var state = state(); var evidence = residents(); state.founding().claim(ACTOR, TOWN, 0, evidence, true, true);
        BlockPos bed = evidence.getFirst().bed();
        var innReport = new BuildingScanner.Report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID,
                java.util.Map.of(BuildingScanner.Check.BED, true),
                java.util.Map.of(BuildingScanner.Feature.BED, java.util.Set.of(bed)), java.util.Set.of());
        assertEquals(KingdomAnchorRegistry.Result.STATION_CONFLICT,
                state.anchors().bind(ACTOR, TOWN, 0, UUID.randomUUID(), -1, new BlockPos(0, 64, 0),
                        net.minecraft.core.Direction.SOUTH, BuildingScanner.Range.BASIC, innReport, 0, 0));
        var councilReport = new BuildingScanner.Report(BuildingScanner.Role.COUNCIL, BuildingScanner.Status.VALID,
                java.util.Map.of(BuildingScanner.Check.BOOKS, true), java.util.Map.of(BuildingScanner.Feature.BED,
                java.util.Set.of(bed), BuildingScanner.Feature.BOOKS, java.util.Set.of(new BlockPos(0, 64, 2))), java.util.Set.of());
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,
                state.anchors().bind(ACTOR, TOWN, 0, UUID.randomUUID(), -1, new BlockPos(0, 64, 0),
                        net.minecraft.core.Direction.SOUTH, BuildingScanner.Range.BASIC, councilReport, 0, 0));
        assertTrue(state.anchors().stationOwner(DIMENSION, bed).isEmpty());
    }

    private static List<VillageFoundingRegistry.Resident> residents() {
        return List.of(new VillageFoundingRegistry.Resident(UUID.randomUUID(), new BlockPos(1, 64, 1), new BlockPos(2, 64, 1)),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(), new BlockPos(1, 64, 3), null),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(), new BlockPos(1, 64, 5), null));
    }
    private static LivingKingdomsState state() {
        var state = new LivingKingdomsState();
        state.registerCustom(TOWN, ACTOR, VillageKey.overworld(0, 0), new BlockPos(0, 64, 0), "Home").orElseThrow();
        state.registerCustom(NEIGHBOR, ACTOR, VillageKey.overworld(5, 0), new BlockPos(5, 64, 0), "Near").orElseThrow(); return state;
    }
}
