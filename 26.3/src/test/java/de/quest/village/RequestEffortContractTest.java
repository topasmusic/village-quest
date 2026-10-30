package de.quest.village;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.config.VillageQuestServerConfig.AdventureProfile;
import de.quest.shrine.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** A stone-only survival start must not need bees, livestock, potatoes or a route to help. */
final class RequestEffortContractTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void everyIdentityKeepsAnOrdinaryStoneAlternativeEvenAfterCompletingItLastCycle() {
        for (var type : VillageBondType.values())
            for (var condition : VillageCondition.values())
                for (var profile : AdventureProfile.values())
                    for (var bond : VillageBondLevel.values())
                        for (int seed : new int[]{-31, 0, 1, 17, Integer.MAX_VALUE}) {
                            var current = VillageRequestType.forVillage(type, seed);
                            var offers = VillageRequestGenerator.generate(type, null, current, null,
                                    bond, condition, profile, seed, 0, 1.0);
                            assertEquals(3, offers.size());
                            var stone = offers.stream().filter(o -> o.request().item() == Items.COBBLESTONE)
                                    .findFirst().orElseThrow(() -> new AssertionError("No basic supply for " + type));
                            assertTrue(stone.amount() > 0 && stone.amount() <= 44, "At most a modest mining trip in any profile");
                            assertEquals(type, stone.request().bondType());
                            assertFalse(stone.primaryNeed(), "General upkeep must not pretend to supply the specialist need");
                            assertEquals(de.quest.economy.DeliveryEconomy.materialValue(Items.COBBLESTONE, 32) + 2,
                                    stone.request().reward());
                            var next = VillageRequestGenerator.generate(type, null, current, stone.request(),
                                    bond, condition, profile, seed, 0, 1.0);
                            assertTrue(next.stream().anyMatch(o -> o.request() == stone.request()),
                                    "Soft variety must never remove the only low-resource alternative");
                            assertEquals(3, next.stream().map(VillageRequestOffer::id).distinct().count());
                        }
    }
}
