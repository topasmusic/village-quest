package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.caravan.KingdomRouteConnections.Connection;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class KingdomMarketPartnerOffersTest {
    private static final UUID HOME=KingdomMarketPlacesTest.TOWN,OWNER=KingdomMarketPlacesTest.OWNER;
    private record Fixture(LivingKingdomsState state,UUID partner,KingdomPersonRegistry.View person,Connection route) {}
    private static Fixture fixture(boolean profile) {
        var state=KingdomMarketPlacesTest.state(0); KingdomMarketPlacesTest.assign(state,0,0);
        var partner=UUID.randomUUID();
        state.registerCustom(partner,OWNER,VillageKey.overworld(300,0),new BlockPos(300,64,0),"Forge partner").orElseThrow();
        for(int project=0;project<2;project++) assertTrue(state.recognition().complete(OWNER,partner,0,project,project,KingdomRecognition.requirements(project)));
        if(profile) assertTrue(state.marketHomes().bind(new KingdomMarketHomes.Profile(partner,OWNER,0,VillageBondType.FORGE)));
        return new Fixture(state,partner,state.people().ensure(HOME,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow(),
                new Connection(OWNER,UUID.randomUUID(),HOME,partner,"active"));
    }
    private static void sign(LivingKingdomsState state,UUID a,UUID b) {
        var offer=state.diplomacy().offer(OWNER,a,b,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        state.diplomacy().accept(OWNER,offer.id(),0).orElseThrow();
    }
    private static KingdomMarketLedger.TraderPlan plan(Fixture f,List<Connection> routes) {
        return KingdomMarketVisitors.plan(f.state,f.person,VillageBondType.GRANARY,routes);
    }
    @Test void anAgreementAndActiveDirectConnectionAndPinnedOriginAreRequiredTogether() {
        var f=fixture(true); var base=plan(f,List.of()); assertEquals(base,plan(f,List.of(f.route)));
        sign(f.state,HOME,f.partner);
        assertEquals(base,plan(f,List.of()));
        assertEquals(base,plan(f,List.of(new Connection(OWNER,f.route.id(),HOME,f.partner,"incident"))));
        var offered=plan(f,List.of(f.route)); assertNotEquals(base,offered);
        assertEquals(HOME,offered.home()); assertEquals(f.person.id(),offered.person());
        assertTrue(java.util.Set.of("rail","minecart","lightning_rod").contains(offered.goods().getFirst().sku()));
        var unknown=fixture(false); sign(unknown.state,HOME,unknown.partner);
        assertEquals(plan(unknown,List.of()),plan(unknown,List.of(unknown.route)));
    }
    @Test void partnerRelationshipsAreNotTransitiveAndDuplicateRoutesCannotMultiplyStock() {
        var f=fixture(true); sign(f.state,HOME,f.partner);
        var third=UUID.randomUUID(); f.state.registerCustom(third,OWNER,VillageKey.overworld(600,0),new BlockPos(600,64,0),"Archive").orElseThrow();
        f.state.marketHomes().bind(new KingdomMarketHomes.Profile(third,OWNER,1,VillageBondType.ARCHIVE));
        sign(f.state,f.partner,third);
        var indirect=new Connection(OWNER,UUID.randomUUID(),f.partner,third,"active");
        assertEquals(plan(f,List.of(f.route)),plan(f,List.of(indirect,f.route,f.route)));
    }
    @Test void newAgreementCannotRerollAnExistingVisitOnRefreshOrReload() {
        var f=fixture(true); var base=plan(f,List.of(f.route));
        var visit=f.state.marketDays().observe(HOME,2000,0,1,List.of(base),true,List.of(f.route)).orElseThrow();
        sign(f.state,HOME,f.partner); var enriched=plan(f,List.of(f.route)); assertNotEquals(base,enriched);
        var reopened=f.state.marketDays().observe(HOME,2001,1,1,List.of(enriched),true,List.of(f.route)).orElseThrow();
        assertEquals(visit,reopened);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        restored.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),10);
        var loaded=restored.marketDays().observe(HOME,2010,10,1,List.of(enriched),true,List.of(f.route)).orElseThrow();
        assertEquals(visit.id(),loaded.id()); assertEquals(visit.traders(),loaded.traders());
    }
    @Test void savedPartnerProfilesAndRouteOrderCannotRerollAnUnstartedPlan() {
        var f=fixture(true); sign(f.state,HOME,f.partner);
        var before=LivingKingdomsState.toNbt(f.state); var expected=plan(f,List.of(f.route));
        assertEquals(before,LivingKingdomsState.toNbt(f.state));
        var restored=LivingKingdomsState.fromNbt(before);
        assertEquals(expected,KingdomMarketVisitors.plan(restored,restored.people().person(f.person.id()).orElseThrow(),VillageBondType.GRANARY,List.of(f.route)));
    }
}
