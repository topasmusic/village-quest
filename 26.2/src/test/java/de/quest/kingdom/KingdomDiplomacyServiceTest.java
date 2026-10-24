package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import de.quest.data.PlayerQuestData;
import de.quest.shrine.*;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

final class KingdomDiplomacyServiceTest {
    @Test void knownObserverCanReadButCannotSignOrDiscoverAnUnknownHost() {
        var s=KingdomMarketPlacesTest.state(0); var home=KingdomMarketPlacesTest.TOWN; var owner=KingdomMarketPlacesTest.OWNER;
        var other=UUID.randomUUID(); var observer=UUID.randomUUID();
        s.registerCustom(other,owner,VillageKey.overworld(40,0),new BlockPos(40,64,0),"Other").orElseThrow();
        var data=new PlayerQuestData(); VillageContactService.establish(data,s.village(other).orElseThrow(),VillageBondType.FORGE);
        assertFalse(KingdomDiplomacyService.canRead(s,data,observer,home));
        VillageContactService.establish(data,s.village(home).orElseThrow(),VillageBondType.FORGE);
        var before=LivingKingdomsState.toNbt(s); var view=KingdomDiplomacyService.view(s,data,observer,home,31);
        assertTrue(view.readOnly()); assertEquals(1,view.partners().size()); assertEquals(other,view.partners().getFirst().village());
        assertFalse(KingdomDiplomacyService.actFromView(s,data,observer,home,0,view.partners().getFirst(),-1,0,false));
        assertEquals(before,LivingKingdomsState.toNbt(s));
    }
    @Test void explanationRetainsLoyaltyAndExactPersonalRepairWithoutExposingAnotherActor() {
        var s=KingdomMarketPlacesTest.state(0); var home=KingdomMarketPlacesTest.TOWN; var owner=KingdomMarketPlacesTest.OWNER;
        var other=UUID.randomUUID(); s.registerCustom(other,owner,VillageKey.overworld(40,0),new BlockPos(40,64,0),"Other").orElseThrow();
        var event=UUID.randomUUID(); s.relations().remember(home,other,event,"loyalty");
        s.relations().delivered(home,other,owner,UUID.randomUUID(),1);
        var view=KingdomDiplomacyService.explain(s,owner,home,other);
        assertEquals("strained",view.state()); assertEquals("loyalty",view.repair());
        assertTrue(view.causes().containsAll(List.of("loyalty","delivery")));
        s.relations().remember(home,other,event,"reconciliation");
        assertEquals("rebuilding",KingdomDiplomacyService.explain(s,owner,home,other).trend());
        for(int i=0;i<3;i++) s.relations().refusedAttack(owner,home,UUID.randomUUID());
        s.relations().apologizeAttack(owner,home,de.quest.village.VillageNeed.GRANARY_PANTRY);
        view=KingdomDiplomacyService.explain(s,owner,home,other);
        assertEquals("attack_freight",view.repair()); assertEquals("granary_pantry",view.need());
        assertEquals(s.village(home).orElseThrow().name(),view.repairVillage());
        var stranger=KingdomDiplomacyService.explain(s,UUID.randomUUID(),home,other);
        assertFalse(stranger.causes().contains("attack")); assertEquals("none",stranger.need());
    }
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void registeredUnknownNeighboursCannotBecomeRemoteNegotiationTargets() {
        var state=KingdomMarketPlacesTest.state(0); var town=KingdomMarketPlacesTest.TOWN; var actor=KingdomMarketPlacesTest.OWNER;
        var other=UUID.randomUUID(); state.registerCustom(other,actor,VillageKey.overworld(40,0),new BlockPos(40,64,0),"Other").orElseThrow();
        var data=new PlayerQuestData();
        assertTrue(KingdomDiplomacyService.view(state,data,actor,town,0).partners().isEmpty());
        assertFalse(KingdomDiplomacyService.act(state,data,actor,town,0,other,0,-1,0,false));
        VillageContactService.establish(data,state.village(other).orElseThrow(),VillageBondType.FORGE);
        var snapshot=KingdomDiplomacyService.view(state,data,actor,town,0).partners().getFirst();
        assertFalse(KingdomDiplomacyService.act(state,data,actor,town,0,other,1,-1,0,false));
        assertTrue(KingdomDiplomacyService.actFromView(state,data,actor,town,0,snapshot,-1,0,false));
        assertFalse(KingdomDiplomacyService.actFromView(state,data,actor,town,0,snapshot,0,0,false),
                "Guessing the current revision cannot bypass the actual private snapshot");
        assertFalse(KingdomDiplomacyService.act(state,data,UUID.randomUUID(),town,0,other,0,0,0,true));
    }
}
