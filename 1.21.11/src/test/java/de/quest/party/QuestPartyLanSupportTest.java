package de.quest.party;

import de.quest.data.QuestState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class QuestPartyLanSupportTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void resetParties() { QuestPartyService.resetRuntimeState(); }

    @ParameterizedTest
    @CsvSource({"true,false,0,true", "false,true,1,true", "false,false,2,true", "false,false,1,false"})
    void enablesDedicatedPublishedLanAndModHostedMultiplayer(
            boolean dedicated, boolean published, int players, boolean expected) {
        var server = server(dedicated, published, players);
        assertEquals(expected, QuestPartyService.isEnabled(server));
    }

    @Test void missingServerCannotEnableParties() {
        assertFalse(QuestPartyService.isEnabled(null));
    }

    @Test void savedPartyLoadsWhenPrivateWorldIsOpenedToLan() throws Exception {
        var server = server(false, false, 1);
        var state = emptyState();
        UUID leader = UUID.randomUUID();
        var party = savedParty(leader);
        state.setQuestPartyState(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()));
        var saved = state.getQuestPartyState().copy();
        try (var access = mockStatic(QuestState.class)) {
            access.when(() -> QuestState.get(server)).thenReturn(state);
            QuestPartyService.loadPersistentState(server);
            QuestPartyService.onServerTick(server);
            assertFalse(QuestPartyService.hasParty(leader));
            assertEquals(saved, state.getQuestPartyState());

            when(server.isPublished()).thenReturn(true);
            QuestPartyService.onServerTick(server);
            assertTrue(QuestPartyService.hasParty(leader));
            assertTrue(QuestPartyService.hasParty(party.members().getLast()));
        }
    }

    @Test void closingLanStillSavesLoadedPartyChanges() throws Exception {
        var server = server(false, true, 1);
        var state = emptyState();
        UUID leader = UUID.randomUUID();
        var party = savedParty(leader);
        state.setQuestPartyState(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()));
        try (var access = mockStatic(QuestState.class)) {
            access.when(() -> QuestState.get(server)).thenReturn(state);
            QuestPartyService.loadPersistentState(server);
            assertTrue(QuestPartyService.hasParty(leader));
            QuestPartyService.partyFor(leader).daily().bind("HARVEST_WHEAT", 41L);
            QuestPartyService.partyFor(leader).daily().setInt("wheat", 17);
            when(server.isPublished()).thenReturn(false);
            QuestPartyService.persistRuntimeState(server);

            Map<UUID, PartyRuntime> restored = new HashMap<>();
            QuestPartyPersistence.read(state.getQuestPartyState(), restored, new HashMap<>(), new HashMap<>());
            assertEquals(17, restored.get(party.id()).daily().getInt("wheat"));
            assertFalse(QuestPartyService.hasParty(leader));
        }
    }

    @Test void savingPrivateWorldPreservesInactivePartyData() throws Exception {
        var server = server(false, false, 1);
        var state = emptyState();
        var party = savedParty(UUID.randomUUID());
        state.setQuestPartyState(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()));
        var saved = state.getQuestPartyState().copy();
        try (var access = mockStatic(QuestState.class)) {
            access.when(() -> QuestState.get(server)).thenReturn(state);
            QuestPartyService.loadPersistentState(server);
            QuestPartyService.persistRuntimeState(server);
            assertEquals(saved, state.getQuestPartyState());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hostCanInviteGuestWhoAcceptsIntoPersistedParty(boolean published) throws Exception {
        var server = server(false, published, 2);
        var world = server.overworld();
        var state = emptyState();
        var host = player(world, "Host");
        var guest = player(world, "Guest");
        try (var access = mockStatic(QuestState.class);
             var messaging = mockStatic(QuestPartyMessaging.class)) {
            // UI/network refreshes require a running client; the membership and save path remain real.
            access.when(() -> QuestState.get(server)).thenReturn(state);
            assertTrue(QuestPartyService.invite(world, host, guest));
            assertTrue(QuestPartyService.acceptInvite(world, guest));
            assertEquals(QuestPartyService.partyFor(host.getUUID()), QuestPartyService.partyFor(guest.getUUID()));
            Map<UUID, UUID> restoredMembers = new HashMap<>();
            QuestPartyPersistence.read(state.getQuestPartyState(), new HashMap<>(), restoredMembers, new HashMap<>());
            assertEquals(2, restoredMembers.size());
            assertEquals(restoredMembers.get(host.getUUID()), restoredMembers.get(guest.getUUID()));
        }
    }

    @Test void lastModHostedGuestDisconnectStillRecordsReconnectGrace() throws Exception {
        var server = server(false, false, 2);
        var world = server.overworld();
        var state = emptyState();
        var host = player(world, "Host");
        var guest = player(world, "Guest");
        try (var access = mockStatic(QuestState.class);
             var messaging = mockStatic(QuestPartyMessaging.class)) {
            access.when(() -> QuestState.get(server)).thenReturn(state);
            assertTrue(QuestPartyService.invite(world, host, guest));
            assertTrue(QuestPartyService.acceptInvite(world, guest));
            when(server.getPlayerCount()).thenReturn(1);
            long before = System.currentTimeMillis();
            QuestPartyService.handleDisconnect(guest);
            Map<UUID, PartyRuntime> restored = new HashMap<>();
            QuestPartyPersistence.read(state.getQuestPartyState(), restored, new HashMap<>(), new HashMap<>());
            var deadline = restored.values().iterator().next().disconnectDeadlines().get(guest.getUUID());
            assertNotNull(deadline);
            assertTrue(deadline >= before + 10L * 60_000L);
        }
    }

    private static ServerPlayer player(ServerLevel world, String name) {
        var player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.getDisplayName()).thenReturn(Component.literal(name));
        when(player.level()).thenReturn(world);
        return player;
    }

    private static QuestState emptyState() throws Exception {
        var constructor = QuestState.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static PartyRuntime savedParty(UUID leader) {
        var party = new PartyRuntime(UUID.randomUUID(), leader);
        party.members().add(leader);
        party.members().add(UUID.randomUUID());
        return party;
    }

    private static MinecraftServer server(boolean dedicated, boolean published, int players) {
        var server = mock(MinecraftServer.class);
        when(server.isDedicatedServer()).thenReturn(dedicated);
        when(server.isPublished()).thenReturn(published);
        when(server.getPlayerCount()).thenReturn(players);
        var world = mock(ServerLevel.class);
        when(world.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(world);
        return server;
    }
}
