package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BuildingEvidenceChangeTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void ordinaryWoodDoorUseCannotStarveACompleteFairBuildingScan() {
        var door=Blocks.OAK_DOOR.defaultBlockState();
        assertFalse(BuildingEvidenceChange.affectsReport(door,door.setValue(DoorBlock.OPEN,true)));
        assertFalse(BuildingEvidenceChange.affectsReport(door,door.setValue(DoorBlock.OPEN,true).setValue(DoorBlock.POWERED,true)));
    }
    @Test void ironDoorPassabilityAndStructuralOrDirectionalChangesStillInvalidate() {
        var iron=Blocks.IRON_DOOR.defaultBlockState(); var wood=Blocks.OAK_DOOR.defaultBlockState();
        assertTrue(BuildingEvidenceChange.affectsReport(iron,iron.setValue(DoorBlock.OPEN,true)));
        assertTrue(BuildingEvidenceChange.affectsReport(wood,Blocks.AIR.defaultBlockState()));
        assertTrue(BuildingEvidenceChange.affectsReport(Blocks.STONE.defaultBlockState(),wood));
        assertTrue(BuildingEvidenceChange.affectsReport(wood,wood.setValue(DoorBlock.FACING,Direction.EAST)));
        assertFalse(BuildingEvidenceChange.affectsReport(wood,wood));
    }
}
