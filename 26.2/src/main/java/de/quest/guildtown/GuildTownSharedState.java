package de.quest.guildtown;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Versioned guild-wide 2.4 project and Chronicle state. Personal history never enters this file. */
public final class GuildTownSharedState extends SavedData {
    static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String ID = "village_quest_guild_town";
    private static final int MAX_GUILD_CHRONICLE_EVENTS = 64;

    public static final SavedDataType<GuildTownSharedState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID), GuildTownSharedState::new,
            CompoundTag.CODEC.xmap(GuildTownSharedState::fromNbt, GuildTownSharedState::toNbt), DataFixTypes.LEVEL);

    /** Contains incomplete work only. Completion always releases the guild's active slot. */
    private final Map<UUID, MutableProject> projects = new HashMap<>();
    /** Completed-project rewards survive independently until their recorded participant claims them. */
    private final Map<UUID, List<RewardEntitlement>> entitlements = new HashMap<>();
    private final Map<UUID, List<String>> chronicle = new HashMap<>();

    GuildTownSharedState() {}

    public static GuildTownSharedState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<ProjectSnapshot> project(UUID guildId) {
        MutableProject project = guildId == null ? null : projects.get(guildId);
        return project == null ? Optional.empty() : Optional.of(project.snapshot());
    }

    public boolean accept(UUID guildId, GuildTownSharedProject type) {
        if (guildId == null || type == null || projects.containsKey(guildId)) return false;
        projects.put(guildId, new MutableProject(UUID.randomUUID(), type));
        addChronicle(guildId, "project.accepted." + type.key());
        setDirty();
        return true;
    }

    /** Mutation is idempotent for a caller-provided delivery token. */
    public ContributionResult contribute(UUID guildId, UUID participant, UUID deliveryToken, int amount) {
        MutableProject project = guildId == null ? null : projects.get(guildId);
        if (project == null || participant == null || deliveryToken == null || amount <= 0
                || !project.deliveryTokens.add(deliveryToken)) {
            return new ContributionResult(project == null ? null : project.snapshot(), 0, false);
        }
        int applied = Math.min(amount, project.type.target() - project.progress);
        if (applied <= 0) return new ContributionResult(project.snapshot(), 0, false);
        project.progress += applied;
        project.participants.add(participant);
        project.revision++;
        boolean completedNow = project.progress >= project.type.target();
        ProjectSnapshot result = project.snapshot(completedNow);
        if (completedNow) {
            for (UUID playerId : project.participants) addEntitlement(playerId,
                    new RewardEntitlement(project.projectId, guildId, project.type, project.revision));
            projects.remove(guildId);
            addChronicle(guildId, "project.completed." + project.type.key());
        }
        setDirty();
        return new ContributionResult(result, applied, completedNow);
    }

    public boolean canClaim(UUID guildId, UUID playerId) {
        return guildId != null && findEntitlement(playerId, guildId) != null;
    }
    public boolean claim(UUID guildId, UUID playerId) {
        return guildId != null && takeEntitlement(playerId, guildId) != null;
    }

    /** Allows a recorded contributor to claim after leaving the guild; no Chronicle data is copied. */
    public ClaimResult claimForParticipant(UUID playerId, UUID preferredGuildId) {
        RewardEntitlement reward = takeEntitlement(playerId, preferredGuildId);
        if (reward == null && preferredGuildId != null) reward = takeEntitlement(playerId, null);
        return reward == null ? null : new ClaimResult(reward.guildId(), reward.type());
    }

    /** Kept for source compatibility with 2.4.0-unreleased.5; completed work is already detached. */
    public boolean clearCompleted(UUID guildId) { return false; }

    public int pendingClaims(UUID playerId) {
        return playerId == null ? 0 : entitlements.getOrDefault(playerId, List.of()).size();
    }

    public boolean addChronicle(UUID guildId, String event) {
        if (guildId == null || event == null || event.isBlank()) return false;
        String safe = sanitizeEvent(event);
        if (safe.isBlank()) return false;
        List<String> events = chronicle.computeIfAbsent(guildId, ignored -> new ArrayList<>());
        if (events.contains(safe) || events.size() >= MAX_GUILD_CHRONICLE_EVENTS) return false;
        events.add(safe);
        setDirty();
        return true;
    }

    public List<String> chronicle(UUID guildId) { return List.copyOf(chronicle.getOrDefault(guildId, List.of())); }

    /**
     * Guild destruction removes only guild-owned state. Earned rewards are personal entitlements and
     * deliberately survive so recorded contributors can still claim after the guild no longer exists.
     */
    public void removeGuild(UUID guildId) {
        if (guildId == null) return;
        boolean changed = projects.remove(guildId) != null | chronicle.remove(guildId) != null;
        if (changed) setDirty();
    }

    public void resetAllProgress() {
        if (projects.isEmpty() && entitlements.isEmpty() && chronicle.isEmpty()) return;
        projects.clear(); entitlements.clear(); chronicle.clear(); setDirty();
    }

    static GuildTownSharedState fromNbt(CompoundTag root) {
        GuildTownSharedState state = new GuildTownSharedState();
        if (root == null || root.isEmpty()) return state;
        int schema = root.getIntOr("schemaVersion", 0);
        if (schema < 0 || schema > CURRENT_SCHEMA_VERSION) return state;
        for (int i = 0; i < root.getListOrEmpty("projects").size(); i++) {
            CompoundTag entry = root.getListOrEmpty("projects").getCompoundOrEmpty(i);
            UUID guildId = parseUuid(entry.getStringOr("guild", ""));
            GuildTownSharedProject type = GuildTownSharedProject.byId(entry.getIntOr("type", -1));
            if (guildId == null || type == null || state.projects.containsKey(guildId)) continue;
            UUID projectId = parseUuid(entry.getStringOr("projectId", ""));
            if (projectId == null) projectId = legacyProjectId(guildId, type, entry.getIntOr("revision", 0));
            MutableProject project = new MutableProject(projectId, type);
            project.progress = Math.max(0, Math.min(type.target(), entry.getIntOr("progress", 0)));
            project.revision = Math.max(0, entry.getIntOr("revision", 0));
            readUuids(entry.getListOrEmpty("participants"), project.participants);
            readUuids(entry.getListOrEmpty("deliveryTokens"), project.deliveryTokens);
            // Target progress is the authoritative terminal condition; a malformed legacy
            // boolean must never leave an unfinishable full project in the active slot.
            boolean completed = project.progress >= type.target();
            if (completed) {
                Set<UUID> rewarded = new HashSet<>();
                readUuids(entry.getListOrEmpty("rewarded"), rewarded);
                for (UUID participant : project.participants) if (!rewarded.contains(participant)) {
                    state.addEntitlement(participant, new RewardEntitlement(project.projectId, guildId, type, project.revision));
                }
            } else state.projects.put(guildId, project);
        }
        if (schema >= 2) readEntitlements(root.getListOrEmpty("entitlements"), state);
        readChronicle(root.getListOrEmpty("chronicle"), state);
        return state;
    }

    static CompoundTag toNbt(GuildTownSharedState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", CURRENT_SCHEMA_VERSION);
        ListTag projects = new ListTag();
        state.projects.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(mapEntry -> {
            MutableProject project = mapEntry.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putString("guild", mapEntry.getKey().toString());
            entry.putString("projectId", project.projectId.toString());
            entry.putInt("type", project.type.id());
            entry.putInt("progress", project.progress);
            entry.putInt("revision", project.revision);
            entry.put("participants", writeUuids(project.participants));
            entry.put("deliveryTokens", writeUuids(project.deliveryTokens));
            projects.add(entry);
        });
        root.put("projects", projects);
        ListTag rewards = new ListTag();
        state.entitlements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(playerEntry -> {
            for (RewardEntitlement reward : playerEntry.getValue()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("player", playerEntry.getKey().toString());
                entry.putString("projectId", reward.projectId().toString());
                entry.putString("guild", reward.guildId().toString());
                entry.putInt("type", reward.type().id());
                entry.putInt("sequence", reward.sequence());
                rewards.add(entry);
            }
        });
        root.put("entitlements", rewards);
        ListTag chronicle = new ListTag();
        state.chronicle.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(mapEntry -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("guild", mapEntry.getKey().toString());
            ListTag events = new ListTag();
            for (String event : mapEntry.getValue()) events.add(net.minecraft.nbt.StringTag.valueOf(event));
            entry.put("events", events); chronicle.add(entry);
        });
        root.put("chronicle", chronicle);
        return root;
    }

    private static void readEntitlements(ListTag list, GuildTownSharedState state) {
        Set<String> seenClaims = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            UUID player = parseUuid(entry.getStringOr("player", ""));
            UUID projectId = parseUuid(entry.getStringOr("projectId", ""));
            UUID guild = parseUuid(entry.getStringOr("guild", ""));
            GuildTownSharedProject type = GuildTownSharedProject.byId(entry.getIntOr("type", -1));
            if (player == null || projectId == null || guild == null || type == null
                    || !seenClaims.add(player + ":" + projectId)) continue;
            state.addEntitlement(player, new RewardEntitlement(projectId, guild, type,
                    Math.max(0, entry.getIntOr("sequence", 0))));
        }
    }

    private static void readChronicle(ListTag list, GuildTownSharedState state) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            UUID guildId = parseUuid(entry.getStringOr("guild", ""));
            if (guildId == null || state.chronicle.containsKey(guildId)) continue;
            List<String> events = new ArrayList<>();
            ListTag stored = entry.getListOrEmpty("events");
            for (int eventIndex = 0; eventIndex < stored.size() && events.size() < MAX_GUILD_CHRONICLE_EVENTS; eventIndex++) {
                String event = sanitizeEvent(stored.getStringOr(eventIndex, ""));
                if (!event.isBlank() && !events.contains(event)) events.add(event);
            }
            if (!events.isEmpty()) state.chronicle.put(guildId, events);
        }
    }

    private void addEntitlement(UUID playerId, RewardEntitlement reward) {
        List<RewardEntitlement> rewards = entitlements.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        if (rewards.stream().anyMatch(existing -> existing.projectId().equals(reward.projectId()))) return;
        rewards.add(reward);
    }

    private RewardEntitlement findEntitlement(UUID playerId, UUID guildId) {
        if (playerId == null) return null;
        for (RewardEntitlement reward : entitlements.getOrDefault(playerId, List.of())) {
            if (guildId == null || guildId.equals(reward.guildId())) return reward;
        }
        return null;
    }

    private RewardEntitlement takeEntitlement(UUID playerId, UUID guildId) {
        RewardEntitlement reward = findEntitlement(playerId, guildId);
        if (reward == null) return null;
        List<RewardEntitlement> rewards = entitlements.get(playerId);
        rewards.remove(reward);
        if (rewards.isEmpty()) entitlements.remove(playerId);
        setDirty();
        return reward;
    }

    private static void readUuids(ListTag list, Set<UUID> output) {
        for (int i = 0; i < list.size(); i++) { UUID id = parseUuid(list.getStringOr(i, "")); if (id != null) output.add(id); }
    }

    private static ListTag writeUuids(Set<UUID> ids) {
        ListTag list = new ListTag();
        ids.stream().sorted().forEach(id -> list.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        return list;
    }

    private static UUID legacyProjectId(UUID guildId, GuildTownSharedProject type, int revision) {
        return UUID.nameUUIDFromBytes(("village-quest:legacy-project:" + guildId + ":" + type.id() + ":" + revision)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitizeEvent(String event) { return event.replaceAll("[^a-z0-9_.-]", ""); }

    private static UUID parseUuid(String raw) {
        try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public record ProjectSnapshot(GuildTownSharedProject type, int progress, boolean completed,
                                  Set<UUID> participants, Set<UUID> rewarded, int revision) {}
    public record ContributionResult(ProjectSnapshot project, int applied, boolean completedNow) {}
    public record ClaimResult(UUID guildId, GuildTownSharedProject type) {}
    private record RewardEntitlement(UUID projectId, UUID guildId, GuildTownSharedProject type, int sequence) {}

    private static final class MutableProject {
        private final UUID projectId;
        private final GuildTownSharedProject type;
        private int progress;
        private int revision;
        private final Set<UUID> participants = new HashSet<>();
        private final Set<UUID> deliveryTokens = new HashSet<>();

        private MutableProject(UUID projectId, GuildTownSharedProject type) { this.projectId = projectId; this.type = type; }
        private ProjectSnapshot snapshot() { return snapshot(false); }
        private ProjectSnapshot snapshot(boolean completed) {
            return new ProjectSnapshot(type, progress, completed,
                    Collections.unmodifiableSet(new HashSet<>(participants)), Set.of(), revision);
        }
    }
}
