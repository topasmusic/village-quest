package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.shrine.VillageBondType;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class LivingVillageNetworkStateTest {
    private static final UUID OWNER = UUID.fromString("b95a13ea-97f1-40a0-8aa5-8f2154677d13");

    @Test
    void legacyVillageMigrationIsStableAndIdempotent() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        var first = state.ensureVillage(OWNER, 0, 120, -40, VillageBondType.FORGE);
        var second = state.ensureVillage(OWNER, 0, 120, -40, VillageBondType.FORGE);

        assertEquals(LivingVillageNetworkState.CURRENT_SCHEMA_VERSION, state.schemaVersion());
        assertEquals(1, state.villagesView().size());
        assertEquals(first, second);
        assertEquals(50, first.support());
        assertEquals(VillageCondition.STABLE, first.condition());
        assertEquals(VillageBondType.FORGE, first.need().villageType());
    }

    @Test
    void supportCycleAdvancesNeedWithoutChangingVillageIdentity() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        var initial = state.ensureVillage(OWNER, 1, 300, 400, VillageBondType.APIARY);
        VillageNeed initialNeed = initial.need();

        assertFalse(state.addSupport(OWNER, 1, 24, 50).needAdvanced());
        assertFalse(state.addSupport(OWNER, 1, 24, 51).needAdvanced());
        var completed = state.addSupport(OWNER, 1, 24, 52);

        assertTrue(completed.needAdvanced());
        assertEquals(55, completed.village().support());
        assertEquals(1, completed.village().cyclesCompleted());
        assertEquals(52, completed.village().lastInteractionDay());
        assertEquals(VillageBondType.APIARY, completed.village().type());
        assertNotEquals(initialNeed, completed.village().need());
    }

    @Test
    void saveLoadRoundTripPreservesVersionedVillageState() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        state.ensureVillage(OWNER, 2, -250, 725, VillageBondType.ARCHIVE);
        state.addSupport(OWNER, 2, 14, 900);

        CompoundTag saved = LivingVillageNetworkState.toNbt(state);
        LivingVillageNetworkState loaded = LivingVillageNetworkState.fromNbt(saved);

        assertEquals(LivingVillageNetworkState.CURRENT_SCHEMA_VERSION,
                saved.getIntOr("schemaVersion", 0));
        assertEquals(state.snapshot(OWNER, 2), loaded.snapshot(OWNER, 2));
    }

    @Test
    void ninthVillageKeepsIndependentNetworkStateAcrossSaveLoad() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        for (int i = 0; i < 9; i++) {
            state.ensureVillage(OWNER, i, 1_000 + i * 32, -2_000 - i * 48,
                    VillageBondType.values()[i % VillageBondType.values().length]);
        }
        state.addSupport(OWNER, 7, 5, 100);
        state.addSupport(OWNER, 8, 19, 101);

        LivingVillageNetworkState loaded = LivingVillageNetworkState.fromNbt(
                LivingVillageNetworkState.toNbt(state));
        var villageEight = loaded.snapshot(OWNER, 7).orElseThrow();
        var villageNine = loaded.snapshot(OWNER, 8).orElseThrow();

        assertEquals(9, loaded.villagesView().size());
        assertEquals(1_224, villageEight.x());
        assertEquals(1_256, villageNine.x());
        assertNotEquals(villageEight.support(), villageNine.support());
        assertNotEquals(villageEight.type(), villageNine.type());
    }

    @Test
    void unversionedDataGetsSafeDefaultsAndInvalidOwnersAreIgnored() {
        CompoundTag legacy = new CompoundTag();
        ListTag entries = new ListTag();
        CompoundTag valid = new CompoundTag();
        valid.putString("owner", OWNER.toString());
        valid.putInt("index", 3);
        valid.putInt("x", 7);
        valid.putInt("z", 11);
        valid.putInt("type", VillageBondType.PASTURE.id());
        entries.add(valid);
        CompoundTag invalid = new CompoundTag();
        invalid.putString("owner", "not-a-uuid");
        invalid.putInt("index", 4);
        entries.add(invalid);
        legacy.put("villages", entries);

        LivingVillageNetworkState migrated = LivingVillageNetworkState.fromNbt(legacy);
        var village = migrated.snapshot(OWNER, 3).orElseThrow();

        assertEquals(1, migrated.villagesView().size());
        assertEquals(50, village.support());
        assertEquals(VillageCondition.STABLE, village.condition());
        assertEquals(VillageBondType.PASTURE, village.need().villageType());
    }

    @Test
    void ownerAndFullResetsCannotLeaveOrphanedNetworkState() {
        UUID secondOwner = UUID.fromString("77fcf29c-2351-423a-956c-d9aa7370dd74");
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        state.ensureVillage(OWNER, 0, 10, 20, VillageBondType.GRANARY);
        state.ensureVillage(OWNER, 1, 30, 40, VillageBondType.FORGE);
        state.ensureVillage(secondOwner, 0, 50, 60, VillageBondType.ARCHIVE);

        assertEquals(2, state.removeOwner(OWNER));
        assertTrue(state.snapshot(OWNER, 0).isEmpty());
        assertTrue(state.snapshot(secondOwner, 0).isPresent());

        state.resetAllProgress();
        assertTrue(state.villagesView().isEmpty());
    }

    @Test
    void routeArrivalsCreateBoundedEnergyAndFailuresStayRepairable() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        state.ensureVillage(OWNER, 0, 10, 20, VillageBondType.GRANARY);

        assertEquals(0, state.recordRouteArrival(OWNER, 0, 5, 1, 10).earnedCharges());
        assertEquals(0, state.recordRouteArrival(OWNER, 0, 5, 1, 11).earnedCharges());
        var third = state.recordRouteArrival(OWNER, 0, 5, 1, 12);
        assertEquals(1, third.earnedCharges());
        assertEquals(0, third.village().energyProgress());
        assertEquals(3, third.village().routeArrivals());

        var strained = state.addStrain(OWNER, 0, 40, 13);
        assertTrue(strained.support() >= 10);
        assertEquals(1, strained.routeFailures());
        assertTrue(state.addSupport(OWNER, 0, 24, 14).village().support() > strained.support());
    }

    @Test
    void networkPrestigeIsBoundedAndSpecializationIsOneTime() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        state.ensureVillage(OWNER, 0, 10, 20, VillageBondType.GRANARY);
        for (int i = 0; i < 18; i++) state.addSupport(OWNER, 0, 24, i);
        assertTrue(state.network(OWNER).rank() >= 2);
        assertTrue(state.specialize(OWNER, NetworkSpecialization.STEWARD));
        assertFalse(state.specialize(OWNER, NetworkSpecialization.COURIER));

        LivingVillageNetworkState loaded = LivingVillageNetworkState.fromNbt(
                LivingVillageNetworkState.toNbt(state));
        assertEquals(NetworkSpecialization.STEWARD, loaded.network(OWNER).specialization());
    }

    @Test
    void adminFixtureControlsAreExactBoundedAndResetSpecialization() {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        state.ensureVillage(OWNER, 0, 10, 20, VillageBondType.GRANARY);

        assertEquals(0, state.adminSetVillageSupport(OWNER, 0, -20).support());
        assertEquals(VillageCondition.CRISIS, state.snapshot(OWNER, 0).orElseThrow().condition());
        assertEquals(1_000_000, state.adminSetRenown(OWNER, Integer.MAX_VALUE).renown());
        assertTrue(state.specialize(OWNER, NetworkSpecialization.STEWARD));

        var reset = state.adminSetRenown(OWNER, 199);
        assertEquals(199, reset.renown());
        assertEquals(2, reset.rank());
        assertEquals(NetworkSpecialization.NONE, reset.specialization());
    }
}
