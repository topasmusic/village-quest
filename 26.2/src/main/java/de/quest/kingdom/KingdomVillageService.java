package de.quest.kingdom;

import de.quest.content.story.ShadowsTradeRoadEncounterService.VillageMarker;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Read-only bridge from shared IDs to legacy gameplay surfaces. Never loads terrain or registers a village. */
public final class KingdomVillageService {
    private KingdomVillageService() {}

    /** Private map report from saved contact evidence; no block reads, discovery or management actions. */
    public static List<Component> mapDetails(ServerLevel world,java.util.UUID actor,int x,int z) {
        var state=LivingKingdomsState.get(world.getServer());
        var key=new VillageKey(world.dimension().identifier().toString(),x,z);
        var data=de.quest.data.QuestState.get(world.getServer()).getPlayerData(actor);
        return mapDetails(state,data,actor,key,de.quest.village.VillageLifeState.get(world.getServer()).status(key),world.getGameTime());
    }
    static List<Component> mapDetails(LivingKingdomsState state,de.quest.data.PlayerQuestData data,java.util.UUID actor,
                                      VillageKey key,de.quest.village.VillageLifeState.Status status,long now) {
        var village=state.byLegacy(key);
        if(village.isEmpty() || !KingdomDiplomacyService.canRead(state,data,actor,village.get().id())) return List.of();
        var lines=new java.util.ArrayList<>(lastKnown(state,status,key,now));
        if(state.tavernGuests().active(village.get().id())>=0)lines.add(Component.translatable("message.village-quest.guest.arrived",village.get().name(),key.anchorX(),key.anchorZ()));
        String prefix="screen.village-quest.building.";
        for(var trip:state.marketTravel().at(village.get().id()).stream().limit(5).toList()) {
                var person=state.people().person(trip.person()); var home=state.village(trip.home());
                if(person.isEmpty() || home.isEmpty()) continue;
                lines.add(Component.translatable("screen.village-quest.trade_route.village_journey",person.get().name(),home.get().name(),village.get().name(),
                        Component.translatable(prefix+"travel_phase."+trip.phase().name().toLowerCase(java.util.Locale.ROOT)),
                        Component.translatable(prefix+(trip.paused()?"travel_paused":"travel_time"),(trip.remaining()+19)/20)));
            }
        var known=KingdomDiplomacyService.contacts(state,data,village.get().id()).stream().map(LivingKingdomsState.VillageView::id).collect(java.util.stream.Collectors.toSet());
        int count=0;
        for(var deal:state.diplomacy().contracts(village.get().id())) {
            var other=deal.left().equals(village.get().id())?deal.right():deal.left();
            if(!known.contains(other)) continue;
            if(count>=40) { lines.add(Component.translatable("screen.village-quest.trade_route.village_more_agreements")); return List.copyOf(lines); }
            count++;
            String dealStatus=deal.status()==KingdomDiplomacy.Status.PROPOSED?(deal.from().equals(village.get().id())?"outgoing":"incoming"):Integer.toString(deal.status().ordinal());
            lines.add(Component.translatable("screen.village-quest.trade_route.village_agreement",state.village(other).orElseThrow().name(),
                    Component.translatable(prefix+"diplomacy_type."+deal.type().ordinal()),Component.translatable(prefix+"diplomacy_status."+dealStatus)));
        }
        if(count==0) lines.add(Component.translatable("screen.village-quest.trade_route.village_no_agreements"));
        return List.copyOf(lines);
    }

    public static List<Component> guestArrivals(ServerLevel world,java.util.UUID actor) {
        return KingdomTavernGuestService.arrivals(world,actor);
    }

    public static de.quest.network.Payloads.JournalVillageStats journalStats(ServerLevel world,java.util.UUID actor,int x,int z) {
        var state=LivingKingdomsState.get(world.getServer());
        var town=state.byLegacy(new VillageKey(world.dimension().identifier().toString(),x,z));
        if(town.isEmpty() || state.readOnly()) return de.quest.network.Payloads.JournalVillageStats.UNKNOWN;
        var id=town.get().id(); int buildings=0;
        for(var role:BuildingScanner.Role.values()) if(state.anchors().official(id,role).isPresent() && !state.anchors().knownDetached(id,role)) buildings++;
        var data=de.quest.data.QuestState.get(world.getServer()).getPlayerData(actor);
        int routes=(int)de.quest.caravan.KingdomRouteConnections.read(world.getServer()).stream()
                .filter(c -> c.active() && (c.home().equals(id) || c.destination().equals(id))).count();
        return new de.quest.network.Payloads.JournalVillageStats(buildings,state.recognition().readOnly()?-1:state.recognition().level(id),routes,
                KingdomDiplomacyService.contacts(state,data,id).size());
    }

