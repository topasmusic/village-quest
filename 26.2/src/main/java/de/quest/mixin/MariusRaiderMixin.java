package de.quest.mixin;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Capture raid identity before native death removes membership. */
@Mixin(Raider.class)
abstract class MariusRaiderMixin {
 @Inject(method="die",at=@At("HEAD"))
 private void villageQuest$raidKill(DamageSource source,CallbackInfo ci) {
  var raider=(Raider)(Object)this;
  if(source.getEntity() instanceof ServerPlayer player && raider.level() instanceof ServerLevel world)
   de.quest.quest.special.MariusQuestService.raidKill(world,player,raider);
 }
}
