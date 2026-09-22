package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildCornerLandmarkStateTest {
    private static final GuildCornerLandmarkState.VillageKey VILLAGE =
            GuildCornerLandmarkState.VillageKey.overworld(320, -144);
    private static final BlockPos POST = new BlockPos(326, 71, -139);
    private static final GuildCornerLandmarkState.Footprint FOOTPRINT =
            new GuildCornerLandmarkState.Footprint(324, 70, -141, 329, 74, -136);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void oneWorldGlobalReservationWinsAcrossRepeatedPlayersAndVisits() {
        GuildCornerLandmarkState state = new GuildCornerLandmarkState();
        var first = state.reserve(VILLAGE, POST, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.PLAINS, FOOTPRINT);
        var second = state.reserve(VILLAGE, POST.offset(20, 0, 20),
                GuildCornerLandmarkState.CornerFacing.SOUTH,
                GuildCornerLandmarkState.CornerStyle.DESERT,
                GuildCornerLandmarkState.Footprint.singleBlock(POST.offset(20, 0, 20)));

        assertTrue(first.accepted());
        assertTrue(first.created());
        assertTrue(second.accepted());
        assertFalse(second.created());
        assertEquals(1, state.size());
        assertEquals(POST, second.landmark().cornerPos());
        assertEquals(GuildCornerLandmarkState.CornerStyle.PLAINS, second.landmark().style());
    }

    @Test
    void globalLandmarkAndPersonalContactsRemainIndependent() {
        GuildCornerLandmarkState state = new GuildCornerLandmarkState();
        PlayerQuestData firstPlayer = new PlayerQuestData();
        PlayerQuestData secondPlayer = new PlayerQuestData();
        state.reserve(VILLAGE, POST, GuildCornerLandmarkState.CornerFacing.EAST,
                GuildCornerLandmarkState.CornerStyle.PLAINS, FOOTPRINT);
        VillageContactService.establish(firstPlayer, VILLAGE.anchorX(), VILLAGE.anchorZ(),
                VillageBondType.GRANARY);

        assertEquals(1, state.size());
        assertEquals(1, VillageContactService.contactCount(firstPlayer));
        assertEquals(0, VillageContactService.contactCount(secondPlayer));
        assertEquals(0, firstPlayer.getTradeRouteInt("route_count"));
        assertEquals(0, secondPlayer.getTradeRouteInt("route_count"));
    }

    @Test
    void lifecycleIsPendingThenGeneratedThenTerminallyRemoved() {
        GuildCornerLandmarkState state = stateWithReservation();

        assertTrue(state.markGenerated(VILLAGE));
        assertFalse(state.markGenerated(VILLAGE));
        assertTrue(state.markRemovedByPlayer("minecraft:overworld", POST));
        assertFalse(state.markRemovedByPlayer(VILLAGE));

        var revisit = state.reserve(VILLAGE, POST.offset(2, 0, 0),
                GuildCornerLandmarkState.CornerFacing.WEST,
                GuildCornerLandmarkState.CornerStyle.TAIGA,
                GuildCornerLandmarkState.Footprint.singleBlock(POST.offset(2, 0, 0)));
        assertFalse(revisit.created());
        assertEquals(GuildCornerLandmarkState.LandmarkStatus.REMOVED_BY_PLAYER,
                revisit.landmark().status());
        assertEquals(POST, revisit.landmark().cornerPos());
    }

    @Test
    void partialLoadRetriesAreDelayedBoundedAndRemainManuallyRecoverable() {
        GuildCornerLandmarkState state = stateWithReservation();
        assertTrue(state.canAttemptAutomaticPlacement(VILLAGE, 100));

        long time = 100;
        for (int attempt = 1; attempt <= GuildCornerLandmarkState.MAX_RETRY_ATTEMPTS; attempt++) {
            var retry = state.scheduleRetry(VILLAGE, time);
            assertTrue(retry.scheduled());
            assertEquals(attempt, retry.landmark().retryAttempts());
            assertFalse(state.canAttemptAutomaticPlacement(VILLAGE, time));
            time = retry.landmark().retryAfterGameTime();
            if (attempt < GuildCornerLandmarkState.MAX_RETRY_ATTEMPTS) {
                assertTrue(state.canAttemptAutomaticPlacement(VILLAGE, time));
            }
        }

        assertFalse(state.scheduleRetry(VILLAGE, time).scheduled());
        assertFalse(state.canAttemptAutomaticPlacement(VILLAGE, Long.MAX_VALUE));
        assertTrue(state.needsManualPlacement(VILLAGE));
        assertEquals(GuildCornerLandmarkState.LandmarkStatus.PENDING,
                state.snapshot(VILLAGE).orElseThrow().status());
        assertTrue(state.markGenerated(VILLAGE));
    }

    @Test
    void unsafeReservedSiteCanBeMovedDirectlyToManualRecovery() {
        GuildCornerLandmarkState state = stateWithReservation();

        assertTrue(state.requireManualPlacement(VILLAGE));
        assertTrue(state.needsManualPlacement(VILLAGE));
        assertFalse(state.canAttemptAutomaticPlacement(VILLAGE, Long.MAX_VALUE));
        assertFalse(state.requireManualPlacement(VILLAGE));
        assertEquals(GuildCornerLandmarkState.LandmarkStatus.PENDING,
                state.snapshot(VILLAGE).orElseThrow().status());
    }

    @Test
    void dimensionsCanUseTheSameVillageCoordinatesWithoutAliasing() {
        GuildCornerLandmarkState state = new GuildCornerLandmarkState();
        GuildCornerLandmarkState.VillageKey nether =
                new GuildCornerLandmarkState.VillageKey("minecraft:the_nether", 320, -144);
        state.reserve(VILLAGE, POST, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.PLAINS, FOOTPRINT);
        state.reserve(nether, POST, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.GENERIC, FOOTPRINT);

        assertEquals(2, state.size());
        assertNotEquals(state.snapshot(VILLAGE).orElseThrow().key(),
                state.snapshot(nether).orElseThrow().key());
    }

    @Test
    void saveLoadPreservesPlacementHistoryWithoutCreatingABlueprintAction() {
        GuildCornerLandmarkState state = stateWithReservation();
        state.scheduleRetry(VILLAGE, 500);
        GuildCornerLandmarkState loaded = GuildCornerLandmarkState.fromNbt(
                GuildCornerLandmarkState.toNbt(state));

        assertEquals(GuildCornerLandmarkState.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
        assertEquals(state.snapshot(VILLAGE), loaded.snapshot(VILLAGE));
        assertEquals(1, loaded.landmarksView().size());
        assertTrue(loaded.findAt("minecraft:overworld", POST).isPresent());
    }

    @Test
    void malformedAndDuplicateNbtCannotCreateUnsafeOrConflictingRecords() {
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        entries.add(entry("bad dimension", 0, 0, 0, 0, 0));
        entries.add(entry("minecraft:overworld", 4, 8, 20, 64, 20));
        CompoundTag duplicate = entry("minecraft:overworld", 4, 8, 99, 70, 99);
        duplicate.putInt("retryAttempts", Integer.MAX_VALUE);
        entries.add(duplicate);
        root.put("landmarks", entries);

        GuildCornerLandmarkState loaded = GuildCornerLandmarkState.fromNbt(root);
        var landmark = loaded.snapshot(GuildCornerLandmarkState.VillageKey.overworld(4, 8)).orElseThrow();

        assertEquals(1, loaded.size());
        assertEquals(new BlockPos(20, 64, 20), landmark.cornerPos());
        assertEquals(GuildCornerLandmarkState.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void oversizedFootprintsAreRejectedAtTheDataBoundary() {
        assertThrows(IllegalArgumentException.class, () ->
                new GuildCornerLandmarkState.Footprint(0, 0, 0, 100, 2, 2));
        GuildCornerLandmarkState state = new GuildCornerLandmarkState();
        assertFalse(state.reserve(VILLAGE, POST, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.PLAINS,
                GuildCornerLandmarkState.Footprint.singleBlock(BlockPos.ZERO)).accepted());
    }

    private static GuildCornerLandmarkState stateWithReservation() {
        GuildCornerLandmarkState state = new GuildCornerLandmarkState();
        state.reserve(VILLAGE, POST, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.PLAINS, FOOTPRINT);
        return state;
    }

    private static CompoundTag entry(String dimension, int villageX, int villageZ,
                                     int cornerX, int cornerY, int cornerZ) {
        CompoundTag entry = new CompoundTag();
        entry.putString("dimension", dimension);
        entry.putInt("villageX", villageX);
        entry.putInt("villageZ", villageZ);
        entry.putInt("cornerX", cornerX);
        entry.putInt("cornerY", cornerY);
        entry.putInt("cornerZ", cornerZ);
        entry.putInt("minX", cornerX);
        entry.putInt("minY", cornerY);
        entry.putInt("minZ", cornerZ);
        entry.putInt("maxX", cornerX + 40);
        entry.putInt("maxY", cornerY + 40);
        entry.putInt("maxZ", cornerZ + 40);
        return entry;
    }
}
