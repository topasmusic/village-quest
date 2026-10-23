package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import de.quest.village.VillageLifeState;
import org.junit.jupiter.api.Test;

final class KingdomVillageSummaryTest {
    @Test void mapReportsDoNotDiscoverOtherVillagesOrRefreshSavedEvidence() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var actor=UUID.randomUUID();
        var key=VillageLifeState.VillageKey.overworld(10,20); var home=UUID.randomUUID();
        state.registerCustom(home,owner,key,new BlockPos(10,64,20),"Home").orElseThrow();
        var data=new de.quest.data.PlayerQuestData();
        assertTrue(KingdomVillageService.mapDetails(state,data,actor,key,VillageLifeState.Status.ACTIVE,20).isEmpty());
        de.quest.shrine.VillageContactService.establish(data,state.village(home).orElseThrow(),de.quest.shrine.VillageBondType.FORGE);
        var before=LivingKingdomsState.toNbt(state); var dataBefore=java.util.Map.copyOf(data.getTradeRouteStringState());
        var lines=KingdomVillageService.mapDetails(state,data,actor,key,VillageLifeState.Status.ACTIVE,20);
        assertEquals(8,lines.size());
        assertTrue(lines.getLast().getString().contains("village_no_agreements"));
        assertEquals(before,LivingKingdomsState.toNbt(state)); assertEquals(dataBefore,data.getTradeRouteStringState());
    }
    @Test void mapAgreementsShowOnlyDirectContractsWithAlreadyKnownPartners() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var home=UUID.randomUUID();
        var key=VillageLifeState.VillageKey.overworld(10,20);
        state.registerCustom(home,owner,key,new BlockPos(10,64,20),"Home").orElseThrow();
        var data=new de.quest.data.PlayerQuestData();
        for(int i=0;i<2;i++) {
            var partner=UUID.randomUUID();
            state.registerCustom(partner,owner,VillageLifeState.VillageKey.overworld(100+i*40,20),new BlockPos(100+i*40,64,20),i==0?"Known":"Hidden").orElseThrow();
            state.diplomacy().offer(owner,home,partner,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
            if(i==0) de.quest.shrine.VillageContactService.establish(data,state.village(partner).orElseThrow(),de.quest.shrine.VillageBondType.FORGE);
        }
        var before=LivingKingdomsState.toNbt(state);
        var lines=KingdomVillageService.mapDetails(state,data,owner,key,VillageLifeState.Status.ACTIVE,20);
        var reports=lines.stream().filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith("village_agreement")).toList();
        assertEquals(1,reports.size());
        var args=((net.minecraft.network.chat.contents.TranslatableContents)reports.getFirst().getContents()).getArgs();
        assertEquals("Known",args[0]);
        assertEquals(before,LivingKingdomsState.toNbt(state));
    }
    @Test void savedSummaryIsBoundedReadOnlyAndUsesCanonicalVillageAfterReload() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var id=UUID.randomUUID();
        var key=VillageLifeState.VillageKey.overworld(10,20);
        state.registerCustom(id,owner,key,new BlockPos(10,64,20),"First").orElseThrow();
        assertTrue(state.observePopulation(id,64,true,100));
        state=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        var before=LivingKingdomsState.toNbt(state);
        var lines=KingdomVillageService.lastKnown(state,VillageLifeState.Status.RECOVERING,key,1300);
        assertEquals(7,lines.size());
        assertTrue(lines.get(0).getString().contains("last_known"));
        assertTrue(lines.get(1).getString().contains("population_at_least"));
        assertTrue(lines.get(6).getString().contains("rebuild_hint"));
        assertEquals(before,LivingKingdomsState.toNbt(state),"Journal must not refresh evidence or change saved state");
        assertEquals(id,state.byLegacy(key).orElseThrow().id());
        assertTrue(KingdomVillageService.lastKnown(state,VillageLifeState.Status.ACTIVE,VillageLifeState.VillageKey.overworld(11,21),1300).isEmpty());
    }
    @Test void missingAndFuturePopulationEvidenceRemainUnknown() {
        var state=new LivingKingdomsState(); var id=UUID.randomUUID(); var key=VillageLifeState.VillageKey.overworld(0,0);
        state.registerCustom(id,UUID.randomUUID(),key,new BlockPos(0,64,0),"Home").orElseThrow();
        assertTrue(KingdomVillageService.lastKnown(state,VillageLifeState.Status.ACTIVE,key,10).get(1).getString().contains("population_unknown"));
        state.observePopulation(id,3,true,100);
        assertTrue(KingdomVillageService.lastKnown(state,VillageLifeState.Status.ACTIVE,key,10).get(1).getString().contains("population_unknown"));
    }
    @Test void geometryAloneCannotReportReadyAfterAnExclusiveStationConflict() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var id=UUID.randomUUID();
        state.registerCustom(id,owner,VillageLifeState.VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        var first=UUID.randomUUID(); var second=UUID.randomUUID();
        var report=new BuildingScanner.Report(BuildingScanner.Role.INN,BuildingScanner.Status.VALID,java.util.Map.of(),
                java.util.Map.of(BuildingScanner.Feature.BED,java.util.Set.of(new BlockPos(1,64,0))),java.util.Set.of());
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,id,0,first,-1,new BlockPos(0,64,0),
                net.minecraft.core.Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
        assertEquals(BuildingScanner.Status.VALID,KingdomBuildingService.villageCheckStatus(state,first,report,0));
        var council=new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,java.util.Map.of(),
                java.util.Map.of(BuildingScanner.Feature.TABLE,java.util.Set.of(new BlockPos(3,64,0))),java.util.Set.of());
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,id,0,second,-1,new BlockPos(4,64,0),
                net.minecraft.core.Direction.NORTH,BuildingScanner.Range.BASIC,council,0,0));
        var changed=new BuildingScanner.Report(BuildingScanner.Role.INN,BuildingScanner.Status.VALID,java.util.Map.of(),
                java.util.Map.of(BuildingScanner.Feature.BED,java.util.Set.of(new BlockPos(3,64,0))),java.util.Set.of());
        assertEquals(KingdomAnchorRegistry.Result.STATION_CONFLICT,state.anchors().observe(first,0,changed,1));
        assertEquals(BuildingScanner.Status.INVALID,KingdomBuildingService.villageCheckStatus(state,first,changed,1));
        assertEquals(BuildingScanner.Status.UNKNOWN,KingdomBuildingService.villageCheckStatus(state,first,null,1));
        state.anchors().removedPhysical(first);
        assertTrue(KingdomVillageService.lastKnown(state,VillageLifeState.Status.ACTIVE,VillageLifeState.VillageKey.overworld(0,0),1)
                .contains(net.minecraft.network.chat.Component.translatable("screen.village-quest.journal.kingdom.building",
                        net.minecraft.network.chat.Component.translatable("screen.village-quest.building.role.inn"),
                        net.minecraft.network.chat.Component.translatable("screen.village-quest.journal.kingdom.building.detached"))));
    }
}
