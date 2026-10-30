package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.content.story.*;
import de.quest.config.VillageQuestServerConfig;
import de.quest.caravan.TradeRouteService;
import de.quest.quest.story.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.*;

final class StoryActionObjectiveTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for(var item:List.of(net.minecraft.world.item.Items.BREAD,net.minecraft.world.item.Items.OAK_PLANKS))
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
    }
    private static String key(Component c) { return ((TranslatableContents)c.getContents()).getKey(); }

    @Test void shadowsUsesTheActualWaitingCompassAndCourierActionAfterTheSummaryCounter() {
        String prefix="quest.village-quest.story.shadows_on_the_trade_road.";
        var expected=Map.of("chapter_2.progress","calibrate_compass", "chapter_5.progress.1","courier",
                "chapter_5.progress.2","deliver_warning", "chapter_6.progress.2","convoy_nights",
                "rescue.progress.scouting","scouting", "rescue.progress.wait_night","wait_night",
                "rescue.progress.compass","follow_distress");
        for(var entry:expected.entrySet()) {
            var action=entry.getKey().equals("chapter_2.progress")?Component.translatable(prefix+entry.getKey(),0,1)
                    :entry.getKey().equals("chapter_6.progress.2")?Component.translatable(prefix+entry.getKey(),2)
                    :Component.translatable(prefix+entry.getKey());
            assertEquals("quest.village-quest.tracker."+entry.getValue(),key(QuestShortObjective.first(List.of(
                    Component.translatable(prefix+"rescue.progress.count",0,1),action))),entry.getKey());
        }
        assertEquals("quest.village-quest.guidance.claim",key(QuestShortObjective.first(List.of(
                Component.translatable(prefix+"chapter_2.progress",1,1)))));
    }

    @Test void baitShowsChoiceThenTravelOrDefenceAndPeacefulMaterialsBeforeCheckpoints() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var id=UUID.randomUUID();
        var players=mock(net.minecraft.server.players.PlayerList.class);
        when(world.getServer()).thenReturn(server);when(server.getPlayerList()).thenReturn(players);
        var ints=new HashMap<String,Integer>();var flags=new HashSet<String>();
        var chapter=new TheEmptyCaravanStoryArc().chapter(4);
        try(var story=mockStatic(StoryQuestService.class)) {
            story.when(()->StoryQuestService.getQuestInt(eq(world),eq(id),anyString())).thenAnswer(c->ints.getOrDefault(c.getArgument(2),0));
            story.when(()->StoryQuestService.hasStoryFlag(eq(world),eq(id),anyString())).thenAnswer(c->flags.contains(c.getArgument(2)));
            assertEquals("quest.village-quest.tracker.bait_choice",key(chapter.shortObjective(world,id,List.of())));
            flags.add(StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY);
            ints.put(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,EmptyCaravanStoryService.BAIT_SCHEDULED);
            assertEquals("quest.village-quest.tracker.bait_follow",key(chapter.shortObjective(world,id,List.of())));
            ints.put(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,EmptyCaravanStoryService.BAIT_ACTIVE);
            assertEquals("quest.village-quest.tracker.bait_defend",key(chapter.shortObjective(world,id,List.of())));
            ints.put(StoryQuestKeys.EMPTY_CARAVAN_OBJECTIVE_MODE,DifficultyObjectiveMode.PEACEFUL.serializedId());
            var initialRows=QuestShortObjective.rows(chapter.shortObjective(world,id,List.of()));assertEquals(2,initialRows.size());
            var material=(TranslatableContents)initialRows.getFirst().getContents();
            assertEquals("quest.village-quest.tracker.friend_material",material.getKey());
            assertEquals("item.minecraft.bread",key((Component)material.getArgs()[0]));assertEquals(12,material.getArgs()[2]);
            var player=mock(ServerPlayer.class);var inventory=mock(net.minecraft.world.entity.player.Inventory.class);
            var bread=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,12);
            var oak=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS,7);
            when(players.getPlayer(id)).thenReturn(player);when(player.getInventory()).thenReturn(inventory);
            when(inventory.getContainerSize()).thenReturn(2);when(inventory.getItem(0)).thenReturn(bread);when(inventory.getItem(1)).thenReturn(oak);
            var updatedRows=QuestShortObjective.rows(chapter.shortObjective(world,id,List.of()));
            assertTrue(QuestObjectiveProgress.complete(updatedRows.getFirst()));
            var second=(TranslatableContents)updatedRows.get(1).getContents();
            assertEquals("block.minecraft.oak_planks",key((Component)second.getArgs()[0]));
            assertEquals(7,second.getArgs()[1]);assertEquals(16,second.getArgs()[2]);
            assertEquals(12,bread.getCount());assertEquals(7,oak.getCount(),"Reading must not consume supplies");
            flags.add(StoryQuestKeys.EMPTY_CARAVAN_PEACEFUL_SUPPLIES);
            ints.put(StoryQuestKeys.EMPTY_CARAVAN_PEACEFUL_CHECKPOINTS,2);
            var checkpoint=(TranslatableContents)chapter.shortObjective(world,id,List.of()).getContents();
            assertEquals("quest.village-quest.tracker.bait_checkpoints",checkpoint.getKey());
            assertArrayEquals(new Object[]{2,3},checkpoint.getArgs());
            ints.put(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,EmptyCaravanStoryService.BAIT_WON);
            var before=Map.copyOf(ints);var flagsBefore=Set.copyOf(flags);
            assertEquals("quest.village-quest.guidance.claim",key(chapter.shortObjective(world,id,List.of())));
            assertEquals(before,ints);assertEquals(flagsBefore,flags,"Reading never records a choice or supply delivery");
        }
    }

    @Test void finalRelayUsesFreshBaselinesAndHonoursMapOnlyWithoutAdvancingProgress() {
        var world=mock(ServerLevel.class);var id=UUID.randomUUID();var config=mock(VillageQuestServerConfig.class);
        var chapter=new ShrinesBetweenRoadsStoryArc().chapter(5);
        try(var story=mockStatic(StoryQuestService.class);var routes=mockStatic(TradeRouteService.class);var configs=mockStatic(VillageQuestServerConfig.class)) {
            configs.when(VillageQuestServerConfig::get).thenReturn(config);
            when(config.caravanVisualMode()).thenReturn(VillageQuestServerConfig.CaravanVisualMode.MAP_ONLY);
            story.when(()->StoryQuestService.getQuestInt(world,id,StoryQuestKeys.SHRINES_RELAY_CONTRACT_BASELINE)).thenReturn(5);
            story.when(()->StoryQuestService.getQuestInt(world,id,StoryQuestKeys.SHRINES_RELAY_SUCCESS_BASELINE)).thenReturn(8);
            routes.when(()->TradeRouteService.completedGuildContracts(world,id)).thenReturn(5);
            assertEquals("quest.village-quest.tracker.relay_contract",key(chapter.shortObjective(world,id,List.of())));
            routes.when(()->TradeRouteService.completedGuildContracts(world,id)).thenReturn(6);
            assertEquals("quest.village-quest.guidance.claim",key(chapter.shortObjective(world,id,List.of())));
            // Any non-MAP_ONLY mode needs a newly secured interactive incident.
            when(config.caravanVisualMode()).thenReturn(VillageQuestServerConfig.CaravanVisualMode.FULL);
            routes.when(()->TradeRouteService.totalRouteSuccesses(world,id)).thenReturn(8);
            assertEquals("quest.village-quest.tracker.relay_incident",key(chapter.shortObjective(world,id,List.of())));
            routes.when(()->TradeRouteService.totalRouteSuccesses(world,id)).thenReturn(9);
            assertEquals("quest.village-quest.guidance.claim",key(chapter.shortObjective(world,id,List.of())));
            story.verify(()->StoryQuestService.setStoryFlag(any(),any(),anyString(),anyBoolean()),never());
        }
    }
}
