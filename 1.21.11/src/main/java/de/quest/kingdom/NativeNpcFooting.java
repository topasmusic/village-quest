package de.quest.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Navigation can open doors; a newly materialized body still needs genuinely empty dry space. */
final class NativeNpcFooting {
    private NativeNpcFooting() {}
    static boolean safe(BlockState foot,BlockState head,BlockState floor,BlockPos pos) {
        return NativeBuildingProbe.classify(foot,EmptyBlockGetter.INSTANCE,pos).passable()
                && NativeBuildingProbe.classify(head,EmptyBlockGetter.INSTANCE,pos.above()).passable()
                && NativeBuildingProbe.classify(floor,EmptyBlockGetter.INSTANCE,pos.below()).support()
                && foot.getCollisionShape(EmptyBlockGetter.INSTANCE,pos).isEmpty()
                && head.getCollisionShape(EmptyBlockGetter.INSTANCE,pos.above()).isEmpty();
    }
}
