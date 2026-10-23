package de.quest.kingdom;

import de.quest.village.VillageNeed;
import de.quest.village.LivingVillageNetworkState.VillageSnapshot;
import de.quest.shrine.VillageBondType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;

/** Shared saved visitors, stock, home demand and numeric receipt reservations. Native inventory application is separate. */
public final class KingdomMarketLedger {
    private static final int MAX_HOSTS=16384,MAX_RECEIPTS=8192,MAX_QUOTES=512;
    private final LivingKingdomsState state;
    private final Map<UUID,Host> hosts=new HashMap<>();
    private final Map<UUID,Visit> visits=new HashMap<>();
    private final Map<UUID,Need> needs=new HashMap<>();
    private final Map<UUID,Receipt> receipts=new LinkedHashMap<>();
    private final Map<UUID,UUID> pendingActors=new HashMap<>();
    private final Map<UUID,Quote> quotes=new LinkedHashMap<>();
    private final Map<Identifier,MarketPricePolicy.Source> priceFloors=new HashMap<>();
    private final Map<Identifier,CompoundTag> floorPayloads=new HashMap<>();
    private Tag retained=new CompoundTag();
    private boolean opaque;
    KingdomMarketLedger(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly() || state.people().readOnly() || state.marketTravel().readOnly(); }
    /** Source changes invalidate unprepared views only; saved receipts and deductions keep their existing contract. */
    public void invalidateQuotes() { quotes.clear(); }
    /** A private view refresh releases only its actor's unprepared quote IDs, never stock or receipts. */
    public void discardQuotes(UUID actor) { if(actor!=null) quotes.values().removeIf(quote -> actor.equals(quote.actor)); }
    public int remainingDemand(UUID visitId,UUID person,String sku) {
        if(readOnly()) return 0;
        var visit=visits.get(visitId); var trader=visit==null?null:visit.traders.get(person);
        var stock=trader==null?null:trader.goods.get(sku); var need=trader==null?null:needs.get(trader.home);
        if(stock==null || need==null || need.sourceOwner!=null && !need.sourceVerified || !matches(need,stock.offer.item)) return 0;
        return Math.max(0,Math.min(stock.offer.demand-stock.bought,
                (128-need.accepted.getOrDefault(stock.offer.item,0))/stock.offer.lot));
    }
    public enum Side { BUY,SELL }
    public enum Phase { RESERVED,COMMITTED,CANCELLED,CONFLICT }
    public enum Resolution { HELD,COMMITTED,CANCELLED }
    public record Goods(String sku,Identifier item,int lot,int stock,int demand,long sell,long buy) {
        public Goods {
            if(sku==null || !sku.matches("[a-z0-9_]{1,32}") || item==null || !item.getNamespace().equals("minecraft")
                    || lot<1 || lot>64 || stock<0 || stock>128/lot || stock==0 && (demand==0 || buy==0) || demand<0 || demand>128/lot || sell<1 || sell>1_000_000_000L
                    || buy<0 || buy>1_000_000_000L) throw new IllegalArgumentException("Invalid market goods");
        }
    }
    public record TraderPlan(UUID person,UUID home,List<Goods> goods) {
        public TraderPlan { goods=List.copyOf(goods); if(person==null || home==null || goods.isEmpty() || goods.size()>8) throw new IllegalArgumentException("Invalid trader plan"); }
    }
    /** One fixed request per new visit; every candidate still needs a positive current source proof. */
    Optional<Goods> buybackOffer(UUID home) {
        return buybackOffer(home,0);
    }
    Optional<Goods> buybackOffer(UUID home,long seed) {
        var need=needs.get(home);
        if(readOnly() || need==null || need.sourceOwner==null || !need.sourceVerified) return Optional.empty();
        var candidates=new ArrayList<Goods>();
        for(var candidate:RegionalBuybackPolicy.CANDIDATES) {
            var item=Identifier.withDefaultNamespace(candidate.item());
            if(!matches(need,item)) continue;
            int lots=Math.min(4,(128-need.accepted.getOrDefault(item,0))/candidate.lot());
            if(lots<1 || MarketPricePolicy.buy(1,candidate.lot(),-15,candidate.sale(),List.of(state.marketSources().source(item)))<1) continue;
            candidates.add(new Goods("need_"+candidate.item(),item,candidate.lot(),0,lots,candidate.sale(),1));
        }
        return candidates.isEmpty()?Optional.empty():Optional.of(candidates.get((int)Math.floorMod(seed,candidates.size())));
    }
    public record GoodsView(Goods offer,int stock,int bought) {}
    public record TraderView(UUID person,UUID home,int budget,List<GoodsView> goods) {}
    public record VisitView(UUID id,UUID market,long revision,boolean open,List<TraderView> traders) {}
    public record Quote(UUID id,UUID actor,UUID visit,UUID trader,String sku,Side side,int lots,int quantity,long price,
                        long revision,long needRevision,long expires) {}
    public record ReceiptView(UUID id,Quote quote,long beforeBalance,long afterBalance,int beforeItems,int afterItems,Phase phase) {}

