package de.quest.caravan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Single-column bridge; caller reserves the bounded reads and checks ticking chunks first. */
public final class AmbientRoadAccess {
    private AmbientRoadAccess() {}
    public static BlockPos near(ServerLevel world,BlockPos at) {
        BlockPos feet=TradeRouteSurfaceResolver.safeSurfaceNearY(world,at.getX(),at.getY(),at.getZ(),1);
        return feet!=null && !hazard(world.getBlockState(feet)) && !hazard(world.getBlockState(feet.above()))?feet:null;
    }
    public static boolean hazard(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.COBWEB) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL);
    }
    public static boolean road(ServerLevel world,BlockPos feet) {
        return TradeRouteSurfaceResolver.isRoadBlock(world.getBlockState(feet.below()));
    }
}
