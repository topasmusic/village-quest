package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.config.VillageQuestServerConfig.AdventureProfile;
import de.quest.shrine.VillageBondLevel;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import org.junit.jupiter.api.Test;

final class VillageRequestGeneratorTest {
    @Test
    void boardAlwaysOffersThreeChoicesAndPrioritizesTheCurrentNeed() {
        var offers = VillageRequestGenerator.generate(VillageBondType.GRANARY,
                VillageNeed.GRANARY_SEED_RESERVES, VillageRequestType.GRANARY_BREAD,
                VillageRequestType.GRANARY_POTATOES, VillageBondLevel.KNOWN,
                VillageCondition.STRAINED, AdventureProfile.STANDARD, 7, 0, 1.0);

        assertEquals(3, offers.size());
        assertEquals(3, offers.stream().map(VillageRequestOffer::id).distinct().count());
        assertTrue(offers.stream().filter(VillageRequestOffer::primaryNeed).count() >= 2);
        assertFalse(offers.stream().anyMatch(offer -> offer.request() == VillageRequestType.GRANARY_POTATOES));
    }

    @Test
    void profilesAndTrustChangeEffortWithoutChangingTheCatalog() {
        var relaxed = VillageRequestGenerator.generate(VillageBondType.FORGE,
                VillageNeed.FORGE_FUEL_AND_ORE, VillageRequestType.FORGE_COAL, null,
                VillageBondLevel.ALLIED, VillageCondition.STABLE, AdventureProfile.RELAXED,
                0, 0, 1.0).getFirst();
        var hardened = VillageRequestGenerator.generate(VillageBondType.FORGE,
                VillageNeed.FORGE_FUEL_AND_ORE, VillageRequestType.FORGE_COAL, null,
                VillageBondLevel.KNOWN, VillageCondition.STABLE, AdventureProfile.HARDENED,
                0, 0, 1.0).getFirst();

        assertEquals(relaxed.request(), hardened.request());
        assertTrue(relaxed.amount() < hardened.amount());
        assertTrue(relaxed.support() > LivingVillageNetworkService.MATCHING_DELIVERY_SUPPORT);
    }
}
