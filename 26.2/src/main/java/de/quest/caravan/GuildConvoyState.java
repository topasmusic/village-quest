package de.quest.caravan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Guild-wide relay convoy state. Saved connection IDs survive route-slot compaction. */
public final class GuildConvoyState extends SavedData {
    private static final String ID = "village_quest_guild_convoys";
    private static final long COOLDOWN_TICKS = 7L * 24_000L;
    private static final int MAX_GUILDS = 1_024;

    public static final SavedDataType<GuildConvoyState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID), GuildConvoyState::new,
            CompoundTag.CODEC.xmap(GuildConvoyState::fromNbt, GuildConvoyState::toNbt), DataFixTypes.LEVEL);

    private final Map<UUID, Convoy> active = new HashMap<>();
    private final Map<UUID, Long> lastStarted = new HashMap<>();
    private final Map<UUID, Map<UUID, Set<VillageKey>>> trustArrivals = new HashMap<>();
    private final Map<UUID, TrustCompletion> pendingTrust = new HashMap<>();
    private final Map<UUID, Set<UUID>> disqualifiedTrust = new HashMap<>();

    public boolean disqualifyTrustFor(UUID player) {
        if (player == null) return false;
        boolean changed = false;
        for (Convoy convoy : active.values()) {
            if (!convoy.participants().contains(player)) continue;
            changed |= disqualifiedTrust.computeIfAbsent(convoy.id(), ignored -> new HashSet<>()).add(player);
            var witnessed = trustArrivals.get(convoy.id());
            if (witnessed != null) changed |= witnessed.remove(player) != null;
        }
        if (changed) setDirty(); return changed;
    }

    public record TrustCompletion(UUID convoyId, Map<UUID, Set<VillageKey>> beneficiaries, long resetToken) {
        public TrustCompletion {
            Map<UUID, Set<VillageKey>> snapshot = new HashMap<>();
            beneficiaries.forEach((player, villages) -> snapshot.put(player, Set.copyOf(villages)));
            beneficiaries = Map.copyOf(snapshot);
        }
    }

    public List<TrustCompletion> pendingTrust() { return List.copyOf(pendingTrust.values()); }

    /** Caller acknowledges only after the recipient QuestState has been durably saved. */
    public boolean acknowledgeTrust(UUID convoyId) {
        if (pendingTrust.remove(convoyId) == null) return false;
        setDirty(); return true;
    }

    public boolean recordTrustArrival(UUID guildId, UUID player, UUID connection, VillageKey village) {
        Convoy convoy = active(guildId);
        if (convoy == null || player == null || village == null || !convoy.headingToVillage()
                || !convoy.currentConnection().equals(connection) || !convoy.participants().contains(player)
                || disqualifiedTrust.getOrDefault(convoy.id(), Set.of()).contains(player)) return false;
        boolean added = trustArrivals.computeIfAbsent(convoy.id(), ignored -> new HashMap<>())
                .computeIfAbsent(player, ignored -> new HashSet<>()).add(village);
        if (added) setDirty();
        return added;
    }

    GuildConvoyState() {}

    public static GuildConvoyState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public record Convoy(UUID id, UUID guildId, UUID ownerId, List<UUID> connections,
                         int phase, Set<UUID> participants, Set<UUID> credited, long startedTick) {
        public Convoy {
            if (id == null || guildId == null || ownerId == null || connections == null
                    || connections.size() != 3 || new HashSet<>(connections).size() != 3
                    || connections.stream().anyMatch(Objects::isNull) || phase < 0 || phase > 8
                    || participants == null || participants.isEmpty()
                    || participants.stream().anyMatch(Objects::isNull)
                    || credited == null || credited.stream().anyMatch(Objects::isNull)
                    || !participants.containsAll(credited) || !credited.contains(ownerId)
                    || startedTick < 0) {
                throw new IllegalArgumentException("invalid guild convoy");
            }
            connections = List.copyOf(connections);
            participants = Set.copyOf(participants);
            credited = Set.copyOf(credited);
        }

        public UUID currentConnection() { return connections.get(phase / 3); }
        public int stopNumber() { return phase / 3 + 1; }
        public boolean headingToVillage() { return phase % 3 == 1; }
    }

    public record AdvanceResult(boolean advanced, boolean completed, Convoy convoy) {
        static AdvanceResult unchanged() { return new AdvanceResult(false, false, null); }
    }

    public Convoy active(UUID guildId) { return active.get(guildId); }

    public Convoy forOwner(UUID ownerId) {
        if (ownerId == null) return null;
        for (Convoy convoy : active.values()) if (ownerId.equals(convoy.ownerId())) return convoy;
        return null;
    }

    public boolean start(UUID guildId, UUID ownerId, List<UUID> connections, long tick) {
        if (guildId == null || ownerId == null || connections == null || connections.size() != 3
                || connections.stream().anyMatch(Objects::isNull)
                || new HashSet<>(connections).size() != 3
                || active.containsKey(guildId) || active.size() >= MAX_GUILDS || tick < 0) return false;
        long previous = lastStarted.getOrDefault(guildId, -COOLDOWN_TICKS);
        if (tick < previous || tick - previous < COOLDOWN_TICKS) return false;
        // Completed guilds no longer need expired cooldowns; live cooldowns must survive reload.
        lastStarted.entrySet().removeIf(entry -> tick >= entry.getValue()
                && tick - entry.getValue() >= COOLDOWN_TICKS);
        active.put(guildId, new Convoy(UUID.randomUUID(), guildId, ownerId, connections,
                0, Set.of(ownerId), Set.of(ownerId), tick));
        lastStarted.put(guildId, tick);
        setDirty();
        return true;
    }

    public boolean join(UUID guildId, UUID memberId) {
        Convoy convoy = active(guildId);
        if (convoy == null || memberId == null || convoy.participants().contains(memberId)
                || convoy.participants().size() >= 64) return false;
        Set<UUID> next = new HashSet<>(convoy.participants());
        next.add(memberId);
        active.put(guildId, new Convoy(convoy.id(), convoy.guildId(), convoy.ownerId(),
                convoy.connections(), convoy.phase(), next, convoy.credited(), convoy.startedTick()));
        setDirty();
        return true;
    }

    /** A signed-up member earns escort credit at a real village arrival witnessed in person. */
    public boolean credit(UUID guildId, UUID memberId) {
        Convoy convoy = active(guildId);
        if (convoy == null || memberId == null || !convoy.participants().contains(memberId)
                || convoy.credited().contains(memberId)) return false;
        Set<UUID> next = new HashSet<>(convoy.credited());
        next.add(memberId);
        active.put(guildId, new Convoy(convoy.id(), convoy.guildId(), convoy.ownerId(),
                convoy.connections(), convoy.phase(), convoy.participants(), next, convoy.startedTick()));
        setDirty();
        return true;
    }

    public AdvanceResult arrive(UUID ownerId, UUID connection, boolean atVillage) {
        return arrive(ownerId, connection, atVillage, de.quest.util.TimeUtil.currentDay());
    }
    public AdvanceResult arrive(UUID ownerId, UUID connection, boolean atVillage, long resetToken) {
        Convoy convoy = forOwner(ownerId);
        if (convoy == null || connection == null || !connection.equals(convoy.currentConnection())
                || atVillage != convoy.headingToVillage()) return AdvanceResult.unchanged();
        if (convoy.phase() == 8) {
            active.remove(convoy.guildId());
            Map<UUID, Set<VillageKey>> witnessed = trustArrivals.remove(convoy.id());
            disqualifiedTrust.remove(convoy.id());
            if (witnessed != null && !witnessed.isEmpty()) pendingTrust.put(convoy.id(), new TrustCompletion(convoy.id(), witnessed, resetToken));
            setDirty();
            return new AdvanceResult(true, true, convoy);
        }
        Convoy advanced = new Convoy(convoy.id(), convoy.guildId(), convoy.ownerId(),
                convoy.connections(), convoy.phase() + 1, convoy.participants(),
                convoy.credited(), convoy.startedTick());
        active.put(convoy.guildId(), advanced);
        setDirty();
        return new AdvanceResult(true, false, advanced);
    }

    public Convoy cancelForRemovedRoute(UUID ownerId, UUID connection) {
        Convoy convoy = forOwner(ownerId);
        if (convoy == null || connection == null || !convoy.connections().contains(connection)) return null;
        active.remove(convoy.guildId());
        trustArrivals.remove(convoy.id());
        disqualifiedTrust.remove(convoy.id());
        setDirty();
        return convoy;
    }

    public Convoy cancelGuild(UUID guildId) {
        Convoy convoy = active.remove(guildId);
        if (convoy != null) { trustArrivals.remove(convoy.id()); disqualifiedTrust.remove(convoy.id()); }
        if (convoy != null) setDirty();
        return convoy;
    }

    static GuildConvoyState fromNbt(CompoundTag root) {
        GuildConvoyState state = new GuildConvoyState();
        if (root.getIntOr("version", 0) != 1) return state;
        ListTag cooldown = root.getListOrEmpty("cooldown");
        for (int i = 0; i < cooldown.size(); i++) {
            CompoundTag entry = cooldown.getCompoundOrEmpty(i);
            UUID guild = uuid(entry.getStringOr("guild", ""));
            long tick = entry.getLongOr("tick", -1);
            if (guild != null && tick >= 0) state.lastStarted.putIfAbsent(guild, tick);
        }
        ListTag entries = root.getListOrEmpty("active");
        for (int i = 0; i < Math.min(MAX_GUILDS, entries.size()); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            UUID id = uuid(entry.getStringOr("id", ""));
            UUID guild = uuid(entry.getStringOr("guild", ""));
            UUID owner = uuid(entry.getStringOr("owner", ""));
            List<UUID> routes = new ArrayList<>();
            for (int slot = 0; slot < 3; slot++) routes.add(uuid(entry.getStringOr("route" + slot, "")));
            Set<UUID> participants = new HashSet<>();
            ListTag names = entry.getListOrEmpty("participants");
            for (int member = 0; member < Math.min(64, names.size()); member++) {
                UUID player = uuid(names.getCompoundOrEmpty(member).getStringOr("id", ""));
                if (player != null) participants.add(player);
            }
            Set<UUID> credited = new HashSet<>();
            credited.add(owner);
            ListTag credits = entry.getListOrEmpty("credited");
            for (int member = 0; member < Math.min(64, credits.size()); member++) {
                UUID player = uuid(credits.getCompoundOrEmpty(member).getStringOr("id", ""));
                if (player != null && participants.contains(player)) credited.add(player);
            }
            try {
                Convoy convoy = new Convoy(id, guild, owner, routes,
                        entry.getIntOr("phase", -1), participants, credited,
                        entry.getLongOr("started", -1));
                state.active.putIfAbsent(guild, convoy);
                state.lastStarted.putIfAbsent(guild, convoy.startedTick());
            } catch (IllegalArgumentException ignored) {
                // Corrupt optional convoy metadata must not prevent loading the world.
            }
        }
        readTrustEntries(root.getListOrEmpty("trustArrivals"), state.trustArrivals);
        Map<UUID, Map<UUID, Set<VillageKey>>> completions = new HashMap<>();
        readTrustEntries(root.getListOrEmpty("pendingTrust"), completions);
        Map<UUID, Long> completionResets = new HashMap<>();
        for (var raw : root.getListOrEmpty("pendingTrust")) if (raw instanceof CompoundTag entry) {
            UUID id = uuid(entry.getStringOr("id", ""));
            if (id != null && entry.get("resetToken") instanceof net.minecraft.nbt.LongTag)
                completionResets.put(id, entry.getLongOr("resetToken", Long.MIN_VALUE));
        }
        completions.forEach((id, beneficiaries) -> state.pendingTrust.put(id, new TrustCompletion(id, beneficiaries, completionResets.getOrDefault(id, Long.MIN_VALUE))));
        state.trustArrivals.keySet().removeIf(id -> state.active.values().stream().noneMatch(convoy -> convoy.id().equals(id)));
        for (var raw : root.getListOrEmpty("disqualifiedTrust")) {
            if (!(raw instanceof CompoundTag entry)) continue;
            UUID convoyId = uuid(entry.getStringOr("convoy", "")), player = uuid(entry.getStringOr("player", ""));
            if (convoyId == null || player == null || state.active.values().stream()
                    .noneMatch(convoy -> convoy.id().equals(convoyId) && convoy.participants().contains(player))) continue;
            state.disqualifiedTrust.computeIfAbsent(convoyId, ignored -> new HashSet<>()).add(player);
            var witnessed = state.trustArrivals.get(convoyId); if (witnessed != null) witnessed.remove(player);
        }
        return state;
    }

    static CompoundTag toNbt(GuildConvoyState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag cooldown = new ListTag();
        for (Map.Entry<UUID, Long> saved : state.lastStarted.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("guild", saved.getKey().toString());
            entry.putLong("tick", saved.getValue());
            cooldown.add(entry);
        }
        root.put("cooldown", cooldown);
        ListTag entries = new ListTag();
        for (Convoy convoy : state.active.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", convoy.id().toString());
            entry.putString("guild", convoy.guildId().toString());
            entry.putString("owner", convoy.ownerId().toString());
            for (int slot = 0; slot < 3; slot++) {
                entry.putString("route" + slot, convoy.connections().get(slot).toString());
            }
            entry.putInt("phase", convoy.phase());
            entry.putLong("started", convoy.startedTick());
            ListTag participants = new ListTag();
            for (UUID member : convoy.participants()) {
                CompoundTag person = new CompoundTag();
                person.putString("id", member.toString());
                participants.add(person);
            }
            entry.put("participants", participants);
            ListTag credited = new ListTag();
            for (UUID member : convoy.credited()) {
                CompoundTag person = new CompoundTag();
                person.putString("id", member.toString());
                credited.add(person);
            }
            entry.put("credited", credited);
            entries.add(entry);
        }
        root.put("active", entries);
        root.put("trustArrivals", writeTrustEntries(state.trustArrivals));
        Map<UUID, Map<UUID, Set<VillageKey>>> completions = new HashMap<>();
        state.pendingTrust.forEach((id, completion) -> completions.put(id, completion.beneficiaries()));
        ListTag savedCompletions = writeTrustEntries(completions);
        for (var raw : savedCompletions) if (raw instanceof CompoundTag entry) {
            TrustCompletion completion = state.pendingTrust.get(uuid(entry.getStringOr("id", "")));
            if (completion != null) entry.putLong("resetToken", completion.resetToken());
        }
        root.put("pendingTrust", savedCompletions);
        ListTag barred = new ListTag();
        state.disqualifiedTrust.forEach((convoy, players) -> players.forEach(player -> {
            CompoundTag tag = new CompoundTag(); tag.putString("convoy", convoy.toString()); tag.putString("player", player.toString()); barred.add(tag);
        })); root.put("disqualifiedTrust", barred);
        return root;
    }

    private static ListTag writeTrustEntries(Map<UUID, Map<UUID, Set<VillageKey>>> entries) {
        ListTag list = new ListTag();
        entries.forEach((convoy, players) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("id", convoy.toString());
            ListTag beneficiaries = new ListTag();
            players.forEach((player, villages) -> {
                CompoundTag person = new CompoundTag(); person.putString("player", player.toString());
                ListTag places = new ListTag();
                villages.stream().sorted().forEach(village -> {
                    CompoundTag place = new CompoundTag(); place.putString("dimension", village.dimension());
                    place.putInt("x", village.anchorX()); place.putInt("z", village.anchorZ()); places.add(place);
                });
                person.put("villages", places); beneficiaries.add(person);
            });
            entry.put("players", beneficiaries); list.add(entry);
        });
        return list;
    }

    private static void readTrustEntries(ListTag list, Map<UUID, Map<UUID, Set<VillageKey>>> target) {
        for (var raw : list) {
            if (!(raw instanceof CompoundTag entry)) continue;
            UUID id = uuid(entry.getStringOr("id", "")); if (id == null) continue;
            Map<UUID, Set<VillageKey>> players = new HashMap<>();
            ListTag people = entry.getListOrEmpty("players");
            for (int p = 0; p < Math.min(64, people.size()); p++) {
                CompoundTag person = people.getCompoundOrEmpty(p);
                UUID player = uuid(person.getStringOr("player", "")); if (player == null) continue;
                Set<VillageKey> villages = new HashSet<>(); ListTag places = person.getListOrEmpty("villages");
                for (int v = 0; v < Math.min(3, places.size()); v++) {
                    CompoundTag place = places.getCompoundOrEmpty(v);
                    try { villages.add(new VillageKey(place.getStringOr("dimension", ""), place.getIntOr("x", 0), place.getIntOr("z", 0))); }
                    catch (IllegalArgumentException corrupt) { /* Optional invalid target has no reward. */ }
                }
                if (!villages.isEmpty()) players.put(player, villages);
            }
            if (!players.isEmpty()) target.put(id, players);
        }
    }

    private static UUID uuid(String raw) {
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
