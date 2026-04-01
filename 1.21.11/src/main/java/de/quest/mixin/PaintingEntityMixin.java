package de.quest.mixin;

import de.quest.painting.PaintingStackFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.decoration.painting.PaintingVariant;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.rule.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PaintingEntity.class)
public abstract class PaintingEntityMixin {
    @Shadow public abstract RegistryEntry<PaintingVariant> getVariant();

    @Inject(method = "onBreak", at = @At("HEAD"), cancellable = true)
    private void villageQuest$preserveVariantOnBreak(ServerWorld world, Entity breaker, CallbackInfo ci) {
        PaintingEntity self = (PaintingEntity) (Object) this;
        RegistryEntry<PaintingVariant> variant = getVariant();
        if (!PaintingStackFactory.isVillageQuestPainting(variant)) {
            return;
        }

        if (!world.getGameRules().getValue(GameRules.ENTITY_DROPS)) {
            ci.cancel();
            return;
        }

        self.playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1.0f, 1.0f);
        if (breaker instanceof PlayerEntity player && player.isCreative()) {
            ci.cancel();
            return;
        }

        ItemStack stack = PaintingStackFactory.create(variant);
        if (!stack.isEmpty()) {
            self.dropStack(world, stack);
        }
        ci.cancel();
    }
}
