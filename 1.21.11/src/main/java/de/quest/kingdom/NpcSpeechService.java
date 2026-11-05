package de.quest.kingdom;

import de.quest.network.NpcSpeechPayload;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Shared PRIVATE/NEARBY dispatcher. Chat and bubbles share the same bounded speech pacing. */
public final class NpcSpeechService {
    private NpcSpeechService() {}
    private static final Map<MinecraftServer,Runtime> RUNTIMES=new WeakHashMap<>();
    private record Reply(String dimension,UUID speaker,UUID other,Component line,long due) {}
    private static final class Runtime { long sequence; final NpcSpeechCooldown cooldown=new NpcSpeechCooldown(); final NpcRefusalDialogue refusal=new NpcRefusalDialogue(); final List<Reply> replies=new ArrayList<>(); }
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            var runtime=RUNTIMES.get(server); if(runtime==null) return; long now=server.overworld().getGameTime();
            var due=runtime.replies.stream().filter(r -> r.due<=now).toList(); runtime.replies.removeAll(due);
            for(var reply:due) {
                var world=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(reply.dimension)));
                if(world==null) continue; var speaker=world.getEntity(reply.speaker); var other=world.getEntity(reply.other);
                if(speaker instanceof de.quest.entity.KingdomNpcEntity npc && other instanceof de.quest.entity.KingdomNpcEntity partner
                        && KingdomNpcService.available(world,npc).isPresent() && KingdomNpcService.available(world,partner).isPresent()
                        && npc.isAlive() && partner.isAlive() && !npc.isSleeping() && !partner.isSleeping() && npc.distanceToSqr(partner)<=36
                        && npc.hasLineOfSight(partner)) nearby(world,npc,reply.line);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(RUNTIMES::remove);
    }
    static void exchange(ServerLevel world,Entity first,Entity second,Component question,Component answer) {
        if(!world.getServer().isSameThread()) return;
        var runtime=RUNTIMES.computeIfAbsent(world.getServer(),s -> new Runtime());
        if(runtime.replies.size()>=32) return;
        nearby(world,first,question);
        runtime.replies.add(new Reply(world.dimension().identifier().toString(),second.getUUID(),first.getUUID(),answer.copy(),world.getServer().overworld().getGameTime()+100));
    }
    public static boolean privateLine(ServerPlayer player,Entity speaker,Component line) {
        if(player==null || speaker==null || line==null) return false;
        return privateLine(player,speaker,line,speaker.getDisplayName().copy().withStyle(ChatFormatting.GOLD)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(line));
    }
    public static boolean privateLine(ServerPlayer player,Entity speaker,Component line,Component fallback) {
        if(player==null || speaker==null || line==null || !(speaker.level() instanceof ServerLevel world)) return false;
        return send(world,player,speaker,line,fallback,NpcSpeechPayload.Scope.PRIVATE,player.getUUID());
    }
    public static void privateRefusal(ServerPlayer actor,de.quest.entity.KingdomNpcEntity npc,UUID person) {
        if(!(npc.level() instanceof ServerLevel world) || actor.level()!=world || !world.getServer().isSameThread()) return;
        var runtime=RUNTIMES.computeIfAbsent(world.getServer(),s -> new Runtime());
        var variant=runtime.refusal.next(person,actor.getUUID(),world.getServer().overworld().getGameTime());
        if(variant.isPresent()) {
            var line=Component.translatable("message.village-quest.market.refusal."+variant.getAsInt(),npc.getDisplayName());
            send(world,actor,npc,line,line,NpcSpeechPayload.Scope.PRIVATE,actor.getUUID(),true);
        }
    }
    public static void nearby(ServerLevel world,Entity speaker,Component line) {
        if(world==null || speaker==null || line==null) return;
        world.getServer().getPlayerList().getPlayers().stream().filter(p -> NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.NEARBY,null,p.getUUID(),p.level()==world,p.distanceToSqr(speaker)))
                .sorted(Comparator.comparingDouble(speaker::distanceToSqr)).limit(16)
                .forEach(player -> send(world,player,speaker,line,line,NpcSpeechPayload.Scope.NEARBY,null));
    }
    private static boolean send(ServerLevel world,ServerPlayer player,Entity speaker,Component line,Component fallback,NpcSpeechPayload.Scope scope,UUID intended) {
        return send(world,player,speaker,line,fallback,scope,intended,false);
    }
    private static boolean send(ServerLevel world,ServerPlayer player,Entity speaker,Component line,Component fallback,NpcSpeechPayload.Scope scope,UUID intended,boolean refusalPaced) {
        if(!world.getServer().isSameThread() || !speaker.isAlive()
                || speaker instanceof de.quest.entity.KingdomNpcEntity npc && npc.isSleeping()
                || !NpcSpeechBuffer.recipient(scope,intended,player.getUUID(),player.level()==world,player.distanceToSqr(speaker))) return false;
        var runtime=RUNTIMES.computeIfAbsent(world.getServer(),s -> new Runtime()); long now=world.getServer().overworld().getGameTime();
        if(!refusalPaced && !runtime.cooldown.allow(speaker.getUUID(),player.getUUID(),line.getString(),now,scope==NpcSpeechPayload.Scope.NEARBY)) return false;
        player.sendSystemMessage(fallback);
        if(line.getString().length()<=1024 && speaker.getDisplayName().getString().length()<=96
                && ServerPlayNetworking.canSend(player,NpcSpeechPayload.ID) && runtime.sequence<Long.MAX_VALUE)
            ServerPlayNetworking.send(player,new NpcSpeechPayload(world.dimension().identifier().toString(),speaker.getUUID(),speaker.getId(),++runtime.sequence,
                    NpcSpeechBuffer.duration(line.getString().length()),scope,speaker.getDisplayName(),line));
        return true;
    }
}
