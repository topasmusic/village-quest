package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceOutputSlot.class)
public abstract class FurnaceOutputSlotMixin {
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void villageQuest$trackFurnaceOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();
        if (stack.isEmpty()) {
            return;
        }

        DailyQuestService.onFurnaceOutput(serverWorld, serverPlayer, stack);
    }
}
