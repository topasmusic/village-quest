package de.quest.client.config;

import de.quest.network.GameplayStatsMetrics;
import java.util.*;
import java.util.function.Consumer;

/** Bounded local active-time measurements. Never serializes a world identity into a report. */
final class StatsMeasurements {
    record Timer(long millis, boolean complete) {}
    final Map<String, Timer> timers = new HashMap<>();
    private final Set<String> snapshot = new HashSet<>();
    private final Set<String> paused = new HashSet<>();
    private final Set<String> confirmed = new HashSet<>();
    private boolean receiving;
    private long sessionMillis, secondRemainder;
    private final Map<String, Long> rejected = new HashMap<>();

    void event(String metric, long now, Consumer<String> emit) {
        if (metric.equals("snapshot.begin")) { snapshot.clear(); paused.clear(); receiving = true; return; }
        if (metric.equals("snapshot.end")) {
            if (receiving) {
                timers.keySet().retainAll(snapshot);
                for (String key : snapshot) timers.putIfAbsent(key, new Timer(0, false));
                confirmed.clear(); confirmed.addAll(snapshot);
            }
            receiving = false; return;
        }
        String[] parts = metric.split("\\.", 3);
        if (parts.length != 3) return;
        String content = parts[0]+"."+parts[2];
        if ((parts[1].equals("paused") || parts[1].equals("resumed")) && GameplayStatsMetrics.content(content)) {
            if (parts[1].equals("paused")) paused.add(content); else paused.remove(content);
            if (receiving) snapshot.add(content);
            return;
        }
        if (parts[1].equals("active")) {
            if (receiving && GameplayStatsMetrics.content(parts[0] + "." + parts[2])) snapshot.add(parts[0] + "." + parts[2]);
            return;
        }
        if (!GameplayStatsMetrics.valid(metric)) return;
        if (parts[0].equals("reject")) {
            Long last = rejected.get(metric);
            if (last != null && now - last < 10000) return;
            rejected.put(metric, now); emit.accept(metric); return;
        }
        emit.accept(metric);
        if (!parts[0].equals("quest") && !parts[0].equals("story")) return;
        String key = parts[0] + "." + parts[2];
        if (parts[1].equals("started")) { timers.put(key, new Timer(0, !timers.containsKey(key))); confirmed.add(key); }
        else if (parts[1].equals("completed") || parts[1].equals("abandoned")) {
            Timer timer = timers.remove(key);
            String bucket = timer == null ? "unknown" : bucket(timer.millis());
            String coverage = timer != null && timer.complete() ? "full" : "partial";
            emit.accept("duration." + parts[1] + "_" + coverage + "_" + bucket + "." + parts[2]);
        }
    }
    void active(long millis, Consumer<String> emit) {
        if (millis < 0 || millis > 2000) { partial(); return; }
        sessionMillis += millis; secondRemainder += millis;
        while (secondRemainder >= 1000) { secondRemainder -= 1000; emit.accept("play.seconds.active"); }
        timers.replaceAll((key, timer) -> paused.contains(key) || !confirmed.contains(key) ? timer : new Timer(Math.min(604800000L, timer.millis() + millis), timer.complete()));
    }
    void partial() { timers.replaceAll((key,timer) -> new Timer(timer.millis(), false)); }
    void end(Consumer<String> emit) {
        if (sessionMillis > 0) emit.accept("session.length." + bucket(sessionMillis));
        sessionMillis = 0; secondRemainder = 0; snapshot.clear(); paused.clear(); confirmed.clear(); receiving = false; rejected.clear(); partial();
    }
    static String bucket(long millis) {
        return millis < 60000 ? "lt1m" : millis < 300000 ? "1to5m" : millis < 900000 ? "5to15m"
                : millis < 3600000 ? "15to60m" : millis < 14400000 ? "1to4h" : "ge4h";
    }
}
