package de.quest.kingdom;
import de.quest.caravan.TradeRouteService;
import de.quest.data.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
final class KingdomAttackNoticeTest {
 @org.junit.jupiter.api.BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void sameIncidentDoesNotRepeatAfterThirtySecondsOrRuntimeWarningExpiry() {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var actor=UUID.randomUUID();var village=UUID.randomUUID();
  var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);var quest=mock(QuestState.class);var data=new PlayerQuestData();var town=mock(LivingKingdomsState.VillageView.class);var list=mock(net.minecraft.server.players.PlayerList.class);
  when(player.level()).thenReturn(world);when(player.getUUID()).thenReturn(actor);when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(server.isSameThread()).thenReturn(true);when(server.getPlayerList()).thenReturn(list);when(list.getPlayer(actor)).thenReturn(player);
  when(quest.getPlayerData(actor)).thenReturn(data);when(state.village(village)).thenReturn(Optional.of(town));when(town.name()).thenReturn("Home");
  var incident=new TradeRouteService.AttackOpportunity(UUID.randomUUID(),village,UUID.randomUUID(),BlockPos.ZERO);
  try(var states=mockStatic(LivingKingdomsState.class);var quests=mockStatic(QuestState.class);var routes=mockStatic(TradeRouteService.class)) {
   states.when(()->LivingKingdomsState.get(server)).thenReturn(state);quests.when(()->QuestState.get(server)).thenReturn(quest);routes.when(()->TradeRouteService.attackOpportunity(player)).thenReturn(Optional.of(incident));
   for(long tick:new long[]{0,20,600,1200,4000,8000}) {when(world.getGameTime()).thenReturn(tick);KingdomAttackService.observe(player);}
   verify(player,times(2)).sendSystemMessage(any(net.minecraft.network.chat.Component.class));
   routes.when(()->TradeRouteService.attackOpportunity(player)).thenReturn(Optional.of(new TradeRouteService.AttackOpportunity(UUID.randomUUID(),village,UUID.randomUUID(),BlockPos.ZERO)));
   KingdomAttackService.observe(player);verify(player,times(4)).sendSystemMessage(any(net.minecraft.network.chat.Component.class));
  }
 }
}
