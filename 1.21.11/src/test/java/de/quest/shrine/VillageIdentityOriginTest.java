package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class VillageIdentityOriginTest {
    @Test
    void professionAndBiomeReasonsFollowTheExistingClassificationOrder() {
        assertEquals(VillageBondType.FORGE,
                VillageIdentityOrigin.classify(1, 2, 0, 0, "minecraft:plains").type());
        assertEquals("smiths",
                VillageIdentityOrigin.classify(1, 2, 0, 0, "minecraft:plains").origin());
        assertEquals("scholars",
                VillageIdentityOrigin.classify(1, 0, 0, 2, "minecraft:plains").origin());
        assertEquals("blooms",
                VillageIdentityOrigin.classify(0, 0, 0, 0, "minecraft:meadow").origin());
        assertEquals("farmers",
                VillageIdentityOrigin.classify(2, 0, 0, 0, "minecraft:plains").origin());
        assertEquals("general",
                VillageIdentityOrigin.classify(0, 0, 0, 0, "minecraft:plains").origin());
    }
}
