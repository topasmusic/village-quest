package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {
    @Inject(method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/PassiveEntity;)V", at = @At("HEAD"))
    private void villageQuest$trackAnimalBreed(ServerWorld world, AnimalEntity mate, PassiveEntity child, CallbackInfo ci) {
        AnimalEntity self = (AnimalEntity) (Object) this;
        ServerPlayerEntity serverPlayer = self.getLovingPlayer();
        if (serverPlayer == null && mate != null) {
            serverPlayer = mate.getLovingPlayer();
        }
        if (serverPlayer == null) {
            return;
        }
        DailyQuestService.onAnimalLove(world, serverPlayer, self);
    }
}
