package de.quest.data;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
final class TerrainDimensionSafetyTest {
    @Test void onlyOverworldChangesAddHistoryAndOldMarksRemain() throws Exception {
        var state=QuestState.fromNbt(new CompoundTag());var pos=new BlockPos(160,65,320);var world=mock(ServerLevel.class);
        var mark=QuestState.class.getMethod("markTerrainModified",ServerLevel.class,BlockPos.class);
        for(var dimension:java.util.List.of(Level.NETHER,Level.END)){when(world.dimension()).thenReturn(dimension);mark.invoke(state,world,pos);assertFalse(state.isTerrainModified(pos,0));}
        when(world.dimension()).thenReturn(Level.OVERWORLD);mark.invoke(state,world,pos);assertTrue(state.isTerrainModified(pos,0));
        var restored=QuestState.fromNbt(QuestState.toNbt(state));assertTrue(restored.isTerrainModified(pos,0));
    }
}
