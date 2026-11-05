package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BuildingVillageChoicesTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void nativeStructureContextCanSelectAHighLargeGeneratedVillageWithoutRewritingItsProvisionalArrival() {
        var state=new LivingKingdomsState(); var generated=state.registerGenerated(VillageKey.overworld(0,0),new BlockPos(0,64,0),"High town").orElseThrow();
        BlockPos desk=new BlockPos(160,200,0);
        var options=BuildingVillageChoices.offer(state,"minecraft:overworld",desk,generated.id());
        assertEquals(generated.id(),options.getFirst().id());
        var policy=new BuildingSessionPolicy(UUID.randomUUID(),"minecraft:overworld",desk,0);
        policy.offer(java.util.Map.of(generated.id(),generated.revision()));
        assertTrue(policy.select(options.getFirst().id()));
        assertEquals(new BlockPos(0,64,0),state.village(generated.id()).orElseThrow().arrival());
    }
    @Test void trustedContextCannotBypassCustomHeightOrDimensionAndChoicesRemainBounded() {
        var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID();
        var custom=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Lower town").orElseThrow();
        assertTrue(BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(0,200,0),custom.id()).isEmpty());
        var foreign=state.registerGenerated(new VillageKey("minecraft:the_nether",0,0),new BlockPos(0,64,0),"Foreign").orElseThrow();
        assertFalse(BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(0,64,0),foreign.id()).stream().anyMatch(v->v.id().equals(foreign.id())));
        for(int x=1;x<=12;x++) {
            var town=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(x,0),new BlockPos(x,64,0),"Town "+x).orElseThrow();
            state.confirmZone(owner,town.id(),0,new LivingKingdomsState.Zone(x-8,x+8,60,68,-8,8));
        }
        var large=state.registerGenerated(VillageKey.overworld(300,0),new BlockPos(300,64,0),"Large").orElseThrow();
        var options=BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(0,64,0),large.id());
        assertEquals(8,options.size()); assertEquals(large.id(),options.getFirst().id()); assertEquals(8,options.stream().map(v->v.id()).distinct().count());
    }
    @Test void proximityAloneNeverAssignsADisconnectedVillage() {
        var state=new LivingKingdomsState();
        var town=state.registerCustom(UUID.randomUUID(),UUID.randomUUID(),VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        assertTrue(BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(30,64,0),null).isEmpty());
    }
    @Test void connectedBuildingAreaFindsVillageBeforeAnchorCenterEntersItsZone() {
        var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID();
        var town=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        state.confirmZone(owner,town.id(),0,new LivingKingdomsState.Zone(-8,8,60,68,-8,8));
        assertEquals(town.id(),BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(18,64,0),null).getFirst().id());
        assertTrue(BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(19,64,0),null).isEmpty());
    }
    @Test void existingAnchorKeepsVillageEvenWhenOtherVillageAreasOverlapAfterReload() {
        var state=new LivingKingdomsState(); UUID owner=UUID.randomUUID();
        var home=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        var other=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(1,0),new BlockPos(1,64,0),"Other").orElseThrow();
        for(var town:java.util.List.of(home,other)) state.confirmZone(owner,town.id(),0,new LivingKingdomsState.Zone(-8,8,60,68,-8,8));
        var report=new BuildingScanner.Report(BuildingScanner.Role.STOREHOUSE,BuildingScanner.Status.VALID,java.util.Map.of(),java.util.Map.of(),java.util.Set.of());
        assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,home.id(),1,UUID.randomUUID(),-1,new BlockPos(0,64,0),
                net.minecraft.core.Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
        var loaded=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(java.util.List.of(home.id()),BuildingVillageChoices.offer(loaded,"minecraft:overworld",new BlockPos(0,64,0),null,
                BuildingScanner.Role.STOREHOUSE,BuildingScanner.Range.BASIC).stream().map(LivingKingdomsState.VillageView::id).toList());
        assertEquals(2,BuildingVillageChoices.offer(loaded,"minecraft:overworld",new BlockPos(2,64,0),null).size());
    }
    @Test void actualRangeDeterminesConnectionAndNoOtherDimensionOrHeightIsGuessed() {
        var state=new LivingKingdomsState();UUID owner=UUID.randomUUID();
        var home=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        state.confirmZone(owner,home.id(),0,new LivingKingdomsState.Zone(-8,8,60,68,-8,8));
        assertTrue(BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(20,64,0),null).isEmpty());
        assertEquals(home.id(),BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(20,64,0),null,
                BuildingScanner.Role.INN,BuildingScanner.Range.EXTENDED).getFirst().id());
        assertTrue(BuildingVillageChoices.offer(state,"minecraft:the_nether",new BlockPos(0,64,0),null).isEmpty());
        assertTrue(BuildingVillageChoices.offer(state,"minecraft:overworld",new BlockPos(0,100,0),null).isEmpty());
    }
}
