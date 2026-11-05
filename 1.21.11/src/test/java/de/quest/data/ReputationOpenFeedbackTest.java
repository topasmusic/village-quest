package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.reputation.*;
import de.quest.network.ReputationPayloads;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;

final class ReputationOpenFeedbackTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { ReputationInteractionService.resetRuntime(); }

    @Test void unconnectedBoardReturnsAnExplanationInsteadOfSilentlyIgnoringTrust() throws Exception {
        checkOpen(true, false, "reputation.village-quest.open.unavailable");
    }
    @Test void disabledTrustReturnsItsOwnExplanation() throws Exception {
        checkOpen(false, false, "reputation.village-quest.open.disabled");
    }
    @Test void validTrustAnchorStillOpensTheNormalDesk() throws Exception {
        checkOpen(true, true, null);
    }
    private void checkOpen(boolean enabled, boolean valid, String reason) throws Exception {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class); UUID id = UUID.randomUUID();
        when(player.getUUID()).thenReturn(id); when(player.level()).thenReturn(world);
        when(player.isAlive()).thenReturn(true); when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world);
        when(world.dimension()).thenReturn(ServerLevel.OVERWORLD); when(world.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        var block = mock(BlockState.class); when(world.getBlockState(BlockPos.ZERO)).thenReturn(block);
        when(block.is(nullable(net.minecraft.world.level.block.Block.class))).thenReturn(true);
        var state = QuestState.fromNbt(new CompoundTag());
        var sent = new ArrayList<CustomPacketPayload>();
        try (var lookup = mockStatic(QuestState.class); var settings = mockStatic(SocialReputationService.class);
             var anchor = mockStatic(ReparationService.class); var network = mockStatic(ServerPlayNetworking.class);
             var dispatch=mockStatic(de.quest.caravan.RegionalDispatchService.class);
             var convoy=mockStatic(de.quest.caravan.GuildConvoyService.class)) {
            // This fixture tests desk-vs-feedback transport. Native service prerequisites have their own boundary tests.
            dispatch.when(() -> de.quest.caravan.RegionalDispatchService.access(eq(world),eq(player),any())).thenReturn(ReputationPayloads.ServiceView.unknown());
            convoy.when(() -> de.quest.caravan.GuildConvoyService.access(world,player)).thenReturn(ReputationPayloads.ServiceView.unknown());
            network.when(() -> ServerPlayNetworking.canSend(eq(player),any(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type.class))).thenReturn(true);
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            settings.when(SocialReputationService::enabled).thenReturn(enabled);
            anchor.when(() -> ReparationService.validAnchor(eq(player), any())).thenReturn(valid);
            network.when(() -> ServerPlayNetworking.send(eq(player), any(CustomPacketPayload.class)))
                    .thenAnswer(call -> { sent.add(call.getArgument(1)); return null; });
            ReputationInteractionService.open(player, new ReputationPayloads.OpenPayload(0, BlockPos.ZERO, 0));
            assertEquals(1, sent.size(), "Every allowed Trust click must return a desk or a reason");
            if (reason == null) assertInstanceOf(ReputationPayloads.InteractionPayload.class, sent.getFirst());
            else assertEquals(reason, sent.getFirst().getClass().getMethod("reasonKey").invoke(sent.getFirst()));
            // The non-pausing board lets the existing four-tick request cooldown expire.
            for (int tick = 0; tick < 4; tick++) state.advanceSocialServerTick();
            ReputationInteractionService.open(player, new ReputationPayloads.OpenPayload(0, BlockPos.ZERO, 0));
            assertEquals(2, sent.size(), "Returning to the board must allow another Trust response after the cooldown");
            if (reason == null) assertInstanceOf(ReputationPayloads.InteractionPayload.class, sent.getLast());
            else assertEquals(reason, sent.getLast().getClass().getMethod("reasonKey").invoke(sent.getLast()));
            assertEquals(0, state.getPlayerData(id).socialReputation().guildTrust());
            assertEquals(0, state.getPlayerData(id).socialReputation().revision());
        }
    }
}
