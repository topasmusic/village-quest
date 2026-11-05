package de.quest.mixin;

import de.quest.guildtown.GuildTownService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the actual crafted quantity, including shift-click, once at Vanilla's achievement boundary. */
@Mixin(ItemStack.class)
public abstract class ItemStackCraftMixin {
    @Shadow public abstract Item getItem();

    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void villageQuest$paperCrafted(Player player, int count, CallbackInfo ci) {
        if (count > 0 && player instanceof ServerPlayer serverPlayer
                && getItem() == Items.PAPER) {
            GuildTownService.onTrackedItemPickup((ServerLevel) serverPlayer.level(), serverPlayer,
                    new ItemStack(Items.PAPER, count), count);
        }
    }
}
