package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ShrinesBetweenRoadsStoryArcTest {
    @Test
    void lastRelayNeedsAnIncidentWhenInteractiveIncidentsAreEnabled() {
        assertFalse(ShrinesBetweenRoadsStoryArc.lastRelayComplete(true, false, true));
        assertTrue(ShrinesBetweenRoadsStoryArc.lastRelayComplete(true, true, true));
    }

    @Test
    void mapOnlyModeDoesNotBlockStoryCompletion() {
        assertTrue(ShrinesBetweenRoadsStoryArc.lastRelayComplete(true, false, false));
    }

    @Test
    void lastRelayAlwaysNeedsItsFreshContract() {
        assertFalse(ShrinesBetweenRoadsStoryArc.lastRelayComplete(false, true, true));
        assertFalse(ShrinesBetweenRoadsStoryArc.lastRelayComplete(false, false, false));
    }
}
