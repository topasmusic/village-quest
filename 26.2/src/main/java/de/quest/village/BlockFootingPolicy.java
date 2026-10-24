package de.quest.village;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared geometric footing policy for loaded route and building observations. */
public final class BlockFootingPolicy {
    private BlockFootingPolicy() {}

    public static boolean supportsFooting(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) return false;
        double top = shape.max(Direction.Axis.Y);
        return top >= 0.45 && top <= 1.0
                && shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X) >= 0.6
                && shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z) >= 0.6;
    }
}
