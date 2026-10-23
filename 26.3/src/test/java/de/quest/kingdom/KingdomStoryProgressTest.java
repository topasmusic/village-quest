package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomStoryProgressTest {
    private static final UUID OWNER=UUID.randomUUID(), TOWN=UUID.randomUUID(), OTHER=UUID.randomUUID();
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void confirmingAnotherVillageDoesNotSilentlyRetargetAndOnlyAnExplicitRebindResetsLocalDeeds() {
        var data = new PlayerQuestData();
        assertTrue(KingdomStoryProgress.bind(data,TOWN,false));
        assertFalse(KingdomStoryProgress.bind(data,OTHER,false));
        assertEquals(TOWN,KingdomStoryProgress.target(data));
        assertTrue(KingdomStoryProgress.record(data,TOWN,KingdomStoryProgress.Deed.MARKET_TRADE,"receipt:1",true));
        data.setStoryChapterProgress("living_kingdoms",3);
        assertTrue(KingdomStoryProgress.bind(data,OTHER,true));
        assertFalse(KingdomStoryProgress.has(data,KingdomStoryProgress.Deed.MARKET_TRADE));
        assertEquals(3,data.getStoryChapterProgress("living_kingdoms"),"Claimed chapters survive target recovery");
    }
    @Test void realServiceReceiptsMustMatchTheBoundVillageAndCannotCreateProgressFromAnAttempt() {
        var data = new PlayerQuestData(); KingdomStoryProgress.bind(data,TOWN,false);
        var deed=KingdomStoryProgress.Deed.STORE_DELIVERY;
        assertFalse(KingdomStoryProgress.record(data,OTHER,deed,"wrong-town",true));
        assertFalse(KingdomStoryProgress.record(data,TOWN,deed,"attempt",false));
        assertFalse(KingdomStoryProgress.record(data,TOWN,deed,"x".repeat(129),true));
        assertFalse(KingdomStoryProgress.has(data,deed));
        assertTrue(KingdomStoryProgress.record(data,TOWN,deed,"delivery:1",true));
        assertFalse(KingdomStoryProgress.record(data,TOWN,deed,"delivery:1",true));
        assertTrue(KingdomStoryProgress.has(data,deed));
        assertFalse(data.hasMilestoneFlag("kingdoms.dorfcheck.earned"));
    }
    @Test void earnedBuildingsInOneVillageCountAfterReloadAndDamageButNeighboursCannotSupplyMissingRoles() {
        var state=new LivingKingdomsState(); var data=new PlayerQuestData(); KingdomStoryProgress.bind(data,TOWN,false);
        state.registerCustom(TOWN,OWNER,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        state.registerCustom(OTHER,OWNER,VillageKey.overworld(40,0),new BlockPos(40,64,0),"Other").orElseThrow();
        bind(state,TOWN,BuildingScanner.Role.COUNCIL,0); bind(state,TOWN,BuildingScanner.Role.INN,3);
        bind(state,TOWN,BuildingScanner.Role.MARKET,6); bind(state,OTHER,BuildingScanner.Role.STOREHOUSE,40);
        assertTrue(KingdomStoryProgress.complete(data,state,0));
        assertFalse(KingdomStoryProgress.complete(data,state,1));
        bind(state,TOWN,BuildingScanner.Role.STOREHOUSE,9);
        var inn=state.anchors().official(TOWN,BuildingScanner.Role.INN).orElseThrow();
        state.anchors().detach(OWNER,inn.id(),inn.revision());
        state=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(KingdomStoryProgress.complete(data,state,1),"Earned construction is not a recurring online gate");
        assertFalse(KingdomStoryProgress.complete(data,state,2));
        for(var deed: new KingdomStoryProgress.Deed[]{KingdomStoryProgress.Deed.MEET_LEADER,KingdomStoryProgress.Deed.MEET_INNKEEPER,KingdomStoryProgress.Deed.MEET_STOREKEEPER})
            assertTrue(KingdomStoryProgress.record(data,TOWN,deed,deed.name(),true));
        assertTrue(KingdomStoryProgress.complete(data,state,2));
        assertFalse(KingdomStoryProgress.complete(data,state,3));
    }
    @Test void oldChapterProgressRestoresRecipeRightsWithoutItemPossessionOrInventingFinalReward() {
        var data=new PlayerQuestData(); data.setStoryChapterProgress("living_kingdoms",1);
        KingdomStoryProgress.restoreUnlocks(data);
        assertTrue(data.hasMilestoneFlag(KingdomBuildingService.COUNCIL_UNLOCK));
        assertTrue(data.hasMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK));
        assertFalse(data.hasMilestoneFlag("kingdoms.dorfcheck.earned"));
    }
    @Test void earnedVillageConstructionCanPrepareOneExistingRouteWithoutCompletingOrReplacingAnOldStory() {
        var data=new PlayerQuestData(); data.setStoryChapterProgress("living_kingdoms",1);
        assertFalse(KingdomStoryProgress.hasLocalRoutePermit(data));
        data.setStoryChapterProgress("living_kingdoms",2);
        assertTrue(KingdomStoryProgress.hasLocalRoutePermit(data));
        assertTrue(data.getStoryCompleted().isEmpty(),"No historical Market Charter or Concord completion is invented");
        assertFalse(data.hasMilestoneFlag("kingdoms.dorfcheck.earned"));
    }
    private static void bind(LivingKingdomsState state,UUID town,BuildingScanner.Role role,int x) {
        var pos=new BlockPos(x,64,0);
        var report=role==BuildingScanner.Role.MARKET
                ? new BuildingScanner.Report(role,BuildingScanner.Status.VALID,Map.of(),Map.of(BuildingScanner.Feature.LIGHT,Set.of(pos.above()),
                    BuildingScanner.Feature.COUNTER,Set.of(pos.north(2)),BuildingScanner.Feature.STALL,Set.of(pos.north(2)),
                    BuildingScanner.Feature.CONTAINER,Set.of(pos.north(2).above())),Set.of(pos.north()))
                : new BuildingScanner.Report(role,BuildingScanner.Status.VALID,Map.of(),Map.of(BuildingScanner.Feature.LIGHT,Set.of(pos.above())),Set.of());
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(OWNER,town,0,UUID.randomUUID(),-1,pos,Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
        if(role==BuildingScanner.Role.MARKET) {
            var a=state.anchors().official(town,role).orElseThrow();
            assertEquals(KingdomMarketPlaces.Result.APPLIED,state.marketPlaces().assign(OWNER,town,0,a.id(),0,0,0,pos.north(),pos.north(2),pos.north(2).above(),0));
        }
    }
}
