package de.quest.mixin;

import de.quest.content.block.GuildNoticePostTargeting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Corrects the block result before Minecraft derives its crosshair entity and debug target. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
    private void villageQuest$pickFullGuildNoticePost(float partialTick, Entity cameraEntity,
                                                       CallbackInfoReturnable<HitResult> cir) {
        if (cameraEntity == null) return;
        LocalPlayer player = (LocalPlayer) (Object) this;
        Vec3 start = cameraEntity.getEyePosition(partialTick);
        Vec3 end = start.add(cameraEntity.getViewVector(partialTick).scale(player.blockInteractionRange()));
        HitResult original = cir.getReturnValue();
        HitResult corrected = GuildNoticePostTargeting.correctHit(
                cameraEntity.level(), start, end, original);
        if (corrected != original) cir.setReturnValue(corrected);
    }
}
