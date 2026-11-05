package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageNeed;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class KingdomCrisisTest {
    private record Fixture(LivingKingdomsState state,UUID actor,UUID host,UUID other) {}
    private static Fixture fixture() {
        var state=new LivingKingdomsState(); var actor=UUID.randomUUID(); var host=UUID.randomUUID(); var other=UUID.randomUUID();
        state.registerCustom(host,actor,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        state.registerCustom(other,actor,VillageKey.overworld(100,0),new BlockPos(100,64,0),"Partner").orElseThrow();
        var offer=state.diplomacy().offer(actor,host,other,0,0,KingdomDiplomacy.Type.SUPPLY,-1,KingdomDiplomacy.Terms.NEEDS_FIRST).orElseThrow();
        state.diplomacy().accept(actor,offer.id(),offer.revision()).orElseThrow();
        return new Fixture(state,actor,host,other);
    }
    private static KingdomCrisis.View start(Fixture f) {
        return f.state.crises().start(f.actor,f.host,f.other,VillageNeed.GRANARY_PANTRY,3,1,true).orElseThrow();
    }
    @Test void oneRealCaseRequiresMandateAndSignedPairAndDoesNotRepeatItsSupplyCycle() {
        var f=fixture(); var c=f.state.crises();
        assertTrue(c.start(UUID.randomUUID(),f.host,f.other,VillageNeed.GRANARY_PANTRY,3,1,false).isEmpty());
        var first=start(f);
        assertTrue(c.start(f.actor,f.host,f.other,VillageNeed.GRANARY_PANTRY,4,1,false).isEmpty());
        assertTrue(c.choose(f.actor,first.id(),first.revision(),KingdomCrisis.Choice.DEFER));
        assertTrue(c.start(f.actor,f.host,f.other,VillageNeed.GRANARY_PANTRY,3,1,false).isEmpty());
        assertEquals(KingdomDiplomacy.Status.SIGNED,f.state.diplomacy().between(f.host,f.other,KingdomDiplomacy.Type.SUPPLY).orElseThrow().status());
    }
    @Test void offlineCombatAndOversizedTimeStepsNeverAdvanceADeadlineOrCreateARefusal() {
        var f=fixture(); var first=start(f); var c=f.state.crises();
        c.advance(f.actor,20,false); c.advance(f.actor,1000000,true);
        assertEquals(first,c.current(f.actor).orElseThrow());
        c.advance(f.actor,20,true); assertEquals(first.remaining()-20,c.current(f.actor).orElseThrow().remaining());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        assertEquals(c.current(f.actor),restored.crises().current(f.actor));
        for(int i=0;i<1201;i++) restored.crises().advance(f.actor,20,true);
        assertEquals(KingdomCrisis.Phase.LAPSED,restored.crises().current(f.actor).orElseThrow().phase());
        assertEquals(KingdomDiplomacy.Status.SIGNED,restored.diplomacy().between(f.host,f.other,KingdomDiplomacy.Type.SUPPLY).orElseThrow().status());
    }
    @Test void explicitLocalRefusalPausesOnlyThisSupplyAndPersonalApologyNeedsTheActualOtherSpeaker() {
        var f=fixture(); var first=start(f); var c=f.state.crises();
        assertFalse(c.choose(UUID.randomUUID(),first.id(),first.revision(),KingdomCrisis.Choice.KEEP_HOME));
        assertTrue(c.choose(f.actor,first.id(),first.revision(),KingdomCrisis.Choice.KEEP_HOME));
        var paused=f.state.diplomacy().between(f.host,f.other,KingdomDiplomacy.Type.SUPPLY).orElseThrow();
        assertEquals(KingdomDiplomacy.Status.PAUSED,paused.status());
        var now=c.current(f.actor).orElseThrow();
        assertFalse(c.choose(f.actor,now.id(),now.revision(),KingdomCrisis.Choice.APOLOGIZE));
        c.hear(f.actor,f.host); now=c.current(f.actor).orElseThrow();
        assertFalse(c.choose(f.actor,now.id(),now.revision(),KingdomCrisis.Choice.APOLOGIZE));
        c.hear(f.actor,f.other); now=c.current(f.actor).orElseThrow();
        assertTrue(c.choose(f.actor,now.id(),now.revision(),KingdomCrisis.Choice.APOLOGIZE));
        assertFalse(c.ready(f.actor,false));
        assertFalse(c.ready(f.actor,true),"A click cannot replace listening to both after the apology");
        c.hear(f.actor,f.host); c.hear(f.actor,f.other); assertTrue(c.ready(f.actor,true));
        now=c.current(f.actor).orElseThrow(); assertTrue(c.choose(f.actor,now.id(),now.revision(),KingdomCrisis.Choice.REOPEN));
        assertEquals(KingdomDiplomacy.Status.PROPOSED,f.state.diplomacy().between(f.host,f.other,KingdomDiplomacy.Type.SUPPLY).orElseThrow().status());
        assertFalse(c.choose(f.actor,now.id(),now.revision(),KingdomCrisis.Choice.REOPEN));
    }
    @Test void aidNeedsTwoRealConversationsAndConsumedMatchingFreightAndIsSharedProjectEvidence() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var f=fixture(); var first=start(f); var c=f.state.crises();
        assertTrue(c.choose(f.actor,first.id(),first.revision(),KingdomCrisis.Choice.SUPPORT));
        c.hear(f.actor,f.host); c.hear(f.actor,f.other);
        assertFalse(c.delivered(f.actor,f.host,f.other,7,net.minecraft.world.item.Items.BREAD));
        assertTrue(f.state.relations().delivered(f.host,f.other,f.actor,UUID.randomUUID(),7));
        assertFalse(c.delivered(f.actor,f.other,f.host,7,net.minecraft.world.item.Items.BREAD));
        assertFalse(c.delivered(f.actor,f.host,f.other,7,net.minecraft.world.item.Items.COAL));
        assertTrue(c.delivered(f.actor,f.host,f.other,7,net.minecraft.world.item.Items.BREAD));
        assertEquals(KingdomCrisis.Phase.RESOLVED,c.current(f.actor).orElseThrow().phase());
        assertTrue(f.state.relations().hasAid(f.host)); assertTrue(f.state.relations().hasAid(f.other));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        assertTrue(restored.relations().hasAid(f.host));
        assertFalse(restored.crises().delivered(f.actor,f.host,f.other,7,net.minecraft.world.item.Items.BREAD));
    }
    @Test void anImprovedRealSupplyReportClosesTheCaseWithoutInventingPersonalAid() {
        var f=fixture(); start(f); assertTrue(f.state.crises().settleNaturally(f.actor));
        assertEquals(KingdomCrisis.Phase.RESOLVED,f.state.crises().current(f.actor).orElseThrow().phase());
        assertFalse(f.state.relations().hasAid(f.host)); assertFalse(f.state.crises().settleNaturally(f.actor));
    }
    @Test void pragmaticRepairCanUseFreshMatchingFreightWhenThePinnedReportBelongsToAnotherPlayer() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var f=fixture(); var c=f.state.crises(); var connection=UUID.randomUUID();
        f.state.relations().delivered(f.host,f.other,f.actor,connection,7);
        var first=start(f); c.choose(f.actor,first.id(),first.revision(),KingdomCrisis.Choice.KEEP_HOME); c.hear(f.actor,f.other);
        var now=c.current(f.actor).orElseThrow(); c.choose(f.actor,now.id(),now.revision(),KingdomCrisis.Choice.APOLOGIZE);
        c.hear(f.actor,f.host); c.hear(f.actor,f.other);
        assertFalse(c.delivered(f.actor,f.host,f.other,7,net.minecraft.world.item.Items.BREAD),"Pre-apology cargo is not a new repair");
        f.state.relations().delivered(f.host,f.other,f.actor,connection,8);
        assertTrue(c.delivered(f.actor,f.host,f.other,8,net.minecraft.world.item.Items.BREAD));
        assertEquals(KingdomCrisis.Phase.READY,c.current(f.actor).orElseThrow().phase());
        assertFalse(f.state.relations().hasAid(f.host),"Reconciliation does not manufacture the separate promised-aid event");
    }
    @Test void unknownAndMalformedCrisisSavesAreRetainedReadOnly() {
        var f=fixture(); start(f); var saved=LivingKingdomsState.toNbt(f.state);
        var crises=(net.minecraft.nbt.CompoundTag)saved.get("crises"); crises.putString("futureField","keep");
        var restored=LivingKingdomsState.fromNbt(saved); assertFalse(restored.crises().readOnly());
        assertEquals("keep",((net.minecraft.nbt.CompoundTag)LivingKingdomsState.toNbt(restored).get("crises")).getStringOr("futureField",""));
        crises.putInt("schema",99); restored=LivingKingdomsState.fromNbt(saved);
        assertTrue(restored.crises().readOnly()); assertEquals(crises,LivingKingdomsState.toNbt(restored).get("crises"));
        crises.putInt("schema",1); var actor=(net.minecraft.nbt.CompoundTag)((net.minecraft.nbt.ListTag)crises.get("actors")).get(0);
        ((net.minecraft.nbt.CompoundTag)actor.get("current")).putInt("remaining",-1);
        restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.crises().readOnly());
        assertEquals(crises,LivingKingdomsState.toNbt(restored).get("crises"));
    }
}
