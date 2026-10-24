package de.quest.caravan;

import de.quest.data.*;
import de.quest.network.Payloads;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class MinimapRestoreTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void savedPreferenceDoesNotShowLockedNoticeOrGrantAccessButManualToggleStillExplains() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var player=mock(ServerPlayer.class);
        var id=UUID.randomUUID();var state=mock(QuestState.class);var data=new PlayerQuestData();
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(player.level()).thenReturn(world);when(player.getUUID()).thenReturn(id);when(state.getPlayerData(id)).thenReturn(data);
        try(var states=mockStatic(QuestState.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            TradeRouteService.setMinimapEnabled(world,player,true);
            TradeRouteService.handleMapAction(player,new Payloads.TradeRouteActionPayload(Payloads.TradeRouteActionPayload.ACTION_MINIMAP_RESTORE,-1));
            assertFalse(TradeRouteService.isMinimapEnabled(id));
            verify(player,never()).sendSystemMessage(any(Component.class),anyBoolean());
            TradeRouteService.toggleMinimap(world,player);
            verify(player).sendSystemMessage(any(Component.class),eq(true));
        }
    }
    @Test void repeatedRestoreNeverTogglesAnAlreadyEnabledMinimapOff() throws Exception {
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);
        var field=TradeRouteService.class.getDeclaredField("MINIMAP_VIEWERS");field.setAccessible(true);
        @SuppressWarnings("unchecked") var viewers=(java.util.Set<UUID>)field.get(null);
        viewers.add(id);
        try {
            TradeRouteService.setMinimapEnabled(world,player,true);TradeRouteService.setMinimapEnabled(world,player,true);
            assertTrue(TradeRouteService.isMinimapEnabled(id));verify(player,never()).sendSystemMessage(any(Component.class),anyBoolean());
        } finally {viewers.remove(id);}
    }
}
