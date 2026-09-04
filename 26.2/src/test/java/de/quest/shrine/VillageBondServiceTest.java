package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import de.quest.data.PlayerQuestData;
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

    @Test
    void ninthHistoricalVillageKeepsIndependentIdentityAndLookup() {
        PlayerQuestData data = new PlayerQuestData();
        for (int i = 0; i < 8; i++) {
            assertEquals(i, VillageBondService.ensureVillageRecord(
                    data, i * 32, i * -48, VillageBondType.values()[i % VillageBondType.values().length]));
        }
        int villageEightX = data.getTradeRouteInt(VillageBondService.villageKey(7, "x"));
        int villageEightZ = data.getTradeRouteInt(VillageBondService.villageKey(7, "z"));
        int villageEightType = data.getTradeRouteInt(VillageBondService.villageKey(7, "type"));

        assertEquals(8, VillageBondService.ensureVillageRecord(data, 8 * 32, 8 * -48, VillageBondType.ARCHIVE));

        assertEquals(9, VillageBondService.historicalVillageCount(data));
        assertEquals(8, VillageBondService.findVillage(data, 8 * 32, 8 * -48));
        assertEquals(villageEightX, data.getTradeRouteInt(VillageBondService.villageKey(7, "x")));
        assertEquals(villageEightZ, data.getTradeRouteInt(VillageBondService.villageKey(7, "z")));
        assertEquals(villageEightType, data.getTradeRouteInt(VillageBondService.villageKey(7, "type")));
        assertEquals(8 * 32, data.getTradeRouteInt(VillageBondService.villageKey(8, "x")));
        assertEquals(8 * -48, data.getTradeRouteInt(VillageBondService.villageKey(8, "z")));
        assertEquals(VillageBondType.ARCHIVE.id() + 1,
                data.getTradeRouteInt(VillageBondService.villageKey(8, "type")));
    }

    @Test
    void historicalVillageSafetyLimitRefusesWithoutAliasing() {
        PlayerQuestData data = new PlayerQuestData();
        for (int i = 0; i < VillageBondService.MAX_HISTORICAL_VILLAGES; i++) {
            assertEquals(i, VillageBondService.ensureVillageRecord(data, i * 32, i * 48, VillageBondType.GRANARY));
        }
        int last = VillageBondService.MAX_HISTORICAL_VILLAGES - 1;
        int lastX = data.getTradeRouteInt(VillageBondService.villageKey(last, "x"));
        int lastZ = data.getTradeRouteInt(VillageBondService.villageKey(last, "z"));

        assertEquals(-1, VillageBondService.ensureVillageRecord(
                data, VillageBondService.MAX_HISTORICAL_VILLAGES * 32,
                VillageBondService.MAX_HISTORICAL_VILLAGES * 48, VillageBondType.FORGE));
        assertEquals(VillageBondService.MAX_HISTORICAL_VILLAGES,
                VillageBondService.historicalVillageCount(data));
        assertEquals(lastX, data.getTradeRouteInt(VillageBondService.villageKey(last, "x")));
        assertEquals(lastZ, data.getTradeRouteInt(VillageBondService.villageKey(last, "z")));
    }
}
