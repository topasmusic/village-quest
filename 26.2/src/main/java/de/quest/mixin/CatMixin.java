package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Cat.class)
public abstract class CatMixin {
    @Inject(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/feline/Cat;setCollarColor(Lnet/minecraft/world/item/DyeColor;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackSuccessfulCollarRecolor(Player player,
                                                           InteractionHand hand,
                                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel world)) {
            return;
        }
        DailyQuestService.onSuccessfulPetCollarRecolor(world, serverPlayer);
    }
}
