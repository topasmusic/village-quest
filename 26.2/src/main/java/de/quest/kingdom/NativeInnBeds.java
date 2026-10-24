package de.quest.kingdom;

import de.quest.entity.KingdomNpcEntity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/** Loaded-only own HOME tickets and real Vanilla sleeping poses; never modifies a villager Brain. */
public final class NativeInnBeds {
    private NativeInnBeds() {}
    private record Pair(boolean observed,BlockState foot,BlockState head) {}
    public static boolean complete(BlockState foot,BlockState head) {
        return foot!=null && head!=null && foot.getBlock() instanceof BedBlock && head.getBlock()==foot.getBlock()
                && foot.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && foot.getValue(BedBlock.PART)==BedPart.FOOT && head.getValue(BedBlock.PART)==BedPart.HEAD
                && foot.getValue(BedBlock.FACING)==head.getValue(BedBlock.FACING);
    }
    static boolean poiAreaLoaded(ServerLevel world,BlockPos head) {
        if(!world.isPositionEntityTicking(head)) return false;
        int x=head.getX()>>4,z=head.getZ()>>4;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
            if(!world.hasChunkAt(new BlockPos((x+dx)*16,head.getY(),(z+dz)*16))) return false;
        return true;
    }
    /** Radius zero, exact-head predicate, all potentially consulted POI columns already loaded. */
    static boolean takeHome(ServerLevel world,BlockPos head) {
        if(!poiAreaLoaded(world,head)) return false;
        var poi=world.getPoiManager(); var info=poi.getDebugPoiInfo(head);
        return info!=null && info.poiType().is(PoiTypes.HOME) && info.freeTicketCount()>0
                && poi.take(type -> type.is(PoiTypes.HOME),(type,pos) -> pos.equals(head),head,0).filter(head::equals).isPresent();
    }
    public static NativeBuildingProbe.BedUse buildingUse(ServerLevel world,UUID inn,BlockPos foot,BlockPos head,BlockState state,
                                                        Set<BlockPos> homes,boolean completeHomes) {
        var kingdoms=LivingKingdomsState.get(world.getServer()); var beds=kingdoms.people().beds();
        if(!completeHomes || beds.readOnly() || kingdoms.founding().readOnly()) return use(KingdomBedRegistry.Permission.UNKNOWN,false);
        if(homes.contains(foot) || homes.contains(head) || kingdoms.founding().bedOwner(world.dimension().identifier().toString(),foot).isPresent()
                || kingdoms.founding().bedOwner(world.dimension().identifier().toString(),head).isPresent()) return use(KingdomBedRegistry.Permission.RESERVED,false);
        var permission=beds.permission(inn,world.dimension().identifier().toString(),foot);
        if(permission!=KingdomBedRegistry.Permission.AVAILABLE) return use(permission,false);
        boolean own=beds.owner(world.dimension().identifier().toString(),foot).isPresent();
        if(own) {
            var lease=beds.owner(world.dimension().identifier().toString(),foot).flatMap(beds::lease);
            boolean matching=lease.filter(owned -> owned.foot().equals(foot) && owned.head().equals(head)
                    && owned.block().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())) && owned.facing()==state.getValue(BedBlock.FACING)).isPresent();
            return use(matching?KingdomBedRegistry.Permission.AVAILABLE:KingdomBedRegistry.Permission.RESERVED,matching);
        }
        if(state.getValue(BedBlock.OCCUPIED)) return use(KingdomBedRegistry.Permission.RESERVED,false);
        if(!world.hasChunkAt(head) || !world.isPositionEntityTicking(head)) return use(KingdomBedRegistry.Permission.UNKNOWN,false);
        var info=world.getPoiManager().getDebugPoiInfo(head);
        if(info==null || !info.poiType().is(PoiTypes.HOME)) return use(KingdomBedRegistry.Permission.UNKNOWN,false);
        return use(info.freeTicketCount()>0?KingdomBedRegistry.Permission.AVAILABLE:KingdomBedRegistry.Permission.RESERVED,false);
    }
    private static NativeBuildingProbe.BedUse use(KingdomBedRegistry.Permission permission,boolean own) { return new NativeBuildingProbe.BedUse(permission,own); }
    public static boolean equivalentOwnOccupancy(ServerLevel world,BlockPos pos,BlockState before,BlockState after) {
        if(before==null || !(before.getBlock() instanceof BedBlock) || before.getBlock()!=after.getBlock()
                || !before.setValue(BedBlock.OCCUPIED,false).equals(after.setValue(BedBlock.OCCUPIED,false))) return false;
        var state=LivingKingdomsState.get(world.getServer()); var dimension=world.dimension().identifier().toString();
        return state.people().beds().owner(dimension,pos).flatMap(owner -> state.people().beds().lease(owner))
                .filter(lease -> !lease.releasing() && lease.block().equals(BuiltInRegistries.BLOCK.getKey(before.getBlock()))
                        && lease.facing()==before.getValue(BedBlock.FACING)
                        && state.people().person(lease.owner()).filter(person -> person.role()==KingdomPersonRegistry.Role.INNKEEPER).isPresent()).isPresent();
    }
    public static Optional<KingdomBedRegistry.LeaseView> observe(ServerLevel world,KingdomNpcEntity npc,KingdomPersonRegistry.View person,long now) {
        if(person.role()!=KingdomPersonRegistry.Role.INNKEEPER) return Optional.empty();
        var state=LivingKingdomsState.get(world.getServer()); var beds=state.people().beds(); if(beds.readOnly()) return Optional.empty();
        var current=beds.lease(person.id());
        if(current.isPresent()) {
            var lease=current.get();
            if(lease.releasing()) return Optional.empty();
            if(!lease.body().equals(npc.getUUID()) || lease.generation()!=person.generation()) { beds.release(lease.id()); return Optional.empty(); }
            var inn=state.anchors().official(lease.inn(),BuildingScanner.Role.INN);
            if(inn.isEmpty() || !state.anchors().mayContinue(inn.get().id(),now)) return Optional.empty();
            var certified=state.anchors().report(inn.get().id());
            if(certified.isEmpty()) return Optional.empty();
            if(!certified.get().stations().getOrDefault(BuildingScanner.Feature.BED,Set.of()).contains(lease.foot())) {
                beds.release(lease.id()); return Optional.empty();
            }
            var probe=inspect(world,lease.foot(),lease.head());
            if(!probe.observed) return Optional.empty();
            if(!matches(probe,lease)) { beds.release(lease.id()); return Optional.empty(); }
            var home=foreignHome(world,lease.foot(),lease.head());
            if(home==KingdomBedRegistry.Permission.UNKNOWN) return Optional.empty();
            if(home==KingdomBedRegistry.Permission.RESERVED) { beds.release(lease.id()); return Optional.empty(); }
            if(!poiAreaLoaded(world,lease.head())) return Optional.empty();
            var info=world.getPoiManager().getDebugPoiInfo(lease.head());
            if(info==null || !info.poiType().is(PoiTypes.HOME)) return Optional.empty();
            if(info.freeTicketCount()>0 && !takeHome(world,lease.head())) return Optional.empty();
            return current;
        }
        var anchor=state.anchors().official(person.village(),BuildingScanner.Role.INN);
        if(anchor.isEmpty() || !state.anchors().maySpawn(anchor.get().id(),now) || !npc.beginBedAttempt(now)) return Optional.empty();
        var report=state.anchors().report(anchor.get().id()); if(report.isEmpty()) return Optional.empty();
        for(var foot:report.get().stations().getOrDefault(BuildingScanner.Feature.BED,Set.of()).stream()
                .sorted(java.util.Comparator.comparingDouble(pos -> pos.distSqr(npc.blockPosition()))).limit(8).toList()) {
            if(!world.hasChunkAt(foot) || !KingdomBuildingService.workBudget(world.getServer()).takeBlock()) return Optional.empty();
            var footState=world.getBlockState(foot);
            if(!(footState.getBlock() instanceof BedBlock) || footState.getValue(BedBlock.PART)!=BedPart.FOOT) continue;
            var head=foot.relative(footState.getValue(BedBlock.FACING));
            if(!world.hasChunkAt(head) || !world.isPositionEntityTicking(head) || !KingdomBuildingService.workBudget(world.getServer()).takeBlock()) return Optional.empty();
            var headState=world.getBlockState(head);
            if(!complete(footState,headState) || footState.getValue(BedBlock.OCCUPIED) || headState.getValue(BedBlock.OCCUPIED)
                    || foreignHome(world,foot,head)!=KingdomBedRegistry.Permission.AVAILABLE || !takeHome(world,head)) continue;
            var ticket=npc.identity().ticket(npc.getUUID()).orElseThrow();
            var result=beds.claim(ticket,person.village(),foot,footState.getValue(BedBlock.FACING),BuiltInRegistries.BLOCK.getKey(footState.getBlock()),now);
            if(result.isEmpty()) world.getPoiManager().release(head);
            return result;
        }
        return Optional.empty();
    }
    public static boolean trySleep(ServerLevel world,KingdomNpcEntity npc,KingdomBedRegistry.LeaseView lease) {
        if(lease.releasing() || !lease.body().equals(npc.getUUID()) || !poiAreaLoaded(world,lease.head())) return false;
        if(npc.isSleeping()) return npc.getSleepingPos().filter(lease.head()::equals).isPresent() && npc.rest().sleep(world,npc,lease.head());
        if(npc.blockPosition().distSqr(lease.head())>4) return false;
        var pair=inspect(world,lease.foot(),lease.head());
        if(!matches(pair,lease) || pair.foot.getValue(BedBlock.OCCUPIED) || pair.head.getValue(BedBlock.OCCUPIED)
                || foreignHome(world,lease.foot(),lease.head())!=KingdomBedRegistry.Permission.AVAILABLE
                || otherSleeper(world,lease.head(),npc.getUUID())!=KingdomBedRegistry.Permission.AVAILABLE
                || !KingdomBuildingService.workBudget(world.getServer()).takeBlock()) return false;
        npc.getNavigation().stop(); return npc.rest().sleep(world,npc,lease.head());
    }
    public static void wake(ServerLevel world,KingdomNpcEntity npc,KingdomBedRegistry.LeaseView lease) {
        if(!npc.isSleeping() || npc.getSleepingPos().filter(lease.head()::equals).isEmpty() || !poiAreaLoaded(world,lease.head())) return;
        var pair=inspect(world,lease.foot(),lease.head()); if(!pair.observed) return;
        var other=otherSleeper(world,lease.head(),npc.getUUID()); if(other==KingdomBedRegistry.Permission.UNKNOWN) return;
        npc.rest().wake(world,npc);
        if(other==KingdomBedRegistry.Permission.RESERVED && complete(pair.foot,pair.head)) {
            world.setBlock(lease.foot(),pair.foot.setValue(BedBlock.OCCUPIED,true),3);
            world.setBlock(lease.head(),pair.head.setValue(BedBlock.OCCUPIED,true),3);
        }
    }
    public static void cleanup(ServerLevel world,KingdomBedRegistry.LeaseView lease) {
        if(!lease.releasing() || !lease.dimension().equals(world.dimension().identifier().toString()) || !poiAreaLoaded(world,lease.head())) return;
        var pair=inspect(world,lease.foot(),lease.head()); if(!pair.observed) return;
        var home=foreignHome(world,lease.foot(),lease.head()); if(home==KingdomBedRegistry.Permission.UNKNOWN) return;
        var other=otherSleeper(world,lease.head(),lease.body()); if(other==KingdomBedRegistry.Permission.UNKNOWN) return;
        var body=world.getEntity(lease.body());
        if(body instanceof KingdomNpcEntity npc && npc.identity().ticket(npc.getUUID()).filter(ticket -> ticket.person().equals(lease.owner()) && ticket.generation()==lease.generation()).isPresent()) wake(world,npc,lease);
        if(matches(pair,lease) && other==KingdomBedRegistry.Permission.AVAILABLE && (pair.foot.getValue(BedBlock.OCCUPIED) || pair.head.getValue(BedBlock.OCCUPIED))) {
            world.setBlock(lease.foot(),pair.foot.setValue(BedBlock.OCCUPIED,false),3);
            world.setBlock(lease.head(),pair.head.setValue(BedBlock.OCCUPIED,false),3);
        }
        if(home!=KingdomBedRegistry.Permission.RESERVED) {
            var info=world.getPoiManager().getDebugPoiInfo(lease.head());
            if(info!=null && info.poiType().is(PoiTypes.HOME) && info.freeTicketCount()==0) world.getPoiManager().release(lease.head());
        }
        if(LivingKingdomsState.get(world.getServer()).people().beds().completeRelease(lease.id())) KingdomBuildingService.onBlockChanged(world,lease.foot());
    }
    private static Pair inspect(ServerLevel world,BlockPos foot,BlockPos head) {
        if(!world.hasChunkAt(foot) || !world.hasChunkAt(head) || !world.isPositionEntityTicking(head)
                || !KingdomBuildingService.workBudget(world.getServer()).takeBlocks(2)) return new Pair(false,null,null);
        return new Pair(true,world.getBlockState(foot),world.getBlockState(head));
    }
    private static boolean matches(Pair pair,KingdomBedRegistry.LeaseView lease) {
        return pair.observed && complete(pair.foot,pair.head) && pair.foot.getValue(BedBlock.FACING)==lease.facing()
                && BuiltInRegistries.BLOCK.getKey(pair.foot.getBlock()).equals(lease.block());
    }
    private static KingdomBedRegistry.Permission foreignHome(ServerLevel world,BlockPos foot,BlockPos head) {
        var residents=world.getEntitiesOfClass(Villager.class,new AABB(head).inflate(64,24,64),v -> v.isAlive() && !v.isRemoved()).stream().limit(129).toList();
        if(residents.size()>128) return KingdomBedRegistry.Permission.UNKNOWN;
        return residents.stream().anyMatch(v -> v.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(home -> home.dimension().equals(world.dimension()) && (home.pos().equals(head) || home.pos().equals(foot))).isPresent())
                ?KingdomBedRegistry.Permission.RESERVED:KingdomBedRegistry.Permission.AVAILABLE;
    }
    private static KingdomBedRegistry.Permission otherSleeper(ServerLevel world,BlockPos head,UUID except) {
        var entities=world.getEntitiesOfClass(LivingEntity.class,new AABB(head).inflate(2),entity -> entity.isAlive() && !entity.getUUID().equals(except)).stream().limit(129).toList();
        if(entities.size()>128) return KingdomBedRegistry.Permission.UNKNOWN;
        return entities.stream().anyMatch(entity -> entity.getSleepingPos().filter(head::equals).isPresent())
                ?KingdomBedRegistry.Permission.RESERVED:KingdomBedRegistry.Permission.AVAILABLE;
    }
}
