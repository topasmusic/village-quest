package de.quest.quest.special;
import de.quest.reward.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class LutteRewardChoiceTest {
 @Test void finalRegistryOffersExactlyOneOfTwoUngatedAuthoredTools() {
  var options=VanillaRewardRegistry.options("friends:lutte",null);assertEquals(2,options.size());
  var pick=options.get(0);var sword=options.get(1);assertEquals("precision",pick.id());assertEquals("masterblade",sword.id());
  assertEquals("netherite_pickaxe",pick.goods().getFirst().item());assertEquals(java.util.Map.of("efficiency",4,"unbreaking",3),pick.goods().getFirst().enchantments());
  assertEquals("netherite_sword",sword.goods().getFirst().item());assertEquals(java.util.Map.of("sharpness",4,"unbreaking",3),sword.goods().getFirst().enchantments());
  assertFalse(pick.diamondGate());assertFalse(sword.diamondGate());assertEquals(0,pick.currency());assertEquals(0,sword.currency());
 }
 @Test void persistedEarnedChoiceSurvivesScreenCloseAndOnlyOneCanBeConsumed() {
  var state=new PlayerRewardState();var snapshot=new CompoundTag();snapshot.putString("key","friends:lutte");var choices=new ListTag();
  for(var option:VanillaRewardRegistry.options("friends:lutte",null)) {var choice=new CompoundTag();choice.putString("option",option.id());choice.put("items",new ListTag());choices.add(choice);}
  snapshot.put("choices",choices);assertTrue(state.accept("friends_lutte","lutte_masterpiece",snapshot));assertTrue(state.earn("friends_lutte","lutte_masterpiece"));
  state=PlayerRewardState.fromNbt(state.toNbt());var instance=((CompoundTag)state.earned().getFirst()).getStringOr("instance","");assertFalse(instance.isBlank());
  assertFalse(state.takeChoice(instance,"precision").isEmpty());assertTrue(state.takeChoice(instance,"masterblade").isEmpty());assertTrue(state.takeChoice(instance,"precision").isEmpty());assertFalse(state.earn("friends_lutte","lutte_masterpiece"));
 }
}
