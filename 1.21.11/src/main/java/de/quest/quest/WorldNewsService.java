package de.quest.quest;

import de.quest.config.ClientPreferenceService;
import de.quest.data.QuestState;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Optional routine reports only. Authoritative progress stays in its existing saved journal. */
public final class WorldNewsService {
 private record Notice(Component text,boolean important,Runnable delivered) {}
 private static final Map<MinecraftServer,Map<UUID,WorldNewsService>> RUNTIME=new WeakHashMap<>();
 private static final long INTERVAL=6000;
 private final LinkedHashMap<String,Notice> pending=new LinkedHashMap<>();
 private List<Runnable> awaitingAcknowledgement=List.of();
 private long next=INTERVAL,lastClock=-1;private int overflow;
 public static void register() {
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(WorldNewsService::tick);
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(RUNTIME::remove);
  net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)-> {
   var players=RUNTIME.get(server);if(players!=null)players.remove(handler.player.getUUID());
  });
 }
 public static void offer(ServerPlayer player,String identity,Component text,boolean important) {offer(player,identity,text,important,()->{});}
 public static void offer(ServerPlayer player,String identity,Component text,boolean important,Runnable delivered) {
  if(player==null || !ClientPreferenceService.caravanEventNotifications(player))return;
  var server=player.level().getServer();long now=server.overworld().getGameTime();
  var players=RUNTIME.computeIfAbsent(server,k->new HashMap<>());
  var queue=players.computeIfAbsent(player.getUUID(),id->{
   var q=new WorldNewsService();long saved=0;
   try{saved=Long.parseLong(QuestState.get(server).getPlayerData(id).getTradeRouteString("world_news.next"));}catch(NumberFormatException ignored){}
   // First report may arrive after a short gathering period; reconnect cannot bypass a saved gap.
   q.next=Math.max(now+400,Math.min(now+INTERVAL,saved));return q;
  });
  queue.offer(identity,text,important,delivered);
 }
 void offer(String identity,Component text,boolean important) {offer(identity,text,important,()->{});}
 void offer(String identity,Component text,boolean important,Runnable delivered) {
  if(identity==null || text==null)return;
  if(!pending.containsKey(identity) && pending.size()>=32) {
   var victim=pending.entrySet().stream().filter(e->!e.getValue().important()).map(Map.Entry::getKey).findFirst();
   if(victim.isEmpty()) {overflow=Math.min(9999,overflow+1);return;}
   pending.remove(victim.get());overflow=Math.min(9999,overflow+1);
  }
  pending.put(identity,new Notice(text.copy(),important,delivered));
 }
 Component poll(long now) {
  if(now<0)return null;
  if(lastClock>=0 && now<lastClock)next=now+INTERVAL;
  lastClock=now;
  if(pending.isEmpty() || now<next)return null;
  var rows=pending.values().stream().sorted(Comparator.comparing(Notice::important).reversed()).toList();
  var report=Component.translatable("message.village-quest.world_news.title");
  for(var row:rows.stream().limit(3).toList())report.append("\n").append(row.text());
  int extra=overflow+Math.max(0,rows.size()-3);
  if(extra>0)report.append("\n").append(Component.translatable("message.village-quest.world_news.more",extra));
  awaitingAcknowledgement=rows.stream().limit(3).map(Notice::delivered).toList();
  pending.clear();overflow=0;next=now+INTERVAL;return report;
 }
 void acknowledge() {var callbacks=awaitingAcknowledgement;awaitingAcknowledgement=List.of();for(var delivered:callbacks)delivered.run();}
 private static void tick(MinecraftServer server) {
  if(server.overworld().getGameTime()%20!=0)return;
  var queues=RUNTIME.get(server);if(queues==null)return;
  for(var player:server.getPlayerList().getPlayers()) {
   var queue=queues.get(player.getUUID());if(queue==null)continue;
   if(!ClientPreferenceService.caravanEventNotifications(player)) {queues.remove(player.getUUID());continue;}
   var message=queue.poll(server.overworld().getGameTime());if(message==null)continue;
   player.sendSystemMessage(message,false);queue.acknowledge();var state=QuestState.get(server);
   state.getPlayerData(player.getUUID()).setTradeRouteString("world_news.next",Long.toString(queue.next));state.setDirty();
  }
 }
}
