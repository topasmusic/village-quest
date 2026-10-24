package de.quest.data;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Saved ledgers only; no server/world execution or test command remains. */
final class RetiredNpcTestMigrationTest {
 @Test void reloadRestoresOriginalNpcStateForOfflineOwnersExactlyOnce() {
  var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var p=state.getPlayerData(id);
  p.setTradeRouteInt("temp.npctest.step",5);
  p.setTradeRouteInt("temp.npctest.backup.friends.simon.stage",3);
  p.setTradeRouteInt("temp.npctest.backup.friends.marius.healed",2450);
  p.setTradeRouteString("temp.npctest.backup.friends.marius.sealUntil","987654");
  p.setTradeRouteInt("friends.marius.stage",2);p.setTradeRouteInt("friends.marius.active",1);
  p.setTradeRouteString("friends.marius.raid","minecraft:overworld:42");
  p.setTradeRouteInt("route.delivery",9);p.setCurrencyBalance(42);
  var loaded=QuestState.fromNbt(QuestState.toNbt(state));p=loaded.getPlayerData(id);
  assertEquals(3,p.getTradeRouteInt("friends.simon.stage"));assertEquals(2450,p.getTradeRouteInt("friends.marius.healed"));
  assertEquals("987654",p.getTradeRouteString("friends.marius.sealUntil"));assertEquals(0,p.getTradeRouteInt("friends.marius.active"));
  assertEquals("",p.getTradeRouteString("friends.marius.raid"));assertEquals(9,p.getTradeRouteInt("route.delivery"));assertEquals(42,p.getCurrencyBalance());
  assertTrue(p.getTradeRouteIntState().keySet().stream().noneMatch(k->k.startsWith("temp.npctest.")));
  assertTrue(p.getTradeRouteStringState().keySet().stream().noneMatch(k->k.startsWith("temp.npctest.")));assertTrue(loaded.isDirty());
  var ints=new HashMap<>(p.getTradeRouteIntState());var strings=new HashMap<>(p.getTradeRouteStringState());
  p=QuestState.fromNbt(QuestState.toNbt(loaded)).getPlayerData(id);assertEquals(ints,p.getTradeRouteIntState());assertEquals(strings,p.getTradeRouteStringState());
 }
 @Test void originallyEmptyNpcLedgerRestoresEmptyWithoutTouchingOtherProgress() {
  var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var p=state.getPlayerData(id);
  p.setTradeRouteInt("temp.npctest.step",6);p.setTradeRouteInt("friends.marius.stage",3);p.setTradeRouteString("friends.marius.sealUntil","12000");
  p.setTradeRouteInt("other.progress",7);p.setTradeRouteString("other.name","preserve");
  p=QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(id);
  assertEquals(Map.of("other.progress",7),p.getTradeRouteIntState());assertEquals(Map.of("other.name","preserve"),p.getTradeRouteStringState());
 }
 @Test void normalNpcProgressAndUnknownMarkersRemainUntouched() {
  for(int marker:new int[]{0,99}) {
   var state=QuestState.fromNbt(new CompoundTag());var id=UUID.randomUUID();var p=state.getPlayerData(id);
   p.setTradeRouteInt("temp.npctest.step",marker);p.setTradeRouteInt("friends.lutte.active",1);p.setTradeRouteInt("friends.lutte.signals",2);
   var before=new HashMap<>(p.getTradeRouteIntState());p=QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(id);assertEquals(before,p.getTradeRouteIntState());
  }
 }
}
