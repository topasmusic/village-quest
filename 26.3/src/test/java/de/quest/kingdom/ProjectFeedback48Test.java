package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

/** Real saved-state and row projection, with no game runtime. */
final class ProjectFeedback48Test {
    @Test void personalProjectEventsSurviveTargetSwitchAndSaveWithoutCreditingOtherVillagesOrPlayers() {
        var quests=QuestState.TYPE.constructor().get();var owner=UUID.randomUUID();var data=quests.getPlayerData(owner);
        var a=UUID.randomUUID();var b=UUID.randomUUID();KingdomStoryProgress.bind(data,a,false);
        for(var deed:List.of(KingdomStoryProgress.Deed.MEET_LEADER,KingdomStoryProgress.Deed.MEET_INNKEEPER,
                KingdomStoryProgress.Deed.MEET_STOREKEEPER,KingdomStoryProgress.Deed.STORE_DELIVERY,KingdomStoryProgress.Deed.MARKET_TRADE)) {
            assertFalse(KingdomStoryProgress.recordProject(data,b,deed,"attempt",false));
            assertFalse(KingdomStoryProgress.hasProject(data,b,deed));
            assertTrue(KingdomStoryProgress.recordProject(data,b,deed,"actual:"+deed,true));
            assertFalse(KingdomStoryProgress.has(data,deed),"Project events in another village cannot complete the personal story");
            assertFalse(KingdomStoryProgress.hasProject(quests.getPlayerData(UUID.randomUUID()),b,deed));
        }
        assertTrue(KingdomStoryProgress.record(data,a,KingdomStoryProgress.Deed.MEET_LEADER,"old-real-dialogue",true));
        KingdomStoryProgress.bind(data,b,true);
        assertTrue(KingdomStoryProgress.hasProject(data,a,KingdomStoryProgress.Deed.MEET_LEADER),"Legacy deeds must be retained before rebind clears the story receipts");
        var saved=QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow();
        var restored=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,saved).getOrThrow().getPlayerData(owner);
        assertTrue(KingdomStoryProgress.hasProject(restored,b,KingdomStoryProgress.Deed.STORE_DELIVERY));
        assertTrue(KingdomStoryProgress.hasProject(restored,b,KingdomStoryProgress.Deed.MARKET_TRADE));
        assertFalse(KingdomStoryProgress.has(restored,KingdomStoryProgress.Deed.MARKET_TRADE));
        assertFalse(KingdomStoryProgress.hasProject(restored,a,KingdomStoryProgress.Deed.STORE_DELIVERY));
    }
    @Test void rememberedRealStaffConversationsDoNotRequireLoadedBodiesOrTheStoryTarget() {
        var state=KingdomMarketPlacesTest.state(0);var data=new PlayerQuestData();var town=KingdomMarketPlacesTest.TOWN;
        KingdomStoryProgress.bind(data,UUID.randomUUID(),false);
        var oldPeople=new ArrayList<String>();
        for(var role:List.of(KingdomPersonRegistry.Role.LEADER,KingdomPersonRegistry.Role.INNKEEPER,KingdomPersonRegistry.Role.STOREKEEPER))
            oldPeople.add(state.people().ensure(town,role,0).orElseThrow().id().toString());
        data.setTradeRouteString("kingdoms.staff.familiar",String.join(",",oldPeople));
        assertTrue(state.people().loadedPeople().isEmpty());
        assertTrue(KingdomRecognitionService.facts(state,data,town,0).contains(KingdomRecognition.Evidence.VOICES));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(KingdomRecognitionService.facts(restored,data,town,1000).contains(KingdomRecognition.Evidence.VOICES));
        assertTrue(KingdomStoryProgress.hasProject(data,town,KingdomStoryProgress.Deed.MEET_LEADER));
        for(int i=0;i<20;i++) KingdomStaffDialogue.remember(data,UUID.randomUUID());
        assertTrue(KingdomRecognitionService.facts(restored,data,town,1000).contains(KingdomRecognition.Evidence.VOICES));
        assertFalse(KingdomRecognitionService.facts(restored,new PlayerQuestData(),town,0).contains(KingdomRecognition.Evidence.VOICES));
    }
    @Test void newFamiliarityWithoutConfirmedLocalConversationNeverBecomesProjectEvidence() {
        var state=KingdomMarketPlacesTest.state(0);var data=new PlayerQuestData();var town=KingdomMarketPlacesTest.TOWN;
        for(var role:List.of(KingdomPersonRegistry.Role.LEADER,KingdomPersonRegistry.Role.INNKEEPER,KingdomPersonRegistry.Role.STOREKEEPER))
            KingdomStaffDialogue.remember(data,state.people().ensure(town,role,0).orElseThrow().id());
        assertFalse(KingdomRecognitionService.facts(state,data,town,0).contains(KingdomRecognition.Evidence.VOICES));
    }
    @Test void counterRowsReportOneRealStallOrTwoLegacyBlocksInAgreementWithTheCheck() {
        for(boolean actual:List.of(false,true)) {
            var stations=new EnumMap<BuildingScanner.Feature,Set<BlockPos>>(BuildingScanner.Feature.class);
            stations.put(BuildingScanner.Feature.COUNTER,actual?Set.of(BlockPos.ZERO):Set.of(BlockPos.ZERO,BlockPos.ZERO.east()));
            if(actual)stations.put(BuildingScanner.Feature.STALL,Set.of(BlockPos.ZERO));
            var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,
                    Map.of(BuildingScanner.Check.COUNTER,true),stations,Set.of());
            var row=KingdomBuildingService.rows(report).getFirst();
            assertEquals(actual?1:2,row.required());assertEquals(row.required(),row.found());assertTrue(row.passed());
        }
    }
}
