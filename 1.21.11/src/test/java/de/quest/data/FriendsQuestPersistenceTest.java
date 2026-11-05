package de.quest.data;
import de.quest.quest.special.FriendsQuestProgress;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FriendsQuestPersistenceTest {
 @Test void personalStagesStatsAndContactsSurviveReloadWithoutReplay() {
  var state=QuestState.fromNbt(new CompoundTag()); var id=UUID.randomUUID(); var p=new FriendsQuestProgress(state.getPlayerData(id));
  assertTrue(p.accept("simon",0,0,0)); assertFalse(p.accept("simon",0,0,0)); assertTrue(p.complete("simon"));
  assertTrue(p.accept("simon",100,200,300)); assertEquals(19999,p.walking(2000099,200,300));
  assertEquals(20000,p.walking(2000100,200,300)); assertTrue(p.complete("simon")); assertFalse(p.complete("simon"));
  assertTrue(p.accept("simon",0,0,0)); var village=UUID.randomUUID(); assertTrue(p.village(village,"plains")); assertFalse(p.village(village,"desert"));
  p.village(UUID.randomUUID(),"plains"); p.village(UUID.randomUUID(),"plains"); assertFalse(p.friendsReady()); p.village(UUID.randomUUID(),"desert"); assertTrue(p.friendsReady());
  var restored=QuestState.fromNbt(QuestState.toNbt(state)); var r=new FriendsQuestProgress(restored.getPlayerData(id));
  assertTrue(r.friendsReady()); assertEquals(2,r.stage("simon")); assertTrue(r.active("simon")); assertEquals(0,new FriendsQuestProgress(restored.getPlayerData(UUID.randomUUID())).stage("simon"));
 }
 @Test void herbsCannotRerollByTalkingOrReloading() {
  var p=new FriendsQuestProgress(new PlayerQuestData()); p.number("simon.stage",3); p.text("simon.finishVisit","a");
  assertFalse(p.rollHerbs("a",0)); assertFalse(p.rollHerbs("b",1)); assertFalse(p.rollHerbs("b",0)); assertTrue(p.rollHerbs("c",0));
 }
 @Test void walkingUsesSeparateNativeCountersWithLongArithmeticAndKeepsEarnedDistance() {
  var p=new FriendsQuestProgress(new PlayerQuestData()); p.number("simon.stage",1); p.accept("simon",1000,2000,3000);
  assertEquals(6,p.walking(1100,2200,3300)); assertEquals(6,p.walking(0,0,0)); assertEquals(20000,p.walking(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE));
 }
 @Test void returningToAnOlderConcurrentVisitCannotRollAgain() {
  var p=new FriendsQuestProgress(new PlayerQuestData());p.number("simon.stage",3);
  assertFalse(p.rollHerbs("village-a:100",1));assertFalse(p.rollHerbs("village-b:101",1));assertFalse(p.rollHerbs("village-a:100",0));assertTrue(p.rollHerbs("village-a:102",0));
 }
 @Test void replacementOrdersPersistCostsTierDeadlineAndGenerationWithoutTalkReset() {
  var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var p=new FriendsQuestProgress(state.getPlayerData(id));
  p.number("lutte.stage",1);p.number("lutte.active",1);p.number("lutte.generation",1);
  assertTrue(p.orderReplacement(100));assertFalse(p.orderReplacement(200));assertFalse(p.replacementReady(48099));assertTrue(p.replacementReady(48100));
  state=QuestState.fromNbt(QuestState.toNbt(state));p=new FriendsQuestProgress(state.getPlayerData(id));assertEquals("48100",p.text("lutte.replacementDue"));assertEquals(1,p.number("lutte.replacements"));assertEquals(2,p.number("lutte.generation"));
  p.text("lutte.replacementDue","");assertTrue(p.orderReplacement(50000));assertEquals("98000",p.text("lutte.replacementDue"));
  p.text("lutte.replacementDue","");assertTrue(p.orderReplacement(100000));assertEquals("436000",p.text("lutte.replacementDue"));
  p.text("lutte.replacementDue","");assertTrue(p.orderReplacement(500000));assertEquals("836000",p.text("lutte.replacementDue"));assertEquals(4,p.number("lutte.replacements"));
 }

 @Test void manySameBiomeVillagesCannotFillAProgressCapAndBlockTheSecondBiome() {
  var p=new FriendsQuestProgress(new PlayerQuestData());p.number("simon.stage",2);p.accept("simon",0,0,0);
  for(int i=0;i<256;i++)p.village(UUID.randomUUID(),"plains");assertEquals(3,p.values("simon.villages").size());
  assertFalse(p.friendsReady());assertTrue(p.village(UUID.randomUUID(),"desert"));assertTrue(p.friendsReady());
 }

}
