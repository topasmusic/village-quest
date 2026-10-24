package de.quest.kingdom;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Original Minecraft navigation, constrained to fresh certified floors and loaded bounded search regions. */
public final class KingdomNpcNavigation extends GroundPathNavigation {
    private Set<BlockPos> certified=Set.of();
    private long validUntil=-1;
    enum Failure { NONE,UNKNOWN,BUDGET,NO_PATH }
    private Failure failure=Failure.NONE;
    Failure failure() { return failure; }
    public KingdomNpcNavigation(Mob mob,Level level) { super(mob,level); setCanOpenDoors(true); }
    public void certify(BuildingScanner.Report report,long now) {
        if(report.status()!=BuildingScanner.Status.VALID) { certified=Set.of(); validUntil=-1; stop(); return; }
        certified=report.walkable(); validUntil=now+40;
    }
    public void invalidate() { certified=Set.of(); validUntil=-1; stop(); }
    void certifyFloors(Set<BlockPos> floor,long now) { certified=Set.copyOf(floor); validUntil=now+40; }
    @Override protected PathFinder createPathFinder(int maxNodes) {
        // Search the certified graph itself. Rejecting only the final path repeatedly
        // selects unsafe shortcuts instead of allowing Minecraft to find a safe detour.
        this.nodeEvaluator=new WalkNodeEvaluator() {
            @Override public int getNeighbors(Node[] neighbors,Node node) {
                return NativeNpcPathPolicy.certifiedNeighbors(neighbors,super.getNeighbors(neighbors,node),certified);
            }
        };
        return new PathFinder(this.nodeEvaluator,Math.min(maxNodes,512));
    }
    private boolean permitted(ServerLevel world) {
        long now=world.getServer().overworld().getGameTime();
        return validUntil>=now && !certified.isEmpty() && world.isPositionEntityTicking(mob.blockPosition())
                && NativeNpcPathPolicy.loaded(mob.blockPosition(),8,24,
                    (x,z) -> world.hasChunkAt(new BlockPos(x*16,mob.blockPosition().getY(),z*16)));
    }
    @Override public Path createPath(BlockPos target,int accuracy) {
        // GroundPathNavigation examines the target before delegating to its protected region search.
        failure=Failure.NONE;
        if(!(level instanceof ServerLevel world) || !permitted(world) || !certified.contains(target)) { failure=Failure.UNKNOWN; return null; }
        return super.createPath(target,accuracy);
    }
    @Override protected Path createPath(Set<BlockPos> targets,int padding,boolean above,int accuracy,float distance) {
        if(!(level instanceof ServerLevel world) || !permitted(world) || targets.size()>8 || !certified.containsAll(targets)
                || !NativeNpcPathPolicy.loaded(above?mob.blockPosition().above():mob.blockPosition(),padding,distance,
                    (x,z) -> world.hasChunkAt(new BlockPos(x*16,mob.blockPosition().getY(),z*16)))) { failure=Failure.UNKNOWN; return null; }
        if(!KingdomBuildingService.workBudget(world.getServer()).takePaths(1)) { failure=Failure.BUDGET; return null; }
        Path result=super.createPath(targets,padding,above,accuracy,distance);
        if(!safe(result,world)) { failure=Failure.NO_PATH; return null; }
        failure=Failure.NONE; return result;
    }
    private boolean safe(Path candidate,ServerLevel world) {
        if(candidate==null || !candidate.canReach() || candidate.getNodeCount()>64) return false;
        for(int i=candidate.getNextNodeIndex();i<candidate.getNodeCount();i++) {
            var pos=candidate.getNodePos(i);
            if(!certified.contains(pos) || !world.hasChunkAt(pos) || !world.isPositionEntityTicking(pos)) return false;
        }
        return true;
    }
    @Override public void tick() {
        if(!(level instanceof ServerLevel world) || !permitted(world) || path!=null && !safe(path,world)) { stop(); return; }
        super.tick();
    }
}
