package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class CaravanAggressionTest {
    @Test void guardTargetsOnlyProvenAttackersInsideTheFixedRouteLeash() {
        UUID connection = UUID.randomUUID(), attacker = UUID.randomUUID(), innocent = UUID.randomUUID();
        var response = CaravanProtectionService.start(connection, attacker, new BlockPos(0, 64, 0), 100);
        assertTrue(CaravanProtectionService.mayChase(response, attacker, new BlockPos(1, 64, 0), new BlockPos(24, 64, 0), 101, true));
        assertFalse(CaravanProtectionService.mayChase(response, innocent, BlockPos.ZERO, new BlockPos(1, 64, 0), 101, true));
        assertFalse(CaravanProtectionService.mayChase(response, attacker, new BlockPos(1, 64, 0), new BlockPos(25, 64, 0), 101, true));
        assertFalse(CaravanProtectionService.mayChase(response, attacker, new BlockPos(25, 64, 0), new BlockPos(1, 64, 0), 101, true));
        assertFalse(CaravanProtectionService.mayChase(response, attacker, new BlockPos(1, 64, 0), new BlockPos(2, 64, 0), 101, false));
    }
    @Test void responseExpiresAfterTwentySecondsAndANewAttackerDoesNotMoveTheLeash() {
        UUID connection = UUID.randomUUID(), attacker = UUID.randomUUID(), other = UUID.randomUUID();
        var origin = new BlockPos(0, 64, 0);
        var response = CaravanProtectionService.start(connection, attacker, origin, 100);
        assertTrue(CaravanProtectionService.active(response, 499)); assertFalse(CaravanProtectionService.active(response, 500));
        response = CaravanProtectionService.renew(response, other, 300);
        assertEquals(origin, response.anchor()); assertTrue(CaravanProtectionService.active(response, 699));
        assertFalse(CaravanProtectionService.mayChase(response, attacker, origin, origin, 501, true));
        assertTrue(CaravanProtectionService.mayChase(response, other, origin, origin, 501, true));
    }
    @Test void warningRetreatsForThirtySecondsWithoutAuthorisingGuardDamage() {
        UUID connection = UUID.randomUUID(), attacker = UUID.randomUUID(); var origin = new BlockPos(0, 64, 0);
        CaravanProtectionService.resetRuntime(); CaravanProtectionService.warn(connection, attacker, origin, 100);
        var response = CaravanProtectionService.response(connection, 699);
        assertNotNull(response); assertFalse(CaravanProtectionService.mayChase(response, attacker, origin, origin, 101, true));
        assertNull(CaravanProtectionService.response(connection, 700)); CaravanProtectionService.resetRuntime();
    }
}
