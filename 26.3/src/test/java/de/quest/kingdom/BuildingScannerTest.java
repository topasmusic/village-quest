package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Pure spatial-policy fixtures: no levels, entities, ticks, Minecraft runtime or GameTest. */
final class BuildingScannerTest {
    private static final BlockPos ANCHOR = new BlockPos(0, 1, 0);

    @Test void highRoofOutsideBasicRangeDoesNotHideNearbyFurnitureOrLight() {
        var fixture = room();
        BuildingScanner.Probe probe = pos -> {
            if (pos.getY() == 3) return new BuildingScanner.Cell(true, true, false, false, Set.of(), null);
            if (pos.getY() == 6) return solid();
            return fixture.getOrDefault(pos, roomCell(pos));
        };
        var basic = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC, probe);
        finish(basic);
        var report = basic.report().orElseThrow();
        assertTrue(report.checks().get(BuildingScanner.Check.ENTRY));
        assertTrue(report.checks().get(BuildingScanner.Check.LIGHT));
        assertTrue(report.checks().get(BuildingScanner.Check.TABLE));
        assertTrue(report.checks().get(BuildingScanner.Check.SEATS));
        assertFalse(report.checks().get(BuildingScanner.Check.ROOF));
        assertEquals(BuildingScanner.Status.INVALID, report.status());
        var extended = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.EXTENDED, probe);
        finish(extended);
        assertEquals(BuildingScanner.Status.VALID, extended.report().orElseThrow().status());
    }

    @Test void scanNeverExceedsItsGrantedBudgetAndIncompleteEvidenceCannotConfirmABuilding() {
        AtomicInteger reads = new AtomicInteger();
        var scanner = scanner(room(), pos -> { reads.incrementAndGet(); return roomCell(pos); });
        assertEquals(256, scanner.advance(256));
        assertEquals(256, reads.get());
        assertFalse(scanner.done()); assertTrue(scanner.report().isEmpty());
        while (!scanner.done()) assertTrue(scanner.advance(256) <= 256);
        assertEquals(17 * 17 * 9, reads.get());
        int completeReads = reads.get(); assertEquals(0, scanner.advance(256)); assertEquals(completeReads, reads.get());
    }

    @Test void councilRequiresReachableProtectedTableSeatsAndBooksWithoutARequiredBlueprint() {
        var fixture = room();
        var scanner = scanner(fixture, fixture::get);
        finish(scanner);
        assertEquals(BuildingScanner.Status.VALID, scanner.report().orElseThrow().status());
        assertEquals(Set.of(new BlockPos(2, 1, 2), new BlockPos(4, 1, 0)),
                scanner.report().orElseThrow().stations().get(BuildingScanner.Feature.SEAT));
    }

    @Test void furnitureBehindAClosedWallDoesNotCountAsReachableCouncilEvidence() {
        var fixture = room();
        for (int y = 1; y <= 3; y++) for (int z = -8; z <= 8; z++) {
            fixture.put(new BlockPos(1, y, z), solid());
        }
        var scanner = scanner(fixture, fixture::get); finish(scanner);
        var report = scanner.report().orElseThrow();
        assertEquals(BuildingScanner.Status.INVALID, report.status());
        assertFalse(report.checks().get(BuildingScanner.Check.TABLE));
        assertFalse(report.checks().get(BuildingScanner.Check.SEATS));
    }

    @Test void stairsPointingAwayFromTheTableDoNotBecomeSeatsByRawBlockCount() {
        var fixture = room();
        fixture.put(new BlockPos(2, 1, 2), feature(BuildingScanner.Feature.SEAT, Direction.SOUTH));
        var scanner = scanner(fixture, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.INVALID, scanner.report().orElseThrow().status());
        assertFalse(scanner.report().orElseThrow().checks().get(BuildingScanner.Check.SEATS));
    }
    @Test void coveredWoodFenceCountsAsOneTableAtChairHeightInCouncilAndInn() {
        for(var role:List.of(BuildingScanner.Role.COUNCIL,BuildingScanner.Role.INN)) {
            var fixture=room(); var table=new BlockPos(2,1,0);
            fixture.put(table,feature(BuildingScanner.Feature.TABLE_LEG,null));
            fixture.put(table.above(),feature(BuildingScanner.Feature.TABLE_TOP,null));
            fixture.put(new BlockPos(2,1,-1),feature(BuildingScanner.Feature.SEAT,Direction.SOUTH));
            var scanner=new BuildingScanner(ANCHOR,Direction.NORTH,role,BuildingScanner.Range.BASIC,fixture::get); finish(scanner);
            var report=scanner.report().orElseThrow();
            assertTrue(report.checks().get(BuildingScanner.Check.TABLE),role.name());
            assertEquals(Set.of(table),report.stations().get(BuildingScanner.Feature.TABLE));
            assertEquals(3,report.stations().get(BuildingScanner.Feature.SEAT).size(),role.name());
            assertFalse(report.stations().containsKey(BuildingScanner.Feature.TABLE_LEG));
            assertFalse(report.stations().containsKey(BuildingScanner.Feature.TABLE_TOP));
        }
    }
    @Test void bareFenceFloatingCoverAndUnknownTopDoNotInventATable() {
        var table=new BlockPos(2,1,0);
        for(int mode=0;mode<3;mode++) {
            var fixture=room();
            fixture.put(table,mode==1?air():feature(BuildingScanner.Feature.TABLE_LEG,null));
            fixture.put(table.above(),mode==0?air():mode==1?feature(BuildingScanner.Feature.TABLE_TOP,null):BuildingScanner.Cell.unknown());
            var scanner=scanner(fixture,fixture::get); finish(scanner); var report=scanner.report().orElseThrow();
            assertFalse(report.checks().get(BuildingScanner.Check.TABLE));
            assertFalse(report.checks().get(BuildingScanner.Check.SEATS));
            if(mode==2) assertEquals(BuildingScanner.Status.UNKNOWN,report.status());
        }
    }

    @Test void unloadedNeighborIsUnknownAndNeverAnInvalidRoofOrEarnedCompletion() {
        var fixture = room();
        fixture.put(new BlockPos(8, 3, 8), BuildingScanner.Cell.unknown());
        var scanner = scanner(fixture, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.UNKNOWN, scanner.report().orElseThrow().status());
    }

    @Test void aWallBetweenAChairAndTableCannotBeBypassedByIndependentFurnitureReachability() {
        var fixture = room();
        fixture.put(new BlockPos(2, 1, 1), solid());
        var scanner = scanner(fixture, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.INVALID, scanner.report().orElseThrow().status());
        assertFalse(scanner.report().orElseThrow().checks().get(BuildingScanner.Check.SEATS));
    }

    @Test void innRequiresItsTwoBarrelsFiveTableFacingSeatsKitchenAndBed() {
        var fixture = room();
        fixture.put(new BlockPos(2, 1, -2), feature(BuildingScanner.Feature.SEAT, Direction.SOUTH));
        fixture.put(new BlockPos(-4, 1, 4), feature(BuildingScanner.Feature.TABLE, null));
        fixture.put(new BlockPos(-4, 1, 6), feature(BuildingScanner.Feature.SEAT, Direction.NORTH));
        fixture.put(new BlockPos(-6, 1, 4), feature(BuildingScanner.Feature.SEAT, Direction.EAST));
        fixture.put(new BlockPos(-4, 1, -3), feature(BuildingScanner.Feature.BARREL, null));
        fixture.put(new BlockPos(-5, 1, -3), feature(BuildingScanner.Feature.BARREL, null));
        fixture.put(new BlockPos(-6, 1, -3), feature(BuildingScanner.Feature.KITCHEN, null));
        fixture.put(new BlockPos(-7, 1, -3), feature(BuildingScanner.Feature.BED, null));
        var scanner = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.INN,
                BuildingScanner.Range.BASIC, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.VALID, scanner.report().orElseThrow().status());
        fixture.put(new BlockPos(-5, 1, -3), roomCell(new BlockPos(-5, 1, -3)));
        scanner = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.INN,
                BuildingScanner.Range.BASIC, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.INVALID, scanner.report().orElseThrow().status());
        assertFalse(scanner.report().orElseThrow().checks().get(BuildingScanner.Check.BARRELS));
    }

    @Test void marketCanOperateOutdoorsButRequiresConnectedCounterAndClearCustomerSpace() {
        var fixture = room();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            fixture.put(new BlockPos(x, 3, z), new BuildingScanner.Cell(true, true, false, false, Set.of(), null));
        }
        fixture.put(new BlockPos(2, 1, 0), feature(BuildingScanner.Feature.COUNTER, null));
        fixture.put(new BlockPos(3, 1, 0), feature(BuildingScanner.Feature.COUNTER, null));
        fixture.put(new BlockPos(2, 1, -2), feature(BuildingScanner.Feature.CONTAINER, null));
        var scanner = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.MARKET,
                BuildingScanner.Range.BASIC, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.VALID, scanner.report().orElseThrow().status());
        assertFalse(scanner.report().orElseThrow().checks().containsKey(BuildingScanner.Check.ROOF));
    }

    @Test void storehouseNeedsFourAccessibleContainerStationsAndAWorkBlock() {
        var fixture = room();
        for (int x = -7; x <= -4; x++) fixture.put(new BlockPos(x, 1, -3), feature(BuildingScanner.Feature.CONTAINER, null));
        fixture.put(new BlockPos(-6, 1, -5), feature(BuildingScanner.Feature.WORKSTATION, null));
        var scanner = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.STOREHOUSE,
                BuildingScanner.Range.BASIC, fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.VALID, scanner.report().orElseThrow().status());
        fixture.put(new BlockPos(-6, 1, -5), roomCell(new BlockPos(-6, 1, -5)));
        scanner = new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.STOREHOUSE,
                BuildingScanner.Range.BASIC, fixture::get); finish(scanner);
        assertFalse(scanner.report().orElseThrow().checks().get(BuildingScanner.Check.WORKSTATION));
    }

    @Test void aSmallRoofOnlyOverTheDeskCannotQualifyExposedCouncilFurniture() {
        var fixture=room();
        for(int x=-8;x<=8;x++) for(int z=-8;z<=8;z++) fixture.put(new BlockPos(x,3,z),air());
        fixture.put(ANCHOR.north().above(2),solid());
        var scanner=scanner(fixture,fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.INVALID,scanner.report().orElseThrow().status());
        assertFalse(scanner.report().orElseThrow().checks().get(BuildingScanner.Check.ROOF));
    }
    @Test void councilCannotBorrowBooksAcrossUncoveredGroundIntoAnotherRoofedBuilding() {
        var fixture=room(); fixture.put(new BlockPos(-3,1,-1),air());
        fixture.put(new BlockPos(7,1,-1),feature(BuildingScanner.Feature.BOOKS,null));
        for(int z=-8;z<=8;z++) fixture.put(new BlockPos(5,3,z),air());
        var scanner=scanner(fixture,fixture::get); finish(scanner);
        assertFalse(scanner.report().orElseThrow().checks().get(BuildingScanner.Check.BOOKS));
        assertEquals(BuildingScanner.Status.INVALID,scanner.report().orElseThrow().status());
    }
    @Test void oneUncoveredFurnishingDoesNotForceARigidBlueprint() {
        var fixture=room(); fixture.put(new BlockPos(4,3,0),air());
        var scanner=scanner(fixture,fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.VALID,scanner.report().orElseThrow().status());
    }
    @Test void anOutdoorStorehouseDoesNotRequireAnInnRoof() {
        var fixture=room();
        for(int x=-8;x<=8;x++) for(int z=-8;z<=8;z++) fixture.put(new BlockPos(x,3,z),air());
        for(int x=-7;x<=-4;x++) fixture.put(new BlockPos(x,1,-3),feature(BuildingScanner.Feature.CONTAINER,null));
        fixture.put(new BlockPos(-6,1,-5),feature(BuildingScanner.Feature.WORKSTATION,null));
        var scanner=new BuildingScanner(ANCHOR,Direction.NORTH,BuildingScanner.Role.STOREHOUSE,BuildingScanner.Range.BASIC,fixture::get); finish(scanner);
        assertEquals(BuildingScanner.Status.VALID,scanner.report().orElseThrow().status());
        assertFalse(scanner.report().orElseThrow().checks().containsKey(BuildingScanner.Check.ROOF));
    }
    private static BuildingScanner.Cell air() { return new BuildingScanner.Cell(true,true,false,false,Set.of(),null); }

    private static BuildingScanner scanner(Map<BlockPos, BuildingScanner.Cell> fixture, BuildingScanner.Probe probe) {
        return new BuildingScanner(ANCHOR, Direction.NORTH, BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC, pos -> fixture.containsKey(pos) ? probe.read(pos) : roomCell(pos));
    }
    private static void finish(BuildingScanner scanner) { while (!scanner.done()) scanner.advance(256); }
    private static Map<BlockPos, BuildingScanner.Cell> room() {
        Map<BlockPos, BuildingScanner.Cell> result = new HashMap<>();
        for (int x = -8; x <= 8; x++) for (int y = -3; y <= 5; y++) for (int z = -8; z <= 8; z++) {
            var pos = new BlockPos(x, y, z); result.put(pos, roomCell(pos));
        }
        result.put(ANCHOR, solid());
        result.put(new BlockPos(2, 1, 0), feature(BuildingScanner.Feature.TABLE, null));
        result.put(new BlockPos(2, 1, 2), feature(BuildingScanner.Feature.SEAT, Direction.NORTH));
        result.put(new BlockPos(4, 1, 0), feature(BuildingScanner.Feature.SEAT, Direction.WEST));
        result.put(new BlockPos(-3, 1, -1), feature(BuildingScanner.Feature.BOOKS, null));
        result.put(new BlockPos(-2, 2, -1), feature(BuildingScanner.Feature.LIGHT, null));
        return result;
    }
    private static BuildingScanner.Cell roomCell(BlockPos pos) {
        return pos.getY() == 0 || pos.getY() == 3 ? solid()
                : new BuildingScanner.Cell(true, true, false, false, Set.of(), null);
    }
    private static BuildingScanner.Cell solid() {
        return new BuildingScanner.Cell(true, false, true, true, Set.of(), null);
    }
    private static BuildingScanner.Cell feature(BuildingScanner.Feature feature, Direction facing) {
        return new BuildingScanner.Cell(true, feature == BuildingScanner.Feature.LIGHT, true, true, Set.of(feature), facing);
    }
}
