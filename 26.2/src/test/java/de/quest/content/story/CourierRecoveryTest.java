package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.entity.CaravanMerchantEntity;
import de.quest.quest.EncounterEntityLifecycle;
import de.quest.quest.story.StoryQuestKeys;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

final class CourierRecoveryTest {
    final MinecraftServer server = mock(MinecraftServer.class);
    final ServerLevel world = mock(ServerLevel.class);
    final ServerPlayer player = mock(ServerPlayer.class);
    final CaravanMerchantEntity old = mock(CaravanMerchantEntity.class);
    final UUID owner = UUID.randomUUID(), oldId = UUID.randomUUID();
    final PlayerQuestData data = new PlayerQuestData();
    MockedStatic<QuestState> states;

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach void setup() throws Exception {
        ShadowsTradeRoadEncounterService.resetRuntimeState();
        EncounterEntityLifecycle.reset();
        when(world.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(world);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(player.level()).thenReturn(world);
        when(player.getUUID()).thenReturn(owner);
        when(old.getUUID()).thenReturn(oldId);
        when(old.entityTags()).thenReturn(Set.of("vq_trade_road_caravan"));
        when(old.isAlive()).thenReturn(true);
        when(world.getRandom()).thenReturn(RandomSource.create(1));
        when(world.getHeight(any(), anyInt(), anyInt())).thenReturn(70);
        when(world.getBlockState(any())).thenAnswer(inv ->
                ((BlockPos) inv.getArgument(0)).getY() < 70
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var state = mock(QuestState.class);
        when(state.getPlayerData(owner)).thenReturn(data);
        states = mockStatic(QuestState.class);
        states.when(() -> QuestState.get(server)).thenReturn(state);
        couriers().put(owner, oldId);
        EncounterEntityLifecycle.expect(old);
        when(world.getGameTime()).thenReturn(100L);
    }

    @AfterEach void cleanup() {
        states.close();
        ShadowsTradeRoadEncounterService.resetRuntimeState();
        EncounterEntityLifecycle.reset();
    }

    @SuppressWarnings("unchecked")
    Map<UUID, UUID> couriers() throws Exception {
        var field = ShadowsTradeRoadEncounterService.class.getDeclaredField("LETTER_COURIERS");
        field.setAccessible(true);
        return (Map<UUID, UUID>) field.get(null);
    }

    void ensure() throws Exception {
        var method = ShadowsTradeRoadEncounterService.class.getDeclaredMethod(
                "ensureCourier", ServerLevel.class, ServerPlayer.class);
        method.setAccessible(true);
        try { method.invoke(null, world, player); }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error error) throw error;
            if (ex.getCause() instanceof RuntimeException error) throw error;
            throw ex;
        }
    }

    @Test void shortUnloadKeepsOriginalIdentityWithoutTerrainSearch() throws Exception {
        ensure();
        when(world.getGameTime()).thenReturn(6099L);
        ensure();
        assertEquals(oldId, couriers().get(owner));
        verify(world, never()).getHeight(any(), anyInt(), anyInt());
        EncounterEntityLifecycle.onLoad(old, world);
        verify(old, never()).discard();
    }

    @Test void longUnloadRetiresIdentityBeforeReplacementSearchAndRejectsLateOldBody() throws Exception {
        ensure();
        when(world.getGameTime()).thenReturn(6100L);
        // No native mod entity registration in a server-free unit test. Observe
        // the recovery boundary at terrain lookup; the existing spawn tail is unchanged.
        doAnswer(inv -> {
            assertNull(couriers().get(owner), "retire before looking for a replacement site");
            return 70;
        }).when(world).getHeight(any(), anyInt(), anyInt());
        doReturn(Blocks.AIR.defaultBlockState()).when(world).getBlockState(any());
        ensure();
        assertNull(couriers().get(owner));
        verify(world).getHeight(any(), anyInt(), anyInt());
        EncounterEntityLifecycle.onLoad(old, world);
        verify(old).discard();
        assertFalse(ShadowsTradeRoadEncounterService.handleCourierInteraction(world, player, old));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SHADOWS_LETTER_RECEIVED));
    }

    @Test void returnBeforeDeadlineRestartsAbsenceWindow() throws Exception {
        ensure();
        when(world.getGameTime()).thenReturn(6000L);
        when(world.getEntity(oldId)).thenReturn(old);
        ensure();
        when(world.getEntity(oldId)).thenReturn(null);
        when(world.getGameTime()).thenReturn(6100L);
        ensure();
        when(world.getGameTime()).thenReturn(12099L);
        ensure();
        assertEquals(oldId, couriers().get(owner));
        verify(world, never()).getHeight(any(), anyInt(), anyInt());
    }

    @Test void failedReplacementSiteStillRetiresOldBodyAndCanRetry() throws Exception {
        ensure();
        when(world.getGameTime()).thenReturn(6100L);
        doReturn(Blocks.AIR.defaultBlockState()).when(world).getBlockState(any());
        ensure();
        assertNull(couriers().get(owner));
        EncounterEntityLifecycle.onLoad(old, world);
        verify(old).discard();
        ensure();
        verify(world, times(2)).getHeight(any(), anyInt(), anyInt());
    }

    @Test void deliveredLetterNeverTriggersTimeoutReplacement() throws Exception {
        ensure();
        data.setStoryInt(StoryQuestKeys.SHADOWS_LETTER_RECEIVED, 1);
        when(world.getGameTime()).thenReturn(6100L);
        ensure();
        assertEquals(oldId, couriers().get(owner));
        assertEquals(1, data.getStoryInt(StoryQuestKeys.SHADOWS_LETTER_RECEIVED));
        verify(world, never()).getHeight(any(), anyInt(), anyInt());
    }

    @Test void runtimeResetRetiresOldBodyWithoutLosingSavedDelivery() throws Exception {
        ensure();
        data.setStoryInt(StoryQuestKeys.SHADOWS_LETTER_RECEIVED, 1);
        ShadowsTradeRoadEncounterService.resetRuntimeState();
        ensure();
        assertTrue(couriers().isEmpty());
        EncounterEntityLifecycle.onLoad(old, world);
        verify(old).discard();
        verify(world, never()).getHeight(any(), anyInt(), anyInt());
    }
}
