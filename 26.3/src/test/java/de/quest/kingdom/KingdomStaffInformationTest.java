package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.caravan.KingdomRouteConnections.Connection;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Saved registries and isolated projections, without starting a world or simulating gameplay. */
final class KingdomStaffInformationTest {
    private static String key(Component c) { return ((TranslatableContents)c.getContents()).getKey(); }
    private static Object[] args(Component c) { return ((TranslatableContents)c.getContents()).getArgs(); }
    private static LivingKingdomsState market() {
        var state=KingdomMarketPlacesTest.state(0); KingdomMarketPlacesTest.assign(state,0,0);
        var p=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,1,
                List.of(KingdomMarketCatalog.plan(p,VillageBondType.GRANARY)),true).orElseThrow();
        return state;
    }
    @Test void plannedRosterIsNotAnObservedGuestAndProjectionCannotSpawnOrRefill() {
        var state=market(); var before=LivingKingdomsState.toNbt(state);
        assertTrue(key(KingdomStaffInformation.inn(state,KingdomMarketPlacesTest.TOWN,0,p -> true).orElseThrow()).endsWith(".quiet"));
        assertEquals(before,LivingKingdomsState.toNbt(state));
    }
    @Test void actualCurrentGenerationAndFreshStandAndNativeObservationAreAllRequired() {
        var state=market(); var town=KingdomMarketPlacesTest.TOWN;
        var p=state.people().ensure(town,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        var ticket=state.people().reserve(p.id(),p.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),0).orElseThrow();
        state.people().commit(ticket);
        assertTrue(key(KingdomStaffInformation.inn(state,town,0,ignored -> false).orElseThrow()).endsWith(".quiet"));
        var news=KingdomStaffInformation.inn(state,town,0,ignored -> true).orElseThrow();
        assertTrue(key(news).endsWith(".market")); assertEquals(1,args(news)[0]); assertEquals(p.name(),args(news)[1]);
        assertTrue(key(KingdomStaffInformation.inn(state,town,41,ignored -> true).orElseThrow()).endsWith(".quiet"));
        state.people().unloaded(p.id(),ticket.generation(),ticket.body(),KingdomMarketPlacesTest.stand(0));
        assertTrue(key(KingdomStaffInformation.inn(state,town,0,ignored -> true).orElseThrow()).endsWith(".quiet"));
    }
    @Test void closedVisitAndRestartNeverAdvertiseAnOldRosterAsCurrentlyPresent() {
        var state=market(); var town=KingdomMarketPlacesTest.TOWN;
        var p=state.people().ensure(town,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        var ticket=state.people().reserve(p.id(),p.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),0).orElseThrow(); state.people().commit(ticket);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(key(KingdomStaffInformation.inn(restored,town,0,ignored -> true).orElseThrow()).endsWith(".quiet"));
        state.markets().close(town);
        assertTrue(key(KingdomStaffInformation.inn(state,town,0,ignored -> true).orElseThrow()).endsWith(".quiet"));
    }
    @Test void freightInformationIsPrivateAndNeedsAnActualArrivalReceipt() {
        var state=market(); var town=KingdomMarketPlacesTest.TOWN; var actor=UUID.randomUUID(); var other=UUID.randomUUID();
        var partner=UUID.randomUUID(); state.registerCustom(partner,actor,VillageKey.overworld(300,0),new BlockPos(300,64,0),"Other village").orElseThrow();
        var route=new Connection(actor,UUID.randomUUID(),town,partner,"active");
        assertTrue(key(KingdomStaffInformation.store(state,other,town,List.of(route)).orElseThrow()).endsWith(".unknown"));
        assertTrue(key(KingdomStaffInformation.store(state,actor,town,List.of(route)).orElseThrow()).endsWith(".route"));
        state.relations().delivered(town,partner,other,route.id(),1);
        assertTrue(key(KingdomStaffInformation.store(state,actor,town,List.of(route)).orElseThrow()).endsWith(".route"));
        state.relations().delivered(town,partner,actor,route.id(),2);
        var saved=LivingKingdomsState.toNbt(state); var restored=LivingKingdomsState.fromNbt(saved);
        var news=KingdomStaffInformation.store(restored,actor,town,List.of(route)).orElseThrow();
        assertTrue(key(news).endsWith(".arrived")); assertEquals("Other village",args(news)[0]);
        assertEquals(saved,LivingKingdomsState.toNbt(restored));
    }
    @Test void replacementRouteDoesNotTurnPairHistoryIntoAnArrivalOnTheNewRoute() {
        var state=market(); var town=KingdomMarketPlacesTest.TOWN; var actor=UUID.randomUUID();
        var partner=UUID.randomUUID(); state.registerCustom(partner,actor,VillageKey.overworld(300,0),new BlockPos(300,64,0),"Partner").orElseThrow();
        var oldRoute=UUID.randomUUID(); state.relations().delivered(town,partner,actor,oldRoute,2);
        var replacement=new Connection(actor,UUID.randomUUID(),town,partner,"active");
        var saved=LivingKingdomsState.toNbt(state);
        var restored=LivingKingdomsState.fromNbt(saved);
        var news=KingdomStaffInformation.store(restored,actor,town,List.of(replacement)).orElseThrow();
        assertTrue(key(news).endsWith(".arrived")); assertEquals("Partner",args(news)[0]);
        assertEquals(saved,LivingKingdomsState.toNbt(restored));
        assertTrue(key(KingdomStaffInformation.store(restored,UUID.randomUUID(),town,List.of(replacement)).orElseThrow()).endsWith(".unknown"));
    }
    @Test void knownPausedRouteTakesPriorityWithoutInventingANewArrivalOrRemoteSurvey() {
        var state=market(); var town=KingdomMarketPlacesTest.TOWN; var actor=UUID.randomUUID();
        var partner=UUID.randomUUID(); state.registerCustom(partner,actor,VillageKey.overworld(300,0),new BlockPos(300,64,0),"Partner").orElseThrow();
        var active=new Connection(actor,UUID.randomUUID(),town,partner,"active");
        var paused=new Connection(actor,UUID.randomUUID(),partner,town,"anchor_moved");
        var before=LivingKingdomsState.toNbt(state);
        var news=KingdomStaffInformation.store(state,actor,town,List.of(active,paused)).orElseThrow();
        assertTrue(key(news).endsWith(".paused")); assertEquals("Partner",args(news)[0]);
        assertEquals("screen.village-quest.trade_route.availability.anchor_moved",key((Component)args(news)[1]));
        assertEquals(before,LivingKingdomsState.toNbt(state));
    }
    @Test void allThreeLanguagesExplainCurrentPresenceAndHistoricalRoadEvidenceWithCompleteArguments() throws Exception {
        for(var locale:List.of("de_de","en_us","es_es")) {
            try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"),java.nio.charset.StandardCharsets.UTF_8)) {
                var catalog=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                String historical=catalog.get("message.village-quest.kingdom.staff_info.store.arrived").getAsString();
                String pending=catalog.get("message.village-quest.kingdom.staff_info.store.route").getAsString();
                assertTrue(historical.contains(switch(locale) {case "de_de" -> "fr\u00fcher"; case "es_es" -> "anterior"; default -> "past";}),locale+": historical pair evidence");
                assertFalse(pending.contains(switch(locale) {case "de_de" -> "auf dieser Verbindung"; case "es_es" -> "en esa conexi\u00f3n"; default -> "on that connection";}),locale+": not a route receipt");
                var examples=java.util.Map.<String,Object[]>of(
                        "inn.quiet",new Object[]{},"inn.market",new Object[]{5,"Mára","Peñabrisa"},
                        "store.unknown",new Object[]{},"store.route",new Object[]{"Peñabrisa"},
                        "store.arrived",new Object[]{"Peñabrisa"},"store.paused",new Object[]{"Peñabrisa","Closed"});
                for(var example:examples.entrySet()) {
                    String text=catalog.get("message.village-quest.kingdom.staff_info."+example.getKey()).getAsString();
                    String spoken=String.format(java.util.Locale.ROOT,text,example.getValue());
                    assertFalse(spoken.contains("%"),locale+": "+example.getKey());
                    if(example.getValue().length>0) assertTrue(spoken.contains("Peñabrisa"));
                    // Same private line contains the normal staff greeting plus this report.
                    for(int p=0;p<4;p++) {
                        String role=example.getKey().startsWith("inn.")?"innkeeper":"storekeeper";
                        String greeting=String.format(java.util.Locale.ROOT,catalog.get("message.village-quest.kingdom.staff."+role+".again."+p).getAsString(),"Mára","Peñabrisa");
                        assertTrue(greeting.length()+1+spoken.length()<1024,locale+": speech payload limit");
                    }
                }
            }
        }
    }
}
