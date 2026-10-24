package de.quest.kingdom;

import de.quest.caravan.NpcNames;
import de.quest.data.QuestState;
import de.quest.entity.KingdomNpcEntity;
import de.quest.village.VillageLifeState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

/** Adapter to existing canonical NPC bodies, certified inn floors and private speech. */
final class KingdomTavernGuestService {
    private KingdomTavernGuestService() {}
    /** Saved-state arrival only. Never probes terrain, entities or pathfinding. */
    static void remote(LivingKingdomsState state,ServerLevel world,KingdomAnchorRegistry.AnchorView inn) {
        var village=inn.villageId();var ledger=state.tavernGuests();long clock=world.getOverworldClockTime();
        if(ledger.readOnly())return;
        boolean eligible=inn.phase()==BuildingOperation.Phase.ACTIVE && ledger.ready(village)
                && state.anchors().official(village,BuildingScanner.Role.INN).filter(a -> a.id().equals(inn.id())).isPresent()
                && state.village(village).filter(v -> VillageLifeState.get(world.getServer()).status(v.legacyKey())==VillageLifeState.Status.ACTIVE).isPresent()
                && state.population(village).filter(p -> !p.readOnly() && p.adults()>0).isPresent()
                && clock>=0 && Math.floorMod(clock,24000)>=1000 && Math.floorMod(clock,24000)<12000;
        ledger.draw(village,clock,eligible,new java.util.Random(world.getRandom().nextLong()));
        if(ledger.active(village)>=0)announce(world,state,village,ledger.visitToken(village));
    }
    static Optional<KingdomPersonRegistry.View> observe(LivingKingdomsState state,ServerLevel world,
            KingdomAnchorRegistry.AnchorView inn,long now,boolean loaded) {
        UUID village=inn.villageId(); var ledger=state.tavernGuests(); long clock=world.getOverworldClockTime();
        if(ledger.readOnly()) return Optional.empty();
        boolean valid=state.anchors().official(village,BuildingScanner.Role.INN).filter(a -> a.id().equals(inn.id())).isPresent()
                && state.anchors().mayContinue(inn.id(),now) && state.village(village).filter(t -> VillageLifeState.get(world.getServer()).status(t.legacyKey())==VillageLifeState.Status.ACTIVE).isPresent();
        boolean invalid=state.anchors().official(village,BuildingScanner.Role.INN).filter(a -> a.id().equals(inn.id())).isEmpty()
                || inn.phase()==BuildingOperation.Phase.GRACE || inn.phase()==BuildingOperation.Phase.PAUSED
                || state.village(village).filter(t -> VillageLifeState.get(world.getServer()).status(t.legacyKey())!=VillageLifeState.Status.ACTIVE).isPresent();
        // Stale/unknown certificates pause; only positive invalid evidence resets the opening period.
        ledger.observeInn(village,inn.id(),now,valid,loaded && (valid || invalid));
        boolean peaceful=loaded && world.getRaidAt(inn.position())==null
                && world.getEntitiesOfClass(Monster.class,new AABB(inn.position()).inflate(inn.range().horizontal),NativeNpcActivities::hostile).isEmpty();
        if(ledger.active(village)>=0) {
            var person=state.people().staffGuest(village,ledger.active(village))
                    .or(() -> state.people().ensure(village,KingdomPersonRegistry.Role.INNKEEPER,ledger.active(village)+1));
            boolean bodyLoaded=loaded && person.filter(p -> p.body()!=null && world.getEntity(p.body()) instanceof KingdomNpcEntity npc
                    && world.isPositionEntityTicking(npc.blockPosition()) && KingdomNpcService.available(world,npc).isPresent()).isPresent();
            ledger.observe(village,now,bodyLoaded && valid);
            if(loaded && (invalid || !peaceful || ledger.expired(village,now)
                    || person.filter(p -> p.presence()==KingdomPersonRegistry.Presence.DEAD).isPresent())) {
                person.ifPresent(p -> {
                    if(p.body()!=null) {
                        var body=world.getEntity(p.body());
                        if(state.people().dismiss(p.id(),p.generation(),p.body()) && body instanceof KingdomNpcEntity npc) npc.discard();
                    }
                });
                ledger.end(village,clock); return Optional.empty();
            }
            if(bodyLoaded && valid) announce(world,state,village,ledger.visitToken(village));
            return person;
        }
        boolean populated=state.population(village).filter(p -> !p.readOnly() && p.adults()>0 && now>=p.tick() && now-p.tick()<=200).isPresent();
        var room=state.anchors().report(inn.id());
        boolean space=room.filter(r -> r.walkable().stream().anyMatch(p -> place(r,inn,p))).isPresent();
        boolean daytime=clock>=0 && Math.floorMod(clock,24000)>=1000 && Math.floorMod(clock,24000)<12000;
        ledger.draw(village,clock,loaded && valid && ledger.ready(village) && peaceful && populated && space && daytime,new java.util.Random(world.getRandom().nextLong()));
        if(ledger.active(village)>=0)announce(world,state,village,ledger.visitToken(village));
        return ledger.active(village)<0?Optional.empty():state.people().ensure(village,KingdomPersonRegistry.Role.INNKEEPER,ledger.active(village)+1);
    }
    static void announce(ServerLevel world,LivingKingdomsState state,UUID village,long visit) {
        var town=state.village(village);if(town.isEmpty() || visit<0)return;
        var quests=QuestState.get(world.getServer());var home=town.get();
        for(var player:world.getServer().getPlayerList().getPlayers()) {
            var data=quests.getPlayerData(player.getUUID());
            if(visit<=lastArrival(data,village) || !related(state,data,player.getUUID(),village))continue;
            de.quest.quest.WorldNewsService.offer(player,"guest:"+village,Component.translatable("message.village-quest.guest.arrived",home.name(),home.legacyKey().anchorX(),home.legacyKey().anchorZ()),true,()->{if(markArrival(data,village,visit))quests.setDirty();});
        }
    }
    static boolean markArrival(de.quest.data.PlayerQuestData data,UUID village,long visit) {
        if(visit<0 || visit<=lastArrival(data,village))return false;
        data.setTradeRouteString("kingdoms.guests.arrival."+village,Long.toString(visit));return true;
    }
    private static long lastArrival(de.quest.data.PlayerQuestData data,UUID village) {
        try{return Long.parseLong(data.getTradeRouteString("kingdoms.guests.arrival."+village));}catch(NumberFormatException ignored){return -1;}
    }
    private static boolean related(LivingKingdomsState state,de.quest.data.PlayerQuestData data,UUID player,UUID village) {
        if(state.canManageVillage(player,village))return true;
        for(int i=0;i<de.quest.shrine.VillageBondService.historicalVillageCount(data);i++)
            if(de.quest.shrine.VillageContactService.isContact(data,i) && village.toString().equals(data.getTradeRouteString(de.quest.shrine.VillageBondService.villageKey(i,"canonical_id"))))return true;
        return false;
    }
    static java.util.List<Component> arrivals(ServerLevel world,UUID player) {
        var state=LivingKingdomsState.get(world.getServer());var data=QuestState.get(world.getServer()).getPlayerData(player);
        var lines=new ArrayList<Component>();
        for(var inn:state.anchors().all())if(inn.role()==BuildingScanner.Role.INN && state.tavernGuests().active(inn.villageId())>=0 && related(state,data,player,inn.villageId()))
            state.village(inn.villageId()).ifPresent(v -> lines.add(Component.translatable("message.village-quest.guest.arrived",v.name(),v.legacyKey().anchorX(),v.legacyKey().anchorZ())));
        return java.util.List.copyOf(lines);
    }
    static boolean place(BuildingScanner.Report room,KingdomAnchorRegistry.AnchorView inn,BlockPos pos) {
        if(!room.walkable().contains(pos) || pos.distSqr(inn.position())>36 || pos.distSqr(inn.position().relative(inn.facing()))<=2) return false;
        for(var feature:List.of(BuildingScanner.Feature.BED,BuildingScanner.Feature.KITCHEN,BuildingScanner.Feature.WORKSTATION))
            if(room.stations().getOrDefault(feature,Set.of()).stream().anyMatch(station -> station.distSqr(pos)<=2)) return false;
        return true;
    }
    static void interact(ServerLevel world,ServerPlayer actor,KingdomNpcEntity npc,KingdomPersonRegistry.View person) {
        if(!actor.hasLineOfSight(npc)) return;
        var state=LivingKingdomsState.get(world.getServer());
        var inn=state.anchors().official(person.village(),BuildingScanner.Role.INN);
        long now=world.getServer().overworld().getGameTime();
        if(inn.isEmpty() || !state.anchors().mayContinue(inn.get().id(),now) || npc.blockPosition().distSqr(inn.get().position())>64) return;
        var quests=QuestState.get(world.getServer()); var data=quests.getPlayerData(actor.getUUID());
        if(person.guest()>=0 && person.guest()<=2) {
            String visit=person.village()+":"+state.tavernGuests().visitToken(person.village());
            de.quest.quest.special.FriendsQuestService.talk(world,actor,npc,person,visit);
            state.tavernGuests().dialogue(person.village(),now); return;
        }
        String id=NpcNames.GUESTS.get(person.guest()),memory="kingdoms.guests.familiar."+id;
        String greeting=data.hasTradeRouteFlag(memory)?"again":"hello";
        var line=Component.translatable("message.village-quest.guest."+id+"."+greeting,person.displayName(),state.village(person.village()).orElseThrow().name());
        var routes=de.quest.caravan.KingdomRouteConnections.read(state,VillageLifeState.get(world.getServer()),actor.getUUID(),data);
        int music=data.getTradeRouteInt("kingdoms.guests.raphael.dialogue");
        if(person.guest()==3 && Math.floorMod(music,2)==1) line.append(" ").append(Component.translatable("message.village-quest.guest.ravel.music"));
        if(person.guest()==0 || person.guest()==1)
            KingdomStaffInformation.store(state,actor.getUUID(),person.village(),routes).ifPresent(info -> line.append(" ").append(info));
        else if(person.guest()==2) {
            for(var role:List.of(BuildingScanner.Role.COUNCIL,BuildingScanner.Role.INN,BuildingScanner.Role.STOREHOUSE,BuildingScanner.Role.MARKET)) {
                var anchor=state.anchors().official(person.village(),role);
                if(anchor.isEmpty()) {
                    line.append(" ").append(Component.translatable("message.village-quest.guest.plan",Component.translatable("screen.village-quest.building.role."+role.name().toLowerCase(Locale.ROOT)))); break;
                }
                if(anchor.isPresent() && state.anchors().report(anchor.get().id()).isPresent()) {
                    var report=state.anchors().report(anchor.get().id()).orElseThrow();
                    var missing=report.checks().entrySet().stream().filter(e -> !e.getValue()).sorted(Map.Entry.comparingByKey()).findFirst();
                    if(missing.isPresent()) { line.append(" ").append(Component.translatable("message.village-quest.guest.building",Component.translatable("screen.village-quest.building.role."+role.name().toLowerCase(Locale.ROOT)),Component.translatable("screen.village-quest.building.check."+missing.get().getKey().name().toLowerCase(Locale.ROOT)))); break; }
                }
            }
        } else state.population(person.village()).filter(p -> !p.readOnly()).ifPresent(p -> line.append(" ").append(Component.translatable(
                "message.village-quest.guest.milestone",p.reachedTen()?10:p.reachedFive()?5:Math.max(0,p.adults()))));
        if(NpcSpeechService.privateLine(actor,npc,line)) {
            data.setTradeRouteFlag(memory,true);
            if(person.guest()==3) data.setTradeRouteInt("kingdoms.guests.raphael.dialogue",Math.floorMod(music,2)+1);
            quests.setDirty(); state.tavernGuests().dialogue(person.village(),now);
        }
    }
}
