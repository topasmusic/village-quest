package de.quest.mixin;

import de.quest.quest.special.SpecialQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {
    @Inject(method = "tame", at = @At("TAIL"))
    private void villageQuest$trackTaming(Player player, CallbackInfo ci) {
        TamableAnimal animal = (TamableAnimal) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (animal.level().isClientSide() || !(animal.level() instanceof ServerLevel world)) {
            return;
        }
        SpecialQuestService.onAnimalTamed(world, serverPlayer, animal);
    }
}
