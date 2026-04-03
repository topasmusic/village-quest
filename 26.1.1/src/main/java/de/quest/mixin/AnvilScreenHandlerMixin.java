package de.quest.mixin;

import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilScreenHandlerMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void villageQuest$trackAnvilEnchant(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) {
            return;
        }

        ForgingScreenHandlerAccessor accessor = (ForgingScreenHandlerAccessor) this;
        ItemStack leftInput = accessor.villageQuest$getInput().getItem(0).copy();
        ItemStack rightInput = accessor.villageQuest$getInput().getItem(1).copy();
        ServerLevel world = (ServerLevel) serverPlayer.level();
        StoryQuestService.onAnvilOutput(world, serverPlayer, leftInput, rightInput, stack.copy());
        SpecialQuestService.onAnvilOutput(world, serverPlayer, leftInput, rightInput, stack.copy());
        ShardRelicQuestService.onAnvilOutput(serverPlayer, leftInput, rightInput, stack.copy());
    }
}
