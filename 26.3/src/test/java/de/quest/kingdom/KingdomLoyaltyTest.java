package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageNeed;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class KingdomLoyaltyTest {
    private record Fixture(LivingKingdomsState s,UUID actor,UUID home,UUID other) {}
    private Fixture fixture() {
        var s=new LivingKingdomsState(); var actor=UUID.randomUUID(); var home=UUID.randomUUID(); var other=UUID.randomUUID();
        s.registerCustom(home,actor,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        s.registerCustom(other,actor,VillageKey.overworld(100,0),new BlockPos(100,64,0),"Other").orElseThrow();
        for(var type:KingdomDiplomacy.Type.values()) {
            var d=s.diplomacy().offer(actor,home,other,0,0,type,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
            s.diplomacy().accept(actor,d.id(),d.revision()).orElseThrow();
        }
        // A real meeting's persisted proof, without starting a world.
        var tag=LivingKingdomsState.toNbt(s);
        var rows=(net.minecraft.nbt.ListTag)((net.minecraft.nbt.CompoundTag)tag.get("diplomacy")).get("contracts");
        for(var row:rows) if(row instanceof net.minecraft.nbt.CompoundTag r && r.getStringOr("type","").equals("FRIENDSHIP")) {
            r.putString("status","SIGNED"); r.putBoolean("met",true);
        }
        return new Fixture(LivingKingdomsState.fromNbt(tag),actor,home,other);
    }
    @Test void loyaltyRequiresAnActualAllianceAndBothVoicesBeforeTheIrreversibleChoice() {
        var f=fixture(); var c=f.s.crises();
        var v=c.startLoyalty(f.actor,f.home,f.other,VillageNeed.GRANARY_PANTRY,4,2).orElseThrow();
        assertEquals(KingdomCrisis.Kind.LOYALTY,v.kind());
        assertFalse(c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.KEEP_HOME));
        c.hear(f.actor,f.home); c.hear(f.actor,f.other); v=c.current(f.actor).orElseThrow();
        assertTrue(c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.KEEP_HOME));
        assertEquals(KingdomDiplomacy.Status.PAUSED,f.s.diplomacy().between(f.home,f.other,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow().status());
        assertEquals(KingdomDiplomacy.Status.PAUSED,f.s.diplomacy().between(f.home,f.other,KingdomDiplomacy.Type.SUPPLY).orElseThrow().status());
        assertEquals(KingdomDiplomacy.Status.SIGNED,f.s.diplomacy().between(f.home,f.other,KingdomDiplomacy.Type.TRADE).orElseThrow().status());
        assertEquals("loyalty",f.s.relations().between(f.home,f.other).orElseThrow().cause());
        assertFalse(c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.KEEP_HOME));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.s));
        assertFalse(restored.crises().readOnly()); assertFalse(restored.relations().readOnly());
        assertEquals(c.current(f.actor),restored.crises().current(f.actor));
    }
    @Test void timeAndDeferralNeverBreakTheAlliance() {
        var f=fixture(); var c=f.s.crises(); var v=c.startLoyalty(f.actor,f.home,f.other,VillageNeed.GRANARY_PANTRY,4,2).orElseThrow();
        assertTrue(c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.DEFER));
        assertEquals(KingdomDiplomacy.Status.SIGNED,f.s.diplomacy().between(f.home,f.other,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow().status());
    }
    @Test void repairReopensBothPromisesForFreshNegotiationRatherThanSilentlyRestoringTheAlliance() {
        var f=fixture(); var c=f.s.crises(); var v=c.startLoyalty(f.actor,f.home,f.other,VillageNeed.GRANARY_PANTRY,4,2).orElseThrow();
        c.hear(f.actor,f.home); c.hear(f.actor,f.other); v=c.current(f.actor).orElseThrow(); c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.KEEP_HOME);
        c.hear(f.actor,f.other); v=c.current(f.actor).orElseThrow(); c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.APOLOGIZE);
        c.hear(f.actor,f.home); c.hear(f.actor,f.other); assertTrue(c.ready(f.actor,true));
        v=c.current(f.actor).orElseThrow(); assertTrue(c.choose(f.actor,v.id(),v.revision(),KingdomCrisis.Choice.REOPEN));
        assertEquals(KingdomDiplomacy.Status.PROPOSED,f.s.diplomacy().between(f.home,f.other,KingdomDiplomacy.Type.FRIENDSHIP).orElseThrow().status());
        assertEquals("reconciliation",f.s.relations().between(f.home,f.other).orElseThrow().cause());
    }
}
