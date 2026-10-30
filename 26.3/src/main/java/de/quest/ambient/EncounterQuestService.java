package de.quest.ambient;

import de.quest.ambient.EncounterQuestProgress.Kind;
import de.quest.entity.AmbientAdventurerEntity;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.network.EncounterQuestPayloads.*;
import de.quest.quest.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Rare personal encounters. Receipts, inventory and XP share the same player save. */
public final class EncounterQuestService {
    private EncounterQuestService(){}
    private static final class Attachments {
        static final AttachmentType<CompoundTag> PLAYER=AttachmentRegistry.create(Identifier.parse("village-quest:encounter_progress"),b->b.persistent(CompoundTag.CODEC).copyOnDeath());
        static final AttachmentType<CompoundTag> OFFERS=AttachmentRegistry.create(Identifier.parse("village-quest:encounter_offers"),b->b.persistent(CompoundTag.CODEC));
    }
    private record Session(UUID token,UUID body,MinecraftServer server,long expires,Kind kind,CompoundTag offer){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private record Step(UUID body,net.minecraft.world.phys.Vec3 npc,net.minecraft.world.phys.Vec3 player){}
    private static final Map<UUID,Step> STEPS=new HashMap<>();
    public static net.minecraft.network.chat.MutableComponent tr(String key,Object... args){return Component.translatable("quest.village-quest.encounter."+key,args);}
    public static EncounterQuestProgress progress(ServerPlayer player){return new EncounterQuestProgress(((AttachmentTarget)player).getAttached(Attachments.PLAYER));}
    static void store(ServerPlayer player,EncounterQuestProgress p){((AttachmentTarget)player).setAttached(Attachments.PLAYER,p.snapshot());}
    private static long day(){return de.quest.util.QuestCalendarService.currentDay();}
    public static void register(){
        var playerType=Attachments.PLAYER;var offerType=Attachments.OFFERS;
        ServerTickEvents.END_SERVER_TICK.register(EncounterQuestService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{SESSIONS.values().removeIf(s->s.server==server);STEPS.clear();});
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{SESSIONS.remove(h.player.getUUID());STEPS.remove(h.player.getUUID());});
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world,entity,killed,source)->{
            if(entity instanceof ServerPlayer player && player.isAlive() && !player.isSpectator() && world.dimension()==Level.OVERWORLD
                    && (killed.getType()==EntityTypes.ZOMBIE || killed.getType()==EntityTypes.HUSK)){
                var p=progress(player);if(p.kind()==Kind.ZOMBIES && p.advance(1)){store(player,p);finish(player,p);changed(player);}
            }
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(hand==InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && entity instanceof Villager villager
                    && sp.isAlive() && !sp.isSpectator() && !villager.isBaby() && villager.isAlive() && sp.distanceToSqr(villager)<=36 && sp.hasLineOfSight(villager)){
                var p=progress(sp);
                if(p.kind()==Kind.GREETINGS && !p.pending() && p.text("dimension").equals(world.dimension().identifier().toString())){
                    if(greetingVillage(LivingKingdomsState.get(world.getServer()),p,world.dimension().identifier().toString(),villager.blockPosition()) && p.advance(1)){
                        store(sp,p);sp.sendSystemMessage(tr("greetings.received"),false);finish(sp,p);changed(sp);
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }
    static boolean greetingVillage(LivingKingdomsState state,EncounterQuestProgress p,String dimension,BlockPos position){
        UUID id;try{id=UUID.fromString(p.text("village"));}catch(IllegalArgumentException invalid){return false;}
        var nearest=state.nearVillages(dimension,position,64,1);
        return !nearest.isEmpty() && nearest.getFirst().id().equals(id);
    }
    /** Returns false only when the normal ambient greeting should be used. */
    public static boolean interact(ServerPlayer player,AmbientAdventurerEntity npc){
        if(!de.quest.network.ProgressionProtocol.require(player) || !ServerPlayNetworking.canSend(player,View.ID))return false;
        var p=progress(player);
        if(!p.writable())return false;
        if(p.kind()==Kind.COMPANY && !p.pending() && !npc.isNightrunner()){
            p.text("companion",npc.getUUID().toString());store(player,p);STEPS.remove(player.getUUID());
            player.sendSystemMessage(tr("company.resume",p.progress(),45),false);changed(player);return true;
        }
        if(!p.canOffer(day()))return false;
        var original=offers(npc);
        var offers=original==null?new CompoundTag():original.copy();String id=player.getUUID().toString();
        CompoundTag offer;
        if(offers.contains(id))offer=offers.getCompoundOrEmpty(id);
        else {
            if(offers.keySet().size()>=256)return false;
            offer=new CompoundTag();offer.putString("kind","");
            if(npc.getRandom().nextInt(5)==0){
                var eligible=new ArrayList<Kind>();
                for(var kind:Kind.values())if(kind.hunter==npc.isNightrunner() && eligible(player,npc,kind))eligible.add(kind);
                if(!eligible.isEmpty()){
                    var kind=eligible.get(npc.getRandom().nextInt(eligible.size()));offer.putString("kind",kind.name());
                    offer.putLong("origin",npc.blockPosition().asLong());offer.putString("dimension",npc.level().dimension().identifier().toString());
                    if(kind==Kind.GREETINGS){var village=nearVillage(player,npc);if(village!=null){offer.putString("village",village.id().toString());offer.putString("villageName",village.name());offer.putLong("target",village.arrival().asLong());}}
                }
            }
            offers.put(id,offer);storeOffers(npc,offers);
        }
        Kind kind=Kind.parse(offer.getStringOr("kind",""));if(kind==null || offer.getBooleanOr("declined",false))return false;
        var session=new Session(UUID.randomUUID(),npc.getUUID(),player.level().getServer(),player.level().getGameTime()+1200,kind,offer.copy());
        SESSIONS.put(player.getUUID(),session);send(player,npc,session);return true;
    }
    private static LivingKingdomsState.VillageView nearVillage(ServerPlayer player,AmbientAdventurerEntity npc){
        return LivingKingdomsState.get(player.level().getServer()).villages().stream()
                .filter(v->v.legacyKey().dimension().equals(npc.level().dimension().identifier().toString()) && v.arrival().distSqr(npc.blockPosition())<=256*256)
                .min(Comparator.comparingDouble(v->v.arrival().distSqr(npc.blockPosition()))).orElse(null);
    }
    private static boolean eligible(ServerPlayer player,AmbientAdventurerEntity npc,Kind kind){
        return switch(kind){
            case PROVISIONS,IRON->true;
            case ZOMBIES->player.level().getDifficulty()!=Difficulty.PEACEFUL && player.level().dimension()==Level.OVERWORLD;
            case GREETINGS->nearVillage(player,npc)!=null;
            case LIGHT->count(player,Items.TORCH)>=2 && player.level().canSeeSky(npc.blockPosition()) && player.level().mayInteract(player,npc.blockPosition());
            default->true;
        };
    }
    static CompoundTag offers(AmbientAdventurerEntity npc){return ((AttachmentTarget)npc).getAttached(Attachments.OFFERS);}
    static void storeOffers(AmbientAdventurerEntity npc,CompoundTag offers){((AttachmentTarget)npc).setAttached(Attachments.OFFERS,offers);}
    private static void consumeOffer(ServerPlayer player,AmbientAdventurerEntity npc){
        var raw=offers(npc);
        if(raw==null)return;var offers=raw.copy();String id=player.getUUID().toString();var offer=offers.getCompoundOrEmpty(id).copy();
        offer.putBoolean("declined",true);offers.put(id,offer);storeOffers(npc,offers);
    }
    public static void handle(ServerPlayer player,Action action){
        var s=SESSIONS.get(player.getUUID());if(s==null || !s.token.equals(action.session()))return;
        if(action.action()==0){SESSIONS.remove(player.getUUID());return;}
        var npc=valid(player,s);if(npc==null){close(player,s);return;}
        if(action.action()==4){close(player,s);return;}
        var p=progress(player);if(!p.canOffer(day()) || !eligible(player,npc,s.kind)){send(player,npc,s);return;}
        if(action.action()!=3)return;
        // Invalidate the single-use session before any inventory or progress mutation.
        SESSIONS.remove(player.getUUID());if(!p.accept(s.kind,day()))return;
        p.text("dimension",s.offer.getStringOr("dimension",""));p.number("origin",s.offer.getLongOr("origin",0));
        p.text("village",s.offer.getStringOr("village",""));p.text("villageName",s.offer.getStringOr("villageName",""));p.number("target",s.offer.getLongOr("target",0));
        p.text("companion",npc.getUUID().toString());
        store(player,p);consumeOffer(player,npc);close(player,s);
        QuestTrackerService.enableForAcceptedQuest((ServerLevel)player.level(),player);
        player.sendSystemMessage(tr("accepted",tr(s.kind.key()+".title")),false);
        changed(player);
    }
    private static AmbientAdventurerEntity valid(ServerPlayer player,Session s){
        if(!player.isAlive() || player.isSpectator() || player.level().getServer()!=s.server || player.level().getGameTime()>s.expires
                || !player.level().dimension().identifier().toString().equals(s.offer.getStringOr("dimension","")))return null;
        if(!(player.level() instanceof ServerLevel world) || !(world.getEntity(s.body) instanceof AmbientAdventurerEntity npc)
                || !npc.isAlive() || !npc.encounterCalm(player) || player.distanceToSqr(npc)>36 || !player.hasLineOfSight(npc))return null;
        return npc;
    }
    private static void send(ServerPlayer player,AmbientAdventurerEntity npc,Session s){
        var objective=objective(s.kind,s.offer,0);
        ServerPlayNetworking.send(player,new View(s.token,true,npc.getDisplayName(),tr(s.kind.key()+".title"),tr(s.kind.key()+".dialogue"),objective,
                tr(s.kind.key()+".rules"),tr("reward",4,s.kind.levels),4,4,s.kind.levels));
    }
    private static void close(ServerPlayer player,Session s){
        SESSIONS.remove(player.getUUID());if(ServerPlayNetworking.canSend(player,View.ID))ServerPlayNetworking.send(player,new View(s.token,false,Component.empty(),Component.empty(),Component.empty(),Component.empty(),Component.empty(),Component.empty(),0,0,0));
    }
    private static void changed(ServerPlayer player){var world=(ServerLevel)player.level();QuestBookHelper.refreshQuestBook(world,player);QuestTrackerService.refresh(world,player);}
    public static int count(ServerPlayer player,Item item){int total=0;for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.is(item))total+=stack.getCount();}return total;}
    private static void remove(ServerPlayer player,Item item,int amount){for(int i=0;i<player.getInventory().getContainerSize() && amount>0;i++){var stack=player.getInventory().getItem(i);if(stack.is(item)){int n=Math.min(amount,stack.getCount());stack.shrink(n);amount-=n;}}player.getInventory().setChanged();}
    private static boolean room(ServerPlayer player){int capacity=0;for(int i=0;i<36;i++){var stack=player.getInventory().getItem(i);if(stack.isEmpty())capacity+=64;else if(ItemStack.isSameItemSameComponents(stack,new ItemStack(Items.EMERALD)))capacity+=stack.getMaxStackSize()-stack.getCount();}return capacity>=4;}
    static void finish(ServerPlayer player,EncounterQuestProgress p){
        if(!p.writable() || !p.active())return;
        if(!p.pending() && p.complete(day())){
            store(player,p);
            if(!room(player))player.sendSystemMessage(tr("pending"),false);
        }
        if(!p.pending() || !room(player))return;
        var kind=p.kind();
        // All mutations run on the server thread and live in the same player save document.
        // No world-saved receipt can get ahead of or behind the inventory/XP snapshot.
        player.getInventory().add(new ItemStack(Items.EMERALD,4));player.giveExperienceLevels(kind.levels);p.clear();store(player,p);
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(completionMessage(kind),false);changed(player);
        if(de.quest.config.ClientPreferenceService.questProgressSounds(player))
            ((ServerLevel)player.level()).playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,net.minecraft.sounds.SoundSource.PLAYERS,
                    .9f*de.quest.config.ClientPreferenceService.questProgressSoundVolume(player),1f);
    }
    /** Same chat structure, title helper, item hover and experience label as Questmaster dailies. */
    static Component completionMessage(Kind kind){
        var gray=net.minecraft.ChatFormatting.GRAY;
        var divider=Component.literal("------------------------------").withStyle(gray);
        var emeralds=new ItemStack(Items.EMERALD,4);
        return Component.empty().append(divider.copy()).append("\n")
                .append(de.quest.util.Texts.completedTitle(tr(kind.key()+".title"),net.minecraft.ChatFormatting.GREEN)).append("\n\n")
                .append(tr(kind.key()+".done")).append("\n\n")
                .append(Component.translatable("text.village-quest.daily.rewards").withStyle(gray)).append(":\n\n")
                .append("    ").append(emeralds.getDisplayName()).append(Component.literal(" x4").withStyle(gray)).append("\n")
                .append("    ").append(Component.translatable("screen.village-quest.questmaster.reward.experience",
                        de.quest.economy.QuestExperienceService.formatBars(kind.levels)).withStyle(net.minecraft.ChatFormatting.GREEN)).append("\n")
                .append(divider.copy());
    }
    /** One server-thread transaction, exact alternative only, never partial or repeated consumption. */
    static boolean collectDelivery(ServerPlayer player,EncounterQuestProgress p){
        if(!p.writable() || !p.active() || !p.kind().delivery() || p.pending() || p.progress()>=p.kind().target)return false;
        Item cost;int amount;
        if(p.kind()==Kind.IRON){cost=Items.IRON_INGOT;amount=1;}
        else if(count(player,Items.BREAD)>=2){cost=Items.BREAD;amount=2;}
        else {cost=Items.BAKED_POTATO;amount=3;}
        if(count(player,cost)<amount)return false;
        remove(player,cost,amount);p.advance(1);store(player,p);
        player.inventoryMenu.broadcastChanges();return true;
    }
    private static void tick(MinecraftServer server){
        boolean second=server.overworld().getGameTime()%20==0;
        if(second)for(var entry:List.copyOf(SESSIONS.entrySet())){var player=server.getPlayerList().getPlayer(entry.getKey());if(player==null)SESSIONS.remove(entry.getKey());else if(valid(player,entry.getValue())==null)close(player,entry.getValue());}
        for(var player:server.getPlayerList().getPlayers()){
            if(!player.isAlive() || player.isSpectator())continue;
            var p=progress(player);if(!p.writable() || !p.active())continue;
            if(p.kind().delivery() && !p.pending()){
                if(collectDelivery(player,p)){finish(player,p);changed(player);}
                else if(p.progress()>=p.kind().target)finish(player,p);
                else if(second)changed(player);
                continue;
            }
            if(!second)continue;
            if(p.pending()){finish(player,p);continue;}
            if(p.kind()==Kind.COMPANY){
                Entity entity=null;try{entity=((ServerLevel)player.level()).getEntity(UUID.fromString(p.text("companion")));}catch(IllegalArgumentException ignored){}
                if(entity instanceof AmbientAdventurerEntity npc && !npc.isNightrunner() && npc.encounterCalm(player) && npc.distanceToSqr(player)<=144){
                    var old=STEPS.put(player.getUUID(),new Step(npc.getUUID(),npc.position(),player.position()));
                    if(old!=null && old.body.equals(npc.getUUID()) && old.npc.distanceToSqr(npc.position())>=.01 && old.player.distanceToSqr(player.position())>=.01 && p.advance(1)){
                        store(player,p);finish(player,p);changed(player);
                    }
                }else STEPS.remove(player.getUUID());
            }
        }
    }
    public static void placed(ServerLevel world,ServerPlayer player,BlockPos pos){
        var p=progress(player);if(!p.writable() || p.kind()!=Kind.LIGHT || p.pending() || !p.text("dimension").equals(world.dimension().identifier().toString())
                || pos.distSqr(BlockPos.of(p.number("origin")))>32*32 || !torch(world,pos) || player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64)return;
        var first=p.text("torch0");
        if(first.isEmpty()){p.text("torch0",Long.toString(pos.asLong()));p.advance(1);store(player,p);changed(player);return;}
        BlockPos previous;try{previous=BlockPos.of(Long.parseLong(first));}catch(NumberFormatException invalid){return;}
        if(!world.hasChunkAt(previous) || !torch(world,previous)){p.text("torch0",Long.toString(pos.asLong()));store(player,p);return;}
        if(previous.distSqr(pos)<16)return;
        p.advance(2);store(player,p);finish(player,p);changed(player);
    }
    private static boolean torch(ServerLevel world,BlockPos pos){var state=world.getBlockState(pos);return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);}
    public static void cancel(ServerPlayer player){var p=progress(player);if(!p.writable() || !p.active() || p.pending())return;p.clear();store(player,p);STEPS.remove(player.getUUID());changed(player);}
    private static Component objective(Kind kind,CompoundTag tag,int progress){
        if(kind==Kind.GREETINGS){var pos=BlockPos.of(tag.getLongOr("target",0));return tr(kind.key()+".objective",pos.getX(),pos.getZ()).append(Component.literal("\n"+tag.getStringOr("villageName","")));}
        if(kind==Kind.LIGHT){var pos=BlockPos.of(tag.getLongOr("origin",0));return tr(kind.key()+".objective",pos.getX(),pos.getY(),pos.getZ());}
        return tr(kind.key()+".objective",progress,kind.target);
    }
    public static List<JournalActiveQuest> journal(ServerPlayer player){
        if(!(player instanceof AttachmentTarget))return List.of();
        var p=progress(player);if(!p.writable() || !p.active())return List.of();var kind=p.kind();
        var target=kind==Kind.PROVISIONS?tr("provisions.collect",Math.min(2,count(player,Items.BREAD)),Math.min(3,count(player,Items.BAKED_POTATO)))
                :kind==Kind.IRON?tr("iron.collect",Math.min(1,count(player,Items.IRON_INGOT))):objective(kind,p.snapshot(),p.progress());
        return List.of(new JournalActiveQuest(new TrackerEntry("special:encounter","special",tr(kind.key()+".title"),p.pending()?tr("pending"):target),
                List.of(tr(kind.key()+".dialogue"),target,kind.delivery()?tr("automatic"):tr("progress",p.progress(),kind.target),tr(kind.key()+".rules"),tr("reward",4,kind.levels),tr(p.pending()?"pending":"automatic")),p.pending()?-1:2));
    }
}
