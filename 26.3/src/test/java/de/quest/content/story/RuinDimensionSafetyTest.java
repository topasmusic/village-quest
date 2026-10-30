package de.quest.content.story;
import static org.mockito.Mockito.*;
import de.quest.quest.story.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.Test;
final class RuinDimensionSafetyTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void otherDimensionMilestoneCannotReadOrMutateOverworldRecovery(){
        var world=mock(ServerLevel.class);var server=mock(net.minecraft.server.MinecraftServer.class);
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(mock(ServerLevel.class));
        var block=mock(net.minecraft.world.level.block.state.BlockState.class);
        when(block.is(org.mockito.ArgumentMatchers.nullable(net.minecraft.world.level.block.Block.class))).thenReturn(true);
        try(var states=mockStatic(de.quest.data.QuestState.class)){
            states.when(()->de.quest.data.QuestState.get(server)).thenThrow(new AssertionError("Other-dimension milestone must not touch Overworld quest records"));
            org.junit.jupiter.api.Assertions.assertTrue(ShrinesBetweenRoadsStoryArc.canBreakActiveRuinMilestone(world,mock(ServerPlayer.class),BlockPos.ZERO,block));
        }
    }
    @Test void otherDimensionCannotResolveOrPlaceAnOverworldRuin(){
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);when(player.level()).thenReturn(mock(ServerLevel.class));
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        try(var story=mockStatic(StoryQuestService.class);var sites=mockStatic(de.quest.util.WildernessSiteValidator.class)){
            story.when(()->StoryQuestService.hasStoryFlag(any(),any(),eq(StoryQuestKeys.SHRINES_RUIN_SEARCH_ACTIVE))).thenReturn(true);
            new ShrinesBetweenRoadsStoryArc().chapter(1).onServerTick(world,player);
            sites.verifyNoInteractions();verify(world,never()).getStructureTemplateManager();
        }
    }
}
