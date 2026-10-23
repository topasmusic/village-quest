package de.quest.kingdom;

import de.quest.caravan.TradeRouteService;
import java.util.*;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Private addressed warning; only a fresh deliberate refusal creates blame. Expiry/restart/unknown are neutral. */
public final class KingdomAttackService {
    private KingdomAttackService() {}
    private record Warning(TradeRouteService.AttackOpportunity attack,long issued,boolean helping) {}
    private static final Map<MinecraftServer,Map<UUID,Warning>> WARNINGS=new WeakHashMap<>();
    static void observe(ServerPlayer player) {
        var server=player.level().getServer(); if(!server.isSameThread()) return;
        var cache=WARNINGS.computeIfAbsent(server,s -> new HashMap<>()); long now=server.overworld().getGameTime();
        cache.entrySet().removeIf(e -> now<e.getValue().issued || now-e.getValue().issued>2400 || server.getPlayerList().getPlayer(e.getKey())==null);
        var attack=TradeRouteService.attackOpportunity(player); if(attack.isEmpty()) return;
        var relations=LivingKingdomsState.get(server).relations(); var a=attack.get();
        if(relations.attackSeen(player.getUUID(),a.village(),a.incident())) return;
        var old=cache.get(player.getUUID());
        if(old!=null && old.attack.incident().equals(a.incident()) && old.helping) return;
        cache.put(player.getUUID(),new Warning(a,now,false));
        var quests=de.quest.data.QuestState.get(server);var data=quests.getPlayerData(player.getUUID());
        if(a.incident().toString().equals(data.getTradeRouteString("kingdoms.attack.notice")))return;
        data.setTradeRouteString("kingdoms.attack.notice",a.incident().toString());quests.setDirty();
        String town=LivingKingdomsState.get(server).village(a.village()).orElseThrow().name();
        player.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_warning",town,a.position().getX(),a.position().getZ(),a.incident().toString()));
        player.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_help")
                .withStyle(s -> s.withColor(0x57A550).withClickEvent(new ClickEvent.RunCommand("/vq aid help "+a.incident())))
                .append(Component.literal("  ")).append(Component.translatable("message.village-quest.kingdom.attack_refuse")
                .withStyle(s -> s.withColor(0xA55252).withClickEvent(new ClickEvent.RunCommand("/vq aid refuse "+a.incident())))));
    }
    public static int choose(ServerPlayer player,String incident,boolean help) {
        if(player==null || !player.level().getServer().isSameThread()) return 0;
        var server=player.level().getServer(); long now=server.overworld().getGameTime();
        var cache=WARNINGS.get(server); var warning=cache==null?null:cache.get(player.getUUID());
        UUID id; try { id=UUID.fromString(incident); } catch(IllegalArgumentException malformed) { return 0; }
        var actual=TradeRouteService.attackOpportunity(player);
        if(warning==null || warning.helping || now<warning.issued || now-warning.issued>200 || !warning.attack.incident().equals(id)
                || actual.filter(a -> a.incident().equals(id) && a.village().equals(warning.attack.village()) && a.connection().equals(warning.attack.connection())).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_neutral")); return 0;
        }
        if(help) {
            cache.put(player.getUUID(),new Warning(warning.attack,now,true));
            player.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_help_chosen")); return 1;
        }
        var relations=LivingKingdomsState.get(server).relations(); boolean distrusted=relations.distrusts(player.getUUID(),warning.attack.village());
        if(!relations.refusedAttack(player.getUUID(),warning.attack.village(),id)) return 0;
        cache.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable(!distrusted && relations.distrusts(player.getUUID(),warning.attack.village())
                ?"message.village-quest.kingdom.attack_distrust":"message.village-quest.kingdom.attack_refused",
                LivingKingdomsState.get(server).village(warning.attack.village()).orElseThrow().name())); return 1;
    }
    public static int apologize(ServerPlayer player) {
        if(player==null || !player.level().getServer().isSameThread() || !player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.hurtTime>0 || player.getLastHurtByMob()!=null && player.tickCount-player.getLastHurtByMobTimestamp()<200) return 0;
        var state=LivingKingdomsState.get(player.level().getServer()); long now=player.level().getServer().overworld().getGameTime();
        var candidates=state.people().loadedPeople().stream().filter(p -> p.role()==KingdomPersonRegistry.Role.LEADER && p.visit()==null
                && state.relations().distrusts(player.getUUID(),p.village()) && p.body()!=null)
                .filter(p -> player.level().getEntity(p.body()) instanceof de.quest.entity.KingdomNpcEntity npc && npc.isAlive() && !npc.isSleeping()
                        && player.distanceToSqr(npc)<=64 && player.hasLineOfSight(npc) && KingdomNpcService.available(player.level(),npc).isPresent())
                .filter(p -> state.anchors().official(p.village(),BuildingScanner.Role.COUNCIL).filter(a -> state.anchors().maySpawn(a.id(),now)
                        && a.dimension().equals(player.level().dimension().identifier().toString()) && a.position().distSqr(player.blockPosition())<=64).isPresent()).toList();
        if(candidates.size()!=1) { player.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_apology_unavailable")); return 0; }
        var leader=candidates.getFirst(); var need=KingdomMarketDemandService.current(player,leader.village());
        if(need.isEmpty() || !state.relations().apologizeAttack(player.getUUID(),leader.village(),need.get().need())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_apology_unavailable")); return 0;
        }
        var body=(de.quest.entity.KingdomNpcEntity)player.level().getEntity(leader.body());
        NpcSpeechService.privateLine(player,body,Component.translatable("message.village-quest.kingdom.attack_apology."+leader.personality(),
                state.village(leader.village()).orElseThrow().name(),state.relations().attackRepairNeed(player.getUUID(),leader.village()).orElseThrow().label())); return 1;
    }
    /** Existing solo ambush resolver has verified the owner completed the actual hostile incident. No new payout. */
    public static void helped(ServerLevel world,ServerPlayer actor,String incident) {
        var server=world.getServer(); if(!server.isSameThread() || actor.level()!=world) return;
        var cache=WARNINGS.get(server); var warning=cache==null?null:cache.get(actor.getUUID()); long now=server.overworld().getGameTime();
        if(warning==null || !warning.helping || now<warning.issued || now-warning.issued>2400 || !warning.attack.incident().toString().equals(incident)) return;
        cache.remove(actor.getUUID()); var state=LivingKingdomsState.get(server); boolean before=state.relations().distrusts(actor.getUUID(),warning.attack.village());
        if(state.relations().helpedAttack(actor.getUUID(),warning.attack.village(),warning.attack.incident()) && before)
            actor.sendSystemMessage(Component.translatable("message.village-quest.kingdom.attack_repaired",state.village(warning.attack.village()).orElseThrow().name()));
    }
    static void talk(ServerPlayer actor,de.quest.entity.KingdomNpcEntity body,UUID village) {
        var state=LivingKingdomsState.get(actor.level().getServer());
        if(state.relations().distrusts(actor.getUUID(),village)) {
            var pending=state.relations().attackRepairNeed(actor.getUUID(),village); var person=KingdomNpcService.available(actor.level(),body);
            var line=pending.isPresent() && person.isPresent()?Component.translatable("message.village-quest.kingdom.attack_apology."+person.get().personality(),
                    state.village(village).orElseThrow().name(),pending.get().label()):Component.translatable("message.village-quest.kingdom.attack_memory");
            NpcSpeechService.privateLine(actor,body,line);
        }
    }
}
