package de.quest.mixin;

import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForgingScreenHandler.class)
public abstract class ForgingScreenHandlerMixin {
    @Shadow @Final protected Inventory input;

    @Unique private ItemStack villageQuest$anvilQuickMoveLeftInput = ItemStack.EMPTY;
    @Unique private ItemStack villageQuest$anvilQuickMoveRightInput = ItemStack.EMPTY;

    @Inject(method = "quickMove", at = @At("HEAD"))
    private void villageQuest$captureAnvilQuickMoveInputs(PlayerEntity player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        villageQuest$clearAnvilQuickMoveInputs();
        if (!((Object) this instanceof AnvilScreenHandler handler)) {
            return;
        }
        if (slot != handler.getResultSlotIndex()) {
            return;
        }

        villageQuest$anvilQuickMoveLeftInput = this.input.getStack(0).copy();
        villageQuest$anvilQuickMoveRightInput = this.input.getStack(1).copy();
    }

    @Inject(method = "quickMove", at = @At("RETURN"))
    private void villageQuest$trackAnvilQuickMoveOutput(PlayerEntity player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (!((Object) this instanceof AnvilScreenHandler handler)) {
                return;
            }
            if (slot != handler.getResultSlotIndex()) {
                return;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }

            ItemStack output = cir.getReturnValue();
            if (output == null || output.isEmpty()) {
                return;
            }

            StoryQuestService.onAnvilOutput((ServerWorld) serverPlayer.getEntityWorld(), serverPlayer, villageQuest$anvilQuickMoveLeftInput, villageQuest$anvilQuickMoveRightInput, output.copy());
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
