package de.quest.reputation;

import de.quest.caravan.GuildConvoyState;
import de.quest.data.QuestState;
import de.quest.reputation.ReputationDamageAdapter.*;
import de.quest.reputation.SocialReputationData.AttackRecord;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Personal consequences; route owners, parties and player guilds are never used as the accused. */
public final class ReputationIncidentService {
    private ReputationIncidentService() {}
    public enum Outcome { WARN, ASSAULT, FATAL, IGNORED }
    public record IncidentResult(Outcome outcome, UUID attacker, UUID caseId, int guildDelta,
                                 Map<VillageKey, Integer> localDeltas, long revision) {
        public IncidentResult { localDeltas = Map.copyOf(localDeltas); }
    }
    public static IncidentResult record(MinecraftServer server, DamageEvidence evidence) {
        if (!SocialReputationService.enabled()) return ignored(null, evidence.attacker());
        QuestState state = QuestState.get(server);
        if (evidence.attacker() == null) return ignored(null, evidence.attacker());
        var data = state.getPlayerData(evidence.attacker()).socialReputation();
        IncidentResult result = record(data, evidence);
        if (result.outcome() != Outcome.IGNORED) state.setDirty();
        if (result.outcome() == Outcome.ASSAULT || result.outcome() == Outcome.FATAL)
            GuildConvoyState.get(server).disqualifyTrustFor(evidence.attacker());
        return result;
    }
    public static IncidentResult record(SocialReputationData data, DamageEvidence evidence) {
        if (data == null || !data.writable() || evidence.attacker() == null || evidence.logicalVictim() == null
                || evidence.kind() == null || evidence.serverTick() < 0 || !Float.isFinite(evidence.acceptedDamage())
                || evidence.acceptedDamage() <= 0 || data.hasFatalVictim(evidence.logicalVictim())) return ignored(data, evidence.attacker());
        long tick = evidence.serverTick();
        AttackRecord previous = data.attack(evidence.logicalVictim(), tick);
        VillageKey crimeVillage = previous == null ? evidence.village() : previous.village();
        boolean weak = evidence.acceptedDamage() <= 4 && !evidence.fatal();
        if (weak && data.activeCase() == null) {
            if (data.warningTick() < 0 || tick >= data.warningTick() && tick - data.warningTick() >= 12_000) {
                data.warnAt(tick);
                data.recordAttack(evidence.logicalVictim(), new AttackRecord(UUID.randomUUID(), crimeVillage, tick, 0, 0, false), false, tick);
                return new IncidentResult(Outcome.WARN, evidence.attacker(), null, 0, Map.of(), data.revision());
            }
            if (tick == data.warningTick()) {
                if (previous == null) data.recordAttack(evidence.logicalVictim(), new AttackRecord(UUID.randomUUID(), crimeVillage, tick, 0, 0, false), false, tick);
                return ignored(data, evidence.attacker());
            }
        }
        Offence offence = !evidence.fatal() ? Offence.ASSAULT : switch (evidence.kind()) {
            case CREW -> Offence.CREW_KILL; case VILLAGER -> Offence.VILLAGER_KILL; case MULE -> Offence.MULE_KILL;
        };
        int before = data.guildTrust();
        java.util.Map<VillageKey, Integer> beforeLocal = new java.util.HashMap<>();
        if (crimeVillage != null) beforeLocal.put(crimeVillage, data.localTrust(crimeVillage));
        if (previous != null && previous.village() != null) beforeLocal.putIfAbsent(previous.village(), data.localTrust(previous.village()));
        boolean aggregated = previous != null && previous.sanctioned() && !evidence.fatal();
        if (!aggregated) {
            int baseline = before;
            if (previous != null) {
                // Restore only this victim's applied loss on the current balance, preserving other events.
                baseline = SocialReputationRules.clamp(before - previous.guildDelta());
                if (previous.village() != null) data.setLocalTrust(previous.village(),
                        data.localTrust(previous.village()) - previous.localDelta());
            }
            data.setGuildTrust(SocialReputationRules.lossAfter(baseline, offence, TrustScope.GUILD));
            if (crimeVillage != null) data.setLocalTrust(crimeVillage,
                    SocialReputationRules.lossAfter(data.localTrust(crimeVillage), offence, TrustScope.VILLAGE));
        }
        UUID eventId = previous == null ? UUID.randomUUID() : previous.id();
        data.openCase(eventId, offence, crimeVillage, tick);
        int delta = data.guildTrust() - before;
        java.util.Map<VillageKey, Integer> localDeltas = new java.util.HashMap<>();
        beforeLocal.forEach((village, value) -> { int change = data.localTrust(village) - value; if (change != 0) localDeltas.put(village, change); });
        int localDelta = crimeVillage == null ? 0 : localDeltas.getOrDefault(crimeVillage, 0);
        if (aggregated) {
            // Refresh the quiet-gap clock while retaining the original applied loss for fatal replacement.
            data.recordAttack(evidence.logicalVictim(), new AttackRecord(eventId, crimeVillage, tick, previous.guildDelta(), previous.localDelta()), false, tick);
        } else {
            data.recordAttack(evidence.logicalVictim(), new AttackRecord(eventId, crimeVillage, tick, delta, localDelta), evidence.fatal(), tick);
            data.appendHistory(new SocialReputationData.HistoryEntry(eventId, "offence." + offence.name(), before,
                    data.guildTrust(), localDeltas, tick));
        }
        return new IncidentResult(evidence.fatal() ? Outcome.FATAL : Outcome.ASSAULT, evidence.attacker(), data.activeCase().id(),
                delta, localDeltas, data.revision());
    }
    private static IncidentResult ignored(SocialReputationData data, UUID attacker) {
        return new IncidentResult(Outcome.IGNORED, attacker, data == null || data.activeCase() == null ? null : data.activeCase().id(),
                0, Map.of(), data == null ? 0 : data.revision());
    }
}
