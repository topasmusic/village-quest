package de.quest.reputation;

import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Owned by PlayerQuestData; mutations are applied only on the server thread. */
public final class SocialReputationData {
    public static final int SCHEMA = 1;
    private int guildTrust;
    private final Map<VillageKey, Integer> localTrust = new HashMap<>();
    private int probationRemaining;
    private long revision;
    private ReparationCase activeCase;
    private final Set<String> receipts = new HashSet<>();
    private long resetToken = Long.MIN_VALUE;
    private int guildUsed;
    private final Map<VillageKey, Integer> localUsed = new HashMap<>();
    private final Map<BenefitKind, Integer> sourceUsed = new EnumMap<>(BenefitKind.class);
    private final List<HistoryEntry> history = new ArrayList<>();
    private final Map<VillageKey, SupportContribution> support = new HashMap<>();
    private long warningTick = -1;
    private final Map<UUID, AttackRecord> attacks = new HashMap<>();
    private final Set<UUID> fatalVictims = new HashSet<>();

    public record AttackRecord(UUID id, VillageKey village, long startedTick, int guildDelta, int localDelta, boolean sanctioned) {
        public AttackRecord(UUID id, VillageKey village, long startedTick, int guildDelta, int localDelta) { this(id, village, startedTick, guildDelta, localDelta, true); }
        public AttackRecord { Objects.requireNonNull(id); }
    }
    long warningTick() { return warningTick; }
    void warnAt(long tick) { if (writable()) { warningTick = tick; revision++; } }
    AttackRecord attack(UUID victim, long tick) {
        AttackRecord previous = attacks.get(victim);
        return previous != null && tick >= previous.startedTick() && tick - previous.startedTick() <= 1_200 ? previous : null;
    }
    public boolean traderWarningActive(UUID victim, long tick) {
        AttackRecord previous = attacks.get(victim);
        return previous != null && !previous.sanctioned() && tick >= previous.startedTick() && tick - previous.startedTick() < 600;
    }
    boolean hasFatalVictim(UUID victim) { return fatalVictims.contains(victim); }
    void recordAttack(UUID victim, AttackRecord attack, boolean fatal, long tick) {
        if (!writable()) return;
        attacks.entrySet().removeIf(entry -> tick > entry.getValue().startedTick() + 1_200);
        if (fatal) { fatalVictims.add(victim); attacks.remove(victim); }
        else attacks.put(victim, attack);
        revision++;
    }
    /** Unknown/corrupt optional data is preserved verbatim, never reset into reward eligibility. */
    private CompoundTag preserved;

    public record SupportContribution(UUID id, int cycle, boolean supplied, boolean resolved, boolean barred) {
        public SupportContribution { Objects.requireNonNull(id); if (cycle <= 0) throw new IllegalArgumentException("Invalid support cycle"); }
    }
    public Map<VillageKey, SupportContribution> supportView() { return Map.copyOf(support); }
    void putSupport(VillageKey village, SupportContribution contribution) {
        if (!writable()) return;
        support.put(Objects.requireNonNull(village), Objects.requireNonNull(contribution)); revision++;
    }

    public record HistoryEntry(UUID id, String type, int guildBefore, int guildAfter,
                               Map<VillageKey, Integer> localDeltas, long tick) {
        public HistoryEntry {
            Objects.requireNonNull(id); Objects.requireNonNull(type);
            localDeltas = Map.copyOf(localDeltas);
        }
        public int guildDelta() { return guildAfter - guildBefore; }
    }

    public record ReparationCase(UUID id, boolean major, Set<VillageKey> affected, int onlineTicksRemaining,
                                 MaterialOption selected, Map<MaterialOption, Integer> aid, long lastOffenceTick, Offence offence) {
        public ReparationCase {
            Objects.requireNonNull(id); Objects.requireNonNull(selected); Objects.requireNonNull(offence);
            affected = Set.copyOf(affected); aid = Map.copyOf(aid);
        }
        public int requiredDeliveries() { return major ? 3 : 1; }
        public int required(MaterialOption material) {
            return major || material == selected ? SocialReputationRules.aidRequired(major, material) : 0;
        }
        public int delivered(MaterialOption material) { return aid.getOrDefault(material, 0); }
        public boolean aidComplete() {
            for (MaterialOption material : MaterialOption.values()) if (delivered(material) < required(material)) return false;
            return true;
        }
    }

