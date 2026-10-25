package de.quest.quest.special;

import de.quest.entity.KingdomNpcEntity;
import de.quest.kingdom.*;
import de.quest.network.FriendsQuestPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;

/** One ephemeral conversation per player; mutations stay in the existing quest service. */
public final class FriendsQuestUiService {
    private FriendsQuestUiService() {}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        final UUID id=UUID.randomUUID(),body;final MinecraftServer server;final String dimension,visit;
        final long expires;long revision;View last;Component lore;
        Session(ServerLevel world,KingdomNpcEntity npc,String visit){body=npc.getUUID();server=world.getServer();dimension=world.dimension().identifier().toString();this.visit=visit;expires=server.overworld().getGameTime()+6000;}
    }
    public static Component ui(String key,Object... args){return Component.translatable("screen.village-quest.friends."+key,args);}
    public static void disconnected(UUID player){SESSIONS.remove(player);}
    public static void stopped(MinecraftServer server){SESSIONS.values().removeIf(s->s.server==server);}
    public static String who(int guest){return switch(guest){case 0->"lutte";case 1->"simon";case 2->"marius";default->"ravel";};}
    public static void open(ServerLevel world,ServerPlayer player,KingdomNpcEntity npc,String visit) {
        openLore(world,player,npc,visit,null);
    }
    public static void openLore(ServerLevel world,ServerPlayer player,KingdomNpcEntity npc,String visit,Component lore) {
        if(!ServerPlayNetworking.canSend(player,View.ID))return;
        var session=new Session(world,npc,visit);session.lore=lore==null?null:lore.copy();
        if(valid(player,session).isEmpty())return;
        SESSIONS.put(player.getUUID(),session);send(player,session,true);
    }
    private static Optional<KingdomPersonRegistry.View> valid(ServerPlayer player,Session s) {
        if(!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel world)
                || world.getServer()!=s.server || !world.dimension().identifier().toString().equals(s.dimension)
                || s.server.overworld().getGameTime()>s.expires
                || !(world.getEntity(s.body) instanceof KingdomNpcEntity npc) || !npc.isAlive() || npc.isSleeping()
                || player.distanceToSqr(npc)>64 || !player.hasLineOfSight(npc))return Optional.empty();
        var person=KingdomNpcService.available(world,npc);if(person.isEmpty() || person.get().guest()<0 || person.get().guest()>3)return Optional.empty();
        var state=LivingKingdomsState.get(world.getServer());var v=person.get();
        if(!s.visit.equals(v.village()+":"+state.tavernGuests().visitToken(v.village())))return Optional.empty();
        var inn=state.anchors().official(v.village(),BuildingScanner.Role.INN);
        return inn.isPresent() && state.anchors().mayContinue(inn.get().id(),world.getServer().overworld().getGameTime())
                && npc.blockPosition().distSqr(inn.get().position())<=64?person:Optional.empty();
    }
    static boolean matches(View shown,Action action,View current) {
        return shown!=null && shown.session().equals(action.session()) && shown.revision()==action.revision()
                && shown.who().equals(current.who()) && shown.chapter()==current.chapter() && shown.active()==current.active()
                && shown.options().stream().anyMatch(o->o.enabled() && o.action().equals(action.action()))
                && current.options().stream().anyMatch(o->o.enabled() && o.action().equals(action.action()));
    }
    public static void handle(ServerPlayer player,Action action) {
        var s=SESSIONS.get(player.getUUID());if(s==null || !s.id.equals(action.session()))return;
        if(action.action().equals("close")){SESSIONS.remove(player.getUUID());return;}
        var person=valid(player,s);if(person.isEmpty()){close(player,s);return;}
        var world=(ServerLevel)player.level();var current=view(world,player,s,person.get(),false,true);
        if(!matches(s.last,action,current)){
            // A rejected click still needs an acknowledgement even when its live data is unchanged.
            s.revision++;send(player,s,false);return;
        }
        // Consume the displayed revision before executing. Repeated packets cannot repeat an operation.
        s.revision++;
        FriendsQuestService.command(player,s.body.toString(),action.action());
        send(player,s,false);
    }
    public static void tick(MinecraftServer server) {
        if(server.overworld().getGameTime()%20!=0)return;
        for(var entry:new ArrayList<>(SESSIONS.entrySet())) {
            var s=entry.getValue();if(s.server!=server)continue;
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null){SESSIONS.remove(entry.getKey());continue;}
            send(player,s,false);
        }
    }
    private static void send(ServerPlayer player,Session s,boolean initial) {
        var person=valid(player,s);if(person.isEmpty()){close(player,s);return;}
        var next=view((ServerLevel)player.level(),player,s,person.get(),initial,true);
        // Do not invalidate an otherwise identical displayed revision every second.
        if(!initial && next.equals(s.last))return;
        s.revision++;
        next=view((ServerLevel)player.level(),player,s,person.get(),initial,true);s.last=next;
        ServerPlayNetworking.send(player,next);
    }
    private static void close(ServerPlayer player,Session s) {
        SESSIONS.remove(player.getUUID());
        if(s.last!=null && ServerPlayNetworking.canSend(player,View.ID)) {
            var v=s.last;ServerPlayNetworking.send(player,new View(v.session(),++s.revision,false,false,v.who(),v.chapter(),v.active(),v.name(),v.title(),v.dialogue(),List.of(),Component.empty(),List.of(),List.of(),List.of()));
        }
    }
    private static View view(ServerLevel world,ServerPlayer player,Session s,KingdomPersonRegistry.View person,boolean initial,boolean valid) {
        String who=who(person.guest());var p=FriendsQuestService.progress(world,player.getUUID());
        if(who.equals("ravel"))return new View(s.id,s.revision,initial,valid,who,0,false,person.displayName(),ui("conversation"),
                s.lore==null?ui("conversation"):s.lore,List.of(),Component.empty(),List.of(),List.of(),List.of());
        return snapshot(world,player,s.id,s.revision,initial,valid,who,person.displayName(),p);
    }
    static View snapshot(ServerLevel world,ServerPlayer player,UUID session,long revision,boolean initial,boolean valid,String who,Component name,FriendsQuestProgress p) {
        int stage=p.stage(who);boolean active=p.active(who),herbs=who.equals("simon") && stage==3 && p.number("simon.herbs")==1;
        String quest=herbs?"simon.herbs":who+"."+(stage+1);
        Component title=stage<3 || herbs?FriendsQuestService.tr(quest+".title"):ui("conversation");
        Component dialogue=stage<3?FriendsQuestService.tr(quest+".dialogue"):FriendsQuestService.tr(who+".again");
        var objectives=new ArrayList<Component>();var notices=new ArrayList<Component>();var options=new ArrayList<Option>();var progress=new ArrayList<Progress>();
        Component reward=Component.empty();
        if(stage<3 || herbs) {
            objectives.add(FriendsQuestService.tr(quest+".objective"));reward=FriendsQuestService.tr(quest+".reward");
            if(active && !(who.equals("simon") && stage==0))objectives.addAll(FriendsQuestService.lines(p,who).stream().filter(c->!c.equals(objectives.getFirst())).toList());
            if((who.equals("simon") && stage==0) || herbs)for(var item:List.of(Items.DANDELION,Items.POPPY,Items.FERN))progress.add(new Progress(new ItemStack(item),count(player,item),8));
            if(who.equals("lutte") && stage==2)for(var cost:FriendsQuestService.forgeCosts().entrySet().stream().sorted(java.util.Comparator.comparing(e->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString())).toList())progress.add(new Progress(new ItemStack(cost.getKey()),count(player,cost.getKey()),cost.getValue()));
            boolean ready=herbs?progress.stream().allMatch(v->v.carried()>=v.required()):active && FriendsQuestService.ready(world,player,who);
            options.add(new Option(herbs?"herbs":active?"claim":"accept",FriendsQuestService.tr(active || herbs?"turn_in":"accept"),!active && !herbs || ready));
            if(active || herbs)notices.add(ui(ready?"ready":"in_progress"));
        }
        if(who.equals("lutte") && active && stage==1) {
            if(!LutteCoastalQuest.hasMap(player,p)) {
                notices.add(FriendsQuestService.tr(p.text("lutte.replacementDue").isBlank()?p.number("lutte.replacements")>=2?"lutte.replace.expensive":"lutte.replace":"lutte.replace.pending"));
                options.add(new Option("replace",ui("replace"),true));
            }
            if(p.number("lutte.cursor")>=8192 && p.number("lutte.targets")<3)options.add(new Option("survey",ui("survey"),true));
        }
        if(who.equals("marius") && active && stage==2 && p.number("marius.raidPhase")!=2 && p.number("marius.victory")==0) {
            options.add(new Option("ready",FriendsQuestService.tr("marius.ready"),true));options.add(new Option("prepare",FriendsQuestService.tr("marius.prepare"),true));
        }
        if(who.equals("lutte") && stage==2 && !de.quest.quest.story.StoryQuestService.isCompleted(world,player.getUUID(),de.quest.quest.story.StoryArcType.SILENT_FORGE)) {
            notices.add(FriendsQuestService.tr("lutte.forge_locked"));
            options.set(0,new Option(active?"claim":"accept",FriendsQuestService.tr(active?"turn_in":"accept"),false));
        }
        var rewards=stage<3 || herbs?FriendsQuestRewards.create(world.registryAccess(),who,Math.min(3,stage+1),herbs,player.getUUID()):Rewards.EMPTY;
        if(who.equals("lutte") && stage==2 && active) {
            // Accepted choices were frozen once; inspect them without preparing/redeeming anything.
            var frozen=de.quest.reward.VanillaRewardService.state(world,player.getUUID()).snapshot("friends_lutte","lutte_masterpiece");
            if(!frozen.isEmpty()) {
                var items=new ArrayList<ItemStack>();
                for(var entry:frozen.getListOrEmpty("choices"))for(var raw:((net.minecraft.nbt.CompoundTag)entry).getListOrEmpty("items"))items.add(de.quest.reward.RewardItems.decode(world,raw));
                rewards=new Rewards(0,0,items,true);
            }
        }
        return new View(session,revision,initial,valid,who,Math.min(3,stage+1),active,name,title,dialogue,objectives,reward,notices,progress,options,rewards);
    }
    private static int count(ServerPlayer player,Item item){int total=0;for(int i=0;i<36;i++){var s=player.getInventory().getItem(i);if(s.is(item))total+=s.getCount();}return total;}
}
