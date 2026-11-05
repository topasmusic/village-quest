package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Pure shared save transitions and real registry body tickets, without starting a world. */
final class KingdomMeetingTest {
    private record Fixture(LivingKingdomsState state,UUID owner,UUID a,UUID b,KingdomDiplomacy.View deal) {}
    private static Fixture fixture() {
        var s=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        town(s,owner,a,0); town(s,owner,b,100);
        var offered=s.diplomacy().offer(owner,a,b,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        var deal=s.diplomacy().accept(owner,offered.id(),0).orElseThrow(); return new Fixture(s,owner,a,b,deal);
    }
    @Test void virtualVisitPreservesLogicalIdentityBlocksHomeAndNeedsActualBodiesToSign() {
        var f=fixture(); var s=f.state; var d=s.diplomacy(); var guest=s.people().ensure(f.b,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var invited=d.invite(f.owner,f.deal.id(),f.deal.revision(),f.a,0).orElseThrow();
        var visitor=s.people().person(guest.id()).orElseThrow();
        assertEquals(guest.appearanceSeed(),visitor.appearanceSeed()); assertEquals(guest.name(),visitor.name());
        assertEquals(f.a,visitor.visit()); assertNull(visitor.body());
        assertTrue(s.people().reserve(visitor.id(),visitor.revision(),UUID.randomUUID(),new BlockPos(100,64,-1),0).isEmpty());
        assertFalse(d.finishMeeting(f.owner,invited.id(),invited.revision(),f.a,UUID.randomUUID(),UUID.randomUUID(),0));
        var local=s.people().ensure(f.a,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var lt=s.people().reserve(local.id(),local.revision(),UUID.randomUUID(),new BlockPos(0,64,-1),0).orElseThrow(); s.people().commit(lt);
        var vt=s.people().reserve(visitor.id(),visitor.revision(),UUID.randomUUID(),new BlockPos(1,64,-1),0).orElseThrow(); s.people().commit(vt);
        assertTrue(d.finishMeeting(f.owner,invited.id(),invited.revision(),f.a,lt.body(),vt.body(),0));
        assertFalse(s.people().available(vt.person(),vt.generation(),vt.body()));
        var signed=d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow(); assertTrue(d.met(signed));
        assertEquals(KingdomDiplomacy.Status.SIGNED,signed.status()); assertTrue(s.people().visitors(f.a).isEmpty());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s));
        assertTrue(restored.diplomacy().met(restored.diplomacy().between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow()));
        assertTrue(KingdomRecognitionService.facts(restored,new de.quest.data.PlayerQuestData(),f.a,0).contains(KingdomRecognition.Evidence.MEETING));
    }
    @Test void restartCancellationAndStaleOrForeignRequestsCannotCloneVisitorsOrSign() {
        var f=fixture(); var d=f.state.diplomacy();
        assertTrue(d.invite(UUID.randomUUID(),f.deal.id(),f.deal.revision(),f.a,0).isEmpty());
        var invited=d.invite(f.owner,f.deal.id(),f.deal.revision(),f.a,0).orElseThrow();
        assertTrue(d.invite(f.owner,invited.id(),invited.revision(),f.a,0).isEmpty());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state)); d=restored.diplomacy();
        assertEquals(f.a,d.meetingHost(d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow()).orElseThrow());
        assertFalse(d.cancelMeeting(f.owner,invited.id(),f.deal.revision(),f.a));
        assertFalse(d.cancelMeeting(UUID.randomUUID(),invited.id(),invited.revision(),f.a));
        assertTrue(d.cancelMeeting(f.owner,invited.id(),invited.revision(),f.a));
        assertTrue(restored.people().visitors(f.a).isEmpty());
        assertEquals(KingdomDiplomacy.Status.PREPARED,d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow().status());
    }
    @Test void departureInvalidatesAnUnloadedHomeBodyBeforeVisitorMaterialization() {
        var f=fixture(); var s=f.state; var person=s.people().ensure(f.b,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var old=s.people().reserve(person.id(),person.revision(),UUID.randomUUID(),new BlockPos(100,64,-1),0).orElseThrow(); s.people().commit(old);
        s.people().unloaded(old.person(),old.generation(),old.body(),new BlockPos(100,64,-1));
        s.diplomacy().invite(f.owner,f.deal.id(),f.deal.revision(),f.a,0).orElseThrow();
        assertFalse(s.people().markLoaded(old.person(),old.generation(),old.body()));
        var visitor=s.people().person(person.id()).orElseThrow();
        var current=s.people().reserve(visitor.id(),visitor.revision(),UUID.randomUUID(),new BlockPos(1,64,-1),0).orElseThrow();
        assertTrue(current.generation()>old.generation()); assertTrue(s.people().commit(current));
        assertEquals(KingdomPersonRegistry.Presence.VISITING,s.people().person(person.id()).orElseThrow().presence());
    }
    @Test void lostVisitBodiesNeedLoadedGraceAndARepairedCouncilCannotSignAForgedSavedMeeting() {
        var f=fixture(); var s=f.state;
        var invited=s.diplomacy().invite(f.owner,f.deal.id(),f.deal.revision(),f.a,0).orElseThrow();
        var visitor=s.people().visitors(f.a).getFirst();
        var ticket=s.people().reserve(visitor.id(),visitor.revision(),UUID.randomUUID(),new BlockPos(1,64,-1),0).orElseThrow(); s.people().commit(ticket);
        s.people().unloaded(ticket.person(),ticket.generation(),ticket.body(),new BlockPos(1,64,-1));
        s.people().observeAbsent(ticket.person(),5000,false);
        assertEquals(ticket.body(),s.people().person(ticket.person()).orElseThrow().body());
        for(long now=5000;now<=5600;now+=100) s.people().observeAbsent(ticket.person(),now,true);
        assertNull(s.people().person(ticket.person()).orElseThrow().body());
        assertEquals(KingdomPersonRegistry.Presence.VISITING,s.people().person(ticket.person()).orElseThrow().presence());
        assertFalse(s.people().markLoaded(ticket.person(),ticket.generation(),ticket.body()));
        assertTrue(s.diplomacy().cancelMeeting(f.owner,invited.id(),invited.revision(),f.a));
        var saved=LivingKingdomsState.toNbt(s);
        var root=(net.minecraft.nbt.CompoundTag)saved.get("diplomacy");
        var row=(net.minecraft.nbt.CompoundTag)((net.minecraft.nbt.ListTag)root.get("contracts")).getFirst();
        row.putString("meetingHost",UUID.randomUUID().toString()); row.putString("meetingActor",f.owner.toString());
        var restored=LivingKingdomsState.fromNbt(saved);
        assertTrue(restored.diplomacy().readOnly()); assertEquals(root,LivingKingdomsState.toNbt(restored).get("diplomacy"));
    }
    @Test void pausedVisitsReleaseTheBodyBudgetAndOnlyExplicitDetachmentCancelsAnInvitation() {
        var f=fixture(); var s=f.state;
        var invited=s.diplomacy().invite(f.owner,f.deal.id(),f.deal.revision(),f.a,0).orElseThrow();
        var visitor=s.people().visitors(f.a).getFirst();
        var ticket=s.people().reserve(visitor.id(),visitor.revision(),UUID.randomUUID(),new BlockPos(1,64,-1),0).orElseThrow(); s.people().commit(ticket);
        assertTrue(s.people().pauseVisit(ticket.person(),ticket.generation(),ticket.body()));
        assertEquals(f.a,s.people().person(visitor.id()).orElseThrow().visit()); assertNull(s.people().person(visitor.id()).orElseThrow().body());
        assertFalse(s.people().markLoaded(ticket.person(),ticket.generation(),ticket.body()));
        assertTrue(s.diplomacy().reconcileMeetings().isEmpty());
        var anchor=s.anchors().official(f.a,BuildingScanner.Role.COUNCIL).orElseThrow();
        s.anchors().detach(f.owner,anchor.id(),anchor.revision());
        s.diplomacy().reconcileMeetings();
        assertTrue(s.people().visitors(f.a).isEmpty());
        assertTrue(s.diplomacy().meetingHost(s.diplomacy().between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow()).isEmpty());
    }
    @Test void aPersonalRepairMeetingSurvivesRestartButNeverRestoresTheBrokenAlliance() {
        var f=fixture(); var s=f.state;
        // Signed meeting proof is represented in persisted contract data; no world starts.
        var saved=LivingKingdomsState.toNbt(s); var root=(net.minecraft.nbt.CompoundTag)saved.get("diplomacy");
        var row=(net.minecraft.nbt.CompoundTag)((net.minecraft.nbt.ListTag)root.get("contracts")).getFirst();
        row.putString("status","SIGNED"); row.putBoolean("met",true); s=LivingKingdomsState.fromNbt(saved);
        refreshCouncil(s,f.a); refreshCouncil(s,f.b);
        var cause=UUID.randomUUID(); var d=s.diplomacy();
        assertTrue(d.pause(f.owner,f.a,f.b,cause,KingdomDiplomacy.Type.FRIENDSHIP));
        assertFalse(d.met(d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow()));
        assertTrue(d.prepareRepairMeeting(f.owner,f.a,f.b));
        var deal=d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow();
        var invited=d.invite(f.owner,deal.id(),deal.revision(),f.a,0).orElseThrow();
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); d=s.diplomacy(); assertFalse(d.readOnly());
        refreshCouncil(s,f.a); refreshCouncil(s,f.b);
        assertEquals(KingdomDiplomacy.Status.PAUSED,d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow().status());
        var local=s.people().ensure(f.a,KingdomPersonRegistry.Role.LEADER,0).orElseThrow(); var visitor=s.people().visitors(f.a).getFirst();
        var lt=s.people().reserve(local.id(),local.revision(),UUID.randomUUID(),new BlockPos(0,64,-1),0).orElseThrow(); s.people().commit(lt);
        var vt=s.people().reserve(visitor.id(),visitor.revision(),UUID.randomUUID(),new BlockPos(1,64,-1),0).orElseThrow(); s.people().commit(vt);
        assertTrue(d.finishMeeting(f.owner,invited.id(),invited.revision(),f.a,lt.body(),vt.body(),0));
        deal=d.between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow(); assertTrue(d.met(deal));
        assertEquals(KingdomDiplomacy.Status.PAUSED,deal.status());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); assertFalse(restored.diplomacy().readOnly());
        assertTrue(restored.diplomacy().reopen(f.owner,f.a,f.b,cause,KingdomDiplomacy.Type.FRIENDSHIP));
        assertEquals(KingdomDiplomacy.Status.PROPOSED,restored.diplomacy().between(f.a,f.b,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow().status());
    }
    private static void refreshCouncil(LivingKingdomsState s,UUID village) {
        var a=s.anchors().official(village,BuildingScanner.Role.COUNCIL).orElseThrow(); var pos=a.position();
        s.anchors().observe(a.id(),a.revision(),new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,
                Map.of(),Map.of(),Set.of(pos.north(),pos.north().east(),pos.north().west(),pos.north(2))),0);
    }
    private static void town(LivingKingdomsState s,UUID owner,UUID id,int x) {
        var pos=new BlockPos(x,64,0); s.registerCustom(id,owner,VillageKey.overworld(x,0),pos,"Town "+x).orElseThrow();
        s.anchors().bind(owner,id,0,UUID.randomUUID(),-1,pos,Direction.NORTH,BuildingScanner.Range.BASIC,
                new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(pos.north(),pos.north().east(),pos.north().west(),pos.north(2))),0,0);
    }
}
