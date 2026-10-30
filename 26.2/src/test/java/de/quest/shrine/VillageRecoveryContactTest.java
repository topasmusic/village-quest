package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class VillageRecoveryContactTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { VillageBondMigrationTest.bootstrap(); }

    @Test void onlyCanonicalPhysicallyConfirmedAbandonmentPermitsReplacement() {
        var data = new PlayerQuestData(); var kingdoms = new LivingKingdomsState();
        var key = new VillageLifeState.VillageKey("minecraft:overworld", 32, 64);
        var village = kingdoms.registerGenerated(key, new BlockPos(32, 64, 64), "Original").orElseThrow();
        var contact = VillageContactService.establish(data, village, VillageBondType.GRANARY);
        assertTrue(contact.accepted());
        var life = mock(VillageLifeState.class);
        when(life.status(key)).thenReturn(VillageLifeState.Status.ACTIVE);
        assertFalse(VillageIntroRequestService.lostContact(data, 0, kingdoms, life), "no loaded population evidence");
        when(life.status(key)).thenReturn(VillageLifeState.Status.RECOVERING);
        when(life.abandonmentCycle(key)).thenReturn(1);
        assertFalse(VillageIntroRequestService.lostContact(data, 0, kingdoms, life), "recovering contact is preserved");
        when(life.status(key)).thenReturn(VillageLifeState.Status.ABANDONED);
        when(life.abandonmentCycle(key)).thenReturn(0);
        assertFalse(VillageIntroRequestService.lostContact(data, 0, kingdoms, life), "status alone cannot invent a confirmed cycle");
        when(life.abandonmentCycle(key)).thenReturn(1);
        assertTrue(VillageIntroRequestService.lostContact(data, 0, kingdoms, life));
        assertFalse(VillageIntroRequestService.lostContact(data, -1, kingdoms, life));
        assertFalse(VillageIntroRequestService.lostContact(data, 1, kingdoms, life));
        data.setTradeRouteString(VillageBondService.villageKey(0, "canonical_id"), UUID.randomUUID().toString());
        assertFalse(VillageIntroRequestService.lostContact(data, 0, kingdoms, life));
        data.setTradeRouteString(VillageBondService.villageKey(0, "canonical_id"), village.id().toString());
        data.setTradeRouteInt(VillageBondService.villageKey(0, "x"), 900);
        assertFalse(VillageIntroRequestService.lostContact(data, 0, kingdoms, life), "foreign location is not this canonical contact");
        assertEquals(0, data.getCurrencyBalance());
        assertEquals(0, data.getTradeRouteInt("route_count"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void nativeAdmissionKeepsPendingClaimsAndPublishesWelcomeAndEpisodeTogether(boolean completedWelcome) {
        var server = mock(net.minecraft.server.MinecraftServer.class);
        var world = mock(net.minecraft.server.level.ServerLevel.class);
        var player = mock(net.minecraft.server.level.ServerPlayer.class); var owner = UUID.randomUUID();
        when(world.getServer()).thenReturn(server); when(player.level()).thenReturn(world);
        when(player.getUUID()).thenReturn(owner); when(server.isSameThread()).thenReturn(true);
        var state = VillageBondMigrationTest.load(new net.minecraft.nbt.CompoundTag());
        var data = state.getPlayerData(owner); var kingdoms = new LivingKingdomsState();
        var oldKey = new VillageLifeState.VillageKey("minecraft:overworld", 32, 64);
        var newKey = new VillageLifeState.VillageKey("minecraft:overworld", 320, 640);
        var oldVillage = kingdoms.registerGenerated(oldKey, new BlockPos(32, 64, 64), "Lost").orElseThrow();
        var newVillage = kingdoms.registerGenerated(newKey, new BlockPos(320, 64, 640), "Replacement").orElseThrow();
        VillageContactService.establish(data, oldVillage, VillageBondType.GRANARY);
        VillageContactService.establish(data, newVillage, VillageBondType.GRANARY);
        data.setTradeRouteFlag("guild_intro.first_daily_completed", true);
        VillageWelcomeService.begin(data, 0);
        VillageWelcomeService.greet(data, UUID.randomUUID()); VillageWelcomeService.greet(data, UUID.randomUUID());
        if (completedWelcome) VillageWelcomeService.greet(data, UUID.randomUUID());
        de.quest.data.PersonalResetReceipts.record(data, "welcome");
        var progress = VillageIntroRequestProgress.open(data, oldVillage.id(), 0, false);
        assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
        assertTrue(progress.complete(VillageIntroRequestType.PATH_STONE));
        data.setTradeRouteInt(VillageBondService.villageKey(0, "completions"), 1);
        var life = mock(VillageLifeState.class);
        when(life.status(oldKey)).thenReturn(VillageLifeState.Status.ABANDONED);
        when(life.abandonmentCycle(oldKey)).thenReturn(1);
        var board = new BlockPos(320, 64, 640);
        var context = new VillageNoticeContext(newVillage.id(), 1, "minecraft:overworld", VillageBondType.GRANARY, board, false, null);
        try (var states = mockStatic(de.quest.data.QuestState.class, call -> call.getMethod().getName().equals("get") ? state : call.callRealMethod());
             var identities = mockStatic(LivingKingdomsState.class);
             var lives = mockStatic(VillageLifeState.class);
             var boards = mockStatic(VillageNoticeBoardService.class);
             var service = mockStatic(VillageIntroRequestService.class, CALLS_REAL_METHODS)) {
            identities.when(() -> LivingKingdomsState.get(server)).thenReturn(kingdoms);
            lives.when(() -> VillageLifeState.get(server)).thenReturn(life);
            boards.when(() -> VillageNoticeBoardService.replacementContext(world, player, board)).thenReturn(java.util.Optional.of(context));
            service.when(() -> VillageIntroRequestService.recover(player)).thenReturn(VillageIntroDeliveryCoordinator.Result.HELD);
            assertNull(VillageIntroRequestService.replacementEpisode(player, board));
            assertFalse(VillageIntroRequestService.replaceContact(player, board, 1, progress.episode()));
            assertSame(data, state.getPlayerData(owner));
            service.when(() -> VillageIntroRequestService.recover(player)).thenReturn(VillageIntroDeliveryCoordinator.Result.CANCELLED);
            var episode = VillageIntroRequestService.replacementEpisode(player, board);
            assertEquals(progress.episode(), episode);
            assertFalse(VillageIntroRequestService.replaceContact(player, board, 2, episode));
            assertFalse(VillageIntroRequestService.replaceContact(player, board, 1, UUID.randomUUID()));
            assertSame(data, state.getPlayerData(owner));
            assertTrue(VillageIntroRequestService.replaceContact(player, board, 1, episode));
            var next = state.getPlayerData(owner);
            assertNotSame(data, next);
            assertEquals(2, next.getTradeRouteInt("guild_intro.welcome_village"));
            assertEquals(0, VillageWelcomeService.greetingCount(next));
            assertEquals(completedWelcome, VillageWelcomeService.isCompleted(next));
            assertEquals(!completedWelcome, VillageWelcomeService.isActive(next));
            assertEquals(newVillage.id().toString(), next.getTradeRouteString(VillageIntroRequestProgress.PREFIX + "village"));
            assertNotEquals(episode.toString(), next.getTradeRouteString(VillageIntroRequestProgress.PREFIX + "episode"));
            assertEquals(1, next.getTradeRouteInt(VillageBondService.villageKey(0, "completions")));
            assertEquals(0, next.getTradeRouteInt(VillageBondService.villageKey(1, "completions")));
            assertTrue(de.quest.data.PersonalResetReceipts.has(next, "welcome"));
            assertEquals(1, VillageIntroRequestProgress.open(next, newVillage.id(), 0, false).paidCount());
            assertEquals(0, next.getCurrencyBalance());
        }
    }
}
