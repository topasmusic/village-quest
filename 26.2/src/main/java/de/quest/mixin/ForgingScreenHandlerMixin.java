package de.quest.mixin;

import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
public abstract class ForgingScreenHandlerMixin {
    @Shadow @Final protected Container inputSlots;

    @Unique private ItemStack villageQuest$anvilQuickMoveLeftInput = ItemStack.EMPTY;
    @Unique private ItemStack villageQuest$anvilQuickMoveRightInput = ItemStack.EMPTY;

    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void villageQuest$captureAnvilQuickMoveInputs(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        villageQuest$clearAnvilQuickMoveInputs();
        if (!((Object) this instanceof AnvilMenu handler)) {
            return;
        }
        if (slot != handler.getResultSlot()) {
            return;
        }

        villageQuest$anvilQuickMoveLeftInput = this.inputSlots.getItem(0).copy();
        villageQuest$anvilQuickMoveRightInput = this.inputSlots.getItem(1).copy();
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void villageQuest$trackAnvilQuickMoveOutput(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (!((Object) this instanceof AnvilMenu handler)) {
                return;
            }
            if (slot != handler.getResultSlot()) {
                return;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ItemStack output = cir.getReturnValue();
            if (output == null || output.isEmpty()) {
                return;
            }

            StoryQuestService.onAnvilOutput((ServerLevel) serverPlayer.level(), serverPlayer, villageQuest$anvilQuickMoveLeftInput, villageQuest$anvilQuickMoveRightInput, output.copy());
            SpecialQuestService.onAnvilOutput((ServerLevel) serverPlayer.level(), serverPlayer, villageQuest$anvilQuickMoveLeftInput, villageQuest$anvilQuickMoveRightInput, output.copy());
            ShardRelicQuestService.onAnvilOutput(serverPlayer, villageQuest$anvilQuickMoveLeftInput, villageQuest$anvilQuickMoveRightInput, output.copy());
        } finally {
            villageQuest$clearAnvilQuickMoveInputs();
        }
    }

    @Unique
    private void villageQuest$clearAnvilQuickMoveInputs() {
        villageQuest$anvilQuickMoveLeftInput = ItemStack.EMPTY;
        villageQuest$anvilQuickMoveRightInput = ItemStack.EMPTY;
    }
}
