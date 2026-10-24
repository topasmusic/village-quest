package de.quest.kingdom;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Uses the native mutation's old state; never queries surrounding blocks. */
public final class BuildingEvidenceChange {
    private BuildingEvidenceChange() {}
    public static boolean affectsReport(BlockState before,BlockState after) {
        if (before==null || before.equals(after)) return false;
        if (before.getBlock()==after.getBlock() && before.getBlock() instanceof DoorBlock door && door.type().canOpenByHand())
            return !before.setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false)
                    .equals(after.setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false));
        return true;
    }
}
