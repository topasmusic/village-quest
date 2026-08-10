package de.quest.mixin;

import net.minecraft.entity.passive.WanderingTraderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WanderingTraderEntity.class)
public interface WanderingTraderEntityInvoker {
    @Invoker("fillRecipes")
    void villageQuest$invokeFillRecipes();
}
