package de.quest.mixin;

import de.quest.painting.PaintingStackFactory;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Painting.class)
public abstract class PaintingEntityMixin {
    @Shadow public abstract Holder<PaintingVariant> getVariant();

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void villageQuest$preserveVariantOnBreak(ServerLevel world, Entity breaker, CallbackInfo ci) {
        Painting self = (Painting) (Object) this;
        Holder<PaintingVariant> variant = getVariant();
        if (!PaintingStackFactory.isVillageQuestPainting(variant)) {
            return;
        }

        if (!world.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ci.cancel();
            return;
        }

        self.playSound(SoundEvents.PAINTING_BREAK, 1.0f, 1.0f);
        if (breaker instanceof Player player && player.isCreative()) {
            ci.cancel();
            return;
        }

        ItemStack stack = PaintingStackFactory.create(variant);
        if (!stack.isEmpty()) {
            self.spawnAtLocation(world, stack, 0.0f);
        }
        ci.cancel();
    }

    @Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
    private void villageQuest$preserveVariantOnPick(CallbackInfoReturnable<ItemStack> cir) {
        Holder<PaintingVariant> variant = getVariant();
        if (!PaintingStackFactory.isVillageQuestPainting(variant)) {
            return;
        }

        ItemStack stack = PaintingStackFactory.create(variant);
        if (!stack.isEmpty()) {
            cir.setReturnValue(stack);
        }
    }
}
