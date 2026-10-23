package de.quest.kingdom;

import de.quest.entity.KingdomNpcEntity;
import de.quest.registry.ModEntities;
import de.quest.village.VillageLifeState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** Small fair loaded-near worklist. No chunk tickets, world scans or Vanilla resident creation. */
final class KingdomNpcController {
    private static final Map<MinecraftServer,KingdomNpcController> RUNTIMES=new WeakHashMap<>();
    private final MinecraftServer server;
    private final Map<UUID,Long> interests=new LinkedHashMap<>();
    private final Map<UUID,Long> lastNear=new java.util.HashMap<>();
    private final Map<UUID,Integer> marketCursor=new java.util.HashMap<>();
    private int playerCursor,anchorCursor,guestCursor,councilCursor;
    private KingdomNpcController(MinecraftServer server) { this.server=server; }
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> RUNTIMES.computeIfAbsent(server,KingdomNpcController::new).tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNTIMES.remove(server));
    }
    private void tick() {
        long now=server.overworld().getGameTime(); var state=LivingKingdomsState.get(server);
        for(var lease:state.people().beds().releasing(2)) {
            ServerLevel world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(lease.dimension())));
            if(world!=null) NativeInnBeds.cleanup(world,lease);
        }
        interests.entrySet().removeIf(entry -> now<entry.getValue() || now-entry.getValue()>200);
        if (now%20==0) {
            var savedAnchors=state.anchors().all();
            var inns=savedAnchors.stream().filter(a -> a.role()==BuildingScanner.Role.INN).toList();
            for(int i=0;i<Math.min(16,inns.size());i++) {
                var inn=inns.get(Math.floorMod(guestCursor++,inns.size()));
                var world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(inn.dimension())));
                if(world!=null)KingdomTavernGuestService.remote(state,world,inn);
            }
            var councils=savedAnchors.stream().filter(a -> a.role()==BuildingScanner.Role.COUNCIL && a.phase()==BuildingOperation.Phase.ACTIVE).toList();
            var savedRoutes=councils.isEmpty()?java.util.List.<de.quest.caravan.KingdomRouteConnections.Connection>of():de.quest.caravan.KingdomRouteConnections.read(server);
            for(int i=0;i<Math.min(8,councils.size());i++) {
                var council=councils.get(Math.floorMod(councilCursor++,councils.size()));
                var town=state.village(council.villageId());
                if(town.isEmpty() || VillageLifeState.get(server).status(town.get().legacyKey())!=VillageLifeState.Status.ACTIVE)continue;
                server.getPlayerList().getPlayers().stream().filter(p -> p.isAlive() && !p.isSpectator() && state.canManageVillage(p.getUUID(),council.villageId())).findFirst().ifPresent(p -> {
                    KingdomAutonomyService.observe(p,council.villageId(),now,savedRoutes);
                    KingdomCrisisService.consider(p,council.villageId());
                });
            }
            for(var retired:state.diplomacy().reconcileMeetings()) {
                var town=state.village(retired.village());
                if(town.isPresent()) {
                    var world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(town.get().legacyKey().dimension())));
                    if(world!=null && retired.body()!=null && world.getEntity(retired.body()) instanceof KingdomNpcEntity npc) npc.discard();
                }
            }
            retireUnobservedBodies(state,now);
            var players=server.getPlayerList().getPlayers();
            for(int i=0;i<Math.min(8,players.size());i++) {
                var player=players.get(Math.floorMod(playerCursor++,players.size()));
                KingdomCrisisService.observe(player);
                KingdomAttackService.observe(player);
                for(var anchor:state.anchors().near(player.level().dimension().identifier().toString(),player.blockPosition(),128,8))
                    if (interests.containsKey(anchor.id()) || interests.size()<64) interests.put(anchor.id(),now);
            }
        }
        var candidates=new ArrayList<>(interests.keySet());
        for(int i=0;i<Math.min(4,candidates.size());i++) {
            var anchor=state.anchors().anchor(candidates.get(Math.floorMod(anchorCursor++,candidates.size())));
            if (anchor.isPresent()) observe(state,anchor.get(),now);
        }
    }
    private void observe(LivingKingdomsState state,KingdomAnchorRegistry.AnchorView anchor,long now) {
        observe(state,anchor,now,null);
        if(anchor.role()==BuildingScanner.Role.INN) {
            var world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(anchor.dimension())));
            if(world!=null) KingdomTavernGuestService.observe(state,world,anchor,now,areaLoaded(world,anchor))
                    .ifPresent(guest -> observe(state,anchor,now,guest.id()));
        }
        if(anchor.role()==BuildingScanner.Role.COUNCIL)
            for(var visitor:state.people().visitors(anchor.villageId()).stream().filter(p -> p.role()==KingdomPersonRegistry.Role.LEADER).limit(4).toList()) observe(state,anchor,now,visitor.id());
    }
    private void observe(LivingKingdomsState state,KingdomAnchorRegistry.AnchorView anchor,long now,UUID marketPerson) {
        ServerLevel world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(anchor.dimension())));
        var town=state.village(anchor.villageId()); if (world==null || town.isEmpty()) return;
        var role=switch(anchor.role()) {
            case COUNCIL -> KingdomPersonRegistry.Role.LEADER;
            case INN -> KingdomPersonRegistry.Role.INNKEEPER;
            case STOREHOUSE -> KingdomPersonRegistry.Role.STOREKEEPER;
            case MARKET -> KingdomPersonRegistry.Role.MARKET_TRADER;
        };
        if(role==KingdomPersonRegistry.Role.MARKET_TRADER && marketPerson==null) { observeMarket(state,anchor,world,now); return; }
        var person=marketPerson==null?state.people().ensure(anchor.villageId(),role,0):state.people().person(marketPerson); if(person.isEmpty()) return;
        var view=person.get();
        if(view.visit()!=null && !view.visit().equals(anchor.villageId())) return;
        boolean visiting=view.visit()!=null;
        boolean loaded=areaLoaded(world,anchor);
        boolean allowed=KingdomNpcPresencePolicy.mayAppear(role,VillageLifeState.get(server).status(town.get().legacyKey()),anchor.phase(),world.getOverworldClockTime());
        if(view.guest()>=0) allowed=state.tavernGuests().active(view.village())==view.guest() && anchor.phase()==BuildingOperation.Phase.ACTIVE;
        if(role==KingdomPersonRegistry.Role.MARKET_TRADER) allowed=(allowed || state.marketTravel().evening(anchor.villageId(),view.id())
                && anchor.phase()==BuildingOperation.Phase.ACTIVE && VillageLifeState.get(server).status(town.get().legacyKey())!=VillageLifeState.Status.ABANDONED)
                && state.marketDays().appearancePlace(anchor.villageId(),view.id(),now).isPresent();
        if(view.presence()==KingdomPersonRegistry.Presence.DEAD) state.people().observeActive(view.id(),now,loaded && allowed);
        if (view.body()!=null) {
            var entity=world.getEntity(view.body());
            if(entity instanceof KingdomNpcEntity candidate && KingdomNpcService.available(world,candidate).isEmpty())
                KingdomNpcService.reactivate(world,candidate);
            if (entity instanceof KingdomNpcEntity npc && KingdomNpcService.available(world,npc).isPresent()) {
                npc.refreshAppearance(state.people().person(view.id()).orElseThrow());
                if(view.guest()>=0) {
                    npc.getNavigation().stop();
                    if(npc.blockPosition().distSqr(anchor.position())>64 && state.people().dismiss(view.id(),view.generation(),view.body())) {
                        state.tavernGuests().end(view.village(),world.getOverworldClockTime()); npc.discard();
                    }
                    return;
                }
                if(loaded && npc.beginActivity(now)) {
                    var navigation=(KingdomNpcNavigation)npc.getNavigation();
                    var report=state.anchors().report(anchor.id());
                    if(state.anchors().mayContinue(anchor.id(),now) && report.isPresent()) navigation.certify(report.get(),now);
                    else navigation.invalidate();
                    boolean fleeing=NativeNpcActivities.tick(world,npc,view,anchor,
                            state.anchors().mayContinue(anchor.id(),now)?report.orElse(null):null,now);
                    long clock=Math.floorMod(world.getOverworldClockTime(),24000);
                    if(clock<13000 || clock>=23000)
                        state.people().beds().lease(view.id()).ifPresent(lease -> NativeInnBeds.wake(world,npc,lease));
                    var bed=fleeing||visiting?java.util.Optional.<KingdomBedRegistry.LeaseView>empty():NativeInnBeds.observe(world,npc,view,now);
                    if(bed.isPresent()) {
                        if(clock>=13000 && clock<23000) {
                            if(!NativeInnBeds.trySleep(world,npc,bed.get()) && !npc.isSleeping()
                                    && report.isPresent() && state.anchors().maySpawn(anchor.id(),now) && navigation.isDone())
                                NativeNpcPathPolicy.sleepApproach(report.get().walkable(),bed.get().head(),npc.blockPosition())
                                        .ifPresent(target -> navigation.moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,0.8));
                        } else {
                            if(!npc.isSleeping() && report.isPresent() && state.anchors().maySpawn(anchor.id(),now) && navigation.isDone()) {
                                var target=anchor.position().relative(anchor.facing());
                                if(npc.blockPosition().distSqr(target)>4 && report.get().walkable().contains(target))
                                    navigation.moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,0.7);
                            }
                        }
                    }
                }
                if (loaded && !allowed && (role!=KingdomPersonRegistry.Role.MARKET_TRADER || state.marketDays().mayDismiss(anchor.villageId(),view.id(),world.getOverworldClockTime(),now)) && anchor.phase()!=BuildingOperation.Phase.GRACE
                        && (visiting?state.people().pauseVisit(view.id(),view.generation(),view.body()):state.people().dismiss(view.id(),view.generation(),view.body()))) npc.discard();
            } else state.people().observeAbsent(view.id(),now,loaded && entity==null && world.isPositionEntityTicking(view.position()));
            return;
        }
        view=state.people().person(view.id()).orElseThrow();
        if (!loaded || !allowed || view.presence()!=KingdomPersonRegistry.Presence.DORMANT && !visiting || !state.anchors().maySpawn(anchor.id(),now)) return;
        var report=state.anchors().report(anchor.id()); if(report.isEmpty()) return;
        int guestIndex=view.guest();
        var positions=role==KingdomPersonRegistry.Role.MARKET_TRADER
                ?state.marketDays().appearancePlace(anchor.villageId(),view.id(),now).map(place -> java.util.List.of(place.position())).orElse(java.util.List.of())
                :report.get().walkable().stream().filter(pos -> guestIndex<0 || KingdomTavernGuestService.place(report.get(),anchor,pos)).sorted(java.util.Comparator.comparingDouble(pos -> pos.distSqr(anchor.position()))).limit(8).toList();
        for(BlockPos position:positions) {
            if(view.guest()>=0 && (!KingdomTavernGuestService.place(report.get(),anchor,position)
                    || world.getBlockState(position).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) continue;
            if (!safe(world,position)) continue;
            if (!world.getEntitiesOfClass(LivingEntity.class,new AABB(position).inflate(0,0.8,0),LivingEntity::isAlive).isEmpty()) continue;
            var ticket=state.people().reserve(view.id(),view.revision(),UUID.randomUUID(),position,now); if(ticket.isEmpty()) return;
            var npc=ModEntities.KINGDOM_NPC.create(world,EntitySpawnReason.EVENT);
            if(npc==null) { state.people().abort(ticket.get()); return; }
            npc.setUUID(ticket.get().body()); npc.snapTo(position.getX()+0.5,position.getY(),position.getZ()+0.5,anchor.facing().toYRot(),0);
            npc.bind(ticket.get(),state.people().person(view.id()).orElseThrow());
            if(!world.addFreshEntity(npc)) { state.people().abort(ticket.get()); return; }
            if(!state.people().commit(ticket.get())) { npc.discard(); state.people().abort(ticket.get()); }
            else if(view.guest()<0 && state.people().arrival(ticket.get()))
                NativeNpcActivities.say(world,npc,net.minecraft.network.chat.Component.translatable("message.village-quest.kingdom.arrival",view.name(),town.get().name()));
            return;
        }
    }
    private void observeMarket(LivingKingdomsState state,KingdomAnchorRegistry.AnchorView anchor,ServerLevel world,long now) {
        if(!state.tavernGuests().ready(anchor.villageId())) return;
        var town=state.village(anchor.villageId()).orElseThrow();
        var observer=server.getPlayerList().getPlayers().stream().filter(p -> p.level()==world && p.blockPosition().distSqr(anchor.position())<=128*128)
                .min(java.util.Comparator.comparingDouble(p -> p.blockPosition().distSqr(anchor.position())));
        boolean loaded=observer.isPresent() && areaLoaded(world,anchor);
        var routes=de.quest.caravan.KingdomRouteConnections.read(server);
        if(loaded) state.marketTravel().recoverLostRoutes(anchor.villageId(),(owner,id) -> {
            var data=de.quest.data.QuestState.get(server).getPlayersView().get(owner);
            return de.quest.caravan.KingdomRouteConnections.permanentlyMissing(data,id);
        });
        if(loaded) {
            var homes=new java.util.HashSet<UUID>(); homes.add(anchor.villageId());
            routes.stream().filter(c -> c.active() && (c.home().equals(anchor.villageId()) || c.destination().equals(anchor.villageId())))
                    .forEach(c -> { homes.add(c.home()); homes.add(c.destination()); });
            for(var home:homes) KingdomMarketDemandService.observe(state,de.quest.data.QuestState.get(server),
                    de.quest.village.LivingVillageNetworkState.get(server),observer.orElseThrow().getUUID(),home);
        }
        var visit=KingdomMarketVisitors.observe(state,de.quest.data.QuestState.get(server),anchor.villageId(),observer.map(p -> p.getUUID()).orElse(null),
                world.getOverworldClockTime(),now,state.recognition().capacity(anchor.villageId()),loaded && !state.recognition().readOnly(),
                VillageLifeState.get(server).status(town.legacyKey())!=VillageLifeState.Status.ABANDONED,
                routes);
        for(var retired:state.marketTravel().takeRetired())
            if(retired.body()!=null && world.getEntity(retired.body()) instanceof KingdomNpcEntity npc) npc.discard();
        if(visit.isEmpty()) {
            state.markets().visit(anchor.villageId()).ifPresent(old -> old.traders().forEach(trader -> {
                var person=state.people().person(trader.person());
                if(state.marketDays().appearancePlace(anchor.villageId(),trader.person(),now).isPresent()) { observe(state,anchor,now,trader.person()); return; }
                if(state.marketDays().mayDismiss(anchor.villageId(),trader.person(),world.getOverworldClockTime(),now) && person.isPresent() && person.get().body()!=null && world.getEntity(person.get().body()) instanceof KingdomNpcEntity npc
                        && world.isPositionEntityTicking(npc.blockPosition()) && (person.get().visit()!=null
                        ?state.people().pauseVisit(person.get().id(),person.get().generation(),person.get().body())
                        :state.people().dismiss(person.get().id(),person.get().generation(),person.get().body()))) npc.discard();
            })); return;
        }
        var traders=visit.get().traders(); if(traders.isEmpty()) return;
        int cursor=marketCursor.getOrDefault(anchor.id(),0); marketCursor.put(anchor.id(),(cursor+1)%traders.size());
        observe(state,anchor,now,traders.get(Math.floorMod(cursor,traders.size())).person());
    }
    private boolean safe(ServerLevel world,BlockPos pos) {
        if(!world.hasChunkAt(pos) || !world.isPositionEntityTicking(pos)) return false;
        var budget=KingdomBuildingService.workBudget(server); if(!budget.takeBlocks(3)) return false;
        return NativeNpcFooting.safe(world.getBlockState(pos),world.getBlockState(pos.above()),world.getBlockState(pos.below()),pos);
    }
    private void retireUnobservedBodies(LivingKingdomsState state,long now) {
        var people=state.people().loadedPeople();
        var ids=people.stream().map(KingdomPersonRegistry.View::id).collect(java.util.stream.Collectors.toSet());
        lastNear.keySet().retainAll(ids);
        for(var person:people) {
            UUID location=person.visit()==null?person.village():person.visit();
            var town=state.village(location); if(town.isEmpty()) continue;
            ServerLevel world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(town.get().legacyKey().dimension())));
            if(world==null || !(world.getEntity(person.body()) instanceof KingdomNpcEntity npc) || !npc.isAlive()) continue;
            if(person.role()==KingdomPersonRegistry.Role.MARKET_TRADER && state.marketDays().mayDismiss(location,person.id(),world.getOverworldClockTime(),now)
                    && world.isPositionEntityTicking(npc.blockPosition()) && retire(state,person)) { npc.discard(); continue; }
            if(state.anchors().knownDetached(location,person.role().anchor)
                    && world.isPositionEntityTicking(npc.blockPosition())
                    && retire(state,person)) { npc.discard(); continue; }
            var official=state.anchors().official(location,person.role().anchor);
            if(official.isPresent() && state.anchors().maySpawn(official.get().id(),now) && !npc.isSleeping()
                    && state.anchors().report(official.get().id()).filter(report -> !report.walkable().contains(person.position())).isPresent()
                    && retire(state,person)) { npc.discard(); continue; }
            if(server.getPlayerList().getPlayers().stream().anyMatch(player -> player.level()==world && player.distanceToSqr(npc)<=128*128))
                lastNear.put(person.id(),now);
            else if(now-lastNear.computeIfAbsent(person.id(),ignored -> now)>=200
                    && (person.visit()!=null?state.people().pauseVisit(person.id(),person.generation(),person.body()):state.people().dismiss(person.id(),person.generation(),person.body()))) npc.discard();
        }
    }
    private static boolean retire(LivingKingdomsState state,KingdomPersonRegistry.View person) {
        return person.visit()!=null?state.people().pauseVisit(person.id(),person.generation(),person.body())
                :state.people().dismiss(person.id(),person.generation(),person.body());
    }
    private static boolean areaLoaded(ServerLevel world,KingdomAnchorRegistry.AnchorView anchor) {
        int radius=anchor.range().horizontal; var pos=anchor.position();
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)
            for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++)
                if(!world.hasChunkAt(new BlockPos(x*16,pos.getY(),z*16))) return false;
        return world.isPositionEntityTicking(pos);
    }
}
