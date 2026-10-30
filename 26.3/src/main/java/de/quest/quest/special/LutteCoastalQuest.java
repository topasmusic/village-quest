package de.quest.quest.special;

import de.quest.data.QuestState;
import de.quest.reward.*;
import de.quest.util.QuestMapHelper;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.*;

/** Three stored coast areas, one personal generation-bound map; reads never request a chunk. */
public final class LutteCoastalQuest {
    private static final String MAP="lutte_coasts";
    private static final int CHART_VERSION=2;
    private record Certificate(UUID player,int signal,BlockPos base) {}
    private static final Map<net.minecraft.server.MinecraftServer,Set<Certificate>> VERIFIED=new WeakHashMap<>();
    private static Set<Certificate> verified(ServerLevel world) {return VERIFIED.computeIfAbsent(world.getServer(),s -> new HashSet<>());}
    private static Certificate certificate(UUID player,FriendsQuestProgress p,int i) {return new Certificate(player,i,pos(p,"lutte.base."+i));}
    /** Rebuilt once per SavedData instance; only active personal signal bases are indexed. */
    private static final Map<net.minecraft.server.MinecraftServer,SignalIndex> INDEX=new WeakHashMap<>();
    private static final class SignalIndex {
        final QuestState state;
        final Map<BlockPos,Set<Certificate>> blocks=new HashMap<>();
        final Map<net.minecraft.world.level.ChunkPos,Set<Certificate>> chunks=new HashMap<>();
        final Map<UUID,Set<Certificate>> owners=new HashMap<>();
        SignalIndex(QuestState state) {
            this.state=state;
            for(var entry:state.getPlayersView().entrySet())update(entry.getKey(),new FriendsQuestProgress(entry.getValue()));
        }
        void remove(UUID player) {
            var old=owners.remove(player);if(old==null)return;
            for(var c:old) {
                for(int bit=0;bit<4;bit++) {var at=c.base().above(bit);var set=blocks.get(at);if(set!=null){set.remove(c);if(set.isEmpty())blocks.remove(at);}}
                var key=new net.minecraft.world.level.ChunkPos(c.base().getX()>>4,c.base().getZ()>>4);var set=chunks.get(key);if(set!=null){set.remove(c);if(set.isEmpty())chunks.remove(key);}
            }
        }
        void update(UUID player,FriendsQuestProgress p) {
            remove(player);if(!p.active("lutte") || p.stage("lutte")!=1)return;
            for(int i=0;i<3;i++)if(p.number("lutte.base."+i+".set")==1) {
                var c=certificate(player,p,i);owners.computeIfAbsent(player,k->new HashSet<>()).add(c);
                for(int bit=0;bit<4;bit++)blocks.computeIfAbsent(c.base().above(bit),k->new HashSet<>()).add(c);
                chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(c.base().getX()>>4,c.base().getZ()>>4),k->new HashSet<>()).add(c);
            }
        }
    }
    private static SignalIndex index(ServerLevel world) {
        var state=QuestState.get(world.getServer());var index=INDEX.get(world.getServer());
        if(index==null || index.state!=state){if(index!=null)VERIFIED.remove(world.getServer());index=new SignalIndex(state);INDEX.put(world.getServer(),index);}return index;
    }
    public static void prime(ServerLevel world) {index(world);}
    public static void finished(ServerLevel world,UUID player) {
        var index=INDEX.get(world.getServer());if(index!=null)index.remove(player);
        var proofs=VERIFIED.get(world.getServer());if(proofs!=null)proofs.removeIf(c->c.player().equals(player));
    }
    public static void observeLoaded(ServerLevel world,UUID player,FriendsQuestProgress p) {
        if(world.dimension()!=Level.OVERWORLD || !p.active("lutte") || p.stage("lutte")!=1) return;
        for(int i=0;i<3;i++) if(p.number("lutte.certified."+i)==1 && world.hasChunkAt(pos(p,"lutte.base."+i))) {
            var certificate=certificate(player,p,i);
            if(complete(world,p,i)) verified(world).add(certificate);
            else {verified(world).remove(certificate);p.number("lutte.certified."+i,0);QuestState.get(world.getServer()).setDirty();}
        }
    }
    public static void chunkLoaded(ServerLevel world,net.minecraft.world.level.chunk.LevelChunk chunk) {
        if(world.dimension()!=Level.OVERWORLD) return;
        var index=index(world);
        for(var c:List.copyOf(index.chunks.getOrDefault(chunk.getPos(),Set.of()))) {
            var data=index.state.getPlayersView().get(c.player());if(data==null)continue;
            var p=new FriendsQuestProgress(data);
            if(!p.active("lutte") || p.stage("lutte")!=1){index.remove(c.player());continue;}
            if(p.number("lutte.certified."+c.signal())!=1)continue;
            // CHUNK_LOAD runs inside this chunk's unfinished FULL future. A world block
            // lookup here waits on that same future and deadlocks world loading.
            // A signal is one vertical column: all four states belong to the supplied chunk.
            boolean intact=true;
            for(int bit=0;bit<4;bit++) {
                if(p.number(part(c.signal(),bit))!=1 || !matches(chunk.getBlockState(c.base().above(bit)),bit)) {intact=false;break;}
            }
            if(intact)verified(world).add(c);
            else {verified(world).remove(c);p.number("lutte.certified."+c.signal(),0);index.state.setDirty();}
        }
    }

    public static BlockPos pos(FriendsQuestProgress p,String key) { return new BlockPos(p.number(key+".x"),p.number(key+".y"),p.number(key+".z")); }
    private static void pos(FriendsQuestProgress p,String key,BlockPos pos) { p.number(key+".x",pos.getX());p.number(key+".y",pos.getY());p.number(key+".z",pos.getZ()); }
    public static boolean accept(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
        if(world.dimension()!=Level.OVERWORLD || p.number("lutte.materials")==1) return false;
        var data=QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        boolean supplies=!de.quest.data.PersonalResetReceipts.has(data,"supplies.lutte");
        var goods=supplies?List.of(new ItemStack(Items.LANTERN,3),new ItemStack(Items.COBBLESTONE_WALL,6),new ItemStack(Items.OAK_LOG,3)):List.<ItemStack>of();
        if(!VanillaRewardService.state(world,player.getUUID()).canEnqueue(goods.size()+1)) return false;
        p.number("lutte.generation",Math.max(1,p.number("lutte.generation"))); pos(p,"lutte.origin",player.blockPosition());
        var items=new ArrayList<>(goods);items.add(map(world,player,p));
        if(!FriendsQuestService.enqueue(world,player,items)) return false;
        de.quest.data.PersonalResetReceipts.record(data,"supplies.lutte");
        p.number("lutte.materials",1);p.number("lutte.mapTarget",-1);return true;
    }
    public static void survey(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
        if(p.number("lutte.targets")>=3 || p.number("lutte.cursor")>=8192) return;
        var origin=pos(p,"lutte.origin");
        for(int work=0;work<16 && p.number("lutte.targets")<3 && p.number("lutte.cursor")<8192;work++) {
            int cursor=p.number("lutte.cursor");p.number("lutte.cursor",cursor+1);
            int ring=cursor/64+1; double angle=(cursor%64)*Math.PI/32;
            int x=origin.getX()+(int)Math.round(Math.cos(angle)*ring*128),z=origin.getZ()+(int)Math.round(Math.sin(angle)*ring*128);
            BlockPos candidate=new BlockPos(x,world.getSeaLevel(),z);
            if(!world.getWorldBorder().isWithinBounds(candidate) || ocean(world,x,z)) continue;
            boolean ocean=false; for(var dir:Direction.Plane.HORIZONTAL) if(ocean(world,x+dir.getStepX()*64,z+dir.getStepZ()*64)) {ocean=true;break;}
            if(!ocean) continue;
            boolean separate=true;for(int i=0;i<p.number("lutte.targets");i++) if(horizontal(pos(p,"lutte.target."+i),candidate)<512*512) separate=false;
            if(!separate) continue;
            // Generator height query does not load/generate a chunk or add a ticket.
            int y=world.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,world,world.getChunkSource().randomState());
            if(y<world.getSeaLevel() || y>world.getSeaLevel()+48) continue;
            pos(p,"lutte.target."+p.number("lutte.targets"),new BlockPos(x,y,z));p.number("lutte.targets",p.number("lutte.targets")+1);
        }
        QuestState.get(world.getServer()).setDirty();
    }
    private static boolean ocean(ServerLevel world,int x,int z) { return world.getUncachedNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(world.getSeaLevel()),QuartPos.fromBlock(z)).is(BiomeTags.IS_OCEAN); }
    public static long horizontal(BlockPos a,BlockPos b) { long x=(long)a.getX()-b.getX(),z=(long)a.getZ()-b.getZ();return x*x+z*z; }
    private static String part(int i,int bit) {return "lutte.built."+i+"."+bit;}
    public static void invalidate(ServerLevel world,BlockPos position) {
        if(world.dimension()!=Level.OVERWORLD) return;
        var index=index(world);
        for(var c:List.copyOf(index.blocks.getOrDefault(position,Set.of()))) {
            var data=index.state.getPlayersView().get(c.player());if(data==null)continue;
            var p=new FriendsQuestProgress(data);
            if(!p.active("lutte") || p.stage("lutte")!=1){index.remove(c.player());continue;}
            int bit=position.getY()-c.base().getY();
            boolean changed=verified(world).remove(c) || p.number(part(c.signal(),bit))!=0 || p.number("lutte.certified."+c.signal())!=0;
            if(changed){p.number(part(c.signal(),bit),0);p.number("lutte.certified."+c.signal(),0);index.state.setDirty();}
        }
    }
    public static void placed(ServerLevel world,ServerPlayer player,BlockPos position) {
        invalidate(world,position);var p=FriendsQuestService.progress(world,player.getUUID());
        if(world.dimension()!=Level.OVERWORLD || !p.active("lutte") || p.stage("lutte")!=1)return;
        int current=p.number("lutte.signals");boolean changed=false;var state=world.getBlockState(position);
        for(int i=0;i<Math.min(3,current+1) && i<p.number("lutte.targets");i++) {
            String key="lutte.base."+i;
            if(i==current && state.is(BlockTags.LOGS) && horizontal(position,pos(p,"lutte.target."+i))<=16*16
                    && Math.abs(position.getY()-pos(p,"lutte.target."+i).getY())<=16
                    && world.getBlockState(position.below()).isFaceSturdy(world,position.below(),Direction.UP)) {
                pos(p,key,position);p.number(key+".set",1);for(int bit=0;bit<4;bit++)p.number(part(i,bit),0);changed=true;
            }
            if(p.number(key+".set")!=1)continue;var base=pos(p,key);int bit=position.getY()-base.getY();
            if(position.getX()!=base.getX() || position.getZ()!=base.getZ() || bit<0 || bit>=4 || !matches(state,bit))continue;
            if(p.number(part(i,bit))!=1){p.number(part(i,bit),1);changed=true;}
            if(complete(world,p,i)) {if(p.number("lutte.certified."+i)!=1)changed=true;p.number("lutte.certified."+i,1);changed|=verified(world).add(certificate(player.getUUID(),p,i));}
            if(i==current && p.number("lutte.certified."+i)==1){p.number("lutte.signals",current+1);changed=true;player.sendSystemMessage(FriendsQuestService.tr("lutte.signals",current+1),false);}
        }
        if(changed){index(world).update(player.getUUID(),p);FriendsQuestService.changed(world,player);}
    }
    static boolean matches(BlockState state,int bit) { return bit==0?state.is(BlockTags.LOGS):bit<3?state.is(BlockTags.WALLS):state.is(Blocks.LANTERN) && !state.getValue(LanternBlock.HANGING); }
    public static boolean complete(ServerLevel world,FriendsQuestProgress p,int i) {
        if(p.number("lutte.base."+i+".set")!=1) return false;var base=pos(p,"lutte.base."+i);
        for(int bit=0;bit<4;bit++) if(p.number(part(i,bit))!=1 || !world.hasChunkAt(base.above(bit)) || !matches(world.getBlockState(base.above(bit)),bit)) return false;
        return true;
    }
    public static boolean ready(ServerLevel world,UUID player,FriendsQuestProgress p) {
        if(world.dimension()!=Level.OVERWORLD || p.number("lutte.signals")!=3) return false;
        observeLoaded(world,player,p);
        for(int i=0;i<3;i++) {
            if(p.number("lutte.certified."+i)!=1 || !verified(world).contains(certificate(player,p,i))) return false;
            var base=pos(p,"lutte.base."+i);
            // Current-session loaded verification survives unload; native mutations revoke it, restart requires fresh loaded evidence.
            if(world.hasChunkAt(base) && !complete(world,p,i)) {p.number("lutte.certified."+i,0);QuestState.get(world.getServer()).setDirty();return false;}
        }
        return true;
    }
    private static boolean valid(ItemStack stack,ServerPlayer player,FriendsQuestProgress p) {
        if(!stack.is(Items.FILLED_MAP) || !QuestMapHelper.isTagged(stack,MAP)) return false;
        var t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return t.getStringOr("owner","").equals(player.getUUID().toString()) && t.getIntOr("generation",0)==p.number("lutte.generation");
    }
    public static boolean hasMap(ServerPlayer player,FriendsQuestProgress p) {
        for(int i=0;i<player.getInventory().getContainerSize();i++) if(valid(player.getInventory().getItem(i),player,p)) return true;return false;
    }
    private static int chartTarget(FriendsQuestProgress p) {
        int next=Math.min(3,p.number("lutte.signals"));
        return next>=3?-2:p.number("lutte.targets")>next?next:-1;
    }
    private static int chartPhase(FriendsQuestProgress p) {
        int target=chartTarget(p);
        return target==-2?3:target>=0?0:p.number("lutte.cursor")>=8192?2:1;
    }
    private static ItemStack map(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
        int index=chartTarget(p);boolean known=index>=0;
        var target=pos(p,known?"lutte.target."+index:"lutte.origin");
        var map=MapItem.create(world,target.getX(),target.getZ(),(byte)2,true,true);
        MapItem.renderBiomePreviewMap(world,map);
        if(known) MapItemSavedData.addTargetDecoration(map,target,"+",MapDecorationTypes.RED_X);
        QuestMapHelper.tag(map,MAP);CustomData.update(DataComponents.CUSTOM_DATA,map,t -> {t.putString("owner",player.getUUID().toString());t.putInt("generation",p.number("lutte.generation"));t.putInt("target",index);t.putInt("chartVersion",CHART_VERSION);t.putInt("chartPhase",chartPhase(p));});
        map.set(DataComponents.ITEM_NAME,FriendsQuestService.tr("lutte.map"));
        List<Component> lore;
        if(known)lore=List.of(FriendsQuestService.tr("lutte.map.target",target.getX(),target.getZ()),FriendsQuestService.tr("lutte.map.lore"),FriendsQuestService.tr("lutte.map.build"),FriendsQuestService.tr("lutte.map.next"));
        else if(index==-2)lore=List.of(FriendsQuestService.tr("lutte.map.finished"),FriendsQuestService.tr("lutte.map.keep"));
        else lore=List.of(FriendsQuestService.tr(chartPhase(p)==2?"lutte.map.exhausted":"lutte.surveying"),FriendsQuestService.tr("lutte.map.origin",target.getX(),target.getZ()),FriendsQuestService.tr(chartPhase(p)==2?"lutte.map.retry":"lutte.map.search"));
        map.set(DataComponents.LORE,new ItemLore(lore));return map;
    }
    public static void updateMap(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
        int target=chartTarget(p);
        ItemStack fresh=null;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {var stack=player.getInventory().getItem(i);
            if(!valid(stack,player,p))continue;
            var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(tag.getIntOr("target",-1)==target && tag.getIntOr("chartVersion",0)==CHART_VERSION && tag.getIntOr("chartPhase",-1)==chartPhase(p))continue;
            if(fresh==null) fresh=map(world,player,p);
            player.getInventory().setItem(i,fresh.copy());
        }
        if(fresh!=null) player.inventoryMenu.broadcastChanges();
    }
    public static boolean replacement(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) {
        if(!p.active("lutte") || p.stage("lutte")!=1 || hasMap(player,p)) return false;
        long now=world.getServer().overworld().getOverworldClockTime();String raw=p.text("lutte.replacementDue");
        if(!raw.isBlank()) {
            long due;try{due=Long.parseLong(raw);}catch(NumberFormatException invalid){return false;}
            if(!p.replacementReady(now)) {player.sendSystemMessage(FriendsQuestService.tr("lutte.wait",(due-now+23999)/24000),false);return false;}
            if(!VanillaRewardService.state(world,player.getUUID()).canEnqueue(1)) return false;
            // The paid order already invalidated the old generation. Delivery is once, through lossless escrow.
            if(!FriendsQuestService.enqueue(world,player,List.of(map(world,player,p)))) return false;
            p.text("lutte.replacementDue","");VanillaRewardService.deliverItems(world,player);player.sendSystemMessage(FriendsQuestService.tr("lutte.collected"),false);return true;
        }
        int count=p.number("lutte.replacements");if(count==Integer.MAX_VALUE || p.number("lutte.generation")==Integer.MAX_VALUE) return false;
        var costs=new HashMap<Item,Integer>();costs.put(Items.PAPER,3);costs.put(Items.INK_SAC,1);if(count>=2)costs.put(Items.DIAMOND,5);
        if(!FriendsQuestService.has(player,costs)) {player.sendSystemMessage(FriendsQuestService.tr("not_ready"),false);return false;}
        if(!p.orderReplacement(now)) return false;
        FriendsQuestService.consume(player,costs);
        player.sendSystemMessage(FriendsQuestService.tr(count>=2?"lutte.vacation":"lutte.lost"),false);QuestState.get(world.getServer()).setDirty();return true;
    }
}
