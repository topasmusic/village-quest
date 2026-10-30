package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.QuestState;
import de.quest.network.VillageNetworkPayloads;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;

/** Native admission and persisted data, without starting a server or simulating a world. */
final class VillageIntroBoardActionTest {
    @BeforeAll static void bootstrap() { VillageBondMigrationTest.bootstrap(); }
    @Test void incompatibleBoardIsRejectedBeforeAnyNewFormatIsSent() {
        var player=mock(ServerPlayer.class);
        try(var networking=mockStatic(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class)) {
            assertFalse(VillageNoticeBoardService.compatible(player));
            verify(player).sendSystemMessage(any(net.minecraft.network.chat.Component.class),eq(false));
            networking.when(() -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,VillageNetworkPayloads.NoticeBoardPayload.ID)).thenReturn(true);
            assertFalse(VillageNoticeBoardService.compatible(player));
            networking.when(() -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,VillageNetworkPayloads.NoticeJourneyPayload.ID)).thenReturn(true);
            assertTrue(VillageNoticeBoardService.compatible(player));
            networking.verify(() -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(any(),any()),never());
        }
        assertTrue(VillageNetworkPayloads.NoticeBoardPayload.ID.id().getPath().endsWith("_v3"));
        assertTrue(VillageNetworkPayloads.NoticeBoardActionPayload.ID.id().getPath().endsWith("_v2"));
        assertTrue(VillageNetworkPayloads.NoticeJourneyPayload.ID.id().getPath().endsWith("_v3"));
    }
    @Test void authorityChecksCancelAndRetryPreservePaidCredits() {
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
        var owner=UUID.randomUUID(); var village=UUID.randomUUID(); var board=new BlockPos(12,64,34);
        when(world.getServer()).thenReturn(server); when(player.level()).thenReturn(world);
        when(server.isSameThread()).thenReturn(true); when(player.getUUID()).thenReturn(owner);
        var state=VillageBondMigrationTest.load(new CompoundTag()); var data=state.getPlayerData(owner);
        VillageBondService.ensureCanonicalVillageRecord(data,village,"minecraft:overworld",12,34,VillageBondType.GRANARY);
        VillageWelcomeService.begin(data,0);
        for (int i=0;i<3;i++) VillageWelcomeService.greet(data,UUID.randomUUID());
        var progress=VillageIntroRequestProgress.open(data,village,0,false);
        var token=new VillageNetworkPayloads.IntroToken(data.progressGeneration(),progress.episode(),village,1);
        var context=new VillageNoticeContext(village,0,"minecraft:overworld",VillageBondType.GRANARY,board,false,null);
        try(var states=mockStatic(QuestState.class,call -> call.getMethod().getName().equals("get") ? state : call.callRealMethod());
            var boards=mockStatic(VillageNoticeBoardService.class);
            // Stubbing a CALLS_REAL_METHODS recovery must not initialize real Fabric attachments.
            var reset=mockStatic(de.quest.data.PersonalResetService.class);
            var service=mockStatic(VillageIntroRequestService.class,CALLS_REAL_METHODS)) {
            boards.when(() -> VillageNoticeBoardService.localContext(world,player,board)).thenReturn(Optional.of(context));
            service.when(() -> VillageIntroRequestService.recoverBeforeActions(player)).thenReturn(true);
            for(var bad:java.util.List.of(new VillageNetworkPayloads.IntroToken(99,token.episode(),village,1),
                    new VillageNetworkPayloads.IntroToken(token.generation(),UUID.randomUUID(),village,1),
                    new VillageNetworkPayloads.IntroToken(token.generation(),token.episode(),UUID.randomUUID(),1),
                    new VillageNetworkPayloads.IntroToken(token.generation(),token.episode(),village,2)))
                assertFalse(VillageIntroRequestService.handleBoardAction(player,board,action(3,2,bad)));
            assertFalse(VillageIntroRequestService.handleBoardAction(player,board,action(3,2,null)));
            assertFalse(VillageIntroRequestService.handleBoardAction(player,board,action(3,99,token)));
            assertNull(progress.active());
            assertTrue(VillageIntroRequestService.handleBoardAction(player,board,action(3,2,token)));
            assertFalse(VillageIntroRequestService.handleBoardAction(player,board,action(3,0,token)));
            assertTrue(progress.harvest(UUID.randomUUID()));
            service.when(() -> VillageIntroRequestService.recoverBeforeActions(player)).thenReturn(false);
            assertFalse(VillageIntroRequestService.handleBoardAction(player,board,action(4,2,token)));
            assertEquals(1,progress.harvestCount());
            service.when(() -> VillageIntroRequestService.recoverBeforeActions(player)).thenReturn(true);
            assertTrue(VillageIntroRequestService.handleBoardAction(player,board,action(4,2,token)));
            assertEquals(0,progress.harvestCount()); assertNotEquals(token.episode(),progress.episode());
            assertFalse(VillageIntroRequestService.handleBoardAction(player,board,action(3,0,token)));
            var fresh=new VillageNetworkPayloads.IntroToken(token.generation(),progress.episode(),village,1);
            assertTrue(VillageIntroRequestService.handleBoardAction(player,board,action(3,0,fresh)));
            assertEquals(0,progress.paidCount()); assertEquals(0,data.getCurrencyBalance());
            assertEquals(0,VillageBondProgression.snapshot(data,0).actualCompletions());
        }
    }
    private static VillageNetworkPayloads.NoticeBoardActionPayload action(int action,int id,VillageNetworkPayloads.IntroToken token) {
        return new VillageNetworkPayloads.NoticeBoardActionPayload(12,64,34,action,id,token);
    }
}
