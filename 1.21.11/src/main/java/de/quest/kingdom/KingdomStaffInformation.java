package de.quest.kingdom;

import de.quest.caravan.KingdomRouteConnections.Connection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

/** Read-only staff reports from existing visits and the listener's own saved freight connections. */
final class KingdomStaffInformation {
    private KingdomStaffInformation() {}
    static Optional<Component> inn(LivingKingdomsState state,UUID village,long now,
                                   Predicate<KingdomPersonRegistry.View> observedBody) {
        if(state.readOnly() || state.marketDays().readOnly() || state.people().readOnly()) return Optional.empty();
        var visit=state.markets().visit(village).filter(KingdomMarketLedger.VisitView::open);
        var guests=visit.stream().flatMap(v -> v.traders().stream())
                .map(t -> state.people().person(t.person())).flatMap(Optional::stream)
                .filter(p -> p.role()==KingdomPersonRegistry.Role.MARKET_TRADER && p.body()!=null
                        && state.people().available(p.id(),p.generation(),p.body())
                        && state.marketDays().place(village,p.id(),now).isPresent() && observedBody.test(p))
                .sorted(Comparator.comparing(KingdomPersonRegistry.View::id)).limit(5).toList();
        if(guests.isEmpty()) return Optional.of(Component.translatable("message.village-quest.kingdom.staff_info.inn.quiet"));
        var first=guests.getFirst(); var home=state.village(first.village());
        if(home.isEmpty()) return Optional.empty();
        return Optional.of(Component.translatable("message.village-quest.kingdom.staff_info.inn.market",
                guests.size(),first.name(),home.get().name()));
    }
    static Optional<Component> store(LivingKingdomsState state,UUID actor,UUID village,List<Connection> connections) {
        if(state.readOnly() || state.relations().readOnly() || state.village(village).isEmpty()) return Optional.empty();
        // Another owner's connection or arrival is never exposed as the listener's freight record.
        var routes=connections.stream().filter(c -> c.owner().equals(actor)
                        && (c.home().equals(village) || c.destination().equals(village)))
                .filter(c -> state.village(other(c,village)).isPresent())
                .sorted(Comparator.comparing(Connection::active).thenComparing(Connection::id)).toList();
        if(routes.isEmpty()) return Optional.of(Component.translatable("message.village-quest.kingdom.staff_info.store.unknown"));
        var route=routes.getFirst(); var partner=other(route,village); String name=state.village(partner).orElseThrow().name();
        if(!route.active()) return Optional.of(Component.translatable("message.village-quest.kingdom.staff_info.store.paused",
                name,Component.translatable("screen.village-quest.trade_route.availability."+route.reason())));
        // Receipt history is personal and pair-scoped, not proof of this selected route.
        boolean arrived=state.relations().latestReceipt(village,partner,actor)>0;
        return Optional.of(Component.translatable("message.village-quest.kingdom.staff_info.store."+(arrived?"arrived":"route"),name));
    }
    private static UUID other(Connection route,UUID village) { return route.home().equals(village)?route.destination():route.home(); }
}
