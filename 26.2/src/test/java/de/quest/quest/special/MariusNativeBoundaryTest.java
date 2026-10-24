package de.quest.quest.special;

import de.quest.data.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native boundaries are mocks: no levels, entity ticks or running Minecraft server. */
final class MariusNativeBoundaryTest {
 private MinecraftServer server;private ServerLevel world;private ServerPlayer player;private QuestState quests;private FriendsQuestProgress p;private Raids raids;private UUID id;
 private MockedStatic<QuestState> stateMock;private MockedStatic<FriendsQuestService> friends;
 @BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @BeforeEach void setup() {
  server=mock(MinecraftServer.class);world=mock(ServerLevel.class);player=mock(ServerPlayer.class);quests=mock(QuestState.class);raids=mock(Raids.class);id=UUID.randomUUID();p=new FriendsQuestProgress(new PlayerQuestData());
  when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(world.dimension()).thenReturn(Level.OVERWORLD);when(world.getOverworldClockTime()).thenReturn(13000L);when(world.getRaids()).thenReturn(raids);
  when(player.getUUID()).thenReturn(id);when(player.isAlive()).thenReturn(true);when(player.blockPosition()).thenReturn(BlockPos.ZERO);when(player.level()).thenReturn(world);when(quests.getPlayerData(id)).thenReturn(new PlayerQuestData());
  stateMock=mockStatic(QuestState.class);stateMock.when(()->QuestState.get(server)).thenReturn(quests);
  friends=mockStatic(FriendsQuestService.class,CALLS_REAL_METHODS);friends.when(()->FriendsQuestService.progress(world,id)).thenReturn(p);friends.when(()->FriendsQuestService.changed(world,player)).thenAnswer(c->null);
  p.number("marius.stage",2);p.accept("marius",0,0,0);new MariusQuestProgress(p).schedule(12000);
  p.text("marius.dimension","minecraft:overworld");
  clearInvocations(quests);
 }
 @AfterEach void close() {friends.close();stateMock.close();}
 private void local() {when(world.hasChunkAt(BlockPos.ZERO)).thenReturn(true);when(world.isVillage(BlockPos.ZERO)).thenReturn(true);when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);}
 private net.minecraft.world.item.ItemStack seal(UUID owner) {
  var stack=mock(net.minecraft.world.item.ItemStack.class);var tag=new net.minecraft.nbt.CompoundTag();tag.putString("watchOwner",owner.toString());
  when(stack.getOrDefault(eq(net.minecraft.core.component.DataComponents.CUSTOM_DATA),any())).thenReturn(net.minecraft.world.item.component.CustomData.of(tag));return stack;
 }
 @Test void earnedSealWorksOutsideVillagesWithExactDurationsAndPersonalRecharge() {
  p.number("marius.stage",3);var stack=seal(id);when(world.getGameTime()).thenReturn(100L);
  when(player.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(0,0,0,1,2,1));
  var monster=mock(net.minecraft.world.entity.monster.Monster.class);
  when(world.getEntitiesOfClass(eq(net.minecraft.world.entity.monster.Monster.class),any(),any())).thenReturn(List.of(monster));
   assertTrue(MariusQuestService.useSeal(world,player,stack));
   verify(player).addEffect(argThat(effect->effect.getEffect().equals(net.minecraft.world.effect.MobEffects.NIGHT_VISION) && effect.getDuration()==2400));
   verify(monster).addEffect(argThat(effect->effect.getEffect().equals(net.minecraft.world.effect.MobEffects.GLOWING) && effect.getDuration()==1200));
   when(world.getGameTime()).thenReturn(1300L);assertFalse(MariusQuestService.useSeal(world,player,seal(id)));
   verify(player).sendSystemMessage(eq(FriendsQuestService.tr("marius.seal.recharging","9:00")),eq(false));
   verify(player,times(1)).addEffect(any());assertEquals("12100",p.text("marius.sealUntil"));
 }
 @Test void foreignAndUnearnedSealsCannotStartCooldownOrEffects() {
  p.number("marius.stage",3);assertFalse(MariusQuestService.useSeal(world,player,seal(UUID.randomUUID())));
  p.number("marius.stage",2);assertFalse(MariusQuestService.useSeal(world,player,seal(id)));
  verify(player,never()).addEffect(any());assertTrue(p.text("marius.sealUntil").isBlank());
 }
 @Test void repairsIncludePlayerBuiltGolemsOutsideVillagesAndOverworld() {
  p.number("marius.stage",1);when(world.dimension()).thenReturn(Level.NETHER);
  var golem=mock(net.minecraft.world.entity.animal.golem.IronGolem.class);
  when(golem.getUUID()).thenReturn(UUID.randomUUID());when(golem.isPlayerCreated()).thenReturn(true);
  MariusQuestService.repaired(world,player,golem,25);
  var other=mock(net.minecraft.world.entity.animal.golem.IronGolem.class);when(other.getUUID()).thenReturn(UUID.randomUUID());
  MariusQuestService.repaired(world,player,other,25);assertEquals(5000,p.number("marius.healed"));
  friends.verify(()->FriendsQuestService.changed(world,player),times(2));
 }
 @Test void unloadedOrDistantVillageNeverStartsOrLoadsARaid() {
  assertFalse(MariusQuestService.tick(world,player,p));verifyNoInteractions(raids);verify(world,never()).getChunk(anyInt(),anyInt());assertEquals(1,p.number("marius.raidPhase"));
  local();when(player.blockPosition()).thenReturn(new BlockPos(1000,0,0));MariusQuestService.tick(world,player,p);verifyNoInteractions(raids);
 }
 @Test void localNightStartsExactlyOneVanillaRaidAtLevelOne() {
  local();var raid=mock(Raid.class);when(raids.createOrExtendRaid(player,BlockPos.ZERO)).thenReturn(raid);when(raids.getId(raid)).thenReturn(OptionalInt.of(42));when(raids.get(42)).thenReturn(raid);
  assertTrue(MariusQuestService.tick(world,player,p));assertEquals(2,p.number("marius.raidPhase"));assertEquals("minecraft:overworld:42",p.text("marius.raid"));verify(raid).setRaidOmenLevel(1);
  MariusQuestService.tick(world,player,p);verify(raids,times(1)).createOrExtendRaid(player,BlockPos.ZERO);
 }
 @Test void existingRaidReturnsToPreparationWithoutExtensionOrAutomaticRetry() {
  local();when(world.getRaidAt(BlockPos.ZERO)).thenReturn(mock(Raid.class));assertTrue(MariusQuestService.tick(world,player,p));assertEquals(0,p.number("marius.raidPhase"));
  MariusQuestService.tick(world,player,p);verify(raids,never()).createOrExtendRaid(any(),any());
 }
 @Test void defeatAndVictoryWithoutThreeKillsBothResetOnlyThisAttempt() {
  for(boolean win:List.of(false,true)) {
   var raid=mock(Raid.class);when(raid.isVictory()).thenReturn(win);when(raid.isLoss()).thenReturn(!win);when(raids.get(42)).thenReturn(raid);
   p.number("marius.raidPhase",2);p.text("marius.raid","minecraft:overworld:42");p.text("marius.raiders",UUID.randomUUID().toString());
   assertTrue(MariusQuestService.tick(world,player,p));assertEquals(0,p.number("marius.raidPhase"));assertEquals(2,p.stage("marius"));assertTrue(p.active("marius"));assertTrue(p.values("marius.raiders").isEmpty());
  }
 }
 @Test void victoryCreditsMatchingOfflineLedgerOnlyOnce() {
  p.number("marius.raidPhase",2);p.text("marius.raid","minecraft:overworld:42");var m=new MariusQuestProgress(p);for(int i=0;i<3;i++)m.raidKill("minecraft:overworld",42,UUID.randomUUID());
  // Use the actual saved player object for the offline sweep.
  var offline=new PlayerQuestData();var saved=new FriendsQuestProgress(offline);saved.number("marius.stage",2);saved.number("marius.active",1);saved.number("marius.raidPhase",2);saved.text("marius.raid",p.text("marius.raid"));saved.text("marius.raiders",p.text("marius.raiders"));
  when(quests.getPlayersView()).thenReturn(Map.of(id,offline));var list=mock(net.minecraft.server.players.PlayerList.class);when(server.getPlayerList()).thenReturn(list);
  var raid=mock(Raid.class);when(raid.isVictory()).thenReturn(true);when(raids.getId(raid)).thenReturn(OptionalInt.of(42));
  MariusQuestService.raidTick(world,raid);assertTrue(new MariusQuestProgress(saved).ready());MariusQuestService.raidTick(world,raid);verify(quests,times(1)).setDirty();
 }
}
