package de.quest.data;

import de.quest.quest.story.*;
import de.quest.party.QuestPartyService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.QuestSoundFeedback;
import de.quest.questmaster.QuestMasterUiService;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class ParallelStoryProgressTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private final UUID owner=UUID.randomUUID();
    private QuestState state;
    private ServerLevel world;
    private ServerPlayer player;
    private MockedStatic<QuestState> states;
    @BeforeEach void setup() {
        state=QuestState.fromNbt(new CompoundTag()); world=mock(ServerLevel.class);
        var server=mock(MinecraftServer.class); var players=mock(PlayerList.class);
        player=mock(ServerPlayer.class); when(player.getUUID()).thenReturn(owner);
        when(world.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayer(owner)).thenReturn(player);
        states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
        states.when(()->QuestState.get(server)).thenAnswer(c->state);
    }
    @AfterEach void cleanup() { states.close(); }

    @Test void kingdomOfferIsAnnouncedOnceWhileCoreIsActiveAndNoticeSurvivesReload() throws Exception {
        var method=de.quest.quest.QuestAvailabilityNotifier.class.getDeclaredMethod("notifyStory",ServerLevel.class,ServerPlayer.class,PlayerQuestData.class);
        method.setAccessible(true);
        try(var stories=mockStatic(StoryQuestService.class);
            var progression=mockStatic(de.quest.questmaster.QuestMasterProgressionService.class)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            progression.when(()->de.quest.questmaster.QuestMasterProgressionService.isStoryCategoryUnlocked(world,owner)).thenReturn(true);
            stories.when(()->StoryQuestService.activeArcType(world,owner)).thenReturn(StoryArcType.FAILING_HARVEST);
            stories.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            stories.when(()->StoryQuestService.chapter(world,owner,StoryArcType.LIVING_KINGDOMS)).thenReturn(chapter);
            when(arc.isUnlocked(world,owner)).thenReturn(true); when(chapter.canAccept(world,player)).thenReturn(true);
            var data=state.getPlayerData(owner);
            assertEquals(true,method.invoke(null,world,player,data));
            assertEquals(false,method.invoke(null,world,player,data));
            assertNull(data.getStoryOfferNoticeKey());
            assertEquals("living_kingdoms:0",data.getTradeRouteString("kingdoms.story.offer_notice"));
            state=QuestState.fromNbt(QuestState.toNbt(state));
            assertEquals(false,method.invoke(null,world,player,state.getPlayerData(owner)));
        }
    }

    @Test void unresolvedDeliveryRefusesStoryAcceptanceBeforeAnyCallback() {
        var unknown=new CompoundTag(); unknown.putInt("schema",72);
        assertTrue(state.compareIntroTransaction(owner,null,unknown));
        try(var services=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var parties=mockStatic(QuestPartyService.class); var books=mockStatic(QuestBookHelper.class);
            var trackers=mockStatic(QuestTrackerService.class); var sounds=mockStatic(QuestSoundFeedback.class);
            var ui=mockStatic(QuestMasterUiService.class)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.isUnlocked(world,owner)).thenReturn(true); when(arc.chapter(0)).thenReturn(chapter);
            when(chapter.canAccept(world,player)).thenReturn(true); when(chapter.title()).thenReturn(Component.literal("Kingdom"));
            services.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            assertFalse(StoryQuestService.acceptQuest(world,player,StoryArcType.LIVING_KINGDOMS));
            verify(chapter,never()).onAccepted(any(),any());
            assertFalse(state.getPlayerData(owner).isKingdomStoryActive());
        }
    }

    @Test void acceptingFinalKingdomChapterCannotForgetReservedCoreReward() {
        var data=state.getPlayerData(owner);
        data.setActiveStoryArc(StoryLane.CORE,StoryArcType.NIGHT_BELLS);
        data.setStoryChapterProgress("living_kingdoms",4);
        var offer=new CompoundTag(); offer.putInt("version",1);
        assertTrue(data.vanillaRewards().accept("story","night_bells:3",offer));
        var before=data.vanillaRewards().toNbt();
        try(var services=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var parties=mockStatic(QuestPartyService.class); var books=mockStatic(QuestBookHelper.class);
            var trackers=mockStatic(QuestTrackerService.class); var sounds=mockStatic(QuestSoundFeedback.class);
            var ui=mockStatic(QuestMasterUiService.class)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.isUnlocked(world,owner)).thenReturn(true); when(arc.chapter(4)).thenReturn(chapter);
            when(chapter.canAccept(world,player)).thenReturn(true); when(chapter.title()).thenReturn(Component.literal("Kingdom"));
            services.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            parties.when(()->QuestPartyService.rewardStoryKey(world,owner,StoryArcType.LIVING_KINGDOMS,4))
                    .thenReturn("story:living_kingdoms");
            assertTrue(StoryQuestService.acceptQuest(world,player,StoryArcType.LIVING_KINGDOMS));
            assertEquals(before,data.vanillaRewards().toNbt());
            assertEquals(StoryArcType.NIGHT_BELLS,data.getActiveStoryArc());
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothAcceptanceAndClaimOrdersKeepOtherLaneAndLocalCounters(boolean kingdomFirst) {
        try(var services=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var parties=mockStatic(QuestPartyService.class); var books=mockStatic(QuestBookHelper.class);
            var trackers=mockStatic(QuestTrackerService.class); var sounds=mockStatic(QuestSoundFeedback.class);
            var ui=mockStatic(QuestMasterUiService.class);
            var projects=mockStatic(VillageProjectService.class);
            var reputation=mockStatic(de.quest.reputation.SocialReputationService.class)) {
            var data=state.getPlayerData(owner);
            data.setLastRewardDay(1); // Both lanes are accepted only after the first ordinary Daily.
            data.setStoryInt("guild_town.saved",23);
            var core=mock(StoryChapterDefinition.class); var kingdom=mock(StoryChapterDefinition.class);
            var coreArc=mock(StoryArcDefinition.class); var kingdomArc=mock(StoryArcDefinition.class);
            when(coreArc.isUnlocked(world,owner)).thenReturn(true); when(kingdomArc.isUnlocked(world,owner)).thenReturn(true);
            when(coreArc.chapter(0)).thenReturn(core); when(kingdomArc.chapter(0)).thenReturn(kingdom);
            when(coreArc.chapterCount()).thenReturn(4); when(kingdomArc.chapterCount()).thenReturn(5);
            for(var chapter:java.util.List.of(core,kingdom)) {
                when(chapter.buildCompletion(any(),any())).thenCallRealMethod();
                when(chapter.canAccept(world,player)).thenReturn(true);
                when(chapter.title()).thenReturn(Component.literal("Chapter"));
                when(chapter.isComplete(world,player)).thenReturn(true);
                when(chapter.consumeCompletionRequirements(world,player)).thenReturn(true);
                when(chapter.buildCompletion()).thenReturn(new StoryChapterCompletion(Component.literal("Title"),
                        Component.empty(),Component.empty(),Component.empty(),0,0,null,0,null));
            }
            services.when(()->StoryQuestService.definition(StoryArcType.FAILING_HARVEST)).thenReturn(coreArc);
            services.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(kingdomArc);
            StoryArcType first=kingdomFirst?StoryArcType.LIVING_KINGDOMS:StoryArcType.FAILING_HARVEST;
            StoryArcType second=kingdomFirst?StoryArcType.FAILING_HARVEST:StoryArcType.LIVING_KINGDOMS;
            assertTrue(StoryQuestService.acceptQuest(world,player,first));
            if(!kingdomFirst) data.setStoryInt("core_counter",17);
            assertTrue(StoryQuestService.acceptQuest(world,player,second));
            if(kingdomFirst) data.setStoryInt("core_counter",17);
            assertEquals(17,data.getStoryInt("core_counter"));
            assertEquals(2,StoryQuestService.activeCount(world,owner));
            assertTrue(StoryQuestService.claimFromQuestMaster(world,player,first));
            assertTrue(StoryQuestService.isActive(world,owner,second));
            assertEquals(1,StoryQuestService.activeCount(world,owner));
            if(kingdomFirst) assertEquals(17,data.getStoryInt("core_counter"));
            assertTrue(StoryQuestService.claimFromQuestMaster(world,player,second));
            assertEquals(0,StoryQuestService.activeCount(world,owner));
            assertEquals(23,data.getStoryInt("guild_town.saved"));
            assertEquals(0,data.getStoryCooldownUntil());
            verify(core,times(1)).onClaimed(world,player); verify(kingdom,times(1)).onClaimed(world,player);
            assertFalse(StoryQuestService.claimFromQuestMaster(world,player,first));
            assertFalse(StoryQuestService.claimFromQuestMaster(world,player,second));
        }
    }

    @Test void explicitReadinessUsesRequestedArcAndClassicFacadeStaysCore() {
        var data=state.getPlayerData(owner);
        data.setActiveStoryArc(StoryLane.CORE,StoryArcType.FAILING_HARVEST);
        data.setActiveStoryArc(StoryLane.KINGDOM,StoryArcType.LIVING_KINGDOMS);
        assertEquals(2,StoryQuestService.activeCount(world,owner));
        assertTrue(StoryQuestService.isActive(world,owner,StoryArcType.LIVING_KINGDOMS));
        assertEquals(StoryArcType.FAILING_HARVEST,StoryQuestService.activeArcType(world,owner));
        try(var services=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var parties=mockStatic(QuestPartyService.class);
            var books=mockStatic(QuestBookHelper.class); var trackers=mockStatic(QuestTrackerService.class);
            var sounds=mockStatic(QuestSoundFeedback.class); var ui=mockStatic(QuestMasterUiService.class)) {
            var core=mock(StoryChapterDefinition.class); var kingdom=mock(StoryChapterDefinition.class);
            var coreArc=mock(StoryArcDefinition.class); var kingdomArc=mock(StoryArcDefinition.class);
            when(coreArc.chapter(0)).thenReturn(core); when(kingdomArc.chapter(0)).thenReturn(kingdom);
            when(kingdom.isComplete(world,player)).thenReturn(true);
            when(kingdom.title()).thenReturn(Component.literal("Kingdom"));
            services.when(()->StoryQuestService.definition(StoryArcType.FAILING_HARVEST)).thenReturn(coreArc);
            services.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(kingdomArc);
            assertFalse(StoryQuestService.isReadyForClaim(world,player,StoryArcType.FAILING_HARVEST));
            assertTrue(StoryQuestService.isReadyForClaim(world,player,StoryArcType.LIVING_KINGDOMS));
            assertTrue(StoryQuestService.completeIfEligible(world,player,StoryArcType.LIVING_KINGDOMS));
            assertTrue(data.hasTradeRouteFlag("kingdoms.story.ready.0"));
            assertFalse(data.hasStoryFlag("story_ready_living_kingdoms_0"));
            assertTrue(StoryQuestService.completeIfEligible(world,player,StoryArcType.LIVING_KINGDOMS));
            sounds.verify(()->QuestSoundFeedback.playReady(world,player),times(1));
            assertFalse(StoryQuestService.completeIfEligible(world,player));
            assertFalse(data.hasStoryFlag("story_ready_failing_harvest_0"));
            assertTrue(data.isKingdomStoryActive());
            verify(core,never()).onClaimed(any(),any()); verify(kingdom,never()).onClaimed(any(),any());
        }
    }

    @Test void kingdomFinalClaimUsesItsOwnRewardIdentityAndDoesNotTouchCoreCooldownOrCounters() {
        try(var services=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var parties=mockStatic(QuestPartyService.class); var books=mockStatic(QuestBookHelper.class);
            var trackers=mockStatic(QuestTrackerService.class); var sounds=mockStatic(QuestSoundFeedback.class);
            var ui=mockStatic(QuestMasterUiService.class); var projects=mockStatic(VillageProjectService.class);
            var social=mockStatic(de.quest.reputation.SocialReputationService.class);
            var rewards=mockStatic(de.quest.reward.VanillaRewardService.class)) {
            var data=state.getPlayerData(owner);
            data.setActiveStoryArc(StoryLane.CORE,StoryArcType.NIGHT_BELLS);
            data.setStoryChapterProgress("night_bells",3);
            data.setStoryChapterProgress("living_kingdoms",4);
            data.setStoryCooldownUntil(Long.MAX_VALUE-123);
            data.setStoryInt("core_counter",17);
            data.setStoryInt("guild_town.saved",29);
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.isUnlocked(world,owner)).thenReturn(true); when(arc.chapter(4)).thenReturn(chapter);
            when(arc.chapterCount()).thenReturn(5);
            when(chapter.canAccept(world,player)).thenReturn(true);
            when(chapter.title()).thenReturn(Component.literal("Kingdom"));
            when(chapter.isComplete(world,player)).thenReturn(true);
            when(chapter.consumeCompletionRequirements(world,player)).thenReturn(true);
            when(chapter.buildCompletion(any(),any())).thenCallRealMethod();
            when(chapter.buildCompletion()).thenReturn(new StoryChapterCompletion(Component.literal("End"),
                    Component.empty(),Component.empty(),Component.empty(),0,0,null,0,null));
            services.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            parties.when(()->QuestPartyService.rewardStoryKey(world,owner,StoryArcType.LIVING_KINGDOMS,4))
                    .thenReturn("story:living_kingdoms");
            rewards.when(()->de.quest.reward.VanillaRewardService.prepare(eq(world),eq(player),eq("kingdom_story"),
                    eq("story:living_kingdoms"),eq("living_kingdoms:4"),any(),any())).thenReturn(true);
            assertTrue(StoryQuestService.acceptQuest(world,player,StoryArcType.LIVING_KINGDOMS));
            assertTrue(StoryQuestService.claimFromQuestMaster(world,player,StoryArcType.LIVING_KINGDOMS));
            rewards.verify(()->de.quest.reward.VanillaRewardService.complete(world,player,"kingdom_story","living_kingdoms:4"));
            rewards.verify(()->de.quest.reward.VanillaRewardService.complete(world,player,"story","night_bells:3"),never());
            assertTrue(data.hasStoryCompleted("living_kingdoms"));
            assertEquals(Long.MAX_VALUE-123,data.getStoryCooldownUntil());
            assertEquals(17,data.getStoryInt("core_counter"));
            assertEquals(29,data.getStoryInt("guild_town.saved"));
            assertEquals(StoryArcType.NIGHT_BELLS,data.getActiveStoryArc());
            assertTrue(PersonalResetReceipts.storyPaid(data,StoryArcType.LIVING_KINGDOMS,4));
            assertFalse(StoryQuestService.claimFromQuestMaster(world,player,StoryArcType.LIVING_KINGDOMS));
        }
    }
}
