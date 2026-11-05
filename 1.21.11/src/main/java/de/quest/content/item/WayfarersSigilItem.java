package de.quest.content.item;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.shrine.VillageBondService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Binds Wayshrines and carries the former Roadmender inspection function. */
public final class WayfarersSigilItem extends Item {
    public WayfarersSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !GuildArchiveService.isSuperseded(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel world
                && context.getPlayer() instanceof ServerPlayer player) {
            if (!GuildArchiveService.validateUse(world, player, context.getItemInHand(),
                    ArchiveItem.WAYFARERS_SIGIL)) return InteractionResult.FAIL;
            return VillageBondService.inspectWithSigil(
                    world, player, context.getClickedPos(), world.getBlockState(context.getClickedPos()));
        }
        return InteractionResult.SUCCESS;
    }
}
