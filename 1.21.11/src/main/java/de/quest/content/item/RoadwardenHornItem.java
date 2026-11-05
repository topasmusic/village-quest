package de.quest.content.item;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.caravan.TradeRouteService;
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

public final class RoadwardenHornItem extends Item {
    public RoadwardenHornItem(Properties properties) { super(properties); }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return !GuildArchiveService.isSuperseded(stack); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel world) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!GuildArchiveService.validateUse(world, serverPlayer,
                player.getItemInHand(hand), ArchiveItem.ROADWARDEN_HORN)) return InteractionResult.FAIL;
        return TradeRouteService.useRoadwardenHorn(world, serverPlayer)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
