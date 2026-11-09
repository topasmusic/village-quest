package de.quest.client.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.quest.network.GameplayStatsEvent;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** Optional official HTTPS statistics; consent version 2 is separate from local testing. */
public final class GameplayStats {
    private static final boolean LOCAL_MODE = Boolean.getBoolean("villagequest.stats.local");
    private static final boolean AVAILABLE = true;
    private static final String COLLECTOR = "https://village-quest-stats.damp-bread-3f6b.workers.dev";
    private static String minecraftLine;
    private static Path choiceFile;
    private static Path pendingFile;
    private static Path stateFile;
    private static final StatsMeasurements MEASURE = new StatsMeasurements();
    private static LocalState local = new LocalState();
    private static String scope;
    private static long lastNanos;
    private static long performanceMillis;
    private static int snapshotTicks;
    private static String feedbackTarget = "general";
    private static String profile;
    private static final class LocalState {
        long lastDay = -1;
        Map<String, Map<String, StatsMeasurements.Timer>> worlds = new java.util.LinkedHashMap<>();
        Map<String, Long> feedback = new HashMap<>();
    }
    private static final Gson JSON = new Gson();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Village Quest statistics");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Bucket, Map<String, Integer>> COUNTS = new HashMap<>();
    private static final List<Report> REPORTS = new ArrayList<>();
    private static String version;
    private static volatile boolean enabled;
    private static volatile int consentGeneration;
    private static int workerGeneration;
    private static boolean prompted;
    private static volatile String session;
    private static String mode;
    private static int elapsed;
    private static HttpURLConnection inFlight;

    private record Bucket(long day, String mode) {}
    private record Report(String id, long day, String line, String version, String mode, Map<String, Integer> counts, boolean sealed) {}

    private GameplayStats() {}

