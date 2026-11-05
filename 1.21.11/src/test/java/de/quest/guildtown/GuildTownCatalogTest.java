package de.quest.guildtown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GuildTownCatalogTest {
    @Test
    void fiveStoriesCoverEveryVillageIdentity() {
        assertEquals(5, GuildTownStory.values().length);
        Set<?> identities = new HashSet<>(java.util.Arrays.stream(GuildTownStory.values())
                .map(GuildTownStory::villageType).toList());
        assertEquals(5, identities.size());
    }

    @Test
    void tenCommissionsCoverEveryUnorderedPairExactlyOnce() {
        assertEquals(10, GuildTownCommission.values().length);
        Set<String> pairs = new HashSet<>();
        Set<String> mechanicRecipes = new HashSet<>();
        for (GuildTownCommission commission : GuildTownCommission.values()) {
            int low = Math.min(commission.first().id(), commission.second().id());
            int high = Math.max(commission.first().id(), commission.second().id());
            assertTrue(pairs.add(low + ":" + high));
            assertTrue(mechanicRecipes.add(commission.firstMechanic() + ":" + commission.secondMechanic()));
            assertTrue(commission.deliveryAmount() > 0);
            assertFalse(commission.firstMechanic().name().contains("KILL"));
            assertFalse(commission.secondMechanic().name().contains("KILL"));
        }
        assertEquals(10, pairs.size());
    }
}
