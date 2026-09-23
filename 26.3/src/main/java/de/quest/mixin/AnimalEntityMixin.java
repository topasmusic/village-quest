package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class AnimalEntityMixin {
    @Inject(method = "finalizeSpawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/AgeableMob;)V", at = @At("HEAD"))
    private void villageQuest$trackAnimalBreed(ServerLevel world, Animal mate, AgeableMob child, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;
        ServerPlayer serverPlayer = self.getLoveCause();
        if (serverPlayer == null && mate != null) {
            serverPlayer = mate.getLoveCause();
        }
        if (serverPlayer == null) {
            return;
        }
        DailyQuestService.onAnimalLove(world, serverPlayer, self);
    }
}
