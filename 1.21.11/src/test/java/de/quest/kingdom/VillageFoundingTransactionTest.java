package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageFoundingTransactionTest {
    private static final UUID ACTOR = UUID.randomUUID(), TOWN = UUID.randomUUID(), ANCHOR = UUID.randomUUID();
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void successfulFoundingCommitsStableVillageZoneCouncilAndExclusiveProofTogetherAndOnlyOnce() {
        var state = new LivingKingdomsState(); var residents = residents();
        assertEquals(LivingKingdomsState.FoundingCode.APPLIED, found(state, residents, true).code());
        assertEquals(1, state.villages().size()); assertEquals(TOWN, state.byLegacy(VillageKey.overworld(0, 0)).orElseThrow().id());
        assertTrue(state.zones(TOWN).getFirst().contains(POS)); assertEquals(ANCHOR, state.anchors().official(TOWN, BuildingScanner.Role.COUNCIL).orElseThrow().id());
        assertEquals(3, state.founding().residents(TOWN).size()); assertTrue(state.anchors().maySpawn(ANCHOR, 0));
        var before = LivingKingdomsState.toNbt(state);
        assertEquals(LivingKingdomsState.FoundingCode.UNCHANGED, found(state, residents, true).code());
        assertEquals(before, LivingKingdomsState.toNbt(state));
        var restored = LivingKingdomsState.fromNbt(before);
        assertEquals(3, restored.founding().residents(TOWN).size()); assertEquals(ANCHOR, restored.anchors().official(TOWN, BuildingScanner.Role.COUNCIL).orElseThrow().id());
    }
    @Test void unknownAndInsufficientProofLeaveNoPartialVillageOrAnchor() {
        var state = new LivingKingdomsState(); var before = LivingKingdomsState.toNbt(state);
        assertEquals(LivingKingdomsState.FoundingCode.UNKNOWN, found(state, residents(), false).code());
        assertEquals(LivingKingdomsState.FoundingCode.INVALID, found(state, residents().subList(0, 2), true).code());
        assertEquals(before, LivingKingdomsState.toNbt(state)); assertTrue(state.villages().isEmpty()); assertTrue(state.anchors().all().isEmpty());
    }
    @Test void rejectedSharedEvidenceCannotLeaveAnotherVillageIdentityBehind() {
        var state = new LivingKingdomsState(); var residents = residents(); found(state, residents, true);
        var before = LivingKingdomsState.toNbt(state);
        var rejected = state.foundCustom(UUID.randomUUID(), ACTOR, VillageKey.overworld(5, 0), POS.offset(5, 0, 0), "Neighbor",
                UUID.randomUUID(), Direction.SOUTH, BuildingScanner.Range.BASIC, council(), 0, 0, residents, true);
        assertEquals(LivingKingdomsState.FoundingCode.CONFLICT, rejected.code()); assertEquals(before, LivingKingdomsState.toNbt(state));
    }
    @Test void committingANewVillageDoesNotDiscardExistingBuildingsFreshOperationalEvidence() {
        var state = new LivingKingdomsState(); UUID other = UUID.randomUUID(), inn = UUID.randomUUID();
        BlockPos pos = new BlockPos(100, 64, 0); state.registerCustom(other, ACTOR, VillageKey.overworld(100, 0), pos, "Old").orElseThrow();
        var report = new BuildingScanner.Report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, Map.of(BuildingScanner.Check.BED, true),
                Map.of(BuildingScanner.Feature.BED, Set.of(pos.offset(2, 0, 0))), Set.of());
        state.anchors().bind(ACTOR, other, 0, inn, -1, pos, Direction.NORTH, BuildingScanner.Range.BASIC, report, 0, 0);
        assertTrue(state.anchors().maySpawn(inn, 0)); assertEquals(LivingKingdomsState.FoundingCode.APPLIED, found(state, residents(), true).code());
        assertTrue(state.anchors().maySpawn(inn, 0)); assertEquals(0, state.village(other).orElseThrow().revision());
    }

    @Test void emptySettlementRegistersFirstAndPopulationIsRecognizedLaterOnlyOnce() {
        var state=new LivingKingdomsState();
        assertEquals(LivingKingdomsState.FoundingCode.APPLIED,found(state,List.of(),true).code());
        assertTrue(state.founding().residents(TOWN).isEmpty());
        var revision=state.village(TOWN).orElseThrow().revision();
        assertEquals(VillageFoundingRegistry.Result.INSUFFICIENT,state.founding().claim(ACTOR,TOWN,revision,residents().subList(0,2),true,true));
        assertEquals(VillageFoundingRegistry.Result.APPLIED,state.founding().claim(ACTOR,TOWN,revision,residents(),true,true));
        var saved=LivingKingdomsState.toNbt(state);
        var restored=LivingKingdomsState.fromNbt(saved);
        assertEquals(VillageFoundingRegistry.Result.UNCHANGED,restored.founding().claim(ACTOR,TOWN,revision,List.of(),false,false));
        assertEquals(saved,LivingKingdomsState.toNbt(restored));
        assertEquals(3,restored.founding().residents(TOWN).size());
    }
    private static LivingKingdomsState.FoundingResult found(LivingKingdomsState state, List<VillageFoundingRegistry.Resident> residents, boolean loaded) {
        return state.foundCustom(TOWN, ACTOR, VillageKey.overworld(0, 0), POS, "Brookfield", ANCHOR, Direction.SOUTH,
                BuildingScanner.Range.BASIC, council(), 0, 0, residents, loaded);
    }
    private static BuildingScanner.Report council() {
        return new BuildingScanner.Report(BuildingScanner.Role.COUNCIL, BuildingScanner.Status.VALID, Map.of(BuildingScanner.Check.BOOKS, true),
                Map.of(BuildingScanner.Feature.BOOKS, Set.of(POS.offset(0, 0, -2))), Set.of(POS.relative(Direction.SOUTH)));
    }
    private static List<VillageFoundingRegistry.Resident> residents() {
        return List.of(new VillageFoundingRegistry.Resident(UUID.randomUUID(), POS.offset(1, 0, 1), POS.offset(2, 0, 1)),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(), POS.offset(1, 0, 3), null),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(), POS.offset(1, 0, 5), null));
    }
}
