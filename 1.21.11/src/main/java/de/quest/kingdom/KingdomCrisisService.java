package de.quest.kingdom;

import de.quest.caravan.KingdomRouteConnections;
import de.quest.data.QuestState;
import de.quest.entity.KingdomNpcEntity;
import de.quest.network.BuildingPayloads;
import de.quest.village.VillageCondition;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Existing council/NPC/freight adapters supply all evidence. No chunks, attacks or supplies are created. */
final class KingdomCrisisService {
    private KingdomCrisisService() {}
    private static boolean shortage(VillageCondition condition) { return condition==VillageCondition.CRISIS || condition==VillageCondition.STRAINED; }
    static void observe(ServerPlayer player) {
        var world=player.level(); var server=world.getServer(); if(!server.isSameThread()) return;
        var state=LivingKingdomsState.get(server); long now=server.overworld().getGameTime();
        if(state.crises().readOnly() || !player.isAlive() || player.isSpectator() || player.isSleeping() || player.hurtTime>0
                || player.getLastHurtByMob()!=null && player.tickCount-player.getLastHurtByMobTimestamp()<200) return;
        var initial=state.crises().current(player.getUUID()); var current=initial;
        var anchor=state.anchors().near(world.dimension().identifier().toString(),player.blockPosition(),64,8).stream()
                .filter(a -> a.role()==BuildingScanner.Role.COUNCIL && state.anchors().maySpawn(a.id(),now))
                .filter(a -> initial.filter(v -> v.host().equals(a.villageId()) || v.other().equals(a.villageId())).isPresent()
                        || state.canManageVillage(player.getUUID(),a.villageId()))
                .min(Comparator.comparingDouble(a -> a.position().distSqr(player.blockPosition())));
        if(anchor.isEmpty() || !NativeNpcPathPolicy.loaded(player.blockPosition(),0,12,(x,z) -> world.hasChunkAt(new net.minecraft.core.BlockPos(x*16,player.blockPosition().getY(),z*16)))) return;
        var enemies=new ArrayList<Monster>(); world.getEntities(EntityTypeTest.forClass(Monster.class),new AABB(player.blockPosition()).inflate(12),NativeNpcActivities::hostile,enemies,16);
        if(!enemies.isEmpty()) return;
        if(current.filter(v -> v.host().equals(anchor.get().villageId()) || v.other().equals(anchor.get().villageId())).isPresent()
                && state.canManageVillage(player.getUUID(),current.get().host())) {
            state.crises().advance(player.getUUID(),20,true); repair(player); current=state.crises().current(player.getUUID());
            if(current.filter(v -> v.phase()==KingdomCrisis.Phase.OPEN || v.phase()==KingdomCrisis.Phase.AID).isPresent()
                    && (KingdomMarketDemandService.current(player,current.get().other()).filter(n -> !shortage(n.condition())).isPresent()
                    || current.get().kind()==KingdomCrisis.Kind.LOYALTY && KingdomMarketDemandService.current(player,current.get().host()).filter(n -> !shortage(n.condition())).isPresent()))
                state.crises().settleNaturally(player.getUUID());
            current=state.crises().current(player.getUUID());
            if(current.filter(v -> v.phase()!=KingdomCrisis.Phase.RESOLVED && v.phase()!=KingdomCrisis.Phase.LAPSED).isPresent()) return;
        }
        consider(player,anchor.get().villageId());
    }
    /** Only creates a saved request. Decision time and physical conversations still require local presence. */
    static void consider(ServerPlayer player,UUID host) {
        var server=player.level().getServer();var state=LivingKingdomsState.get(server);
        if(!server.isSameThread() || !state.canManageVillage(player.getUUID(),host) || state.crises().readOnly()
                || state.crises().current(player.getUUID()).filter(v -> v.phase()!=KingdomCrisis.Phase.RESOLVED && v.phase()!=KingdomCrisis.Phase.LAPSED).isPresent())return;
        var known=KingdomDiplomacyService.contacts(state,QuestState.get(server).getPlayerData(player.getUUID()),host);
        for(var deal:state.diplomacy().contracts(host)) {
            if(deal.type()!=KingdomDiplomacy.Type.SUPPLY || deal.status()!=KingdomDiplomacy.Status.SIGNED) continue;
            UUID other=deal.left().equals(host)?deal.right():deal.left(); if(known.stream().noneMatch(v -> v.id().equals(other))) continue;
            var need=KingdomMarketDemandService.current(player,other); if(need.isEmpty() || !shortage(need.get().condition())) continue;
            var person=state.people().ensure(other,KingdomPersonRegistry.Role.LEADER,0); if(person.isEmpty()) continue;
            boolean competing=KingdomMarketDemandService.current(player,host).filter(n -> shortage(n.condition())).isPresent();
            boolean loyalty=competing && state.diplomacy().between(host,other,KingdomDiplomacy.Type.FRIENDSHIP)
                    .filter(d -> d.status()==KingdomDiplomacy.Status.SIGNED && state.diplomacy().met(d)).isPresent();
            var started=loyalty?state.crises().startLoyalty(player.getUUID(),host,other,need.get().need(),need.get().cyclesCompleted(),person.get().personality()):
                    state.crises().start(player.getUUID(),host,other,need.get().need(),need.get().cyclesCompleted(),person.get().personality(),competing);
            if(started.isPresent()) {
                de.quest.quest.WorldNewsService.offer(player,"crisis",Component.translatable(loyalty?"message.village-quest.kingdom.loyalty_open":"message.village-quest.kingdom.crisis_open",state.village(other).orElseThrow().name(),need.get().need().label()),true);
                break;
            }
        }
    }
    static BuildingPayloads.Crisis view(ServerPlayer player,UUID council) {
        var state=LivingKingdomsState.get(player.level().getServer());
        return state.crises().current(player.getUUID()).filter(v -> v.host().equals(council) || v.other().equals(council))
                .map(v -> new BuildingPayloads.Crisis(v.id(),v.revision(),state.village(v.host()).orElseThrow().name(),state.village(v.other()).orElseThrow().name(),
                        v.phase().name().toLowerCase(Locale.ROOT),v.need().key(),v.remaining(),v.heard(),v.personality(),v.competing(),v.kind().name().toLowerCase(Locale.ROOT)))
                .orElse(BuildingPayloads.Crisis.EMPTY);
    }
    static boolean choose(ServerPlayer player,UUID council,BuildingPayloads.Crisis offered,UUID id,long revision,int choice) {
        var server=player.level().getServer(); var state=LivingKingdomsState.get(server);
        var current=state.crises().current(player.getUUID());
        if(!server.isSameThread() || choice<0 || choice>=KingdomCrisis.Choice.values().length || !offered.id().equals(id) || offered.revision()!=revision
                || current.filter(v -> v.id().equals(id) && (v.host().equals(council) || v.other().equals(council))).isEmpty()) return false;
        var before=current.get(); var selected=KingdomCrisis.Choice.values()[choice];
        if(before.kind()==KingdomCrisis.Kind.LOYALTY && before.phase()==KingdomCrisis.Phase.OPEN && (selected==KingdomCrisis.Choice.SUPPORT || selected==KingdomCrisis.Choice.KEEP_HOME)) {
            if(KingdomMarketDemandService.current(player,before.host()).filter(n -> shortage(n.condition())).isEmpty()
                    || KingdomMarketDemandService.current(player,before.other()).filter(n -> shortage(n.condition())).isEmpty()) return false;
        }
        if(!state.crises().choose(player.getUUID(),id,revision,selected)) return false;
        if(selected==KingdomCrisis.Choice.APOLOGIZE && (before.personality()==0 || before.personality()==3))
            state.diplomacy().prepareRepairMeeting(player.getUUID(),before.host(),before.other());
        if(selected==KingdomCrisis.Choice.REOPEN) {
            var deal=state.diplomacy().between(before.host(),before.other(),KingdomDiplomacy.Type.SUPPLY).orElseThrow();
            var need=KingdomMarketDemandService.current(player,before.other());
            state.diplomacy().respond(deal,KingdomNegotiation.answer(before.personality(),deal.type(),deal.terms(),
                    need.map(de.quest.village.LivingVillageNetworkState.VillageSnapshot::need).orElse(null),
                    need.map(de.quest.village.LivingVillageNetworkState.VillageSnapshot::condition).orElse(null)));
        }
        player.sendSystemMessage(Component.translatable("screen.village-quest.building."+(before.kind()==KingdomCrisis.Kind.LOYALTY?"loyalty":"crisis")+"_choice_done."+choice)); repair(player); return true;
    }
    static void talk(ServerPlayer player,KingdomNpcEntity body,KingdomPersonRegistry.View person) {
        var state=LivingKingdomsState.get(player.level().getServer()); var current=state.crises().current(player.getUUID());
        if(current.isEmpty() || person.role()!=KingdomPersonRegistry.Role.LEADER || !player.hasLineOfSight(body) || body.isSleeping()) return;
        var c=current.get(); boolean other=c.other().equals(person.village());
        if(!other && !c.host().equals(person.village())) return;
        String scene=switch(c.phase()) { case OPEN,AID -> "request"; case WITHDRAWN -> "withdrawn"; case RECONCILING,READY -> "repair"; case RESOLVED -> "resolved"; case LAPSED -> "lapsed"; };
        var line=Component.translatable("message.village-quest.kingdom."+(c.kind()==KingdomCrisis.Kind.LOYALTY?"loyalty":"crisis")+"_voice."+(other?c.personality()+".":"home.")+scene,
                state.village(c.host()).orElseThrow().name(),state.village(c.other()).orElseThrow().name(),c.need().label());
        NpcSpeechService.privateLine(player,body,line); state.crises().hear(player.getUUID(),person.village()); repair(player);
    }
    private static void repair(ServerPlayer player) {
        var server=player.level().getServer(); var state=LivingKingdomsState.get(server); var current=state.crises().current(player.getUUID());
        if(current.filter(v -> v.phase()==KingdomCrisis.Phase.RECONCILING).isEmpty()) return; var c=current.get(); boolean done;
        if(c.personality()==0 || c.personality()==3) done=state.diplomacy().between(c.host(),c.other(),KingdomDiplomacy.Type.FRIENDSHIP)
                .filter(d -> d.revision()>state.crises().meetingBaseline(player.getUUID()) && (d.status()==KingdomDiplomacy.Status.SIGNED || state.diplomacy().repairMeeting(d)) && state.diplomacy().met(d)).isPresent();
        else if(c.personality()==1) done=KingdomMarketDemandService.current(player,c.other()).filter(n -> !shortage(n.condition())).isPresent();
        else done=KingdomRouteConnections.read(server).stream().anyMatch(r -> r.active() && r.joins(c.host(),c.other()))
                && (c.kind()!=KingdomCrisis.Kind.LOYALTY || state.crises().freshRepairDelivery(player.getUUID()));
        state.crises().ready(player.getUUID(),done);
    }
}
