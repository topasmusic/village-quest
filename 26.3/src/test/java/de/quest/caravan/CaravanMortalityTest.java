package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaravanMortalityTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static PlayerQuestData route() {
        var data = new PlayerQuestData(); data.setTradeRouteInt("route_count", 2); return data;
    }
    @Test void migrationAndRematerializationPreserveTheCurrentNameAndLogicalMember() {
        var data = route(); data.setTradeRouteString("route_0_crew_master_name", "Mara Oakridge");
        var first = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER);
        assertEquals("Mara Oakridge", first.name()); assertEquals(0, first.generation()); assertFalse(first.dead());
        var repeated = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER);
        assertEquals(first, repeated);
        assertNotEquals(first.id(), CaravanCrewLifecycle.resolve(data, 1, CaravanRole.MASTER).id());
    }
    @Test void aRealDeathIsIdempotentAndOnlyASafeDepartureReplacesThatRole() {
        var data = route();
        var master = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER);
        var trader = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.TRADER);
        assertTrue(CaravanCrewLifecycle.recordDeath(data, 0, CaravanRole.MASTER, master.id()));
        assertFalse(CaravanCrewLifecycle.recordDeath(data, 0, CaravanRole.MASTER, master.id()));
        assertTrue(CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER).dead());
        assertFalse(CaravanCrewLifecycle.onSafeDeparture(data, 0, false));
        assertTrue(CaravanCrewLifecycle.onSafeDeparture(data, 0, true));
        var replacement = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER);
        assertFalse(replacement.dead()); assertEquals(1, replacement.generation());
        assertNotEquals(master.id(), replacement.id()); assertNotEquals(master.name(), replacement.name());
        assertEquals(trader, CaravanCrewLifecycle.resolve(data, 0, CaravanRole.TRADER));
        assertFalse(CaravanCrewLifecycle.recordDeath(data, 0, CaravanRole.MASTER, master.id()));
        assertFalse(CaravanCrewLifecycle.onSafeDeparture(data, 0, true));
    }
    @Test void removalCompactionKeepsMemberIdentityAndTombstoneWhileNewConnectionGetsNewPeople() {
        var data = route(); var second = CaravanCrewLifecycle.resolve(data, 1, CaravanRole.GUARD);
        assertTrue(CaravanCrewLifecycle.recordDeath(data, 1, CaravanRole.GUARD, second.id()));
        assertTrue(TradeRouteData.removeRoute(data, 0, 5));
        var compacted = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.GUARD);
        assertEquals(second.id(), compacted.id()); assertTrue(compacted.dead());
        assertTrue(TradeRouteData.removeRoute(data, 0, 5)); data.setTradeRouteInt("route_count", 1);
        assertNotEquals(second.id(), CaravanCrewLifecycle.resolve(data, 0, CaravanRole.GUARD).id());
    }
    @Test void packMuleHasItsOwnSavedLifeAndCannotBecomeCrewLootOrAnotherRole() {
        var data = route(); var mule = CaravanCrewLifecycle.resolveMule(data, 0);
        assertNull(mule.role()); assertNotEquals(mule.id(), CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER).id());
        assertTrue(CaravanCrewLifecycle.recordDeath(data, 0, null, mule.id()));
        assertTrue(CaravanCrewLifecycle.resolveMule(data, 0).dead());
        assertTrue(CaravanCrewLifecycle.onSafeDeparture(data, 0, true));
        var replacement = CaravanCrewLifecycle.resolveMule(data, 0);
        assertNotEquals(mule.id(), replacement.id()); assertNotEquals(mule.name(), replacement.name());
    }
    @Test void firstLifeMigrationIsReproducibleFromConnectionAndRoleWithoutRewritingSavedLives() {
        UUID connection = UUID.randomUUID(); var a = route(); var b = route();
        a.setTradeRouteString("route_0_connection_id", connection.toString()); b.setTradeRouteString("route_0_connection_id", connection.toString());
        for (var role : CaravanRole.values()) assertEquals(CaravanCrewLifecycle.resolve(a, 0, role).id(), CaravanCrewLifecycle.resolve(b, 0, role).id());
        assertEquals(CaravanCrewLifecycle.resolveMule(a, 0).id(), CaravanCrewLifecycle.resolveMule(b, 0).id());
        UUID saved = UUID.randomUUID(); a.setTradeRouteString("route_0_crew_master_id", saved.toString());
        assertEquals(saved, CaravanCrewLifecycle.resolve(a, 0, CaravanRole.MASTER).id());
    }
}
