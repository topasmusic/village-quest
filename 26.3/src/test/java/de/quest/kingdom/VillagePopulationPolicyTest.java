package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillagePopulationPolicyTest {
    private static final UUID OWNER=UUID.randomUUID(), HOME=UUID.randomUUID(), NEIGHBOUR=UUID.randomUUID();
    private static final String DIM="minecraft:overworld";
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void aCertifiedResidentCannotBeCountedByTwoNeighbouringVillagesEvenInOverlappingZones() {
        var state=state(); UUID resident=UUID.randomUUID();
        var people=List.of(new VillageFoundingRegistry.Resident(resident,new BlockPos(2,64,2),new BlockPos(3,64,2)),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),new BlockPos(4,64,2),null),
                new VillageFoundingRegistry.Resident(UUID.randomUUID(),new BlockPos(6,64,2),null));
        assertEquals(VillageFoundingRegistry.Result.APPLIED,state.founding().claim(OWNER,HOME,1,people,true,true));
        BlockPos shared=new BlockPos(8,64,0);
        assertEquals(VillagePopulationPolicy.Membership.INCLUDED,VillagePopulationPolicy.classify(state,HOME,DIM,resident,shared));
        assertEquals(VillagePopulationPolicy.Membership.OTHER_VILLAGE,VillagePopulationPolicy.classify(state,NEIGHBOUR,DIM,resident,shared));
        assertEquals(VillagePopulationPolicy.Membership.INCLUDED,VillagePopulationPolicy.classify(
                LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state)),HOME,DIM,resident,shared));
    }
    @Test void anUnclaimedResidentInAnAmbiguousAreaIsUnknownRatherThanDoubleCreditOrProofOfEmpty() {
        var state=state(); UUID resident=UUID.randomUUID();
        assertEquals(VillagePopulationPolicy.Membership.UNKNOWN,VillagePopulationPolicy.classify(state,HOME,DIM,resident,new BlockPos(8,64,0)));
        assertEquals(VillagePopulationPolicy.Membership.INCLUDED,VillagePopulationPolicy.classify(state,HOME,DIM,resident,new BlockPos(1,64,0)));
        assertEquals(VillagePopulationPolicy.Membership.OTHER_VILLAGE,VillagePopulationPolicy.classify(state,NEIGHBOUR,DIM,resident,new BlockPos(1,64,0)));
    }
    @Test void opaqueFutureFoundingProofIsNeutralAndLegacyGeneratedAreasNeedNoNewCertificate() {
        var state=state(); var saved=LivingKingdomsState.toNbt(state); var founding=saved.getCompoundOrEmpty("founding");
        founding.putInt("schema",99); saved.put("founding",founding);
        assertEquals(VillagePopulationPolicy.Membership.UNKNOWN,VillagePopulationPolicy.classify(LivingKingdomsState.fromNbt(saved),HOME,DIM,UUID.randomUUID(),new BlockPos(1,64,0)));
        assertEquals(VillagePopulationPolicy.Membership.INCLUDED,VillagePopulationPolicy.classify(new LivingKingdomsState(),null,DIM,UUID.randomUUID(),new BlockPos(40,64,0)));
        assertEquals(VillagePopulationPolicy.Membership.INCLUDED,VillagePopulationPolicy.classify(null,null,DIM,UUID.randomUUID(),BlockPos.ZERO));
    }
    private static LivingKingdomsState state() {
        var state=new LivingKingdomsState();
        state.registerCustom(HOME,OWNER,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        state.confirmZone(OWNER,HOME,0,new LivingKingdomsState.Zone(-8,8,60,68,-8,8));
        state.registerCustom(NEIGHBOUR,OWNER,VillageKey.overworld(16,0),new BlockPos(16,64,0),"Other").orElseThrow();
        state.confirmZone(OWNER,NEIGHBOUR,0,new LivingKingdomsState.Zone(8,24,60,68,-8,8)); return state;
    }
}
