package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

final class SurveyorHomeConfirmationTest {
    final MinecraftServer server = mock(MinecraftServer.class);
    final ServerLevel world = mock(ServerLevel.class);
    final ServerPlayer player = mock(ServerPlayer.class);
    final ItemStack compass = mock(ItemStack.class);
    final PlayerQuestData data = new PlayerQuestData();
    MockedStatic<QuestState> states;

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach void setup() {
        when(world.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(world);
        when(world.getGameTime()).thenReturn(100L);
        when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        var state = mock(QuestState.class);
        when(state.getPlayerData(player.getUUID())).thenReturn(data);
        states = mockStatic(QuestState.class);
        states.when(() -> QuestState.get(server)).thenReturn(state);
    }

    @AfterEach void cleanup() { states.close(); }

    void useHome() throws Exception {
        var method = SurveyorCompassQuestService.class.getDeclaredMethod(
                "returnHome", ServerLevel.class, ServerPlayer.class, ItemStack.class);
        method.setAccessible(true);
        try { method.invoke(null, world, player, compass); }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error error) throw error;
            if (ex.getCause() instanceof RuntimeException error) throw error;
            throw ex;
        }
    }

    @Test void firstClickOnlyArmsConfirmationWithoutResolvingRespawnOrLoadingChunks() throws Exception {
        useHome();
        assertTrue(data.getSurveyorCompassHomeConfirmUntil() > 100L);
        assertEquals(0L, data.getSurveyorCompassHomeCooldownUntil());
        verify(player, never()).getRespawnConfig();
        verify(server, never()).findRespawnDimension();
        verify(world, never()).getChunk(anyInt(), anyInt());
        verifyNoInteractions(compass);
    }

    @Test void expiredConfirmationRequiresAnotherFirstClickWithoutResolving() throws Exception {
        data.setSurveyorCompassHomeConfirmUntil(100L);
        useHome();
        assertTrue(data.getSurveyorCompassHomeConfirmUntil() > 100L);
        verify(player, never()).getRespawnConfig();
        verify(server, never()).findRespawnDimension();
    }

    @Test void secondClickResolvesCurrentDestinationAndClearsMissingTarget() throws Exception {
        data.setSurveyorCompassHomeConfirmUntil(200L);
        useHome();
        verify(player).getRespawnConfig();
        verify(server).findRespawnDimension();
        assertEquals(0L, data.getSurveyorCompassHomeConfirmUntil());
        assertEquals(0L, data.getSurveyorCompassHomeCooldownUntil());
        verifyNoInteractions(compass);
    }

    @Test void existingCooldownBlocksResolutionAndClearsConfirmation() throws Exception {
        data.setSurveyorCompassHomeConfirmUntil(200L);
        data.setSurveyorCompassHomeCooldownUntil(500L);
        useHome();
        assertEquals(0L, data.getSurveyorCompassHomeConfirmUntil());
        assertEquals(500L, data.getSurveyorCompassHomeCooldownUntil());
        verify(player, never()).getRespawnConfig();
        verify(server, never()).findRespawnDimension();
    }

    @Test void confirmedSafeFallbackTeleportsAndRetainsThirtyMinuteCooldown() throws Exception {
        confirmedFallback(true);
        assertEquals(36100L, data.getSurveyorCompassHomeCooldownUntil());
        verify(player).resetFallDistance();
    }

    @Test void rejectedTeleportDoesNotSpendCooldown() throws Exception {
        confirmedFallback(false);
        assertEquals(0L, data.getSurveyorCompassHomeCooldownUntil());
        verify(player, never()).resetFallDistance();
    }

    void confirmedFallback(boolean success) throws Exception {
        data.setSurveyorCompassHomeConfirmUntil(200L);
        var respawn = mock(LevelData.RespawnData.class);
        when(server.findRespawnDimension()).thenReturn(world);
        when(server.getRespawnData()).thenReturn(respawn);
        when(respawn.pos()).thenReturn(new BlockPos(0, 70, 0));
        when(world.getWorldBorder()).thenReturn(new WorldBorder());
        when(world.isInValidBounds(any())).thenReturn(true);
        when(world.noCollision(isNull(), any())).thenReturn(true);
        when(world.getBlockState(any())).thenAnswer(inv -> ((BlockPos) inv.getArgument(0)).getY() < 70
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        when(player.teleportTo(eq(world), eq(0.5), eq(70.0), eq(0.5), anySet(), eq(0f), eq(0f), eq(true)))
                .thenReturn(success);
        try (var dismount = mockStatic(net.minecraft.world.entity.vehicle.DismountHelper.class)) {
            dismount.when(() -> net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(
                    net.minecraft.world.entity.EntityTypes.PLAYER, world, new BlockPos(0, 70, 0), true))
                    .thenReturn(new Vec3(0.5, 70, 0.5));
            useHome();
        }
        verify(world, atLeastOnce()).getChunk(anyInt(), anyInt());
        verify(player).teleportTo(eq(world), eq(0.5), eq(70.0), eq(0.5), anySet(), eq(0f), eq(0f), eq(true));
        assertEquals(0L, data.getSurveyorCompassHomeConfirmUntil());
    }
}
