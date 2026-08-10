package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockDropMixin {
    @Inject(
            method = "dropStack(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private static void villageQuest$captureTrackedBlockDrop(World world, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld) {
            QuestDropTracker.onBlockResourceDrop(serverWorld, pos, stack);
        }
    }
}
