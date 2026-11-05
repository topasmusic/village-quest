package de.quest.content.item;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.*;
public final class VillageWatchSealItem extends Item {
 public VillageWatchSealItem(Properties properties){super(properties);}
 @Override public net.minecraft.network.chat.Component getName(ItemStack stack) {return net.minecraft.network.chat.Component.translatable(getDescriptionId()).withStyle(net.minecraft.ChatFormatting.GREEN);}
 @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
  if(!(level instanceof ServerLevel world) || !(player instanceof ServerPlayer actor))return InteractionResult.SUCCESS;
  return de.quest.quest.special.MariusQuestService.useSeal(world,actor,player.getItemInHand(hand))?InteractionResult.SUCCESS:InteractionResult.FAIL;
 }
}
