package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomAnchorRegistryTest {
    private static final UUID ACTOR = UUID.randomUUID(), TOWN = UUID.randomUUID(), ANCHOR = UUID.randomUUID();
    private static final BlockPos POS = new BlockPos(0, 64, 0), BED = new BlockPos(2, 64, 2);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void unchangedLoadedServiceContinuesWithoutAuthorizingAStaleNewTransaction() {
        var state=state();var registry=state.anchors();bind(state,ACTOR,0,ANCHOR,POS,0);
        assertTrue(registry.maySpawn(ANCHOR,0));
        assertTrue(registry.maintainCached(ANCHOR,0,0,120));
        assertFalse(registry.maySpawn(ANCHOR,120),"New transactions still require a scan at most 40 ticks old");
        assertTrue(registry.mayTrade(ANCHOR,120),"Maintained unchanged evidence allows trades between full scans");
        assertTrue(registry.mayContinue(ANCHOR,120),"An unchanged freshly observed service must not disappear between full scans");
        assertFalse(registry.mayContinue(ANCHOR,161),"A lost loaded observation expires too");
        registry.invalidateEvidence(ANCHOR);
        assertFalse(registry.mayTrade(ANCHOR,120),"A real block change blocks new trades immediately");
        assertFalse(registry.mayContinue(ANCHOR,120),"A real block change revokes continuation immediately");
    }
    @Test void aConflictingFullScanCannotBecomeTradeEvidenceThroughCacheMaintenance() {
        var state=state(); bind(state,ACTOR,0,ANCHOR,POS,0);
        UUID other=UUID.randomUUID(), otherAnchor=UUID.randomUUID(); BlockPos otherPos=POS.offset(5,0,0), otherBed=BED.offset(5,0,0);
        state.registerCustom(other,ACTOR,VillageKey.overworld(5,0),otherPos,"Neighbor").orElseThrow();
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(ACTOR,other,0,otherAnchor,-1,otherPos,Direction.SOUTH,
                BuildingScanner.Range.BASIC,report(BuildingScanner.Role.INN,BuildingScanner.Status.VALID,otherBed),0,0));
        assertEquals(KingdomAnchorRegistry.Result.STATION_CONFLICT,state.anchors().observe(ANCHOR,0,
                report(BuildingScanner.Role.INN,BuildingScanner.Status.VALID,otherBed),10));
        assertFalse(state.anchors().maintainCached(ANCHOR,0,10,20));
        assertFalse(state.anchors().mayTrade(ANCHOR,20));
    }
    @Test void roleIsUniqueAndForeignOrStaleGuiCannotBindAnAnchor() {
        var state = state(); var registry = state.anchors();
        assertEquals(KingdomAnchorRegistry.Result.UNAUTHORIZED, bind(state, UUID.randomUUID(), 0, ANCHOR, POS, 0));
        assertEquals(KingdomAnchorRegistry.Result.STALE, bind(state, ACTOR, 10, ANCHOR, POS, 0));
        assertEquals(KingdomAnchorRegistry.Result.APPLIED, bind(state, ACTOR, 0, ANCHOR, POS, 0));
        assertEquals(KingdomAnchorRegistry.Result.ALREADY_ASSIGNED,
                bind(state, ACTOR, 0, UUID.randomUUID(), POS.offset(5, 0, 0), 0));
        assertEquals(ANCHOR, registry.official(TOWN, BuildingScanner.Role.INN).orElseThrow().id());
    }
    @Test void onlyKnownPhysicalDetachmentCanRetireAPersistedOperator() {
        var state=state(); assertEquals(KingdomAnchorRegistry.Result.APPLIED,bind(state,ACTOR,0,ANCHOR,POS,0));
        assertFalse(state.anchors().knownDetached(TOWN,BuildingScanner.Role.INN));
        state.anchors().removedPhysical(ANCHOR);
        assertTrue(state.anchors().knownDetached(TOWN,BuildingScanner.Role.INN));
        var saved=LivingKingdomsState.toNbt(state); var future=new CompoundTag(); future.putInt("schema",99);
        saved.put("anchors",future);
        assertFalse(LivingKingdomsState.fromNbt(saved).anchors().knownDetached(TOWN,BuildingScanner.Role.INN));
    }

    @Test void staleUnknownAndWrongRoleEvidenceCannotCreateAnOfficialBuilding() {
        var state = state(); var registry = state.anchors();
        assertEquals(KingdomAnchorRegistry.Result.NO_EVIDENCE,
                registry.bind(ACTOR, TOWN, 0, ANCHOR, -1, POS, Direction.SOUTH, BuildingScanner.Range.BASIC,
                        report(BuildingScanner.Role.INN, BuildingScanner.Status.UNKNOWN, BED), 0, 0));
        assertEquals(KingdomAnchorRegistry.Result.NO_EVIDENCE, bind(state, ACTOR, 0, ANCHOR, POS, 41));
        assertTrue(registry.official(TOWN, BuildingScanner.Role.INN).isEmpty());
    }

    @Test void neighbouringVillagesCannotShareBedsAndMovingKeepsIdentityAndEarnedCredit() {
        var state = state(); var registry = state.anchors();
        assertEquals(KingdomAnchorRegistry.Result.APPLIED, bind(state, ACTOR, 0, ANCHOR, POS, 0));
        UUID other = UUID.randomUUID();
        state.registerCustom(other, ACTOR, VillageKey.overworld(5, 0), new BlockPos(5, 64, 0), "Neighbor").orElseThrow();
        assertEquals(KingdomAnchorRegistry.Result.STATION_CONFLICT,
                registry.bind(ACTOR, other, 0, UUID.randomUUID(), -1, new BlockPos(5, 64, 0), Direction.SOUTH,
                        BuildingScanner.Range.BASIC, report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 0, 0));
        var first = registry.official(TOWN, BuildingScanner.Role.INN).orElseThrow();
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,
                registry.bind(ACTOR, TOWN, 0, ANCHOR, first.revision(), POS.offset(1, 0, 0), Direction.WEST,
                        BuildingScanner.Range.BASIC, report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 10, 10));
        var moved = registry.official(TOWN, BuildingScanner.Role.INN).orElseThrow();
        assertEquals(ANCHOR, moved.id()); assertTrue(moved.earned()); assertEquals(POS.offset(1, 0, 0), moved.position());
        assertEquals(KingdomAnchorRegistry.Result.STALE,
                registry.bind(ACTOR, TOWN, 0, ANCHOR, first.revision(), POS, Direction.SOUTH,
                        BuildingScanner.Range.BASIC, report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 20, 20));
    }

    @Test void reloadRetainsExclusiveStationsAndDemandsFreshObservationBeforeSpawning() {
        var state = state(); bind(state, ACTOR, 0, ANCHOR, POS, 0);
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        var registry = restored.anchors();
        assertEquals(ANCHOR, registry.stationOwner("minecraft:overworld", BED).orElseThrow());
        assertTrue(registry.official(TOWN, BuildingScanner.Role.INN).orElseThrow().earned());
        assertFalse(registry.maySpawn(ANCHOR, 9000));
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,
                registry.observe(ANCHOR, 0, report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 9000));
        assertTrue(registry.maySpawn(ANCHOR, 9000));
    }

    @Test void corruptAnchorListIsPreservedReadOnlyWithoutInventingAnEmptyRegistry() {
        var state = state(); var root = LivingKingdomsState.toNbt(state);
        var anchors = new CompoundTag(); anchors.putInt("schema", 1); anchors.putString("records", "damaged");
        root.put("anchors", anchors);
        var restored = LivingKingdomsState.fromNbt(root);
        assertEquals(KingdomAnchorRegistry.Result.READ_ONLY, bind(restored, ACTOR, 0, ANCHOR, POS, 0));
        assertEquals(anchors, LivingKingdomsState.toNbt(restored).getCompoundOrEmpty("anchors"));
    }

    @Test void detachingImmediatelySuspendsSpawnAndReloadCannotRebindTheRemovedPhysicalPosition() {
        var state = state(); bind(state, ACTOR, 0, ANCHOR, POS, 0);
        assertEquals(KingdomAnchorRegistry.Result.APPLIED, state.anchors().detach(ACTOR, ANCHOR, 0));
        assertFalse(state.anchors().maySpawn(ANCHOR, 0)); assertTrue(state.anchors().at("minecraft:overworld", POS).isEmpty());
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(restored.anchors().at("minecraft:overworld", POS).isEmpty());
        assertTrue(restored.anchors().official(TOWN, BuildingScanner.Role.INN).orElseThrow().earned());
        assertTrue(restored.anchors().stationOwner("minecraft:overworld", BED).isEmpty());
        assertEquals(KingdomAnchorRegistry.Result.NO_EVIDENCE,
                restored.anchors().observe(ANCHOR, 1, report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 10));
    }

    @Test void obsoleteScanTickCannotReplaceClaimsOrRestoreOperation() {
        var state = state(); bind(state, ACTOR, 0, ANCHOR, POS, 0);
        state.anchors().observe(ANCHOR, 0, report(BuildingScanner.Role.INN, BuildingScanner.Status.UNKNOWN, BED), 100);
        assertEquals(KingdomAnchorRegistry.Result.STALE,
                state.anchors().observe(ANCHOR, 0, report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 80));
        assertFalse(state.anchors().maySpawn(ANCHOR, 100));
    }

    @Test void futureOperationRecordIsQuarantinedAndUnknownStationFieldsSurviveReload() {
        var state = state(); bind(state, ACTOR, 0, ANCHOR, POS, 0);
        var root = LivingKingdomsState.toNbt(state);
        var record = root.getCompoundOrEmpty("anchors").getListOrEmpty("records").getCompoundOrEmpty(0);
        record.getListOrEmpty("stations").getCompoundOrEmpty(0).putString("futureStationNote", "keep");
        var restored = LivingKingdomsState.fromNbt(root);
        assertEquals("keep", LivingKingdomsState.toNbt(restored).getCompoundOrEmpty("anchors").getListOrEmpty("records")
                .getCompoundOrEmpty(0).getListOrEmpty("stations").getCompoundOrEmpty(0).getStringOr("futureStationNote", ""));
        record.getCompoundOrEmpty("operation").putInt("schema", 9);
        var future = LivingKingdomsState.fromNbt(root);
        assertTrue(future.anchors().official(TOWN, BuildingScanner.Role.INN).isEmpty());
        assertEquals(KingdomAnchorRegistry.Result.READ_ONLY, bind(future, ACTOR, 0, ANCHOR, POS, 0));
        assertEquals(root.getCompoundOrEmpty("anchors").getListOrEmpty("records"),
                LivingKingdomsState.toNbt(future).getCompoundOrEmpty("anchors").getListOrEmpty("records"));
    }

    private static LivingKingdomsState state() {
        var state = new LivingKingdomsState();
        state.registerCustom(TOWN, ACTOR, VillageKey.overworld(0, 0), POS, "Home").orElseThrow(); return state;
    }
    private static KingdomAnchorRegistry.Result bind(LivingKingdomsState state, UUID actor, long revision,
                                                     UUID id, BlockPos pos, long now) {
        return state.anchors().bind(actor, TOWN, revision, id, -1, pos, Direction.SOUTH, BuildingScanner.Range.BASIC,
                report(BuildingScanner.Role.INN, BuildingScanner.Status.VALID, BED), 0, now);
    }
    private static BuildingScanner.Report report(BuildingScanner.Role role, BuildingScanner.Status status, BlockPos bed) {
        return new BuildingScanner.Report(role, status, Map.of(BuildingScanner.Check.BED, true),
                Map.of(BuildingScanner.Feature.BED, Set.of(bed)), Set.of(POS.relative(Direction.SOUTH)));
    }
}
