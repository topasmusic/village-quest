package de.quest.kingdom;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Incremental bounded spatial policy. Probe adapters may read loaded blocks only; no world or entity ticks. */
public final class BuildingScanner {
    private final BlockPos anchor;
    private final Direction facing;
    private final Role role;
    private final Range range;
    private final Probe probe;
    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final Map<BlockPos, Boolean> shelterCache = new HashMap<>();
    private final int width, height, total;
    private int cursor;
    private boolean unknown;
    private Report report;

    public BuildingScanner(BlockPos anchor, Direction facing, Role role, Range range, Probe probe) {
        if (anchor == null || facing == null || !facing.getAxis().isHorizontal()
                || role == null || range == null || probe == null) throw new IllegalArgumentException("Invalid scan");
        this.anchor = anchor.immutable(); this.facing = facing; this.role = role; this.range = range; this.probe = probe;
        width = range.horizontal * 2 + 1; height = range.vertical * 2 + 1; total = width * width * height;
    }

    public int advance(int grantedSamples) {
        if (done() || grantedSamples <= 0) return 0;
        int used = 0, budget = Math.min(256, grantedSamples);
        while (cursor < total && used < budget) {
            int index = cursor++;
            int x = index % width - range.horizontal;
            int z = (index / width) % width - range.horizontal;
            int y = index / (width * width) - range.vertical;
            BlockPos pos = anchor.offset(x, y, z);
            Cell cell = probe.read(pos);
            if (cell == null || !cell.loaded) { cell = Cell.unknown(); unknown = true; }
            cells.put(pos, cell);
            used++;
        }
        if (cursor == total) { report = evaluate(); cells.clear(); shelterCache.clear(); }
        return used;
    }

    public boolean done() { return report != null; }
    public Optional<Report> report() { return Optional.ofNullable(report); }

    private Report evaluate() {
        Set<BlockPos> walkable = connectedFloor();
        Map<Feature, Set<BlockPos>> stations = new EnumMap<>(Feature.class);
        for (var entry : cells.entrySet()) {
            if (!entry.getValue().loaded || !approachable(entry.getKey(), walkable)) continue;
            for (Feature feature : entry.getValue().features) {
                // Two-block furniture is resolved only from the budgeted scan, never extra world reads.
                if(feature==Feature.TABLE_TOP) continue;
                if(feature==Feature.TABLE_LEG) {
                    if(cell(entry.getKey().above()).features.contains(Feature.TABLE_TOP))
                        stations.computeIfAbsent(Feature.TABLE,ignored -> new HashSet<>()).add(entry.getKey());
                    continue;
                }
                if (feature==Feature.BED && !completeBed(entry.getKey(),entry.getValue())) continue;
                BlockPos station = feature == Feature.CONTAINER && entry.getValue().resourceRoot != null
                        ? entry.getValue().resourceRoot : entry.getKey();
                stations.computeIfAbsent(feature, ignored -> new HashSet<>()).add(station);
            }
        }
        Set<BlockPos> tables = stations.getOrDefault(Feature.TABLE, Set.of());
        Set<BlockPos> seats = new HashSet<>();
        for (BlockPos seat : stations.getOrDefault(Feature.SEAT, Set.of())) {
            Direction front = cell(seat).seatFacing;
            if (front != null && tables.stream().anyMatch(table -> facesTable(seat, front, table))) seats.add(seat);
        }
        stations.put(Feature.SEAT, seats);
        Map<Check, Boolean> checks = new EnumMap<>(Check.class);
        checks.put(Check.ENTRY, walkable.size() >= 4);
        checks.put(Check.LIGHT, !stations.getOrDefault(Feature.LIGHT, Set.of()).isEmpty());
        if (indoor()) checks.put(Check.ROOF, walkable.size()>=4 && sheltered(anchor.relative(facing)) && coveredFurnishings(stations));
        switch (role) {
            case COUNCIL -> {
                checks.put(Check.TABLE, !tables.isEmpty()); checks.put(Check.SEATS, seats.size() >= 2);
                checks.put(Check.BOOKS, count(stations, Feature.BOOKS) >= 1);
            }
            case INN -> {
                checks.put(Check.TABLE, !tables.isEmpty()); checks.put(Check.SEATS, seats.size() >= 5);
                checks.put(Check.BARRELS, count(stations, Feature.BARREL) >= 2);
                checks.put(Check.KITCHEN, count(stations, Feature.KITCHEN) >= 1);
                checks.put(Check.BED, count(stations, Feature.BED) >= 1);
            }
            case MARKET -> {
                // Certify each surface locally: floor blocks and remote storage cannot lend a stall.
                for(Feature feature:List.of(Feature.COUNTER,Feature.STALL)) {
                    Set<BlockPos> usable=new HashSet<>();
                    for(BlockPos counter:stations.getOrDefault(feature,Set.of()))
                        if(marketServiceCounter(counter,walkable,stations.getOrDefault(Feature.CONTAINER,Set.of()))) usable.add(counter);
                    stations.put(feature,usable);
                }
                checks.put(Check.CONTAINERS, count(stations, Feature.CONTAINER) >= 1);
                checks.put(Check.COUNTER, !stations.getOrDefault(Feature.STALL, Set.of()).isEmpty()
                        || connectedCounter(stations.getOrDefault(Feature.COUNTER, Set.of())));
                checks.put(Check.HANDOVER, walkable.contains(anchor.relative(facing))
                        && walkable.contains(anchor.relative(facing, 2)));
            }
            case STOREHOUSE -> {
                checks.put(Check.CONTAINERS, count(stations, Feature.CONTAINER) >= 4);
                checks.put(Check.WORKSTATION, count(stations, Feature.WORKSTATION) >= 1);
                checks.put(Check.HANDOVER, walkable.contains(anchor.relative(facing))
                        && walkable.contains(anchor.relative(facing, 2)));
            }
        }
        Status status = unknown ? Status.UNKNOWN : checks.values().stream().allMatch(Boolean::booleanValue)
                ? Status.VALID : Status.INVALID;
        return new Report(role, status, checks, stations, walkable);
    }

