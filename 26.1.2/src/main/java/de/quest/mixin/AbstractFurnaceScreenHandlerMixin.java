package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceScreenHandlerMixin {
    private static final int VILLAGE_QUEST$OUTPUT_SLOT_INDEX = 2;

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void villageQuest$trackQuickMoveOutput(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != VILLAGE_QUEST$OUTPUT_SLOT_INDEX) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = cir.getReturnValue();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ServerLevel serverWorld = (ServerLevel) serverPlayer.level();
        DailyQuestService.onFurnaceOutput(serverWorld, serverPlayer, stack);
    }
}
