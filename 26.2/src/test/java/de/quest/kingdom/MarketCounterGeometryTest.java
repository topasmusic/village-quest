package de.quest.kingdom;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
final class MarketCounterGeometryTest {
    static final BlockPos ANCHOR=new BlockPos(0,64,0),COUNTER=new BlockPos(3,64,0);
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    BuildingScanner.Report scan(boolean localStorage,boolean rearSpace,boolean floorCounter){
        Map<BlockPos,BlockState> blocks=new HashMap<>();
        var counter=floorCounter?COUNTER.below():COUNTER;
        blocks.put(counter,Blocks.OAK_SLAB.defaultBlockState());blocks.put(counter.east(),Blocks.OAK_SLAB.defaultBlockState());
        blocks.put(localStorage?COUNTER.west():new BlockPos(-6,64,0),Blocks.BARREL.defaultBlockState());
        blocks.put(new BlockPos(0,65,3),Blocks.TORCH.defaultBlockState());
        if(!rearSpace)for(int x=2;x<=5;x++)blocks.put(new BlockPos(x,64,1),Blocks.STONE.defaultBlockState());
        var scanner=new BuildingScanner(ANCHOR,Direction.NORTH,BuildingScanner.Role.MARKET,BuildingScanner.Range.BASIC,pos->{
            var state=blocks.getOrDefault(pos,(pos.getY()==63)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            return NativeBuildingProbe.classify(state,EmptyBlockGetter.INSTANCE,pos);
        });while(!scanner.done())scanner.advance(256);return scanner.report().orElseThrow();
    }
    @Test void twoSurfacesCannotBorrowStorageFromAcrossTheMarket(){assertEquals(BuildingScanner.Status.INVALID,scan(false,true,false).status());}
    @Test void raisedVanillaCounterWithStorageAndBothServiceSidesWorksOutdoors(){var report=scan(true,true,false);assertEquals(BuildingScanner.Status.VALID,report.status(),report.toString());}
    @Test void floorSurfacesAreNotAServiceCounter(){assertEquals(BuildingScanner.Status.INVALID,scan(false,true,true).status());}
    @Test void counterNeedsTwoOppositeReachableServiceSides(){assertEquals(BuildingScanner.Status.INVALID,scan(true,false,false).status());}
}
