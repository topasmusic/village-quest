package de.quest.kingdom;

import de.quest.data.QuestState;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import java.util.*;

/** Server-owned local roster adapter. Missing real contact/profile evidence never becomes a guessed origin. */
final class KingdomMarketVisitors {
    private KingdomMarketVisitors() {}
    /** Native entry point: capacity comes only from the shared earned recognition registry. */
    static Optional<KingdomMarketLedger.VisitView> observe(LivingKingdomsState state,QuestState quests,UUID host,UUID observer,
                                                         long clock,long now,boolean loaded,boolean inhabited) {
        if(state==null) return Optional.empty();
        return observe(state,quests,host,observer,clock,now,state.recognition().capacity(host),loaded && !state.recognition().readOnly(),inhabited);
    }
    static Optional<KingdomMarketLedger.VisitView> observe(LivingKingdomsState state,QuestState quests,UUID host,UUID observer,
                                                         long clock,long now,int earnedCapacity,boolean loaded,boolean inhabited) {
        return observe(state,quests,host,observer,clock,now,earnedCapacity,loaded,inhabited,List.of());
    }
    static Optional<KingdomMarketLedger.VisitView> observe(LivingKingdomsState state,QuestState quests,UUID host,UUID observer,
            long clock,long now,int earnedCapacity,boolean loaded,boolean inhabited,List<de.quest.caravan.KingdomRouteConnections.Connection> routes) {
        if(state==null || quests==null || host==null || state.marketDays().readOnly()) return Optional.empty();
        var anchor=state.anchors().official(host,BuildingScanner.Role.MARKET).orElse(null);
        boolean ready=loaded && inhabited && anchor!=null && state.anchors().mayContinue(anchor.id(),now);
        // Advance both existing clocks before choosing identities; a fresh slot must not replace
        // yesterday's recurring person just because its cooldown is updated one callback later.
        state.markets().observe(host,now,ready);
        state.marketTravel().observe(host,clock,now,ready,routes);
        var plans=new ArrayList<KingdomMarketLedger.TraderPlan>();
        if(ready && !state.marketDays().plannedToday(host,clock) && clock>=0 && Math.floorMod(clock,24000)>=1000 && Math.floorMod(clock,24000)<12000) {
            int count=Math.min(earnedCapacity,state.marketPlaces().usable(anchor.id(),now).size());
            // A quiet local day remains part of the routine; higher earned capacity admits more guests.
            if(Math.floorMod(clock/24000+host.getLeastSignificantBits(),3)!=0) {
                var homes=routes.stream().filter(c -> c.active() && (c.home().equals(host) || c.destination().equals(host)))
                        .map(c -> c.home().equals(host)?c.destination():c.home()).distinct().sorted().toList();
                for(var home:homes) {
                    if(plans.size()>=Math.min(count,(earnedCapacity+1)/2) || !state.marketTravel().permits(home,host)) continue;
                    var origin=profile(state,quests,home,observer); if(origin.isEmpty()) continue;
                    for(int i=0;i<state.recognition().capacity(home);i++) {
                        var person=state.people().ensure(home,KingdomPersonRegistry.Role.MARKET_TRADER,i);
                        if(person.isPresent() && state.marketTravel().canPlan(host,person.get().id(),clock/24000)) {
                            plans.add(plan(state,person.get(),origin.get(),routes,clock/24000)); break;
                        }
                    }
                }
            }
            var profile=profile(state,quests,host,observer);
            if(profile.isPresent()) {
                for(int i=0;i<earnedCapacity && plans.size()<count;i++) {
                    var person=state.people().ensure(host,KingdomPersonRegistry.Role.MARKET_TRADER,i);
                    if(person.isPresent() && state.marketTravel().canPlan(host,person.get().id(),clock/24000)) plans.add(plan(state,person.get(),profile.get(),routes,clock/24000));
                }
            }
        }
        return state.marketDays().observe(host,clock,now,earnedCapacity,plans,ready,routes);
    }
    /** A fresh visit may carry partner specialties; an existing visit is never rebuilt from these inputs. */
    static KingdomMarketLedger.TraderPlan plan(LivingKingdomsState state,KingdomPersonRegistry.View person, VillageBondType type,
                                               List<de.quest.caravan.KingdomRouteConnections.Connection> routes) {
        return plan(state,person,type,routes,0);
    }
    static KingdomMarketLedger.TraderPlan plan(LivingKingdomsState state,KingdomPersonRegistry.View person,VillageBondType type,
                                               List<de.quest.caravan.KingdomRouteConnections.Connection> routes,long date) {
        var home=person.village();
        var partners=routes.stream().filter(c -> c.active() && (c.home().equals(home) || c.destination().equals(home)))
                .map(c -> c.home().equals(home)?c.destination():c.home()).filter(p -> !p.equals(home)).distinct().sorted()
                .filter(p -> state.marketTravel().permits(home,p))
                .map(p -> state.marketHomes().profile(p).map(profile -> new KingdomMarketCatalog.Partner(profile.type(),state.recognition().level(p))))
                .flatMap(Optional::stream).distinct().limit(5).toList();
        var sale=KingdomMarketCatalog.regional(person,type,state.recognition().level(home),date,partners);
        var request=state.markets().buybackOffer(home,person.appearanceSeed() ^ Long.rotateLeft(date,17));
        if(request.isEmpty()) return sale;
        var goods=new ArrayList<>(sale.goods()); if(goods.size()==8) goods.removeLast(); goods.add(request.get());
        return new KingdomMarketLedger.TraderPlan(person.id(),home,goods);
    }
    private static Optional<VillageBondType> profile(LivingKingdomsState state,QuestState quests,UUID host,UUID observer) {
        var shared=state.marketHomes().profile(host); if(shared.isPresent()) return shared.map(KingdomMarketHomes.Profile::type);
        var village=state.village(host).orElse(null); if(village==null) return Optional.empty();
        var pinned=state.markets().homeNeedSource(host);
        UUID owner=pinned.map(KingdomMarketLedger.HomeNeedSource::owner).orElse(village.origin()==LivingKingdomsState.Origin.CUSTOM?village.founder():observer);
        var data=quests.getPlayersView().get(owner); if(data==null) return Optional.empty();
        int index=pinned.map(KingdomMarketLedger.HomeNeedSource::index).orElse(-1);
        if(index<0) for(int i=0;i<VillageBondService.historicalVillageCount(data);i++)
            if(host.toString().equals(data.getTradeRouteString(VillageBondService.villageKey(i,"canonical_id")))) { index=i; break; }
        var contact=VillageContactService.read(data,index); var key=village.legacyKey();
        int type=data.getTradeRouteInt(VillageBondService.villageKey(index,"type"));
        if(contact==null || type<1 || type>5 || !host.toString().equals(data.getTradeRouteString(VillageBondService.villageKey(index,"canonical_id")))
                || !key.dimension().equals(data.getTradeRouteString(VillageBondService.villageKey(index,"dimension")))
                || state.byLegacy(new de.quest.village.VillageLifeState.VillageKey(key.dimension(),contact.x(),contact.z()))
                    .filter(actual -> actual.id().equals(host)).isEmpty()) return Optional.empty();
        return state.marketHomes().bind(new KingdomMarketHomes.Profile(host,owner,index,contact.type()))?Optional.of(contact.type()):Optional.empty();
    }
}
