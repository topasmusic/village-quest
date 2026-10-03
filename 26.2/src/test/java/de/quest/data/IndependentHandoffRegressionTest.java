package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.RegionalDispatchService;
import de.quest.network.VillageNetworkPayloads;
import de.quest.reputation.*;
import de.quest.reputation.SocialReputationRules.BenefitKind;
import de.quest.util.TimeUtil;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;

final class IndependentHandoffRegressionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void disabledDailyBonusAndWeeklyCompletionsConsumeReceiptsAcrossReloadAndReenable() {
        var server = mock(MinecraftServer.class);
        var state = QuestState.fromNbt(new CompoundTag()); UUID player = UUID.randomUUID();
        try (var states = mockStatic(QuestState.class);
             var service = mockStatic(SocialReputationService.class, CALLS_REAL_METHODS);
             var time = mockStatic(TimeUtil.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            states.when(() -> QuestState.toNbt(any())).thenCallRealMethod();
            states.when(() -> QuestState.fromNbt(any())).thenCallRealMethod();
            time.when(TimeUtil::currentDay).thenReturn(10L);
            service.when(SocialReputationService::enabled).thenReturn(false);
            for (var slot : new String[] {"normal", "bonus", "weekly"}) {
                var kind = slot.equals("weekly") ? BenefitKind.WEEKLY : BenefitKind.DAILY;
                var name = slot.equals("weekly") ? "normal" : slot;
                var result = SocialReputationService.recordQuestCompletion(server, player, kind, 7, name);
                assertTrue(result.applied()); assertEquals(0, result.guildDelta());
                assertTrue(state.isDirty());
                state = QuestState.fromNbt(QuestState.toNbt(state));
                states.when(() -> QuestState.get(server)).thenReturn(state);
                assertTrue(state.getPlayerData(player).socialReputation().hasReceipt(kind,
                        SocialReputationService.instanceId(kind, player, "7:" + name)));
            }
            service.when(SocialReputationService::enabled).thenReturn(true);
            time.when(TimeUtil::currentDay).thenReturn(11L);
            for (var slot : new String[] {"normal", "bonus", "weekly"}) {
                var kind = slot.equals("weekly") ? BenefitKind.WEEKLY : BenefitKind.DAILY;
                assertFalse(SocialReputationService.recordQuestCompletion(server, player, kind, 7,
                        slot.equals("weekly") ? "normal" : slot).applied());
            }
            assertEquals(0, state.getPlayerData(player).socialReputation().guildTrust());
            assertEquals(1, SocialReputationService.recordQuestCompletion(server, player, BenefitKind.DAILY, 11, "normal").guildDelta());
        }
    }

    @Test void dispatchRejectsUnloadedNearbyBoardWithoutReadingOrLoadingIt() throws Exception {
        var fixture = new BoardFixture();
        when(fixture.world.getBlockState(any())).thenThrow(new AssertionError("Unloaded chunk read"));
        assertFalse(fixture.dispatchValid());
        verify(fixture.world, never()).getBlockState(any());
    }

    @Test void dispatchRejectsDeadAndSpectatorPlayersBeforeBoardRead() throws Exception {
        var fixture = new BoardFixture(); var block = mock(BlockState.class);
        when(fixture.world.hasChunkAt(any())).thenReturn(true);
        when(fixture.world.getBlockState(any())).thenReturn(block);
        when(block.is(nullable(Block.class))).thenReturn(true);
        when(fixture.player.isAlive()).thenReturn(false);
        assertFalse(fixture.dispatchValid());
        when(fixture.player.isAlive()).thenReturn(true); when(fixture.player.isSpectator()).thenReturn(true);
        assertFalse(fixture.dispatchValid());
        verify(fixture.world, never()).getBlockState(any());
    }

    @Test void allNoticePostEntrypointsRejectUnloadedNearbyBoards() throws Exception {
        var fixture = new BoardFixture();
        when(fixture.world.getBlockState(any())).thenThrow(new AssertionError("Unloaded chunk read"));
        var type = Class.forName("de.quest.shrine.VillageNoticeBoardService");
        var use = type.getDeclaredMethod("use", ServerLevel.class, ServerPlayer.class, BlockPos.class);
        use.setAccessible(true); assertDoesNotThrow(() -> use.invoke(null, fixture.world, fixture.player, BlockPos.ZERO));
        var action = type.getDeclaredMethod("handleAction", ServerPlayer.class, VillageNetworkPayloads.NoticeBoardActionPayload.class);
        var boardAction = mock(VillageNetworkPayloads.NoticeBoardActionPayload.class);
        when(boardAction.action()).thenReturn(VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_DELIVER);
        action.setAccessible(true); assertDoesNotThrow(() -> action.invoke(null, fixture.player, boardAction));
        var journey = type.getDeclaredMethod("handleJourneyAction", ServerPlayer.class, VillageNetworkPayloads.NoticeJourneyActionPayload.class);
        journey.setAccessible(true); assertDoesNotThrow(() -> journey.invoke(null, fixture.player, mock(VillageNetworkPayloads.NoticeJourneyActionPayload.class)));
        verify(fixture.world, never()).getBlockState(any());
    }

    private static final class BoardFixture {
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel world = mock(ServerLevel.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        BoardFixture() {
            when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world);
            when(player.level()).thenReturn(world); when(player.blockPosition()).thenReturn(BlockPos.ZERO);
            when(player.isAlive()).thenReturn(true);
        }
        boolean dispatchValid() throws Exception {
            var method = RegionalDispatchService.class.getDeclaredMethod("validBoard", ServerLevel.class, ServerPlayer.class, BlockPos.class);
            method.setAccessible(true); return (boolean)method.invoke(null, world, player, BlockPos.ZERO);
        }
    }
}