    public int guildTrust() { return guildTrust; }
    public int localTrust(VillageKey key) { return localTrust.getOrDefault(key, 0); }
    public Map<VillageKey, Integer> localTrustView() { return Map.copyOf(localTrust); }
    public int probationRemaining() { return probationRemaining; }
    public long revision() { return revision; }
    public ReparationCase activeCase() { return activeCase; }
    public boolean writable() { return preserved == null; }
    public List<HistoryEntry> history() { return List.copyOf(history); }
    long resetToken() { return resetToken; }
    int guildUsed() { return guildUsed; }
    int localUsed(VillageKey village) { return localUsed.getOrDefault(village, 0); }
    int sourceUsed(BenefitKind kind) { return sourceUsed.getOrDefault(kind, 0); }

    void beginReset(long token) {
        if (!writable() || token <= resetToken) return;
        resetToken = token; guildUsed = 0; localUsed.clear(); sourceUsed.clear(); revision++;
    }
    void accountRepeatable(BenefitKind kind, int guild, Map<VillageKey, Integer> local) {
        if (!writable()) return;
        guildUsed += guild;
        local.forEach((key, value) -> localUsed.merge(key, value, Integer::sum));
        if (guild > 0 || local.values().stream().anyMatch(value -> value > 0)) sourceUsed.merge(kind, 1, Integer::sum);
        revision++;
    }
    void appendHistory(HistoryEntry entry) {
        if (!writable()) return;
        history.add(entry);
        if (history.size() > 32) history.removeFirst();
        revision++;
    }
    public void appendAdministrativeHistory(UUID id, int guildBefore, VillageKey village, int localBefore, long tick) {
        appendHistory(new HistoryEntry(id, village == null ? "admin.guild" : "admin.village", guildBefore, guildTrust,
                village == null ? Map.of() : Map.of(village, localTrust(village) - localBefore), Math.max(0, tick)));
    }

    public void setGuildTrust(int value) {
        if (!writable()) return;
        int next = SocialReputationRules.clamp(value);
        if (guildTrust != next) { guildTrust = next; revision++; }
    }
    public void setLocalTrust(VillageKey key, int value) {
        Objects.requireNonNull(key);
        if (!writable()) return;
        int next = SocialReputationRules.clamp(value);
        if (localTrust(key) != next) { localTrust.put(key, next); revision++; }
    }
    public void setProbationRemaining(int value) {
        if (!writable()) return;
        int next = Math.clamp(value, 0, 2);
        if (probationRemaining != next) { probationRemaining = next; revision++; }
    }

    public boolean markReceipt(BenefitKind kind, UUID source) {
        Objects.requireNonNull(kind); Objects.requireNonNull(source);
        if (!writable() || !receipts.add(kind.name() + ":" + source)) return false;
        revision++; return true;
    }
    public boolean hasReceipt(BenefitKind kind, UUID source) { return receipts.contains(kind.name() + ":" + source); }

    public void openCase(UUID id, Offence offence, VillageKey village, long tick) {
        Objects.requireNonNull(id); Objects.requireNonNull(offence);
        if (!writable()) return;
        boolean major = offence != Offence.ASSAULT || activeCase != null && activeCase.major();
        Set<VillageKey> affected = activeCase == null ? new HashSet<>() : new HashSet<>(activeCase.affected());
        if (village != null) affected.add(village);
        if (village != null) {
            SupportContribution contribution = support.get(village);
            if (contribution != null && !contribution.resolved())
                putSupport(village, new SupportContribution(contribution.id(), contribution.cycle(), contribution.supplied(), false, true));
        }
        Offence cause = activeCase != null && activeCase.major() && offence == Offence.ASSAULT ? activeCase.offence() : offence;
        activeCase = new ReparationCase(activeCase == null ? id : activeCase.id(), major, affected,
                major ? SocialReputationRules.MAJOR_ONLINE_TICKS : SocialReputationRules.MINOR_ONLINE_TICKS,
                MaterialOption.WHEAT, Map.of(), Math.max(0, tick), cause);
        probationRemaining = 0; revision++;
    }

