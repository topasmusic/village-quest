package de.quest.ambient;

import de.quest.caravan.AmbientRoadAccess;
import de.quest.kingdom.NativeNpcPathPolicy;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.*;

/** Only the controller can authorize a native search. Automatic goals/recomputations cannot bypass it. */
public final class AmbientAdventurerNavigation extends GroundPathNavigation {
    private final Set<BlockPos> floor=new HashSet<>();
    private BlockPos destination, cursor, lastSafe;
    private long validUntil, lastRequest=-40, lastSearch=-40;
    private boolean searching, failed;
    private double requestedSpeed;
    public AmbientAdventurerNavigation(Mob mob,Level level) {
        super(mob,level); setCanOpenDoors(false); setCanFloat(false);
    }
    @Override protected PathFinder createPathFinder(int count) {
        nodeEvaluator=new WalkNodeEvaluator() {
            @Override public int getNeighbors(Node[] nodes,Node node) {
                int count=super.getNeighbors(nodes,node), kept=0;
                for(int i=0;i<count;i++) if(nodes[i]!=null && floor.contains(nodes[i].asBlockPos())) nodes[kept++]=nodes[i];
                Arrays.fill(nodes,kept,count,null); return kept;
            }
        };
        return new PathFinder(nodeEvaluator,Math.min(512,count));
    }
    public boolean request(BlockPos target,double speed) {
        if(!(level instanceof ServerLevel world) || target==null || !isDone() || destination!=null
                || world.getGameTime()-lastRequest<40 || mob.blockPosition().distSqr(target)>144 || !loaded(world)) return false;
        lastRequest=world.getGameTime(); failed=false; floor.clear(); cursor=mob.blockPosition();
        destination=target.immutable(); lastSafe=null; requestedSpeed=speed; return true;
    }
    public boolean busy() { return destination!=null || !isDone(); }
    public boolean takeFailure() { boolean result=failed; failed=false; return result; }
    private boolean loaded(ServerLevel world) {
        return world.isPositionEntityTicking(mob.blockPosition()) && NativeNpcPathPolicy.loaded(mob.blockPosition(),8,24,
                (x,z) -> world.hasChunkAt(new BlockPos(x*16,mob.blockPosition().getY(),z*16))
                        && world.isPositionEntityTicking(new BlockPos(x*16,mob.blockPosition().getY(),z*16)));
    }
    private boolean safe(Path candidate) {
        if(candidate==null || !candidate.canReach() || candidate.getNodeCount()>32) return false;
        for(int i=candidate.getNextNodeIndex();i<candidate.getNodeCount();i++) if(!floor.contains(candidate.getNodePos(i))) return false;
        return true;
    }
    private void failure() { cancel(); failed=true; }
    public void cancel() {
        stop(); destination=null; floor.clear(); lastSafe=null;
        if(level instanceof ServerLevel world) AmbientAdventurerService.releaseTravelReads(world,mob.getUUID());
    }
    @Override public void tick() {
        if(!(level instanceof ServerLevel world) || !loaded(world)) { if(busy()) failure(); return; }
        if(destination!=null) {
            // At most one 3-height probe plus two body hazard checks per NPC/tick.
            if(!AmbientAdventurerService.takeTravelReads(world,mob.getUUID(),11)) return;
            BlockPos safe=AmbientRoadAccess.near(world,cursor);
            if(safe==null) {
                // Use a reachable safe prefix instead of rejecting the entire walk at a distant wall/edge.
                if(floor.size()<2 || lastSafe==null) { failure(); return; }
                safe=lastSafe; destination=safe;
            }
            if(floor.size()>24) { failure(); return; }
            floor.add(safe); cursor=safe;
            lastSafe=safe;
            int dx=destination.getX()-cursor.getX(), dz=destination.getZ()-cursor.getZ();
            if(dx!=0 || dz!=0) {
                // Cardinal connected corridor: no diagonal corner cutting or planned jumps.
                cursor=cursor.offset(Math.abs(dx)>=Math.abs(dz)?Integer.signum(dx):0,0,Math.abs(dx)<Math.abs(dz)?Integer.signum(dz):0);
                return;
            }
            if(world.getGameTime()-lastSearch<40 || !AmbientAdventurerService.takePath(world)) return;
            lastSearch=world.getGameTime();
            validUntil=world.getGameTime()+100; searching=true;
            Path result;
            try { result=createPath(Set.of(safe),8,false,0,24); } finally { searching=false; }
            destination=null;
            if(!safe(result) || !super.moveTo(result,requestedSpeed)) { failure(); return; }
        }
        if(path!=null && !isDone()) {
            if(world.getGameTime()>validUntil || !safe(path)) { failure(); return; }
            // Recheck the actual next footing before movement; terrain edits stop the path.
            BlockPos next=path.getNextNodePos();
            if(!AmbientAdventurerService.takeTravelReads(world,mob.getUUID(),11)) { mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0); return; }
            if(!next.equals(AmbientRoadAccess.near(world,next))) { failure(); return; }
        }
        super.tick();
    }
    private boolean permitted() { return searching && level instanceof ServerLevel world && loaded(world); }
    @Override public Path createPath(BlockPos target,int accuracy) { return permitted() && floor.contains(target)?super.createPath(target,accuracy):null; }
    @Override public Path createPath(BlockPos target,int radius,int accuracy) { return permitted() && floor.contains(target)?super.createPath(target,radius,accuracy):null; }
    @Override public Path createPath(Entity target,int accuracy) { return permitted() && target!=null && floor.contains(target.blockPosition())?super.createPath(target,accuracy):null; }
    @Override public Path createPath(Stream<BlockPos> targets,int accuracy) { return null; }
    @Override public Path createPath(Set<BlockPos> targets,int accuracy) { return permitted() && floor.containsAll(targets)?super.createPath(targets,accuracy):null; }
    @Override protected Path createPath(Set<BlockPos> targets,int padding,boolean above,int accuracy,float distance) {
        if(!permitted() || targets.size()>1 || !floor.containsAll(targets) || !NativeNpcPathPolicy.loaded(above?mob.blockPosition().above():mob.blockPosition(),padding,distance,
                (x,z) -> level.hasChunkAt(new BlockPos(x*16,mob.blockPosition().getY(),z*16)) && ((ServerLevel)level).isPositionEntityTicking(new BlockPos(x*16,mob.blockPosition().getY(),z*16)))) return null;
        Path result=super.createPath(targets,padding,above,accuracy,distance); return safe(result)?result:null;
    }
    @Override public void recomputePath() { if(path!=null) failure(); }
    @Override public boolean canCutCorner(PathType type) { return false; }
    @Override public boolean moveTo(Path path,double speed) { return searching && safe(path) && super.moveTo(path,speed); }
    @Override public void updatePathfinderMaxVisitedNodes() { /* fixed 512-node search; never enlarge after attribute changes */ }
    @Override public void setMaxVisitedNodesMultiplier(float multiplier) { super.setMaxVisitedNodesMultiplier(1); }
}
