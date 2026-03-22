package de.quest.mixin;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TradeOutputSlot.class)
public abstract class TradeOutputSlotMixin {
    @Shadow @Final private Merchant merchant;

    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void villageQuest$trackVillagerTrade(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(merchant instanceof VillagerEntity)) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();
        if (stack.isEmpty()) {
            return;
        }

        DailyQuestService.onVillagerTrade(serverWorld, serverPlayer, stack);
    }
}
