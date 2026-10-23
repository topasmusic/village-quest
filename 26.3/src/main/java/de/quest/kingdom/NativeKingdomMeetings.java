package de.quest.kingdom;

import de.quest.entity.KingdomNpcEntity;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Only actual loaded bodies at a freshly verified council can complete a friendship meeting. */
final class NativeKingdomMeetings {
    private NativeKingdomMeetings() {}
    static boolean act(ServerPlayer actor,UUID host,UUID other,long revision,int choice) {
        if(!(actor.level() instanceof ServerLevel world) || !world.getServer().isSameThread() || choice<0 || choice>2) return false;
        var state=LivingKingdomsState.get(world.getServer()); var diplomacy=state.diplomacy();
        var deal=diplomacy.between(host,other,KingdomDiplomacy.Type.FRIENDSHIP);
        var anchor=state.anchors().official(host,BuildingScanner.Role.COUNCIL); long now=world.getServer().overworld().getGameTime();
        if(deal.isEmpty() || deal.get().revision()!=revision || anchor.isEmpty()
                || !anchor.get().dimension().equals(world.dimension().identifier().toString())
                || actor.blockPosition().distSqr(anchor.get().position())>64 || !state.anchors().maySpawn(anchor.get().id(),now)) return false;
        var guest=state.people().ensure(other,KingdomPersonRegistry.Role.LEADER,0); if(guest.isEmpty()) return false;
        UUID former=guest.get().body();
        if(choice==0) {
            boolean started=diplomacy.invite(actor.getUUID(),deal.get().id(),revision,host,now).isPresent();
            if(started) discard(world,former); return started;
        }
        if(choice==2) {
            boolean cancelled=diplomacy.cancelMeeting(actor.getUUID(),deal.get().id(),revision,host);
            if(cancelled) discard(world,former); return cancelled;
        }
        var local=state.people().ensure(host,KingdomPersonRegistry.Role.LEADER,0); var floor=state.anchors().report(anchor.get().id());
        if(local.isEmpty() || floor.isEmpty() || local.get().body()==null || former==null
                || !(world.getEntity(local.get().body()) instanceof KingdomNpcEntity leader)
                || !(world.getEntity(former) instanceof KingdomNpcEntity visitor)
                || !leader.isAlive() || !visitor.isAlive() || leader.isSleeping() || visitor.isSleeping()
                || KingdomNpcService.available(world,leader).isEmpty() || KingdomNpcService.available(world,visitor).isEmpty()
                || actor.distanceToSqr(leader)>64 || actor.distanceToSqr(visitor)>64 || leader.distanceToSqr(visitor)>36
                || !actor.hasLineOfSight(leader) || !actor.hasLineOfSight(visitor)
                || !floor.get().walkable().contains(leader.blockPosition()) || !floor.get().walkable().contains(visitor.blockPosition())) return false;
        boolean repair=diplomacy.repairMeeting(deal.get());
        boolean completed=diplomacy.finishMeeting(actor.getUUID(),deal.get().id(),revision,host,leader.getUUID(),visitor.getUUID(),now);
        if(completed) {
            NativeNpcActivities.say(world,leader,net.minecraft.network.chat.Component.translatable(repair?"message.village-quest.kingdom.meeting_repair":"message.village-quest.kingdom.meeting_signed",
                    leader.getDisplayName(),visitor.getDisplayName(),state.village(host).orElseThrow().name(),state.village(other).orElseThrow().name()));
            discard(world,former);
        }
        return completed;
    }
    private static void discard(ServerLevel world,UUID body) {
        if(body!=null && world.getEntity(body) instanceof KingdomNpcEntity npc) npc.discard();
    }
}
