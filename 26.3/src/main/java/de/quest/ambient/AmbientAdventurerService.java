package de.quest.ambient;

import de.quest.caravan.AmbientRoadAccess;
import de.quest.config.VillageQuestServerConfig;
import de.quest.entity.AmbientAdventurerEntity;
import de.quest.kingdom.KingdomBuildingService;
import de.quest.registry.ModEntities;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Bounded server-local encounters, reservations, cooldowns and work allowance. No persistent people. */
public final class AmbientAdventurerService {
    private AmbientAdventurerService() {}
    private static final Map<MinecraftServer,Runtime> RUNTIMES=new WeakHashMap<>();
    record Arrival(BlockPos position,boolean hunter,long until) {}
    static final class Runtime {
        final Map<UUID,AmbientAdventurerEntity> live=new LinkedHashMap<>();
        final Set<AmbientAdventurerEntity> retiring=new LinkedHashSet<>();
        final Map<Long,Long> regions=new LinkedHashMap<>();
        final List<Arrival> arrivals=new ArrayList<>();
        final Set<UUID> waitingForReads=new LinkedHashSet<>();
        long tick=-1,pathTick=-1,started; int reads, playerCursor, attempts; UUID candidatePlayer; boolean hunter,cave;
        Runtime(long now) { started=now; }
        void begin(long now) { if(tick!=now) { tick=now; reads=64; } }
    }
    private static Runtime runtime(ServerLevel world) {
        Runtime r=RUNTIMES.computeIfAbsent(world.getServer(),s -> new Runtime(world.getGameTime())); r.begin(world.getGameTime()); return r;
    }
    public static boolean takeReads(ServerLevel world,int count) {
        Runtime r=runtime(world); if(count<0 || count>r.reads) return false; r.reads-=count; return true;
    }
    public static boolean takeTravelReads(ServerLevel world,UUID npc,int count) {
        Runtime r=runtime(world);
        if(npc==null || count<=0 || count>64) return false;
        r.waitingForReads.add(npc);
        if(!r.waitingForReads.iterator().next().equals(npc) || !takeReads(world,count)) return false;
        r.waitingForReads.remove(npc); return true;
    }
    public static void releaseTravelReads(ServerLevel world,UUID npc) { runtime(world).waitingForReads.remove(npc); }
    public static boolean takePath(ServerLevel world) {
        Runtime r=runtime(world); var shared=KingdomBuildingService.workBudget(world.getServer());
        if(r.pathTick==r.tick || shared.paths()<=4 || !shared.takePaths(1)) return false;
        r.pathTick=r.tick; return true;
    }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AmbientAdventurerService::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            if(entity instanceof AmbientAdventurerEntity npc) {
                var r=runtime(world);
                if(world.dimension()!=Level.OVERWORLD || !VillageQuestServerConfig.get().ambient().enabled()
                        || !r.live.containsKey(npc.getUUID()) && !caps(world,r,npc.blockPosition(),npc.isNightrunner())) { npc.discard(); return; }
                r.live.putIfAbsent(npc.getUUID(),npc);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world) -> {
            if(entity instanceof AmbientAdventurerEntity npc) {
                Runtime r=RUNTIMES.get(world.getServer()); if(r!=null) r.live.remove(entity.getUUID());
                // Tracking loss can run inside a live native entity-section iteration.
                // discard() removes from that same section; wait for the server tick boundary.
                if(!npc.isRemoved()) runtime(world).retiring.add(npc);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Runtime r=RUNTIMES.remove(server);
            if(r!=null) { r.retiring.addAll(r.live.values()); r.live.clear(); retirePending(r); }
        });
    }
    private static void retirePending(Runtime r) {
        var pending=List.copyOf(r.retiring); r.retiring.clear();
        for(var npc:pending) if(!npc.isRemoved()) npc.discard();
    }
    static boolean caps(ServerLevel world,Runtime r,BlockPos position,boolean hunter) {
        var config=VillageQuestServerConfig.get().ambient();
        if(r.live.size()>=config.total() || hunter && r.live.values().stream().filter(AmbientAdventurerEntity::isNightrunner).count()>=config.nightrunners()) return false;
        for(var player:world.players()) if(player.isAlive() && !player.isSpectator() && player.blockPosition().distSqr(position)<=96*96) {
            if(r.live.values().stream().filter(n -> n.distanceToSqr(player)<=96*96).count()>=2) return false;
        }
        return true;
    }
    public static boolean conflict(ServerLevel world,BlockPos position) {
        if(world.getRaidAt(position)!=null) return true;
        if(de.quest.content.story.ShadowsTradeRoadEncounterService.ambientConflict(position)
                || de.quest.content.story.EmptyCaravanStoryService.ambientConflict(world,position)
                || de.quest.caravan.TradeRouteService.ambientConflict(world,position)) return true;
        return false;
    }
    private static void tick(MinecraftServer server) {
        ServerLevel world=server.overworld(); Runtime r=runtime(world); long now=r.tick;
        retirePending(r);
        r.live.values().removeIf(n -> n.isRemoved() || !n.isAlive());
        r.waitingForReads.removeIf(id -> !r.live.containsKey(id));
        reconcile(world,r);
        if(!VillageQuestServerConfig.get().ambient().enabled()) { for(var n:List.copyOf(r.live.values())) n.discard(); r.attempts=0; return; }
        r.arrivals.removeIf(a -> a.until<=now); r.regions.values().removeIf(t -> t<=now);
        if(now-r.started<1200) return;
        if(r.attempts>0) { spawnAttempt(world,r); return; }
        // Fairly sample one player each second; each spatial region gets only one roll/minute.
        if(now%20!=0 || world.players().isEmpty()) return;
        var players=world.players(); var player=players.get(Math.floorMod(r.playerCursor++,players.size()));
        if(!player.isAlive() || player.isSpectator()) return;
        long region=net.minecraft.world.level.ChunkPos.pack(Math.floorDiv(player.blockPosition().getX(),128),Math.floorDiv(player.blockPosition().getZ(),128));
        if(r.regions.containsKey(region) || r.regions.size()>=512) return;
        r.regions.put(region,now+1200);
        // 3/16 instead of 1/4: 25% fewer successful regional spawn rolls.
        if(world.getRandom().nextInt(16)>=3) return;
        boolean night=AmbientAdventurerPolicy.night(world.getOverworldClockTime());
        r.hunter=night && world.getRandom().nextInt(3)!=0; r.cave=r.hunter && world.getRandom().nextInt(8)==0;
        if(!r.hunter && night && world.getRandom().nextInt(4)!=0) return;
        r.candidatePlayer=player.getUUID(); r.attempts=8;
    }
    static void reconcile(ServerLevel world,Runtime r) {
        // Players/encounters can move together after spawn; caps must still hold then.
        var config=VillageQuestServerConfig.get().ambient();
        for(var npc:r.live.values().stream().filter(AmbientAdventurerEntity::isNightrunner).skip(config.nightrunners()).toList()) remove(r,npc);
        for(var npc:r.live.values().stream().skip(config.total()).toList()) remove(r,npc);
        for(var player:world.players()) if(player.isAlive() && !player.isSpectator()) {
            for(var npc:r.live.values().stream().filter(n -> n.distanceToSqr(player)<=96*96)
                    .sorted(Comparator.comparingDouble((AmbientAdventurerEntity n) -> n.distanceToSqr(player)).thenComparing(Entity::getUUID)).skip(2).toList()) remove(r,npc);
        }
    }
    private static void remove(Runtime r,AmbientAdventurerEntity npc) { r.live.remove(npc.getUUID()); npc.discard(); }
    private static void spawnAttempt(ServerLevel world,Runtime r) {
        // One position per tick. Guard before heightmap, support, collision or LOS access.
        r.attempts--; var player=world.getServer().getPlayerList().getPlayer(r.candidatePlayer);
        if(player==null || player.level()!=world || !player.isAlive() || player.isSpectator()) { r.attempts=0; return; }
        if(r.hunter && !AmbientAdventurerPolicy.night(world.getOverworldClockTime())) { r.attempts=0; return; }
        double angle=world.getRandom().nextDouble()*Math.PI*2, distance=40+world.getRandom().nextDouble()*24;
        BlockPos column=BlockPos.containing(player.getX()+Math.cos(angle)*distance,player.getY(),player.getZ()+Math.sin(angle)*distance);
        if(!de.quest.kingdom.NativeNpcPathPolicy.loaded(column,8,24,(x,z) -> world.hasChunkAt(new BlockPos(x*16,column.getY(),z*16))
                && world.isPositionEntityTicking(new BlockPos(x*16,column.getY(),z*16))) || !takeReads(world,32)) return;
        int surface=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
        int y=r.cave?player.blockPosition().getY():surface;
        if(r.cave && (y<surface-16 || y>=surface-2 || y<world.getSeaLevel()-16)) return;
        BlockPos pos=AmbientRoadAccess.near(world,new BlockPos(column.getX(),y,column.getZ()));
        if(pos==null || !world.getWorldBorder().isWithinBounds(new AABB(pos).inflate(1,2,1)) || conflict(world,pos) || !caps(world,r,pos,r.hunter)) return;
        if(!r.hunter && !AmbientRoadAccess.road(world,pos) && !world.canSeeSky(pos)) return;
        for(var other:world.players()) if(!other.isSpectator() && other.isAlive()) {
            double d=other.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            if(d<40*40) return;
            // Conservative off-camera appearance; no ray can load an unknown chunk.
            var delta=new net.minecraft.world.phys.Vec3(pos.getX()+.5-other.getX(),pos.getY()+1-other.getEyeY(),pos.getZ()+.5-other.getZ());
            if(d<96*96 && other.getLookAngle().dot(delta.normalize())>0) return;
        }
        if(player.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>64*64) return;
        if(r.arrivals.stream().anyMatch(a -> a.hunter==r.hunter && a.position.distSqr(pos)<128*128) || r.arrivals.size()>=256) return;
        var npc=ModEntities.AMBIENT_ADVENTURER.create(world,EntitySpawnReason.EVENT); if(npc==null) return;
        npc.initialize(r.hunter,world.getRandom().nextInt(3)); npc.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        if(!world.noCollision(npc,npc.getBoundingBox()) || !world.getWorldBorder().isWithinBounds(npc.getBoundingBox())
                || AmbientAdventurerCombat.threats(world,npc).size()>=2) { npc.discard(); return; }
        // Reserve BEFORE native add, including all clustered players. Roll back a failed add.
        if(add(world,r,npc,pos,r.hunter)) r.attempts=0;
    }
    static boolean add(ServerLevel world,Runtime r,AmbientAdventurerEntity npc,BlockPos pos,boolean hunter) {
        if(!caps(world,r,pos,hunter)) { npc.discard(); return false; }
        r.live.put(npc.getUUID(),npc);
        try {
            if(!world.addFreshEntity(npc) || npc.isRemoved()) { r.live.remove(npc.getUUID()); npc.discard(); return false; }
        } catch(RuntimeException failure) { r.live.remove(npc.getUUID()); npc.discard(); throw failure; }
        r.arrivals.add(new Arrival(pos,hunter,r.tick+(hunter?12000:6000))); return true;
    }
}
