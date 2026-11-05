package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class BuildingSessionPolicyTest {
    private static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), TOWN = UUID.randomUUID();
    private static final String DIM = "minecraft:overworld";
    @Test void sessionRejectsAnotherPlayerAnotherDimensionDistantExpiredAndReplayedActions() {
        var session = new BuildingSessionPolicy(ALICE, DIM, BlockPos.ZERO, 100);
        assertEquals(BuildingSessionPolicy.Access.ALLOWED, session.validate(ALICE, DIM, new BlockPos(7, 0, 0), 101, 0));
        assertEquals(BuildingSessionPolicy.Access.INVALID, session.validate(BOB, DIM, BlockPos.ZERO, 101, 0));
        assertEquals(BuildingSessionPolicy.Access.INVALID, session.validate(ALICE, "minecraft:the_nether", BlockPos.ZERO, 101, 0));
        assertEquals(BuildingSessionPolicy.Access.INVALID, session.validate(ALICE, DIM, new BlockPos(9, 0, 0), 101, 0));
        assertEquals(BuildingSessionPolicy.Access.EXPIRED, session.validate(ALICE, DIM, BlockPos.ZERO, 1301, 0));
        session.changed(); assertEquals(BuildingSessionPolicy.Access.STALE, session.validate(ALICE, DIM, BlockPos.ZERO, 102, 0));
    }
    @Test void explicitSelectionIsRestrictedToTheOfferedSnapshotAndCannotForgeRevision() {
        var session = new BuildingSessionPolicy(ALICE, DIM, BlockPos.ZERO, 0);
        session.offer(Map.of(TOWN, 7L));
        assertFalse(session.select(UUID.randomUUID())); assertTrue(session.select(TOWN));
        assertEquals(TOWN, session.selected()); assertEquals(7, session.villageRevision());
        assertFalse(session.select(UUID.randomUUID())); assertEquals(TOWN, session.selected());
    }
    @Test void completedGuiCloseAndExcessiveCandidateListsCannotRetainAuthority() {
        var session = new BuildingSessionPolicy(ALICE, DIM, BlockPos.ZERO, 0);
        var candidates = new java.util.HashMap<UUID, Long>(); for (int n = 0; n < 9; n++) candidates.put(UUID.randomUUID(), 0L);
        assertThrows(IllegalArgumentException.class, () -> session.offer(candidates));
        session.close(); assertEquals(BuildingSessionPolicy.Access.INVALID, session.validate(ALICE, DIM, BlockPos.ZERO, 1, 0));
    }
    @Test void officialAnchorMustStillMatchThePrivateSnapshotBeforeAnotherPlayerCanMoveOrReleaseIt() {
        var session = new BuildingSessionPolicy(ALICE, DIM, BlockPos.ZERO, 0);
        UUID anchor = UUID.randomUUID(); session.offerAnchor(anchor, 4);
        long seen = session.revision();
        assertTrue(session.matchesAnchor(anchor, 4));
        assertFalse(session.matchesAnchor(anchor, 5));
        assertFalse(session.matchesAnchor(UUID.randomUUID(), 4));
        session.offerAnchor(anchor, 5);
        assertEquals(BuildingSessionPolicy.Access.STALE, session.validate(ALICE, DIM, BlockPos.ZERO, 1, seen));
        long current = session.revision(); session.offerAnchor(anchor, 5);
        assertEquals(current, session.revision(), "Repeated unchanged views must not invalidate clicks");
        session.offerAnchor(BuildingSessionPolicy.NONE, -1);
        assertFalse(session.matchesAnchor(anchor, 5));
        assertTrue(session.matchesAnchor(BuildingSessionPolicy.NONE, -1));
    }
}
