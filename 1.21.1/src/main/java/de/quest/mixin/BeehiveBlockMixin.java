package de.quest.mixin;

import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeehiveBlock.class)
public abstract class BeehiveBlockMixin {
    @Inject(
            method = "onUseWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BeehiveBlock;dropHoneycomb(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackSuccessfulHoneycombCut(ItemStack stack,
                                                          BlockState state,
                                                          World world,
                                                          BlockPos pos,
                                                          PlayerEntity player,
                                                          Hand hand,
                                                          BlockHitResult hit,
                                                          CallbackInfoReturnable<ItemActionResult> cir) {
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
            villageQuest$trackSuccessfulHarvest(serverWorld, serverPlayer, state, Items.SHEARS.getDefaultStack());
        }
    }

    @Inject(
            method = "takeHoney(Lnet/minecraft/world/World;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/block/entity/BeehiveBlockEntity$BeeState;)V",
            at = @At("TAIL")
    )
    private void villageQuest$trackSuccessfulHoneyBottleFill(World world,
                                                             BlockState state,
                                                             BlockPos pos,
                                                             PlayerEntity player,
                                                             BeehiveBlockEntity.BeeState beeState,
                                                             CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
            villageQuest$trackSuccessfulHarvest(serverWorld, serverPlayer, state, Items.GLASS_BOTTLE.getDefaultStack());
        }
    }

    private static void villageQuest$trackSuccessfulHarvest(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack tool) {
        DailyQuestService.onBeeNestInteract(world, player, state, tool);
        StoryQuestService.onBeeNestInteract(world, player, state, tool);
        SpecialQuestService.onBeeNestInteract(world, player, state, tool);
        PilgrimContractService.onBeeNestInteract(world, player, state, tool);
    }
}
