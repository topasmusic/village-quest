package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Sheep.class)
public abstract class SheepMixin {
    @ModifyArg(
            method = "shear",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/sheep/Sheep;dropFromShearingLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/item/ItemInstance;Ljava/util/function/BiConsumer;)V"
            ),
            index = 3
    )
    private BiConsumer<ServerLevel, ItemStack> villageQuest$wrapShearedLootConsumer(BiConsumer<ServerLevel, ItemStack> consumer) {
        Sheep sheep = (Sheep) (Object) this;
        return (dropWorld, dropStack) -> {
            ItemStack trackedStack = dropStack.copy();
            consumer.accept(dropWorld, dropStack);
            QuestDropTracker.onShearedDrop(dropWorld, sheep, trackedStack);
        };
    }

    @Inject(method = "shear", at = @At("TAIL"))
    private void villageQuest$clearPendingShear(ServerLevel world, SoundSource source, ItemStack tool, CallbackInfo ci) {
        QuestDropTracker.onShearedFinished(world, (Sheep) (Object) this);
    }
}
