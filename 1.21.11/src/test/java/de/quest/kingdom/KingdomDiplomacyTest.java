package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import de.quest.village.VillageLifeState.VillageKey;
import org.junit.jupiter.api.Test;

final class KingdomDiplomacyTest {
    private record Fixture(LivingKingdomsState state,UUID a,UUID b,UUID c,UUID ownerA,UUID ownerB) {}
    private static Fixture fixture() {
        var s=new LivingKingdomsState(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); var c=UUID.randomUUID();
        var ownerA=UUID.randomUUID(); var ownerB=UUID.randomUUID();
        s.registerCustom(a,ownerA,VillageKey.overworld(0,0),new BlockPos(0,64,0),"A").orElseThrow();
        s.registerCustom(b,ownerB,VillageKey.overworld(40,0),new BlockPos(40,64,0),"B").orElseThrow();
        s.registerCustom(c,ownerA,VillageKey.overworld(80,0),new BlockPos(80,64,0),"C").orElseThrow();
        return new Fixture(s,a,b,c,ownerA,ownerB);
    }
    @Test void bothMandatesAndCurrentRevisionsAreRequiredAndCounteroffersReplaceConsent() {
        var f=fixture(); var d=f.state.diplomacy();
        assertTrue(d.offer(f.ownerB,f.a,f.b,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).isEmpty());
        var first=d.offer(f.ownerA,f.a,f.b,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        assertTrue(d.accept(f.ownerA,first.id(),0).isEmpty(),"A mandate for A does not sign for B");
        var counter=d.offer(f.ownerB,f.b,f.a,0,0,KingdomDiplomacy.Type.TRADE,0,KingdomDiplomacy.Terms.CAUTIOUS).orElseThrow();
        assertEquals(first.id(),counter.id()); assertEquals(1,counter.revision());
        assertTrue(d.accept(f.ownerA,first.id(),0).isEmpty());
        var signed=d.accept(f.ownerA,counter.id(),1).orElseThrow();
        assertEquals(KingdomDiplomacy.Status.SIGNED,signed.status());
        assertEquals(KingdomDiplomacy.Terms.CAUTIOUS,signed.terms());
        assertTrue(d.accept(f.ownerA,counter.id(),1).isEmpty());
    }
    @Test void pairsPersistIndependentlyAndFriendshipWaitsForTheRealMeeting() {
        var f=fixture(); var d=f.state.diplomacy();
        var ab=d.offer(f.ownerA,f.a,f.b,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        assertEquals(KingdomDiplomacy.Status.PREPARED,d.accept(f.ownerB,ab.id(),0).orElseThrow().status());
        var ac=d.offer(f.ownerA,f.a,f.c,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        assertEquals(KingdomDiplomacy.Status.SIGNED,d.accept(f.ownerA,ac.id(),0).orElseThrow().status(),"One authorized player may represent both villages");
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        assertEquals(2,restored.diplomacy().contracts(f.a).size());
        assertTrue(restored.diplomacy().between(f.b,f.c,KingdomDiplomacy.Type.TRADE).isEmpty());
        assertEquals(2,restored.diplomacy().contracts(f.a).stream().map(KingdomDiplomacy.View::id).distinct().count());
    }
    @Test void staleVillageMandatesAndUnknownSavedSchemasCannotBecomeAgreements() {
        var f=fixture(); var offer=f.state.diplomacy().offer(f.ownerA,f.a,f.b,0,0,KingdomDiplomacy.Type.SUPPLY,-1,KingdomDiplomacy.Terms.NEEDS_FIRST).orElseThrow();
        f.state.rename(f.ownerA,f.a,0,"Renamed");
        assertTrue(f.state.diplomacy().accept(f.ownerB,offer.id(),0).isEmpty());
        var saved=LivingKingdomsState.toNbt(f.state); var future=new CompoundTag(); future.putInt("schema",99); future.putString("future","keep"); saved.put("diplomacy",future);
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.diplomacy().readOnly());
        assertEquals(future,LivingKingdomsState.toNbt(restored).get("diplomacy"));
    }
}
