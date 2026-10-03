package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.caravan.RegionalDispatchLedger;
import de.quest.caravan.RegionalDispatchLedger.Dispatch;
import de.quest.caravan.RegionalDispatchLedger.Endpoint;
import de.quest.caravan.RegionalDispatchLedger.Stage;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RegionalDispatchLedgerTest {
    private static final UUID OWNER = UUID.fromString("a3c02fc0-6e16-4d42-80bd-f20933df3d65");
    private static final UUID SOURCE = UUID.fromString("14b3055f-d10d-4247-a576-55789564d0b6");
    private static final UUID TARGET = UUID.fromString("185eef40-f047-4fcb-acad-b44864b914c4");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Dispatch offer() {
        return new Dispatch(UUID.fromString("46da7fe9-10aa-4415-9440-95509be96d77"),
                SOURCE, TARGET, "minecraft:overworld", 10, 20, 100, 200,
                2, 2, "minecraft:bread", 12, 18, Stage.WAITING_AT_SOURCE);
    }

    @Test
    void twoLegCargoSurvivesEveryTransferAndPaysExactlyOnce() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(OWNER);
        assertTrue(RegionalDispatchLedger.start(data, offer()));
        assertFalse(RegionalDispatchLedger.start(data, offer()));
        assertEquals(Stage.WAITING_AT_SOURCE, reload(state).stage());

        assertFalse(RegionalDispatchLedger.arrive(data, TARGET, Endpoint.HUB, true, true, true));
        assertFalse(RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.SOURCE, true, true, true));
        assertEquals(Stage.LEG_ONE, reload(state).stage());
        assertFalse(RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.SOURCE, true, true, true));
        assertFalse(RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.HUB, true, true, true));
        assertEquals(Stage.WAITING_AT_HUB, reload(state).stage());
        assertFalse(RegionalDispatchLedger.arrive(data, TARGET, Endpoint.HUB, true, true, true));
        assertEquals(Stage.LEG_TWO, reload(state).stage());
        assertTrue(RegionalDispatchLedger.arrive(data, TARGET, Endpoint.TARGET, true, true, true));
        assertEquals(Stage.DELIVERED, reload(state).stage());
        assertFalse(RegionalDispatchLedger.arrive(data, TARGET, Endpoint.TARGET, true, true, true));
    }

    @Test
    void blockedDestinationHoldsArrivedCargoUntilRecovery() {
        PlayerQuestData data = QuestState.fromNbt(new CompoundTag()).getPlayerData(OWNER);
        assertTrue(RegionalDispatchLedger.start(data, offer()));
        RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.SOURCE, true, true, true);
        RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.HUB, true, true, true);
        RegionalDispatchLedger.arrive(data, TARGET, Endpoint.HUB, true, true, true);
        assertFalse(RegionalDispatchLedger.arrive(data, TARGET, Endpoint.TARGET, true, false, true));
        assertEquals(Stage.HELD_AT_DESTINATION, RegionalDispatchLedger.read(data).stage());
        assertTrue(RegionalDispatchLedger.restoreDestination(data, true));
        assertFalse(RegionalDispatchLedger.restoreDestination(data, true));
    }

    @Test
    void routeRemovalCancelsAtCurrentLocationAndRefundCanBeClaimedOnce() {
        PlayerQuestData data = QuestState.fromNbt(new CompoundTag()).getPlayerData(OWNER);
        RegionalDispatchLedger.start(data, offer());
        RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.SOURCE, true, true, true);
        RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.HUB, true, true, true);
        assertFalse(RegionalDispatchLedger.cancelForRemovedRoute(data, SOURCE));
        assertTrue(RegionalDispatchLedger.cancelForRemovedRoute(data, TARGET));
        assertEquals(Stage.CANCELLED_SAFE, RegionalDispatchLedger.read(data).stage());
        assertNotNull(RegionalDispatchLedger.claimCancelled(data));
        assertNull(RegionalDispatchLedger.claimCancelled(data));
        assertNull(RegionalDispatchLedger.read(data));
    }

    @Test
    void unavailableHubPreventsNewDeparturesWithoutDestroyingFreight() {
        PlayerQuestData data = QuestState.fromNbt(new CompoundTag()).getPlayerData(OWNER);
        RegionalDispatchLedger.start(data, offer());
        assertFalse(RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.SOURCE, true, true, false));
        assertEquals(Stage.WAITING_AT_SOURCE, RegionalDispatchLedger.read(data).stage());
    }

    @Test
    void removingSecondRouteBeforeTheFirstLegStillSecuresFreight() {
        PlayerQuestData data = QuestState.fromNbt(new CompoundTag()).getPlayerData(OWNER);
        RegionalDispatchLedger.start(data, offer());
        RegionalDispatchLedger.arrive(data, SOURCE, Endpoint.SOURCE, true, true, true);
        assertTrue(RegionalDispatchLedger.cancelForRemovedRoute(data, TARGET));
        assertEquals(Stage.CANCELLED_SAFE, RegionalDispatchLedger.read(data).stage());
    }

    private static Dispatch reload(QuestState state) {
        return RegionalDispatchLedger.read(QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(OWNER));
    }
}