    private Set<BlockPos> connectedFloor() {
        BlockPos entry = anchor.relative(facing);
        Set<BlockPos> reached = new HashSet<>();
        if (!canStand(entry)) return reached;
        // A roof beyond BASIC must not hide nearby furnishings. Once this room's roof is known,
        // keep its sheltered connectivity so another building cannot lend furniture across a yard.
        boolean followRoof = indoor() && sheltered(entry);
        var queue = new ArrayDeque<BlockPos>(); queue.add(entry); reached.add(entry);
        while (!queue.isEmpty()) {
            BlockPos from = queue.removeFirst();
            for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                for (int dy : new int[] { 0, 1, -1 }) {
                    BlockPos candidate = from.relative(direction).above(dy);
                    if (!reached.contains(candidate) && canStand(candidate) && (!followRoof || sheltered(candidate))
                            && (dy <= 0 || cell(from.above(2)).passable)) {
                        reached.add(candidate); queue.addLast(candidate);
                    }
                }
            }
        }
        return reached;
    }

    private boolean canStand(BlockPos pos) {
        Cell foot = cell(pos), head = cell(pos.above()), floor = cell(pos.below());
        return foot.loaded && head.loaded && floor.loaded && foot.passable && head.passable && floor.support;
    }

    private boolean approachable(BlockPos pos, Set<BlockPos> walkable) {
        if (walkable.contains(pos) || walkable.contains(pos.below())) return true;
        for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            if (walkable.contains(pos.relative(direction)) || walkable.contains(pos.relative(direction).below())) return true;
        }
        return false;
    }

    private boolean sheltered(BlockPos stand) {
        return shelterCache.computeIfAbsent(stand, pos -> canStand(pos) && roofOver(pos,2));
    }
    private boolean roofOver(BlockPos pos,int clearance) {
        for (int y=clearance; pos.getY()+y<=anchor.getY()+range.vertical; y++)
            if (cell(pos.above(y)).loaded && cell(pos.above(y)).roof) return true;
        return false;
    }
    private boolean indoor() { return role==Role.COUNCIL || role==Role.INN; }
    private boolean coveredFurnishings(Map<Feature,Set<BlockPos>> stations) {
        var points=new HashSet<BlockPos>(); points.add(anchor.relative(facing));
        var features=role==Role.COUNCIL?List.of(Feature.TABLE,Feature.SEAT,Feature.BOOKS)
                :List.of(Feature.TABLE,Feature.SEAT,Feature.BARREL,Feature.KITCHEN,Feature.BED);
        for (var feature:features) points.addAll(stations.getOrDefault(feature,Set.of()));
        long covered=points.stream().filter(pos -> roofOver(pos,1)).count();
        return covered*4>=points.size()*3;
    }

    private boolean facesTable(BlockPos seat, Direction front, BlockPos table) {
        if (Math.abs(table.getY() - seat.getY()) > 1) return false;
        int dx = table.getX() - seat.getX(), dz = table.getZ() - seat.getZ();
        int along = dx * front.getStepX() + dz * front.getStepZ();
        int sideways = Math.abs(dx * front.getStepZ() - dz * front.getStepX());
        if (along < 1 || along > 3 || sideways != 0) return false;
        for (int step = 1; step < along; step++) {
            Cell between = cell(seat.relative(front, step));
            if (!between.loaded || !between.passable) return false;
        }
        return true;
    }

    private static boolean connectedCounter(Set<BlockPos> positions) {
        return positions.stream().anyMatch(pos -> positions.stream().anyMatch(other -> !pos.equals(other)
                && pos.getY() == other.getY() && Math.abs(pos.getX() - other.getX()) + Math.abs(pos.getZ() - other.getZ()) == 1));
    }

    private static boolean marketServiceCounter(BlockPos counter,Set<BlockPos> floor,Set<BlockPos> containers) {
        boolean serviceSides=false;
        for(Direction direction:List.of(Direction.NORTH,Direction.EAST))
            if(floor.contains(counter.relative(direction)) && floor.contains(counter.relative(direction.getOpposite()))) serviceSides=true;
        if(!serviceSides) return false;
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) {
            if(Math.abs(dx)+Math.abs(dz)>3) continue;
            for(int dy=-1;dy<=1;dy++)
                if((dx!=0 || dy!=0 || dz!=0) && containers.contains(counter.offset(dx,dy,dz))) return true;
        }
        return false;
    }
    private static int count(Map<Feature, Set<BlockPos>> stations, Feature feature) {
        return stations.getOrDefault(feature, Set.of()).size();
    }
    private Cell cell(BlockPos pos) { return cells.getOrDefault(pos, Cell.unknown()); }

    private boolean completeBed(BlockPos pos,Cell foot) {
        if (foot.bed==null) return true; // Abstract probes may supply already-certified BED evidence.
        if (foot.bed.part()!=net.minecraft.world.level.block.state.properties.BedPart.FOOT) return false;
        Cell head=cell(pos.relative(foot.bed.facing()));
        return head.loaded && head.bed!=null && head.bed.part()==net.minecraft.world.level.block.state.properties.BedPart.HEAD
                && head.bed.block()==foot.bed.block() && head.bed.facing()==foot.bed.facing()
                && (!(foot.bed.occupied() || head.bed.occupied()) || foot.bed.ownHeld());
    }

    @FunctionalInterface public interface Probe { Cell read(BlockPos pos); }
    public enum Role { COUNCIL, INN, MARKET, STOREHOUSE }
    public enum Status { VALID, INVALID, UNKNOWN }
    public enum Range {
        BASIC(8, 4), EXTENDED(12, 8);
        public final int horizontal, vertical;
        Range(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }
    }
    public enum Feature { TABLE, SEAT, BOOKS, BARREL, CONTAINER, KITCHEN, BED, COUNTER, WORKSTATION, LIGHT, STALL, TABLE_LEG, TABLE_TOP }
    public enum Check { ENTRY, LIGHT, ROOF, TABLE, SEATS, BOOKS, BARRELS, KITCHEN, BED, COUNTER, CONTAINERS, WORKSTATION, HANDOVER }
    public record Cell(boolean loaded, boolean passable, boolean support, boolean roof,
                       Set<Feature> features, Direction seatFacing, BlockPos resourceRoot, BedGeometry bed) {
        public Cell(boolean loaded, boolean passable, boolean support, boolean roof,
                    Set<Feature> features, Direction seatFacing, BlockPos resourceRoot) {
            this(loaded, passable, support, roof, features, seatFacing, resourceRoot, null);
        }
        public Cell(boolean loaded, boolean passable, boolean support, boolean roof,
                    Set<Feature> features, Direction seatFacing) {
            this(loaded, passable, support, roof, features, seatFacing, null);
        }
        public Cell { features = Set.copyOf(features); }
        public static Cell unknown() { return new Cell(false, false, false, false, Set.of(), null); }
    }
    public record BedGeometry(net.minecraft.world.level.block.Block block,Direction facing,
                              net.minecraft.world.level.block.state.properties.BedPart part,boolean occupied,boolean ownHeld) {}
    public record Report(Role role, Status status, Map<Check, Boolean> checks,
                         Map<Feature, Set<BlockPos>> stations, Set<BlockPos> walkable) {
        public Report {
            checks = Map.copyOf(checks);
            Map<Feature, Set<BlockPos>> frozen = new EnumMap<>(Feature.class);
            stations.forEach((feature, positions) -> frozen.put(feature, Set.copyOf(positions)));
            stations = Map.copyOf(frozen); walkable = Set.copyOf(walkable);
        }
    }
}
