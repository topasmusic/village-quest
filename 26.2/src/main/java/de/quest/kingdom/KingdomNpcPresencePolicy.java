package de.quest.kingdom;

import de.quest.village.VillageLifeState;

/** Building evidence and physical village life are independent sources of authority. */
final class KingdomNpcPresencePolicy {
    private KingdomNpcPresencePolicy() {}
    static boolean mayAppear(KingdomPersonRegistry.Role role,VillageLifeState.Status life,BuildingOperation.Phase phase,long clock) {
        if (role==null || life==null || phase!=BuildingOperation.Phase.ACTIVE) return false;
        return switch(role) {
            case INNKEEPER,STOREKEEPER -> true;
            case LEADER -> life==VillageLifeState.Status.ACTIVE;
            case MARKET_TRADER -> life!=VillageLifeState.Status.ABANDONED && Math.floorMod(clock,24000)<12000;
        };
    }
}
