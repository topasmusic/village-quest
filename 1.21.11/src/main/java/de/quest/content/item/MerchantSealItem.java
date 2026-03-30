package de.quest.content.item;

import de.quest.entity.PilgrimEntity;
import de.quest.quest.special.MerchantSealQuestService;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class MerchantSealItem extends Item {
    public MerchantSealItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey()).formatted(Formatting.GOLD);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(user instanceof ServerPlayerEntity serverPlayer)
                || !(serverPlayer.getEntityWorld() instanceof ServerWorld serverWorld)
                || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (entity instanceof PilgrimEntity pilgrim) {
            return MerchantSealQuestService.tryUseOnPilgrim(serverWorld, serverPlayer, pilgrim, stack);
        }
        if (entity instanceof WanderingTraderEntity trader) {
            return MerchantSealQuestService.tryUseOnWanderingTrader(serverWorld, serverPlayer, trader, stack);
        }
        return ActionResult.PASS;
    }
}
