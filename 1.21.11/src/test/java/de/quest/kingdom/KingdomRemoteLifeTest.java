package de.quest.kingdom;

import de.quest.data.*;
import de.quest.village.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class KingdomRemoteLifeTest {
 @org.junit.jupiter.api.BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void remoteCrisisIsSavedWithoutTerrainReadsOrAdvancingDecisionTime() {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var actor=UUID.randomUUID();var host=UUID.randomUUID();var other=UUID.randomUUID();
  var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);var quests=mock(QuestState.class);var data=new PlayerQuestData();
  when(player.level()).thenReturn(world);when(player.getUUID()).thenReturn(actor);when(world.getServer()).thenReturn(server);when(server.isSameThread()).thenReturn(true);when(quests.getPlayerData(actor)).thenReturn(data);
  when(state.canManageVillage(actor,host)).thenReturn(true);when(state.crises().current(actor)).thenReturn(Optional.empty());
  var town=mock(LivingKingdomsState.VillageView.class);when(town.id()).thenReturn(other);when(town.name()).thenReturn("Other");when(state.village(other)).thenReturn(Optional.of(town));
  var deal=mock(KingdomDiplomacy.View.class);when(deal.type()).thenReturn(KingdomDiplomacy.Type.SUPPLY);when(deal.status()).thenReturn(KingdomDiplomacy.Status.SIGNED);when(deal.left()).thenReturn(host);when(deal.right()).thenReturn(other);when(state.diplomacy().contracts(host)).thenReturn(List.of(deal));
  var person=mock(KingdomPersonRegistry.View.class);when(state.people().ensure(other,KingdomPersonRegistry.Role.LEADER,0)).thenReturn(Optional.of(person));
  var need=mock(LivingVillageNetworkState.VillageSnapshot.class);when(need.condition()).thenReturn(VillageCondition.STRAINED);when(need.need()).thenReturn(VillageNeed.PASTURE_FODDER);
  when(state.crises().start(actor,host,other,VillageNeed.PASTURE_FODDER,0,0,false)).thenReturn(Optional.of(mock(KingdomCrisis.View.class)));
  try(var states=mockStatic(LivingKingdomsState.class);var saved=mockStatic(QuestState.class);var contacts=mockStatic(KingdomDiplomacyService.class);var needs=mockStatic(KingdomMarketDemandService.class);var news=mockStatic(de.quest.quest.WorldNewsService.class)) {
   states.when(()->LivingKingdomsState.get(server)).thenReturn(state);saved.when(()->QuestState.get(server)).thenReturn(quests);contacts.when(()->KingdomDiplomacyService.contacts(state,data,host)).thenReturn(List.of(town));needs.when(()->KingdomMarketDemandService.current(player,other)).thenReturn(Optional.of(need));
   KingdomCrisisService.consider(player,host);
   verify(state.crises()).start(actor,host,other,VillageNeed.PASTURE_FODDER,0,0,false);verify(state.crises(),never()).advance(any(),anyInt(),anyBoolean());verify(world,never()).hasChunkAt(any());verify(player,never()).blockPosition();
   news.verify(()->de.quest.quest.WorldNewsService.offer(eq(player),eq("crisis"),any(),eq(true)));
  }
 }
}
