package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.quest.entity.CaravanMerchantEntity;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

final class RouteEntityInteractionTest {
    @org.junit.jupiter.api.BeforeAll
    static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void unrelatedEntityOutsideOverworldPassesWithoutRouteFeedback() {
        var world = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(mock(ServerLevel.class));
        var player = mock(ServerPlayer.class);
        var entity = mock(Entity.class);

        assertEquals(InteractionResult.PASS, TradeRouteService.onEntityUse(world, player, entity));
        verifyNoInteractions(player, entity);
    }

    @Test
    void unregisteredCaravanOutsideOverworldDoesNotEmitRouteFeedback() {
        var world = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(mock(ServerLevel.class));
        var player = mock(ServerPlayer.class);
        var merchant = mock(CaravanMerchantEntity.class);
        when(merchant.getUUID()).thenReturn(java.util.UUID.randomUUID());

        assertEquals(InteractionResult.PASS, TradeRouteService.onEntityUse(world, player, merchant));
        verifyNoInteractions(player);
    }

    @Test
    void explicitSurveyStillExplainsOverworldRestriction() {
        var server = mock(MinecraftServer.class);
        var overworld = mock(ServerLevel.class);
        var otherWorld = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        when(otherWorld.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(overworld);

        assertFalse(TradeRouteService.startRouteSurvey(otherWorld, player, 0));
        verify(player).sendSystemMessage(argThat(c -> c.getContents() instanceof TranslatableContents t
                && t.getKey().equals("message.village-quest.trade_route.survey.wrong_dimension")), eq(false));
    }
}
