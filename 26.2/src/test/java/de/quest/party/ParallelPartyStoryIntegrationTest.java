package de.quest.party;

import de.quest.data.*;
import de.quest.quest.story.*;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.reward.VanillaRewardService;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class ParallelPartyStoryIntegrationTest {
    final UUID leader=UUID.randomUUID(), guest=UUID.randomUUID();
    QuestState state;
    ServerLevel world;
    MinecraftServer server;
    ServerPlayer host, member;
    MockedStatic<QuestState> states;
    MockedStatic<QuestPartyMessaging> messaging;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        net.minecraft.world.item.Items.BREAD.builtInRegistryHolder().bindComponents(components);
        net.minecraft.world.item.Items.EMERALD.builtInRegistryHolder().bindComponents(components);
    }
    @BeforeEach void setup() {
        state=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        world=mock(ServerLevel.class); server=mock(MinecraftServer.class);
        var players=mock(PlayerList.class);
        when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world);
        when(server.isDedicatedServer()).thenReturn(true); when(server.getPlayerList()).thenReturn(players);
        host=player(leader); member=player(guest);
        when(players.getPlayer(leader)).thenReturn(host); when(players.getPlayer(guest)).thenReturn(member);
        states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
        states.when(()->QuestState.get(server)).thenAnswer(c->state);
        messaging=mockStatic(QuestPartyMessaging.class);
        messaging.when(()->QuestPartyMessaging.onlineMembers(eq(world),anyList())).thenCallRealMethod();
    }
    @AfterEach void cleanup() { QuestPartyService.resetRuntimeState(); messaging.close(); states.close(); }
    ServerPlayer player(UUID id) {
        var p=mock(ServerPlayer.class); when(p.getUUID()).thenReturn(id);
        when(p.level()).thenReturn(world); when(p.getDisplayName()).thenReturn(Component.literal(id.toString())); return p;
    }
    PartyRuntime load(boolean synced) {
        var p=new PartyRuntime(UUID.randomUUID(),leader); p.members().addAll(List.of(leader,guest));
        for(var arc:List.of(StoryArcType.FAILING_HARVEST,StoryArcType.LIVING_KINGDOMS)) {
            int chapter=arc==StoryArcType.LIVING_KINGDOMS?0:2;
            var s=QuestPartySessions.ensureStorySession(p,arc,chapter); s.markSynced(leader);
            state.getPlayerData(leader).setActiveStoryArc(StoryLane.forArc(arc),arc);
            state.getPlayerData(leader).setStoryChapterProgress(arc.id(),chapter);
            state.getPlayerData(guest).setStoryChapterProgress(arc.id(),chapter);
            if(synced) {
                s.markSynced(guest); state.getPlayerData(guest).setActiveStoryArc(StoryLane.forArc(arc),arc);
            } else QuestPartySessions.offersFor(p,arc).put(guest,new QuestJoinOffer(s.questId(),s.revision(),leader));
        }
        state.setQuestPartyState(QuestPartyPersistence.write(Map.of(p.id(),p),Map.of()));
        QuestPartyService.loadPersistentState(server); return QuestPartyService.partyFor(leader);
    }
    @Test void kingdomMembersRemainSharedWithConcurrentCoreAndDifferentTargetsStayExcluded() {
        var p=load(true); UUID village=UUID.randomUUID();
        de.quest.kingdom.KingdomStoryProgress.bind(state.getPlayerData(leader),village,true);
        de.quest.kingdom.KingdomStoryProgress.bind(state.getPlayerData(guest),village,true);
        p.kingdomStory().setFlag("kingdoms.bound."+village,true);
        assertEquals(2,QuestPartyService.storySharedMemberCount(world,leader,StoryArcType.LIVING_KINGDOMS,0));
        assertEquals(Set.of(leader,guest),new HashSet<>(QuestPartyService.activeStoryMembers(world,leader,StoryArcType.LIVING_KINGDOMS,0)));
        de.quest.kingdom.KingdomStoryProgress.bind(state.getPlayerData(guest),UUID.randomUUID(),true);
        assertEquals(List.of(leader),QuestPartyService.activeStoryMembers(world,leader,StoryArcType.LIVING_KINGDOMS,0));
        assertEquals(1,QuestPartyService.storySharedMemberCount(world,leader,StoryArcType.LIVING_KINGDOMS,0));
        assertEquals(2,QuestPartyService.storySharedMemberCount(world,leader,StoryArcType.FAILING_HARVEST,2));
    }

    @Test void turnInEvidenceNeverCreditsAFormerPartnerWithDifferentVillage() {
        var p=load(true); UUID town=UUID.randomUUID();
        de.quest.kingdom.KingdomStoryProgress.bind(state.getPlayerData(leader),town,true);
        de.quest.kingdom.KingdomStoryProgress.bind(state.getPlayerData(guest),UUID.randomUUID(),true);
        p.kingdomStory().setFlag("kingdoms.bound."+town,true);
        QuestPartyService.markStoryTurnInRequirementsConsumed(world,leader,StoryArcType.LIVING_KINGDOMS,0);
        String receipt=StoryQuestKeys.sharedTurnInConsumed(StoryArcType.LIVING_KINGDOMS,0);
        assertTrue(state.getPlayerData(leader).hasStoryFlag(receipt));
        assertFalse(state.getPlayerData(guest).hasStoryFlag(receipt));
        assertFalse(QuestPartyService.storyTurnInRequirementsConsumed(world,guest,StoryArcType.LIVING_KINGDOMS,0));
    }
    @Test void decliningKingdomLeavesCoreOfferAndAmbiguousLegacyActionConsumesNeither() {
        var p=load(false);
        assertFalse(QuestPartyService.acceptStoryOffer(world,member));
        assertEquals(1,p.storyOffers().size()); assertEquals(1,p.kingdomStoryOffers().size());
        assertFalse(QuestPartyService.declineStoryOffer(world,member));
        assertTrue(QuestPartyService.declineStoryOffer(world,member,StoryLane.KINGDOM));
        assertTrue(p.kingdomStoryOffers().isEmpty()); assertTrue(p.storyOffers().containsKey(guest));
    }
    @Test void kingdomAcceptancePreservesCoreAndBonusAndIgnoresCoreCooldown() {
        var p=load(false); var data=state.getPlayerData(guest);
        data.setActiveStoryArc(StoryLane.CORE,StoryArcType.NIGHT_BELLS);
        data.setStoryInt("core_counter",11); data.setStoryCooldownUntil(Long.MAX_VALUE);
        var bonus=new CompoundTag(); bonus.putInt("version",1); bonus.putString("key","story:night_bells");
        assertTrue(data.vanillaRewards().accept("story","night_bells:3",bonus));
        var before=data.vanillaRewards().toNbt();
        try(var stories=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var trackers=mockStatic(QuestTrackerService.class)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.isUnlocked(world,guest)).thenReturn(true); when(arc.chapter(0)).thenReturn(chapter);
            when(chapter.title()).thenReturn(Component.literal("Council")); when(chapter.canAccept(world,member)).thenReturn(true);
            stories.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            assertTrue(QuestPartyService.acceptStoryOffer(world,member,StoryLane.KINGDOM));
            assertTrue(data.isKingdomStoryActive()); assertEquals(StoryArcType.NIGHT_BELLS,data.getActiveStoryArc());
            assertEquals(11,data.getStoryInt("core_counter")); assertEquals(before,data.vanillaRewards().toNbt());
            assertTrue(p.kingdomStory().hasSynced(guest)); assertTrue(p.storyOffers().containsKey(guest));
            verify(chapter).onAccepted(world,member);
        }
    }
    @Test void coreCompletionNeverClearsKingdomSessionAndOfflineMemberRetainsClaim() {
        var p=load(true); var kingdom=p.kingdomStory();
        when(server.getPlayerList().getPlayer(guest)).thenReturn(null);
        state.getPlayerData(leader).setActiveStoryArc(StoryLane.CORE,null);
        QuestPartyService.clearStorySessionIfFinished(world,leader,StoryArcType.FAILING_HARVEST,2);
        assertNotNull(p.story().questId()); // Offline partner still owns its claim.
        state.getPlayerData(guest).setActiveStoryArc(StoryLane.CORE,null);
        QuestPartyService.clearStorySessionIfFinished(world,leader,StoryArcType.FAILING_HARVEST,2);
        assertNull(p.story().questId()); assertEquals("living_kingdoms#0",kingdom.questId());
    }
    @Test void resetDetachRemovesOnlyOwnerFromBothSessionsAndKeepsOfflinePartner() {
        var p=load(true); p.story().setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED,true);
        when(server.getPlayerList().getPlayer(guest)).thenReturn(null);
        QuestPartyService.detachForPersonalRestart(world,leader);
        assertNull(QuestPartyService.partyFor(leader)); assertSame(p,QuestPartyService.partyFor(guest));
        assertEquals(guest,p.leaderId());
        for(var s:List.of(p.story(),p.kingdomStory())) {
            assertFalse(s.hasSynced(leader)); assertTrue(s.hasSynced(guest));
            assertFalse(s.canJoinAfterTurnIn(StoryQuestKeys.SHARED_TURN_IN_CONSUMED,leader));
        }
        QuestPartyService.persistRuntimeState(server); QuestPartyService.loadPersistentState(server);
        assertTrue(QuestPartyService.partyFor(guest).kingdomStory().hasSynced(guest));
    }
    @Test void leavingLastPartnerCopiesBothConsumedReceiptsBeforeDisbanding() {
        var p=load(true);
        p.story().setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED,true);
        p.kingdomStory().setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED,true);
        assertTrue(QuestPartyService.leave(world,host));
        assertNull(QuestPartyService.partyFor(guest));
        for(var id:List.of(leader,guest)) for(var arc:List.of(StoryArcType.FAILING_HARVEST,StoryArcType.LIVING_KINGDOMS)) {
            int chapter=arc==StoryArcType.LIVING_KINGDOMS?0:2;
            assertTrue(state.getPlayerData(id).hasStoryFlag(StoryQuestKeys.sharedTurnInConsumed(arc,chapter)));
        }
    }
    @Test void resetFromPrivateWorldLoadsPersistedBothLanesAndPreservesOfflinePaidPartnerAcrossReplay() {
        var p=load(true);
        for(var session:List.of(p.story(),p.kingdomStory())) {
            session.setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED,true);
        }
        PersonalResetReceipts.storyPaidThrough(state.getPlayerData(guest),StoryArcType.FAILING_HARVEST,3);
        PersonalResetReceipts.storyPaidThrough(state.getPlayerData(guest),StoryArcType.LIVING_KINGDOMS,1);
        state.setQuestPartyState(QuestPartyPersistence.write(Map.of(p.id(),p),Map.of()));
        QuestPartyService.resetRuntimeState();when(server.isDedicatedServer()).thenReturn(false);
        when(server.getPlayerCount()).thenReturn(1);when(server.getPlayerList().getPlayer(guest)).thenReturn(null);
        for(int retry=0;retry<2;retry++) {
            QuestPartyService.detachForPersonalRestart(world,leader);assertTrue(QuestPartyService.personalRestartDetached(world,leader));
            var remaining=QuestPartyService.partyFor(guest);assertNotNull(remaining);assertEquals(Set.of(guest),remaining.members());
            for(var s:List.of(remaining.story(),remaining.kingdomStory())) {
                assertTrue(s.hasSynced(guest));assertTrue(s.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED));
            }
            assertTrue(PersonalResetReceipts.storyPaid(state.getPlayerData(guest),StoryArcType.FAILING_HARVEST,2));
            assertTrue(PersonalResetReceipts.storyPaid(state.getPlayerData(guest),StoryArcType.LIVING_KINGDOMS,0));
            state=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,
                    QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,state).getOrThrow()).getOrThrow();
            QuestPartyService.resetRuntimeState();
        }
    }

    @Test void changingCoreProgressKeepsKingdomMaterialReceiptUntilPersonalReset() {
        var data=state.getPlayerData(leader);
        String key=StoryQuestKeys.sharedTurnInConsumed(StoryArcType.LIVING_KINGDOMS,1);
        data.setStoryFlag(key,true); data.clearStoryProgress();
        assertTrue(data.hasStoryFlag(key));
        data.resetStoryState(); assertFalse(data.hasStoryFlag(key));
    }
    @Test void pendingResetIsNotExpiredOrOverwrittenWhenLastPartnerLeaves() {
        var p=load(true);var root=(CompoundTag)QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,state).getOrThrow();
        var owners=new CompoundTag();owners.putString(leader.toString(),"unknown reset intent");root.put("personalResetTransactions",owners);
        state=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,root).getOrThrow();
        p.disconnectDeadlines().put(leader,1L);when(server.getPlayerList().getPlayer(leader)).thenReturn(null);
        var oldLeader=state.getPlayerData(leader);oldLeader.setStoryInt("frozen.sentinel",31);
        QuestPartyService.onServerTick(server);assertSame(p,QuestPartyService.partyFor(leader));
        assertFalse(QuestPartyService.leave(world,host));assertTrue(QuestPartyService.leave(world,member));
        assertSame(p,QuestPartyService.partyFor(leader));assertEquals(Set.of(leader),p.members());assertEquals(31,oldLeader.getStoryInt("frozen.sentinel"));
        assertSame(oldLeader,state.getPlayerData(leader));assertEquals(2,state.getPlayerData(leader).getStoryChapterProgress(StoryArcType.FAILING_HARVEST.id()));
    }

    @Test void samePersonalChapterStillRequiresExplicitPartyAcceptance() {
        var p=load(false); state.getPlayerData(guest).setKingdomStoryActive(true);
        try(var stories=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.chapter(0)).thenReturn(chapter); when(chapter.title()).thenReturn(Component.literal("Council"));
            when(chapter.progressLines(world,leader)).thenReturn(List.of());
            stories.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            QuestPartyService.onStoryQuestAccepted(world,host,StoryArcType.LIVING_KINGDOMS,0,chapter);
            assertFalse(p.kingdomStory().hasSynced(guest));
            assertTrue(p.kingdomStoryOffers().containsKey(guest));
            assertTrue(p.storyOffers().containsKey(guest));
        }
    }

    @Test void kingdomOfferCannotReplaceDifferentPersonalVillage() {
        var p=load(false); var data=state.getPlayerData(guest);
        UUID own=UUID.randomUUID(), shared=UUID.randomUUID();
        de.quest.kingdom.KingdomStoryProgress.bind(data,own,true);
        p.kingdomStory().setFlag("kingdoms.bound."+shared,true);
        try(var stories=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var trackers=mockStatic(QuestTrackerService.class)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.isUnlocked(world,guest)).thenReturn(true); when(arc.chapter(0)).thenReturn(chapter);
            when(chapter.title()).thenReturn(Component.literal("Council"));
            stories.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            assertFalse(QuestPartyService.acceptStoryOffer(world,member,StoryLane.KINGDOM));
            assertEquals(own,de.quest.kingdom.KingdomStoryProgress.target(data));
            assertFalse(p.kingdomStory().hasSynced(guest));
        }
    }

    @Test void unresolvedDeliveryCannotAcceptPartyStoryDirectly() {
        var p=load(false); var unknown=new CompoundTag(); unknown.putInt("schema",72);
        assertTrue(state.compareIntroTransaction(guest,null,unknown));
        try(var stories=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.isUnlocked(world,guest)).thenReturn(true); when(arc.chapter(0)).thenReturn(chapter);
            when(chapter.title()).thenReturn(Component.literal("Council"));
            stories.when(()->StoryQuestService.definition(StoryArcType.LIVING_KINGDOMS)).thenReturn(arc);
            assertFalse(QuestPartyService.acceptStoryOffer(world,member,StoryLane.KINGDOM));
            assertFalse(p.kingdomStory().hasSynced(guest));
            assertTrue(p.kingdomStoryOffers().containsKey(guest));
        }
    }

    @Test void offlineClaimSurvivesNextChapterAndReloadWithoutSecondMaterialOrUniqueReward() throws Exception {
        var p=load(true);
        var arcType=StoryArcType.FAILING_HARVEST;
        p.story().setInt(StoryQuestKeys.FAILING_HARVEST_BREAD,9);
        var hostBread=inventory(host);
        var guestBread=inventory(member);
        var awarded=new HashMap<UUID,List<net.minecraft.world.item.ItemStack>>();
        when(server.getPlayerList().getPlayer(guest)).thenReturn(null);
        try(var stories=mockStatic(StoryQuestService.class,CALLS_REAL_METHODS);
            var projects=mockStatic(VillageProjectService.class);
            var books=mockStatic(de.quest.quest.QuestBookHelper.class);
            var trackers=mockStatic(QuestTrackerService.class);
            var ui=mockStatic(de.quest.questmaster.QuestMasterUiService.class);
            var sounds=mockStatic(de.quest.quest.QuestSoundFeedback.class)) {
            var arc=mock(StoryArcDefinition.class); var chapter=mock(StoryChapterDefinition.class);
            when(arc.chapter(2)).thenReturn(chapter); when(arc.chapterCount()).thenReturn(4);
            when(chapter.isComplete(world,host)).thenReturn(true);
            when(chapter.consumeCompletionRequirements(world,host)).thenAnswer(c->
                    QuestPartyService.consumeStoryTurnInItem(world,leader,arcType,2,net.minecraft.world.item.Items.BREAD,5));
            when(chapter.buildCompletion(any(),any())).thenCallRealMethod();
            when(chapter.buildCompletion()).thenReturn(new StoryChapterCompletion(Component.literal("Bread"),
                    Component.empty(),Component.empty(),Component.empty(),0,0,null,0,null));
            doAnswer(c->{
                var recipient=(ServerPlayer)c.getArgument(1);
                awarded.computeIfAbsent(recipient.getUUID(),id->new ArrayList<>())
                        .add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
                return null;
            }).when(chapter).onClaimed(eq(world),any());
            stories.when(()->StoryQuestService.definition(arcType)).thenReturn(arc);
            assertTrue(StoryQuestService.claimFromQuestMaster(world,host,arcType));
            assertEquals(0,hostBread.getCount()); assertEquals(5,guestBread.getCount());
            assertTrue(state.getPlayerData(leader).isKingdomStoryActive());
            state.getPlayerData(leader).setActiveStoryArc(StoryLane.CORE,arcType);
            QuestPartyService.onStoryQuestAccepted(world,host,arcType,3,null);
            assertEquals(9,state.getPlayerData(guest).getStoryInt(StoryQuestKeys.FAILING_HARVEST_BREAD));
            var saved=QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,state).getOrThrow();
            state=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,saved).getOrThrow();
            QuestPartyService.loadPersistentState(server);
            when(server.getPlayerList().getPlayer(guest)).thenReturn(member);
            assertTrue(StoryQuestService.claimFromQuestMaster(world,member,arcType));
            assertFalse(StoryQuestService.claimFromQuestMaster(world,member,arcType));
            assertEquals(5,guestBread.getCount());
            assertEquals(1,awarded.get(leader).size()); assertEquals(1,awarded.get(guest).size());
            assertTrue(PersonalResetReceipts.storyPaid(state.getPlayerData(guest),arcType,2));
            assertTrue(state.getPlayerData(guest).isKingdomStoryActive());
            assertEquals("living_kingdoms#0",QuestPartyService.partyFor(guest).kingdomStory().questId());
        }
    }

    private net.minecraft.world.item.ItemStack inventory(ServerPlayer player) throws Exception {
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,5);
        var inv=mock(net.minecraft.world.entity.player.Inventory.class);
        when(inv.getContainerSize()).thenReturn(1); when(inv.getItem(0)).thenReturn(stack);
        when(player.getInventory()).thenReturn(inv);
        var menuField=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menuField.setAccessible(true);
        menuField.set(player,mock(net.minecraft.world.inventory.InventoryMenu.class));
        return stack;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void kingdomDeedsIncludeOnlyExplicitlyAdmittedPartnersIncludingOffline(boolean admitted) {
        var p=load(admitted); UUID village=UUID.randomUUID();
        state.getPlayerData(guest).setKingdomStoryActive(true);
        for(var id:List.of(leader,guest)) de.quest.kingdom.KingdomStoryProgress.bind(state.getPlayerData(id),village,true);
        p.kingdomStory().setFlag("kingdoms.bound."+village,true);
        when(server.isSameThread()).thenReturn(true);
        if(admitted) when(server.getPlayerList().getPlayer(guest)).thenReturn(null);
        var actor=admitted?host:member;
        var other=admitted?guest:leader;
        try(var registries=mockStatic(de.quest.kingdom.LivingKingdomsState.class);
            var stories=mockStatic(StoryQuestService.class);
            var ui=mockStatic(de.quest.questmaster.QuestMasterUiService.class)) {
            var registry=mock(de.quest.kingdom.LivingKingdomsState.class);
            when(registry.village(village)).thenReturn(Optional.of(mock(de.quest.kingdom.LivingKingdomsState.VillageView.class)));
            registries.when(()->de.quest.kingdom.LivingKingdomsState.get(server)).thenReturn(registry);
            assertTrue(de.quest.kingdom.KingdomStoryService.recordConfirmed(world,actor,village,
                    de.quest.kingdom.KingdomStoryProgress.Deed.MARKET_TRADE,"receipt:one"));
            assertTrue(de.quest.kingdom.KingdomStoryProgress.has(state.getPlayerData(actor.getUUID()),de.quest.kingdom.KingdomStoryProgress.Deed.MARKET_TRADE));
            assertEquals(admitted,de.quest.kingdom.KingdomStoryProgress.has(state.getPlayerData(other),de.quest.kingdom.KingdomStoryProgress.Deed.MARKET_TRADE));
        }
    }
}
