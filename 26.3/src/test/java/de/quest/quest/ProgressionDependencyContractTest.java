package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.story.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Native prerequisite evaluation from earned-save evidence; no claim, world tick or simulation.
 * Material/event producers themselves are covered by the native family and freight suites. */
final class ProgressionDependencyContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
    }
    @ParameterizedTest @EnumSource(value=Difficulty.class,names={"PEACEFUL","NORMAL"})
    void savedCoreCompletionsAndPeacefulPilgrimEvidenceReachTheLateStoryChain(Difficulty mode) {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var owner=UUID.randomUUID();var data=quests.getPlayerData(owner);
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);when(world.getDifficulty()).thenReturn(mode);
        try(var q=mockStatic(QuestState.class,CALLS_REAL_METHODS)) {
            q.when(()->QuestState.get(server)).thenReturn(quests);
            assertFalse(de.quest.questmaster.QuestMasterProgressionService.isStoryCategoryUnlocked(world,owner));
            data.setLastRewardDay(1);
            assertTrue(de.quest.questmaster.QuestMasterProgressionService.isStoryCategoryUnlocked(world,owner));
            assertTrue(StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS).isUnlocked(world,owner));
            var edges=List.of(
                    List.of(StoryArcType.FAILING_HARVEST,StoryArcType.SILENT_FORGE),
                    List.of(StoryArcType.SILENT_FORGE,StoryArcType.MARKET_ROAD_TROUBLES),
                    List.of(StoryArcType.MARKET_ROAD_TROUBLES,StoryArcType.RESTLESS_PENS),
                    List.of(StoryArcType.RESTLESS_PENS,StoryArcType.NIGHT_BELLS),
                    List.of(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD,StoryArcType.THE_EMPTY_CARAVAN));
            for(var edge:edges) {
                assertFalse(StoryQuestService.definition(edge.get(1)).isUnlocked(world,owner),edge.toString());
                data.setStoryCompleted(edge.getFirst().id(),true);
                assertTrue(StoryQuestService.definition(edge.get(1)).isUnlocked(world,owner),edge.toString());
            }
            var shadows=StoryQuestService.definition(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
            data.setStoryCompleted(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id(),false);
            assertFalse(shadows.isUnlocked(world,owner));
            data.setUnlockedProject(VillageProjectType.WATCH_BELL.id(),true);
            data.setMilestoneFlag("pilgrim_contract_completed.quench_for_the_hall",true);
            data.setMilestoneFlag("pilgrim_contract_completed.wool_before_rain",true);
            assertFalse(shadows.isUnlocked(world,owner),"Two rumors cannot replace the third distinct expedition");
            data.setMilestoneFlag("pilgrim_contract_completed.tracks_in_the_dark",true);
            assertTrue(shadows.isUnlocked(world,owner),"Peaceful expeditions use the same earned completion evidence");
            var shrines=StoryQuestService.definition(StoryArcType.SHRINES_BETWEEN_ROADS);
            assertFalse(shrines.isUnlocked(world,owner));
            data.setStoryCompleted(StoryArcType.THE_EMPTY_CARAVAN.id(),true);
            data.setUnlockedProject(VillageProjectType.CARAVAN_YARD.id(),true);
            data.setTradeRouteInt("route_count",2);data.setTradeRouteInt("guild_contracts_completed",2);
            assertFalse(shrines.isUnlocked(world,owner));
            data.setTradeRouteInt("guild_contracts_completed",3);
            assertTrue(shrines.isUnlocked(world,owner));
            assertEquals(-1,data.getWeeklyRewardCycle(),"No weekly payout is required by these prerequisites");
        }
    }
}
