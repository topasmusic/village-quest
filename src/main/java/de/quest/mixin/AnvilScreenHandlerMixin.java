package de.quest.mixin;

import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.ShardRelicQuestService;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void villageQuest$trackAnvilEnchant(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || stack.isEmpty()) {
            return;
        }

        ForgingScreenHandlerAccessor accessor = (ForgingScreenHandlerAccessor) this;
        ItemStack leftInput = accessor.villageQuest$getInput().getStack(0).copy();
        ItemStack rightInput = accessor.villageQuest$getInput().getStack(1).copy();
        ServerWorld world = (ServerWorld) serverPlayer.getEntityWorld();
        StoryQuestService.onAnvilOutput(world, serverPlayer, leftInput, rightInput, stack.copy());
        SpecialQuestService.onAnvilOutput(world, serverPlayer, leftInput, rightInput, stack.copy());
        ShardRelicQuestService.onAnvilOutput(serverPlayer, leftInput, rightInput, stack.copy());
    }
}
