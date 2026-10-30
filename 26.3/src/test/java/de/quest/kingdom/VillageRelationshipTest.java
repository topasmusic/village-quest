package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import de.quest.reputation.VillageRelationship;
import de.quest.shrine.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class VillageRelationshipTest {
    static String key(net.minecraft.network.chat.Component text) { return ((TranslatableContents)text.getContents()).getKey(); }
    @Test void repeatedProjectionKeepsEarnedBondNegativeStandingAndAllSavedStateUnchanged() {
        var state=new LivingKingdomsState(); var actor=UUID.randomUUID();var village=UUID.randomUUID();
        var k=VillageKey.overworld(32,64);var data=new PlayerQuestData();
        state.registerCustom(village,actor,k,new BlockPos(32,64,64),"A").orElseThrow();
        VillageBondService.ensureCanonicalVillageRecord(data,village,k.dimension(),32,64,VillageBondType.GRANARY);
        data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),8);
        data.socialReputation().setLocalTrust(k,-70);
        var ints=Map.copyOf(data.getTradeRouteIntState());var strings=Map.copyOf(data.getTradeRouteStringState());
        var social=data.socialReputation().toNbt();var kingdoms=LivingKingdomsState.toNbt(state);
        for(int i=0;i<50;i++) {
            var hints=VillageRelationship.project(data,0,k,actor,state,true);
            assertEquals("screen.village-quest.relationship.bond_max",key(hints.nextBond()));
            assertTrue(hints.enabled());assertTrue(hints.available());assertEquals(1,hints.conflict());
            assertEquals(VillageBondLevel.ALLIED,VillageBondProgression.snapshot(data,0).level());
        }
        assertEquals(-70,data.socialReputation().localTrust(k)); assertEquals(social,data.socialReputation().toNbt());
        assertEquals(ints,data.getTradeRouteIntState());assertEquals(strings,data.getTradeRouteStringState());
        assertEquals(kingdoms,LivingKingdomsState.toNbt(state));
    }
    @Test void exactDimensionAnchorCanonicalVillageAndActorKeepConflictSeparateFromNeutralTrust() {
        var state=new LivingKingdomsState();var actor=UUID.randomUUID();var village=UUID.randomUUID();
        var k=VillageKey.overworld(32,64);var nether=new VillageKey("minecraft:the_nether",32,64);
        var second=UUID.randomUUID();var data=new PlayerQuestData();
        state.registerCustom(village,actor,k,new BlockPos(32,64,64),"Same name").orElseThrow();
        state.registerCustom(second,actor,nether,new BlockPos(32,64,64),"Same name").orElseThrow();
        VillageBondService.ensureCanonicalVillageRecord(data,village,k.dimension(),32,64,VillageBondType.GRANARY);
        VillageBondService.ensureCanonicalVillageRecord(data,second,nether.dimension(),32,64,VillageBondType.GRANARY);
        for(int i=0;i<3;i++) state.relations().refusedAttack(actor,village,UUID.randomUUID());
        assertEquals(0,data.socialReputation().localTrust(k));
        assertEquals(2,VillageRelationship.project(data,0,k,actor,state,true).conflict());
        assertEquals(1,VillageRelationship.project(data,1,nether,actor,state,true).conflict());
        assertEquals(1,VillageRelationship.project(data,0,k,UUID.randomUUID(),state,true).conflict());
        assertEquals(1,VillageRelationship.exactIndex(data,nether));
        assertEquals(-1,VillageRelationship.exactIndex(data,VillageKey.overworld(33,64)),"No proximity fallback");
        data.setTradeRouteString(VillageBondService.villageKey(0,"canonical_id"),second.toString());
        assertEquals(0,VillageRelationship.project(data,0,k,actor,state,true).conflict(),"Mismatched identity is unavailable, not clear");
    }
    @Test void disabledSocialDoesNotHideIndependentConflictAndApologyExposesActualNeed() {
        var state=new LivingKingdomsState();var actor=UUID.randomUUID();var village=UUID.randomUUID();
        var k=VillageKey.overworld(0,0);var data=new PlayerQuestData();
        state.registerCustom(village,actor,k,BlockPos.ZERO,"A").orElseThrow();
        VillageBondService.ensureCanonicalVillageRecord(data,village,k.dimension(),0,0,VillageBondType.GRANARY);
        for(int i=0;i<3;i++) state.relations().refusedAttack(actor,village,UUID.randomUUID());
        var hints=VillageRelationship.project(data,0,k,actor,state,false);
        assertFalse(hints.enabled());assertEquals(2,hints.conflict());
        assertEquals("screen.village-quest.relationship.conflict_pending",key(hints.repair()));
        state.relations().apologizeAttack(actor,village,de.quest.village.VillageNeed.GRANARY_PANTRY);
        hints=VillageRelationship.project(data,0,k,actor,state,false);
        assertEquals("screen.village-quest.relationship.conflict_repair",key(hints.repair()));
        var args=((TranslatableContents)hints.repair().getContents()).getArgs();
        assertEquals("text.village-quest.village_network.need.granary_pantry",key((net.minecraft.network.chat.Component)args[0]));
    }
    @Test void newBondEvidenceChangesViewWithoutSocialRevisionAndHistoricalMissingVillageKeepsBond() {
        var state=new LivingKingdomsState();var actor=UUID.randomUUID();var village=UUID.randomUUID();var data=new PlayerQuestData();
        var k=VillageKey.overworld(32,64);
        VillageBondService.ensureCanonicalVillageRecord(data,village,k.dimension(),32,64,VillageBondType.GRANARY);
        var revision=data.socialReputation().revision();
        var first=VillageRelationship.project(data,0,k,actor,state,true);
        data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),1);
        var second=VillageRelationship.project(data,0,k,actor,state,true);
        assertNotEquals(first,second);assertEquals(revision,data.socialReputation().revision());assertEquals(0,second.conflict());
        assertEquals("screen.village-quest.relationship.bond_trade",key(second.nextBond()));
        assertEquals(1,((TranslatableContents)second.nextBond().getContents()).getArgs()[0]);
        assertEquals(4,((TranslatableContents)second.nextBond().getContents()).getArgs()[1]);
        data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),8);
        assertEquals("screen.village-quest.relationship.bond_max",key(VillageRelationship.project(data,0,k,actor,state,true).nextBond()));
    }
}
