package de.quest.caravan;

import de.quest.data.*;
import de.quest.guild.VillageGuildState;
import de.quest.guildtown.GuildTownProgress;
import de.quest.network.ReputationPayloads.ServiceView;
import de.quest.shrine.*;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Read-only access snapshots using mocked native boundaries; never starts a world or convoy loop. */
final class LogisticsAccessTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static final class Fixture implements AutoCloseable {
        final UUID id=UUID.randomUUID(); final BlockPos board=new BlockPos(0,64,0);
        final MinecraftServer server=mock(MinecraftServer.class); final ServerLevel world=mock(ServerLevel.class);
        final ServerPlayer player=mock(ServerPlayer.class);
        final QuestState quests=QuestState.TYPE.constructor().get(); final PlayerQuestData data=quests.getPlayerData(id);
        final VillageGuildState guilds=VillageGuildState.TYPE.constructor().get(); final GuildConvoyState convoys=new GuildConvoyState();
        final MockedStatic<QuestState> quest=mockStatic(QuestState.class);
        final MockedStatic<VillageGuildState> guild=mockStatic(VillageGuildState.class);
        final MockedStatic<GuildConvoyState> convoy=mockStatic(GuildConvoyState.class);
        final MockedStatic<TradeRouteService> routes=mockStatic(TradeRouteService.class);
        final MockedStatic<GuildTownProgress> story=mockStatic(GuildTownProgress.class);
        final MockedStatic<VillageBondService> bonds=mockStatic(VillageBondService.class,CALLS_REAL_METHODS);
        final MockedStatic<ShadowsTradeRoadEncounterService> villages=mockStatic(ShadowsTradeRoadEncounterService.class);
        Fixture() {
            when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world); when(world.dimension()).thenReturn(Level.OVERWORLD);
            when(player.level()).thenReturn(world); when(player.getUUID()).thenReturn(id); when(player.blockPosition()).thenReturn(board); when(player.isAlive()).thenReturn(true);
            when(world.hasChunkAt(any())).thenReturn(true); var state=mock(BlockState.class);
            when(state.is(nullable(net.minecraft.world.level.block.Block.class))).thenReturn(true); when(world.getBlockState(any())).thenReturn(state);
            quest.when(() -> QuestState.get(server)).thenReturn(quests); guild.when(() -> VillageGuildState.get(server)).thenReturn(guilds);
            convoy.when(() -> GuildConvoyState.get(server)).thenReturn(convoys); data.socialReputation().setGuildTrust(8);
        }
        void activeRoutes(int count) {
            data.setTradeRouteInt("route_count",count); routes.when(() -> TradeRouteService.hasRouteAccess(world,id)).thenReturn(true);
            bonds.when(() -> VillageBondService.historicalVillageCount(data)).thenReturn(count);
            for(int index=0;index<count;index++) {
                final int slot=index; TradeRouteData.setRouteInt(data,index,"village_index",index+1);
                TradeRouteData.setRouteInt(data,index,"x",index*100);
                data.setTradeRouteInt(VillageBondService.villageKey(index,"type"),1);
                data.setTradeRouteInt(VillageBondService.villageKey(index,"x"),index*100);
                routes.when(() -> TradeRouteService.dispatchReady(world,data,slot)).thenReturn(true);
            }
            villages.when(() -> ShadowsTradeRoadEncounterService.currentVillage(world,board)).thenReturn(mock(ShadowsTradeRoadEncounterService.VillageMarker.class));
        }
        @Override public void close() { villages.close(); bonds.close(); story.close(); routes.close(); convoy.close(); guild.close(); quest.close(); }
    }
    @Test void eightTrustAloneNeverAdvertisesDispatchesOrConvoysAsReady() {
        try(var f=new Fixture()) {
            var dispatch=RegionalDispatchService.access(f.world,f.player,f.board); var convoy=GuildConvoyService.access(f.world,f.player);
            assertFalse(dispatch.available()); assertTrue(dispatch.statusKey().endsWith(".routes"));
            assertFalse(convoy.available()); assertTrue(convoy.statusKey().endsWith(".guild"));
            assertEquals(8,f.data.socialReputation().guildTrust()); assertEquals(0,f.data.getTradeRouteInt("route_count"));
        }
    }
    @Test void dispatchNeedsTwoActualActiveVillageRoutesAndTheConnectedBoard() {
        try(var f=new Fixture()) {
            f.activeRoutes(2);
            assertEquals(ServiceView.DISPATCH,RegionalDispatchService.access(f.world,f.player,f.board).action());
            assertEquals(ServiceView.LOCKED,RegionalDispatchService.access(f.world,f.player,null).action());
            f.routes.when(() -> TradeRouteService.dispatchReady(f.world,f.data,1)).thenReturn(false);
            assertEquals(ServiceView.LOCKED,RegionalDispatchService.access(f.world,f.player,f.board).action());
            assertEquals(0,f.data.getTradeRouteInt("economy.delivery_accepted")); assertNull(RegionalDispatchLedger.read(f.data));
        }
    }
    @Test void convoyRequiresRoleConcordThreeRoutesAndLocalHubAndDoesNotStartFromReading() {
        try(var f=new Fixture()) {
            var guild=f.guilds.create(f.id,"Friends"); f.activeRoutes(3);
            assertFalse(GuildConvoyService.access(f.world,f.player).available());
            f.story.when(() -> GuildTownProgress.hasReward(f.data,"concord_title")).thenReturn(true);
            assertTrue(GuildConvoyService.access(f.world,f.player).statusKey().endsWith(".hub"));
            f.routes.when(() -> TradeRouteService.isNearHome(f.world,f.id,f.board,20)).thenReturn(true);
            assertEquals(ServiceView.START_CONVOY,GuildConvoyService.access(f.world,f.player).action());
            assertNull(f.convoys.active(guild.id()));
            f.data.socialReputation().setGuildTrust(-20);
            assertEquals(ServiceView.LOCKED,GuildConvoyService.access(f.world,f.player).action());
        }
    }
}