    public Optional<VisitView> visit(UUID market) {
        var host=hosts.get(market); return host==null || host.visit==null?Optional.empty():Optional.of(host.visit.view(host));
    }
    public Optional<VisitView> visitById(UUID id) { return Optional.ofNullable(visits.get(id)).map(v -> v.view(hosts.get(v.market))); }
    public Optional<ReceiptView> receipt(UUID id) { return Optional.ofNullable(receipts.get(id)).map(Receipt::view); }
    public boolean nativeAcknowledged(UUID id) {
        var receipt=receipts.get(id); return receipt!=null && receipt.nativePlan!=null && receipt.nativeAck
                && (receipt.phase==Phase.COMMITTED || receipt.phase==Phase.CANCELLED);
    }
    public List<ReceiptView> pending(UUID actor) {
        var id=pendingActors.get(actor); return id==null?List.of():List.of(receipts.get(id).view());
    }
    /** Reserve the exact native plan in the same state mutation as the shared stock/budget. */
    public Optional<ReceiptView> prepareNative(Quote quote,UUID actor,long balance,Container bag,
                                              MarketInventoryPlan plan,HolderLookup.Provider registries,long now) {
        if(readOnly() || quote==null || plan==null || registries==null || actor==null || !actor.equals(quote.actor)
                || plan.side()!=quote.side || plan.quantity()!=quote.quantity) return Optional.empty();
        var trader=trader(quote).orElse(null); var stock=trader==null?null:trader.goods.get(quote.sku);
        if(stock==null || !BuiltInRegistries.ITEM.getKey(plan.commodity().getItem()).equals(stock.offer.item)) return Optional.empty();
        final CompoundTag snapshot;
        try { snapshot=plan.toNbt(registries); } catch(RuntimeException invalid) { return Optional.empty(); }
        var existing=receipts.get(quote.id);
        if(existing!=null) return existing.quote.equals(quote) && snapshot.equals(existing.nativePlan)
                && balance==existing.beforeBalance?Optional.of(existing.view()):Optional.empty();
        if(!plan.matchesBefore(bag)) return Optional.empty();
        var prepared=prepare(quote,actor,balance,plan.beforeCount(),true,now);
        if(prepared.isPresent()) { receipts.get(quote.id).nativePlan=snapshot; state.setDirty(); }
        return prepared;
    }
    public Optional<MarketInventoryPlan> nativePlan(UUID id,HolderLookup.Provider registries) {
        var receipt=receipts.get(id); if(receipt==null || receipt.nativePlan==null) return Optional.empty();
        return MarketInventoryPlan.fromNbt(receipt.nativePlan,registries).filter(plan -> plan.side()==receipt.quote.side
                && plan.quantity()==receipt.quote.quantity && plan.beforeCount()==receipt.beforeItems && plan.afterCount()==receipt.afterItems
                && trader(receipt.quote).map(t -> t.goods.get(receipt.quote.sku)).map(s -> s.offer.item)
                    .filter(item -> item.equals(BuiltInRegistries.ITEM.getKey(plan.commodity().getItem()))).isPresent());
    }
    public Optional<MarketTransferMarker> marker(UUID id,MarketTransferMarker.Stage stage) {
        var receipt=receipts.get(id);
        return receipt==null || receipt.nativePlan==null || stage==null?Optional.empty():Optional.of(new MarketTransferMarker(transfer(receipt),stage));
    }
    /** Reconcile never reapplies goods or money. Both independently saved documents must agree exactly. */
    public Resolution resolveNative(UUID id,UUID actor,long balance,Container bag,Tag walletProof,Tag inventoryProof,HolderLookup.Provider registries) {
        var receipt=receipts.get(id);
        if(readOnly() || receipt==null || !receipt.quote.actor.equals(actor) || receipt.nativePlan==null) return Resolution.HELD;
        var wallet=MarketTransferMarker.fromNbt(walletProof); var inventory=MarketTransferMarker.fromNbt(inventoryProof);
        var expected=transfer(receipt); var plan=nativePlan(id,registries);
        if(wallet.isEmpty() || inventory.isEmpty() || plan.isEmpty() || !wallet.get().matches(expected)
                || !inventory.get().matches(expected)) return Resolution.HELD;
        boolean walletAfter=wallet.get().stage()==MarketTransferMarker.Stage.AFTER || wallet.get().stage()==MarketTransferMarker.Stage.COMMITTED;
        boolean inventoryAfter=inventory.get().stage()==MarketTransferMarker.Stage.AFTER || inventory.get().stage()==MarketTransferMarker.Stage.COMMITTED;
        if(walletAfter && inventoryAfter && balance==receipt.afterBalance && plan.get().matchesAfter(bag)
                && receipt.phase!=Phase.CANCELLED && commitChecked(receipt,balance,plan.get().afterCount())) return Resolution.COMMITTED;
        if(!walletAfter && !inventoryAfter && balance==receipt.beforeBalance && plan.get().matchesBefore(bag)
                && receipt.phase!=Phase.COMMITTED) {
            if(receipt.phase==Phase.CANCELLED || cancelChecked(receipt,balance,plan.get().beforeCount())) return Resolution.CANCELLED;
        }
        return Resolution.HELD;
    }
    /** Keep the reservation owner blocked and the old visit retained until both terminal markers exist. */
    public boolean acknowledgeNative(UUID id,UUID actor,Tag walletProof,Tag inventoryProof) {
        var receipt=receipts.get(id);
        if(readOnly() || receipt==null || receipt.nativePlan==null || !receipt.quote.actor.equals(actor)
                || receipt.phase!=Phase.COMMITTED && receipt.phase!=Phase.CANCELLED) return false;
        var stage=receipt.phase==Phase.COMMITTED?MarketTransferMarker.Stage.COMMITTED:MarketTransferMarker.Stage.CANCELLED;
        var expected=transfer(receipt); var wallet=MarketTransferMarker.fromNbt(walletProof); var inventory=MarketTransferMarker.fromNbt(inventoryProof);
        if(wallet.isEmpty() || inventory.isEmpty() || wallet.get().stage()!=stage || inventory.get().stage()!=stage
                || !wallet.get().matches(expected) || !inventory.get().matches(expected)) return false;
        if(!receipt.nativeAck) { receipt.nativeAck=true; pendingActors.remove(actor,id); state.setDirty(); }
        return true;
    }
    private static CompoundTag transfer(Receipt receipt) {
        var root=new CompoundTag(); var q=receipt.quote;
        root.putString("id",q.id.toString()); root.putString("actor",q.actor.toString()); root.putString("visit",q.visit.toString());
        root.putString("trader",q.trader.toString()); root.putString("sku",q.sku); root.putLong("price",q.price);
        root.putLong("beforeBalance",receipt.beforeBalance); root.putLong("afterBalance",receipt.afterBalance);
        root.put("plan",receipt.nativePlan.copy()); return root;
    }
    /** Native bridge supplies the actual saved home need/cycle; another observer cannot reset the same cycle. */
    public boolean observeNeed(UUID home,VillageNeed actual,long cycle) {
        var need=needs.get(home); if(need!=null && need.sourceOwner!=null) return false;
        return observeNeedChecked(home,actual,cycle);
    }
    public record HomeNeedSource(UUID owner,int index) {}
    public Optional<HomeNeedSource> homeNeedSource(UUID home) {
        var need=needs.get(home); return need==null || need.sourceOwner==null?Optional.empty():Optional.of(new HomeNeedSource(need.sourceOwner,need.sourceIndex));
    }
    /** Missing source data disables sales, without resetting or replacing saved demand counters. */
    public void invalidateHomeNeed(UUID home) { var need=needs.get(home); if(need!=null) need.sourceVerified=false; }
    public boolean observeHomeNeed(UUID home,UUID owner,VillageSnapshot actual) {
        if(readOnly() || owner==null || owner.equals(BuildingSessionPolicy.NONE)) return false;
        var previous=needs.get(home);
        if(previous!=null && previous.sourceOwner!=null && (!previous.sourceOwner.equals(owner)
                || actual!=null && previous.sourceIndex!=actual.index())) return false;
        invalidateHomeNeed(home);
        var village=state.village(home).orElse(null);
        if(village==null || actual==null || actual.index()<0 || actual.index()>=1024 || actual.revision()<0
                || actual.type()==null || actual.need()==null || actual.need().villageType()!=actual.type()
                || !village.legacyKey().dimension().equals("minecraft:overworld")
                || actual.x()!=village.legacyKey().anchorX() || actual.z()!=village.legacyKey().anchorZ()) return false;
        if(previous!=null && previous.sourceOwner!=null) {
            long difference=(long)actual.cyclesCompleted()-previous.cycle;
            if(actual.type()!=previous.sourceType || actual.revision()<previous.sourceRevision || difference<0
                    || difference>0 && actual.revision()<=previous.sourceRevision
                    || actual.need()!=(difference%2==0?previous.type:VillageNeed.next(previous.sourceType,previous.type))) return false;
        }
        if(!observeNeedChecked(home,actual.need(),actual.cyclesCompleted())) return false;
        var need=needs.get(home);
        boolean changed=need.sourceOwner==null || need.sourceRevision!=actual.revision();
        need.sourceOwner=owner; need.sourceIndex=actual.index(); need.sourceType=actual.type();
        need.sourceRevision=actual.revision(); need.sourceVerified=true;
        if(changed) state.setDirty(); return true;
    }
    private boolean observeNeedChecked(UUID home,VillageNeed actual,long cycle) {
        if(readOnly() || actual==null || cycle<0 || cycle>1_000_000 || state.village(home).isEmpty()) return false;
        var need=needs.get(home);
        if(need!=null && cycle==need.cycle) return need.type==actual;
        if(need!=null && (cycle<need.cycle || need.revision==Long.MAX_VALUE)
                || receipts.values().stream().anyMatch(r -> blocksReuse(r) && trader(r.quote).map(t -> t.home.equals(home)).orElse(false))) return false;
        if(need==null && needs.size()>=MAX_HOSTS) return false;
        var next=new Need(home,actual,cycle); if(need!=null) { next.revision=need.revision+1; next.retained=need.retained.copy(); }
        needs.put(home,next); state.setDirty(); return true;
    }
    /** Counts only consecutive loaded observations. Restart and long gaps never grant restocking time. */
    public void observe(UUID market,long now,boolean loaded) {
        var host=hosts.get(market); if(readOnly() || host==null || now<0) return;
        if(!loaded) { host.last=-1; return; }
        long delta=host.last>=0 && now>=host.last && now-host.last<=100?now-host.last:0;
        host.last=now;
        if(delta>0 && host.elapsed<6000) { host.elapsed=Math.min(6000,host.elapsed+delta); state.setDirty(); }
    }
    public Optional<VisitView> start(UUID market,List<TraderPlan> plans,long now) {
        if(readOnly() || now<0 || plans==null || plans.isEmpty() || plans.size()>5 || state.village(market).isEmpty()) return Optional.empty();
        var anchor=state.anchors().official(market,BuildingScanner.Role.MARKET);
        if(anchor.isEmpty() || !state.anchors().maySpawn(anchor.get().id(),now)) return Optional.empty();
        var host=hosts.get(market);
        if(host!=null && host.visit!=null && host.visit.open) return Optional.of(host.visit.view(host));
        UUID currentVisit=host==null || host.visit==null?null:host.visit.id;
        if(host!=null && (host.revision==Long.MAX_VALUE || host.visit!=null && (host.elapsed<6000
                || receipts.values().stream().anyMatch(r -> r.quote.visit.equals(currentVisit) && blocksReuse(r))))) return Optional.empty();
        if(host==null && hosts.size()>=MAX_HOSTS) return Optional.empty();
        var next=new Visit(UUID.randomUUID(),market);
        var plannedItems=new java.util.HashSet<>(priceFloors.keySet());
        for(var plan:plans) {
            if(state.village(plan.home).isEmpty() || state.people().person(plan.person).filter(p -> p.role()==KingdomPersonRegistry.Role.MARKET_TRADER && p.village().equals(plan.home)).isEmpty()
                    || next.traders.containsKey(plan.person)) return Optional.empty();
            var trader=new Trader(plan.person,plan.home);
            for(var goods:plan.goods) { if(trader.goods.putIfAbsent(goods.sku,new Stock(goods))!=null) return Optional.empty(); plannedItems.add(goods.item); }
            next.traders.put(plan.person,trader);
        }
        if(plannedItems.size()>256) return Optional.empty();
        if(host==null) { host=new Host(market); hosts.put(market,host); }
        if(host.visit!=null) {
            UUID old=host.visit.id; visits.remove(old);
            receipts.entrySet().removeIf(entry -> entry.getValue().quote.visit.equals(old));
        }
        host.visit=next; host.elapsed=0; host.last=-1; host.revision++; visits.put(next.id,next);
        for(var trader:next.traders.values()) for(var stock:trader.goods.values()) rememberFloor(stock.offer);
        state.setDirty(); return Optional.of(next.view(host));
    }
    public boolean close(UUID market) {
        var host=hosts.get(market);
        if(readOnly() || host==null || host.revision==Long.MAX_VALUE || host.visit==null || !host.visit.open) return false;
        host.visit.open=false; host.revision++; state.setDirty(); return true;
    }
    /** Resumes the same interrupted visit, preserving stock and money; only the native schedule calls this. */
    public boolean resume(UUID market) {
        var host=hosts.get(market);
        if(readOnly() || host==null || host.revision==Long.MAX_VALUE || host.visit==null || host.visit.open) return false;
        host.visit.open=true; host.revision++; state.setDirty(); return true;
    }
    /** Production entry: current shared source proof is mandatory for every new buyback quote. */
    public Optional<Quote> quote(UUID actor,UUID visitId,UUID person,String sku,Side side,int lots,int factor,int discount,long now) {
        if(side==Side.SELL && !state.marketSources().ready()) return Optional.empty();
        var visit=visits.get(visitId); if(visit==null) return Optional.empty();
        var trader=visit.traders.get(person); if(trader==null) return Optional.empty();
        var stock=trader.goods.get(sku); if(stock==null) return Optional.empty();
        return quote(actor,visitId,person,sku,side,lots,factor,discount,
                side==Side.SELL ? List.of(state.marketSources().source(stock.offer.item)) : List.of(),now);
    }
    /** Final new-native-prepare bound, also protects against an internal quote producer omitting sources. */
    public boolean sourceAllows(Quote quote) {
        if(readOnly() || quote==null || quote.side!=Side.SELL || !quote.equals(quotes.get(quote.id))
                || !state.marketSources().ready()) return false;
        var visit=visits.get(quote.visit); if(visit==null) return false;
        var trader=visit.traders.get(quote.trader); if(trader==null) return false;
        var stock=trader.goods.get(quote.sku); if(stock==null) return false;
        var source=state.marketSources().source(stock.offer.item);
        return source.price()>0 && java.math.BigInteger.valueOf(quote.price).multiply(java.math.BigInteger.valueOf(source.quantity()))
                .compareTo(java.math.BigInteger.valueOf(source.price()).multiply(java.math.BigInteger.valueOf(quote.quantity)))<0;
    }
    public Optional<Quote> quote(UUID actor,UUID visitId,UUID person,String sku,Side side,int lots,int factor,int discount,
                                 List<MarketPricePolicy.Source> otherSources,long now) {
        if(readOnly() || actor==null || actor.equals(BuildingSessionPolicy.NONE) || side==null || now<0 || lots<1 || lots>128
                || otherSources==null || otherSources.size()>63 || otherSources.stream().anyMatch(java.util.Objects::isNull)) return Optional.empty();
        var visit=visits.get(visitId); if(visit==null || !visit.open) return Optional.empty();
        var host=hosts.get(visit.market); var trader=visit.traders.get(person);
        if(host.revision==Long.MAX_VALUE || trader==null || !state.marketTravel().mayTrade(visit.market,person,now)) return Optional.empty();
        var stock=trader.goods.get(sku); if(stock==null || stock.offer.lot*lots>128) return Optional.empty();
        var goods=stock.offer; var need=needs.get(trader.home); int quantity=goods.lot*lots;
        if(side==Side.BUY && stock.remaining<lots || side==Side.SELL && (need==null || need.sourceOwner!=null && !need.sourceVerified || need.revision==Long.MAX_VALUE
                || !matches(need,goods.item) || stock.bought+lots>goods.demand || need.accepted.getOrDefault(goods.item,0)+quantity>128)) return Optional.empty();
        var bounds=new ArrayList<>(otherSources); var global=priceFloors.get(goods.item); if(global!=null) bounds.add(global);
        long perLot=side==Side.BUY?MarketPricePolicy.sell(goods.sell,factor,discount):MarketPricePolicy.buy(goods.buy,goods.lot,factor,goods.sell,bounds);
        long price=perLot*lots; if(price<=0 || side==Side.SELL && price>trader.budget) return Optional.empty();
        quotes.entrySet().removeIf(entry -> now>entry.getValue().expires);
        if(quotes.size()>=MAX_QUOTES || now>Long.MAX_VALUE-200) return Optional.empty();
        var result=new Quote(UUID.randomUUID(),actor,visit.id,person,sku,side,lots,quantity,price,host.revision,side==Side.SELL?need.revision:0,now+200);
        quotes.put(result.id,result); return Optional.of(result);
    }
    public Optional<ReceiptView> prepare(Quote quote,UUID actor,long balance,int owned,boolean room,long now) {
        if(readOnly() || quote==null || actor==null || !actor.equals(quote.actor)) return Optional.empty();
        var existing=receipts.get(quote.id);
        if(existing!=null) return existing.quote.equals(quote)?Optional.of(existing.view()):Optional.empty();
        if(!quote.equals(quotes.get(quote.id)) || now<0 || now>quote.expires || balance<0 || owned<0 || owned>4096
                || pendingActors.containsKey(actor) || receipts.size()>=MAX_RECEIPTS) return Optional.empty();
        var visit=visits.get(quote.visit); if(visit==null || !visit.open) return Optional.empty();
        var host=hosts.get(visit.market); var trader=visit.traders.get(quote.trader);
        if(host.revision!=quote.revision || host.revision==Long.MAX_VALUE || trader==null || !state.marketTravel().mayTrade(visit.market,quote.trader,now)) return Optional.empty();
        var stock=trader.goods.get(quote.sku); var need=needs.get(trader.home); if(stock==null) return Optional.empty();
        long afterBalance; int afterItems;
        if(quote.side==Side.BUY) {
            if(!room || balance<quote.price || stock.remaining<quote.lots || owned+quote.quantity>4096) return Optional.empty();
            afterBalance=balance-quote.price; afterItems=owned+quote.quantity; stock.remaining-=quote.lots;
        } else {
            if(owned<quote.quantity || balance>Long.MAX_VALUE-quote.price || trader.budget<quote.price || need==null || need.sourceOwner!=null && !need.sourceVerified
                    || need.revision!=quote.needRevision || need.revision==Long.MAX_VALUE || !matches(need,stock.offer.item)
                    || need.accepted.getOrDefault(stock.offer.item,0)+quote.quantity>128 || stock.bought+quote.lots>stock.offer.demand) return Optional.empty();
            afterBalance=balance+quote.price; afterItems=owned-quote.quantity; trader.budget-=Math.toIntExact(quote.price); stock.bought+=quote.lots;
            need.accepted.merge(stock.offer.item,quote.quantity,Integer::sum); need.revision++;
        }
        var receipt=new Receipt(quote,balance,afterBalance,owned,afterItems); receipts.put(quote.id,receipt); pendingActors.put(actor,quote.id);
        host.revision++; state.setDirty(); return Optional.of(receipt.view());
    }
    public boolean commit(UUID id,long balance,int owned) {
        var receipt=receipts.get(id); if(readOnly() || receipt==null || receipt.nativePlan!=null) return false;
        return commitChecked(receipt,balance,owned);
    }
    private boolean commitChecked(Receipt receipt,long balance,int owned) {
        var id=receipt.quote.id; if(receipt.phase==Phase.CANCELLED) return false;
        if(receipt.phase==Phase.COMMITTED) return true;
        if(balance!=receipt.afterBalance || owned!=receipt.afterItems) {
            if(balance!=receipt.beforeBalance || owned!=receipt.beforeItems) { receipt.phase=Phase.CONFLICT; state.setDirty(); }
            return false;
        }
        receipt.phase=Phase.COMMITTED; if(receipt.nativePlan==null) pendingActors.remove(receipt.quote.actor,id); state.setDirty(); return true;
    }
    public boolean cancel(UUID id,long balance,int owned) {
        var receipt=receipts.get(id);
        if(readOnly() || receipt==null || receipt.nativePlan!=null) return false;
        return cancelChecked(receipt,balance,owned);
    }
    private boolean cancelChecked(Receipt receipt,long balance,int owned) {
        var id=receipt.quote.id;
        if(!unresolved(receipt) || balance!=receipt.beforeBalance || owned!=receipt.beforeItems) return false;
        var visit=visits.get(receipt.quote.visit); if(visit==null) return false;
        var host=hosts.get(visit.market); if(host.revision==Long.MAX_VALUE) return false;
        var trader=visit.traders.get(receipt.quote.trader); var stock=trader.goods.get(receipt.quote.sku);
        if(receipt.quote.side==Side.BUY) stock.remaining+=receipt.quote.lots;
        else {
            var need=needs.get(trader.home); if(need==null || need.revision==Long.MAX_VALUE) return false;
            trader.budget+=Math.toIntExact(receipt.quote.price); stock.bought-=receipt.quote.lots;
            need.accepted.merge(stock.offer.item,-receipt.quote.quantity,Integer::sum); need.revision++;
        }
        receipt.phase=Phase.CANCELLED; if(receipt.nativePlan==null) pendingActors.remove(receipt.quote.actor,id); host.revision++; state.setDirty(); return true;
    }
    private Optional<Trader> trader(Quote quote) { return Optional.ofNullable(visits.get(quote.visit)).map(v -> v.traders.get(quote.trader)); }
    private static boolean unresolved(Receipt r) { return r.phase==Phase.RESERVED || r.phase==Phase.CONFLICT; }
    private static boolean blocksReuse(Receipt r) { return unresolved(r) || r.nativePlan!=null && !r.nativeAck; }
    private static boolean matches(Need need,Identifier item) { return need.type.matches(BuiltInRegistries.ITEM.getValue(item)); }
    private void rememberFloor(Goods goods) {
        if(goods.stock==0) return;
        var source=new MarketPricePolicy.Source(MarketPricePolicy.sell(goods.sell,-15,15),goods.lot); var previous=priceFloors.get(goods.item);
        if(previous==null || source.price()*previous.quantity()<previous.price()*source.quantity()) priceFloors.put(goods.item,source);
    }

    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",-1)!=1) { opaque=true; return; }
        try {
            for(var tag:rows(root,"hosts",MAX_HOSTS)) {
                var host=new Host(uuid(tag,"market")); if(state.village(host.market).isEmpty() || hosts.containsKey(host.market)) throw bad();
                host.retained=tag.copy(); host.revision=number(tag,"revision",0,Long.MAX_VALUE); host.elapsed=number(tag,"elapsed",0,6000);
                var v=tag.getCompound("visit").orElseThrow(KingdomMarketLedger::bad); var visit=new Visit(uuid(v,"id"),host.market);
                visit.retained=v.copy(); visit.open=bool(v,"open");
                for(var t:rows(v,"traders",5)) {
                    var trader=new Trader(uuid(t,"person"),uuid(t,"home")); trader.retained=t.copy(); trader.budget=integer(t,"budget",0,16);
                    if(state.village(trader.home).isEmpty() || state.people().person(trader.person).filter(p -> p.role()==KingdomPersonRegistry.Role.MARKET_TRADER && p.village().equals(trader.home)).isEmpty()
                            || visit.traders.containsKey(trader.person)) throw bad();
                    for(var g:rows(t,"goods",8)) {
                        var goods=new Goods(g.getStringOr("sku",""),item(g,"item"),integer(g,"lot",1,64),integer(g,"initialStock",0,128),
                                integer(g,"demand",0,128),number(g,"sell",1,1_000_000_000),number(g,"buy",0,1_000_000_000));
                        var stock=new Stock(goods); stock.retained=g.copy(); stock.remaining=integer(g,"remaining",0,goods.stock); stock.bought=integer(g,"bought",0,goods.demand);
                        if(trader.goods.putIfAbsent(goods.sku,stock)!=null) throw bad();
                    }
                    if(trader.goods.isEmpty()) throw bad(); visit.traders.put(trader.person,trader);
                }
                if(visit.traders.isEmpty() || visits.putIfAbsent(visit.id,visit)!=null) throw bad(); host.visit=visit; hosts.put(host.market,host);
            }
            for(var tag:rows(root,"needs",MAX_HOSTS)) {
                var need=new Need(uuid(tag,"home"),VillageNeed.valueOf(tag.getStringOr("type","")),number(tag,"cycle",0,1_000_000));
                need.retained=tag.copy(); need.revision=number(tag,"revision",0,Long.MAX_VALUE);
                if(state.village(need.home).isEmpty() || needs.containsKey(need.home)) throw bad();
                if(tag.contains("source")) {
                    if(!(tag.get("source") instanceof CompoundTag source) || integer(source,"schema",1,1)!=1) throw bad();
                    need.sourceOwner=uuid(source,"owner"); need.sourceIndex=integer(source,"index",0,1023);
                    need.sourceRevision=integer(source,"revision",0,Integer.MAX_VALUE);
                    need.sourceType=VillageBondType.valueOf(source.getStringOr("type",""));
                    if(need.sourceType!=need.type.villageType()) throw bad();
                }
                for(var amount:rows(tag,"accepted",32)) {
                    var item=item(amount,"item");
                    if(need.accepted.putIfAbsent(item,integer(amount,"amount",0,128))!=null) throw bad();
                    need.acceptedPayloads.put(item,amount.copy());
                }
                needs.put(need.home,need);
            }
            for(var tag:rows(root,"floors",256)) {
                var item=item(tag,"item"); var source=new MarketPricePolicy.Source(number(tag,"price",0,1_000_000_000),integer(tag,"quantity",1,4096));
                if(priceFloors.putIfAbsent(item,source)!=null) throw bad(); floorPayloads.put(item,tag.copy());
            }
            for(var tag:rows(root,"receipts",MAX_RECEIPTS)) {
                var q=new Quote(uuid(tag,"id"),uuid(tag,"actor"),uuid(tag,"visit"),uuid(tag,"trader"),tag.getStringOr("sku",""),
                        Side.valueOf(tag.getStringOr("side","")),integer(tag,"lots",1,128),integer(tag,"quantity",1,128),number(tag,"price",1,128_000_000_000L),
                        number(tag,"revision",0,Long.MAX_VALUE),number(tag,"needRevision",0,Long.MAX_VALUE),number(tag,"expires",0,Long.MAX_VALUE));
                var receipt=new Receipt(q,number(tag,"beforeBalance",0,Long.MAX_VALUE),number(tag,"afterBalance",0,Long.MAX_VALUE),integer(tag,"beforeItems",0,4096),integer(tag,"afterItems",0,4096));
                receipt.retained=tag.copy(); receipt.phase=Phase.valueOf(tag.getStringOr("phase",""));
                if(tag.contains("nativePlan")) {
                    if(!(tag.get("nativePlan") instanceof CompoundTag plan) || !(plan.get("schema") instanceof IntTag)
                            || plan.getIntOr("schema",-1)!=1 || !(plan.get("quantity") instanceof IntTag)
                            || plan.getIntOr("quantity",-1)!=q.quantity || !plan.getStringOr("side","").equals(q.side.name())) throw bad();
                    for(String key:List.of("before","after")) {
                        if(!(plan.get(key) instanceof ListTag slots) || slots.size()!=36) throw bad();
                        for(var slot:slots) if(!(slot instanceof CompoundTag)) throw bad();
                    }
                    if(!(plan.get("commodity") instanceof CompoundTag) || !(plan.get("limits") instanceof ListTag limits) || limits.size()!=36) throw bad();
                    for(var limit:limits) if(!(limit instanceof IntTag value) || value.intValue()<0 || value.intValue()>64) throw bad();
                    receipt.nativePlan=plan.copy(); receipt.nativeAck=bool(tag,"nativeAck");
                    if(receipt.nativeAck && unresolved(receipt)) throw bad();
                } else if(tag.contains("nativeAck")) throw bad();
                var trader=trader(q).orElseThrow(KingdomMarketLedger::bad); var stock=trader.goods.get(q.sku);
                if(stock==null || q.lots*stock.offer.lot!=q.quantity || q.side==Side.BUY && (receipt.beforeBalance<q.price || receipt.afterBalance!=receipt.beforeBalance-q.price || receipt.afterItems!=receipt.beforeItems+q.quantity)
                        || q.side==Side.SELL && (receipt.beforeBalance>Long.MAX_VALUE-q.price || receipt.afterBalance!=receipt.beforeBalance+q.price || receipt.afterItems!=receipt.beforeItems-q.quantity)
                        || receipts.putIfAbsent(q.id,receipt)!=null || blocksReuse(receipt) && pendingActors.putIfAbsent(q.actor,q.id)!=null) throw bad();
                if(unresolved(receipt)) {
                    if(q.side==Side.BUY && stock.remaining+q.lots>stock.offer.stock) throw bad();
                    var need=needs.get(trader.home);
                    if(q.side==Side.SELL && (stock.bought<q.lots || trader.budget>16-q.price || need==null
                            || need.accepted.getOrDefault(stock.offer.item,0)<q.quantity)) throw bad();
                }
            }
            // Validate all receipts together: two reservations need two deductions, not one deduction checked twice.
            var totals=new java.util.IdentityHashMap<Stock,long[]>(); var spent=new java.util.IdentityHashMap<Trader,Long>();
            for(var receipt:receipts.values()) {
                if(receipt.phase==Phase.CANCELLED) continue;
                var t=trader(receipt.quote).orElseThrow(KingdomMarketLedger::bad); var stock=t.goods.get(receipt.quote.sku);
                var amounts=totals.computeIfAbsent(stock,ignored -> new long[2]);
                if(receipt.quote.side==Side.BUY) amounts[0]+=receipt.quote.lots;
                else { amounts[1]+=receipt.quote.lots; spent.merge(t,receipt.quote.price,Long::sum); }
            }
            for(var host:hosts.values()) for(var t:host.visit.traders.values()) {
                for(var stock:t.goods.values()) {
                    var amounts=totals.getOrDefault(stock,new long[2]);
                    if(amounts[0]!=stock.offer.stock-stock.remaining || amounts[1]!=stock.bought) throw bad();
                }
                if(spent.getOrDefault(t,0L)!=16-t.budget) throw bad();
            }
            for(var host:hosts.values()) for(var trader:host.visit.traders.values()) for(var stock:trader.goods.values()) rememberFloor(stock.offer);
        } catch(IllegalArgumentException bad) { opaque=true; }
    }
    Tag toNbt() {
        if(readOnly()) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var hostRows=new ListTag();
        hosts.values().stream().sorted(java.util.Comparator.comparing(h -> h.market)).forEach(host -> {
            var h=host.retained.copy(); h.putString("market",host.market.toString()); h.putLong("revision",host.revision); h.putLong("elapsed",host.elapsed);
            var v=host.visit.retained.copy(); v.putString("id",host.visit.id.toString()); v.putBoolean("open",host.visit.open); var traders=new ListTag();
            for(var trader:host.visit.traders.values()) {
                var t=trader.retained.copy(); t.putString("person",trader.person.toString()); t.putString("home",trader.home.toString()); t.putInt("budget",trader.budget); var goods=new ListTag();
                for(var stock:trader.goods.values()) {
                    var g=stock.retained.copy(); var offer=stock.offer; g.putString("sku",offer.sku); g.putString("item",offer.item.toString()); g.putInt("lot",offer.lot);
                    g.putInt("initialStock",offer.stock); g.putInt("demand",offer.demand); g.putLong("sell",offer.sell); g.putLong("buy",offer.buy); g.putInt("remaining",stock.remaining); g.putInt("bought",stock.bought); goods.add(g);
                }
                t.put("goods",goods); traders.add(t);
            }
            v.put("traders",traders); h.put("visit",v); hostRows.add(h);
        }); root.put("hosts",hostRows);
        var needRows=new ListTag(); needs.values().stream().sorted(java.util.Comparator.comparing(n -> n.home)).forEach(need -> {
            var n=need.retained.copy(); n.putString("home",need.home.toString()); n.putString("type",need.type.name()); n.putLong("cycle",need.cycle); n.putLong("revision",need.revision); var amounts=new ListTag();
            need.accepted.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> { var a=need.acceptedPayloads.getOrDefault(entry.getKey(),new CompoundTag()).copy(); a.putString("item",entry.getKey().toString()); a.putInt("amount",entry.getValue()); amounts.add(a); });
            if(need.sourceOwner!=null) {
                var source=n.getCompound("source").map(CompoundTag::copy).orElseGet(CompoundTag::new);
                source.putInt("schema",1); source.putString("owner",need.sourceOwner.toString()); source.putInt("index",need.sourceIndex);
                source.putInt("revision",need.sourceRevision); source.putString("type",need.sourceType.name()); n.put("source",source);
            }
            n.put("accepted",amounts); needRows.add(n);
        }); root.put("needs",needRows);
        var floors=new ListTag(); priceFloors.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var f=floorPayloads.getOrDefault(entry.getKey(),new CompoundTag()).copy(); f.putString("item",entry.getKey().toString()); f.putLong("price",entry.getValue().price()); f.putInt("quantity",entry.getValue().quantity()); floors.add(f);
        }); root.put("floors",floors);
        var receiptRows=new ListTag(); for(var receipt:receipts.values()) {
            var r=receipt.retained.copy(); var q=receipt.quote; r.putString("id",q.id.toString()); r.putString("actor",q.actor.toString()); r.putString("visit",q.visit.toString()); r.putString("trader",q.trader.toString());
            r.putString("sku",q.sku); r.putString("side",q.side.name()); r.putInt("lots",q.lots); r.putInt("quantity",q.quantity); r.putLong("price",q.price); r.putLong("revision",q.revision);
            r.putLong("needRevision",q.needRevision); r.putLong("expires",q.expires); r.putLong("beforeBalance",receipt.beforeBalance); r.putLong("afterBalance",receipt.afterBalance);
            r.putInt("beforeItems",receipt.beforeItems); r.putInt("afterItems",receipt.afterItems); r.putString("phase",receipt.phase.name());
            if(receipt.nativePlan!=null) { r.put("nativePlan",receipt.nativePlan.copy()); r.putBoolean("nativeAck",receipt.nativeAck); }
            receiptRows.add(r);
        } root.put("receipts",receiptRows); return root;
    }
    private static List<CompoundTag> rows(CompoundTag parent,String key,int cap) {
        if(!(parent.get(key) instanceof ListTag list) || list.size()>cap) throw bad();
        var result=new ArrayList<CompoundTag>(); for(var value:list) { if(!(value instanceof CompoundTag row)) throw bad(); result.add(row); } return result;
    }
    private static IllegalArgumentException bad() { return new IllegalArgumentException("Invalid saved market"); }
    private static UUID uuid(CompoundTag tag,String key) { UUID value=UUID.fromString(tag.getStringOr(key,"")); if(value.equals(BuildingSessionPolicy.NONE)) throw bad(); return value; }
    private static Identifier item(CompoundTag tag,String key) { var value=Identifier.tryParse(tag.getStringOr(key,"")); if(value==null || !value.getNamespace().equals("minecraft")) throw bad(); return value; }
    private static long number(CompoundTag tag,String key,long min,long max) { if(!(tag.get(key) instanceof LongTag)) throw bad(); long value=tag.getLongOr(key,min-1); if(value<min || value>max) throw bad(); return value; }
    private static int integer(CompoundTag tag,String key,int min,int max) { if(!(tag.get(key) instanceof IntTag)) throw bad(); int value=tag.getIntOr(key,min-1); if(value<min || value>max) throw bad(); return value; }
    private static boolean bool(CompoundTag tag,String key) { if(!(tag.get(key) instanceof ByteTag b) || b.byteValue()!=0 && b.byteValue()!=1) throw bad(); return b.byteValue()==1; }
    private static final class Host { final UUID market; long revision,elapsed,last=-1; Visit visit; CompoundTag retained=new CompoundTag(); Host(UUID market) { this.market=market; } }
    private static final class Visit {
        final UUID id,market; boolean open=true; final Map<UUID,Trader> traders=new LinkedHashMap<>(); CompoundTag retained=new CompoundTag();
        Visit(UUID id,UUID market) { this.id=id; this.market=market; }
        VisitView view(Host host) { return new VisitView(id,market,host.revision,open,traders.values().stream().map(t -> new TraderView(t.person,t.home,t.budget,t.goods.values().stream().map(s -> new GoodsView(s.offer,s.remaining,s.bought)).toList())).toList()); }
    }
    private static final class Trader { final UUID person,home; int budget=16; final Map<String,Stock> goods=new LinkedHashMap<>(); CompoundTag retained=new CompoundTag(); Trader(UUID person,UUID home) { this.person=person; this.home=home; } }
    private static final class Stock { final Goods offer; int remaining,bought; CompoundTag retained=new CompoundTag(); Stock(Goods offer) { this.offer=offer; remaining=offer.stock; } }
    private static final class Need {
        final UUID home; final VillageNeed type; final long cycle; long revision;
        UUID sourceOwner; int sourceIndex,sourceRevision; VillageBondType sourceType; boolean sourceVerified;
        final Map<Identifier,Integer> accepted=new HashMap<>(); final Map<Identifier,CompoundTag> acceptedPayloads=new HashMap<>();
        CompoundTag retained=new CompoundTag(); Need(UUID home,VillageNeed type,long cycle) { this.home=home; this.type=type; this.cycle=cycle; }
    }
    private static final class Receipt {
        final Quote quote; final long beforeBalance,afterBalance; final int beforeItems,afterItems; Phase phase=Phase.RESERVED; CompoundTag retained=new CompoundTag();
        CompoundTag nativePlan; boolean nativeAck;
        Receipt(Quote q,long before,long after,int items,int next) { quote=q; beforeBalance=before; afterBalance=after; beforeItems=items; afterItems=next; }
        ReceiptView view() { return new ReceiptView(quote.id,quote,beforeBalance,afterBalance,beforeItems,afterItems,phase); }
    }
}
