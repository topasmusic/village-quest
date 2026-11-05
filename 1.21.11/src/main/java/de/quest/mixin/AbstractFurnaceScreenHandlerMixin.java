package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.FurnaceOutputDeduplicator;
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

    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void villageQuest$beginQuickMoveOutput(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == VILLAGE_QUEST$OUTPUT_SLOT_INDEX && player instanceof ServerPlayer) {
            ItemStack output = ((AbstractFurnaceMenu) (Object) this).getSlot(VILLAGE_QUEST$OUTPUT_SLOT_INDEX).getItem();
            FurnaceOutputDeduplicator.beginQuickMove(output.isEmpty() ? 0 : output.getCount());
        }
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void villageQuest$trackQuickMoveOutput(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != VILLAGE_QUEST$OUTPUT_SLOT_INDEX) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = cir.getReturnValue();
        int fallbackCount = stack == null || stack.isEmpty() ? 0 : stack.getCount();
        ItemStack remaining = ((AbstractFurnaceMenu) (Object) this).getSlot(VILLAGE_QUEST$OUTPUT_SLOT_INDEX).getItem();
        int removedCount = FurnaceOutputDeduplicator.finishQuickMoveCount(
                fallbackCount, remaining.isEmpty() ? 0 : remaining.getCount());
        if (stack == null || stack.isEmpty() || removedCount <= 0) {
            return;
        }

        ServerLevel serverWorld = (ServerLevel) serverPlayer.level();
        ItemStack removed = stack.copy();
        removed.setCount(removedCount);
        DailyQuestService.onFurnaceOutput(serverWorld, serverPlayer, removed);
    }
}
