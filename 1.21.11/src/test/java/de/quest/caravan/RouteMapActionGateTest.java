package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RouteMapActionGateTest {
    @Test
    void openSessionAllowsAnActionAndClosingRevokesIt() {
        RouteMapActionGate gate = new RouteMapActionGate();
        UUID player = UUID.randomUUID();
        assertFalse(gate.accept(player, 100));
        gate.open(player, 100);
        assertTrue(gate.accept(player, 100));
        gate.close(player);
        assertFalse(gate.accept(player, 110));
    }

    @Test
    void staleSessionCannotMutateUntilMapReopens() {
        RouteMapActionGate gate = new RouteMapActionGate();
        UUID player = UUID.randomUUID();
        gate.open(player, 100);
        assertFalse(gate.accept(player, 701));
        gate.open(player, 702);
        assertTrue(gate.accept(player, 702));
    }

    @Test
    void heartbeatKeepsLegitimatelyOpenMapUsable() {
        RouteMapActionGate gate = new RouteMapActionGate();
        UUID player = UUID.randomUUID();
        gate.open(player, 100);
        assertTrue(gate.heartbeat(player, 500));
        assertTrue(gate.accept(player, 800));
        assertFalse(gate.heartbeat(player, 1401));
    }

    @Test
    void repeatedActionsAreDebouncedWithoutExtendingSession() {
        RouteMapActionGate gate = new RouteMapActionGate();
        UUID player = UUID.randomUUID();
        gate.open(player, 100);
        assertTrue(gate.accept(player, 100));
        assertFalse(gate.accept(player, 100));
        assertFalse(gate.accept(player, 103));
        assertFalse(gate.accept(player, 109));
        assertTrue(gate.accept(player, 110));
        assertFalse(gate.accept(player, 701));
    }

    @Test
    void packetSpamAllowsAtMostTwoMutationsPerSecondAndNormalClicksRecover() {
        RouteMapActionGate gate = new RouteMapActionGate();
        UUID player = UUID.randomUUID();
        gate.open(player, 200);
        int accepted = 0;
        for (long tick = 200; tick < 220; tick++) {
            if (gate.accept(player, tick)) accepted++;
        }
        assertEquals(2, accepted);
        assertTrue(gate.accept(player, 220));
    }

    @Test
    void independentMinimapToggleIsDebouncedWithoutAFullMapSession() {
        RouteMapActionGate gate = new RouteMapActionGate();
        UUID player = UUID.randomUUID();
        assertTrue(gate.acceptMinimapToggle(player, 100));
        assertFalse(gate.acceptMinimapToggle(player, 101));
        assertTrue(gate.acceptMinimapToggle(player, 104));
        gate.disconnect(player);
        assertTrue(gate.acceptMinimapToggle(player, 105));
    }
}
