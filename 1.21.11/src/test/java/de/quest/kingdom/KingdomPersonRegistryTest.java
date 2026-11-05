package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Logical presence/persistence only. No actual entities, levels, ticks or Minecraft simulations. */
final class KingdomPersonRegistryTest {
    private static final UUID OWNER=UUID.randomUUID(),HOME=UUID.randomUUID(),OTHER=UUID.randomUUID();
    private static final BlockPos ANCHOR=new BlockPos(0,64,0),STAND=ANCHOR.north();
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void onePersonPerVillageRoleAndPromotionPreserveIdentityFaceAndNameAcrossReload() {
        var state=state(); var registry=state.people();
        var leader=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        assertEquals(leader.id(),registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow().id());
        assertTrue(registry.promote(leader.id(),leader.revision()));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        var mayor=restored.people().person(leader.id()).orElseThrow();
        assertEquals(leader.id(),mayor.id()); assertEquals(leader.appearanceSeed(),mayor.appearanceSeed());
        assertEquals(leader.name(),mayor.name()); assertTrue(mayor.mayor());
        assertTrue(registry.ensure(HOME,KingdomPersonRegistry.Role.MARKET_TRADER,5).isEmpty());
    }
    @Test void bodyTicketsRequireFreshOwnedFloorEvidenceAndCannotMaterializeTwoCopies() {
        var state=state(); var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        assertTrue(registry.reserve(person.id(),person.revision(),UUID.randomUUID(),ANCHOR.east(30),0).isEmpty());
        var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow();
        assertTrue(registry.reserve(person.id(),registry.person(person.id()).orElseThrow().revision(),UUID.randomUUID(),STAND,0).isEmpty());
        assertTrue(registry.markLoaded(person.id(),ticket.generation(),ticket.body()));
        assertTrue(registry.commit(ticket));
        assertTrue(registry.available(person.id(),ticket.generation(),ticket.body()));
        assertFalse(registry.available(person.id(),ticket.generation(),UUID.randomUUID()));
    }
    @Test void unloadPreservesBodyIdentityAndIsNeitherDeathNorPermissionToRespawn() {
        var state=state(); var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(ticket); registry.markLoaded(person.id(),ticket.generation(),ticket.body());
        assertTrue(registry.unloaded(person.id(),ticket.generation(),ticket.body(),STAND));
        assertFalse(registry.available(person.id(),ticket.generation(),ticket.body()));
        assertTrue(registry.reserve(person.id(),registry.person(person.id()).orElseThrow().revision(),UUID.randomUUID(),STAND,0).isEmpty());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(restored.people().markLoaded(person.id(),ticket.generation(),ticket.body()));
        assertTrue(restored.people().available(person.id(),ticket.generation(),ticket.body()));
        assertEquals(0,restored.people().person(person.id()).orElseThrow().returnTicks());
    }
    @Test void deathReturnRequiresAnObservedActiveDayAndNeverCountsOfflineGaps() {
        var state=state(); var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(ticket);
        assertTrue(registry.died(person.id(),ticket.generation(),ticket.body()));
        for(long tick=0;tick<=12000;tick+=100) registry.observeActive(person.id(),tick,true);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state)); registry=restored.people();
        registry.observeActive(person.id(),100000,true); registry.observeActive(person.id(),200000,false);
        assertEquals(12000,registry.person(person.id()).orElseThrow().returnTicks());
        for(long tick=200000;tick<=212000;tick+=100) registry.observeActive(person.id(),tick,true);
        assertEquals(KingdomPersonRegistry.Presence.DORMANT,registry.person(person.id()).orElseThrow().presence());
        assertFalse(registry.markLoaded(person.id(),ticket.generation(),ticket.body()));
        assertEquals(person.appearanceSeed(),registry.person(person.id()).orElseThrow().appearanceSeed());
    }
    @Test void aVisitBlocksHomeCopiesAndCancellingAnUnloadedVisitInvalidatesTheOldBody() {
        var state=state(); state.registerCustom(OTHER,OWNER,VillageKey.overworld(40,0),new BlockPos(40,64,0),"Other").orElseThrow();
        var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(ticket); registry.markLoaded(person.id(),ticket.generation(),ticket.body());
        assertTrue(registry.beginVisit(person.id(),registry.person(person.id()).orElseThrow().revision(),OTHER));
        registry.unloaded(person.id(),ticket.generation(),ticket.body(),new BlockPos(40,64,0));
        assertEquals(KingdomPersonRegistry.Presence.VISITING,registry.person(person.id()).orElseThrow().presence());
        assertTrue(registry.reserve(person.id(),registry.person(person.id()).orElseThrow().revision(),UUID.randomUUID(),STAND,0).isEmpty());
        assertTrue(registry.endVisit(person.id(),registry.person(person.id()).orElseThrow().revision(),false));
        assertFalse(registry.markLoaded(person.id(),ticket.generation(),ticket.body()));
        assertTrue(registry.reserve(person.id(),registry.person(person.id()).orElseThrow().revision(),UUID.randomUUID(),STAND,0).isPresent());
    }
    @Test void opaqueFutureAndMalformedPersonRootsCannotSpawnOrEraseTheirData() {
        var future=new CompoundTag(); future.putInt("schema",99); future.putString("opaque","keep");
        for(var payload:java.util.List.of(future,StringTag.valueOf("future people data"))) {
            var saved=LivingKingdomsState.toNbt(state()); saved.put("people",payload.copy());
            var restored=LivingKingdomsState.fromNbt(saved);
            assertTrue(restored.people().readOnly()); assertTrue(restored.people().ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).isEmpty());
            assertEquals(payload,LivingKingdomsState.toNbt(restored).get("people"));
        }
    }
    @Test void missingBodyEvidenceNeedsLoadedGraceAndUnknownObservationsCannotInstantlyMintAnotherBody() {
        var state=state(); var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(ticket);
        registry.unloaded(person.id(),ticket.generation(),ticket.body(),STAND);
        registry.observeAbsent(person.id(),10000,true); registry.observeAbsent(person.id(),20000,false);
        assertEquals(ticket.body(),registry.person(person.id()).orElseThrow().body());
        for(long tick=20000;tick<=20600;tick+=100) registry.observeAbsent(person.id(),tick,true);
        assertNull(registry.person(person.id()).orElseThrow().body());
        assertFalse(registry.markLoaded(person.id(),ticket.generation(),ticket.body()));
    }
    @Test void bodyBudgetIsSharedAcrossVillagesAndFailedCreationReleasesOnlyItsOwnTicket() {
        var state=new LivingKingdomsState(); var registry=state.people(); KingdomPersonRegistry.Ticket first=null;
        for(int n=0;n<33;n++) {
            UUID town=UUID.randomUUID(); BlockPos pos=ANCHOR.east(n*32),stand=pos.north();
            state.registerCustom(town,OWNER,VillageKey.overworld(n*32,0),pos,"Town "+n).orElseThrow();
            state.anchors().bind(OWNER,town,0,UUID.randomUUID(),-1,pos,Direction.NORTH,BuildingScanner.Range.BASIC,
                    new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(stand)),0,0);
            var person=registry.ensure(town,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
            var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),stand,0);
            if(n<32) { assertTrue(ticket.isPresent()); if(first==null) first=ticket.orElseThrow(); }
            else { assertTrue(ticket.isEmpty()); assertTrue(registry.abort(first));
                assertTrue(registry.reserve(person.id(),person.revision(),UUID.randomUUID(),stand,0).isPresent()); }
        }
        assertEquals(32,registry.loadedBodies());
    }
    @Test void exhaustedRevisionCannotWrapDuringAnEntityLoadOrDeath() {
        var state=state(); var person=state.people().ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var ticket=state.people().reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); state.people().commit(ticket);
        var saved=LivingKingdomsState.toNbt(state); var records=saved.getCompoundOrEmpty("people").getListOrEmpty("records");
        records.getCompoundOrEmpty(0).putLong("revision",Long.MAX_VALUE);
        var restored=LivingKingdomsState.fromNbt(saved); var before=LivingKingdomsState.toNbt(restored);
        assertFalse(restored.people().markLoaded(person.id(),ticket.generation(),ticket.body()));
        assertFalse(restored.people().died(person.id(),ticket.generation(),ticket.body()));
        assertEquals(before,LivingKingdomsState.toNbt(restored));
    }

    @Test void controlledDismissIsNotDeathAndLateUnloadCannotReclaimTheNextGeneration() {
        var state=state(); var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var first=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(first);
        assertTrue(registry.dismiss(person.id(),first.generation(),first.body()));
        var current=registry.person(person.id()).orElseThrow(); assertEquals(KingdomPersonRegistry.Presence.DORMANT,current.presence());
        assertEquals(0,current.returnTicks()); assertEquals(0,registry.loadedBodies());
        var second=registry.reserve(person.id(),current.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(second);
        assertFalse(registry.unloaded(person.id(),first.generation(),first.body(),STAND));
        assertTrue(registry.available(person.id(),second.generation(),second.body()));
        assertEquals(first.generation()+1,second.generation());
    }
    @Test void arrivalIsAcknowledgedOncePerLogicalPersonAndSurvivesUnloadAndRebuilding() {
        var state=state(); var registry=state.people(); var person=registry.ensure(HOME,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var ticket=registry.reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); registry.commit(ticket);
        assertTrue(registry.arrival(ticket)); assertFalse(registry.arrival(ticket));
        assertTrue(registry.unloaded(ticket.person(),ticket.generation(),ticket.body(),STAND));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(restored.people().markLoaded(ticket.person(),ticket.generation(),ticket.body()));
        assertFalse(restored.people().arrival(ticket));
        assertTrue(restored.people().dismiss(ticket.person(),ticket.generation(),ticket.body()));
        var current=restored.people().person(ticket.person()).orElseThrow();
        // Restoring a fresh native report is separate from reading a save.
        restored.anchors().observe(restored.anchors().official(HOME,BuildingScanner.Role.COUNCIL).orElseThrow().id(),
                restored.anchors().official(HOME,BuildingScanner.Role.COUNCIL).orElseThrow().revision(),
                new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(STAND)),0);
        var next=restored.people().reserve(current.id(),current.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); restored.people().commit(next);
        assertFalse(restored.people().arrival(next));
    }
    private static LivingKingdomsState state() {
        var state=new LivingKingdomsState(); state.registerCustom(HOME,OWNER,VillageKey.overworld(0,0),ANCHOR,"Home").orElseThrow();
        state.anchors().bind(OWNER,HOME,0,UUID.randomUUID(),-1,ANCHOR,Direction.NORTH,BuildingScanner.Range.BASIC,
                new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(STAND,STAND.north(),STAND.west(),STAND.east())),0,0); return state;
    }
}
