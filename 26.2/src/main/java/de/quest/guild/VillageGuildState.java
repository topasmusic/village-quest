package de.quest.guild;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Optional shared guild layer. Personal stories, archives, and unique tools are never stored here. */
public final class VillageGuildState extends SavedData {
    static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String ID = "village_quest_guilds";

    public static final SavedDataType<VillageGuildState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID), VillageGuildState::new,
            CompoundTag.CODEC.xmap(VillageGuildState::fromNbt, VillageGuildState::toNbt), DataFixTypes.LEVEL);

    private final Map<UUID, MutableGuild> guilds = new HashMap<>();
    private final Map<UUID, UUID> memberGuilds = new HashMap<>();
    private final Map<UUID, UUID> invitations = new HashMap<>();

    VillageGuildState() {}

    public static VillageGuildState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<GuildSnapshot> guildFor(UUID playerId) {
        UUID guildId = playerId == null ? null : memberGuilds.get(playerId);
        MutableGuild guild = guildId == null ? null : guilds.get(guildId);
        return guild == null ? Optional.empty() : Optional.of(guild.snapshot());
    }

    public Optional<UUID> invitationFor(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : invitations.get(playerId));
    }

    public GuildSnapshot create(UUID leaderId, String name) {
        if (leaderId == null || memberGuilds.containsKey(leaderId)) return null;
        UUID guildId = UUID.randomUUID();
        MutableGuild guild = new MutableGuild(guildId, name, 0, VillageGuildProject.NONE, 0);
        guild.members.put(leaderId, VillageGuildRole.LEADER);
        guilds.put(guildId, guild);
        memberGuilds.put(leaderId, guildId);
        setDirty();
        return guild.snapshot();
    }

    public boolean invite(UUID actorId, UUID targetId) {
        MutableGuild guild = mutableGuildFor(actorId);
        if (guild == null || targetId == null || memberGuilds.containsKey(targetId)
                || !guild.members.getOrDefault(actorId, VillageGuildRole.MEMBER).canInvite()) return false;
        invitations.put(targetId, guild.id);
        setDirty();
        return true;
    }

    public GuildSnapshot accept(UUID playerId) {
        if (playerId == null || memberGuilds.containsKey(playerId)) return null;
        UUID guildId = invitations.remove(playerId);
        MutableGuild guild = guildId == null ? null : guilds.get(guildId);
        if (guild == null) return null;
        guild.members.put(playerId, VillageGuildRole.MEMBER);
        memberGuilds.put(playerId, guild.id);
        guild.revision++;
        setDirty();
        return guild.snapshot();
    }

    public boolean leave(UUID playerId) {
        MutableGuild guild = mutableGuildFor(playerId);
        if (guild == null) return false;
        VillageGuildRole role = guild.members.get(playerId);
        if (role == VillageGuildRole.LEADER && guild.members.size() > 1) return false;
        guild.members.remove(playerId);
        memberGuilds.remove(playerId);
        invitations.remove(playerId);
        if (guild.members.isEmpty()) {
            guilds.remove(guild.id);
            invitations.entrySet().removeIf(entry -> entry.getValue().equals(guild.id));
        } else {
            guild.revision++;
        }
        setDirty();
        return true;
    }

    public boolean promote(UUID actorId, UUID targetId) {
        MutableGuild guild = mutableGuildFor(actorId);
        if (guild == null || targetId == null || !guild.members.containsKey(targetId)
                || !guild.members.get(actorId).canPromote() || actorId.equals(targetId)) return false;
        VillageGuildRole targetRole = guild.members.get(targetId);
        if (targetRole == VillageGuildRole.LEADER) return false;
        guild.members.put(targetId, VillageGuildRole.STEWARD);
        guild.revision++;
        setDirty();
        return true;
    }

    public boolean transferLeadership(UUID actorId, UUID targetId) {
        MutableGuild guild = mutableGuildFor(actorId);
        if (guild == null || targetId == null || actorId.equals(targetId)
                || guild.members.get(actorId) != VillageGuildRole.LEADER
                || !guild.members.containsKey(targetId)) return false;
        guild.members.put(actorId, VillageGuildRole.STEWARD);
        guild.members.put(targetId, VillageGuildRole.LEADER);
        guild.revision++;
        setDirty();
        return true;
    }

    public boolean kick(UUID actorId, UUID targetId) {
        MutableGuild guild = mutableGuildFor(actorId);
        if (guild == null || targetId == null || actorId.equals(targetId)
                || guild.members.get(actorId) != VillageGuildRole.LEADER
                || !guild.members.containsKey(targetId)) return false;
        guild.members.remove(targetId);
        memberGuilds.remove(targetId);
        invitations.remove(targetId);
        guild.revision++;
        setDirty();
        return true;
    }

    public boolean selectProject(UUID actorId, VillageGuildProject project) {
        MutableGuild guild = mutableGuildFor(actorId);
        if (guild == null || project == null || project == VillageGuildProject.NONE
                || !guild.members.get(actorId).canChooseProject() || guild.renown < 75) return false;
        guild.project = project;
        guild.revision++;
        setDirty();
        return true;
    }

    public GuildSnapshot addRenown(UUID memberId, int amount) {
        MutableGuild guild = mutableGuildFor(memberId);
        if (guild == null || amount <= 0) return guild == null ? null : guild.snapshot();
        guild.renown = Math.min(1_000_000, guild.renown + amount);
        guild.revision++;
        setDirty();
        return guild.snapshot();
    }

    public int removePlayer(UUID playerId) {
        if (playerId == null) return 0;
        invitations.remove(playerId);
        boolean removed = leave(playerId);
        if (!removed) {
            MutableGuild guild = mutableGuildFor(playerId);
            if (guild != null && guild.members.get(playerId) == VillageGuildRole.LEADER) {
                for (UUID member : guild.members.keySet()) memberGuilds.remove(member);
                guilds.remove(guild.id);
                invitations.entrySet().removeIf(entry -> entry.getValue().equals(guild.id));
                setDirty();
                return 1;
            }
        }
        return removed ? 1 : 0;
    }

    public void resetAllProgress() {
        if (guilds.isEmpty() && invitations.isEmpty()) return;
        guilds.clear(); memberGuilds.clear(); invitations.clear(); setDirty();
    }

    private MutableGuild mutableGuildFor(UUID playerId) {
        UUID guildId = playerId == null ? null : memberGuilds.get(playerId);
        return guildId == null ? null : guilds.get(guildId);
    }

    static VillageGuildState fromNbt(CompoundTag root) {
        VillageGuildState state = new VillageGuildState();
        if (root == null || root.isEmpty()) return state;
        for (CompoundTag entry : compounds(root.getListOrEmpty("guilds"))) {
            UUID id = parseUuid(entry.getStringOr("id", ""));
            if (id == null) continue;
            MutableGuild guild = new MutableGuild(id, sanitizeName(entry.getStringOr("name", "Guild")),
                    Math.max(0, entry.getIntOr("renown", 0)),
                    VillageGuildProject.byId(entry.getIntOr("project", 0)),
                    Math.max(0, entry.getIntOr("revision", 0)));
            for (CompoundTag member : compounds(entry.getListOrEmpty("members"))) {
                UUID memberId = parseUuid(member.getStringOr("id", ""));
                if (memberId == null || state.memberGuilds.containsKey(memberId)) continue;
                VillageGuildRole role = VillageGuildRole.byId(member.getIntOr("role", 0));
                guild.members.put(memberId, role);
                state.memberGuilds.put(memberId, id);
            }
            if (!guild.members.isEmpty()) state.guilds.put(id, guild);
        }
        for (CompoundTag entry : compounds(root.getListOrEmpty("invitations"))) {
            UUID player = parseUuid(entry.getStringOr("player", ""));
            UUID guild = parseUuid(entry.getStringOr("guild", ""));
            if (player != null && guild != null && state.guilds.containsKey(guild)
                    && !state.memberGuilds.containsKey(player)) state.invitations.put(player, guild);
        }
        return state;
    }

    static CompoundTag toNbt(VillageGuildState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", CURRENT_SCHEMA_VERSION);
        ListTag guildEntries = new ListTag();
        state.guilds.values().stream().sorted((a, b) -> a.id.toString().compareTo(b.id.toString())).forEach(guild -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", guild.id.toString()); entry.putString("name", guild.name);
            entry.putInt("renown", guild.renown); entry.putInt("project", guild.project.id());
            entry.putInt("revision", guild.revision);
            ListTag members = new ListTag();
            guild.members.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    (left, right) -> left.toString().compareTo(right.toString()))).forEach(member -> {
                CompoundTag memberEntry = new CompoundTag();
                memberEntry.putString("id", member.getKey().toString());
                memberEntry.putInt("role", member.getValue().id()); members.add(memberEntry);
            });
            entry.put("members", members); guildEntries.add(entry);
        });
        root.put("guilds", guildEntries);
        ListTag invites = new ListTag();
        state.invitations.forEach((player, guild) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("player", player.toString());
            entry.putString("guild", guild.toString()); invites.add(entry);
        });
        root.put("invitations", invites);
        return root;
    }

    private static Iterable<CompoundTag> compounds(ListTag list) {
        java.util.List<CompoundTag> result = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) result.add(list.getCompoundOrEmpty(i));
        return result;
    }

    static String sanitizeName(String raw) {
        String clean = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}§]", "").trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) return "Guild";
        return clean.length() <= 24 ? clean : clean.substring(0, 24).trim();
    }

    private static UUID parseUuid(String raw) {
        try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public record GuildSnapshot(UUID id, String name, int renown, int rank, int nextRankThreshold,
                                VillageGuildProject project, Map<UUID, VillageGuildRole> members, int revision) {
        public VillageGuildRole role(UUID playerId) { return members.getOrDefault(playerId, VillageGuildRole.MEMBER); }
    }

    public static int guildRank(int renown) {
        int safe = Math.max(0, renown);
        if (safe >= 600) return 4;
        if (safe >= 250) return 3;
        if (safe >= 75) return 2;
        return 1;
    }

    public static int nextRankThreshold(int renown) {
        return switch (guildRank(renown)) { case 1 -> 75; case 2 -> 250; case 3 -> 600; default -> 0; };
    }

    private static final class MutableGuild {
        private final UUID id;
        private String name;
        private int renown;
        private VillageGuildProject project;
        private int revision;
        private final Map<UUID, VillageGuildRole> members = new HashMap<>();
        private MutableGuild(UUID id, String name, int renown, VillageGuildProject project, int revision) {
            this.id = id; this.name = sanitizeName(name); this.renown = renown;
            this.project = project == null ? VillageGuildProject.NONE : project; this.revision = revision;
        }
        private GuildSnapshot snapshot() {
            return new GuildSnapshot(id, name, renown, guildRank(renown), nextRankThreshold(renown),
                    project, Collections.unmodifiableMap(new HashMap<>(members)), revision);
        }
    }
}
