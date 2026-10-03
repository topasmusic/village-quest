package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildConvoyStateTest {
    private static final UUID GUILD = UUID.fromString("fbf78254-a095-43f0-8729-ab7334800d91");
    private static final UUID OWNER = UUID.fromString("a180cb26-f156-4c21-9ce8-43791f9d44f0");
    private static final UUID MEMBER = UUID.fromString("4181f6d8-5916-483b-8f7c-ed368873134d");
    private static final List<UUID> ROUTES = List.of(
            UUID.fromString("11ab3431-9104-46ce-9a7a-4fa95f62963e"),
            UUID.fromString("2dc3b284-fb18-4997-a22f-88f81df4d159"),
            UUID.fromString("a4a01e45-911a-4c52-92b5-5f8995a950d0"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void threeRealHubAndVillageRoundTripsSurviveReloadAndCompleteOnce() {
        GuildConvoyState state = new GuildConvoyState();
        assertTrue(state.start(GUILD, OWNER, ROUTES, 10));
        assertFalse(state.start(GUILD, OWNER, ROUTES, 11));
        assertTrue(state.join(GUILD, MEMBER));
        assertFalse(state.join(GUILD, MEMBER));
        assertFalse(state.active(GUILD).credited().contains(MEMBER));
        assertFalse(state.credit(GUILD, UUID.randomUUID()));
        assertTrue(state.credit(GUILD, MEMBER));
        assertFalse(state.credit(GUILD, MEMBER));
        assertTrue(GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state))
                .active(GUILD).credited().contains(MEMBER));
        assertFalse(state.arrive(OWNER, ROUTES.get(0), true).advanced());
        for (int leg = 0; leg < 3; leg++) {
            assertTrue(state.arrive(OWNER, ROUTES.get(leg), false).advanced());
            assertTrue(state.arrive(OWNER, ROUTES.get(leg), true).advanced());
            state = GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state));
            var result = state.arrive(OWNER, ROUTES.get(leg), false);
            assertTrue(result.advanced());
            assertEquals(leg == 2, result.completed());
            if (leg == 2) {
                assertEquals(2, result.convoy().participants().size());
                assertEquals(2, result.convoy().credited().size());
            }
        }
        assertNull(state.active(GUILD));
        assertFalse(state.arrive(OWNER, ROUTES.get(2), false).advanced());
        assertFalse(state.start(GUILD, OWNER, ROUTES, 20));
        assertTrue(state.start(GUILD, OWNER, ROUTES, 10 + 7L * 24_000));
    }

    @Test
    void joiningWithoutAnObservedStopDoesNotGrantEscortCredit() {
        GuildConvoyState state = new GuildConvoyState();
        assertTrue(state.start(GUILD, OWNER, ROUTES, 10));
        assertTrue(state.join(GUILD, MEMBER));
        assertEquals(java.util.Set.of(OWNER), state.active(GUILD).credited());
    }

    @Test
    void routeRemovalCancelsWithoutLosingOtherGuildState() {
        GuildConvoyState state = new GuildConvoyState();
        assertTrue(state.start(GUILD, OWNER, ROUTES, 10));
        assertNull(state.cancelForRemovedRoute(OWNER, UUID.randomUUID()));
        assertNotNull(state.cancelForRemovedRoute(OWNER, ROUTES.get(1)));
        assertNull(state.cancelForRemovedRoute(OWNER, ROUTES.get(1)));
        assertNull(state.active(GUILD));
    }

    @Test void aggressionRemovesOnlyTheOffendersTrustForTheWholeCurrentConvoy() {
        GuildConvoyState state = new GuildConvoyState();
        assertTrue(state.start(GUILD, OWNER, ROUTES, 10)); assertTrue(state.join(GUILD, MEMBER));
        var village = de.quest.village.VillageLifeState.VillageKey.overworld(100, 200);
        state.arrive(OWNER, ROUTES.get(0), false);
        assertTrue(state.recordTrustArrival(GUILD, MEMBER, ROUTES.get(0), village));
        assertTrue(state.recordTrustArrival(GUILD, OWNER, ROUTES.get(0), village));
        assertTrue(state.disqualifyTrustFor(MEMBER)); assertFalse(state.disqualifyTrustFor(MEMBER));
        state = GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state));
        assertFalse(state.recordTrustArrival(GUILD, MEMBER, ROUTES.get(0), village));
        state.arrive(OWNER, ROUTES.get(0), true); state.arrive(OWNER, ROUTES.get(0), false);
        for (int leg = 1; leg < 3; leg++) {
            state.arrive(OWNER, ROUTES.get(leg), false); state.arrive(OWNER, ROUTES.get(leg), true);
            state.arrive(OWNER, ROUTES.get(leg), false);
        }
        assertEquals(java.util.Set.of(OWNER), state.pendingTrust().getFirst().beneficiaries().keySet());
    }

    @Test void trustRequiresAnActualArrivalAndItsCompletionCanBeReplayedAcrossReload() {
        GuildConvoyState state = new GuildConvoyState();
        assertTrue(state.start(GUILD, OWNER, ROUTES, 10));
        assertTrue(state.join(GUILD, MEMBER));
        var village = de.quest.village.VillageLifeState.VillageKey.overworld(100, 200);
        assertFalse(state.recordTrustArrival(GUILD, OWNER, ROUTES.get(0), village));
        assertTrue(state.arrive(OWNER, ROUTES.get(0), false).advanced());
        assertTrue(state.recordTrustArrival(GUILD, MEMBER, ROUTES.get(0), village));
        assertFalse(state.recordTrustArrival(GUILD, MEMBER, ROUTES.get(0), village));
        assertFalse(state.recordTrustArrival(GUILD, UUID.randomUUID(), ROUTES.get(0), village));
        assertTrue(state.arrive(OWNER, ROUTES.get(0), true).advanced());
        state = GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state));
        assertTrue(state.arrive(OWNER, ROUTES.get(0), false).advanced());
        for (int leg = 1; leg < 3; leg++) {
            state.arrive(OWNER, ROUTES.get(leg), false);
            state.arrive(OWNER, ROUTES.get(leg), true);
            state.arrive(OWNER, ROUTES.get(leg), false);
        }
        GuildConvoyState loaded = GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state));
        assertEquals(1, loaded.pendingTrust().size());
        var completed = loaded.pendingTrust().getFirst();
        assertEquals(java.util.Set.of(village), completed.beneficiaries().get(MEMBER));
        assertFalse(completed.beneficiaries().containsKey(OWNER));
        assertTrue(loaded.acknowledgeTrust(completed.convoyId()));
        assertFalse(loaded.acknowledgeTrust(completed.convoyId()));
        assertTrue(GuildConvoyState.fromNbt(GuildConvoyState.toNbt(loaded)).pendingTrust().isEmpty());
    }
    @Test void terminalTrustResetIsSavedRatherThanReassignedAtReplay() {
        GuildConvoyState state = new GuildConvoyState(); state.start(GUILD, OWNER, ROUTES, 10);
        var village = de.quest.village.VillageLifeState.VillageKey.overworld(100, 200);
        state.arrive(OWNER, ROUTES.get(0), false, 77); state.recordTrustArrival(GUILD, OWNER, ROUTES.get(0), village);
        state.arrive(OWNER, ROUTES.get(0), true, 77); state.arrive(OWNER, ROUTES.get(0), false, 77);
        for (int leg = 1; leg < 3; leg++) {
            state.arrive(OWNER, ROUTES.get(leg), false, 77); state.arrive(OWNER, ROUTES.get(leg), true, 77); state.arrive(OWNER, ROUTES.get(leg), false, 77);
        }
        var loaded = GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state));
        assertEquals(77, loaded.pendingTrust().getFirst().resetToken());
    }

}
