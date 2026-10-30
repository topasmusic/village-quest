package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.quest.story.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

/** Real chapter producers and saved quest data; no world ticks or gameplay simulation. */
final class NativeStoryObjectiveMatrixTest {
    private static Item originalCore;
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
        // Item registration belongs to mod startup; this server-free fixture supplies its distinct item identity.
        originalCore=de.quest.registry.ModItems.CRACKED_SHRINE_CORE;
        de.quest.registry.ModItems.CRACKED_SHRINE_CORE=Items.ECHO_SHARD;
    }
    @AfterAll static void restoreCore() { de.quest.registry.ModItems.CRACKED_SHRINE_CORE=originalCore; }

    @TestFactory Stream<DynamicTest> everyActualChapterProducesAReadOnlyObjectiveInBothModes() {
        return Arrays.stream(StoryArcType.values()).flatMap(arc ->
                java.util.stream.IntStream.range(0,arc.chapterCount()).boxed().flatMap(chapter ->
                Stream.of(Difficulty.PEACEFUL,Difficulty.NORMAL).map(mode -> DynamicTest.dynamicTest(
                        arc.id()+"/"+chapter+"/"+mode,() -> {
            try(var f=new Fixture(arc,chapter,mode)) {
                var definition=StoryQuestService.currentChapter(f.world,f.id,arc);
                assertNotNull(definition);
                var ints=Map.copyOf(f.data.getStoryIntState());
                var flags=Set.copyOf(f.data.getStoryFlags());
                var routes=Map.copyOf(f.data.getTradeRouteIntState());
                var lines=definition.progressLines(f.world,f.id);
                assertFalse(lines.isEmpty());
                assertNotNull(definition.shortObjective(f.world,f.id,lines));
                assertFalse(definition.descriptionLines(f.world,f.id).isEmpty());
                assertEquals(ints,f.data.getStoryIntState(),"Reading must not record progress");
                assertEquals(flags,f.data.getStoryFlags(),"Reading must not choose or grant a reward");
                assertEquals(routes,f.data.getTradeRouteIntState(),"Reading must not initialize a target");
            }
        }))));
    }

    @Test void harvestedMaterialsThatWereStoredElsewhereAreStillNeededForTheActualTurnIn() {
        try(var f=new Fixture(StoryArcType.FAILING_HARVEST,0,Difficulty.PEACEFUL)) {
            f.data.setStoryInt(StoryQuestKeys.FAILING_HARVEST_WHEAT,16);
            f.data.setStoryInt(StoryQuestKeys.FAILING_HARVEST_POTATO,8);
            var chapter=StoryQuestService.currentChapter(f.world,f.id,StoryArcType.FAILING_HARVEST);
            assertFalse(chapter.isComplete(f.world,f.player));
            var initialRows=QuestShortObjective.rows(chapter.shortObjective(f.world,f.id,chapter.progressLines(f.world,f.id)));
            assertEquals(2,initialRows.size());
            var missing=(TranslatableContents)initialRows.getFirst().getContents();
            assertEquals("quest.village-quest.tracker.friend_material",missing.getKey());
            assertEquals(0,missing.getArgs()[1]); assertEquals(16,missing.getArgs()[2]);
            f.inventory.setItem(0,new ItemStack(Items.WHEAT,16));
            var updatedRows=QuestShortObjective.rows(chapter.shortObjective(f.world,f.id,chapter.progressLines(f.world,f.id)));
            assertEquals(2,updatedRows.size());assertTrue(QuestObjectiveProgress.complete(updatedRows.getFirst()));
            var potato=(TranslatableContents)updatedRows.get(1).getContents();
            assertEquals(8,potato.getArgs()[2]);
            f.inventory.setItem(1,new ItemStack(Items.POTATO,8));
            assertTrue(chapter.isComplete(f.world,f.player));
            assertEquals("quest.village-quest.guidance.claim",((TranslatableContents)chapter.shortObjective(
                    f.world,f.id,chapter.progressLines(f.world,f.id)).getContents()).getKey());
            assertEquals(16,f.inventory.getItem(0).getCount(),"Presentation never consumes the delivery");
        }
    }

    @Test void realHarvestAllowsTheSecondMaterialFirstAndKeepsBothCountersVisible() {
        try(var f=new Fixture(StoryArcType.FAILING_HARVEST,0,Difficulty.PEACEFUL)) {
            var chapter=StoryQuestService.currentChapter(f.world,f.id,StoryArcType.FAILING_HARVEST);
            var initial=QuestShortObjective.rows(chapter.shortObjective(f.world,f.id,chapter.progressLines(f.world,f.id)));
            assertEquals(2,initial.size());
            f.data.setStoryInt(StoryQuestKeys.FAILING_HARVEST_POTATO,8);
            var before=Map.copyOf(f.data.getStoryIntState());
            var changed=QuestShortObjective.rows(chapter.shortObjective(f.world,f.id,chapter.progressLines(f.world,f.id)));
            assertEquals(2,changed.size());assertFalse(QuestObjectiveProgress.complete(changed.get(0)));
            assertTrue(QuestObjectiveProgress.complete(changed.get(1)));assertEquals(before,f.data.getStoryIntState());
        }
    }

    @TestFactory Stream<DynamicTest> caravanOffersDescribeTheSavedPeacefulWorkWithoutChangingRewards() {
        return Stream.of(2,3,4,5).map(index -> DynamicTest.dynamicTest("shadows/"+index,() -> {
            try(var f=new Fixture(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD,index,Difficulty.NORMAL)) {
                f.data.setStoryInt(StoryQuestKeys.SHADOWS_OBJECTIVE_MODE,DifficultyObjectiveMode.PEACEFUL.serializedId());
                var chapter=StoryQuestService.currentChapter(f.world,f.id,StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
                var before=Map.copyOf(f.data.getStoryIntState());
                assertEquals("quest.village-quest.story.shadows_on_the_trade_road.peaceful.description."+(index+1),
                        ((TranslatableContents)chapter.descriptionLines(f.world,f.id).getFirst().getContents()).getKey());
                var original=chapter.buildCompletion();
                var peaceful=chapter.buildCompletion(f.world,f.id);
                assertEquals("quest.village-quest.story.shadows_on_the_trade_road.peaceful.complete."+(index+1)+".1",
                        ((TranslatableContents)peaceful.completionLine1().getContents()).getKey());
                assertEquals(original.currencyReward(),peaceful.currencyReward());
                assertEquals(original.levels(),peaceful.levels());
                assertEquals(original.reputationTrack(),peaceful.reputationTrack());
                assertEquals(original.reputationAmount(),peaceful.reputationAmount());
                assertEquals(original.unlockedProject(),peaceful.unlockedProject());
                assertEquals(before,f.data.getStoryIntState());
                f.data.setStoryInt(StoryQuestKeys.SHADOWS_OBJECTIVE_MODE,DifficultyObjectiveMode.COMBAT.serializedId());
                assertEquals(chapter.descriptionLines(),chapter.descriptionLines(f.world,f.id));
                assertEquals(original,chapter.buildCompletion(f.world,f.id));
            }
        }));
    }

    @Test void peacefulBaitExplainsAndSeparatelyTracksTheRealMaterialsBeforeTheCheckpoints() {
        try(var f=new Fixture(StoryArcType.THE_EMPTY_CARAVAN,4,Difficulty.NORMAL)) {
            f.data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_OBJECTIVE_MODE,DifficultyObjectiveMode.PEACEFUL.serializedId());
            var chapter=StoryQuestService.currentChapter(f.world,f.id,StoryArcType.THE_EMPTY_CARAVAN);
            assertEquals("quest.village-quest.story.the_empty_caravan.peaceful.description.5",
                    ((TranslatableContents)chapter.descriptionLines(f.world,f.id).getLast().getContents()).getKey());
            assertEquals("quest.village-quest.story.the_empty_caravan.peaceful.complete.5.1",
                    ((TranslatableContents)chapter.buildCompletion(f.world,f.id).completionLine1().getContents()).getKey());
            f.data.setStoryFlag(StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY,true);
            f.data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,1);
            var lines=chapter.progressLines(f.world,f.id);
            assertEquals(2,lines.stream().filter(c->c.getContents() instanceof TranslatableContents t
                    && t.getKey().equals("screen.village-quest.ui_review.objective_row")).count());
            var materialRows=QuestShortObjective.rows(chapter.shortObjective(f.world,f.id,lines));assertEquals(2,materialRows.size());
            var bread=(TranslatableContents)materialRows.getFirst().getContents();
            assertEquals(12,bread.getArgs()[2]);
            f.inventory.setItem(0,new ItemStack(Items.BREAD,12));
            var updatedRows=QuestShortObjective.rows(chapter.shortObjective(f.world,f.id,chapter.progressLines(f.world,f.id)));
            assertTrue(QuestObjectiveProgress.complete(updatedRows.getFirst()));
            var planks=(TranslatableContents)updatedRows.get(1).getContents();
            assertEquals(16,planks.getArgs()[2]);
            f.inventory.setItem(1,new ItemStack(Items.OAK_PLANKS,16));
            assertEquals("quest.village-quest.tracker.bait_checkpoints",((TranslatableContents)chapter.shortObjective(
                    f.world,f.id,chapter.progressLines(f.world,f.id)).getContents()).getKey());
            f.data.setStoryFlag(StoryQuestKeys.EMPTY_CARAVAN_PEACEFUL_SUPPLIES,true);
            f.inventory.setItem(0,ItemStack.EMPTY); f.inventory.setItem(1,ItemStack.EMPTY);
            assertEquals("quest.village-quest.tracker.bait_checkpoints",((TranslatableContents)chapter.shortObjective(
                    f.world,f.id,chapter.progressLines(f.world,f.id)).getContents()).getKey());
            f.data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,3);
            assertEquals("quest.village-quest.guidance.claim",((TranslatableContents)chapter.shortObjective(
                    f.world,f.id,chapter.progressLines(f.world,f.id)).getContents()).getKey());
        }
    }

    @Test void peacefulFinalWatchDoesNotClaimThatThePlayerSurvivedARaid() {
        try(var f=new Fixture(StoryArcType.NIGHT_BELLS,3,Difficulty.PEACEFUL)) {
            var chapter=StoryQuestService.currentChapter(f.world,f.id,StoryArcType.NIGHT_BELLS);
            var completion=chapter.buildCompletion(f.world,f.id);
            assertEquals("quest.village-quest.story.night_bells.peaceful.complete.3.1",
                    ((TranslatableContents)completion.completionLine1().getContents()).getKey());
            assertEquals(chapter.buildCompletion().currencyReward(),completion.currencyReward());
            assertEquals(chapter.buildCompletion().unlockedProject(),completion.unlockedProject());
        }
    }

    @Test void actualStoryClaimUsesSavedPeacefulDialogueBeforeClearingTheCoreProgress() {
        try(var f=new Fixture(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD,2,Difficulty.NORMAL);
            var parties=mockStatic(de.quest.party.QuestPartyService.class);
            var books=mockStatic(QuestBookHelper.class);var trackers=mockStatic(QuestTrackerService.class);
            var ui=mockStatic(de.quest.questmaster.QuestMasterUiService.class);
            var projects=mockStatic(VillageProjectService.class)) {
            f.data.setStoryInt(StoryQuestKeys.SHADOWS_OBJECTIVE_MODE,DifficultyObjectiveMode.PEACEFUL.serializedId());
            f.data.setStoryInt(StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS,1);
            PersonalResetReceipts.storyPaidThrough(f.data,StoryArcType.SHADOWS_ON_THE_TRADE_ROAD,3);
            assertTrue(StoryQuestService.claimFromQuestMaster(f.world,f.player,StoryArcType.SHADOWS_ON_THE_TRADE_ROAD));
            var messages=org.mockito.ArgumentCaptor.forClass(Component.class);
            verify(f.player,atLeastOnce()).sendSystemMessage(messages.capture(),eq(false));
            assertTrue(messages.getAllValues().stream().anyMatch(c->containsKey(c,"quest.village-quest.story.shadows_on_the_trade_road.peaceful.complete.3.1")));
            assertFalse(messages.getAllValues().stream().anyMatch(c->containsKey(c,"quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.1")));
            assertEquals(0,f.data.getStoryInt(StoryQuestKeys.SHADOWS_OBJECTIVE_MODE));
            assertNull(f.data.getActiveStoryArc());assertEquals(3,f.data.getStoryChapterProgress(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id()));
            assertFalse(StoryQuestService.claimFromQuestMaster(f.world,f.player,StoryArcType.SHADOWS_ON_THE_TRADE_ROAD));
        }
    }

    private static boolean containsKey(Component line,String key) {
        if(line.getContents() instanceof TranslatableContents t) {
            if(t.getKey().equals(key))return true;
            for(Object arg:t.getArgs())if(arg instanceof Component c && containsKey(c,key))return true;
        }
        return line.getSiblings().stream().anyMatch(c->containsKey(c,key));
    }

    @Test void activePeacefulEscortShowsBreadThenTheNextMerchantCheckpointWithoutAdvancingAnything() throws Exception {
        try(var f=new Fixture(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD,2,Difficulty.PEACEFUL)) {
            Class<?> service=de.quest.content.story.ShadowsTradeRoadEncounterService.class;
            Class<?> spec=Class.forName(service.getName()+"$EncounterSpec");
            var specConstructor=spec.getDeclaredConstructors()[0]; specConstructor.setAccessible(true);
            var peacefulSpec=specConstructor.newInstance(1,3,Component.literal("escort"),false,List.of());
            Class<?> active=Class.forName(service.getName()+"$ActiveEncounter");
            var constructor=active.getDeclaredConstructor(UUID.class,spec,net.minecraft.core.BlockPos.class);constructor.setAccessible(true);
            Object encounter=constructor.newInstance(f.id,peacefulSpec,new net.minecraft.core.BlockPos(100,64,100));
            var checkpoints=active.getDeclaredField("peacefulCheckpoints");checkpoints.setAccessible(true);
            @SuppressWarnings("unchecked") var plan=(List<net.minecraft.core.BlockPos>)checkpoints.get(encounter);
            plan.addAll(List.of(new net.minecraft.core.BlockPos(120,64,100),new net.minecraft.core.BlockPos(140,64,100),new net.minecraft.core.BlockPos(160,64,100)));
            var activeField=service.getDeclaredField("ACTIVE");activeField.setAccessible(true);
            @SuppressWarnings("unchecked") var all=(Map<UUID,Object>)activeField.get(null);
            all.put(f.id,encounter);
            try {
                var chapter=StoryQuestService.currentChapter(f.world,f.id,StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
                var lines=chapter.progressLines(f.world,f.id);
                var bread=(TranslatableContents)chapter.shortObjective(f.world,f.id,lines).getContents();
                assertEquals("screen.village-quest.ui_review.objective_row",bread.getKey());
                assertEquals(8,bread.getArgs()[2]);
                var delivered=active.getDeclaredField("peacefulSuppliesDelivered");delivered.setAccessible(true);delivered.setBoolean(encounter,true);
                var next=(TranslatableContents)chapter.shortObjective(f.world,f.id,chapter.progressLines(f.world,f.id)).getContents();
                assertEquals("quest.village-quest.story.shadows_on_the_trade_road.peaceful.progress.checkpoints",next.getKey());
                assertArrayEquals(new Object[]{0,3},next.getArgs());
                var target=(TranslatableContents)chapter.progressLines(f.world,f.id).stream().filter(c->c.getContents() instanceof TranslatableContents t
                        && t.getKey().endsWith(".peaceful.next_checkpoint")).findFirst().orElseThrow().getContents();
                assertArrayEquals(new Object[]{120,64,100},target.getArgs());
                var index=active.getDeclaredField("peacefulCheckpointIndex");index.setAccessible(true);
                assertEquals(0,index.getInt(encounter));assertEquals(3,plan.size());
            } finally {all.remove(f.id);}
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID id=UUID.randomUUID();
        final MinecraftServer server=mock(MinecraftServer.class);
        final ServerLevel world=mock(ServerLevel.class);
        final ServerPlayer player=mock(ServerPlayer.class);
        final net.minecraft.world.entity.player.Inventory inventory;
        final QuestState state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        final PlayerQuestData data=state.getPlayerData(id);
        final org.mockito.MockedStatic<QuestState> states;
        final org.mockito.MockedStatic<LivingKingdomsState> kingdoms;
        Fixture(StoryArcType arc,int chapter,Difficulty mode) {
            when(world.getServer()).thenReturn(server); when(world.getDifficulty()).thenReturn(mode);
            when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
            var players=mock(net.minecraft.server.players.PlayerList.class);
            when(server.getPlayerList()).thenReturn(players); when(players.getPlayer(id)).thenReturn(player);
            when(player.getUUID()).thenReturn(id); when(player.blockPosition()).thenReturn(net.minecraft.core.BlockPos.ZERO);
            inventory=mock(net.minecraft.world.entity.player.Inventory.class);
            var slots=new ArrayList<ItemStack>(Collections.nCopies(36,ItemStack.EMPTY));
            when(inventory.getContainerSize()).thenReturn(36);
            when(inventory.getItem(anyInt())).thenAnswer(c -> slots.get(c.getArgument(0)));
            doAnswer(c -> {slots.set(c.getArgument(0),c.getArgument(1));return null;}).when(inventory).setItem(anyInt(),any());
            when(player.getInventory()).thenReturn(inventory); when(player.level()).thenReturn(world);
            data.setLastRewardDay(1); data.setStoryChapterProgress(arc.id(),chapter);
            if(arc==StoryArcType.LIVING_KINGDOMS) data.setKingdomStoryActive(true); else data.setActiveStoryArc(arc);
            int objectiveMode=DifficultyObjectiveMode.forDifficulty(mode).serializedId();
            if(arc==StoryArcType.NIGHT_BELLS)data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_MODE_PREFIX+chapter,objectiveMode);
            if(arc==StoryArcType.THE_EMPTY_CARAVAN)data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_OBJECTIVE_MODE,objectiveMode);
            if(arc==StoryArcType.SHADOWS_ON_THE_TRADE_ROAD)data.setStoryInt(StoryQuestKeys.SHADOWS_OBJECTIVE_MODE,objectiveMode);
            states=mockStatic(QuestState.class,CALLS_REAL_METHODS); states.when(()->QuestState.get(server)).thenReturn(state);
            kingdoms=mockStatic(LivingKingdomsState.class,CALLS_REAL_METHODS);
            kingdoms.when(()->LivingKingdomsState.get(server)).thenReturn(new LivingKingdomsState());
        }
        public void close() { kingdoms.close(); states.close(); }
    }
}
