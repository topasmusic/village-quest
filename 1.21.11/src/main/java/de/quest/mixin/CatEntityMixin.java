package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CatEntity.class)
public abstract class CatEntityMixin {
    @Inject(
            method = "interactMob",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/passive/CatEntity;setCollarColor(Lnet/minecraft/util/DyeColor;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackSuccessfulCollarRecolor(PlayerEntity player,
                                                           Hand hand,
                                                           CallbackInfoReturnable<ActionResult> cir) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !(serverPlayer.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        DailyQuestService.onSuccessfulPetCollarRecolor(world, serverPlayer);
    }
}
