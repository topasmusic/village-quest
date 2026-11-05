package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageLifeStateTest {
    private static final VillageLifeState.VillageKey STONEFORD =
            VillageLifeState.VillageKey.overworld(320, -144);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void oldVillageRemainsOperationalUntilLoadedEvidenceConfirmsAbandonment() {
        VillageLifeState state = new VillageLifeState();

        assertEquals(VillageLifeState.Status.ACTIVE, state.status(STONEFORD));
        assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.UNKNOWN, 0).becameAbandoned());
        assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 100).becameAbandoned());
        assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 100).becameAbandoned());
        assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 300).becameAbandoned());
        assertTrue(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 500).becameAbandoned());
        assertEquals(VillageLifeState.Status.ABANDONED, state.status(STONEFORD));
        assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 700).becameAbandoned());
    }

    @Test
    void villagerReturnOrUnknownChunksCancelAnUnconfirmedEmptySequence() {
        VillageLifeState state = new VillageLifeState();
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 100);
        state.observe(STONEFORD, VillageLifeState.Evidence.INHABITED, 300);
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 500);
        state.observe(STONEFORD, VillageLifeState.Evidence.UNKNOWN, 700);

        assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 900).becameAbandoned());
        assertEquals(VillageLifeState.Status.ACTIVE, state.status(STONEFORD));
    }

    @Test
    void confirmedAbandonmentIsWorldSharedAndSurvivesSaveLoad() {
        VillageLifeState state = new VillageLifeState();
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 100);
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 300);
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 500);

        VillageLifeState restored = VillageLifeState.fromNbt(VillageLifeState.toNbt(state));

        assertEquals(VillageLifeState.Status.ABANDONED, restored.status(STONEFORD));
        assertEquals(VillageLifeState.Status.ACTIVE,
                restored.status(VillageLifeState.VillageKey.overworld(900, 900)));
        assertFalse(restored.observe(STONEFORD, VillageLifeState.Evidence.INHABITED, 700)
                .becameAbandoned());
        assertEquals(VillageLifeState.Status.ABANDONED, restored.status(STONEFORD));
    }

    @Test
    void resettlementRequiresStableAdultVillagersAndBedsBeforeRecovery() {
        VillageLifeState state = abandonedVillage();

        assertEquals(VillageLifeState.Status.ABANDONED,
                state.observeResettlement(STONEFORD, 1, 2, true, 600).status());
        state.observeResettlement(STONEFORD, 2, 2, true, 700);
        state.observeResettlement(STONEFORD, 2, 2, false, 800);
        assertEquals(VillageLifeState.Status.ABANDONED,
                state.observeResettlement(STONEFORD, 2, 2, true, 900).status());
        assertEquals(VillageLifeState.Status.ABANDONED,
                state.observeResettlement(STONEFORD, 2, 2, true, 1000).status());
        assertTrue(state.observeResettlement(STONEFORD, 2, 2, true, 1100).becameRecovering());
        assertEquals(VillageLifeState.Status.RECOVERING, state.status(STONEFORD));
    }

    @Test
    void recoverySurvivesSaveLoadAndCompletesOnlyAfterLoadedResidence() {
        VillageLifeState state = abandonedVillage();
        for (long tick : new long[]{700, 800, 900}) {
            state.observeResettlement(STONEFORD, 2, 2, true, tick);
        }
        VillageLifeState restored = VillageLifeState.fromNbt(VillageLifeState.toNbt(state));
        assertEquals(VillageLifeState.Status.RECOVERING, restored.status(STONEFORD));

        for (long tick = 1000; tick < 2100; tick += 100) {
            assertFalse(restored.observeResettlement(STONEFORD, 3, 3, true, tick).becameActive());
        }
        assertTrue(restored.observeResettlement(STONEFORD, 3, 3, true, 2100).becameActive());
        assertEquals(VillageLifeState.Status.ACTIVE, restored.status(STONEFORD));
    }

    @Test
    void remainingSettlerDuringRecoveryDoesNotProveCompleteDepopulation() {
        VillageLifeState state = abandonedVillage();
        for (long tick : new long[]{700, 800, 900}) {
            state.observeResettlement(STONEFORD, 2, 2, true, tick);
        }
        assertEquals(VillageLifeState.Status.RECOVERING, state.status(STONEFORD));
        assertEquals(VillageLifeState.Status.RECOVERING,
                state.observeResettlement(STONEFORD, 1, 2, true, 1000).status());
    }

    @Test
    void eachConfirmedAbandonmentHasOnePersistentNotificationCycle() {
        VillageLifeState state = abandonedVillage();
        assertEquals(1, state.abandonmentCycle(STONEFORD));
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 700);
        assertEquals(1, state.abandonmentCycle(STONEFORD));
        for (long tick : new long[]{900, 1000, 1100}) {
            state.observeResettlement(STONEFORD, 2, 2, true, tick);
        }
        for (long tick = 1200; tick <= 2300; tick += 100) {
            state.observeResettlement(STONEFORD, 3, 3, true, tick);
        }
        for (long tick : new long[]{2500, 2700, 2900}) {
            state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, tick);
        }
        assertEquals(2, state.abandonmentCycle(STONEFORD));
        VillageLifeState restored = VillageLifeState.fromNbt(VillageLifeState.toNbt(state));
        assertEquals(2, restored.abandonmentCycle(STONEFORD));
    }

    @Test
    void twoSettlersNeverActivateEvenAfterManyLoadedObservations() {
        VillageLifeState state = recoveringVillage();
        for (long tick = 1000; tick <= 5000; tick += 100) {
            assertEquals(VillageLifeState.Status.RECOVERING,
                    state.observeResettlement(STONEFORD, 2, 2, true, tick).status());
        }
        for (long tick = 5100; tick < 6300; tick += 100) {
            assertFalse(state.observeResettlement(STONEFORD, 3, 3, true, tick).becameActive());
        }
        assertTrue(state.observeResettlement(STONEFORD, 3, 3, true, 6300).becameActive());
    }

    @Test
    void insufficientBedsResetRecoveryTimeWithoutDeletingRecoveryStatus() {
        VillageLifeState state = recoveringVillage();
        for (long tick = 1000; tick <= 2000; tick += 100) {
            state.observeResettlement(STONEFORD, 3, 3, true, tick);
        }
        assertEquals(VillageLifeState.Status.RECOVERING,
                state.observeResettlement(STONEFORD, 3, 2, true, 2100).status());
        for (long tick = 2200; tick <= 3300; tick += 100) {
            assertFalse(state.observeResettlement(STONEFORD, 3, 3, true, tick).becameActive());
        }
        assertTrue(state.observeResettlement(STONEFORD, 3, 3, true, 3400).becameActive());
    }

    @Test
    void emptyPopulationRequiresRepeatedEvidenceEvenWhileRecoveryChecksRun() {
        VillageLifeState state = recoveringVillage();
        for (long tick : new long[]{1000, 1100}) {
            assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, tick)
                    .becameAbandoned());
            assertEquals(VillageLifeState.Status.RECOVERING,
                    state.observeResettlement(STONEFORD, 0, 0, true, tick).status());
        }
        assertTrue(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 1200)
                .becameAbandoned());
        assertEquals(1, state.abandonmentCycle(STONEFORD));
    }

    @Test
    void unknownObservationAndLongUnloadedGapCannotActivateARecoveringVillage() {
        VillageLifeState state = recoveringVillage();
        state.observeResettlement(STONEFORD, 3, 3, true, 1000);
        state.observeResettlement(STONEFORD, 3, 3, false, 1100);
        assertFalse(state.observeResettlement(STONEFORD, 3, 3, true, 100000).becameActive());
        assertEquals(VillageLifeState.Status.RECOVERING, state.status(STONEFORD));
    }

    @Test
    void activeLegacyVillageDoesNotNeedToMeetFoundingPopulationAgain() {
        VillageLifeState state = new VillageLifeState();
        assertEquals(VillageLifeState.Status.ACTIVE,
                state.observeResettlement(STONEFORD, 1, 0, true, 1000).status());
        assertEquals(VillageLifeState.Status.ACTIVE,
                state.observeResettlement(STONEFORD, 0, 0, false, 2000).status());
    }

    private static VillageLifeState recoveringVillage() {
        VillageLifeState state = abandonedVillage();
        for (long tick : new long[]{700, 800, 900}) {
            state.observeResettlement(STONEFORD, 2, 2, true, tick);
        }
        return state;
    }

    @Test
    void futureLifeSchemaSurvivesObservationWithoutBeingDowngradedToAnEmptySave() {
        CompoundTag future = new CompoundTag();
        future.putInt("schemaVersion", 99);
        future.putString("newerPopulationData", "preserve original");
        VillageLifeState state = VillageLifeState.fromNbt(future);
        for (long tick : new long[]{100, 300, 500}) {
            assertFalse(state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, tick).becameAbandoned());
        }
        state.observeResettlement(STONEFORD, 3, 3, true, 700);
        assertEquals(future, VillageLifeState.toNbt(state));
    }

    private static VillageLifeState abandonedVillage() {
        VillageLifeState state = new VillageLifeState();
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 100);
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 300);
        state.observe(STONEFORD, VillageLifeState.Evidence.EMPTY_LOADED, 500);
        return state;
    }
}
