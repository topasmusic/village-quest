package de.quest.village;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildNoticePostSiteTest {
    private static final BlockPos ROOT = new BlockPos(0, 64, 0);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static boolean safe(Direction facing, Map<BlockPos, BlockState> overrides) throws Exception {
        Function<BlockPos, BlockState> read = pos -> overrides.containsKey(pos) ? overrides.get(pos)
                : pos.getY() == 63 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
        var method = Class.forName("de.quest.village.GuildNoticePostSite").getDeclaredMethod("isSafe",
                BlockPos.class, Direction.class, Function.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ROOT, facing, read);
    }

    @Test void bareGroundAndNaturalCanopyAllowACompactBoardInEveryOrientation() throws Exception {
        for (var facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            assertTrue(safe(facing, Map.of()));
            assertTrue(safe(facing, Map.of(ROOT.above(), Blocks.OAK_LEAVES.defaultBlockState())));
        }
    }

    @Test void allSixVirtualCellsMustBeFreeOfPlayerBlocksAndPersistentLeaves() throws Exception {
        for (var pos : de.quest.content.block.GuildNoticePostBlock.occupiedPositions(ROOT, Direction.NORTH)) {
            assertFalse(safe(Direction.NORTH, Map.of(pos, Blocks.OAK_PLANKS.defaultBlockState())));
            assertFalse(safe(Direction.NORTH, Map.of(pos, Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, true))));
            var unloaded = new HashMap<BlockPos, BlockState>(); unloaded.put(pos, null);
            assertFalse(safe(Direction.NORTH, unloaded));
        }
    }

    @Test void noFloatingBoardWaterOrBlockedFrontAccess() throws Exception {
        assertFalse(safe(Direction.NORTH, Map.of(ROOT.below(), Blocks.AIR.defaultBlockState())));
        assertFalse(safe(Direction.NORTH, Map.of(ROOT, Blocks.WATER.defaultBlockState())));
        assertFalse(safe(Direction.NORTH, Map.of(ROOT.south(), Blocks.STONE.defaultBlockState())));
        assertFalse(safe(Direction.NORTH, Map.of(ROOT.south().above(), Blocks.STONE.defaultBlockState())));
        assertFalse(safe(Direction.NORTH, Map.of(ROOT.below(), Blocks.OAK_LOG.defaultBlockState())));
    }
}
