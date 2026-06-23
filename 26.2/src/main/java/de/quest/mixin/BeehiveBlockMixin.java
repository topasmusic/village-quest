package de.quest.mixin;

import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeehiveBlock.class)
public abstract class BeehiveBlockMixin {
    @Inject(
            method = "useItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackSuccessfulHoneycombCut(ItemStack stack,
                                                           BlockState state,
                                                           Level level,
                                                           BlockPos pos,
                                                           Player player,
                                                           InteractionHand hand,
                                                           BlockHitResult hit,
                                                           CallbackInfoReturnable<InteractionResult> cir) {
        villageQuest$trackSuccessfulHarvest(level, player, state, Items.SHEARS.getDefaultInstance());
    }

    @Inject(
            method = "useItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackSuccessfulHoneyBottleFill(ItemStack stack,
                                                             BlockState state,
                                                             Level level,
                                                             BlockPos pos,
                                                             Player player,
                                                             InteractionHand hand,
                                                             BlockHitResult hit,
                                                             CallbackInfoReturnable<InteractionResult> cir) {
        villageQuest$trackSuccessfulHarvest(level, player, state, Items.GLASS_BOTTLE.getDefaultInstance());
    }

    private static void villageQuest$trackSuccessfulHarvest(Level level, Player player, BlockState state, ItemStack tool) {
        if (!(level instanceof ServerLevel world) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        DailyQuestService.onBeeNestInteract(world, serverPlayer, state, tool);
        StoryQuestService.onBeeNestInteract(world, serverPlayer, state, tool);
        SpecialQuestService.onBeeNestInteract(world, serverPlayer, state, tool);
        PilgrimContractService.onBeeNestInteract(world, serverPlayer, state, tool);
    }
}
