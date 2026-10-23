package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageCondition;
import de.quest.village.VillageNeed;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

final class KingdomNegotiationTest {
    @Test void everyRequestedNeedHasARealFreightPathAndPublishedCargoIdsRemainStable() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var types=de.quest.caravan.TradeContractType.values();
        assertEquals(java.util.List.of("BAKERS_DELIVERY","ROADWORKS","IRON_CONSIGNMENT","ARCHIVE_POST","WINTER_FODDER",
                "APIARY_CRATES","WATCH_SUPPLIES","FESTIVAL_BREAD"),java.util.Arrays.stream(types).limit(8).map(Enum::name).toList());
        for(var need:VillageNeed.values()) assertTrue(java.util.Arrays.stream(types).anyMatch(t -> need.matches(t.item())),need.name());
    }
    private record Fixture(LivingKingdomsState state,UUID a,UUID b,UUID owner) {}
    private static Fixture fixture() {
        var state=new LivingKingdomsState(); var owner=UUID.randomUUID(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        state.registerCustom(a,owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"A").orElseThrow();
        state.registerCustom(b,owner,VillageKey.overworld(100,0),new BlockPos(100,64,0),"B").orElseThrow();
        return new Fixture(state,a,b,owner);
    }
    @Test void savedCharacterAndActualUrgencyDetermineCounterofferWithoutRandomness() {
        for(int p=0;p<4;p++) {
            var answer=KingdomNegotiation.answer(p,KingdomDiplomacy.Type.TRADE,KingdomDiplomacy.Terms.BALANCED,
                    VillageNeed.GRANARY_PANTRY,VillageCondition.STABLE);
            assertEquals(answer,KingdomNegotiation.answer(p,KingdomDiplomacy.Type.TRADE,KingdomDiplomacy.Terms.BALANCED,
                    VillageNeed.GRANARY_PANTRY,VillageCondition.STABLE));
            assertEquals(p==1?KingdomDiplomacy.Terms.NEEDS_FIRST:p==2?KingdomDiplomacy.Terms.CAUTIOUS:KingdomDiplomacy.Terms.BALANCED,answer.preferred());
            var urgent=KingdomNegotiation.answer(p,KingdomDiplomacy.Type.SUPPLY,KingdomDiplomacy.Terms.BALANCED,
                    VillageNeed.GRANARY_PANTRY,VillageCondition.CRISIS);
            assertEquals(KingdomNegotiation.Reaction.COUNTER,urgent.reaction());
            assertEquals(KingdomDiplomacy.Terms.NEEDS_FIRST,urgent.preferred());
        }
        assertEquals(KingdomNegotiation.Reaction.UNKNOWN,KingdomNegotiation.answer(0,KingdomDiplomacy.Type.SUPPLY,
                KingdomDiplomacy.Terms.BALANCED,null,null).reaction());
    }
    @Test void counterCannotBeSignedAndNewTermsDiscardThePreviousTaskConsent() {
        var f=fixture(); var d=f.state.diplomacy();
        var offer=d.offer(f.owner,f.a,f.b,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        assertTrue(d.respond(offer,KingdomNegotiation.answer(2,offer.type(),offer.terms(),null,null)));
        assertTrue(d.accept(f.owner,offer.id(),offer.revision()).isEmpty());
        var next=d.offer(f.owner,f.a,f.b,0,0,offer.type(),offer.revision(),KingdomDiplomacy.Terms.CAUTIOUS).orElseThrow();
        assertTrue(d.respond(next,KingdomNegotiation.answer(2,next.type(),next.terms(),null,null)));
        assertTrue(d.accept(f.owner,next.id(),offer.revision()).isEmpty());
        assertEquals(KingdomDiplomacy.Status.SIGNED,d.accept(f.owner,next.id(),next.revision()).orElseThrow().status());
    }
    @Test void realMatchingFreightMustReachRequestedVillageAndSurvivesRestart() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var f=fixture(); var d=f.state.diplomacy();
        var offer=d.offer(f.owner,f.a,f.b,0,0,KingdomDiplomacy.Type.SUPPLY,-1,KingdomDiplomacy.Terms.NEEDS_FIRST).orElseThrow();
        d.respond(offer,KingdomNegotiation.answer(1,offer.type(),offer.terms(),VillageNeed.GRANARY_PANTRY,VillageCondition.STRAINED));
        assertTrue(d.accept(f.owner,offer.id(),offer.revision()).isEmpty());
        d.delivered(f.b,f.a,net.minecraft.world.item.Items.BREAD);
        d.delivered(f.a,f.b,net.minecraft.world.item.Items.IRON_INGOT);
        assertEquals(KingdomNegotiation.Reaction.DELIVERY,d.answer(offer).orElseThrow().reaction());
        var saved=LivingKingdomsState.toNbt(f.state); var restored=LivingKingdomsState.fromNbt(saved); d=restored.diplomacy();
        assertEquals(KingdomNegotiation.Reaction.DELIVERY,d.answer(d.between(f.a,f.b,offer.type()).orElseThrow()).orElseThrow().reaction());
        d.delivered(f.a,f.b,net.minecraft.world.item.Items.BREAD);
        var ready=d.between(f.a,f.b,offer.type()).orElseThrow(); assertEquals(offer.revision()+1,ready.revision());
        assertTrue(d.accept(f.owner,offer.id(),offer.revision()).isEmpty());
        assertEquals(KingdomNegotiation.Reaction.READY,d.answer(ready).orElseThrow().reaction());
        assertTrue(d.accept(f.owner,ready.id(),ready.revision()).isPresent());
    }
    @Test void freightMemoryIsSharedNontransitiveAndDeduplicatedAcrossRestartAndRouteReplacement() {
        var f=fixture(); var r=f.state.relations(); var connection=UUID.randomUUID();
        assertTrue(r.delivered(f.a,f.b,f.owner,connection,7));
        assertFalse(r.delivered(f.b,f.a,f.owner,connection,7));
        assertFalse(r.delivered(f.a,f.b,f.owner,UUID.randomUUID(),6));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state)); r=restored.relations();
        assertEquals(1,r.between(f.a,f.b).orElseThrow().deliveries());
        assertFalse(r.delivered(f.a,f.b,f.owner,connection,7));
        assertTrue(r.delivered(f.a,f.b,f.owner,connection,8));
        assertEquals(2,r.between(f.b,f.a).orElseThrow().deliveries());
        assertTrue(r.between(f.a,UUID.randomUUID()).isEmpty());
        assertFalse(r.delivered(f.a,f.a,f.owner,connection,9));
        assertFalse(r.delivered(f.a,f.b,f.owner,new UUID(0,0),9));
    }
    @Test void futureAndMalformedPairMemoryIsPreservedWithoutGrantingEvidence() {
        var f=fixture(); assertTrue(f.state.relations().delivered(f.a,f.b,f.owner,UUID.randomUUID(),1));
        var saved=LivingKingdomsState.toNbt(f.state); var tag=(CompoundTag)saved.get("relations");
        tag.putString("futureField","preserve");
        assertEquals("preserve",((CompoundTag)LivingKingdomsState.toNbt(LivingKingdomsState.fromNbt(saved)).get("relations")).getStringOr("futureField",""));
        tag.putInt("schema",99);
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.relations().readOnly());
        assertEquals(tag,LivingKingdomsState.toNbt(restored).get("relations"));
        tag.putInt("schema",1); ((CompoundTag)((ListTag)tag.get("pairs")).get(0)).putString("revision","bad");
        restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.relations().readOnly());
        assertEquals(tag,LivingKingdomsState.toNbt(restored).get("relations"));
    }
}
