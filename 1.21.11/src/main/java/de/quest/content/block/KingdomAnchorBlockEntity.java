package de.quest.content.block;

import de.quest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Marker entity, with no replicated inventory/NPC/progress state. Shared IDs remain in LivingKingdomsState. */
public final class KingdomAnchorBlockEntity extends BlockEntity {
    public KingdomAnchorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.KINGDOM_ANCHOR, pos, state); }
}
