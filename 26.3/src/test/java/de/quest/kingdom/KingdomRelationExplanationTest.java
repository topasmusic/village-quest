package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import de.quest.village.VillageNeed;
import org.junit.jupiter.api.Test;

final class KingdomRelationExplanationTest {
    @Test void supplyReconciliationRemainsExplainableAfterLaterFreightWithoutInventingLoyalty() {
        var other=UUID.randomUUID(); var s=state(other); var r=s.relations();
        r.remember(home,other,UUID.randomUUID(),"reconciliation");
        r.delivered(home,other,actor,UUID.randomUUID(),1);
        var view=r.between(home,other).orElseThrow();
        assertTrue(view.reconciliationRemembered()); assertFalse(view.loyaltyBroken());
        var explanation=KingdomDiplomacyService.explain(s,actor,home,other);
        assertEquals("rebuilding",explanation.trend()); assertTrue(explanation.causes().contains("reconciliation"));
    }
    private final UUID home=KingdomMarketPlacesTest.TOWN, actor=KingdomMarketPlacesTest.OWNER;
    private LivingKingdomsState state(UUID other) {
        var s=KingdomMarketPlacesTest.state(0);
        s.registerCustom(other,actor,de.quest.village.VillageLifeState.VillageKey.overworld(100,0),
                new net.minecraft.core.BlockPos(100,64,0),"Other").orElseThrow();
        return s;
    }
    @Test void subsequentFreightDoesNotHideUnrepairedLoyaltyAndReadDoesNotMutateSave() {
        var other=UUID.randomUUID(); var s=state(other); var r=s.relations();
        r.remember(home,other,UUID.randomUUID(),"loyalty");
        r.delivered(home,other,actor,UUID.randomUUID(),1);
        var before=LivingKingdomsState.toNbt(s);
        var explanation=r.between(home,other).orElseThrow();
        assertEquals("delivery",explanation.cause());
        assertTrue(explanation.loyaltyBroken()); assertFalse(explanation.loyaltyRepaired());
        assertEquals(before,LivingKingdomsState.toNbt(s));
        var reload=LivingKingdomsState.fromNbt(before);
        assertEquals(explanation,reload.relations().between(other,home).orElseThrow());
    }
    @Test void onlyMatchingReconciliationRepairsCurrentLoyaltyMemory() {
        var other=UUID.randomUUID(); var r=state(other).relations(); var first=UUID.randomUUID();
        r.remember(home,other,first,"loyalty"); r.remember(home,other,first,"reconciliation");
        r.delivered(home,other,actor,UUID.randomUUID(),1);
        assertTrue(r.between(home,other).orElseThrow().loyaltyRepaired());
        r.remember(home,other,UUID.randomUUID(),"loyalty");
        assertFalse(r.between(home,other).orElseThrow().loyaltyRepaired(),"An older repair is not forgiveness of a new case");
    }
    @Test void actualAidSurvivesLaterFreightWithoutInventingThirdVillageRelations() {
        var other=UUID.randomUUID(); var r=state(other).relations();
        r.delivered(home,other,actor,UUID.randomUUID(),1); r.aid(home,other,actor,1);
        r.delivered(home,other,actor,UUID.randomUUID(),2);
        assertTrue(r.between(home,other).orElseThrow().aidRemembered());
        assertTrue(r.between(home,UUID.randomUUID()).isEmpty());
    }
    @Test void personalAttackSnapshotIsPrivateAndKeepsTheExactRepairNeedAfterReload() {
        var other=UUID.randomUUID(); var s=state(other); var r=s.relations();
        for(int i=0;i<3;i++) r.refusedAttack(actor,other,UUID.randomUUID());
        r.apologizeAttack(actor,other,VillageNeed.GRANARY_PANTRY);
        var before=LivingKingdomsState.toNbt(s); var reload=LivingKingdomsState.fromNbt(before);
        var personal=reload.relations().personal(actor,other);
        assertTrue(personal.distrust()); assertEquals("granary_pantry",personal.repairNeed());
        assertFalse(reload.relations().personal(UUID.randomUUID(),other).distrust());
        assertEquals("none",reload.relations().personal(actor,home).repairNeed());
        assertEquals(before,LivingKingdomsState.toNbt(reload));
    }
    @Test void fulfilledHelpHidesOldRepairInstructions() {
        var other=UUID.randomUUID(); var r=state(other).relations();
        for(int i=0;i<3;i++) r.refusedAttack(actor,other,UUID.randomUUID());
        r.apologizeAttack(actor,other,VillageNeed.GRANARY_PANTRY);
        assertTrue(r.helpedAttack(actor,other,UUID.randomUUID()));
        assertFalse(r.personal(actor,other).distrust());
        assertEquals("none",r.personal(actor,other).repairNeed());
    }
    @Test void futureRelationDataIsUnavailableAndPreservedRatherThanPresentedAsForgiveness() {
        var other=UUID.randomUUID(); var s=state(other); var tag=LivingKingdomsState.toNbt(s);
        var future=new net.minecraft.nbt.CompoundTag(); future.putInt("schema",999); future.putString("future","keep");
        tag.put("relations",future);
        var restored=LivingKingdomsState.fromNbt(tag);
        assertTrue(restored.relations().readOnly());
        assertFalse(restored.relations().personal(actor,other).available());
        assertTrue(restored.relations().between(home,other).isEmpty());
        assertEquals(tag,LivingKingdomsState.toNbt(restored));
    }
}
