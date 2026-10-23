package de.quest.content.item;
import de.quest.data.QuestState;
import de.quest.quest.special.FriendsQuestService;
import de.quest.quest.special.MariusQuestService;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
/** Native drink lifecycle; the persisted world effect commits only when drinking finishes. */
public final class WeatherTonicItem extends Item {
 public WeatherTonicItem(Properties properties) {super(properties);}
 private boolean available(ServerLevel world,ServerPlayer player) {
  var tonic=QuestState.get(world.getServer()).weatherTonic();
  if(world.dimension()!=Level.OVERWORLD || !player.isAlive() || player.isSpectator() || !tonic.writable()) {
   player.sendSystemMessage(FriendsQuestService.tr("weather.unavailable"),false);return false;
  }
  if(tonic.remaining()>0) {
   player.sendSystemMessage(FriendsQuestService.tr("weather.remaining",MariusQuestService.activeTime(tonic.remaining())),false);return false;
  }
  return true;
 }
 @Override public InteractionResult use(Level level,Player user,InteractionHand hand) {
  if(level instanceof ServerLevel world && user instanceof ServerPlayer player && !available(world,player))return InteractionResult.FAIL;
  return super.use(level,user,hand);
 }
 @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user) {
  if(!(level instanceof ServerLevel world)) {
   // Native completion is also predicted after the server's finish event, even on rejection.
   // Keep the final gulp, but let the authoritative inventory update own the consumption.
   var consumable=stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
   if(consumable!=null)consumable.emitParticlesAndSounds(user.getRandom(),user,stack,16);
   return stack;
  }
  if(!(user instanceof ServerPlayer player) || stack.isEmpty() || !available(world,player))return stack;
  var state=QuestState.get(world.getServer());
  // Reserve the single bottle before the native consumption. No usingConvertsTo or extra shrink.
  if(!player.getAbilities().instabuild && !FriendsQuestService.enqueue(world,player,java.util.List.of(new ItemStack(Items.GLASS_BOTTLE))))return stack;
  if(!state.weatherTonic().start(world.isRaining() || world.isThundering()))return stack;
  ItemStack result=super.finishUsingItem(stack,level,user);
  FriendsQuestService.applyWeather(world,state.weatherTonic());state.setDirty();
  de.quest.reward.VanillaRewardService.deliverItems(world,player);
  player.sendSystemMessage(FriendsQuestService.tr("weather.used"),false);
  return result;
 }
}
