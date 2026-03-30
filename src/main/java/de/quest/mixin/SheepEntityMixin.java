package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import java.util.function.BiConsumer;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SheepEntity.class)
public abstract class SheepEntityMixin {
    @Redirect(
            method = "sheared",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/passive/SheepEntity;forEachShearedItem(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/item/ItemStack;Ljava/util/function/BiConsumer;)V"
            )
    )
    private void villageQuest$trackShearedWool(SheepEntity sheep,
                                               ServerWorld world,
                                               RegistryKey<LootTable> lootTable,
                                               ItemStack tool,
                                               BiConsumer<ServerWorld, ItemStack> consumer) {
        BiConsumer<ServerWorld, ItemStack> wrappedConsumer = (dropWorld, dropStack) -> {
            ItemStack trackedStack = dropStack.copy();
            consumer.accept(dropWorld, dropStack);
            QuestDropTracker.onShearedDrop(dropWorld, sheep, trackedStack);
        };
        ((LivingEntityShearingInvoker) sheep).villageQuest$forEachShearedItem(world, lootTable, tool, wrappedConsumer);
        QuestDropTracker.onShearedFinished(world, sheep);
    }
}
