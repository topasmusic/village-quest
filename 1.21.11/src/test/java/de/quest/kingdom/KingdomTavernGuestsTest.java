package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

/** Pure saved-state policy tests. Does not start a level, entity tick or game simulation. */
final class KingdomTavernGuestsTest {
    private static final UUID HOME=UUID.randomUUID(),OWNER=UUID.randomUUID();
    private static final BlockPos POS=new BlockPos(0,64,0);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private LivingKingdomsState state() {
        var s=new LivingKingdomsState(); s.registerCustom(HOME,OWNER,de.quest.village.VillageLifeState.VillageKey.overworld(0,0),POS,"Home").orElseThrow(); return s;
    }
    private static Random always(int value) { return new Random(0) { @Override public int nextInt(int bound) { return Math.floorMod(value,bound); } }; }
    @Test void newInnRequiresTwoLoadedDaysAndReloadUnloadOrRebuildingCannotBypassIt() {
        var s=state(); var g=s.tavernGuests(); var inn=UUID.randomUUID();
        g.observeInn(HOME,inn,0,true,true);
        for(long t=100;t<=24000;t+=100) g.observeInn(HOME,inn,t,true,true);
        assertFalse(g.ready(HOME,inn));
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); g=s.tavernGuests();
        g.observeInn(HOME,inn,1000000,true,true); assertFalse(g.ready(HOME,inn));
        g.observeInn(HOME,inn,1000100,true,false); g.observeInn(HOME,inn,2000000,true,true);
        for(long t=2000100;t<=2024000;t+=100) g.observeInn(HOME,inn,t,true,true);
        assertTrue(g.ready(HOME,inn));
        g.observeInn(HOME,inn,2024100,false,true); assertFalse(g.ready(HOME,inn));
        g.observeInn(HOME,UUID.randomUUID(),2024200,true,true); assertFalse(g.ready(HOME,inn));
    }
    @Test void failedDrawCannotRerollAfterReloadOrBackwardsClock() {
        var s=state(); assertFalse(s.tavernGuests().draw(HOME,1000,true,always(1)));
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s));
        assertFalse(s.tavernGuests().draw(HOME,1100,true,always(0)));
        assertFalse(s.tavernGuests().draw(HOME,0,true,always(0)));
        assertTrue(s.tavernGuests().draw(HOME,25000,true,always(0)));
        assertFalse(s.tavernGuests().draw(HOME,49000,true,always(0)));
    }
    @Test void openingReportReadsExactSavedTicksWithoutCatchupAndSurvivesReload() {
        var s=state(); var g=s.tavernGuests(); var inn=UUID.randomUUID();
        var before=LivingKingdomsState.toNbt(s).copy();
        assertEquals(new KingdomTavernGuests.Opening(0,48000,false),g.opening(HOME,inn,999999));
        assertEquals(before,LivingKingdomsState.toNbt(s),"An observation report must not create an opening record");
        g.observeInn(HOME,inn,0,true,true);
        for(long t=100;t<=1200;t+=100)g.observeInn(HOME,inn,t,true,true);
        assertEquals(new KingdomTavernGuests.Opening(1200,46800,true),g.opening(HOME,inn,1200));
        assertEquals(new KingdomTavernGuests.Opening(1200,46800,false),g.opening(HOME,inn,999999));
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); g=s.tavernGuests();
        assertEquals(new KingdomTavernGuests.Opening(1200,46800,false),g.opening(HOME,inn,1200));
        g.observeInn(HOME,inn,999999,true,true);
        assertEquals(1200,g.opening(HOME,inn,999999).elapsed(),"Calendar jumps and reload do not supply observed operation");
        assertEquals(0,g.opening(HOME,UUID.randomUUID(),999999).elapsed());
        g.observeInn(HOME,inn,1000099,false,true);
        assertEquals(new KingdomTavernGuests.Opening(0,48000,false),g.opening(HOME,inn,1000099));
    }
    @Test void visitsCountOnlyContinuousLoadedTimeAndPersistWithoutCatchup() {
        var s=state(); var g=s.tavernGuests(); assertTrue(g.draw(HOME,1000,true,always(0)));
        g.observe(HOME,100,true); for(long t=200;t<=3700;t+=100) g.observe(HOME,t,true);
        assertFalse(g.expired(HOME,3700));
        s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); g=s.tavernGuests();
        g.observe(HOME,1000000,true); assertFalse(g.expired(HOME,1000000));
        g.observe(HOME,1000100,false); g.observe(HOME,2000000,true);
        for(long t=2000100;t<=2003600;t+=100) g.observe(HOME,t,true);
        assertTrue(g.expired(HOME,2003600));
    }
    @Test void departureCooldownAndNoImmediateRepeatSurviveReload() {
        var s=state(); var g=s.tavernGuests(); g.draw(HOME,1000,true,always(0)); assertEquals(0,g.active(HOME));
        g.end(HOME,5000); s=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s)); g=s.tavernGuests();
        assertFalse(g.draw(HOME,25000,true,always(0))); assertFalse(g.draw(HOME,49000,true,always(0)));
        assertTrue(g.draw(HOME,73000,true,always(0))); assertEquals(1,g.active(HOME));
    }
    @Test void wrongFutureOrMalformedGuestDataStaysOpaqueAndCannotSpawn() {
        for(net.minecraft.nbt.Tag data:List.of(StringTag.valueOf("future"),new CompoundTag())) {
            var root=LivingKingdomsState.toNbt(state()); root.put("tavernGuests",data);
            var restored=LivingKingdomsState.fromNbt(root);
            assertTrue(restored.tavernGuests().readOnly()); assertFalse(restored.tavernGuests().draw(HOME,1000,true,always(0)));
            assertEquals(new KingdomTavernGuests.Opening(-1,-1,false),restored.tavernGuests().opening(HOME,UUID.randomUUID(),1000));
            assertEquals(data,LivingKingdomsState.toNbt(restored).get("tavernGuests"));
        }
    }
    @Test void briefDialogueGraceCannotKeepAnExpiredVisitAliveByRepeatedClicking() {
        var g=state().tavernGuests(); g.draw(HOME,1000,true,always(0));
        g.observe(HOME,0,true); for(long t=100;t<7200;t+=100) g.observe(HOME,t,true);
        g.dialogue(HOME,7150); g.observe(HOME,7200,true);
        assertFalse(g.expired(HOME,7200)); g.dialogue(HOME,7240); assertTrue(g.expired(HOME,7250));
    }
    @Test void guestIdentityDoesNotOccupyTheInnkeeperSlotAndCannotReserveAnUnscheduledBody() {
        var s=state(); var keeper=s.people().ensure(HOME,KingdomPersonRegistry.Role.INNKEEPER,0).orElseThrow();
        var guest=s.people().ensure(HOME,KingdomPersonRegistry.Role.INNKEEPER,1).orElseThrow();
        assertNotEquals(keeper.id(),guest.id()); assertEquals(-1,keeper.guest()); assertEquals(0,guest.guest());
        assertTrue(s.people().reserve(guest.id(),guest.revision(),UUID.randomUUID(),POS,0).isEmpty());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(s));
        assertEquals(guest.id(),restored.people().staffGuest(HOME,0).orElseThrow().id());
        assertEquals(keeper.id(),restored.people().staff(HOME,KingdomPersonRegistry.Role.INNKEEPER).orElseThrow().id());
    }
}
