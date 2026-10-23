package de.quest.village;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import java.util.UUID;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SharedFreightNeedTest {
    @BeforeAll static void items() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void foreignFreightChangesOnlyPinnedPhysicalNeedAndDeduplicatesAcrossRestart() {
        var s=new LivingVillageNetworkState(); var home=UUID.randomUUID(); var carrier=UUID.randomUUID();
        var v=s.ensureVillage(home,0,100,200,VillageBondType.GRANARY); s.ensureVillage(carrier,3,100,200,VillageBondType.GRANARY);
        s.adminSetVillageSupport(home,0,25);
        var before=s.snapshot(home,0).orElseThrow(); var homeNetwork=s.network(home); var carrierNetwork=s.network(carrier); var carrierBefore=s.snapshot(carrier,3);
        var matching=before.need()==VillageNeed.GRANARY_PANTRY?Items.BREAD:Items.WHEAT;
        assertTrue(s.sharedFreight(home,0,100,200,VillageBondType.GRANARY,carrier,7,matching,10));
        var after=s.snapshot(home,0).orElseThrow(); assertEquals(45,after.support());
        assertEquals(before.routeArrivals(),after.routeArrivals()); assertEquals(before.energyProgress(),after.energyProgress());
        assertEquals(homeNetwork,s.network(home)); assertEquals(carrierNetwork,s.network(carrier)); assertEquals(carrierBefore,s.snapshot(carrier,3));
        assertFalse(s.sharedFreight(home,0,100,200,VillageBondType.GRANARY,carrier,7,matching,10));
        s=LivingVillageNetworkState.fromNbt(LivingVillageNetworkState.toNbt(s));
        assertFalse(s.sharedFreight(home,0,100,200,VillageBondType.GRANARY,carrier,7,matching,11));
        assertEquals(after,s.snapshot(home,0).orElseThrow());
        assertTrue(s.sharedFreight(home,0,100,200,VillageBondType.GRANARY,carrier,8,matching,12));
        assertEquals(65,s.snapshot(home,0).orElseThrow().support()); assertEquals(homeNetwork,s.network(home));
    }
    @Test void matchingGoodsAdvanceTheSameRealCycleWithoutAnArrivalReward() {
        var s=new LivingVillageNetworkState(); var home=UUID.randomUUID(); var carrier=UUID.randomUUID();
        var v=s.ensureVillage(home,0,100,200,VillageBondType.GRANARY); s.adminSetVillageSupport(home,0,90);
        var renown=s.network(home); var item=v.need()==VillageNeed.GRANARY_PANTRY?Items.BREAD:Items.WHEAT;
        assertTrue(s.sharedFreight(home,0,100,200,VillageBondType.GRANARY,carrier,1,item,10));
        var after=s.snapshot(home,0).orElseThrow(); assertEquals(55,after.support()); assertEquals(1,after.cyclesCompleted());
        assertEquals(VillageNeed.next(v.type(),v.need()),after.need()); assertEquals(0,after.routeArrivals()); assertEquals(0,after.energyProgress());
        assertEquals(renown,s.network(home));
    }
    @Test void sameOwnerWrongIdentityUnknownRowAndFutureInputRemainNeutral() {
        var s=new LivingVillageNetworkState(); var owner=UUID.randomUUID(); var carrier=UUID.randomUUID(); s.ensureVillage(owner,0,100,200,VillageBondType.GRANARY);
        var before=s.snapshot(owner,0);
        assertFalse(s.sharedFreight(owner,0,100,200,VillageBondType.GRANARY,owner,1,Items.WHEAT,0));
        assertFalse(s.sharedFreight(owner,0,101,200,VillageBondType.GRANARY,carrier,1,Items.WHEAT,0));
        assertFalse(s.sharedFreight(owner,0,100,200,VillageBondType.FORGE,carrier,1,Items.WHEAT,0));
        assertFalse(s.sharedFreight(owner,1,100,200,VillageBondType.GRANARY,carrier,1,Items.WHEAT,0));
        assertEquals(before,s.snapshot(owner,0));
        var saved=LivingVillageNetworkState.toNbt(s); saved.putInt("schemaVersion",99); s=LivingVillageNetworkState.fromNbt(saved);
        assertFalse(s.sharedFreight(owner,0,100,200,VillageBondType.GRANARY,carrier,1,Items.WHEAT,0));
    }
    @Test void malformedReplayLedgerIsRetainedAndDisablesPhysicalMarketDemandMutation() {
        var s=new LivingVillageNetworkState(); var owner=UUID.randomUUID(); s.ensureVillage(owner,0,100,200,VillageBondType.GRANARY);
        var saved=LivingVillageNetworkState.toNbt(s); var row=(net.minecraft.nbt.CompoundTag)((net.minecraft.nbt.ListTag)saved.get("villages")).getFirst();
        var bad=new net.minecraft.nbt.CompoundTag(); bad.putString("not-a-uuid","bad"); row.put("sharedFreight",bad);
        s=LivingVillageNetworkState.fromNbt(saved); assertTrue(s.marketSnapshot(owner,0).isEmpty());
        assertFalse(s.sharedFreight(owner,0,100,200,VillageBondType.GRANARY,UUID.randomUUID(),1,Items.WHEAT,0));
        assertEquals(bad,((net.minecraft.nbt.CompoundTag)((net.minecraft.nbt.ListTag)LivingVillageNetworkState.toNbt(s).get("villages")).getFirst()).get("sharedFreight"));
    }
}
