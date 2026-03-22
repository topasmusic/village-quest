package de.quest.mixin;

import de.quest.quest.special.ShardRelicQuestService;
import net.minecraft.advancement.criterion.BrewedPotionCriterion;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.potion.Potion;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewedPotionCriterion.class)
public abstract class BrewedPotionCriterionMixin {
    @Inject(method = "trigger", at = @At("TAIL"))
    private void villageQuest$trackShardRelicPotion(ServerPlayerEntity player, RegistryEntry<Potion> potion, CallbackInfo ci) {
        ShardRelicQuestService.onPotionBrewed(player);
    }
}
