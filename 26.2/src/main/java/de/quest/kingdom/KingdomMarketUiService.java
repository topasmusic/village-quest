package de.quest.kingdom;

import de.quest.economy.CurrencyService;
import de.quest.economy.ProsperityBranch;
import de.quest.economy.ProsperityService;
import de.quest.entity.KingdomNpcEntity;
import de.quest.network.MarketPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Thin live adapter: private displayed quotes feed the existing native receipt/currency/inventory bridge. */
public final class KingdomMarketUiService {
    private static final int MAX_SESSIONS=32;
    private static final Map<MinecraftServer,Map<UUID,Desk>> RUNTIMES=new WeakHashMap<>();
    private KingdomMarketUiService() {}
    private static final class Desk {
        final MarketSessionPolicy policy;
        MarketPayloads.View last;
        long sent=-1,recovered=-1;
        Desk(MarketSessionPolicy policy) { this.policy=policy; }
    }
    private record Context(ServerLevel world,LivingKingdomsState state,KingdomNpcEntity npc,
                           KingdomPersonRegistry.View person,KingdomMarketLedger.VisitView visit) {}
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(KingdomMarketUiService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> close(server,handler.player,false));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNTIMES.remove(server));
    }
    public static boolean open(ServerLevel world,ServerPlayer actor,KingdomNpcEntity npc) {
        if(world==null || actor==null || npc==null || !world.getServer().isSameThread()) return false;
        var context=context(actor,npc); if(context.isEmpty() || context.get().world!=world || !ServerPlayNetworking.canSend(actor,MarketPayloads.View.ID)) return false;
        var server=world.getServer(); var desks=RUNTIMES.computeIfAbsent(server,key -> new HashMap<>());
        if(desks.size()>=MAX_SESSIONS && !desks.containsKey(actor.getUUID())) {
            actor.sendSystemMessage(Component.translatable("screen.village-quest.market.busy"),false); return false;
        }
        close(server,actor,false);
        KingdomMarketTransferService.reconcilePlayer(actor);
        var current=context.get(); long now=server.overworld().getGameTime();
        var policy=new MarketSessionPolicy(actor.getUUID(),world.dimension().identifier().toString(),current.person.id(),npc.getUUID(),
                current.person.generation(),current.visit.id(),now);
        var desk=new Desk(policy); desks.put(actor.getUUID(),desk);
        refresh(actor,current,desk,true,MarketPayloads.Feedback.UPDATED); return true;
    }
    public static void handle(MinecraftServer server,ServerPlayer actor,MarketPayloads.Action action) {
        if(server==null || actor==null || action==null || !server.isSameThread()) return;
        if(actor.level().getServer()!=server) return;
        var desks=RUNTIMES.get(server); var desk=desks==null?null:desks.get(actor.getUUID());
        if(desk==null || !desk.policy.id().equals(action.session())) return;
        if(action.kind()==MarketPayloads.Kind.CLOSE) { close(server,actor,false); return; }
        var live=context(actor,desk); if(live.isEmpty()) { close(server,actor,true); return; }
        var current=live.get(); var policy=desk.policy; long now=server.overworld().getGameTime();
        var access=policy.validate(actor.getUUID(),action.session(),current.world.dimension().identifier().toString(),
                current.npc.getUUID(),current.person.generation(),now,action.revision());
        if(access==MarketSessionPolicy.Access.INVALID || access==MarketSessionPolicy.Access.EXPIRED) { close(server,actor,true); return; }
        if(access==MarketSessionPolicy.Access.STALE) { resend(actor,desk); return; }
        var feedback=MarketPayloads.Feedback.UPDATED;
        if(action.kind()==MarketPayloads.Kind.LOTS) policy.setLots(1);
        else if(action.kind()==MarketPayloads.Kind.REFRESH) {
            // Recovery may save independent documents. A refresh flood must not force repeated IO.
            if(desk.recovered<0 || now-desk.recovered>=20) { desk.recovered=now; KingdomMarketTransferService.reconcilePlayer(actor); }
        } else if(action.kind()==MarketPayloads.Kind.TRADE) {
            var anchor=current.state.anchors().official(current.visit.market(),BuildingScanner.Role.MARKET);
            if(anchor.isEmpty() || !current.state.anchors().mayTrade(anchor.get().id(),now)) {
                refresh(actor,current,desk,false,MarketPayloads.Feedback.CHECKING); return;
            }
            var quote=policy.take(actor.getUUID(),action.session(),current.world.dimension().identifier().toString(),
                    current.npc.getUUID(),current.person.generation(),now,action.revision(),action.quote());
            if(quote.isEmpty()) feedback=MarketPayloads.Feedback.EXPIRED;
            else {
                var offer=current.visit.traders().stream().filter(trader -> trader.person().equals(policy.trader()))
                        .flatMap(trader -> trader.goods().stream()).filter(goods -> goods.offer().sku().equals(quote.get().sku())).findFirst();
                var item=offer.map(goods -> BuiltInRegistries.ITEM.getValue(goods.offer().item())).orElse(Items.AIR);
                var plan=item==Items.AIR?Optional.<MarketInventoryPlan>empty():MarketInventoryPlan.plan(actor.getInventory(),
                        new ItemStack(item),quote.get().side(),quote.get().quantity());
                if(plan.isEmpty()) feedback=MarketPayloads.Feedback.INVENTORY;
                else feedback=switch(KingdomMarketTransferService.execute(actor,quote.get(),plan.get())) {
                    case COMMITTED -> MarketPayloads.Feedback.COMMITTED;
                    case CANCELLED -> MarketPayloads.Feedback.CANCELLED;
                    case HELD -> MarketPayloads.Feedback.HELD;
                    case REJECTED -> MarketPayloads.Feedback.REJECTED;
                };
            }
        }
        refresh(actor,current,desk,false,feedback);
    }
    /** Pure saved-state projection, also exercised without a server/world. It creates only ephemeral quotes. */
    static Optional<MarketPayloads.View> snapshot(LivingKingdomsState state,MarketSessionPolicy policy,boolean initial,
                                                long balance,int discount,long now,boolean buybacks,MarketPayloads.Feedback feedback) {
        return snapshot(state,policy,initial,balance,discount,now,buybacks,0,feedback);
    }
    static Optional<MarketPayloads.View> snapshot(LivingKingdomsState state,MarketSessionPolicy policy,boolean initial,
                                                long balance,int discount,long now,boolean buybacks,int bondLevel,MarketPayloads.Feedback feedback) {
        return snapshot(state,policy,initial,balance,discount,now,buybacks,bondLevel,0,0,feedback);
    }
    static Optional<MarketPayloads.View> snapshot(LivingKingdomsState state,MarketSessionPolicy policy,boolean initial,
            long balance,int discount,long now,boolean buybacks,int bondLevel,int reputation,long departureTicks,MarketPayloads.Feedback feedback) {
        if(state==null || policy==null || state.markets().readOnly()) return Optional.empty();
        var ledger=state.markets(); var visit=ledger.visitById(policy.visit()).orElse(null);
        var person=state.people().person(policy.trader()).orElse(null);
        if(visit==null || !visit.open() || person==null || person.role()!=KingdomPersonRegistry.Role.MARKET_TRADER) return Optional.empty();
        var trader=visit.traders().stream().filter(value -> value.person().equals(policy.trader())).findFirst().orElse(null);
        var home=trader==null?null:state.village(trader.home()).orElse(null); var host=state.village(visit.market()).orElse(null);
        if(trader==null || home==null || host==null || trader.goods().size()>8 || !trader.home().equals(person.village())) return Optional.empty();
        ledger.discardQuotes(policy.owner()); int factor=KingdomMarketCatalog.priceFactor(person);
        var quotes=new ArrayList<KingdomMarketLedger.Quote>(); var rows=new ArrayList<MarketPayloads.Row>();
        boolean pending=!ledger.pending(policy.owner()).isEmpty();
        for(var stock:trader.goods()) {
            var goods=stock.offer();
            int demand=ledger.remainingDemand(visit.id(),trader.person(),goods.sku());
            var adjustment=MarketPricePolicy.adjust(factor,goods.stock()==0?1:stock.stock(),Math.max(1,goods.stock()),demand,bondLevel,reputation);
            var buy=pending?Optional.<KingdomMarketLedger.Quote>empty():ledger.quote(policy.owner(),visit.id(),trader.person(),goods.sku(),
                    KingdomMarketLedger.Side.BUY,policy.lots(),adjustment.buying(),discount,now);
            var sell=pending || !buybacks?Optional.<KingdomMarketLedger.Quote>empty():ledger.quote(policy.owner(),visit.id(),trader.person(),goods.sku(),
                    KingdomMarketLedger.Side.SELL,policy.lots(),adjustment.selling(),discount,now);
            buy.ifPresent(quotes::add); sell.ifPresent(quotes::add);
            rows.add(new MarketPayloads.Row(goods.sku(),goods.item(),goods.lot(),stock.stock(),demand,adjustment,
                    buy.map(KingdomMarketLedger.Quote::id).orElse(MarketPayloads.NONE),buy.map(KingdomMarketLedger.Quote::price).orElse(0L),
                    sell.map(KingdomMarketLedger.Quote::id).orElse(MarketPayloads.NONE),sell.map(KingdomMarketLedger.Quote::price).orElse(0L)));
        }
        policy.offer(quotes);
        boolean request=trader.goods().stream().anyMatch(g -> g.offer().buy()>0 && g.offer().demand()>0);
        boolean remaining=trader.goods().stream().anyMatch(g -> g.offer().buy()>0 && ledger.remainingDemand(visit.id(),trader.person(),g.offer().sku())>0);
        var shown=pending?MarketPayloads.Feedback.HELD:
                feedback!=MarketPayloads.Feedback.UPDATED?feedback:
                !buybacks || !request?MarketPayloads.Feedback.BUYBACKS_UNAVAILABLE:
                !remaining?MarketPayloads.Feedback.NEED_EXHAUSTED:
                trader.budget()==0?MarketPayloads.Feedback.BUDGET_EXHAUSTED:feedback;
        return Optional.of(new MarketPayloads.View(policy.id(),policy.revision(),initial,true,person.name(),home.name(),host.name(),
                policy.lots(),balance,trader.budget(),factor,discount,rows,shown,departureTicks));
    }
    private static Optional<Context> context(ServerPlayer actor,Desk desk) {
        if(!actor.level().getServer().isSameThread()) return Optional.empty();
        var body=actor.level().getEntity(desk.policy.body());
        return body instanceof KingdomNpcEntity npc?context(actor,npc).filter(current -> current.person.id().equals(desk.policy.trader())
                && current.person.generation()==desk.policy.generation() && current.visit.id().equals(desk.policy.visit())):Optional.empty();
    }
    private static Optional<Context> context(ServerPlayer actor,KingdomNpcEntity npc) {
        var world=actor.level(); var server=world.getServer();
        if(!server.isSameThread() || !actor.isAlive() || actor.isRemoved() || actor.isSpectator() || npc.level()!=world
                || !npc.isAlive() || npc.isRemoved() || actor.distanceToSqr(npc)>64 || !world.hasChunkAt(npc.blockPosition())) return Optional.empty();
        var state=LivingKingdomsState.get(server); if(state.markets().readOnly()) return Optional.empty();
        var person=KingdomNpcService.available(world,npc).filter(value -> value.role()==KingdomPersonRegistry.Role.MARKET_TRADER).orElse(null);
        if(person==null) return Optional.empty();
        if(!KingdomNpcService.tradeAllowed(world,actor,person.id(),person.visit()==null?person.village():person.visit())) return Optional.empty();
        var market=person.presence()==KingdomPersonRegistry.Presence.VISITING?person.visit():person.village();
        var visit=state.markets().visit(market).orElse(null); if(visit==null || !visit.open()
                || visit.traders().stream().noneMatch(trader -> trader.person().equals(person.id()) && trader.home().equals(person.village()))) return Optional.empty();
        var anchor=state.anchors().official(market,BuildingScanner.Role.MARKET);
        if(anchor.isEmpty() || !state.anchors().mayContinue(anchor.get().id(),server.overworld().getGameTime())
                || state.marketDays().place(market,person.id(),server.overworld().getGameTime()).isEmpty()
                || state.anchors().report(anchor.get().id()).filter(report -> report.walkable().contains(npc.blockPosition())).isEmpty()) return Optional.empty();
        return Optional.of(new Context(world,state,npc,person,visit));
    }
    private static void refresh(ServerPlayer actor,Context context,Desk desk,boolean initial,MarketPayloads.Feedback feedback) {
        var server=context.world.getServer(); long now=server.overworld().getGameTime();
        boolean sources=KingdomMarketSourceService.ready(server);
        boolean homeNeed=KingdomMarketDemandService.observe(actor,context.person.village());
        var personal=de.quest.data.QuestState.get(server).getPlayersView().get(actor.getUUID());
        int bond=de.quest.shrine.VillageBondService.knownBondLevel(personal,context.person.village()).id();
        int discount=ProsperityService.rank(context.world,actor.getUUID(),ProsperityBranch.MARKET)*5;
        var host=context.state.village(context.visit.market()).orElseThrow();
        int reputation=personal==null || !de.quest.reputation.SocialReputationService.enabled()?0:
                MarketPricePolicy.reputation(personal.socialReputation().guildTrust(),personal.socialReputation().localTrust(host.legacyKey()));
        long leaves=context.state.marketTravel().journey(context.person.id()).filter(j -> j.late()).isPresent()?13000:12000;
        long departure=Math.max(0,leaves-Math.floorMod(context.world.getOverworldClockTime(),24000));
        var view=snapshot(context.state,desk.policy,initial,CurrencyService.getBalance(context.world,actor.getUUID()),discount,now,sources && homeNeed,bond,reputation,departure,feedback);
        if(view.isEmpty()) { close(server,actor,true); return; }
        desk.last=view.get(); desk.sent=now; ServerPlayNetworking.send(actor,view.get());
    }
    private static void resend(ServerPlayer actor,Desk desk) {
        var view=desk.last;
        if(view!=null) ServerPlayNetworking.send(actor,new MarketPayloads.View(view.session(),view.revision(),false,view.active(),view.trader(),view.home(),
                view.market(),view.lots(),view.balance(),view.budget(),view.factor(),view.discount(),view.rows(),view.feedback(),view.departureTicks()));
    }
    private static void tick(MinecraftServer server) {
        var desks=RUNTIMES.get(server); if(desks==null || desks.isEmpty()) return;
        long now=server.overworld().getGameTime();
        for(var desk:List.copyOf(desks.values())) {
            var actor=server.getPlayerList().getPlayer(desk.policy.owner());
            if(actor==null) { desks.remove(desk.policy.owner()); LivingKingdomsState.get(server).markets().discardQuotes(desk.policy.owner()); continue; }
            var live=context(actor,desk);
            if(live.isEmpty() || desk.policy.validate(actor.getUUID(),desk.policy.id(),actor.level().dimension().identifier().toString(),
                    desk.policy.body(),desk.policy.generation(),now,desk.policy.revision())!=MarketSessionPolicy.Access.ALLOWED) {
                close(server,actor,true); continue;
            }
            if(now-desk.sent>=100) refresh(actor,live.get(),desk,false,MarketPayloads.Feedback.UPDATED);
        }
    }
    private static void close(MinecraftServer server,ServerPlayer actor,boolean notify) {
        var desks=RUNTIMES.get(server); var desk=desks==null?null:desks.remove(actor.getUUID()); if(desk==null) return;
        desk.policy.close(); LivingKingdomsState.get(server).markets().discardQuotes(actor.getUUID());
        if(notify && ServerPlayNetworking.canSend(actor,MarketPayloads.View.ID)) ServerPlayNetworking.send(actor,new MarketPayloads.View(desk.policy.id(),
                desk.policy.revision(),false,false,"","","",desk.policy.lots(),0,0,0,0,List.of(),MarketPayloads.Feedback.CLOSED));
    }
}
