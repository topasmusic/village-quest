package de.quest.ambient;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure decisions only: no server, world, entity ticks, pathfinder or Ingame simulation. */
final class AmbientAdventurerPolicyTest {
    private AmbientAdventurerPolicy hunter() { return new AmbientAdventurerPolicy(true,9600); }
    @Test void repeatedNavigationFailuresAlwaysRetryAndDoNotInventDangerOrSpendCombatBudget() {
        var p=hunter();
        for(int i=0;i<80;i++) {
            int delay=p.navigationFailed();
            assertTrue(delay>=60 && delay<=200);
            assertTrue(p.travelLength(i)>=1 && p.travelLength(i)<=3);
            assertFalse(p.fleeing()); assertEquals(0,p.mobCommitments()); assertEquals(0,p.strikes());
        }
        p.navigationProgress(); assertTrue(p.canHunt(0));
        for(int roll=0;roll<6;roll++) assertEquals(6+roll,p.travelLength(roll));
    }
    @Test void blockedRetreatTriesBothSidesWithoutTurningTowardTheThreat() {
        var p=hunter(); var headings=new java.util.HashSet<Double>();
        for(int i=0;i<5;i++) { p.navigationFailed(); double angle=p.retreatHeading(0); headings.add(angle); assertTrue(Math.abs(angle)<=Math.PI/2); }
        assertEquals(5,headings.size());
    }
    @Test void roundedNormalWalksAlwaysFitTheTwelveBlockNavigationRadius() {
        var p=hunter();
        for(int roll=0;roll<6;roll++) for(int degrees=0;degrees<360;degrees++) {
            double angle=Math.toRadians(degrees); int length=p.travelLength(roll);
            long x=Math.round(Math.cos(angle)*length),z=Math.round(Math.sin(angle)*length);
            assertTrue(x*x+z*z<=144);
        }
    }
    @Test void genuineDamageIncludesAbsorptionButNotBlockedOrRejectedHits() {
        assertFalse(AmbientAdventurerPolicy.acceptedDamage(24,4,24,4,true));
        assertFalse(AmbientAdventurerPolicy.acceptedDamage(24,4,20,4,false));
        assertTrue(AmbientAdventurerPolicy.acceptedDamage(24,4,24,2,true));
        assertTrue(AmbientAdventurerPolicy.acceptedDamage(24,0,23,0,true));
    }
    @Test void secondHitRequiresSamePlayerWithinTenSecondsAndNeverTargetsBystander() {
        var p=hunter(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        assertEquals(AmbientAdventurerPolicy.Assault.WARN,p.assaulted(a,0,true,24));
        assertEquals(AmbientAdventurerPolicy.Assault.WARN,p.assaulted(b,20,true,24));
        assertNull(p.defending());
        assertEquals(AmbientAdventurerPolicy.Assault.DEFEND,p.assaulted(a,200,true,24));
        assertEquals(a,p.defending()); assertTrue(p.refuses(a));
        assertFalse(p.counter(b,201,true,true,true)); assertEquals(0,p.counters());
    }
    @Test void oldHitIsRefusalAndFlightWithoutCounterattack() {
        var p=hunter(); var a=UUID.randomUUID(); p.assaulted(a,0,true,24);
        assertEquals(AmbientAdventurerPolicy.Assault.RETREAT,p.assaulted(a,201,true,24));
        assertNull(p.defending()); assertTrue(p.refuses(a));
    }
    @Test void eightSecondDefenseNeverExtendsFromFurtherHits() {
        var p=hunter(); var a=UUID.randomUUID(); p.assaulted(a,0,true,24); p.assaulted(a,1,true,24);
        p.assaulted(a,150,true,24);
        assertTrue(p.defenseActive(160)); assertFalse(p.defenseActive(161));
        assertFalse(p.counter(a,161,true,true,true));
    }
    @Test void playerCountersAreStationaryRateLimitedAndLifetimeLimitedAcrossAggressors() {
        var p=hunter(); var a=UUID.randomUUID(); p.assaulted(a,0,true,24); p.assaulted(a,1,true,24);
        assertFalse(p.counter(a,1,false,true,true)); assertEquals(0,p.counters());
        assertTrue(p.counter(a,1,true,true,true)); assertFalse(p.counter(a,20,true,true,true));
        assertTrue(p.counter(a,21,true,true,true));
        p.endDefense(22); var b=UUID.randomUUID(); p.assaulted(b,23,true,24); p.assaulted(b,24,true,24);
        assertTrue(p.counter(b,41,true,true,true)); assertFalse(p.defenseActive(42));
        assertEquals(AmbientAdventurerPolicy.Assault.RETREAT,p.assaulted(b,42,true,24)); assertEquals(3,p.counters());
    }
    @Test void pvpDisabledLowHealthWanderersAndUnattributedDamageNeverDefend() {
        for(var p:new AmbientAdventurerPolicy[]{hunter(),new AmbientAdventurerPolicy(false,9600)}) {
            var a=UUID.randomUUID(); p.assaulted(a,0,false,24); assertNotEquals(AmbientAdventurerPolicy.Assault.DEFEND,p.assaulted(a,1,false,24));
            assertEquals(AmbientAdventurerPolicy.Assault.RETREAT,p.assaulted(null,2,true,24)); assertNull(p.defending());
        }
        var p=hunter(); var a=UUID.randomUUID(); p.assaulted(a,0,true,8); assertNotEquals(AmbientAdventurerPolicy.Assault.DEFEND,p.assaulted(a,1,true,8));
    }
    @Test void creativeOrPvpChangeStopsDefenseWithoutSpendingAnAttack() {
        for(boolean pvp:new boolean[]{true,false}) {
            var p=hunter(); var a=UUID.randomUUID(); p.assaulted(a,0,true,24); p.assaulted(a,1,true,24);
            assertFalse(p.counter(a,2,true,!pvp,pvp)); assertEquals(0,p.counters()); assertNull(p.defending());
        }
    }
    @Test void atMostThreeDistinctTargetsIncludingSelfDefenseAndAbortedCommitments() {
        var p=hunter();
        for(int i=0;i<3;i++) { assertTrue(p.commit(UUID.randomUUID(),i*601L,i>0)); p.abort(i*601L+1); }
        assertFalse(p.commit(UUID.randomUUID(),2000,true)); assertEquals(3,p.mobCommitments()); assertTrue(p.fleeing());
    }
    @Test void failedOrBlockedExecutedStrikesStillConsumeFifteenAttemptBudget() {
        var p=hunter(); var target=UUID.randomUUID(); assertTrue(p.commit(target,0,false));
        for(int i=0;i<15;i++) { assertTrue(p.mobStrike(target,i*20)); assertFalse(p.mobStrike(target,i*20+1)); }
        assertFalse(p.mobStrike(target,300)); assertEquals(15,p.strikes());
        assertFalse(p.continueFight(300,0,true,true,24,1)); assertTrue(p.fleeing());
        assertFalse(p.commit(UUID.randomUUID(),1000,true));
    }
    @Test void targetSwitchCannotResetStrikesOrReacquireAbortedMobVoluntarily() {
        var p=hunter(); var first=UUID.randomUUID(); p.commit(first,0,false); p.mobStrike(first,0); p.abort(1);
        assertFalse(p.canHunt(600)); assertTrue(p.canHunt(601)); assertFalse(p.commit(first,601,false));
        var second=UUID.randomUUID(); assertTrue(p.commit(second,601,false)); assertEquals(1,p.strikes()); assertEquals(2,p.mobCommitments());
    }
    @Test void selfDefenseDoesNotRefreshOriginalTwentySecondTargetDeadline() {
        var p=hunter(); var a=UUID.randomUUID(); p.commit(a,0,false); p.abort(100);
        assertTrue(p.commit(a,200,true)); assertTrue(p.continueFight(399,0,true,true,24,1));
        assertFalse(p.continueFight(400,0,true,true,24,1)); assertFalse(p.commit(a,401,true));
    }
    @Test void leashLosCrowdInvalidTargetsAndLowHealthAbortWithoutExtendingLimits() {
        for(int failure=0;failure<5;failure++) {
            var p=hunter(); p.commit(UUID.randomUUID(),0,false);
            if(failure==0) assertFalse(p.continueFight(1,145,true,true,24,1));
            if(failure==1) { assertTrue(p.continueFight(1,0,false,true,24,1)); assertFalse(p.continueFight(61,0,false,true,24,1)); }
            if(failure==2) assertFalse(p.continueFight(1,0,true,true,24,3));
            if(failure==3) assertFalse(p.continueFight(1,0,true,false,24,1));
            if(failure==4) assertFalse(p.continueFight(1,0,true,true,8,1));
            assertNull(p.fighting()); assertEquals(1,p.mobCommitments()); assertFalse(p.canHunt(2));
        }
    }
    @Test void finiteLifeAndAbsenceCannotBeExtendedByConversationOrNames() {
        var p=new AmbientAdventurerPolicy(false,Integer.MAX_VALUE);
        for(int i=1;i<9600;i++) assertFalse(p.tick(true)); assertTrue(p.tick(true));
        var absent=hunter(); for(int i=1;i<600;i++) assertFalse(absent.tick(false)); assertTrue(absent.tick(false));
        var rejoined=hunter(); for(int i=0;i<599;i++) rejoined.tick(false); assertFalse(rejoined.tick(true));
    }
    @Test void onlyAcceptedNightWindowAllowsSurfaceHuntRolls() {
        assertFalse(AmbientAdventurerPolicy.night(12999)); assertTrue(AmbientAdventurerPolicy.night(13000));
        assertTrue(AmbientAdventurerPolicy.night(22999)); assertFalse(AmbientAdventurerPolicy.night(23000));
        assertTrue(AmbientAdventurerPolicy.night(37000));
    }
    @Test void dialogueUsesObservedConditionsWithRefusalAndDangerPrecedence() {
        assertEquals("refusal",AmbientAdventurerDialogue.context(true,true,true,true,true,true));
        assertEquals("danger",AmbientAdventurerDialogue.context(false,true,true,true,true,true));
        assertEquals("cave",AmbientAdventurerDialogue.context(false,false,true,true,true,true));
        assertEquals("rain",AmbientAdventurerDialogue.context(false,false,false,true,true,false));
        assertEquals("dawn",AmbientAdventurerDialogue.context(false,false,false,false,false,true));
        assertEquals("hunt",AmbientAdventurerDialogue.context(false,false,false,false,true,true));
        assertEquals("day",AmbientAdventurerDialogue.context(false,false,false,false,false,false));
    }
}
