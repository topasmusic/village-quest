package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.quest.shrine.VillageBondType;
import org.junit.jupiter.api.Test;

class RegionalDispatchCommissionTest {
    @Test
    void sameIdentityPairsHaveDistinctLocalThemes() {
        for (VillageBondType type : VillageBondType.values()) {
            assertEquals("same_" + type.name().toLowerCase(java.util.Locale.ROOT),
                    RegionalDispatchCommission.key(type, type));
        }
        assertNotEquals(RegionalDispatchCommission.key(VillageBondType.GRANARY, VillageBondType.GRANARY),
                RegionalDispatchCommission.key(VillageBondType.FORGE, VillageBondType.FORGE));
    }

    @Test
    void mixedPairUsesDestinationNeedAndKeepsTheTwoStopsDistinct() {
        assertEquals("to_forge", RegionalDispatchCommission.key(
                VillageBondType.GRANARY, VillageBondType.FORGE));
        assertEquals("to_granary", RegionalDispatchCommission.key(
                VillageBondType.FORGE, VillageBondType.GRANARY));
    }
}
