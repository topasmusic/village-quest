package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;

final class SurveyorHomeSafetyTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    final ServerLevel world=mock(ServerLevel.class);final Vec3 at=new Vec3(0.5,70,0.5);
    @BeforeEach void setup(){
        when(world.getWorldBorder()).thenReturn(new WorldBorder());when(world.isInValidBounds(any())).thenReturn(true);
        when(world.noCollision(isNull(),any())).thenReturn(true);
        when(world.getBlockState(any())).thenAnswer(inv->((BlockPos)inv.getArgument(0)).getY()<70?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
    }
    @Test void clearSupportedArrivalIsAccepted(){assertTrue(SurveyorCompassQuestService.safeArrival(world,at));}
    @Test void solidCollisionIsRejected(){when(world.noCollision(isNull(),any())).thenReturn(false);assertFalse(SurveyorCompassQuestService.safeArrival(world,at));}
    @Test void lavaAndDamagingFloorsAreRejected(){for(var hazard:new net.minecraft.world.level.block.Block[]{Blocks.LAVA,Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.CACTUS,Blocks.POWDER_SNOW}){
        when(world.getBlockState(new BlockPos(0,69,0))).thenReturn(hazard.defaultBlockState());assertFalse(SurveyorCompassQuestService.safeArrival(world,at));}}
    @Test void unsupportedAirColumnAndOutsideBorderAreRejected(){
        when(world.getBlockState(new BlockPos(0,69,0))).thenReturn(Blocks.AIR.defaultBlockState());assertFalse(SurveyorCompassQuestService.safeArrival(world,at));
        assertFalse(SurveyorCompassQuestService.safeArrival(world,new Vec3(30_000_001,70,0)));
    }
}
