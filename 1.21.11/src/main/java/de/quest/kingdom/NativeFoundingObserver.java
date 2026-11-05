package de.quest.kingdom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/** Loaded local founding evidence. Reads Vanilla assignments, never writes Brains or creates residents. */
public final class NativeFoundingObserver {
    private NativeFoundingObserver() {}
    public static Evidence observe(ServerLevel world, BlockPos anchor, BuildingScanner.Range range, int pathBudget) {
        return observe(world, anchor, range, pathBudget, () -> true, id -> true);
    }
    public static Evidence observe(ServerLevel world, BlockPos anchor, BuildingScanner.Range range, int pathBudget,
                                  java.util.function.BooleanSupplier blockRead, java.util.function.Predicate<java.util.UUID> eligible) {
        if (world == null || anchor == null || range == null || pathBudget < 0) return new Evidence(0, List.of(), false, 0);
        AABB area = new AABB(anchor.getX() - range.horizontal, anchor.getY() - range.vertical, anchor.getZ() - range.horizontal,
                anchor.getX() + range.horizontal + 1, anchor.getY() + range.vertical + 1, anchor.getZ() + range.horizontal + 1);
        return observeArea(world,area,pathBudget,blockRead,eligible,p -> true);
    }
    /** Population recognition covers the registered village area, independently of each building scan. */
    public static Evidence observeVillage(ServerLevel world,LivingKingdomsState state,java.util.UUID village,int pathBudget,
            java.util.function.BooleanSupplier blockRead,java.util.function.Predicate<java.util.UUID> eligible) {
        var zones=state.zones(village);
        if(zones.isEmpty()) return new Evidence(0,List.of(),false,0);
        var area=KingdomVillageService.observationArea(world,null,zones);
        return observeArea(world,area,pathBudget,blockRead,eligible,p -> zones.stream().anyMatch(z -> z.contains(p)));
    }
    private static Evidence observeArea(ServerLevel world,AABB area,int pathBudget,
            java.util.function.BooleanSupplier blockRead,java.util.function.Predicate<java.util.UUID> eligible,java.util.function.Predicate<BlockPos> location) {
        if (!loaded(world, BlockPos.containing(area.minX,area.minY,area.minZ),
                BlockPos.containing(area.maxX-1,area.maxY-1,area.maxZ-1), 0)) return new Evidence(0, List.of(), false, 0);
        List<Villager> adults = world.getEntitiesOfClass(Villager.class, area,
                villager -> villager.isAlive() && !villager.isRemoved() && !villager.isBaby()).stream()
                .filter(v -> v.isAlive() && !v.isRemoved() && !v.isBaby() && inside(area, v.blockPosition()) && location.test(v.blockPosition()) && eligible.test(v.getUUID())).limit(64).toList();
        List<VillageFoundingRegistry.Resident> evidence = new ArrayList<>(); Set<BlockPos> beds = new HashSet<>();
        boolean complete = true; int paths = 0, budget = Math.min(12, pathBudget);
        for (Villager villager : adults) {
            GlobalPos assigned = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
            if (assigned == null || !assigned.dimension().equals(world.dimension()) || !inside(area, assigned.pos()) || !location.test(assigned.pos())) continue;
            BlockPos home = assigned.pos(), origin = villager.blockPosition();
            if (!loaded(world, origin, home, 8)) { complete = false; continue; }
            if (!blockRead.getAsBoolean()) { complete = false; break; }
            var homeState = world.getBlockState(home);
            if (!(homeState.getBlock() instanceof BedBlock)) continue;
            BlockPos foot = homeState.getValue(BedBlock.PART) == BedPart.FOOT ? home
                    : home.relative(homeState.getValue(BedBlock.FACING).getOpposite());
            BlockPos head = foot.relative(homeState.getValue(BedBlock.FACING));
            if (!inside(area, foot) || !inside(area, head) || !location.test(foot) || !location.test(head) || beds.contains(foot)) continue;
            if (!world.hasChunkAt(foot) || !world.hasChunkAt(head)) { complete = false; continue; }
            if (!blockRead.getAsBoolean()) { complete = false; break; }
            var footState = world.getBlockState(foot);
            if (!blockRead.getAsBoolean()) { complete = false; break; }
            var headState = world.getBlockState(head);
            if (!footState.is(homeState.getBlock()) || footState.getValue(BedBlock.PART) != BedPart.FOOT
                    || !headState.is(homeState.getBlock()) || headState.getValue(BedBlock.PART) != BedPart.HEAD
                    || footState.getValue(BedBlock.FACING) != headState.getValue(BedBlock.FACING)
                    || !footState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) continue;
            if (paths >= budget) { complete = false; break; }
            paths++; var bedPath = villager.getNavigation().createPath(home, 0);
            if (bedPath == null || !bedPath.canReach()) continue;
            BlockPos job = null;
            var profession = villager.getVillagerData().profession();
            GlobalPos assignment = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
            if (assignment != null && assignment.dimension().equals(world.dimension()) && inside(area, assignment.pos()) && location.test(assignment.pos())
                    && !profession.is(VillagerProfession.NONE) && !profession.is(VillagerProfession.NITWIT)) {
                if (!loaded(world, origin, assignment.pos(), 8)) complete = false;
                else {
                    if (!blockRead.getAsBoolean()) { complete = false; break; }
                    var poi = PoiTypes.forState(world.getBlockState(assignment.pos()));
                    if (poi.isPresent() && profession.value().heldJobSite().test(poi.get())) {
                        if (paths >= budget) { complete = false; break; }
                        // Vanilla AcquirePoi uses this POI's interaction range, not the solid block's interior.
                        paths++; var jobPath = villager.getNavigation().createPath(assignment.pos(), poi.get().value().validRange());
                        if (jobPath != null && jobPath.canReach()) job = assignment.pos().immutable();
                    }
                }
            }
            beds.add(foot.immutable()); evidence.add(new VillageFoundingRegistry.Resident(villager.getUUID(), foot, job));
            if (evidence.size() >= 3 && evidence.stream().anyMatch(r -> r.job() != null)) break;
        }
        return new Evidence(adults.size(), evidence, complete, paths);
    }
    private static boolean inside(AABB area, BlockPos pos) {
        return pos != null && area.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
    private static boolean loaded(ServerLevel world, BlockPos a, BlockPos b, int halo) {
        for (int x = (Math.min(a.getX(), b.getX()) - halo) >> 4; x <= (Math.max(a.getX(), b.getX()) + halo) >> 4; x++) {
            for (int z = (Math.min(a.getZ(), b.getZ()) - halo) >> 4; z <= (Math.max(a.getZ(), b.getZ()) + halo) >> 4; z++)
                if (!world.hasChunk(x, z)) return false;
        }
        return true;
    }
    public record Evidence(int adults, List<VillageFoundingRegistry.Resident> residents, boolean fullyLoaded, int pathChecks) {
        public Evidence { residents = List.copyOf(residents); }
        public int beds() { return residents.size(); }
        public boolean hasJob() { return residents.stream().anyMatch(r -> r.job() != null); }
    }
}
