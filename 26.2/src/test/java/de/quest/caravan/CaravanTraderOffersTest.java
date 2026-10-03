package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.shrine.VillageBondType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaravanTraderOffersTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyVillageIdentityHasTwoSmallPositivePriceOffers() {
        for (VillageBondType type : VillageBondType.values()) {
            var offers = CaravanTraderOffers.forVillage(type);
            assertEquals(2, offers.size(), type.name());
            assertNotEquals(offers.get(0).item(), offers.get(1).item());
            for (var offer : offers) {
                assertTrue(offer.count() >= 1 && offer.count() <= 8);
                assertTrue(offer.silvermarks() >= 1 && offer.silvermarks() <= 4);
            }
        }
    }
}
