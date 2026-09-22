package de.quest.mixin;

import de.quest.data.QuestState;
import de.quest.content.block.GuildNoticePostBlock;
import de.quest.guildtown.GuildTownService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks only block placements that actually succeeded. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void villageQuest$rejectGuildNoticePostOverlap(BlockPlaceContext context,
                                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (context != null && GuildNoticePostBlock.blocksPlacementAt(
                context.getLevel(), context.getClickedPos())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void villageQuest$trackSuccessfulPlacement(BlockPlaceContext context,
                                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()
                || !(context.getLevel() instanceof ServerLevel world)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        BlockItem item = (BlockItem) (Object) this;
        QuestState.get(world.getServer()).markTerrainModified(context.getClickedPos());
        GuildTownService.onPlaceBlock(world, player, context.getClickedPos(), item.getDefaultInstance());
    }
}