    public static void init() {
        if (!AVAILABLE) return;
        Path config = FabricLoader.getInstance().getConfigDir();
        String suffix = LOCAL_MODE ? "local-v2" : "public-v2";
        choiceFile = config.resolve("village-quest-stats-choice-" + suffix + ".txt");
        pendingFile = config.resolve("village-quest-stats-pending-" + suffix + ".json");
        stateFile = config.resolve("village-quest-stats-measurements-" + suffix + ".json");
        // A v1 opt-in never enables the broader v2 scope; discard its unsent spool.
        try { Files.deleteIfExists(config.resolve("village-quest-stats-pending-" + (LOCAL_MODE ? "local-v1" : "public-v1") + ".json")); }
        catch (IOException ignored) {}
        minecraftLine = FabricLoader.getInstance().getModContainer("minecraft")
                .orElseThrow().getMetadata().getVersion().getFriendlyString();
        version = FabricLoader.getInstance().getModContainer("village-quest")
                .orElseThrow().getMetadata().getVersion().getFriendlyString();
        try {
            String value = Files.readString(choiceFile);
            prompted = true;
            enabled = value.equals("yes-v2-adult");
        } catch (IOException ignored) {
            // Missing or unreadable consent always defaults to off.
        }
        if (enabled) loadLocal(); else deleteLocal();
        WORKER.execute(() -> {
            if (enabled) {
                try {
                    if (Files.size(pendingFile) > 1048576) throw new IOException("Spool too large");
                    List<Report> stored = JSON.fromJson(Files.readString(pendingFile), new TypeToken<List<Report>>() {}.getType());
                    if (stored != null && stored.size() <= 128) stored.stream().filter(GameplayStats::validReport).forEach(REPORTS::add);
                } catch (IOException | RuntimeException ignored) {
                    // An invalid local spool must never prevent play.
                }
            } else {
                deletePending();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(GameplayStatsEvent.ID, (event, context) -> {
            if (!enabled) return;
            int generation = consentGeneration;
            context.client().execute(() -> {
                if (generation == consentGeneration && enabled && !de.quest.client.screen.UiTestTour.active()) {
                    if (event.metric().startsWith("profile.current.")) {
                        String value=event.metric().substring("profile.current.".length());
                        if (java.util.Set.of("relaxed","standard","hardened").contains(value)) profile=value;
                        return;
                    }
                    MEASURE.event(event.metric(), System.nanoTime()/1_000_000, GameplayStats::record);
                    if (event.metric().startsWith("quest.completed.") || event.metric().startsWith("story.completed.")) {
                        String key = event.metric().substring(event.metric().lastIndexOf('.')+1);
                        feedbackTarget = event.metric().startsWith("story.") ? "story" : key.substring(0,key.indexOf('_'));
                        if (!java.util.Set.of("daily","weekly","special","town","commission","freight","shared","afterstory").contains(feedbackTarget)) feedbackTarget="general";
                    }
                }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean playing = enabled && client.level != null && client.player != null;
            if (!playing) {
                if (session != null) endSession();
                lastNanos = 0;
            } else {
                if (session == null) startSession(client);
                long now = System.nanoTime();
                long delta = lastNanos == 0 ? 0 : (now-lastNanos)/1_000_000;
                lastNanos = now;
                if (!client.isPaused() && client.gui.screen() == null && !de.quest.client.screen.UiTestTour.active()) {
                    MEASURE.active(delta, GameplayStats::record);
                    if (delta <= 2000) performanceMillis += delta;
                    if (performanceMillis >= 60000) {
                        performanceMillis %= 60000;
                        int fps = client.getFps();
                        record("performance.fps." + (fps < 30 ? "lt30" : fps < 60 ? "30to59" : "ge60"));
                        record("context.difficulty."+client.level.getDifficulty().name().toLowerCase(java.util.Locale.ROOT));
                        if (profile != null) record("context.profile."+profile);
                    }
                }
                if (++snapshotTicks >= 200) {
                    snapshotTicks = 0;
                    if (ClientPlayNetworking.canSend(GameplayStatsEvent.ID) && !de.quest.client.screen.UiTestTour.active())
                        ClientPlayNetworking.send(new GameplayStatsEvent("snapshot.request"));
                }
            }
            if (enabled && ++elapsed >= 100) { elapsed = 0; checkpointLocal(); flush(); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (session != null) endSession();
            flush();
            try {
                WORKER.submit(() -> {}).get(3, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
                // The last checkpoint remains available when the worker is busy.
            }
        });
        WORKER.scheduleWithFixedDelay(GameplayStats::sendHeartbeat, 0, 60, TimeUnit.SECONDS);
        WORKER.scheduleWithFixedDelay(GameplayStats::upload, 30, 600, TimeUnit.SECONDS);
    }

    public static boolean available() { return AVAILABLE; }
    public static boolean localMode() { return LOCAL_MODE; }
    public static String privacyUrl() { return COLLECTOR + "/privacy"; }
    public static boolean enabled() { return enabled; }

    public static boolean offer() {
        if (!AVAILABLE || prompted) return false;
        prompted = true;
        saveChoice("no");
        return true;
    }

    public static boolean choose(boolean share) {
        if (!AVAILABLE) return false;
        if (share == enabled) return true;
        if (share) {
            if (!saveChoice("yes-v2-adult")) return false;
            int generation = ++consentGeneration;
            WORKER.execute(() -> workerGeneration = generation);
            enabled = true;
            return true;
        }
        String endingSession = session;
        synchronized (GameplayStats.class) {
            enabled = false;
            consentGeneration++;
            session = null;
            if (inFlight != null) inFlight.disconnect();
        }
        COUNTS.clear();
        textRetry=null;
        MEASURE.timers.clear(); MEASURE.end(metric -> {});
        local = new LocalState(); scope = null; mode = null; lastNanos = 0; feedbackTarget="general";
        deleteLocal();
        WORKER.execute(() -> {
            REPORTS.clear();
            deletePending(); deleteLocal();
            if (endingSession != null) post("/leave", Map.of("session", endingSession), -1);
        });
        return saveChoice("no");
    }

    private static boolean saveChoice(String value) {
        try {
            Files.createDirectories(choiceFile.getParent());
            Files.writeString(choiceFile, value);
            return true;
        } catch (IOException ignored) {
            if (value.equals("no")) {
                try {
                    Files.deleteIfExists(choiceFile);
                    return true;
                } catch (IOException ignoredAgain) {
                    return false;
                }
            }
            return false;
        }
    }

    private static void record(String metric) {
        if (!enabled || mode == null || !GameplayStatsEvent.valid(metric)) return;
        var counters = COUNTS.computeIfAbsent(new Bucket(today(), mode), unused -> new HashMap<>());
        if ((!counters.containsKey(metric) && counters.size() >= 200) || counters.getOrDefault(metric,0) >= 10000) {
            flush(); record(metric); return;
        }
        counters.merge(metric, 1, Integer::sum);
    }

    private static void startSession(net.minecraft.client.Minecraft client) {
        session = UUID.randomUUID().toString();
        mode = client.getSingleplayerServer() == null ? "multi" : "single";
        // Local-only world/player scope. Neither the hash nor its inputs enter Report.
        String world = client.getCurrentServer() != null ? "server:"+client.getCurrentServer().ip
                : client.getSingleplayerServer() != null ? "world:"+client.getSingleplayerServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize() : "unknown";
        scope = QuestTrackerFocus.digest(world+":player:"+client.player.getUUID());
        MEASURE.timers.clear();
        MEASURE.timers.putAll(local.worlds.getOrDefault(scope, Map.of()));
        // Offline progress/replacement cannot be verified: restored samples remain explicitly partial.
        MEASURE.partial();
        snapshotTicks = 199; performanceMillis = 0;
        profile = null;
        long gap = local.lastDay < 0 ? -1 : today()-local.lastDay;
        record("return.gap." + (gap < 0 ? "first" : gap == 0 ? "same_day" : gap == 1 ? "next_day"
                : gap <= 7 ? "2to7d" : gap <= 30 ? "8to30d" : "gt30d"));
        local.lastDay = today();
    }
    private static void endSession() {
        MEASURE.end(GameplayStats::record);
        checkpointLocal(); flush();
        String ending = session; session = null; mode = null; scope = null;
        WORKER.execute(() -> { if (ending != null) post("/leave",Map.of("session",ending),-1); });
    }
    public static String feedbackTarget() { return feedbackTarget; }
    public static boolean feedback(String target, String answer) {
        if (!enabled || mode == null || de.quest.client.screen.UiTestTour.active()) return false;
        String metric = "feedback."+target+"."+answer;
        if (!de.quest.network.GameplayStatsMetrics.valid(metric)) return false;
        String group = java.util.Set.of("helpful","confusing").contains(answer) ? "clarity"
                : java.util.Set.of("easy","balanced","hard").contains(answer) ? "difficulty" : "problem";
        String key = target+"."+group;
        if (local.feedback.getOrDefault(key,-1L) == today()) return false;
        local.feedback.put(key,today()); record(metric); checkpointLocal(); return true;
    }
    private record TextFeedback(String id,long day,String line,String version,String target,String message) {}
    private static TextFeedback textRetry;
    private static boolean textSending;
    public static boolean validFeedbackText(String value) {
        return value!=null&&!value.isBlank()&&value.length()<=500&&value.chars().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t');
    }
    public static boolean canSendText(){return enabled&&mode!=null&&!textSending&&!de.quest.client.screen.UiTestTour.active()&&local.feedback.getOrDefault("text",-1L)!=today();}
    public static void sendText(String target,String value,java.util.function.Consumer<Boolean> done){
        if(!canSendText()||!validFeedbackText(value)||!de.quest.network.GameplayStatsMetrics.valid("feedback."+target+".helpful")){done.accept(false);return;}
        String body=value.strip();
        if(textRetry==null||!textRetry.target().equals(target)||!textRetry.message().equals(body)||textRetry.day()<today()-1)
            textRetry=new TextFeedback(UUID.randomUUID().toString(),today(),minecraftLine,version,target,body);
        TextFeedback packet=textRetry;textSending=true;int generation=consentGeneration;
        WORKER.execute(()->{
            boolean ok=post("/feedback",packet,generation)==204;
            net.minecraft.client.Minecraft.getInstance().execute(()->{
                textSending=false;
                boolean accepted=ok&&enabled&&generation==consentGeneration;
                if(accepted){local.feedback.put("text",today());textRetry=null;checkpointLocal();}
                done.accept(accepted);
            });
        });
    }
    private static void loadLocal() {
        try {
            if (Files.size(stateFile) > 262144) return;
            LocalState stored = JSON.fromJson(Files.readString(stateFile),LocalState.class);
            if (stored == null || stored.worlds == null || stored.feedback == null || stored.worlds.size()>32 || stored.feedback.size()>64) return;
            if (stored.lastDay > today() || stored.lastDay < today()-90) return;
            for (var entry : stored.worlds.entrySet()) {
                if (!entry.getKey().matches("[a-f0-9]{64}") || entry.getValue()==null || entry.getValue().size()>200) return;
                for (var t : entry.getValue().entrySet()) if (!de.quest.network.GameplayStatsMetrics.content(t.getKey())
                        || t.getValue()==null || t.getValue().millis()<0 || t.getValue().millis()>604800000L) return;
            }
            stored.feedback.entrySet().removeIf(e -> e.getValue()==null || e.getValue()!=today());
            local = stored;
        } catch (IOException | RuntimeException ignored) {}
    }
    private static void checkpointLocal() {
        if (!enabled || stateFile == null) return;
        if (scope != null) local.worlds.put(scope,Map.copyOf(MEASURE.timers));
        while (local.worlds.size()>32) local.worlds.remove(local.worlds.keySet().iterator().next());
        local.feedback.entrySet().removeIf(e -> e.getValue()!=today());
        String json = JSON.toJson(local); int generation = consentGeneration;
        WORKER.execute(() -> {
            if (!enabled || generation != consentGeneration) return;
            try { Files.writeString(stateFile,json); } catch (IOException ignored) {}
        });
    }
    private static void deleteLocal() {
        if (stateFile != null) try { Files.deleteIfExists(stateFile); } catch (IOException ignored) {}
    }

    private static long today() { return LocalDate.now(ZoneOffset.UTC).toEpochDay(); }

    private static void flush() {
        if (COUNTS.isEmpty()) return;
        var counters = new HashMap<>(COUNTS);
        COUNTS.clear();
        int generation = consentGeneration;
        WORKER.execute(() -> {
            if (!enabled || generation != consentGeneration) return;
            counters.forEach(GameplayStats::queue);
            REPORTS.removeIf(report -> report.day() < today() - 6);
            while (REPORTS.size() > 128) REPORTS.removeFirst();
            while (JSON.toJson(REPORTS).length() > 1000000) REPORTS.removeFirst();
            persist();
        });
    }

    /** Only never-attempted drafts may merge. A retry ID always represents identical counters. */
    private static void queue(Bucket bucket, Map<String,Integer> counts) {
        if (!REPORTS.isEmpty()) {
            Report last = REPORTS.getLast();
            if (!last.sealed() && last.day()==bucket.day() && last.mode().equals(bucket.mode())
                    && last.line().equals(minecraftLine) && last.version().equals(version)) {
                var merged = new HashMap<>(last.counts());
                counts.forEach((key,value) -> merged.merge(key,value,Integer::sum));
                if (merged.size()<=200 && merged.values().stream().allMatch(value -> value<=10000)) {
                    REPORTS.set(REPORTS.size()-1,new Report(last.id(),last.day(),last.line(),last.version(),last.mode(),merged,false));
                    return;
                }
            }
        }
        REPORTS.add(new Report(UUID.randomUUID().toString(),bucket.day(),minecraftLine,version,bucket.mode(),Map.copyOf(counts),false));
    }

    private static void upload() {
        int generation = consentGeneration;
        if (!enabled || generation != workerGeneration) return;
        boolean changed = REPORTS.removeIf(report -> report.day() < today() - 6);
        for (int count = 0; count < 8 && enabled && generation == consentGeneration && !REPORTS.isEmpty(); count++) {
            Report report = REPORTS.getFirst();
            if (!report.sealed()) {
                report = new Report(report.id(),report.day(),report.line(),report.version(),report.mode(),report.counts(),true);
                REPORTS.set(0,report);
            }
            // Persist the immutable retry boundary before any bytes can reach the receiver.
            if (!persist()) break;
            int status = post("/report", report, generation);
            if (status != 204 && status != 400) break;
            REPORTS.removeFirst();
            changed = true;
        }
        if (changed && enabled && generation == consentGeneration) persist();
    }

    private static void sendHeartbeat() {
        int generation = consentGeneration;
        String current = session;
        if (enabled && current != null && generation == workerGeneration) post("/heartbeat", Map.of("session", current), generation);
    }

    private static int post(String route, Object body, int generation) {
        HttpURLConnection connection = null;
        try {
            synchronized (GameplayStats.class) {
                if (!route.equals("/leave") && (!enabled || generation != consentGeneration)) return -1;
                connection = (HttpURLConnection) URI.create((LOCAL_MODE ? "http://127.0.0.1:8765" : COLLECTOR) + route).toURL()
                        .openConnection(Proxy.NO_PROXY);
                inFlight = connection;
            }
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            if (body instanceof Report report) body = LOCAL_MODE ? Map.of("id", report.id(), "day", report.day(),
                    "version", report.version(), "mode", report.mode(), "counts", report.counts())
                    : Map.of("id",report.id(),"day",report.day(),"line",report.line(),"version",report.version(),"mode",report.mode(),"counts",report.counts());
            byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (var output = connection.getOutputStream()) {
                output.write(bytes);
            }
            return connection.getResponseCode();
        } catch (IOException ignored) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
            synchronized (GameplayStats.class) { inFlight = null; }
        }
    }

    private static boolean validReport(Report report) {
        return report != null && report.id() != null
                && report.id().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                && report.day() >= today() - 6 && report.day() <= today()
                && minecraftLine.equals(report.line()) && version.equals(report.version())
                && ("single".equals(report.mode()) || "multi".equals(report.mode()))
                && report.counts() != null && !report.counts().isEmpty() && report.counts().size() <= 256
                && report.counts().entrySet().stream().allMatch(e -> GameplayStatsEvent.valid(e.getKey())
                        && e.getValue() != null && e.getValue() >= 1 && e.getValue() <= 10000);
    }

    private static boolean persist() {
        if (REPORTS.isEmpty()) {
            deletePending();
            return true;
        }
        Path temporary = pendingFile.resolveSibling(pendingFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(choiceFile.getParent());
            Files.writeString(temporary, JSON.toJson(REPORTS));
            Files.move(temporary, pendingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ignored) {
            // Keep the queued counts in memory while the local file is unavailable.
            return false;
        }
    }

    private static void deletePending() {
        try {
            Files.deleteIfExists(pendingFile);
            Files.deleteIfExists(pendingFile.resolveSibling(pendingFile.getFileName() + ".tmp"));
        } catch (IOException ignored) {
            // Consent is checked separately before any queued data can be sent.
        }
    }
}
