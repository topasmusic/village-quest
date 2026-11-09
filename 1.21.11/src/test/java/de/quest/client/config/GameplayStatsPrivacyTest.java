package de.quest.client.config;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
final class GameplayStatsPrivacyTest {
    @TempDir Path dir;
    private static Field field(String name) throws Exception {var f=GameplayStats.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void set(String name,Object value)throws Exception{field(name).set(null,value);}
    private static void call(String name,Class<?>[] types,Object...args)throws Exception{var m=GameplayStats.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(null,args);}
    private static void drain()throws Exception{((ScheduledExecutorService)field("WORKER").get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);}
    @Test void withdrawingDeletesRealPendingReportsAndStopsFurtherRecording() throws Exception {
        set("choiceFile",dir.resolve("choice"));set("pendingFile",dir.resolve("pending.json"));
        set("version","2.6.5-unreleased.3");set("minecraftLine","26.3");set("mode","single");set("session",null);set("enabled",false);
        try {
            assertFalse(GameplayStats.enabled());
            assertTrue(GameplayStats.choose(true));drain();
            assertEquals("yes-v2-adult",Files.readString(dir.resolve("choice")));
            call("record",new Class<?>[]{String.class},"quest.completed.daily_honey");
            call("record",new Class<?>[]{String.class},"quest.started.player_alice");
            call("flush",new Class<?>[]{});drain();
            String queued=Files.readString(dir.resolve("pending.json"));
            assertTrue(queued.contains("quest.completed.daily_honey"));assertFalse(queued.contains("alice"));
            assertTrue(queued.contains("\"line\":\"26.3\""));
            assertTrue(GameplayStats.choose(false));drain();assertFalse(GameplayStats.enabled());
            assertEquals("no",Files.readString(dir.resolve("choice")));assertFalse(Files.exists(dir.resolve("pending.json")));
            call("record",new Class<?>[]{String.class},"quest.completed.daily_honey");call("flush",new Class<?>[]{});drain();
            assertFalse(Files.exists(dir.resolve("pending.json")));
        } finally {set("enabled",false);set("session",null);((Map<?,?>)field("COUNTS").get(null)).clear();drain();}
    }
    @Test void spoolRejectsStaleWrongLineWrongVersionAndUnknownMetrics() throws Exception {
        set("version","2.6.5-unreleased.3");set("minecraftLine","26.3");
        Class<?> type=Arrays.stream(GameplayStats.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Report")).findFirst().orElseThrow();
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var valid=GameplayStats.class.getDeclaredMethod("validReport",type);valid.setAccessible(true);
        long today=LocalDate.now(ZoneOffset.UTC).toEpochDay();String id=UUID.randomUUID().toString();
        assertEquals(true,valid.invoke(null,constructor.newInstance(id,today,"26.3","2.6.5-unreleased.3","single",Map.of("quest.completed.daily_honey",1),false)));
        for(Object[] args:new Object[][]{
            {id,today-7,"26.3","2.6.5-unreleased.3","single",Map.of("quest.completed.daily_honey",1)},
            {id,today,"1.21.11","2.6.5-unreleased.3","single",Map.of("quest.completed.daily_honey",1)},
            {id,today,"26.3","2.6.4","single",Map.of("quest.completed.daily_honey",1)},
            {id,today,"26.3","2.6.5-unreleased.3","single",Map.of("quest.started.player_alice",1)}})
            assertEquals(false,valid.invoke(null,constructor.newInstance(java.util.stream.Stream.concat(Arrays.stream(args),java.util.stream.Stream.of(false)).toArray())));
    }

    @Test void draftsMergeWithoutSaturationButAttemptedPacketsNeverChange() throws Exception {
        set("choiceFile",dir.resolve("choice"));set("pendingFile",dir.resolve("pending"));set("stateFile",null);
        set("version","2.6.5-unreleased.4");set("minecraftLine","26.3");set("mode","single");set("session",null);set("enabled",false);
        var reports=(List<Object>)field("REPORTS").get(null);reports.clear();((Map<?,?>)field("COUNTS").get(null)).clear();
        try {
            assertTrue(GameplayStats.choose(true));drain();
            for(int i=0;i<10001;i++)call("record",new Class<?>[]{String.class},"play.seconds.active");
            call("flush",new Class<?>[]{});drain();
            assertEquals(2,reports.size());
            var type=reports.getFirst().getClass();var counts=type.getDeclaredMethod("counts");counts.setAccessible(true);
            assertEquals(10000,((Map<?,?>)counts.invoke(reports.getFirst())).get("play.seconds.active"));
            assertEquals(1,((Map<?,?>)counts.invoke(reports.getLast())).get("play.seconds.active"));
            call("record",new Class<?>[]{String.class},"quest.completed.daily_honey");call("flush",new Class<?>[]{});drain();
            assertEquals(2,reports.size());
            Object last=reports.getLast();var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
            var accessors=Arrays.stream(type.getRecordComponents()).map(c->{try{var m=c.getAccessor();m.setAccessible(true);return m.invoke(last);}catch(Exception e){throw new RuntimeException(e);}}).toArray();
            accessors[6]=true;reports.set(reports.size()-1,constructor.newInstance(accessors));
            String before=Files.readString(dir.resolve("pending"));
            call("record",new Class<?>[]{String.class},"quest.completed.daily_honey");call("flush",new Class<?>[]{});drain();
            assertEquals(3,reports.size());
            assertEquals(1,((Map<?,?>)counts.invoke(reports.get(1))).get("quest.completed.daily_honey"));
            assertEquals(1,((Map<?,?>)counts.invoke(reports.getLast())).get("quest.completed.daily_honey"));
            assertTrue(GameplayStats.choose(false));drain();
        } finally {set("enabled",false);reports.clear();((Map<?,?>)field("COUNTS").get(null)).clear();drain();}
    }
    @Test void feedbackNeedsConsentAndLimitsEachQuestionGroupPerDay() throws Exception {
        set("choiceFile",dir.resolve("choice"));set("pendingFile",dir.resolve("pending"));set("stateFile",dir.resolve("measurements"));
        set("version","2.6.5-unreleased.4");set("minecraftLine","26.3");set("mode","single");set("session",null);set("enabled",false);
        assertFalse(GameplayStats.feedback("special","helpful"));
        try {
            assertTrue(GameplayStats.choose(true));drain();
            assertTrue(GameplayStats.feedback("special","helpful"));assertFalse(GameplayStats.feedback("special","confusing"));
            assertTrue(GameplayStats.feedback("special","hard"));assertFalse(GameplayStats.feedback("special","easy"));
            assertFalse(GameplayStats.feedback("player_alice","helpful"));
            drain();assertTrue(Files.exists(dir.resolve("measurements")));
            assertTrue(GameplayStats.choose(false));drain();assertFalse(Files.exists(dir.resolve("measurements")));
        } finally {set("enabled",false);((Map<?,?>)field("COUNTS").get(null)).clear();drain();}
    }
    @Test void optionalTextIsBoundedAndNeverImplicitlySent() {
        assertFalse(GameplayStats.validFeedbackText(" "));assertFalse(GameplayStats.validFeedbackText("x".repeat(501)));
        assertFalse(GameplayStats.validFeedbackText("hello\u0001"));assertTrue(GameplayStats.validFeedbackText("Quest balance could improve."));
    }
}
