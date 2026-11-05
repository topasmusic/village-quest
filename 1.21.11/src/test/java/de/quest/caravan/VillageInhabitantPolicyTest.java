package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class VillageInhabitantPolicyTest {
    @Test
    void loadedVillagerConfirmsEvenWhenOtherDistrictsAreUnloaded() {
        assertEquals(VillageInhabitantPolicy.Status.INHABITED,
                VillageInhabitantPolicy.classify(true, 0, 20, 0, 20, (x, z) -> false));
    }

    @Test
    void emptyFullyLoadedVillageIsAbandoned() {
        assertEquals(VillageInhabitantPolicy.Status.ABANDONED,
                VillageInhabitantPolicy.classify(false, 0, 1, 0, 1, (x, z) -> true));
    }

    @Test
    void emptyVillageWithUnloadedDistrictRetriesUntilLoaded() {
        assertEquals(VillageInhabitantPolicy.Status.UNKNOWN,
                VillageInhabitantPolicy.classify(false, 0, 20, 0, 20,
                        (x, z) -> x < 10));
        assertEquals(VillageInhabitantPolicy.Status.INHABITED,
                VillageInhabitantPolicy.classify(true, 0, 20, 0, 20,
                        (x, z) -> x < 10));
    }

    @Test
    void emptyVillageBecomesAbandonedWhenRemainingChunksLoad() {
        assertEquals(VillageInhabitantPolicy.Status.UNKNOWN,
                VillageInhabitantPolicy.classify(false, 0, 2, 0, 1,
                        (x, z) -> x != 2));
        assertEquals(VillageInhabitantPolicy.Status.ABANDONED,
                VillageInhabitantPolicy.classify(false, 0, 2, 0, 1,
                        (x, z) -> true));
    }
}
