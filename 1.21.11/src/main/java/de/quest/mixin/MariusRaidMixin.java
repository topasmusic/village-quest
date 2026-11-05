package de.quest.mixin;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Raid.class)
abstract class MariusRaidMixin {
 @Unique private boolean villageQuest$victoryRecorded;
 @Inject(method="tick",at=@At("RETURN"))
 private void villageQuest$raidVictory(ServerLevel world,CallbackInfo ci) {
  var raid=(Raid)(Object)this;
  if(!villageQuest$victoryRecorded && raid.isVictory()) {
   villageQuest$victoryRecorded=true;
   de.quest.quest.special.MariusQuestService.raidTick(world,raid);
  }
 }
}
