package de.quest.quest.special;
import de.quest.data.QuestState;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.economy.CurrencyService;
import de.quest.registry.ModItems;
import de.quest.reward.VanillaRewardService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.raid.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.*;
public final class MariusQuestService {
 private MariusQuestService() {}
 public static boolean peaceful(ServerLevel world) {return world!=null && world.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL;}
 public static Map<Item,Integer> peacefulCosts(int stage) {
  var costs=new LinkedHashMap<Item,Integer>();
  switch(stage) {case 0 -> costs.put(Items.TORCH,16);case 1 -> costs.put(Items.IRON_INGOT,8);case 2 -> {costs.put(Items.BREAD,16);costs.put(Items.SHIELD,1);}default -> {}}
  return Collections.unmodifiableMap(costs);
 }
 public static boolean ready(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
  return p.active("marius") && p.stage("marius")<3 && (peaceful(world)
    ? FriendsQuestService.has(player,peacefulCosts(p.stage("marius"))) : new MariusQuestProgress(p).ready());
 }
 public static net.minecraft.network.chat.Component shortObjective(ServerPlayer player,FriendsQuestProgress p) {
  var rows=new ArrayList<net.minecraft.network.chat.Component>();boolean missing=false;
  for(var cost:peacefulCosts(p.stage("marius")).entrySet()) {
   int count=de.quest.quest.daily.DailyQuestService.countInventoryItem(player,cost.getKey());
   missing|=count<cost.getValue();
   rows.add(de.quest.quest.QuestShortObjective.material(net.minecraft.network.chat.Component.translatable(cost.getKey().getDescriptionId()),count,cost.getValue()));
  }
  if(missing)return de.quest.quest.QuestShortObjective.group(rows);
  return net.minecraft.network.chat.Component.translatable("quest.village-quest.tracker.meet_friend",net.minecraft.network.chat.Component.literal("Marius"));
 }
 public static List<net.minecraft.network.chat.Component> lines(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
  if(!peaceful(world) || p.stage("marius")>=3)return lines(p);
  var result=new ArrayList<net.minecraft.network.chat.Component>();
  result.add(FriendsQuestService.tr("marius.peaceful.objective."+p.stage("marius")));
  for(var cost:peacefulCosts(p.stage("marius")).entrySet())result.add(net.minecraft.network.chat.Component.translatable("screen.village-quest.ui_review.objective_row",net.minecraft.network.chat.Component.translatable(cost.getKey().getDescriptionId()),player==null?0:de.quest.quest.daily.DailyQuestService.countInventoryItem(player,cost.getKey()),cost.getValue()));
  return List.copyOf(result);
 }
 public static void killed(ServerLevel world,ServerPlayer player,Entity entity) {
  var p=FriendsQuestService.progress(world,player.getUUID());
  if(!player.isAlive() || player.isSpectator() || !(entity instanceof Monster) || !p.active("marius") || p.stage("marius")!=0 || world.dimension()!=Level.OVERWORLD)return;
  long time=Math.floorMod(world.getOverworldClockTime(),24000);if(time<13000 || time>=23000 || ShadowsTradeRoadEncounterService.currentVillage(world,entity.blockPosition())==null)return;
  if(new MariusQuestProgress(p).nightKill(entity.getUUID()))FriendsQuestService.changed(world,player);
 }
 public static void repaired(ServerLevel world,ServerPlayer player,IronGolem golem,float healed) {
  var p=FriendsQuestService.progress(world,player.getUUID());if(!p.active("marius") || p.stage("marius")!=1 || player.isSpectator() || !player.isAlive())return;
  if(new MariusQuestProgress(p).repair(golem.getUUID(),healed))FriendsQuestService.changed(world,player);
 }
 public static void raidKill(ServerLevel world,ServerPlayer player,Raider raider) {
  var p=FriendsQuestService.progress(world,player.getUUID());if(!p.active("marius") || p.stage("marius")!=2 || !player.isAlive() || player.isSpectator())return;
  if(p.number("marius.raidPhase")!=2)return;
  var raid=raider.getCurrentRaid();if(raid==null || raid.isOver() || raid.isStopped() || !raid.isActive())return;
  var id=world.getRaids().getId(raid);if(id.isPresent() && p.text("marius.raid").equals(world.dimension().identifier()+":"+id.getAsInt()) && new MariusQuestProgress(p).raidKill(world.dimension().identifier().toString(),id.getAsInt(),raider.getUUID()))FriendsQuestService.changed(world,player);
 }
 public static void raidTick(ServerLevel world,Raid raid) {
  if(!raid.isVictory())return;var id=world.getRaids().getId(raid);if(id.isEmpty())return;
  var state=QuestState.get(world.getServer());
  for(var entry:state.getPlayersView().entrySet()) {
   if(new MariusQuestProgress(new FriendsQuestProgress(entry.getValue())).victory(world.dimension().identifier().toString(),id.getAsInt())) {
    state.setDirty();var player=world.getServer().getPlayerList().getPlayer(entry.getKey());if(player!=null && player.level() instanceof ServerLevel level)FriendsQuestService.changed(level,player);
   }
  }
 }
 public static boolean turnIn(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
  if(!ready(world,player,p))return false;int stage=p.stage("marius");var goods=FriendsQuestRewards.create(world.registryAccess(),"marius",stage+1,false,player.getUUID()).items();
  var data=QuestState.get(world.getServer()).getPlayerData(player.getUUID());
  boolean payout=!de.quest.data.PersonalResetReceipts.friendPaid(data,"marius",stage);
  if(stage==2) for(var item:goods) CustomData.update(DataComponents.CUSTOM_DATA,item,t -> t.putInt("watchGeneration",Math.max(1,p.number("marius.generation"))));
  if((payout || stage==2) && !FriendsQuestService.enqueue(world,player,goods))return false;
  if(peaceful(world))FriendsQuestService.consume(player,peacefulCosts(stage));
  if(!p.complete("marius"))throw new IllegalStateException("Watch quest changed in server turn-in");
  de.quest.data.PersonalResetReceipts.friendPaidThrough(data,"marius",stage+1);
  if(payout && stage<2) {CurrencyService.addBalance(world,player.getUUID(),stage==0?12:20);FriendsQuestService.experience(player,stage==0?2:3);}
  VanillaRewardService.deliverItems(world,player);return true;
 }
 public static boolean useSeal(ServerLevel world,ServerPlayer player,ItemStack stack) {
  var p=FriendsQuestService.progress(world,player.getUUID());var state=new MariusQuestProgress(p);long now=world.getServer().overworld().getGameTime();
  if(!player.isAlive() || player.isSpectator() || !stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("watchOwner","").equals(player.getUUID().toString()) || stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getIntOr("watchGeneration",1)!=Math.max(1,p.number("marius.generation")) || p.stage("marius")!=3) {
   player.sendSystemMessage(FriendsQuestService.tr("marius.seal.unavailable"),false);return false;
  }
  long remaining=state.sealRemaining(now);
  if(remaining!=0) {player.sendSystemMessage(remaining>0?FriendsQuestService.tr("marius.seal.recharging",activeTime(remaining)):FriendsQuestService.tr("marius.seal.charge_needed"),false);return false;}
  if(!state.useSeal(now))return false;QuestState.get(world.getServer()).setDirty();
  player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,2400,0,false,false));
  for(var mob:world.getEntitiesOfClass(Monster.class,player.getBoundingBox().inflate(24),m -> m.isAlive() && player.distanceToSqr(m)<=576))mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,1200,0,false,false));
  player.sendSystemMessage(FriendsQuestService.tr("marius.seal.used"),false);
  return true;
 }
 public static String activeTime(long ticks) {long seconds=ticks/20+(ticks%20==0?0:1);return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
 public static java.util.List<net.minecraft.network.chat.Component> lines(FriendsQuestProgress p) {
  if(p.stage("marius")==0)return List.of(FriendsQuestService.tr("marius.kills",p.values("marius.nightKills").size()));
  if(p.stage("marius")==1)return List.of(FriendsQuestService.tr("marius.healing",p.number("marius.healed")/100));
  var lines=new ArrayList<net.minecraft.network.chat.Component>();
  lines.add(FriendsQuestService.tr("marius.raiders",p.values("marius.raiders").size()));
  if(p.number("marius.victory")==1)lines.add(FriendsQuestService.tr("marius.won"));
  else if(p.number("marius.raidPhase")==0)lines.add(FriendsQuestService.tr("marius.preparation"));
  else if(p.number("marius.raidPhase")==2)lines.add(FriendsQuestService.tr("marius.running"));
  else {int left=p.number("marius.wait");lines.add(FriendsQuestService.tr(left>0?"marius.countdown":"marius.awaiting",String.format(java.util.Locale.ROOT,"%d:%02d",left/1200,left/20%60)));}
  return List.copyOf(lines);
 }
 public static boolean schedule(ServerLevel world,ServerPlayer player,FriendsQuestProgress p,UUID village,BlockPos center) {
  if(peaceful(world) || world.dimension()!=Level.OVERWORLD)return false;
  if(!new MariusQuestProgress(p).schedule(world.getOverworldClockTime()))return false;
  p.number("marius.wait",(int)Math.min(24000,Long.parseLong(p.text("marius.raidDue"))-world.getOverworldClockTime()));
  p.text("marius.village",village.toString());p.text("marius.dimension",world.dimension().identifier().toString());
  p.number("marius.centerX",center.getX());p.number("marius.centerY",center.getY());p.number("marius.centerZ",center.getZ());
  FriendsQuestService.changed(world,player);player.sendSystemMessage(FriendsQuestService.tr("marius.scheduled"),false);return true;
 }
 public static boolean tick(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
  if(peaceful(world))return false;
  if(p.number("marius.victory")==1 || !world.dimension().identifier().toString().equals(p.text("marius.dimension")))return false;
  if(p.number("marius.raidPhase")==2) {
   String raw=p.text("marius.raid");int split=raw.lastIndexOf(':');if(split<0)return false;
   int id;try{id=Integer.parseInt(raw.substring(split+1));}catch(NumberFormatException invalid){return false;}
   var raid=world.getRaids().get(id);
   if(raid!=null && raid.isVictory() && p.values("marius.raiders").size()>=3) {raidTick(world,raid);return false;}
   if(raid==null || raid.isLoss() || raid.isStopped() || raid.isVictory()) {p.number("marius.raidPhase",0);p.text("marius.raiders","");p.text("marius.raid","");player.sendSystemMessage(FriendsQuestService.tr("marius.retry"),false);return true;}
   return false;
  }
  if(p.number("marius.raidPhase")!=1)return false;
  long due;try{due=Long.parseLong(p.text("marius.raidDue"));}catch(NumberFormatException invalid){return false;}
  long clock=world.getOverworldClockTime();int remaining=(int)Math.min(24000,Math.max(0,due-clock));boolean changed=p.number("marius.wait")!=remaining;p.number("marius.wait",remaining);
  long time=Math.floorMod(clock,24000);if(clock<due || time<13000 || time>=23000)return changed;
  var center=new BlockPos(p.number("marius.centerX"),p.number("marius.centerY"),p.number("marius.centerZ"));
  if(!world.hasChunkAt(center) || player.blockPosition().distSqr(center)>96*96 || !world.isVillage(player.blockPosition()) || world.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return changed;
  // Never create/extend a different existing raid. A newly created raid is bound immediately on the same server thread.
  if(world.getRaidAt(center)!=null) {p.number("marius.raidPhase",0);p.text("marius.raidDue","");player.sendSystemMessage(FriendsQuestService.tr("marius.existing"),false);return true;}
  var raid=world.getRaids().createOrExtendRaid(player,center);if(raid==null)return changed;
  var id=world.getRaids().getId(raid);if(id.isEmpty())return changed;
  raid.setRaidOmenLevel(1);world.getRaids().setDirty();
  p.number("marius.raidPhase",2);p.text("marius.raid",world.dimension().identifier()+":"+id.getAsInt());p.text("marius.raiders","");
  QuestState.get(world.getServer()).setDirty();player.sendSystemMessage(FriendsQuestService.tr("marius.started"),false);return true;
 }

}
