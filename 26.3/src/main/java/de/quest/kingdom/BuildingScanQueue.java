package de.quest.kingdom;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Server-wide round-robin work queue. All dimensions share its 256-sample tick budget. */
public final class BuildingScanQueue {
    private static final int BUDGET = 256, QUANTUM = 32, CAPACITY = 128;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Map<UUID, Job> active = new HashMap<>();
    private final Map<UUID, Long> requests = new HashMap<>();
    private long lastTick = -1;

    public boolean request(UUID id, long tick, BuildingScanner scanner,
                           BiConsumer<UUID, BuildingScanner.Report> completed) {
        if (id == null || tick < 0 || scanner == null || scanner.done() || completed == null
                || tick < lastTick || active.containsKey(id) || active.size() >= CAPACITY) return false;
        Long previous = requests.get(id);
        if (previous != null && (tick < previous || tick - previous < 40)) return false;
        requests.entrySet().removeIf(entry -> tick >= entry.getValue() && tick - entry.getValue() >= 40);
        Job job = new Job(id, scanner, completed);
        jobs.addLast(job); active.put(id, job); requests.put(id, tick);
        return true;
    }

    public int tick(long tick) {
        return tick(tick, BUDGET);
    }
    public int tick(long tick, int availableSamples) {
        if (tick < 0 || tick <= lastTick) return 0;
        lastTick = tick;
        int used = 0, budget = Math.clamp(availableSamples, 0, BUDGET);
        while (!jobs.isEmpty() && used < budget) {
            Job job = jobs.removeFirst();
            int samples = job.scanner.advance(Math.min(QUANTUM, budget - used));
            used += samples;
            if (job.scanner.done()) {
                active.remove(job.id);
                job.completed.accept(job.id, job.scanner.report().orElseThrow());
            } else {
                jobs.addLast(job);
                if (samples == 0) break;
            }
        }
        return used;
    }
    public void cancel(UUID id) {
        Job job = active.remove(id);
        if (job != null) jobs.remove(job);
    }
    public boolean pending(UUID id) { return active.containsKey(id); }
    public int size() { return active.size(); }
    private record Job(UUID id, BuildingScanner scanner, BiConsumer<UUID, BuildingScanner.Report> completed) {
        private Job { Objects.requireNonNull(id); }
    }
}
