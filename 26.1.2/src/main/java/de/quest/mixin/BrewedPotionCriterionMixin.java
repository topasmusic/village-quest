package de.quest.mixin;

import de.quest.quest.special.ShardRelicQuestService;
import net.minecraft.advancements.criterion.BrewedPotionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewedPotionTrigger.class)
public abstract class BrewedPotionCriterionMixin {
    @Inject(method = "trigger", at = @At("TAIL"))
    private void villageQuest$trackShardRelicPotion(ServerPlayer player, Holder<Potion> potion, CallbackInfo ci) {
        ShardRelicQuestService.onPotionBrewed(player);
    }
}
