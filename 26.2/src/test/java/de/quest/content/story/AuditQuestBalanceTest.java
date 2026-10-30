package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.PlayerQuestData;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

/** Chapter behavior with real counters/stacks, only server/stat/inventory services replaced. */
final class AuditQuestBalanceTest {
    final UUID owner=UUID.randomUUID();PlayerQuestData data;ServerLevel world;ServerPlayer player;
    final Map<Item,Integer> crafts=new HashMap<>(); final List<ItemStack> goods=new ArrayList<>();
    MockedStatic<StoryQuestService> story;MockedStatic<DailyQuestService> daily;
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:BuiltInRegistries.ITEM){var id=BuiltInRegistries.ITEM.getKey(item);
            if(id.getPath().endsWith("_horse_armor") || List.of(Items.PAPER,Items.BOOK,Items.LEATHER_CHESTPLATE,Items.IRON_PICKAXE,Items.BUCKET,Items.SHEARS,Items.SHIELD).contains(item)) {
                var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64);
                if(List.of(Items.IRON_PICKAXE,Items.SHEARS,Items.SHIELD).contains(item))components.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE,100).set(net.minecraft.core.component.DataComponents.DAMAGE,0);
                item.builtInRegistryHolder().bindComponents(components.build());
            }
        }
    }
    @BeforeEach void setup(){
        data=new PlayerQuestData();world=mock(ServerLevel.class);player=mock(ServerPlayer.class);when(player.getUUID()).thenReturn(owner);
        story=mockStatic(StoryQuestService.class);daily=mockStatic(DailyQuestService.class);
        story.when(()->StoryQuestService.getQuestInt(eq(world),eq(owner),anyString())).thenAnswer(c->data.getStoryInt(c.getArgument(2)));
        story.when(()->StoryQuestService.setQuestInt(eq(world),eq(owner),anyString(),anyInt())).thenAnswer(c->{data.setStoryInt(c.getArgument(2),c.getArgument(3));return null;});
        story.when(()->StoryQuestService.addQuestIntClamped(eq(world),eq(owner),anyString(),anyInt(),anyInt())).thenAnswer(c->{String k=c.getArgument(2);data.setStoryInt(k,Math.min(c.getArgument(4),data.getStoryInt(k)+(int)c.getArgument(3)));return null;});
        story.when(()->StoryQuestService.hasStoryFlag(eq(world),eq(owner),anyString())).thenAnswer(c->data.hasStoryFlag(c.getArgument(2)));
        story.when(()->StoryQuestService.setStoryFlag(eq(world),eq(owner),anyString(),anyBoolean())).thenAnswer(c->{data.setStoryFlag(c.getArgument(2),c.getArgument(3));return null;});
        story.when(()->StoryQuestService.countCompletionItem(eq(world),eq(owner),any())).thenAnswer(c->goods.stream().filter(s->s.is(c.<Item>getArgument(2))).mapToInt(ItemStack::getCount).sum());
        story.when(()->StoryQuestService.countMatchingCompletionItems(eq(world),eq(owner),any())).thenAnswer(c->goods.stream().filter(c.<Predicate<ItemStack>>getArgument(2)).mapToInt(ItemStack::getCount).sum());
        story.when(()->StoryQuestService.consumeMatchingCompletionItems(eq(world),eq(owner),any(),anyInt())).thenAnswer(c->{var match=c.<Predicate<ItemStack>>getArgument(2);int left=c.getArgument(3);if(goods.stream().filter(match).mapToInt(ItemStack::getCount).sum()<left)return false;for(var s:goods)if(match.test(s)){int n=Math.min(left,s.getCount());s.shrink(n);left-=n;}return left==0;});
        daily.when(()->DailyQuestService.getCraftedStat(eq(player),any())).thenAnswer(c->crafts.getOrDefault(c.getArgument(1),0));
    }
    @AfterEach void close(){daily.close();story.close();}
    @Test void booksCraftedBeforePaperFinishesCountOnceAndSurviveFurtherTicks(){
        var chapter=new MarketRoadTroublesStoryArc().chapter(1);chapter.onAccepted(world,player);
        crafts.put(Items.PAPER,48);crafts.put(Items.BOOK,16);chapter.onServerTick(world,player);
        assertEquals(16,data.getStoryInt(StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED));
        crafts.put(Items.PAPER,96);chapter.onServerTick(world,player);chapter.onServerTick(world,player);
        assertEquals(16,data.getStoryInt(StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED));assertEquals(96,data.getStoryInt(StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED));
        goods.add(new ItemStack(Items.PAPER,48));goods.add(new ItemStack(Items.BOOK,16));assertTrue(chapter.isComplete(world,player));
    }
    @Test void booksInThePaperThresholdTickCountToo(){var c=new MarketRoadTroublesStoryArc().chapter(1);c.onAccepted(world,player);crafts.put(Items.PAPER,96);crafts.put(Items.BOOK,16);c.onServerTick(world,player);assertEquals(16,data.getStoryInt(StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED));}
    @Test void sixAdultsAndSixSavedContactsCanFinishMarketDay(){var c=new MarketRoadTroublesStoryArc().chapter(3);data.setStoryInt(StoryQuestKeys.MARKET_ROAD_VILLAGERS,6);var adults=new ArrayList<Villager>();for(int i=0;i<6;i++)adults.add(mock(Villager.class));when(world.getEntitiesOfClass(eq(Villager.class),any(),any())).thenReturn(adults);c.onUseBlock(world,player,BlockPos.ZERO,Blocks.BELL.defaultBlockState(),ItemStack.EMPTY);assertTrue(c.isComplete(world,player));}
    @Test void fiveAdultsCannotFinishMarketDay(){var c=new MarketRoadTroublesStoryArc().chapter(3);data.setStoryInt(StoryQuestKeys.MARKET_ROAD_VILLAGERS,6);when(world.getEntitiesOfClass(eq(Villager.class),any(),any())).thenReturn(Collections.nCopies(5,mock(Villager.class)));c.onUseBlock(world,player,BlockPos.ZERO,Blocks.BELL.defaultBlockState(),ItemStack.EMPTY);assertFalse(c.isComplete(world,player));}
    @Test void contactsRequireDifferentAdultIdentitiesAndStopAtSix(){try(var dialogue=mockStatic(VillagerDialogueService.class)){
        var c=new MarketRoadTroublesStoryArc().chapter(3);var baby=mock(Villager.class);when(baby.isBaby()).thenReturn(true);c.onEntityUse(world,player,baby,ItemStack.EMPTY);assertEquals(0,data.getStoryInt(StoryQuestKeys.MARKET_ROAD_VILLAGERS));
        for(int i=0;i<7;i++){var villager=mock(Villager.class);when(villager.getStringUUID()).thenReturn(new UUID(0,i+1).toString());c.onEntityUse(world,player,villager,ItemStack.EMPTY);c.onEntityUse(world,player,villager,ItemStack.EMPTY);}
        assertEquals(6,data.getStoryInt(StoryQuestKeys.MARKET_ROAD_VILLAGERS));
    }}
    void contact(StoryChapterDefinition c,String name){var villager=mock(Villager.class);var profession=BuiltInRegistries.VILLAGER_PROFESSION.get(Identifier.withDefaultNamespace(name)).orElseThrow();when(villager.getVillagerData()).thenReturn(new VillagerData(BuiltInRegistries.VILLAGER_TYPE.get(Identifier.withDefaultNamespace("plains")).orElseThrow(),profession,1));c.onEntityUse(world,player,villager,ItemStack.EMPTY);}
    @Test void anyFourWorkingProfessionsCountButDuplicatesAndUnemployedDoNot(){try(var dialogue=mockStatic(VillagerDialogueService.class)){
        var c=new MarketRoadTroublesStoryArc().chapter(2);data.setStoryInt(StoryQuestKeys.MARKET_ROAD_TRADES,24);
        for(String name:List.of("armorer","butcher","cleric"))contact(c,name);contact(c,"cleric");contact(c,"none");contact(c,"nitwit");assertFalse(c.isComplete(world,player));contact(c,"cartographer");assertTrue(c.isComplete(world,player));
    }}
    @Test void oldProfessionFlagsRetainTheirCredit(){var c=new MarketRoadTroublesStoryArc().chapter(2);for(var k:List.of(StoryQuestKeys.MARKET_ROAD_TOOLSMITH,StoryQuestKeys.MARKET_ROAD_FARMER,StoryQuestKeys.MARKET_ROAD_SHEPHERD,StoryQuestKeys.MARKET_ROAD_LIBRARIAN))data.setStoryFlag(k,true);data.setStoryInt(StoryQuestKeys.MARKET_ROAD_TRADES,24);assertTrue(c.isComplete(world,player));}
    @Test void newAndOldProfessionEvidenceRemainSharedInTheSameChapter(){var p=de.quest.party.QuestShareProfiles.storyProfile(StoryArcType.MARKET_ROAD_TROUBLES,2);assertTrue(p.matchesFlag("market_road_profession_minecraft:cleric"));assertTrue(p.matchesFlag(StoryQuestKeys.MARKET_ROAD_FARMER));assertFalse(p.matchesFlag("other_quest_profession"));}
    @Test void eachNativeHorseArmorCanBeShownWithoutBeingConsumed(){var c=new RestlessPensStoryArc().chapter(3);data.setStoryInt(StoryQuestKeys.RESTLESS_PENS_CALL,1);for(var item:BuiltInRegistries.ITEM){var id=BuiltInRegistries.ITEM.getKey(item);if(!id.getNamespace().equals("minecraft")||!id.getPath().endsWith("_horse_armor"))continue;goods.clear();goods.add(new ItemStack(item));assertTrue(c.isComplete(world,player),id.toString());assertTrue(c.consumeCompletionRequirements(world,player));assertEquals(1,goods.getFirst().getCount());}goods.clear();goods.add(new ItemStack(Items.LEATHER_CHESTPLATE));assertFalse(c.isComplete(world,player));}
    @Test void oneFreshToolOfEachKindIsEnoughAndAllFourAreConsumed(){var c=new SilentForgeStoryArc().chapter(2);c.onAccepted(world,player);for(var item:List.of(Items.IRON_PICKAXE,Items.BUCKET,Items.SHEARS,Items.SHIELD)){crafts.put(item,1);goods.add(new ItemStack(item));}c.onServerTick(world,player);assertTrue(c.isComplete(world,player));assertTrue(c.consumeCompletionRequirements(world,player));assertTrue(goods.stream().allMatch(ItemStack::isEmpty));}
    @Test void damagedToolOrMissingFreshProofCannotConsumeAnyPartOfTheKit(){
        var c=new SilentForgeStoryArc().chapter(2);c.onAccepted(world,player);for(var item:List.of(Items.IRON_PICKAXE,Items.BUCKET,Items.SHEARS,Items.SHIELD))goods.add(new ItemStack(item));
        assertFalse(c.consumeCompletionRequirements(world,player));assertTrue(goods.stream().allMatch(s->s.getCount()==1));
        for(var item:List.of(Items.IRON_PICKAXE,Items.BUCKET,Items.SHEARS,Items.SHIELD))crafts.put(item,1);c.onServerTick(world,player);
        goods.getFirst().set(net.minecraft.core.component.DataComponents.DAMAGE,1);
        assertFalse(c.consumeCompletionRequirements(world,player));assertTrue(goods.stream().allMatch(s->s.getCount()==1));
    }
}
