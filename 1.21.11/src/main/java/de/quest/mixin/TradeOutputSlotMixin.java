package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantInventory;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TradeOutputSlot.class)
public abstract class TradeOutputSlotMixin {
    @Shadow @Final private Merchant merchant;
    @Shadow @Final private MerchantInventory merchantInventory;

    @Inject(
            method = "onTakeItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/village/Merchant;trade(Lnet/minecraft/village/TradeOffer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackVillagerTrade(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(merchant instanceof VillagerEntity)) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();
        ItemStack rewardStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (rewardStack.isEmpty()) {
            TradeOffer offer = this.merchantInventory.getTradeOffer();
            rewardStack = offer == null ? ItemStack.EMPTY : offer.copySellItem();
        }

        DailyQuestService.onVillagerTrade(serverWorld, serverPlayer, rewardStack);
    }
}
