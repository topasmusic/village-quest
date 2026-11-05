package de.quest.mixin;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Observe successful vanilla ingot healing without replacing consumption, sounds or behavior. */
@Mixin(IronGolem.class)
abstract class IronGolemRepairMixin {
 @Unique private float villageQuest$beforeRepair;
 @Unique private boolean villageQuest$ironRepair;
 @Inject(method="mobInteract",at=@At("HEAD"))
 private void villageQuest$repairBefore(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) {
  villageQuest$beforeRepair=((IronGolem)(Object)this).getHealth();villageQuest$ironRepair=player.getItemInHand(hand).is(Items.IRON_INGOT);
 }
 @Inject(method="mobInteract",at=@At("RETURN"))
 private void villageQuest$repairAfter(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) {
  var golem=(IronGolem)(Object)this;
  if(villageQuest$ironRepair && cir.getReturnValue().consumesAction() && player instanceof ServerPlayer actor && golem.level() instanceof ServerLevel world)
   de.quest.quest.special.MariusQuestService.repaired(world,actor,golem,golem.getHealth()-villageQuest$beforeRepair);
 }
}