    /** Returns the accepted quantity; callers must validate/consume real inventory for exactly this quantity. */
    public int recordAid(MaterialOption material, int offered) {
        Objects.requireNonNull(material);
        if (!writable() || activeCase == null || offered <= 0) return 0;
        int accepted = Math.min(offered, activeCase.required(material) - activeCase.delivered(material));
        if (accepted <= 0) return 0;
        Map<MaterialOption, Integer> aid = new EnumMap<>(MaterialOption.class); aid.putAll(activeCase.aid());
        aid.put(material, activeCase.delivered(material) + accepted);
        activeCase = new ReparationCase(activeCase.id(), activeCase.major(), activeCase.affected(),
                activeCase.onlineTicksRemaining(), activeCase.selected(), aid, activeCase.lastOffenceTick(), activeCase.offence());
        revision++; return accepted;
    }
    public void advanceOnlineTicks(int ticks) {
        if (!writable() || activeCase == null || ticks <= 0) return;
        int remaining = Math.max(0, activeCase.onlineTicksRemaining() - ticks);
        if (remaining == activeCase.onlineTicksRemaining()) return;
        activeCase = new ReparationCase(activeCase.id(), activeCase.major(), activeCase.affected(),
                remaining, activeCase.selected(), activeCase.aid(), activeCase.lastOffenceTick(), activeCase.offence());
        // Timer-only progress does not invalidate a material action that is in flight.
    }
    boolean selectAid(MaterialOption material) {
        if (!writable() || activeCase == null || activeCase.major() || activeCase.aid().values().stream().anyMatch(count -> count > 0)) return false;
        if (activeCase.selected() == material) return true;
        activeCase = new ReparationCase(activeCase.id(), false, activeCase.affected(), activeCase.onlineTicksRemaining(),
                material, Map.of(), activeCase.lastOffenceTick(), activeCase.offence()); revision++; return true;
    }
    void closeCase() {
        if (!writable() || activeCase == null) return;
        setGuildTrust(Math.max(-10, guildTrust));
        for (VillageKey key : activeCase.affected()) setLocalTrust(key, Math.max(-10, localTrust(key)));
        activeCase = null; setProbationRemaining(2); revision++;
    }

