package de.quest.content.block;

import de.quest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GuildWayshrineBlockEntity extends BlockEntity {
    public GuildWayshrineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUILD_WAYSHRINE, pos, state);
    }
}
