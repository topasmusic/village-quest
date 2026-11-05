package de.quest.mixin;

import de.quest.kingdom.KingdomBuildingService;
import de.quest.kingdom.BuildingEvidenceChange;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Local cache invalidation only, after a successful native state change; no block reads or Brain patches. */
@Mixin(Level.class)
abstract class KingdomBuildingDirtyMixin {
    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState villageQuest$dirtyBuilding(LevelChunk chunk,BlockPos pos,BlockState state,int flags,Operation<BlockState> original) {
        BlockState before=original.call(chunk,pos,state,flags);
        if ((Object) this instanceof ServerLevel world)
            de.quest.village.GuildCornerPlacementService.onBlockReplaced(world, pos, before, state);
        if (BuildingEvidenceChange.affectsReport(before,state) && (Object) this instanceof ServerLevel world)
            if(!de.quest.kingdom.NativeInnBeds.equivalentOwnOccupancy(world,pos,before,state)) KingdomBuildingService.onBlockChanged(world, pos);
        if(before!=null && (Object)this instanceof ServerLevel world && (!before.is(state.getBlock()) || before.is(net.minecraft.world.level.block.Blocks.LANTERN) && before.getValue(net.minecraft.world.level.block.LanternBlock.HANGING)!=state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING)))
            de.quest.quest.special.LutteCoastalQuest.invalidate(world,pos);
        return before;
    }
}
