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
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Lease authority and persistence only; no worlds, actual entities or sleeping simulation. */
final class KingdomBedRegistryTest {
    private static final UUID TOWN=UUID.randomUUID(),OWNER=UUID.randomUUID();
    private static final BlockPos ANCHOR=new BlockPos(0,64,0),STAND=ANCHOR.north(),BED=ANCHOR.west(4);
    private static final Identifier BED_TYPE=Identifier.fromNamespaceAndPath("minecraft","red_bed");
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void bothBedHalvesAreExclusiveAndAnOwnInnLeaseRemainsFunctionalAcrossUnloadAndReload() {
        var state=state(); var ticket=body(state,KingdomPersonRegistry.Role.INNKEEPER,0);
        var lease=state.people().beds().claim(ticket,TOWN,BED,Direction.EAST,BED_TYPE,0).orElseThrow();
        assertEquals(ticket.person(),state.people().beds().owner("minecraft:overworld",BED.east()).orElseThrow());
        assertEquals(KingdomBedRegistry.Permission.AVAILABLE,state.people().beds().permission(TOWN,"minecraft:overworld",BED));
        var trader=body(state,KingdomPersonRegistry.Role.MARKET_TRADER,0);
        assertTrue(state.people().beds().claim(trader,TOWN,BED.east(),Direction.WEST,BED_TYPE,0).isEmpty());
        assertTrue(state.people().unloaded(ticket.person(),ticket.generation(),ticket.body(),STAND));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(lease,restored.people().beds().lease(ticket.person()).orElseThrow());
        assertEquals(KingdomBedRegistry.Permission.AVAILABLE,restored.people().beds().permission(TOWN,"minecraft:overworld",BED));
    }
    @Test void deathKeepsAReleaseReservationUntilLoadedNativeCleanupAndOldCleanupCannotDeleteANewLease() {
        var state=state(); var ticket=body(state,KingdomPersonRegistry.Role.INNKEEPER,0);
        var old=state.people().beds().claim(ticket,TOWN,BED,Direction.EAST,BED_TYPE,0).orElseThrow();
        assertTrue(state.people().died(ticket.person(),ticket.generation(),ticket.body()));
        assertTrue(state.people().beds().lease(ticket.person()).orElseThrow().releasing());
        assertEquals(KingdomBedRegistry.Permission.RESERVED,state.people().beds().permission(TOWN,"minecraft:overworld",BED));
        assertTrue(state.people().beds().completeRelease(old.id()));
        for(long tick=0;tick<=24000;tick+=100) state.people().observeActive(ticket.person(),tick,true);
        var person=state.people().person(ticket.person()).orElseThrow();
        var next=state.people().reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow(); state.people().commit(next);
        var replacement=state.people().beds().claim(next,TOWN,BED,Direction.EAST,BED_TYPE,0).orElseThrow();
        assertFalse(state.people().beds().completeRelease(old.id()));
        assertEquals(replacement,state.people().beds().lease(next.person()).orElseThrow());
    }
    @Test void staleOrUnsupportedBodiesAndUncertifiedBedPositionsCannotObtainLeases() {
        var state=state(); var ticket=body(state,KingdomPersonRegistry.Role.INNKEEPER,0);
        assertTrue(state.people().beds().claim(new KingdomPersonRegistry.Ticket(ticket.person(),ticket.generation()+1,ticket.body()),TOWN,BED,Direction.EAST,BED_TYPE,0).isEmpty());
        assertTrue(state.people().beds().claim(ticket,TOWN,BED.east(20),Direction.EAST,BED_TYPE,0).isEmpty());
        assertTrue(state.people().beds().claim(ticket,TOWN,BED,Direction.UP,BED_TYPE,0).isEmpty());
        assertTrue(state.people().beds().claim(ticket,TOWN,BED,Direction.EAST,BED_TYPE,41).isEmpty());
    }
    @Test void unknownFutureAndMalformedBedRootsAreOpaqueAndNeverTreatedAsUnclaimedBeds() {
        var future=new CompoundTag(); future.putInt("schema",99); future.putString("opaque","keep");
        for(var payload:java.util.List.of(future,StringTag.valueOf("future lease data"))) {
            var saved=LivingKingdomsState.toNbt(state()); saved.getCompoundOrEmpty("people").put("beds",payload.copy());
            var restored=LivingKingdomsState.fromNbt(saved);
            assertTrue(restored.people().beds().readOnly());
            assertEquals(KingdomBedRegistry.Permission.UNKNOWN,restored.people().beds().permission(TOWN,"minecraft:overworld",BED));
            assertEquals(payload,LivingKingdomsState.toNbt(restored).getCompoundOrEmpty("people").get("beds"));
        }
    }
    private static KingdomPersonRegistry.Ticket body(LivingKingdomsState state,KingdomPersonRegistry.Role role,int slot) {
        var person=state.people().ensure(TOWN,role,slot).orElseThrow();
        if(role==KingdomPersonRegistry.Role.MARKET_TRADER) {
            var anchor=state.anchors().official(TOWN,BuildingScanner.Role.MARKET).orElseThrow();
            var counter=STAND.north(); var container=counter.above();
            state.anchors().observe(anchor.id(),anchor.revision(),new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,
                    Map.of(),Map.of(BuildingScanner.Feature.COUNTER,Set.of(counter),BuildingScanner.Feature.STALL,Set.of(counter),BuildingScanner.Feature.LIGHT,Set.of(counter.above(2)),BuildingScanner.Feature.CONTAINER,Set.of(container)),Set.of(STAND)),0);
            assertEquals(KingdomMarketPlaces.Result.APPLIED,state.marketPlaces().assign(OWNER,TOWN,state.village(TOWN).orElseThrow().revision(),
                    anchor.id(),anchor.revision(),0,slot,STAND,counter,container,0));
            state.marketDays().observe(TOWN,2000,0,1,java.util.List.of(KingdomMarketCatalog.plan(person,de.quest.shrine.VillageBondType.GRANARY)),true).orElseThrow();
        }
        var ticket=state.people().reserve(person.id(),person.revision(),UUID.randomUUID(),STAND,0).orElseThrow();
        assertTrue(state.people().commit(ticket)); return ticket;
    }
    private static LivingKingdomsState state() {
        var state=new LivingKingdomsState(); state.registerCustom(TOWN,OWNER,VillageKey.overworld(0,0),ANCHOR,"Inn town").orElseThrow();
        for(var role:java.util.List.of(BuildingScanner.Role.INN,BuildingScanner.Role.MARKET)) {
            BlockPos anchor=role==BuildingScanner.Role.INN?ANCHOR:ANCHOR.south(4);
            long rev=state.village(TOWN).orElseThrow().revision();
            state.anchors().bind(OWNER,TOWN,rev,UUID.randomUUID(),-1,anchor,Direction.NORTH,BuildingScanner.Range.BASIC,
                    new BuildingScanner.Report(role,BuildingScanner.Status.VALID,Map.of(),role==BuildingScanner.Role.INN?Map.of(BuildingScanner.Feature.BED,Set.of(BED)):Map.of(),Set.of(STAND)),0,0);
        }
        return state;
    }
}
