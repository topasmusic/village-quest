package de.quest.mixin;

import de.quest.kingdom.ArtifactJourneyService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attribute proof only after this player's real Vanilla lever action returns successfully. */
@Mixin(LeverBlock.class)
abstract class ArtifactLeverMixin {
    @Inject(method="useWithoutItem",at=@At("RETURN"))
    private void villageQuest$artifactLever(BlockState before,Level level,BlockPos pos,Player actor,BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if(cir.getReturnValue().consumesAction() && level instanceof ServerLevel world && actor instanceof ServerPlayer player)
            ArtifactJourneyService.leverChanged(world,player,pos,before);
    }
}
