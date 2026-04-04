package de.quest.mixin;

import de.quest.quest.special.SpecialQuestService;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TameableEntity.class)
public abstract class TamableAnimalMixin {
    @Inject(method = "setTamedBy", at = @At("TAIL"))
    private void villageQuest$trackTaming(PlayerEntity player, CallbackInfo ci) {
        TameableEntity animal = (TameableEntity) (Object) this;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (animal.getEntityWorld().isClient() || !(animal.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        SpecialQuestService.onAnimalTamed(world, serverPlayer, animal);
    }
}
