package de.quest.client.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class StatsMeasurementsTest {
    @Test void measuredCompletionUsesOnlyActiveSamplesAndNeverDisconnectAsAbandonment() {
        var state=new StatsMeasurements(); var out=new ArrayList<String>();
        state.event("quest.started.daily_honey",0,out::add);
        for(int i=0;i<60;i++) state.active(1000,out::add);
        state.end(out::add);
        assertFalse(out.stream().anyMatch(s->s.contains("abandoned")));
        state.event("snapshot.begin",0,out::add);state.event("quest.active.daily_honey",0,out::add);state.event("snapshot.end",0,out::add);
        state.event("quest.completed.daily_honey",0,out::add);
        assertTrue(out.contains("duration.completed_partial_1to5m.daily_honey"));
        assertEquals(60,out.stream().filter(s->s.equals("play.seconds.active")).count());
    }
    @Test void snapshotsNeverInventStartsAndUnknownIsDifferentFromZero() {
        var state=new StatsMeasurements(); var out=new ArrayList<String>();
        state.event("snapshot.begin",0,out::add);state.event("quest.active.daily_honey",0,out::add);state.event("snapshot.end",0,out::add);
        assertTrue(out.isEmpty());
        state.event("quest.completed.daily_honey",0,out::add);
        state.event("quest.completed.weekly_market_week",0,out::add);
        assertTrue(out.contains("duration.completed_partial_lt1m.daily_honey"));
        assertTrue(out.contains("duration.completed_partial_unknown.weekly_market_week"));
    }
    @Test void stallsCannotBecomePlaytimeAndRejectedClicksAreDebounced() {
        var state=new StatsMeasurements(); var out=new ArrayList<String>();
        state.event("quest.started.daily_honey",0,out::add);state.active(600000,out::add);
        state.event("quest.completed.daily_honey",0,out::add);
        assertTrue(out.contains("duration.completed_partial_lt1m.daily_honey"));
        state.event("reject.aid.stale",0,out::add);state.event("reject.aid.stale",9999,out::add);state.event("reject.aid.stale",10000,out::add);
        assertEquals(2,out.stream().filter(s->s.equals("reject.aid.stale")).count());
        state.event("quest.started.player_alice",0,out::add);assertFalse(out.stream().anyMatch(s->s.contains("alice")));
    }
    @Test void fullCompletionAndBucketsHaveExactBoundaries() {
        var state=new StatsMeasurements(); var out=new ArrayList<String>();
        state.event("story.started.failing_harvest_1",0,out::add);state.active(1000,out::add);
        state.event("story.completed.failing_harvest_1",0,out::add);
        assertTrue(out.contains("duration.completed_full_lt1m.failing_harvest_1"));
        assertEquals("lt1m",StatsMeasurements.bucket(59999));assertEquals("1to5m",StatsMeasurements.bucket(60000));
        assertEquals("5to15m",StatsMeasurements.bucket(300000));assertEquals("15to60m",StatsMeasurements.bucket(900000));
        assertEquals("1to4h",StatsMeasurements.bucket(3600000));assertEquals("ge4h",StatsMeasurements.bucket(14400000));
    }
    @Test void pausedQuestStopsWhileSessionContinuesAndRestoredTimersAwaitSnapshot() {
        var state=new StatsMeasurements();var out=new ArrayList<String>();
        state.event("quest.started.town_long_drive",0,out::add);
        state.active(1000,out::add);state.event("quest.paused.town_long_drive",0,out::add);
        for(int i=0;i<90;i++)state.active(1000,out::add);
        state.event("quest.resumed.town_long_drive",0,out::add);state.active(1000,out::add);
        state.event("quest.completed.town_long_drive",0,out::add);
        assertTrue(out.contains("duration.completed_full_lt1m.town_long_drive"));
        state.end(out::add);state.timers.put("quest.daily_honey",new StatsMeasurements.Timer(59000,false));
        state.active(2000,out::add);assertEquals(59000,state.timers.get("quest.daily_honey").millis());
        state.event("snapshot.begin",0,out::add);state.event("quest.active.daily_honey",0,out::add);state.event("snapshot.end",0,out::add);
        state.active(1000,out::add);assertEquals(60000,state.timers.get("quest.daily_honey").millis());
    }
}
