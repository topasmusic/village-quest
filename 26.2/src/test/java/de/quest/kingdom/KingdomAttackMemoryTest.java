package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import de.quest.village.VillageLifeState.VillageKey;
import org.junit.jupiter.api.Test;

final class KingdomAttackMemoryTest {
    @Test void threeDistinctExplicitRefusalsAffectOnlyOneActorAndVillageAndRealHelpRepairsThem() {
        var s=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        s.registerCustom(a,owner,VillageKey.overworld(0,0),BlockPos.ZERO,"A").orElseThrow();
        s.registerCustom(b,owner,VillageKey.overworld(100,0),new BlockPos(100,0,0),"B").orElseThrow();
        var first=UUID.randomUUID(); assertTrue(s.relations().refusedAttack(owner,a,first));
        assertFalse(s.relations().refusedAttack(owner,a,first)); assertFalse(s.relations().distrusts(owner,a));
        assertTrue(s.relations().refusedAttack(owner,a,UUID.randomUUID())); assertFalse(s.relations().distrusts(owner,a));
        assertTrue(s.relations().refusedAttack(owner,a,UUID.randomUUID())); assertTrue(s.relations().distrusts(owner,a));
        assertFalse(s.relations().distrusts(owner,b)); assertFalse(s.relations().distrusts(UUID.randomUUID(),a));
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); assertFalse(s.relations().readOnly()); assertTrue(s.relations().distrusts(owner,a));
        assertFalse(s.relations().helpedAttack(owner,a,first),"A refused incident cannot later become a new repair reward");
        assertTrue(s.relations().helpedAttack(owner,a,UUID.randomUUID())); assertFalse(s.relations().distrusts(owner,a));
        assertFalse(s.relations().refusedAttack(owner,a,first),"Repair keeps old replay IDs");
    }
    @Test void everyMissingSafetyProofIsNeutral() {
        assertTrue(KingdomAttackPolicy.equipped(16,12,6));
        assertFalse(KingdomAttackPolicy.equipped(15,12,6)); assertFalse(KingdomAttackPolicy.equipped(20,11,6));
        assertFalse(KingdomAttackPolicy.equipped(20,20,5)); assertFalse(KingdomAttackPolicy.equipped(Float.NaN,20,10));
        var good=new KingdomAttackPolicy.Evidence(true,true,true,true,true,true,true,600);
        assertTrue(KingdomAttackPolicy.eligible(good));
        for(int field=0;field<7;field++) {
            boolean[] v={true,true,true,true,true,true,true}; v[field]=false;
            assertFalse(KingdomAttackPolicy.eligible(new KingdomAttackPolicy.Evidence(v[0],v[1],v[2],v[3],v[4],v[5],v[6],600)));
        }
        assertFalse(KingdomAttackPolicy.eligible(new KingdomAttackPolicy.Evidence(true,true,true,true,true,true,true,599)));
        assertFalse(KingdomAttackPolicy.eligible(null));
    }
    @Test void distrustBlocksOnlyNewFriendshipForTheAffectedVillageAndNotManagementOrOtherTreaties() {
        var s=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); var c=UUID.randomUUID();
        s.registerCustom(a,owner,VillageKey.overworld(0,0),BlockPos.ZERO,"A").orElseThrow();
        s.registerCustom(b,owner,VillageKey.overworld(100,0),new BlockPos(100,0,0),"B").orElseThrow();
        s.registerCustom(c,owner,VillageKey.overworld(200,0),new BlockPos(200,0,0),"C").orElseThrow();
        for(int i=0;i<3;i++) s.relations().refusedAttack(owner,a,UUID.randomUUID());
        assertTrue(s.canManageVillage(owner,a),"Distrust cannot steal founder rights or block repairs");
        assertTrue(s.diplomacy().offer(owner,a,b,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).isEmpty());
        assertTrue(s.diplomacy().offer(owner,b,a,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).isEmpty());
        assertTrue(s.diplomacy().offer(owner,a,b,0,0,KingdomDiplomacy.Type.SUPPLY,-1,KingdomDiplomacy.Terms.NEEDS_FIRST).isPresent());
        assertTrue(s.diplomacy().offer(owner,b,c,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).isPresent());
        s.relations().helpedAttack(owner,a,UUID.randomUUID());
        assertTrue(s.diplomacy().offer(owner,a,b,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).isPresent());
    }
    @Test void malformedAttackMemoryIsRetainedAndBlocksFurtherDiplomaticMutation() {
        var s=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=UUID.randomUUID();
        s.registerCustom(a,owner,VillageKey.overworld(0,0),BlockPos.ZERO,"A").orElseThrow(); s.relations().refusedAttack(owner,a,UUID.randomUUID());
        var saved=LivingKingdomsState.toNbt(s); var relations=(net.minecraft.nbt.CompoundTag)saved.get("relations");
        var row=(net.minecraft.nbt.CompoundTag)((net.minecraft.nbt.ListTag)relations.get("attacks")).getFirst(); row.putInt("refusals",-1);
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.relations().readOnly());
        assertEquals(relations,LivingKingdomsState.toNbt(restored).get("relations"));
        assertFalse(restored.relations().refusedAttack(owner,a,UUID.randomUUID()));
    }
    @Test void peacefulRepairRequiresAnExplicitApologyAndNewConsumedMatchingFreightToThatVillage() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var s=new LivingKingdomsState(); var actor=UUID.randomUUID(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); var connection=UUID.randomUUID();
        s.registerCustom(a,actor,VillageKey.overworld(0,0),BlockPos.ZERO,"A").orElseThrow();
        s.registerCustom(b,actor,VillageKey.overworld(100,0),new BlockPos(100,0,0),"B").orElseThrow();
        for(int i=0;i<3;i++) s.relations().refusedAttack(actor,b,UUID.randomUUID());
        s.relations().delivered(a,b,actor,connection,7);
        assertFalse(s.relations().repairAttackFreight(actor,a,b,7,net.minecraft.world.item.Items.BREAD));
        assertTrue(s.relations().apologizeAttack(actor,b,de.quest.village.VillageNeed.GRANARY_PANTRY));
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); assertFalse(s.relations().readOnly());
        assertFalse(s.relations().repairAttackFreight(actor,a,b,7,net.minecraft.world.item.Items.BREAD));
        s.relations().delivered(a,b,actor,connection,8);
        assertFalse(s.relations().repairAttackFreight(actor,a,b,8,net.minecraft.world.item.Items.COAL));
        assertFalse(s.relations().repairAttackFreight(actor,b,a,8,net.minecraft.world.item.Items.BREAD));
        assertTrue(s.relations().repairAttackFreight(actor,a,b,8,net.minecraft.world.item.Items.BREAD));
        assertFalse(s.relations().distrusts(actor,b));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); assertFalse(restored.relations().readOnly());
        assertFalse(restored.relations().distrusts(actor,b));
        assertFalse(restored.relations().repairAttackFreight(actor,a,b,8,net.minecraft.world.item.Items.BREAD));
    }
}
