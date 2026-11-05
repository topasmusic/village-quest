package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

/** Saved service records and explicit trusted proof fixtures; no world or gameplay runtime. */
final class KingdomRecognitionTest {
    @Test void completedPersonalStoryCanExplicitlyRebindAndEarnRecognitionInASecondVillageWithoutResettingHistory() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        for(var town:List.of(a,b)) {
            int center=town.equals(a)?0:200;
            state.registerCustom(town,owner,de.quest.village.VillageLifeState.VillageKey.overworld(center,0),
                    new net.minecraft.core.BlockPos(center,64,0),town.equals(a)?"A":"B").orElseThrow();
            int offset=0;
            for(var role:BuildingScanner.Role.values()) {
                var pos=new net.minecraft.core.BlockPos(center+offset++,64,0);
                var report=new BuildingScanner.Report(role,BuildingScanner.Status.VALID,Map.of(),
                        Map.of(BuildingScanner.Feature.LIGHT,Set.of(pos.above())),Set.of());
                assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,town,0,UUID.randomUUID(),-1,pos,
                        net.minecraft.core.Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
            }
        }
        for(var town:List.of(a,b)) { var pos=state.village(town).orElseThrow().arrival();
            assertEquals(VillageFoundingRegistry.Result.APPLIED,state.founding().claim(owner,town,0,List.of(
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),pos.offset(0,0,5),pos.offset(0,0,6)),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),pos.offset(1,0,5),null),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),pos.offset(2,0,5),null)),true,true)); }
        var quests=de.quest.data.QuestState.TYPE.constructor().get(); var data=quests.getPlayerData(owner);
        var other=quests.getPlayerData(UUID.randomUUID()); KingdomStoryProgress.bind(other,a,false);
        assertTrue(KingdomStoryProgress.bind(data,a,false));
        for(var deed:KingdomStoryProgress.Deed.values()) assertTrue(KingdomStoryProgress.record(data,a,deed,"A:"+deed,true));
        assertTrue(state.recognition().complete(owner,a,0,0,0,KingdomRecognitionService.facts(state,data,a,0)));
        data.setStoryCompleted(KingdomStoryProgress.ARC,true); data.setStoryChapterProgress(KingdomStoryProgress.ARC,5);
        data.setMilestoneFlag(KingdomStoryProgress.CHECK_EARNED,true);
        data.setActiveStoryArc(de.quest.quest.story.StoryArcType.ARTIFACT_JOURNEY);
        assertFalse(KingdomStoryProgress.bind(data,b,false)); assertTrue(KingdomStoryProgress.bind(data,b,true));
        assertFalse(KingdomRecognitionService.facts(state,data,b,0).contains(KingdomRecognition.Evidence.TRADE));
        for(var deed:KingdomStoryProgress.Deed.values()) {
            assertFalse(KingdomStoryProgress.has(data,deed));
            assertFalse(KingdomStoryProgress.record(data,a,deed,"stale-A",true));
            assertTrue(KingdomStoryProgress.record(data,b,deed,"B:"+deed,true));
        }
        assertTrue(state.recognition().complete(owner,b,0,0,0,KingdomRecognitionService.facts(state,data,b,0)));
        assertEquals(1,state.recognition().level(a)); assertEquals(1,state.recognition().level(b));
        assertEquals(a,KingdomStoryProgress.target(other));
        var restored=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,
                de.quest.data.QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,quests).getOrThrow()).getOrThrow().getPlayerData(owner);
        assertEquals(b,KingdomStoryProgress.target(restored)); assertTrue(restored.hasStoryCompleted(KingdomStoryProgress.ARC));
        assertEquals(5,restored.getStoryChapterProgress(KingdomStoryProgress.ARC)); assertTrue(restored.hasMilestoneFlag(KingdomStoryProgress.CHECK_EARNED));
        assertEquals(de.quest.quest.story.StoryArcType.ARTIFACT_JOURNEY,restored.getActiveStoryArc());
        var reloaded=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(1,reloaded.recognition().level(a)); assertEquals(1,reloaded.recognition().level(b));
    }
    @Test void rightsRevisionsAndMissingFactsCannotAwardOrSkipProjects() {
        var state=KingdomMarketPlacesTest.state(0); var registry=state.recognition();
        var town=KingdomMarketPlacesTest.TOWN; var owner=KingdomMarketPlacesTest.OWNER;
        var ready=EnumSet.allOf(KingdomRecognition.Evidence.class);
        assertEquals(1,registry.capacity(town));
        assertFalse(registry.complete(UUID.randomUUID(),town,0,0,0,ready));
        assertFalse(registry.complete(owner,town,1,0,0,ready));
        assertFalse(registry.complete(owner,town,0,1,0,ready));
        assertFalse(registry.complete(owner,town,0,0,1,ready));
        assertFalse(registry.complete(owner,town,0,0,0,Set.of()));
        assertEquals(0,registry.level(town));
        assertTrue(registry.complete(owner,town,0,0,0,ready));
        assertEquals(1,registry.level(town));
        assertFalse(registry.complete(owner,town,0,0,0,ready));
        assertEquals(1,registry.revision(town));
    }
    @Test void fiveEarnedLevelsPersistWithoutPopulationOrFreshBuildingRequirements() {
        var state=KingdomMarketPlacesTest.state(0); var town=KingdomMarketPlacesTest.TOWN;
        for(int i=0;i<5;i++) {
            assertTrue(state.recognition().complete(KingdomMarketPlacesTest.OWNER,town,0,i,i,
                    EnumSet.allOf(KingdomRecognition.Evidence.class)));
            assertEquals(Math.max(1,i+1),state.recognition().capacity(town));
        }
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(5,restored.recognition().level(town));
        assertEquals(5,restored.recognition().capacity(town));
        assertFalse(restored.recognition().complete(KingdomMarketPlacesTest.OWNER,town,0,5,5,
                EnumSet.allOf(KingdomRecognition.Evidence.class)));
    }
    @Test void unknownOrMalformedRecognitionIsPreservedWithoutGrantingRanks() {
        var state=KingdomMarketPlacesTest.state(0); var saved=LivingKingdomsState.toNbt(state);
        var future=new CompoundTag(); future.putInt("schema",99); future.putString("future","keep"); saved.put("recognition",future);
        var restored=LivingKingdomsState.fromNbt(saved);
        assertTrue(restored.recognition().readOnly());
        assertFalse(restored.recognition().complete(KingdomMarketPlacesTest.OWNER,KingdomMarketPlacesTest.TOWN,0,0,0,
                EnumSet.allOf(KingdomRecognition.Evidence.class)));
        assertEquals(future,LivingKingdomsState.toNbt(restored).get("recognition"));
    }
    @Test void nativeFactsUseOnlyFreshLocalBuildingsAndTheActorsActualVillageBinding() {
        var state=KingdomMarketPlacesTest.state(0); var town=KingdomMarketPlacesTest.TOWN; var owner=KingdomMarketPlacesTest.OWNER;
        int x=3;
        for(var role:List.of(BuildingScanner.Role.COUNCIL,BuildingScanner.Role.INN,BuildingScanner.Role.STOREHOUSE)) {
            var pos=new net.minecraft.core.BlockPos(x,64,0); x+=3;
            var report=new BuildingScanner.Report(role,BuildingScanner.Status.VALID,Map.of(),
                    Map.of(BuildingScanner.Feature.LIGHT,Set.of(pos.above())),Set.of());
            assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,town,0,UUID.randomUUID(),-1,pos,
                    net.minecraft.core.Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
        }
        var data=new de.quest.data.PlayerQuestData(); KingdomStoryProgress.bind(data,town,false);
        for(var deed:KingdomStoryProgress.Deed.values()) if(deed!=KingdomStoryProgress.Deed.FIRST_PROJECT)
            assertTrue(KingdomStoryProgress.record(data,town,deed,"confirmed:"+deed,true));
        assertFalse(KingdomRecognitionService.facts(state,data,town,0).contains(KingdomRecognition.Evidence.POPULATION));
        var home=state.village(town).orElseThrow().arrival();
        assertEquals(VillageFoundingRegistry.Result.APPLIED,state.founding().claim(owner,town,0,List.of(
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),home.offset(0,0,5),home.offset(0,0,6)),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),home.offset(1,0,5),null),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),home.offset(2,0,5),null)),true,true));
        assertTrue(KingdomRecognitionService.facts(state,data,town,0).containsAll(KingdomRecognition.requirements(0)));
        assertFalse(KingdomRecognitionService.facts(state,data,town,41).contains(KingdomRecognition.Evidence.COUNCIL));
        var council=state.anchors().official(town,BuildingScanner.Role.COUNCIL).orElseThrow();
        assertTrue(state.anchors().maintainCached(council.id(),council.revision(),0,41));
        assertFalse(state.anchors().maySpawn(council.id(),41));
        assertTrue(KingdomRecognitionService.facts(state,data,town,41).contains(KingdomRecognition.Evidence.COUNCIL),"Unchanged monitored buildings remain ready between scans");
        state.anchors().invalidateEvidence(council.id());
        assertFalse(KingdomRecognitionService.facts(state,data,town,41).contains(KingdomRecognition.Evidence.COUNCIL),"Actual dirtiness immediately revokes readiness");
        assertFalse(KingdomRecognitionService.facts(state,data,town,0).contains(KingdomRecognition.Evidence.MEETING));
        KingdomStoryProgress.bind(data,UUID.randomUUID(),true);
        assertTrue(KingdomRecognitionService.facts(state,data,town,0).contains(KingdomRecognition.Evidence.TRADE),"Changing story targets must preserve confirmed project history for the old village");
    }

    @Test void conversationDetailsTrackOnlyThisPlayersConfirmedRolesInThisVillage() {
        var data=new de.quest.data.PlayerQuestData(); var town=UUID.randomUUID(); var other=UUID.randomUUID();
        assertEquals(List.of("leader","innkeeper","storekeeper"),KingdomRecognitionService.missingConversations(data,town));
        KingdomStoryProgress.recordProject(data,other,KingdomStoryProgress.Deed.MEET_LEADER,"other",true);
        KingdomStoryProgress.recordProject(data,town,KingdomStoryProgress.Deed.MARKET_TRADE,"trade",true);
        assertEquals(3,KingdomRecognitionService.missingConversations(data,town).size());
        KingdomStoryProgress.recordProject(data,town,KingdomStoryProgress.Deed.MEET_STOREKEEPER,"store",true);
        assertEquals(List.of("leader","innkeeper"),KingdomRecognitionService.missingConversations(data,town));
        KingdomStoryProgress.recordProject(data,town,KingdomStoryProgress.Deed.MEET_LEADER,"leader",true);
        KingdomStoryProgress.recordProject(data,town,KingdomStoryProgress.Deed.MEET_INNKEEPER,"inn",true);
        assertTrue(KingdomRecognitionService.missingConversations(data,town).isEmpty());
    }
}
