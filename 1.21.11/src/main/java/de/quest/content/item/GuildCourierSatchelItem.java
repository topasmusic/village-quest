package de.quest.content.item;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.caravan.TradeGuildService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A one-slot, contract-aware freight container. */
public final class GuildCourierSatchelItem extends Item {
    public GuildCourierSatchelItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.GOLD);
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel world && player instanceof ServerPlayer serverPlayer) {
            if (!GuildArchiveService.validateUse(world, serverPlayer,
                    player.getItemInHand(hand), ArchiveItem.GUILD_COURIERS_SATCHEL)) return InteractionResult.FAIL;
            return TradeGuildService.useSatchel(world, serverPlayer, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }
}
