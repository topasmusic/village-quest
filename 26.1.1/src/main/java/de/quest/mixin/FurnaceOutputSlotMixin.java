package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceOutputSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Inject(method = "remove", at = @At("RETURN"))
    private void villageQuest$trackFurnaceOutput(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel serverWorld = (ServerLevel) serverPlayer.level();
        ItemStack stack = cir.getReturnValue();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        DailyQuestService.onFurnaceOutput(serverWorld, serverPlayer, stack);
    }
}
