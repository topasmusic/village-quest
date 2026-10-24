package de.quest.data;

import de.quest.quest.special.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Saved policy only: no world, server, raid simulation or entity ticking. */
final class MariusQuestProgressTest {
 private FriendsQuestProgress stage(int stage) {
  var p=new FriendsQuestProgress(new PlayerQuestData());p.number("marius.stage",stage);p.accept("marius",0,0,0);return p;
 }
 @Test void killsRequireAcceptanceAndDedupeAcrossPersistence() {
  var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var p=new FriendsQuestProgress(state.getPlayerData(id));var m=new MariusQuestProgress(p);var mob=UUID.randomUUID();
  assertFalse(m.nightKill(mob));p.accept("marius",0,0,0);assertTrue(m.nightKill(mob));assertFalse(m.nightKill(mob));
  for(int i=0;i<13;i++)m.nightKill(UUID.randomUUID());assertFalse(m.ready());m.nightKill(UUID.randomUUID());assertTrue(m.ready());
  state=QuestState.fromNbt(QuestState.toNbt(state));p=new FriendsQuestProgress(state.getPlayerData(id));m=new MariusQuestProgress(p);assertTrue(m.ready());assertFalse(m.nightKill(mob));
  assertTrue(p.complete("marius"));assertFalse(p.complete("marius"));assertFalse(m.nightKill(UUID.randomUUID()));
 }
 @Test void healingAcrossGolemsPreservesLegacyProgressAndCountsOnlyFinitePositiveActualDelta() {
  var p=stage(1);var m=new MariusQuestProgress(p);var golem=UUID.randomUUID();
  for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})assertFalse(m.repair(golem,invalid));
  p.text("marius.golem",golem.toString());p.number("marius.healed",2450);
  assertTrue(m.repair(UUID.randomUUID(),25));assertFalse(m.ready());assertTrue(m.repair(UUID.randomUUID(),0.5f));assertTrue(m.ready());assertEquals(5000,p.number("marius.healed"));assertFalse(m.repair(golem,1));
 }
 @Test void raidCannotSwitchIdentityOrCountBeforeExplicitStart() {
  var p=stage(2);var m=new MariusQuestProgress(p);assertFalse(m.raidKill("minecraft:overworld",1,UUID.randomUUID()));
  p.number("marius.raidPhase",2);p.text("marius.raid","minecraft:overworld:1");var mob=UUID.randomUUID();
  assertTrue(m.raidKill("minecraft:overworld",1,mob));assertFalse(m.raidKill("minecraft:overworld",1,mob));
  assertFalse(m.raidKill("minecraft:overworld",2,UUID.randomUUID()));assertFalse(m.raidKill("minecraft:the_nether",1,UUID.randomUUID()));assertEquals(1,p.values("marius.raiders").size());
  assertFalse(m.victory("minecraft:overworld",1));m.raidKill("minecraft:overworld",1,UUID.randomUUID());m.raidKill("minecraft:overworld",1,UUID.randomUUID());assertFalse(m.victory("minecraft:overworld",2));assertTrue(m.victory("minecraft:overworld",1));assertTrue(m.ready());assertFalse(m.victory("minecraft:overworld",1));
 }
 @Test void readinessSchedulesNextNightOnlyAndCannotReroll() {
  var p=stage(2);var m=new MariusQuestProgress(p);assertTrue(m.schedule(12000));assertEquals("13000",p.text("marius.raidDue"));assertFalse(m.schedule(12001));
  p.number("marius.raidPhase",0);assertTrue(m.schedule(13000));assertEquals("37000",p.text("marius.raidDue"));
  p.number("marius.raidPhase",0);assertFalse(m.schedule(-1));assertFalse(m.schedule(Long.MAX_VALUE));
 }
 @Test void sealCooldownIsPersonalDurableAndCannotBeResetByAnotherCopy() {
  var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var p=new FriendsQuestProgress(state.getPlayerData(id));var m=new MariusQuestProgress(p);
  assertFalse(m.useSeal(0));p.number("marius.stage",3);assertTrue(m.useSeal(100));assertFalse(new MariusQuestProgress(p).useSeal(100));
  state=QuestState.fromNbt(QuestState.toNbt(state));p=new FriendsQuestProgress(state.getPlayerData(id));m=new MariusQuestProgress(p);assertFalse(m.useSeal(12099));assertTrue(m.useSeal(12100));assertFalse(m.useSeal(12000));
  p.text("marius.sealUntil","broken");assertFalse(m.useSeal(99999));
 }
}
