package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {
    @Inject(method = "lovePlayer", at = @At("TAIL"))
    private void villageQuest$trackAnimalLove(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();
        DailyQuestService.onAnimalLove(serverWorld, serverPlayer, (AnimalEntity) (Object) this);
    }
}
