package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceScreenHandler.class)
public abstract class AbstractFurnaceScreenHandlerMixin {
    private static final int VILLAGE_QUEST$OUTPUT_SLOT_INDEX = 2;

    @Inject(method = "quickMove", at = @At("RETURN"))
    private void villageQuest$trackQuickMoveOutput(PlayerEntity player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != VILLAGE_QUEST$OUTPUT_SLOT_INDEX) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemStack stack = cir.getReturnValue();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();
        DailyQuestService.onFurnaceOutput(serverWorld, serverPlayer, stack);
    }
}
