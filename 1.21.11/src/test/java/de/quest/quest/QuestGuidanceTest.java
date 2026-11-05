package de.quest.quest;

import de.quest.data.QuestState;
import de.quest.data.PlayerQuestData;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.kingdom.KingdomStoryProgress;
import de.quest.shrine.VillageBondService;
import de.quest.village.VillageLifeState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import de.quest.network.Payloads;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestService;
import de.quest.questmaster.QuestMasterUiService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs native services and entry construction; never starts a world. */
final class QuestGuidanceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void firstStoryIsVisibleButCannotBeAcceptedBeforeTheFirstDaily() throws Exception {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        UUID owner=UUID.randomUUID(); when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            Method build=QuestMasterUiService.class.getDeclaredMethod("buildStoryEntries",ServerLevel.class,ServerPlayer.class);
            build.setAccessible(true);
            @SuppressWarnings("unchecked") var entries=(List<Payloads.QuestMasterEntryData>)build.invoke(null,world,player);
            assertEquals(1,entries.size(),"The first story needs a locked preview before Daily");
            assertTrue(entries.getFirst().locked());
            assertNull(StoryQuestService.availableArcType(world,owner));
            assertFalse(StoryQuestService.acceptQuest(world,player,StoryArcType.FAILING_HARVEST));
            assertNull(quests.getPlayerData(owner).getActiveStoryArc());
            quests.getPlayerData(owner).setLastRewardDay(1);
            assertEquals(StoryArcType.FAILING_HARVEST,StoryQuestService.availableArcType(world,owner),"No Weekly is required");
        }
    }

    @Test void directAcceptCannotBypassDailyEvenWithoutOpeningAQuestmaster() {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        UUID owner=UUID.randomUUID(); when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        when(server.getPlayerList()).thenReturn(mock(net.minecraft.server.players.PlayerList.class));
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
            var sounds=mockStatic(QuestSoundFeedback.class); var trackers=mockStatic(QuestTrackerService.class);
            var books=mockStatic(QuestBookHelper.class); var parties=mockStatic(de.quest.party.QuestPartyService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            assertFalse(StoryQuestService.acceptQuest(world,player,StoryArcType.FAILING_HARVEST));
            assertTrue(quests.getPlayerData(owner).getStoryDiscovered().isEmpty());
        }
    }

    private static String key(QuestGuidanceService.QuestNextStep step) {
        return ((TranslatableContents)step.action().getContents()).getKey();
    }
    @Test void lockedLaterStoryPointsBackToReachableFirstStoryAndInactiveKingdomRequiresAcceptance() {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        UUID owner=UUID.randomUUID(); when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        quests.getPlayerData(owner).setLastRewardDay(1);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            var next=QuestGuidanceService.lockedStory(world,player,StoryArcType.SHRINES_BETWEEN_ROADS);
            assertEquals("story:core:failing_harvest",next.id()); assertTrue(key(next).endsWith(".accept_story"));
            assertTrue(key(QuestGuidanceService.story(world,player,StoryArcType.LIVING_KINGDOMS)).endsWith(".accept_story"));
            assertNull(quests.getPlayerData(owner).getActiveStoryArc());
        }
    }
    @Test void alreadyConsumedPartyMaterialsKeepTheRealClaimAheadOfMissingVillageRecovery() {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        UUID owner=UUID.randomUUID(); when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        var data=quests.getPlayerData(owner); data.setLastRewardDay(1);
        data.setActiveStoryArc(de.quest.quest.story.StoryLane.KINGDOM,StoryArcType.LIVING_KINGDOMS);
        UUID missing=UUID.randomUUID(); assertTrue(KingdomStoryProgress.bind(data,missing,false));
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS); var parties=mockStatic(de.quest.party.QuestPartyService.class);
            var kingdoms=mockStatic(LivingKingdomsState.class,CALLS_REAL_METHODS)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            kingdoms.when(()->LivingKingdomsState.get(server)).thenReturn(new LivingKingdomsState());
            parties.when(()->de.quest.party.QuestPartyService.storyTurnInRequirementsConsumed(world,owner,StoryArcType.LIVING_KINGDOMS,0)).thenReturn(true);
            var next=QuestGuidanceService.story(world,player,StoryArcType.LIVING_KINGDOMS);
            assertEquals(QuestGuidanceService.Status.READY,next.status()); assertTrue(key(next).endsWith(".claim"));
            assertEquals(missing,KingdomStoryProgress.target(data));
        }
    }
    private static PlayerQuestData welcome(LivingKingdomsState state,UUID village) {
        var data=new PlayerQuestData(); data.setLastRewardDay(1);
        data.setTradeRouteFlag("guild_intro.first_daily_completed",true);
        data.setTradeRouteInt("guild_intro.welcome_village",1);
        data.setTradeRouteInt("bond_village_count",1);
        data.setTradeRouteString(VillageBondService.villageKey(0,"canonical_id"),village.toString());
        data.setTradeRouteString(VillageBondService.villageKey(0,"dimension"),"minecraft:overworld");
        data.setTradeRouteInt(VillageBondService.villageKey(0,"x"),32);
        data.setTradeRouteInt(VillageBondService.villageKey(0,"z"),64);
        return data;
    }
    @Test void welcomeAndLocalHelpAreReachableWithoutRoutesAndDoNotCreateProgress() {
        var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID();
        var village=state.registerGenerated(VillageLifeState.VillageKey.overworld(32,64),new BlockPos(32,70,64),"Oak").orElseThrow();
        var data=welcome(state,village.id()); var life=mock(VillageLifeState.class);
        assertTrue(key(QuestGuidanceService.villageJourney(data,state,life)).endsWith(".contact"));
        data.setTradeRouteFlag("guild_intro.welcome_active",true); data.setTradeRouteInt("guild_intro.welcome_greetings",1);
        var greeting=QuestGuidanceService.villageJourney(data,state,life);
        assertTrue(key(greeting).endsWith(".greet")); assertEquals(1,greeting.current()); assertEquals(3,greeting.required());
        assertEquals(new BlockPos(32,70,64),greeting.location());
        data.setTradeRouteFlag("guild_intro.welcome_completed",true);
        assertTrue(key(QuestGuidanceService.villageJourney(data,state,life)).endsWith(".local_help"));
        assertEquals(QuestGuidanceService.localTrust(data,0,state).action(),QuestGuidanceService.villageJourney(data,state,life).action());
        data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),1);
        assertTrue(key(QuestGuidanceService.villageJourney(data,state,life)).endsWith(".local_help_or_trade"));
        assertEquals(0,data.getTradeRouteInt("route_count")); assertFalse(state.canManageVillage(owner,village.id()));
    }
    @Test void bothTrustedPathsLeadToMandateAndAuthorityThenLeadsToBuildingConfirmation() throws Exception {
        for(boolean trades:new boolean[]{false,true}) {
            var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID();
            var village=state.registerGenerated(VillageLifeState.VillageKey.overworld(32,64),new BlockPos(32,70,64),"Oak").orElseThrow();
            var data=welcome(state,village.id()); data.setTradeRouteFlag("guild_intro.welcome_completed",true);
            data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),trades?1:2);
            if(trades) for(int i=0;i<4;i++) assertTrue(recordTrade(data,village.id()));
            var before=LivingKingdomsState.TYPE.codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow().copy();
            assertTrue(key(QuestGuidanceService.kingdom(data,state,owner,0)).endsWith(".mandate"));
            assertEquals(before,LivingKingdomsState.TYPE.codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow());
            assertTrue(state.recognizeMediator(village.id(),owner,true));
            assertTrue(key(QuestGuidanceService.kingdom(data,state,owner,0)).endsWith(".council_bind"));
            assertTrue(KingdomStoryProgress.bind(data,village.id(),false));
            assertTrue(key(QuestGuidanceService.kingdom(data,state,owner,0)).endsWith(".building"));
        }
    }
    @Test void limitedTradesDoNotReplaceTrustAndMissingTargetDoesNotRedirectTheStory() throws Exception {
        var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID();
        var village=state.registerGenerated(VillageLifeState.VillageKey.overworld(32,64),new BlockPos(32,70,64),"Oak").orElseThrow();
        var data=welcome(state,village.id()); data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),1);
        for(int i=0;i<2;i++) assertTrue(recordTrade(data,village.id()));
        assertTrue(key(QuestGuidanceService.kingdom(data,state,owner,0)).endsWith(".prepare_and_help"));
        UUID missing=UUID.randomUUID(); assertTrue(KingdomStoryProgress.bind(data,missing,false));
        assertTrue(key(QuestGuidanceService.kingdom(data,state,owner,0)).endsWith(".recover_target"));
        assertEquals(missing,KingdomStoryProgress.target(data));
    }
    private static boolean recordTrade(PlayerQuestData data,UUID village) throws Exception {
        Method record=de.quest.shrine.VillageTradeTrustService.class.getDeclaredMethod("recordTrade",
                PlayerQuestData.class,int.class,UUID.class,UUID.class,long.class,UUID.class);
        record.setAccessible(true);
        return (boolean)record.invoke(null,data,0,village,UUID.randomUUID(),1L,UUID.randomUUID());
    }
    @Test void readyKingdomChapterPointsToClaimAndPreservesOtherVillageEvidence() {
        var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID(), id=UUID.randomUUID();
        var village=state.registerCustom(id,owner,VillageLifeState.VillageKey.overworld(32,64),new BlockPos(32,70,64),"Oak").orElseThrow();
        var data=welcome(state,village.id()); assertTrue(KingdomStoryProgress.bind(data,id,false));
        var role=de.quest.kingdom.BuildingScanner.Role.COUNCIL;
        var report=new de.quest.kingdom.BuildingScanner.Report(role,de.quest.kingdom.BuildingScanner.Status.VALID,
                java.util.Map.of(),java.util.Map.of(),java.util.Set.of());
        assertEquals(de.quest.kingdom.KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,id,0,UUID.randomUUID(),-1,
                new BlockPos(32,70,64),net.minecraft.core.Direction.NORTH,de.quest.kingdom.BuildingScanner.Range.BASIC,report,0,0));
        var next=QuestGuidanceService.kingdom(data,state,owner,0);
        assertEquals(QuestGuidanceService.Status.READY,next.status()); assertTrue(key(next).endsWith(".claim"));
        assertEquals(id,next.village()); assertEquals(0,data.getStoryChapterProgress(KingdomStoryProgress.ARC));
        assertFalse(data.hasMilestoneFlag(KingdomStoryProgress.CHECK_EARNED));
    }
    @Test void confirmedVillageLossExplainsReplacementButUnknownPopulationDoesNotDeclareLoss() {
        var state=new LivingKingdomsState();
        var village=state.registerGenerated(VillageLifeState.VillageKey.overworld(32,64),new BlockPos(32,70,64),"Oak").orElseThrow();
        var data=welcome(state,village.id()); data.setTradeRouteFlag("guild_intro.welcome_completed",true);
        var life=mock(VillageLifeState.class);
        assertTrue(key(QuestGuidanceService.villageJourney(data,state,life)).endsWith(".local_help"));
        when(life.status(village.legacyKey())).thenReturn(VillageLifeState.Status.ABANDONED);
        when(life.abandonmentCycle(village.legacyKey())).thenReturn(1);
        assertTrue(key(QuestGuidanceService.villageJourney(data,state,life)).endsWith(".replace_contact"));
        assertEquals(0,data.getTradeRouteInt("route_count"));
    }
}
