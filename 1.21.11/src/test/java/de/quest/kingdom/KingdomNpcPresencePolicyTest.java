package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState;
import org.junit.jupiter.api.Test;

final class KingdomNpcPresencePolicyTest {
    @Test void abandonmentKeepsInnAndWarehouseStaffButCannotCreateALeaderOrMarket() {
        for(var life:VillageLifeState.Status.values()) {
            assertTrue(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.INNKEEPER,life,BuildingOperation.Phase.ACTIVE,6000));
            assertTrue(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.STOREKEEPER,life,BuildingOperation.Phase.ACTIVE,6000));
        }
        assertFalse(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.LEADER,VillageLifeState.Status.ABANDONED,BuildingOperation.Phase.ACTIVE,6000));
        assertFalse(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.MARKET_TRADER,VillageLifeState.Status.ABANDONED,BuildingOperation.Phase.ACTIVE,6000));
    }
    @Test void marketClosesAtNightWithoutInvalidatingItsBuildingAndUnknownBuildingCannotSpawnStaff() {
        assertTrue(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.MARKET_TRADER,VillageLifeState.Status.ACTIVE,BuildingOperation.Phase.ACTIVE,6000));
        assertFalse(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.MARKET_TRADER,VillageLifeState.Status.ACTIVE,BuildingOperation.Phase.ACTIVE,18000));
        for(var phase:BuildingOperation.Phase.values()) if(phase!=BuildingOperation.Phase.ACTIVE)
            assertFalse(KingdomNpcPresencePolicy.mayAppear(KingdomPersonRegistry.Role.INNKEEPER,VillageLifeState.Status.ACTIVE,phase,6000));
    }
}
