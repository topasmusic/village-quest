package de.quest.questmaster;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.QuestState;
import de.quest.reward.*;
import de.quest.network.Payloads;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
final class EarnedQuestRewardsTest {
 @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
 @Test void laterRewardHasPersistentQuestEntryWithClaimActionAndNoObjectives() throws Exception {
  var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
  when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(UUID.randomUUID());
  var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var quests=(QuestState)load.invoke(null,new CompoundTag());
  var state=quests.getPlayerData(player.getUUID()).vanillaRewards(); var offer=new CompoundTag();offer.putInt("version",1);offer.putString("key","daily:SMITH_SMELTING");
  var choices=new ListTag();var choice=new CompoundTag();choice.putString("option","coins");choice.putLong("currency",12);choices.add(choice);offer.put("choices",choices);
  assertTrue(state.accept("daily","old",offer));assertTrue(state.earn("daily","old"));state.resetSlots();
  try(var lookup=mockStatic(QuestState.class)) {
   lookup.when(() -> QuestState.get(server)).thenReturn(quests);
   var entries=EarnedQuestRewards.entries(world,player);assertEquals(1,entries.size());var entry=entries.getFirst();
   assertEquals("daily",entry.categoryId());assertTrue(entry.objectiveLines().isEmpty());assertFalse(entry.rewardLines().isEmpty());
   assertEquals(Payloads.QuestMasterActionPayload.ACTION_CLAIM,entry.primaryAction());assertTrue(entry.primaryEnabled());
   assertTrue(entry.entryId().startsWith(EarnedQuestRewards.PREFIX));
   assertTrue(EarnedQuestRewards.owns(world,player,entry.entryId()));assertFalse(EarnedQuestRewards.owns(world,player,EarnedQuestRewards.PREFIX+UUID.randomUUID()));
   assertEquals(1,PlayerRewardState.fromNbt(state.toNbt()).earned().size());
   String instance=entry.entryId().substring(EarnedQuestRewards.PREFIX.length());state.takeChoice(instance,"coins");assertTrue(EarnedQuestRewards.entries(world,player).isEmpty());
  }
 }
 @Test void twentyLegacyGiftsAreGroupedAndPreservedWhileNewOffersStopAtEight() throws Exception {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);when(world.getServer()).thenReturn(server);when(player.getUUID()).thenReturn(UUID.randomUUID());
  var offer=new CompoundTag();offer.putInt("version",1);offer.putString("key","daily:SMITH_SMELTING");var choices=new ListTag();var choice=new CompoundTag();choice.putString("option","coins");choices.add(choice);offer.put("choices",choices);
  var capped=new PlayerRewardState();for(int i=0;i<8;i++) {assertTrue(capped.accept("daily","d"+i,offer));assertTrue(capped.earn("daily","d"+i));}assertFalse(capped.canAccept());assertFalse(capped.accept("weekly","new",offer));
  var tag=capped.toNbt();var earned=new ListTag();for(int i=0;i<20;i++) {var e=offer.copy();e.putString("slot","daily");e.putString("instance",UUID.randomUUID().toString());earned.add(e);}tag.put("earned",earned);
  var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var quests=(QuestState)load.invoke(null,new CompoundTag());
  quests.getPlayerData(player.getUUID()).vanillaRewards().pending(new ListTag());
  try(var lookup=mockStatic(QuestState.class);var rewards=mockStatic(VanillaRewardService.class,CALLS_REAL_METHODS)) {
   lookup.when(() -> QuestState.get(server)).thenReturn(quests);var restored=PlayerRewardState.fromNbt(tag);assertTrue(restored.writable());assertEquals(20,restored.earned().size());assertFalse(restored.canAccept());
   rewards.when(() -> VanillaRewardService.state(world,player.getUUID())).thenReturn(restored);
   assertEquals(1,EarnedQuestRewards.entries(world,player).size());var entry=EarnedQuestRewards.entries(world,player).getFirst();assertTrue(EarnedQuestRewards.owns(world,player,entry.entryId()));
   restored.takeChoice(entry.entryId().substring(EarnedQuestRewards.PREFIX.length()),"coins");assertEquals(19,restored.earned().size());assertEquals(1,EarnedQuestRewards.entries(world,player).size());
   assertEquals(19,PlayerRewardState.fromNbt(restored.toNbt()).earned().size());
  }
 }
}
