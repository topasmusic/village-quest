package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FurnaceOutputSlot.class)
public abstract class FurnaceOutputSlotMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "takeStack", at = @At("RETURN"))
    private void villageQuest$trackFurnaceOutput(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();
        ItemStack stack = cir.getReturnValue();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        DailyQuestService.onFurnaceOutput(serverWorld, serverPlayer, stack);
    }
}