    public CompoundTag toNbt() {
        if (!writable()) return preserved.copy();
        CompoundTag root = new CompoundTag();
        root.putInt("schema", SCHEMA); root.putInt("guildTrust", guildTrust);
        root.putInt("probation", probationRemaining); root.putLong("revision", revision);
        ListTag villages = new ListTag();
        localTrust.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag tag = villageTag(entry.getKey()); tag.putInt("value", entry.getValue()); villages.add(tag);
        });
        root.put("villages", villages);
        ListTag awarded = new ListTag(); receipts.stream().sorted().forEach(receipt -> awarded.add(StringTag.valueOf(receipt)));
        root.put("receipts", awarded);
        root.putLong("resetToken", resetToken); root.putInt("guildUsed", guildUsed);
        ListTag localCounters = new ListTag();
        localUsed.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag tag = villageTag(entry.getKey()); tag.putInt("value", entry.getValue()); localCounters.add(tag);
        });
        root.put("localUsed", localCounters);
        CompoundTag sourceCounters = new CompoundTag(); sourceUsed.forEach((kind, count) -> sourceCounters.putInt(kind.name(), count));
        root.put("sourceUsed", sourceCounters);
        ListTag recent = new ListTag();
        for (HistoryEntry entry : history) {
            CompoundTag tag = new CompoundTag(); tag.putString("id", entry.id().toString()); tag.putString("type", entry.type());
            tag.putInt("before", entry.guildBefore()); tag.putInt("after", entry.guildAfter()); tag.putLong("tick", entry.tick());
            ListTag deltas = new ListTag(); entry.localDeltas().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(delta -> {
                CompoundTag village = villageTag(delta.getKey()); village.putInt("value", delta.getValue()); deltas.add(village);
            });
            tag.put("deltas", deltas); recent.add(tag);
        }
        root.put("history", recent);
        ListTag helpers = new ListTag();
        support.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag tag = villageTag(entry.getKey()); SupportContribution contribution = entry.getValue();
            tag.putString("id", contribution.id().toString()); tag.putInt("cycle", contribution.cycle());
            tag.putBoolean("supplied", contribution.supplied()); tag.putBoolean("resolved", contribution.resolved());
            tag.putBoolean("barred", contribution.barred()); helpers.add(tag);
        });
        root.put("support", helpers);
        root.putLong("warningTick", warningTick);
        ListTag savedAttacks = new ListTag();
        attacks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            AttackRecord attack = entry.getValue(); CompoundTag tag = new CompoundTag();
            tag.putString("victim", entry.getKey().toString()); tag.putString("id", attack.id().toString());
            tag.putLong("started", attack.startedTick()); tag.putInt("guildDelta", attack.guildDelta()); tag.putInt("localDelta", attack.localDelta()); tag.putBoolean("sanctioned", attack.sanctioned());
            if (attack.village() != null) tag.put("village", villageTag(attack.village())); savedAttacks.add(tag);
        }); root.put("attacks", savedAttacks);
        ListTag deaths = new ListTag(); fatalVictims.stream().sorted().forEach(id -> deaths.add(StringTag.valueOf(id.toString())));
        root.put("fatalVictims", deaths);
        if (activeCase != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", activeCase.id().toString()); tag.putBoolean("major", activeCase.major());
            tag.putInt("ticks", activeCase.onlineTicksRemaining()); tag.putString("selected", activeCase.selected().name());
            tag.putLong("lastOffence", activeCase.lastOffenceTick()); tag.putString("offence", activeCase.offence().name());
            CompoundTag aid = new CompoundTag(); activeCase.aid().forEach((material, count) -> aid.putInt(material.name(), count));
            tag.put("aid", aid);
            ListTag affected = new ListTag(); activeCase.affected().stream().sorted().forEach(village -> affected.add(villageTag(village)));
            tag.put("affected", affected); root.put("case", tag);
        }
        return root;
    }

    public static SocialReputationData fromNbt(CompoundTag root) {
        if (root == null || root.isEmpty()) return new SocialReputationData();
        try {
            if (requiredInt(root, "schema") != SCHEMA) return quarantine(root);
            SocialReputationData data = new SocialReputationData();
            data.guildTrust = bounded(requiredInt(root, "guildTrust"), -100, 100);
            data.probationRemaining = bounded(requiredInt(root, "probation"), 0, 2);
            data.revision = nonnegative(requiredLong(root, "revision"));
            for (var raw : optionalList(root, "villages")) {
                if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid village record");
                VillageKey key = villageFromTag(tag);
                if (data.localTrust.put(key, bounded(requiredInt(tag, "value"), -100, 100)) != null) throw new IllegalArgumentException("Duplicate village");
            }
            for (var raw : optionalList(root, "receipts")) {
                String receipt = raw.asString().orElseThrow();
                int separator = receipt.indexOf(':');
                BenefitKind.valueOf(receipt.substring(0, separator)); UUID.fromString(receipt.substring(separator + 1));
                data.receipts.add(receipt);
            }
            data.resetToken = requiredLong(root, "resetToken");
            data.guildUsed = bounded(root.contains("guildUsed") ? requiredInt(root, "guildUsed") : 0, 0, 8);
            for (var raw : optionalList(root, "localUsed")) {
                if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid local counter");
                if (data.localUsed.put(villageFromTag(tag), bounded(requiredInt(tag, "value"), 0, 8)) != null)
                    throw new IllegalArgumentException("Duplicate local counter");
            }
            if (root.contains("sourceUsed") && !(root.get("sourceUsed") instanceof CompoundTag)) throw new IllegalArgumentException("Invalid source counters");
            CompoundTag sourceCounters = root.getCompoundOrEmpty("sourceUsed");
            for (String key : sourceCounters.keySet()) data.sourceUsed.put(BenefitKind.valueOf(key), bounded(requiredInt(sourceCounters, key), 0, Integer.MAX_VALUE));
            for (var raw : optionalList(root, "history")) {
                if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid history");
                Map<VillageKey, Integer> deltas = new HashMap<>();
                for (var saved : optionalList(tag, "deltas")) {
                    if (!(saved instanceof CompoundTag delta)) throw new IllegalArgumentException("Invalid history delta");
                    deltas.put(villageFromTag(delta), bounded(requiredInt(delta, "value"), -200, 200));
                }
                String type = tag.getStringOr("type", "");
                if (type.isBlank() || type.length() > 64) throw new IllegalArgumentException("Invalid history type");
                data.history.add(new HistoryEntry(UUID.fromString(tag.getStringOr("id", "")), type,
                        bounded(requiredInt(tag, "before"), -100, 100), bounded(requiredInt(tag, "after"), -100, 100),
                        deltas, nonnegative(tag.getLongOr("tick", 0))));
                if (data.history.size() > 32) throw new IllegalArgumentException("Overlong visible history");
            }
            if (root.contains("case")) {
                if (!(root.get("case") instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid case");
                Set<VillageKey> affected = new HashSet<>();
                for (var raw : optionalList(tag, "affected")) {
                    if (!(raw instanceof CompoundTag village)) throw new IllegalArgumentException("Invalid case village");
                    affected.add(villageFromTag(village));
                }
                boolean major = requiredBoolean(tag, "major");
                MaterialOption selected = MaterialOption.valueOf(tag.getStringOr("selected", ""));
                Map<MaterialOption, Integer> aid = new EnumMap<>(MaterialOption.class);
                if (!(tag.get("aid") instanceof CompoundTag)) throw new IllegalArgumentException("Invalid aid record");
                CompoundTag savedAid = tag.getCompoundOrEmpty("aid");
                for (String key : savedAid.keySet()) {
                    MaterialOption material = MaterialOption.valueOf(key);
                    int required = major || selected == material ? SocialReputationRules.aidRequired(major, material) : 0;
                    aid.put(material, bounded(requiredInt(savedAid, key), 0, required));
                }
                data.activeCase = new ReparationCase(UUID.fromString(tag.getStringOr("id", "")), major, affected,
                        bounded(requiredInt(tag, "ticks"), 0, major ? 36_000 : 12_000), selected, aid,
                        nonnegative(requiredLong(tag, "lastOffence")), Offence.valueOf(tag.getStringOr("offence", "")));
            }
            for (var raw : optionalList(root, "support")) {
                if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid support");
                VillageKey village = villageFromTag(tag);
                SupportContribution contribution = new SupportContribution(UUID.fromString(tag.getStringOr("id", "")),
                        requiredInt(tag, "cycle"), requiredBoolean(tag, "supplied"), requiredBoolean(tag, "resolved"), requiredBoolean(tag, "barred"));
                if (data.support.put(village, contribution) != null) throw new IllegalArgumentException("Duplicate support village");
            }
            data.warningTick = root.getLongOr("warningTick", -1);
            if (data.warningTick < -1 || root.contains("warningTick") && !(root.get("warningTick") instanceof net.minecraft.nbt.LongTag))
                throw new IllegalArgumentException("Invalid warning clock");
            for (var raw : optionalList(root, "attacks")) {
                if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid attack");
                VillageKey village = null;
                if (tag.contains("village")) {
                    if (!(tag.get("village") instanceof CompoundTag place)) throw new IllegalArgumentException("Invalid attack village");
                    village = villageFromTag(place);
                }
                UUID victim = UUID.fromString(tag.getStringOr("victim", ""));
                AttackRecord attack = new AttackRecord(UUID.fromString(tag.getStringOr("id", "")), village,
                        nonnegative(tag.getLong("started").orElseThrow()), bounded(requiredInt(tag, "guildDelta"), -100, 0),
                        bounded(requiredInt(tag, "localDelta"), -100, 0), !tag.contains("sanctioned") || requiredBoolean(tag, "sanctioned"));
                if (data.attacks.put(victim, attack) != null) throw new IllegalArgumentException("Duplicate attack");
            }
            for (var raw : optionalList(root, "fatalVictims")) data.fatalVictims.add(UUID.fromString(raw.asString().orElseThrow()));
            return data;
        } catch (RuntimeException corrupt) { return quarantine(root); }
    }
    private static SocialReputationData quarantine(CompoundTag root) {
        SocialReputationData data = new SocialReputationData(); data.preserved = root.copy(); return data;
    }
    private static int requiredInt(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof IntTag)) throw new IllegalArgumentException("Missing/invalid integer: " + key);
        return tag.getIntOr(key, 0);
    }
    private static long requiredLong(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof net.minecraft.nbt.LongTag)) throw new IllegalArgumentException("Missing/invalid clock: " + key);
        return tag.getLongOr(key, 0);
    }
    private static boolean requiredBoolean(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof net.minecraft.nbt.ByteTag)) throw new IllegalArgumentException("Invalid boolean: " + key);
        return tag.getBooleanOr(key, false);
    }
    private static ListTag optionalList(CompoundTag root, String key) {
        if (!root.contains(key)) return new ListTag();
        if (!(root.get(key) instanceof ListTag list)) throw new IllegalArgumentException("Invalid list: " + key);
        return list;
    }
    private static int bounded(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Value out of range"); return value;
    }
    private static long nonnegative(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative clock/revision"); return value;
    }
    static CompoundTag villageTag(VillageKey village) {
        CompoundTag tag = new CompoundTag(); tag.putString("dimension", village.dimension());
        tag.putInt("x", village.anchorX()); tag.putInt("z", village.anchorZ()); return tag;
    }
    static VillageKey villageFromTag(CompoundTag tag) {
        return new VillageKey(tag.getStringOr("dimension", ""), requiredInt(tag, "x"), requiredInt(tag, "z"));
    }
}
