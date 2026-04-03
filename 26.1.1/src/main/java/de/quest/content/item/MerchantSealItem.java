package de.quest.content.item;

import de.quest.entity.PilgrimEntity;
import de.quest.quest.special.MerchantSealQuestService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MerchantSealItem extends Item {
    public MerchantSealItem(Properties settings) {
        super(settings);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (!(user instanceof ServerPlayer serverPlayer)
                || !(serverPlayer.level() instanceof ServerLevel serverWorld)
                || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (entity instanceof PilgrimEntity pilgrim) {
            return MerchantSealQuestService.tryUseOnPilgrim(serverWorld, serverPlayer, pilgrim, stack);
        }
        if (entity instanceof WanderingTrader trader) {
            return MerchantSealQuestService.tryUseOnWanderingTrader(serverWorld, serverPlayer, trader, stack);
        }
        return InteractionResult.PASS;
    }
}
