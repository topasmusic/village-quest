package de.quest.kingdom;

import de.quest.data.QuestState;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageContactService;
import de.quest.village.LivingVillageNetworkState;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Reads an existing personal network row pinned once to a shared market home. Never creates or retargets one. */
public final class KingdomMarketDemandService {
    private KingdomMarketDemandService() {}
    static java.util.Optional<LivingVillageNetworkState.VillageSnapshot> current(ServerPlayer observer,UUID home) {
        if(!observe(observer,home)) return java.util.Optional.empty();
        var state=LivingKingdomsState.get(observer.level().getServer());
        return state.markets().homeNeedSource(home).flatMap(source ->
                LivingVillageNetworkState.get(observer.level().getServer()).marketSnapshot(source.owner(),source.index()));
    }
    public static boolean observe(ServerPlayer observer,UUID home) {
        if(observer==null || !observer.level().getServer().isSameThread()) return false;
        var server=observer.level().getServer();
        return observe(LivingKingdomsState.get(server),QuestState.get(server),LivingVillageNetworkState.get(server),observer.getUUID(),home);
    }
    static boolean observe(LivingKingdomsState state,QuestState quests,LivingVillageNetworkState network,UUID observer,UUID home) {
        var ledger=state.markets(); ledger.invalidateHomeNeed(home);
        var village=state.village(home).orElse(null);
        if(village==null || state.marketHomes().readOnly() || network.schemaVersion()!=2 || !village.legacyKey().dimension().equals("minecraft:overworld")) return false;
        var pinned=ledger.homeNeedSource(home);
        var shared=state.marketHomes().profile(home);
        UUID owner=pinned.map(KingdomMarketLedger.HomeNeedSource::owner).orElse(
                shared.map(KingdomMarketHomes.Profile::owner).orElse(village.origin()==LivingKingdomsState.Origin.CUSTOM?village.founder():observer));
        var data=quests.getPlayersView().get(owner); if(data==null) return false;
        int index=pinned.map(KingdomMarketLedger.HomeNeedSource::index).orElse(shared.map(KingdomMarketHomes.Profile::index).orElse(-1));
        if(index<0) {
            for(int i=0;i<VillageBondService.historicalVillageCount(data);i++) {
                if(home.toString().equals(data.getTradeRouteString(VillageBondService.villageKey(i,"canonical_id")))) { index=i; break; }
            }
        }
        var contact=VillageContactService.read(data,index);
        if(contact==null || !home.toString().equals(data.getTradeRouteString(VillageBondService.villageKey(index,"canonical_id")))
                || shared.filter(profile -> !profile.owner().equals(owner) || profile.index()!=contact.villageIndex() || profile.type()!=contact.type()).isPresent()
                || !"minecraft:overworld".equals(data.getTradeRouteString(VillageBondService.villageKey(index,"dimension")))
                || contact.x()!=village.legacyKey().anchorX() || contact.z()!=village.legacyKey().anchorZ()) return false;
        var actual=network.marketSnapshot(owner,index);
        return actual.filter(snapshot -> snapshot.type()==contact.type()).map(snapshot -> ledger.observeHomeNeed(home,owner,snapshot)).orElse(false);
    }
}
