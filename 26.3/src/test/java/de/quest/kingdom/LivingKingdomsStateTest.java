package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class LivingKingdomsStateTest {
    private static final UUID ALICE = UUID.fromString("dd65ea8d-a004-48b4-a1fb-5c3bb7ce7bb2");
    private static final UUID BOB = UUID.fromString("bda906bf-0449-43e2-b070-d8f0d1c9e448");
    private static final UUID TOWN = UUID.fromString("b696826d-bc1f-4c4b-8204-52f87979a8fd");
    private static final VillageKey KEY = VillageKey.overworld(100, 100);

    @BeforeAll
    static void registries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void generatedLegacyContactIsIdempotentAndDeterministicWithoutPlayerOwnership() {
        LivingKingdomsState state = new LivingKingdomsState();
        var first = state.registerGenerated(KEY, new BlockPos(100, 64, 100), "Stoneford").orElseThrow();
        var repeated = state.registerGenerated(KEY, new BlockPos(100, 65, 100), "Other name").orElseThrow();
        var fresh = new LivingKingdomsState().registerGenerated(KEY,
                new BlockPos(100, 64, 100), "Stoneford").orElseThrow();
        assertEquals(first.id(), repeated.id());
        assertEquals(first.id(), fresh.id());
        assertEquals("Stoneford", repeated.name());
        assertNull(first.founder());
        assertFalse(state.canManageVillage(ALICE, first.id()));
        assertEquals(1, state.villages().size());
    }

    @Test
    void closeCustomVillagesStayDistinctAndExactLegacyAliasCannotBeStolen() {
        LivingKingdomsState state = new LivingKingdomsState();
        var first = custom(state);
        var second = state.registerCustom(UUID.randomUUID(), BOB, VillageKey.overworld(103, 100),
                new BlockPos(103, 64, 100), "Nearbrook").orElseThrow();
        assertNotEquals(first.id(), second.id());
        assertEquals(first.id(), state.byLegacy(KEY).orElseThrow().id());
        assertEquals(second.id(), state.byLegacy(VillageKey.overworld(103, 100)).orElseThrow().id());
        assertTrue(state.registerCustom(UUID.randomUUID(), BOB, KEY,
                new BlockPos(100, 64, 100), "Stolen").isEmpty());
    }

    @Test
    void renameAndUndergroundCouncilMoveKeepTheIdAndLegacyLifeKeyAcrossReload() {
        LivingKingdomsState state = new LivingKingdomsState();
        var first = custom(state);
        assertEquals(LivingKingdomsState.Mutation.APPLIED, state.rename(ALICE, TOWN, first.revision(), "  Talheim  "));
        long revision = state.village(TOWN).orElseThrow().revision();
        assertEquals(LivingKingdomsState.Mutation.APPLIED,
                state.moveCouncil(ALICE, TOWN, revision, new BlockPos(110, -20, 108)));
        LivingKingdomsState restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        var town = restored.village(TOWN).orElseThrow();
        assertEquals("Talheim", town.name());
        assertEquals(new BlockPos(110, -20, 108), town.arrival());
        assertEquals(KEY, town.legacyKey());
        assertEquals(TOWN, restored.byLegacy(KEY).orElseThrow().id());
    }

    @Test
    void foreignAndStaleActionsDoNotMutateAndRevokedDelegateCannotUseOldGui() {
        LivingKingdomsState state = new LivingKingdomsState();
        var first = custom(state);
        assertEquals(LivingKingdomsState.Mutation.UNAUTHORIZED, state.rename(BOB, TOWN, first.revision(), "No"));
        assertEquals(LivingKingdomsState.Mutation.APPLIED, state.setDelegate(ALICE, TOWN, first.revision(), BOB, true));
        long delegatedRevision = state.village(TOWN).orElseThrow().revision();
        assertEquals(LivingKingdomsState.Mutation.STALE, state.rename(BOB, TOWN, first.revision(), "Old"));
        assertEquals(LivingKingdomsState.Mutation.APPLIED, state.rename(BOB, TOWN, delegatedRevision, "Shared"));
        assertEquals(LivingKingdomsState.Mutation.APPLIED,
                state.setDelegate(ALICE, TOWN, state.village(TOWN).orElseThrow().revision(), BOB, false));
        assertEquals(LivingKingdomsState.Mutation.UNAUTHORIZED,
                state.moveCouncil(BOB, TOWN, delegatedRevision, new BlockPos(0, 64, 0)));
        assertEquals("Shared", state.village(TOWN).orElseThrow().name());
        assertEquals(new BlockPos(100, 64, 100), state.village(TOWN).orElseThrow().arrival());
    }

    @Test
    void delegateCannotDelegateAwayTheFoundersAuthority() {
        LivingKingdomsState state = new LivingKingdomsState();
        custom(state);
        state.setDelegate(ALICE, TOWN, 0, BOB, true);
        assertEquals(LivingKingdomsState.Mutation.UNAUTHORIZED,
                state.setDelegate(BOB, TOWN, 1, ALICE, false));
        LivingKingdomsState restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(restored.canManageVillage(ALICE, TOWN));
        assertTrue(restored.canManageVillage(BOB, TOWN));
    }

    @Test
    void generatedMediatorNeedsAnExplicitServerVerifiedMandate() {
        LivingKingdomsState state = new LivingKingdomsState();
        var town = state.registerGenerated(KEY, new BlockPos(100, 64, 100), "Stoneford").orElseThrow();
        assertFalse(state.recognizeMediator(town.id(), ALICE, false));
        assertFalse(state.canManageVillage(ALICE, town.id()));
        assertTrue(state.recognizeMediator(town.id(), ALICE, true));
        assertTrue(state.canManageVillage(ALICE, town.id()));
        assertFalse(state.canManageVillage(BOB, town.id()));
        assertEquals(LivingKingdomsState.Mutation.APPLIED,
                state.rename(ALICE, town.id(), state.village(town.id()).orElseThrow().revision(), "Newford"));
    }

    @Test
    void replayedRegistrationDoesNotCreateASecondTownOrTransferItsFounder() {
        LivingKingdomsState state = new LivingKingdomsState();
        var first = custom(state);
        assertEquals(first.id(), custom(state).id());
        assertTrue(state.registerCustom(TOWN, BOB, KEY, new BlockPos(100, 64, 100), "Claimed").isEmpty());
        assertEquals(ALICE, state.village(TOWN).orElseThrow().founder());
        assertEquals(1, state.villages().size());
    }

    @Test
    void futureSchemaIsPreservedByteForByteAndRefusesWrites() {
        CompoundTag future = new CompoundTag();
        future.putInt("schemaVersion", 999);
        future.putString("newerData", "keep me");
        LivingKingdomsState state = LivingKingdomsState.fromNbt(future);
        assertTrue(state.readOnly());
        assertTrue(state.registerCustom(TOWN, ALICE, KEY, new BlockPos(100, 64, 100), "No").isEmpty());
        assertEquals(future, LivingKingdomsState.toNbt(state));
    }

    @Test
    void malformedVillageEntryIsRetainedWhileHealthyRecordsRemainUsable() {
        LivingKingdomsState state = new LivingKingdomsState();
        custom(state);
        CompoundTag root = LivingKingdomsState.toNbt(state);
        CompoundTag bad = new CompoundTag();
        bad.putString("id", "invalid-id");
        bad.putString("unknownPayload", "preserve this original record");
        root.getListOrEmpty("villages").add(bad);
        LivingKingdomsState restored = LivingKingdomsState.fromNbt(root);
        assertEquals(TOWN, restored.byLegacy(KEY).orElseThrow().id());
        assertEquals(LivingKingdomsState.Mutation.APPLIED, restored.rename(ALICE, TOWN, 0, "Repaired name"));
        ListTag saved = LivingKingdomsState.toNbt(restored).getListOrEmpty("villages");
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(bad::equals));
    }

    @Test
    void blankOrControlContainingNamesAreRejectedWithoutRevisionChanges() {
        LivingKingdomsState state = new LivingKingdomsState();
        custom(state);
        assertEquals(LivingKingdomsState.Mutation.INVALID, state.rename(ALICE, TOWN, 0, "\n"));
        assertEquals(LivingKingdomsState.Mutation.INVALID, state.rename(ALICE, TOWN, 0, "Bad\u0000name"));
        assertEquals("Stoneford", state.village(TOWN).orElseThrow().name());
        assertEquals(0, state.village(TOWN).orElseThrow().revision());
    }

    private static LivingKingdomsState.VillageView custom(LivingKingdomsState state) {
        return state.registerCustom(TOWN, ALICE, KEY, new BlockPos(100, 64, 100), "Stoneford").orElseThrow();
    }

    @Test
    void nonCompoundVillagePayloadIsPreservedAndNeverRewrittenAsEmptyRecords() {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        ListTag entries = new ListTag();
        entries.add(StringTag.valueOf("unexpected original payload"));
        root.put("villages", entries);
        LivingKingdomsState state = LivingKingdomsState.fromNbt(root);
        assertTrue(state.readOnly());
        assertEquals(root, LivingKingdomsState.toNbt(state));
    }

    @Test
    void invalidLegacyCoordinateCannotCreateDataThatQuarantinesItselfOnReload() {
        LivingKingdomsState state = new LivingKingdomsState();
        assertTrue(state.registerCustom(TOWN, ALICE, VillageKey.overworld(Integer.MIN_VALUE, 100),
                new BlockPos(100, 64, 100), "No").isEmpty());
        assertTrue(state.villages().isEmpty());
    }

    @Test
    void exhaustedRevisionCannotWrapAroundThroughMediatorAcceptance() {
        LivingKingdomsState state = new LivingKingdomsState();
        var town = state.registerGenerated(KEY, new BlockPos(100, 64, 100), "Stoneford").orElseThrow();
        CompoundTag root = LivingKingdomsState.toNbt(state);
        root.getListOrEmpty("villages").getCompoundOrEmpty(0).putLong("revision", Long.MAX_VALUE);
        LivingKingdomsState restored = LivingKingdomsState.fromNbt(root);
        assertFalse(restored.recognizeMediator(town.id(), ALICE, true));
        assertEquals(Long.MAX_VALUE, restored.village(town.id()).orElseThrow().revision());
        assertEquals(root, LivingKingdomsState.toNbt(restored));
    }
}
