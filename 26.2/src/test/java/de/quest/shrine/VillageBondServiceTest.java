package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageBondServiceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void travelCostUsesShortMediumAndLongBands() {
        BlockPos origin = BlockPos.ZERO;
        assertEquals(2, VillageBondService.travelCost(origin, new BlockPos(499, 0, 0)));
        assertEquals(4, VillageBondService.travelCost(origin, new BlockPos(500, 0, 0)));
        assertEquals(4, VillageBondService.travelCost(origin, new BlockPos(1499, 0, 0)));
        assertEquals(6, VillageBondService.travelCost(origin, new BlockPos(1500, 0, 0)));
    }

    @Test
    void requestCatalogHasFourVariantsForEveryVillageType() {
        VillageRequestType[] requests = VillageRequestType.values();
        int[] expectedAmounts = {
                160, 80, 160, 80, 60, 120, 120, 40, 160, 60,
                160, 160, 80, 160, 60, 120, 60, 40, 40, 120
        };
        assertEquals(expectedAmounts.length, requests.length);
        for (VillageBondType type : VillageBondType.values()) {
            assertEquals(4, VillageRequestType.countFor(type));
        }
        for (int i = 0; i < requests.length; i++) {
            VillageRequestType request = requests[i];
            assertEquals(i, request.id());
            assertEquals(expectedAmounts[i], request.amount());
            assertTrue(request.reward() >= 24);
        }
    }

    @Test
    void requestRotationUsesEveryVariantBeforeRepeating() {
        for (VillageBondType type : VillageBondType.values()) {
            VillageRequestType start = VillageRequestType.forVillage(type, 0);
            VillageRequestType current = start;
            Set<VillageRequestType> seen = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                assertTrue(seen.add(current));
                VillageRequestType next = VillageRequestType.nextAfter(type, current);
                assertNotEquals(current, next);
                current = next;
            }
            assertEquals(start, current);
        }
    }

    @Test
    void bondLevelsRequireTwoAndEightCompletedRequests() {
        assertEquals(VillageBondLevel.KNOWN, VillageBondService.levelForCompletions(0));
        assertEquals(VillageBondLevel.KNOWN, VillageBondService.levelForCompletions(1));
        assertEquals(VillageBondLevel.TRUSTED, VillageBondService.levelForCompletions(2));
        assertEquals(VillageBondLevel.TRUSTED, VillageBondService.levelForCompletions(7));
        assertEquals(VillageBondLevel.ALLIED, VillageBondService.levelForCompletions(8));
    }

    @Test
    void dailyRequestGateReopensOnlyOnANewResetDay() {
        assertTrue(VillageBondService.requestAvailable(0, 20_000));
        assertFalse(VillageBondService.requestAvailable(20_000, 20_000));
        assertTrue(VillageBondService.requestAvailable(20_000, 20_001));
    }
}
