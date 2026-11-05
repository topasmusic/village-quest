package de.quest.mixin;

import de.quest.ambient.AmbientAdventurerCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final causal killer only. Includes recent-player equipment/XP without altering later player kills. */
@Mixin(LivingEntity.class)
public abstract class AmbientAdventurerLootMixin {
    @Inject(method="dropAllDeathLoot",at=@At("HEAD"),cancellable=true)
    private void villageQuest$ambientFinalKill(ServerLevel world,DamageSource source,CallbackInfo ci) {
        if(AmbientAdventurerCombat.suppressLoot((LivingEntity)(Object)this,source)) ci.cancel();
    }
}
