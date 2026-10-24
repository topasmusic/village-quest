package de.quest.kingdom;

import de.quest.caravan.KingdomRouteConnections;
import de.quest.data.QuestState;
import de.quest.entity.KingdomNpcEntity;
import de.quest.village.VillageCondition;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Saved direct-route relationships progress independently of NPC materialization. */
final class KingdomAutonomyService {
    private KingdomAutonomyService() {}
    static List<LivingKingdomsState.VillageView> observationPartners(List<LivingKingdomsState.VillageView> known,
            List<KingdomRouteConnections.Connection> routes,UUID host,long now) {
        var direct=activePartners(routes,host);
        var eligible=known.stream().filter(v -> direct.contains(v.id())).toList();
        if(eligible.size()<=32) return eligible;
        // Ten consecutive observations per window; gaps still earn no time in the pair policy.
        int offset=(int)((Math.max(0,now)/200*32)%eligible.size());
        var selected=new ArrayList<LivingKingdomsState.VillageView>(32);
        for(int i=0;i<32;i++) selected.add(eligible.get((offset+i)%eligible.size()));
        return List.copyOf(selected);
    }
    private static Set<UUID> activePartners(List<KingdomRouteConnections.Connection> routes,UUID host) {
        var direct=new HashSet<UUID>();
        for(var connection:routes) if(connection.active()) {
            if(connection.home().equals(host)) direct.add(connection.destination());
            if(connection.destination().equals(host)) direct.add(connection.home());
        }
        return direct;
    }
    static void observe(ServerPlayer player,UUID host,long now) {
        observe(player,host,now,KingdomRouteConnections.read(player.level().getServer()));
    }
    static void observe(ServerPlayer player,UUID host,long now,List<KingdomRouteConnections.Connection> routes) {
        var server=player.level().getServer(); var state=LivingKingdomsState.get(server);
        if(!state.canManageVillage(player.getUUID(),host) || state.autonomy().readOnly()) return;
        var local=KingdomMarketDemandService.current(player,host); if(local.isEmpty()) return;
        var leader=state.people().ensure(host,KingdomPersonRegistry.Role.LEADER,0); if(leader.isEmpty()) return;
        var known=KingdomDiplomacyService.contacts(state,QuestState.get(server).getPlayerData(player.getUUID()),host);
        var direct=activePartners(routes,host);
        // A briefly disconnected known pair must lose its clock even outside the current batch.
        for(var other:known) if(!direct.contains(other.id())) state.autonomy().observe(host,other.id(),now,false,0,0,false);
        for(var other:observationPartners(known,routes,host,now)) {
            boolean active=routes.stream().anyMatch(c -> c.active() && c.joins(host,other.id()));
            var need=active?KingdomMarketDemandService.current(player,other.id()):Optional.<de.quest.village.LivingVillageNetworkState.VillageSnapshot>empty();
            if(!active || need.isEmpty() || state.anchors().official(other.id(),BuildingScanner.Role.COUNCIL).isEmpty()) {
                state.autonomy().observe(host,other.id(),now,false,0,0,false); continue;
            }
            var remote=state.people().ensure(other.id(),KingdomPersonRegistry.Role.LEADER,0); if(remote.isEmpty()) continue;
            boolean shortage=need.get().condition()==VillageCondition.CRISIS || need.get().condition()==VillageCondition.STRAINED
                    || local.get().condition()==VillageCondition.CRISIS || local.get().condition()==VillageCondition.STRAINED;
            if(state.autonomy().observeRemote(host,other.id(),now,true,leader.get().personality(),remote.get().personality(),shortage)) {
                var request=state.autonomy().between(host,other.id()).orElseThrow().request();
                de.quest.quest.WorldNewsService.offer(player,"initiative:"+host+":"+other.id(),Component.translatable("message.village-quest.kingdom.initiative",state.village(host).orElseThrow().name(),other.name(),
                        Component.translatable("screen.village-quest.building.diplomacy_type."+request.ordinal())),true);
            }
        }
    }
    static void talk(ServerPlayer player,KingdomNpcEntity npc,KingdomPersonRegistry.View person) {
        if(person.role()!=KingdomPersonRegistry.Role.LEADER || npc.isSleeping() || !player.hasLineOfSight(npc)) return;
        var state=LivingKingdomsState.get(player.level().getServer());
        var data=QuestState.get(player.level().getServer()).getPlayerData(player.getUUID());
        if(!state.canManageVillage(player.getUUID(),person.village()) || state.crises().current(player.getUUID())
                .filter(c -> c.phase()!=KingdomCrisis.Phase.RESOLVED && c.phase()!=KingdomCrisis.Phase.LAPSED).isPresent()) return;
        for(var other:KingdomDiplomacyService.contacts(state,data,person.village())) {
            var memory=state.autonomy().between(person.village(),other.id());
            if(memory.isEmpty() || memory.get().request()==null) continue;
            NpcSpeechService.privateLine(player,npc,Component.translatable("message.village-quest.kingdom.initiative_voice."+person.personality()+"."
                    +memory.get().request().name().toLowerCase(Locale.ROOT),other.name())); return;
        }
    }
}
