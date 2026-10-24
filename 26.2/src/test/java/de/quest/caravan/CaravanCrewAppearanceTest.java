package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaravanCrewAppearanceTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void legacyNamesAndLogicalLivesKeepTheirStableAppearanceAcrossRouteCompaction() {
        var data=new PlayerQuestData();
        data.setTradeRouteInt("route_count",2); data.setTradeRouteString("route_1_crew_master_name","Mara Oakridge");
        var person=CaravanCrewLifecycle.resolve(data,1,CaravanRole.MASTER); assertTrue(person.female());
        TradeRouteData.removeRoute(data,0,TradeRouteService.MAX_ROUTES);
        assertEquals(person,CaravanCrewLifecycle.resolve(data,0,CaravanRole.MASTER));
        assertEquals("Mara Oakridge",CaravanCrewLifecycle.resolve(data,0,CaravanRole.MASTER).name());
    }
    @Test void appearanceIsBoundToTheLogicalLifeInsteadOfBeingChosenAgainOnEachEncounter() {
        var data=new PlayerQuestData(); data.setTradeRouteInt("route_count",1);
        data.setTradeRouteString("route_0_crew_trader_name","Hanna Riverbend");
        var trader=CaravanCrewLifecycle.resolve(data,0,CaravanRole.TRADER); assertTrue(trader.female());
        data.setTradeRouteString("route_0_crew_trader_name","Elric Riverbend");
        assertTrue(CaravanCrewLifecycle.resolve(data,0,CaravanRole.TRADER).female());
        assertTrue(CaravanCrewLifecycle.recordDeath(data,0,CaravanRole.TRADER,trader.id()));
        assertTrue(CaravanCrewLifecycle.onSafeDeparture(data,0,true));
        var replacement=CaravanCrewLifecycle.resolve(data,0,CaravanRole.TRADER);
        assertNotEquals(trader.id(),replacement.id());
        assertEquals(replacement,CaravanCrewLifecycle.resolve(data,0,CaravanRole.TRADER));
        assertFalse(CaravanCrewLifecycle.resolveMule(data,0).female());
    }
    @Test void knownLegacyMaleNamesRemainMaleAndTheirNamesAreNotRewritten() {
        var data=new PlayerQuestData(); data.setTradeRouteInt("route_count",1);
        data.setTradeRouteString("route_0_crew_guard_name","Torren Bellweather");
        var guard=CaravanCrewLifecycle.resolve(data,0,CaravanRole.GUARD);
        assertFalse(guard.female()); assertEquals("Torren Bellweather",guard.name());
    }
}
