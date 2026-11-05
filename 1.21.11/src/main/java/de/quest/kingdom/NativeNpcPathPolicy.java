package de.quest.kingdom;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;

/** Bounds and loaded evidence around the region used by Minecraft's own pathfinder. */
public final class NativeNpcPathPolicy {
    private NativeNpcPathPolicy() {}
    /** Filter vanilla's collision-checked neighbors before its search chooses a shortcut. */
    static int certifiedNeighbors(net.minecraft.world.level.pathfinder.Node[] nodes,int count,Set<BlockPos> floor) {
        int kept=0;
        for(int i=0;i<count;i++) {
            var node=nodes[i];
            if(node!=null && floor.contains(node.asBlockPos())) nodes[kept++]=node;
        }
        java.util.Arrays.fill(nodes,kept,count,null);
        return kept;
    }
    static boolean workplaceWarning(boolean attempted,boolean moving,KingdomNpcNavigation.Failure failure) {
        return attempted && !moving && failure==KingdomNpcNavigation.Failure.NO_PATH;
    }
    public static boolean loaded(BlockPos origin,int padding,float distance,BiPredicate<Integer,Integer> loaded) {
        if(origin==null || loaded==null || padding<0 || padding>8 || !Float.isFinite(distance) || distance<=0 || distance>24) return false;
        int radius=(int)(distance+padding);
        for(int x=(origin.getX()-radius)>>4;x<=(origin.getX()+radius)>>4;x++)
            for(int z=(origin.getZ()-radius)>>4;z<=(origin.getZ()+radius)>>4;z++) if(!loaded.test(x,z)) return false;
        return true;
    }
    public static Optional<BlockPos> sleepApproach(Set<BlockPos> floor,BlockPos head,BlockPos from) {
        return floor.stream().filter(pos -> pos.distSqr(head)<=2 && pos.getY()==head.getY())
                .min(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(from)).thenComparingLong(BlockPos::asLong));
    }
}
