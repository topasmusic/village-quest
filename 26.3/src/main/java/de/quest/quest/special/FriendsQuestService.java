package de.quest.quest.special;

import de.quest.data.*;
import de.quest.kingdom.*;
import de.quest.entity.KingdomNpcEntity;
import de.quest.network.Payloads;
import de.quest.quest.*;
import de.quest.quest.daily.DailyQuestService;
import de.quest.reward.*;
import de.quest.economy.CurrencyService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Guest-bound acceptance and turn-in; personal progression continues while guests are away. */
public final class FriendsQuestService {
    private FriendsQuestService() {}
    public static FriendsQuestProgress progress(ServerLevel world,UUID player) {
        return new FriendsQuestProgress(QuestState.get(world.getServer()).getPlayerData(player));
    }
    public static MutableComponent tr(String suffix,Object... args) { return Component.translatable("quest.village-quest.friends."+suffix,args); }
    private static int stat(ServerPlayer player,net.minecraft.resources.Identifier id) { return DailyQuestService.getCustomStat(player,id); }
    public static void changed(ServerLevel world,ServerPlayer player) {
        QuestState.get(world.getServer()).setDirty(); QuestBookHelper.refreshQuestBook(world,player); QuestTrackerService.refresh(world,player);
        de.quest.questmaster.QuestMasterUiService.refreshIfOpen(world,player);
    }
    public static void talk(ServerLevel world,ServerPlayer player,KingdomNpcEntity npc,KingdomPersonRegistry.View person,String visit) {
        String who=person.guest()==1?"simon":person.guest()==0?"lutte":person.guest()==2?"marius":""; if(who.isEmpty()) return;
        var p=progress(world,player.getUUID()); p.number(who+".met",1);
        if(who.equals("simon") && p.stage(who)==3 && !visit.equals(p.text("simon.lastVisit"))) p.rollHerbs(visit,world.getRandom().nextInt(4));
        if(who.equals("simon")) p.text("simon.currentVisit",visit);
        QuestState.get(world.getServer()).setDirty();
        int stage=p.stage(who); String quest=who+"."+Math.min(3,stage+1);
        Component line=stage>=3?tr(who+".again"):tr(quest+".dialogue");
        NpcSpeechService.privateLine(player,npc,line);
        if(who.equals("lutte") && p.active(who) && stage==1 && !LutteCoastalQuest.hasMap(player,p)) {
            player.sendSystemMessage(tr(p.text("lutte.replacementDue").isBlank()?p.number("lutte.replacements")>=2?"lutte.replace.expensive":"lutte.replace":"lutte.replace.pending"),false);
            choice(player,npc,"replace","lutte.replace.button");
        }
        if(who.equals("lutte") && p.active(who) && stage==1 && p.number("lutte.cursor")>=8192 && p.number("lutte.targets")<3) choice(player,npc,"survey","lutte.survey.button");
        if(who.equals("marius") && p.active(who) && stage==2) {
            if(p.number("marius.raidPhase")!=2 && p.number("marius.victory")==0) {choice(player,npc,"ready","marius.ready");choice(player,npc,"prepare","marius.prepare");}
            for(var status:MariusQuestService.lines(p))player.sendSystemMessage(status,false);
        }
        if(stage<3) {
            player.sendSystemMessage(tr(quest+".objective"),false); player.sendSystemMessage(tr(quest+".reward"),false);
            choice(player,npc,p.active(who)?"claim":"accept",p.active(who)?"turn_in":"accept");
        } else if(who.equals("simon") && p.number("simon.herbs")==1) {
            player.sendSystemMessage(tr("simon.herbs.objective"),false); choice(player,npc,"herbs","turn_in");
        }
    }
    private static void choice(ServerPlayer player,KingdomNpcEntity npc,String action,String label) {
        player.sendSystemMessage(tr(label).withStyle(s -> s.withColor(0xE5BC56).withClickEvent(new ClickEvent.RunCommand("/vq friends "+npc.getUUID()+" "+action))),false);
    }
    public static int command(ServerPlayer player,String body,String action) {
        if(player==null || !player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel world)) return 0;
        UUID id; try { id=UUID.fromString(body); } catch(IllegalArgumentException invalid) { return 0; }
        if(!(world.getEntity(id) instanceof KingdomNpcEntity npc) || !npc.isAlive() || npc.isSleeping() || player.distanceToSqr(npc)>64 || !player.hasLineOfSight(npc)) return 0;
        var person=KingdomNpcService.available(world,npc); if(person.isEmpty() || person.get().guest()<0 || person.get().guest()>2) return 0;
        var v=person.get(); var state=LivingKingdomsState.get(world.getServer());
        var inn=state.anchors().official(v.village(),BuildingScanner.Role.INN);
        if(inn.isEmpty() || !state.anchors().mayContinue(inn.get().id(),world.getServer().overworld().getGameTime()) || npc.blockPosition().distSqr(inn.get().position())>64) return 0;
        String who=v.guest()==1?"simon":v.guest()==2?"marius":"lutte"; var p=progress(world,player.getUUID());
        p.text("simon.currentVisit",v.village()+":"+state.tavernGuests().visitToken(v.village()));
        if(who.equals("marius") && p.active(who) && p.stage(who)==2 && (action.equals("ready") || action.equals("prepare"))) {
            if(action.equals("prepare")) {if(p.number("marius.raidPhase")!=2) {p.number("marius.raidPhase",0);p.text("marius.raidDue","");changed(world,player);}player.sendSystemMessage(tr("marius.take_time"),false);return 1;}
            var town=state.village(v.village());if(town.isEmpty())return 0;
            return MariusQuestService.schedule(world,player,p,town.get().id(),new BlockPos(town.get().legacyKey().anchorX(),npc.blockPosition().getY(),town.get().legacyKey().anchorZ()))?1:0;
        }
        if(who.equals("lutte") && p.active(who) && p.stage(who)==1) {
            if(action.equals("replace")) { boolean result=LutteCoastalQuest.replacement(world,player,p); changed(world,player);return result?1:0; }
            if(action.equals("survey") && p.number("lutte.cursor")>=8192 && p.number("lutte.targets")<3 && world.dimension()==Level.OVERWORLD) {
                p.number("lutte.origin.x",player.blockPosition().getX());p.number("lutte.origin.y",player.blockPosition().getY());p.number("lutte.origin.z",player.blockPosition().getZ());p.number("lutte.cursor",0);changed(world,player);return 1;
            }
        }
        if(action.equals("accept") && p.stage(who)<3 && !p.active(who)) {
            if(who.equals("lutte")) {
                if(p.stage(who)==1 && !LutteCoastalQuest.accept(world,player,p)) return 0;
                if(p.stage(who)==2 && (!de.quest.quest.story.StoryQuestService.isCompleted(world,player.getUUID(),de.quest.quest.story.StoryArcType.SILENT_FORGE)
                        || !VanillaRewardService.prepare(world,player,"friends_lutte","friends:lutte","lutte_masterpiece",null,() -> {}))) {
                    player.sendSystemMessage(tr("lutte.forge_locked"),false);return 0;
                }
            }
            if(p.accept(who,stat(player,Stats.WALK_ONE_CM),stat(player,Stats.SPRINT_ONE_CM),stat(player,Stats.CROUCH_ONE_CM))) {
                if(who.equals("lutte") && p.stage(who)==1) VanillaRewardService.deliverItems(world,player);
                QuestTrackerService.enableForAcceptedQuest(world,player); changed(world,player); player.sendSystemMessage(tr("accepted",tr(who+"."+(p.stage(who)+1)+".title")),false);
                if(who.equals("marius") && p.stage(who)==2) {choice(player,npc,"ready","marius.ready");choice(player,npc,"prepare","marius.prepare");}
                return 1;
            }
        } else if(action.equals("claim") && p.active(who)) {
            if(ready(world,player,who) && turnIn(world,player,who)) { changed(world,player); player.sendSystemMessage(tr("completed"),false); return 1; }
            player.sendSystemMessage(tr("not_ready"),false);
        } else if(action.equals("herbs") && who.equals("simon") && p.stage(who)==3 && p.number("simon.herbs")==1) {
            var costs=plants(); var goods=List.of(new ItemStack(de.quest.registry.ModItems.WEATHER_TONIC));
            if(has(player,costs) && enqueue(world,player,goods)) { consume(player,costs); p.number("simon.herbs",0); changed(world,player); VanillaRewardService.deliverItems(world,player); return 1; }
            player.sendSystemMessage(tr("not_ready"),false);
        }
        return 0;
    }
    private static Map<Item,Integer> plants() { return Map.of(Items.DANDELION,8,Items.POPPY,8,Items.FERN,8); }
    public static boolean has(ServerPlayer player,Map<Item,Integer> costs) {
        for(var cost:costs.entrySet()) { int count=0; for(int i=0;i<36;i++) { var s=player.getInventory().getItem(i); if(s.is(cost.getKey())) count+=s.getCount(); } if(count<cost.getValue()) return false; } return true;
    }
    public static void consume(ServerPlayer player,Map<Item,Integer> costs) {
        for(var cost:costs.entrySet()) { int left=cost.getValue(); for(int i=0;i<36 && left>0;i++) { var s=player.getInventory().getItem(i); if(s.is(cost.getKey())) { int take=Math.min(left,s.getCount()); s.shrink(take); left-=take; } } }
        player.getInventory().setChanged();
    }
    public static boolean enqueue(ServerLevel world,ServerPlayer player,List<ItemStack> goods) {
        var rewards=VanillaRewardService.state(world,player.getUUID()); if(!rewards.canEnqueue(goods.size())) return false;
        var pending=rewards.pending(); for(var raw:RewardItems.encode(world,goods)) pending.add(raw.copy()); rewards.pending(pending); QuestState.get(world.getServer()).setDirty(); return true;
    }
    private static boolean turnIn(ServerLevel world,ServerPlayer player,String who) {
        var p=progress(world,player.getUUID()); int stage=p.stage(who);
        if(who.equals("marius")) return MariusQuestService.turnIn(world,player,p);
        if(who.equals("lutte")) return turnInLutte(world,player,p);
        List<ItemStack> goods;
        if(stage==0) { var potion=new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.SWIFTNESS)); goods=List.of(potion.copy(),potion.copy(),potion.copy()); }
        else if(stage==1) { var mark=new ItemStack(Items.FEATHER); mark.set(DataComponents.ITEM_NAME,tr("simon.keepsake")); goods=List.of(mark); }
        else goods=List.of(new ItemStack(de.quest.registry.ModItems.WEATHER_TONIC,3));
        if(!enqueue(world,player,goods)) return false;
        if(stage==0) consume(player,plants());
        if(!p.complete(who)) throw new IllegalStateException("Guest quest state changed during server turn-in");
        if(stage==2) p.text("simon.finishVisit",p.text("simon.currentVisit"));
        CurrencyService.addBalance(world,player.getUUID(),new int[]{10,30,25}[stage]);
        experience(player,stage==0?1.5:4); VanillaRewardService.deliverItems(world,player); return true;
    }
    private static Map<Item,Integer> forgeCosts() {return Map.of(Items.NETHERITE_INGOT,1,Items.REDSTONE,16,Items.AMETHYST_SHARD,8);}
    private static boolean turnInLutte(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
        int stage=p.stage("lutte");
        if(stage==2) {
            var rewards=VanillaRewardService.state(world,player.getUUID());var snapshot=rewards.snapshot("friends_lutte","lutte_masterpiece");
            if(!rewards.writable() || snapshot.isEmpty() || snapshot.getBooleanOr("redeemed",true) || rewards.earned().size()>=32) return false;
            consume(player,forgeCosts());p.complete("lutte");QuestState.get(world.getServer()).setDirty();
            VanillaRewardService.complete(world,player,"friends_lutte","lutte_masterpiece");return true;
        }
        List<ItemStack> goods=stage==1?RewardItems.create(world,List.of(new VanillaRewardRegistry.Goods("enchanted_book",1,"",Map.of("unbreaking",2)))):List.of();
        if(!enqueue(world,player,goods)) return false;
        if(!p.complete("lutte")) return false;
        if(stage==1)LutteCoastalQuest.finished(world,player.getUUID());
        CurrencyService.addBalance(world,player.getUUID(),stage==0?12:20);experience(player,stage==0?2:3);
        VanillaRewardService.deliverItems(world,player);return true;
    }
    public static void experience(ServerPlayer player,double bars) {
        int levels=(int)bars; player.giveExperienceLevels(levels); if(bars>levels) player.giveExperiencePoints(Math.max(1,(int)Math.round((bars-levels)*player.getXpNeededForNextLevel())));
    }
    public static boolean ready(ServerLevel world,ServerPlayer player,String who) {
        var p=progress(world,player.getUUID()); if(!p.active(who)) return false;
        if(who.equals("marius")) return new MariusQuestProgress(p).ready();
        if(who.equals("simon")) return switch(p.stage(who)) { case 0 -> has(player,plants()); case 1 -> p.number("simon.walk")>=20000; case 2 -> p.friendsReady(); default -> false; };
        return switch(p.stage(who)) {
            case 0 -> p.values("lutte.biomes").size()>=3;
            case 1 -> LutteCoastalQuest.ready(world,player.getUUID(),p);
            case 2 -> de.quest.quest.story.StoryQuestService.isCompleted(world,player.getUUID(),de.quest.quest.story.StoryArcType.SILENT_FORGE) && has(player,forgeCosts());
            default -> false;
        };
    }
    public static void applyWeather(ServerLevel world,WeatherTonicState state) {
        var weather=world.getWeatherData(); weather.setClearWeatherTime(state.rain()?0:state.remaining()+1);
        weather.setRainTime(state.remaining()+1); weather.setThunderTime(state.remaining()+1);
        weather.setRaining(state.rain()); weather.setThundering(false); weather.setDirty();
    }
    public static void onServerTick(MinecraftServer server) {
        var state=QuestState.get(server); var weather=state.weatherTonic();
        if(weather.remaining()>0 && weather.writable()) {
            // Reapply after sleep and administrative weather changes. No time-command or restart catch-up.
            applyWeather(server.overworld(),weather);
            weather.tick(); state.setDirty();
        }
        if(server.overworld().getGameTime()%20!=0) return;
        var players=new ArrayList<>(server.getPlayerList().getPlayers());
        if(!players.isEmpty()) Collections.rotate(players,-Math.floorMod(server.overworld().getGameTime()/20,players.size()));
        int surveyBudget=128;
        for(var player:players) {
            if(!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel world)) continue;
            var p=progress(world,player.getUUID()); boolean change=false;
            if(p.active("simon") && p.stage("simon")==1) { int before=p.number("simon.walk"); p.walking(stat(player,Stats.WALK_ONE_CM),stat(player,Stats.SPRINT_ONE_CM),stat(player,Stats.CROUCH_ONE_CM)); change=p.number("simon.walk")!=before; }
            if(p.active("lutte") && p.stage("lutte")==0 && world.dimension()==Level.OVERWORLD) {
                var at=player.blockPosition();
                var biome=world.getBiome(new BlockPos(at.getX(),world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,at.getX(),at.getZ())-1,at.getZ()));
                if(!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER)) change|=p.biome(biome.unwrapKey().map(k -> k.identifier().toString()).orElse(""));
            }
            if(p.active("lutte") && p.stage("lutte")==1 && world.dimension()==Level.OVERWORLD) {
                LutteCoastalQuest.observeLoaded(world,player.getUUID(),p);
                int before=p.number("lutte.targets");
                if(surveyBudget>=16) {LutteCoastalQuest.survey(world,player,p);surveyBudget-=16;}
                LutteCoastalQuest.updateMap(world,player,p);change|=before!=p.number("lutte.targets");
            }
            if(p.active("marius") && p.stage("marius")==2) change|=MariusQuestService.tick(world,player,p);
            if(change) changed(world,player);
        }
    }
    public static void villagerContact(ServerLevel world,ServerPlayer player,BlockPos pos) {
        var p=progress(world,player.getUUID()); if(!p.active("simon") || p.stage("simon")!=2) return;
        var marker=de.quest.content.story.ShadowsTradeRoadEncounterService.currentVillage(world,pos); if(marker==null) return;
        var result=de.quest.shrine.VillageContactService.establish(world,player.getUUID(),marker); if(!result.accepted()) return;
        var village=LivingKingdomsState.get(world.getServer()).byLegacy(new de.quest.village.VillageLifeState.VillageKey(world.dimension().identifier().toString(),marker.centerX(),marker.centerZ()));
        village.ifPresent(v -> residentContact(world,player,v.id(),pos));
    }
    public static void residentContact(ServerLevel world,ServerPlayer player,UUID village,BlockPos pos) {
        var p=progress(world,player.getUUID());
        if(!p.active("simon") || p.stage("simon")!=2) return;
        String biome=world.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
        if(p.village(village,biome)) changed(world,player);
    }
    public static List<Component> lines(FriendsQuestProgress p,String who) {
        int stage=p.stage(who); if(stage>=3) return List.of(tr(who.equals("simon") && p.number("simon.herbs")==1?"simon.herbs.objective":who+".again"));
        if(who.equals("marius")) return MariusQuestService.lines(p);
        if(who.equals("simon")) return switch(stage) {
            case 1 -> List.of(tr("walking",p.number("simon.walk"),20000));
            case 2 -> List.of(tr("villages",Math.min(3,p.values("simon.villages").size()),Math.min(2,p.values("simon.biomes").size())));
            default -> List.of(tr("simon.1.objective")); };
        return p.stage(who)==0?List.of(tr("biomes",p.values("lutte.biomes").size())):p.stage(who)==1?
                List.of(tr("lutte.signals",p.number("lutte.signals")),tr("lutte.targets",p.number("lutte.targets")),tr("lutte.2.objective")):
                List.of(tr("lutte.3.objective"));
    }
    public static SpecialQuestStatus openStatus(ServerLevel world,UUID player) {
        var p=progress(world,player); var lines=new ArrayList<Component>();
        for(String who:List.of("simon","lutte","marius")) if(p.active(who)) { lines.add(tr(who+"."+(p.stage(who)+1)+".title")); lines.addAll(lines(p,who)); }
        if(p.number("simon.herbs")==1) lines.add(tr("simon.herbs.objective"));
        return lines.isEmpty()?null:new SpecialQuestStatus(tr("title"),List.copyOf(lines));
    }
    public static int completed(FriendsQuestProgress p) { return p.stage("simon")+p.stage("lutte")+p.stage("marius"); }
    public static int active(FriendsQuestProgress p) { return (p.active("simon")?1:0)+(p.active("lutte")?1:0)+(p.active("marius")?1:0); }
    public static int discovered(FriendsQuestProgress p) { return completed(p)+(p.stage("simon")<3 && p.number("simon.met")==1?1:0)+(p.stage("lutte")<3 && p.number("lutte.met")==1?1:0)+(p.stage("marius")<3 && p.number("marius.met")==1?1:0); }
}