    /** Saved evidence only: opening a journal never probes terrain, residents or remote buildings. */
    public static List<Component> lastKnown(ServerLevel world,int x,int z) {
        var state=LivingKingdomsState.get(world.getServer());
        var key=new VillageKey(world.dimension().identifier().toString(),x,z);
        return lastKnown(state,de.quest.village.VillageLifeState.get(world.getServer()).status(key),key,world.getGameTime());
    }
    static List<Component> lastKnown(LivingKingdomsState state,de.quest.village.VillageLifeState.Status status,VillageKey key,long now) {
        var village=state.byLegacy(key); if(village.isEmpty()) return List.of();
        var id=village.get().id(); String text="screen.village-quest.journal.kingdom.";
        var lines=new java.util.ArrayList<Component>();
        lines.add(Component.translatable(text+"last_known",Component.translatable(text+"life."+status.name().toLowerCase(java.util.Locale.ROOT))));
        var population=state.population(id).orElseThrow();
        if(population.readOnly() || population.adults()<0 || population.tick()<0 || now<population.tick())
            lines.add(Component.translatable(text+"population_unknown"));
        else lines.add(Component.translatable(text+(population.adults()==64?"population_at_least":"population"),
                population.adults(),(now-population.tick())/1200));
        for(var role:BuildingScanner.Role.values()) {
            var anchor=state.anchors().official(id,role);
            lines.add(Component.translatable(text+"building",Component.translatable("screen.village-quest.building.role."+role.name().toLowerCase(java.util.Locale.ROOT)),
                    Component.translatable(text+"building."+anchor.map(a -> state.anchors().knownDetached(id,role)?"detached":a.phase().name().toLowerCase(java.util.Locale.ROOT)).orElse("unconfirmed"))));
        }
        if(state.anchors().official(id,BuildingScanner.Role.INN).isPresent()) {
            lines.add(state.tavernGuests().openingText(id,now));
            lines.add(Component.translatable("screen.village-quest.inn.opening.recorded"));
        }
        lines.add(Component.translatable(text+(status==de.quest.village.VillageLifeState.Status.ACTIVE?"route_hint":"rebuild_hint")));
        return List.copyOf(lines);
    }

    public static VillageMarker current(LivingKingdomsState state, String dimension, BlockPos pos) {
        if (state == null) return null;
        var village = state.resolveAt(dimension, pos);
        if (village.isEmpty()) return null;
        var value = village.orElseThrow();
        List<LivingKingdomsState.Zone> zones = state.zones(value.id());
        if (zones.isEmpty()) return null;
        var key = value.legacyKey();
        return new VillageMarker(key.anchorX() + "_" + key.anchorZ(), key.anchorX(), key.anchorZ(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::minX).min().orElseThrow(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::maxX).max().orElseThrow(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::minZ).min().orElseThrow(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::maxZ).max().orElseThrow(),
                state.anchors().all().stream().filter(anchor -> anchor.villageId().equals(value.id()))
                        .map(anchor -> new de.quest.village.GuildCornerCandidateSearch.StructureFootprint(
                                anchor.position().getX(), anchor.position().getX(),
                                anchor.position().getZ(), anchor.position().getZ())).toList());
    }

    public static Component name(LivingKingdomsState state, VillageKey key, Component fallback) {
        return state == null ? fallback : state.byLegacy(key).<Component>map(v -> Component.literal(v.name())).orElse(fallback);
    }

    public static Component name(ServerLevel world, int x, int z, Component fallback) {
        if (world == null || world.getServer() == null) return fallback;
        return name(LivingKingdomsState.get(world.getServer()),
                new VillageKey(world.dimension().identifier().toString(), x, z), fallback);
    }

    /** Keep village specialization visible alongside its shared player-visible name. */
    public static Component identityLabel(ServerLevel world, int x, int z, Component type) {
        Component name = name(world, x, z, type);
        return name == type ? type : name.copy().append(" · ").append(type);
    }

    /** Default generated sea-level metadata is not physical navigation evidence. */
    public static Optional<BlockPos> arrival(LivingKingdomsState state, VillageKey key) {
        if (state == null) return Optional.empty();
        return state.byLegacy(key).filter(v -> state.zones(v.id()).stream().anyMatch(z -> z.contains(v.arrival())))
                .map(LivingKingdomsState.VillageView::arrival);
    }

    public static boolean sameVillage(LivingKingdomsState state, String dimension,
                                      int x, int z, int otherX, int otherZ) {
        if (x == otherX && z == otherZ) return true;
        if (state == null) return false;
        var first = state.byLegacy(new VillageKey(dimension, x, z));
        var second = state.byLegacy(new VillageKey(dimension, otherX, otherZ));
        return first.isPresent() && second.isPresent() && first.orElseThrow().id().equals(second.orElseThrow().id());
    }

    public static List<LivingKingdomsState.Zone> confirmedZones(ServerLevel world, VillageMarker marker) {
        if (world == null || marker == null || world.getServer() == null) return List.of();
        var state = LivingKingdomsState.get(world.getServer());
        return state.byLegacy(new VillageKey(world.dimension().identifier().toString(), marker.centerX(), marker.centerZ()))
                .filter(v -> v.origin() == LivingKingdomsState.Origin.CUSTOM)
                .map(v -> state.zones(v.id())).orElse(List.of());
    }

    public static AABB observationArea(ServerLevel world, VillageMarker marker,
                                       List<LivingKingdomsState.Zone> zones) {
        if (zones.isEmpty()) return new AABB(marker.minX() - 16.0, world.getMinY(), marker.minZ() - 16.0,
                marker.maxX() + 17.0, world.getMaxY(), marker.maxZ() + 17.0);
        return new AABB(zones.stream().mapToInt(LivingKingdomsState.Zone::minX).min().orElseThrow(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::minY).min().orElseThrow(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::minZ).min().orElseThrow(),
                zones.stream().mapToInt(LivingKingdomsState.Zone::maxX).max().orElseThrow() + 1.0,
                zones.stream().mapToInt(LivingKingdomsState.Zone::maxY).max().orElseThrow() + 1.0,
                zones.stream().mapToInt(LivingKingdomsState.Zone::maxZ).max().orElseThrow() + 1.0);
    }

    public static boolean withinConfirmedArea(List<LivingKingdomsState.Zone> zones, BlockPos pos) {
        return zones.isEmpty() || zones.stream().anyMatch(z -> z.contains(pos));
    }
}
