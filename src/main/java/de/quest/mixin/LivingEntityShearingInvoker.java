package de.quest.mixin;

import java.util.function.BiConsumer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityShearingInvoker {
    @Invoker("forEachShearedItem")
    void villageQuest$forEachShearedItem(ServerWorld world,
                                         RegistryKey<LootTable> lootTable,
                                         ItemStack tool,
                                         BiConsumer<ServerWorld, ItemStack> consumer);
}
