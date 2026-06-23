package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class TradeOutputSlotMixin {
    @Shadow @Final private Merchant merchant;
    @Shadow @Final private MerchantContainer slots;

    @Inject(
            method = "onTake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$trackVillagerTrade(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(merchant instanceof Villager)) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel serverWorld = (ServerLevel) serverPlayer.level();
        ItemStack rewardStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (rewardStack.isEmpty()) {
            MerchantOffer offer = this.slots.getActiveOffer();
            rewardStack = offer == null ? ItemStack.EMPTY : offer.assemble();
        }

        DailyQuestService.onVillagerTrade(serverWorld, serverPlayer, rewardStack);
    }
}
