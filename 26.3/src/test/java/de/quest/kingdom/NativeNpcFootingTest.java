package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NativeNpcFootingTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void aNavigableWoodDoorIsNotEmptySpaceForMaterializingAPhysicalBody() {
        var air=Blocks.AIR.defaultBlockState(); var floor=Blocks.STONE.defaultBlockState(); var pos=new BlockPos(0,64,0);
        assertTrue(NativeNpcFooting.safe(air,air,floor,pos));
        assertFalse(NativeNpcFooting.safe(Blocks.OAK_DOOR.defaultBlockState(),air,floor,pos));
        assertFalse(NativeNpcFooting.safe(air,Blocks.OAK_DOOR.defaultBlockState(),floor,pos));
        assertFalse(NativeNpcFooting.safe(Blocks.WATER.defaultBlockState(),air,floor,pos));
        assertFalse(NativeNpcFooting.safe(air,air,Blocks.MAGMA_BLOCK.defaultBlockState(),pos));
        assertFalse(NativeNpcFooting.safe(Blocks.FIRE.defaultBlockState(),air,floor,pos));
    }
}
