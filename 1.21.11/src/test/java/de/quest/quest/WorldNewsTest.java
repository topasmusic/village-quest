package de.quest.quest;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WorldNewsTest {
 @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void onlyThreeVisibleImportantRowsReceiveDeliveryMarkers() {
  var deliveries=new java.util.concurrent.atomic.AtomicInteger();var news=new WorldNewsService();
  for(int i=0;i<4;i++)news.offer("guest:"+i,Component.literal("guest"+i),true,deliveries::incrementAndGet);
  assertNotNull(news.poll(6000));news.acknowledge();assertEquals(3,deliveries.get());
 }
 @Test void disabledPreferenceDoesNotQueueOrAcknowledgeAnArrival() {
  var player=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);var delivered=new java.util.concurrent.atomic.AtomicInteger();
  try(var preferences=org.mockito.Mockito.mockStatic(de.quest.config.ClientPreferenceService.class)) {
   WorldNewsService.offer(player,"guest",Component.literal("guest"),true,delivered::incrementAndGet);
   assertEquals(0,delivered.get());org.mockito.Mockito.verifyNoInteractions(player);
  }
 }
 @Test void deliveryMarkerIsAcknowledgedOnlyForVisibleRowsAfterActualSend() {
  var delivered=new java.util.concurrent.atomic.AtomicInteger();var news=new WorldNewsService();
  news.offer("guest",Component.literal("guest"),true,delivered::incrementAndGet);
  assertNull(news.poll(5999));assertEquals(0,delivered.get());assertNotNull(news.poll(6000));assertEquals(0,delivered.get());
  news.acknowledge();assertEquals(1,delivered.get());news.acknowledge();assertEquals(1,delivered.get());
 }
 @Test void routineReportsCoalesceAndNeverCreateMinuteByMinuteChat() {
  var news=new WorldNewsService();news.offer("route:a",Component.literal("old"),false);news.offer("route:a",Component.literal("latest"),false);news.offer("guest:a",Component.literal("guest"),true);
  assertNull(news.poll(0));assertNull(news.poll(5999));var first=news.poll(6000);assertNotNull(first);assertTrue(first.getString().contains("latest"));assertFalse(first.getString().contains("old"));assertTrue(first.getString().contains("guest"));
  news.offer("route:b",Component.literal("next"),false);assertNull(news.poll(11999));assertNotNull(news.poll(12000));assertNull(news.poll(18000));
 }
 @Test void boundedBatchKeepsImportantWorldEventsAheadOfRoutineReports() {
  var news=new WorldNewsService();for(int i=0;i<100;i++)news.offer("route:"+i,Component.literal("route"+i),false);news.offer("crisis",Component.literal("important"),true);
  var report=news.poll(6000);assertNotNull(report);assertTrue(report.getString().contains("important"));assertTrue(report.getString().contains("message.village-quest.world_news.more"));assertNull(news.poll(12000));
 }
 @Test void clockRollbackRebasesWithoutInstantBurstAndEmptyPollDoesNotConsumeBudget() {
  var news=new WorldNewsService();assertNull(news.poll(1000));news.offer("one",Component.literal("one"),true);assertNotNull(news.poll(6000));
  news.offer("two",Component.literal("two"),true);assertNull(news.poll(0));assertNull(news.poll(5999));assertNotNull(news.poll(6000));
 }
}
