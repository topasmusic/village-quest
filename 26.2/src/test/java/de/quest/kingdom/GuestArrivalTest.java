package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class GuestArrivalTest {
 @Test void logoutBeforeFlushCanRequeueCurrentVisitUntilExactlyOneDeliveryIsAcknowledged() {
  var world=mock(net.minecraft.server.level.ServerLevel.class);var server=mock(net.minecraft.server.MinecraftServer.class);var player=mock(net.minecraft.server.level.ServerPlayer.class);var list=mock(net.minecraft.server.players.PlayerList.class);
  var state=mock(LivingKingdomsState.class);var quests=mock(de.quest.data.QuestState.class);var town=mock(LivingKingdomsState.VillageView.class);var village=UUID.randomUUID();var actor=UUID.randomUUID();var data=new PlayerQuestData();var callbacks=new ArrayList<Runnable>();
  when(world.getServer()).thenReturn(server);when(server.getPlayerList()).thenReturn(list);when(list.getPlayers()).thenReturn(List.of(player));when(player.getUUID()).thenReturn(actor);when(quests.getPlayerData(actor)).thenReturn(data);
  when(state.village(village)).thenReturn(Optional.of(town));when(state.canManageVillage(actor,village)).thenReturn(true);when(town.name()).thenReturn("Home");when(town.legacyKey()).thenReturn(de.quest.village.VillageLifeState.VillageKey.overworld(10,20));
  try(var saved=mockStatic(de.quest.data.QuestState.class);var news=mockStatic(de.quest.quest.WorldNewsService.class)) {
   saved.when(()->de.quest.data.QuestState.get(server)).thenReturn(quests);
   news.when(()->de.quest.quest.WorldNewsService.offer(eq(player),anyString(),any(),eq(true),any(Runnable.class))).thenAnswer(call->{callbacks.add(call.getArgument(4));return null;});
   KingdomTavernGuestService.announce(world,state,village,10);assertEquals("",data.getTradeRouteString("kingdoms.guests.arrival."+village));
   // Disconnect drops the first runtime queue/callback. The saved visit still exists on reconnect.
   callbacks.clear();KingdomTavernGuestService.announce(world,state,village,10);assertEquals(1,callbacks.size());callbacks.getFirst().run();
   assertEquals("10",data.getTradeRouteString("kingdoms.guests.arrival."+village));KingdomTavernGuestService.announce(world,state,village,10);assertEquals(1,callbacks.size());
  }
 }
 @org.junit.jupiter.api.BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void savedRemoteVisitCreatesItsMissingGuestIdentityOnLocalObservation() {
  var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);var world=mock(net.minecraft.server.level.ServerLevel.class);var server=mock(net.minecraft.server.MinecraftServer.class);
  var ledger=mock(KingdomTavernGuests.class);var village=UUID.randomUUID();var inn=mock(KingdomAnchorRegistry.AnchorView.class);var town=mock(LivingKingdomsState.VillageView.class);var guest=mock(KingdomPersonRegistry.View.class);var life=mock(de.quest.village.VillageLifeState.class);
  var key=de.quest.village.VillageLifeState.VillageKey.overworld(10,20);when(world.getServer()).thenReturn(server);when(state.tavernGuests()).thenReturn(ledger);when(ledger.active(village)).thenReturn(2);when(inn.villageId()).thenReturn(village);when(inn.id()).thenReturn(UUID.randomUUID());when(inn.phase()).thenReturn(BuildingOperation.Phase.ACTIVE);
  when(state.anchors().official(village,BuildingScanner.Role.INN)).thenReturn(Optional.of(inn));when(state.village(village)).thenReturn(Optional.of(town));when(town.legacyKey()).thenReturn(key);when(life.status(key)).thenReturn(de.quest.village.VillageLifeState.Status.ACTIVE);
  when(state.people().staffGuest(village,2)).thenReturn(Optional.empty());when(state.people().ensure(village,KingdomPersonRegistry.Role.INNKEEPER,3)).thenReturn(Optional.of(guest));
  try(var nativeState=mockStatic(de.quest.village.VillageLifeState.class)) {
   nativeState.when(()->de.quest.village.VillageLifeState.get(server)).thenReturn(life);
   assertEquals(Optional.of(guest),KingdomTavernGuestService.observe(state,world,inn,0,false));
   verify(state.people()).ensure(village,KingdomPersonRegistry.Role.INNKEEPER,3);
  }
 }
 @Test void remoteArrivalUsesSavedEvidenceWithoutTerrainEntityOrNearbyPlayerChecks() {
  var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);var world=mock(net.minecraft.server.level.ServerLevel.class);var server=mock(net.minecraft.server.MinecraftServer.class);
  var ledger=mock(KingdomTavernGuests.class);var village=UUID.randomUUID();var inn=mock(KingdomAnchorRegistry.AnchorView.class);var innId=UUID.randomUUID();var town=mock(LivingKingdomsState.VillageView.class);
  var key=de.quest.village.VillageLifeState.VillageKey.overworld(10,20);var life=mock(de.quest.village.VillageLifeState.class);
  when(state.tavernGuests()).thenReturn(ledger);when(ledger.ready(village)).thenReturn(true);when(ledger.active(village)).thenReturn(-1);
  when(inn.id()).thenReturn(innId);when(inn.villageId()).thenReturn(village);when(inn.phase()).thenReturn(BuildingOperation.Phase.ACTIVE);when(state.anchors().official(village,BuildingScanner.Role.INN)).thenReturn(Optional.of(inn));
  when(state.village(village)).thenReturn(Optional.of(town));when(town.legacyKey()).thenReturn(key);when(state.population(village)).thenReturn(Optional.of(new VillagePopulationHistory.View(2,1,false,false,false)));
  when(world.getServer()).thenReturn(server);when(world.getOverworldClockTime()).thenReturn(25000L);when(world.getRandom()).thenReturn(mock(net.minecraft.util.RandomSource.class));when(life.status(key)).thenReturn(de.quest.village.VillageLifeState.Status.ACTIVE);
  try(var nativeState=mockStatic(de.quest.village.VillageLifeState.class)) {
   nativeState.when(()->de.quest.village.VillageLifeState.get(server)).thenReturn(life);KingdomTavernGuestService.remote(state,world,inn);
   verify(ledger).draw(eq(village),eq(25000L),eq(true),any());verify(world,never()).hasChunkAt(any());verify(world,never()).getRaidAt(any());verify(server,never()).getPlayerList();
  }
 }
 @Test void noticesArePersonalAndOncePerVillageVisitEvenAfterReturningToAnOlderVisit() {
  var p=new PlayerQuestData();var home=UUID.randomUUID();var other=UUID.randomUUID();
  assertFalse(KingdomTavernGuestService.markArrival(p,home,-1));assertTrue(KingdomTavernGuestService.markArrival(p,home,10));assertFalse(KingdomTavernGuestService.markArrival(p,home,10));
  assertTrue(KingdomTavernGuestService.markArrival(p,other,11));assertFalse(KingdomTavernGuestService.markArrival(p,home,9));assertTrue(KingdomTavernGuestService.markArrival(p,home,12));
  assertTrue(KingdomTavernGuestService.markArrival(new PlayerQuestData(),home,12));
 }
}
