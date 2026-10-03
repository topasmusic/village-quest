package de.quest.reputation;

import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Social standing is independent of earned skill reputation and permanent village bonds. */
public final class SocialReputationRules {
    public static final int REPEATABLE_GUILD_CAP = 8;
    public static final int REPEATABLE_VILLAGE_CAP = 8;
    public static final int MINOR_ONLINE_TICKS = 12_000;
    public static final int MAJOR_ONLINE_TICKS = 36_000;
    private SocialReputationRules() {}

    public enum TrustScope { GUILD, VILLAGE }
    public enum SocialRank { OSTRACISED, DISTRUSTED, NEUTRAL, RELIABLE, RESPECTED }
    public enum Offence { ASSAULT, CREW_KILL, VILLAGER_KILL, MULE_KILL }
    public enum BenefitKind { DAILY, WEEKLY, NOTICE, DISPATCH, CONVOY, INCIDENT, RESETTLEMENT, STORY, AFTERSTORY }
    public enum ServiceKind { CARAVAN_TRADE, DISPATCH_START, CONVOY_START, CONVOY_JOIN, NOTICE_ACCEPT }
    public enum MaterialOption { WHEAT, PLANKS, IRON }

    public record StandingKey(UUID playerId, TrustScope scope, VillageKey village) {
        public StandingKey {
            Objects.requireNonNull(playerId); Objects.requireNonNull(scope);
            if ((scope == TrustScope.VILLAGE) != (village != null)) throw new IllegalArgumentException("Standing scope/target mismatch");
        }
    }
    public record BenefitEvent(UUID sourceId, BenefitKind kind, UUID beneficiary, List<VillageKey> villages, long resetToken) {
        public BenefitEvent {
            Objects.requireNonNull(sourceId); Objects.requireNonNull(kind); Objects.requireNonNull(beneficiary);
            villages = List.copyOf(villages);
        }
    }
    public record MutationResult(boolean applied, int guildDelta, Map<VillageKey, Integer> localDeltas, long revision) {
        public MutationResult { localDeltas = Map.copyOf(localDeltas); }
    }
    public record ServiceDecision(boolean allowed, String reasonKey, int caravanBuyLimit, int rewardPercent) {}

    public static int clamp(int value) { return Math.clamp(value, -100, 100); }
    public static SocialRank rankFor(int value) {
        if (value <= -60) return SocialRank.OSTRACISED;
        if (value <= -20) return SocialRank.DISTRUSTED;
        if (value < 20) return SocialRank.NEUTRAL;
        return value < 60 ? SocialRank.RELIABLE : SocialRank.RESPECTED;
    }

    /** Returns the new value, not a fixed subtraction: a bank of goodwill cannot cancel a death. */
    public static int lossAfter(int before, Offence offence, TrustScope scope) {
        int value = clamp(before);
        return clamp(switch (offence) {
            case ASSAULT -> value - (scope == TrustScope.GUILD ? 15 : 25);
            case CREW_KILL -> Math.min(value - (scope == TrustScope.GUILD ? 60 : 70), -60);
            case VILLAGER_KILL -> Math.min(value - (scope == TrustScope.GUILD ? 40 : 70), scope == TrustScope.GUILD ? -20 : -60);
            case MULE_KILL -> scope == TrustScope.GUILD ? Math.min(value - 35, -20) : Math.min(value - 45, -45);
        });
    }

    public static int aidRequired(boolean major, MaterialOption option) {
        return switch (option) { case WHEAT -> major ? 64 : 32; case PLANKS -> major ? 32 : 16; case IRON -> major ? 8 : 4; };
    }

    public static int rewardWithBonus(int base, int percent) {
        if (base < 0 || percent < 0 || percent > 5) throw new IllegalArgumentException("Invalid dispatch reward/bonus");
        return Math.toIntExact((long) base + (long) base * percent / 100);
    }

    public static ServiceDecision decision(ServiceKind kind, SocialReputationData data, VillageKey village) {
        return decision(kind, data, village, SocialReputationService.enabled());
    }

    public static ServiceDecision decision(ServiceKind kind, SocialReputationData data, VillageKey village, boolean enabled) {
        Objects.requireNonNull(kind); Objects.requireNonNull(data);
        if (!enabled) return new ServiceDecision(true, "", 4, 0);
        var active = data.activeCase();
        boolean caseBlocked = active != null && (kind != ServiceKind.NOTICE_ACCEPT || active.affected().contains(village));
        boolean lowTrustBlocked = kind != ServiceKind.NOTICE_ACCEPT
                && (data.guildTrust() <= -60 || (data.guildTrust() <= -20 && kind != ServiceKind.CARAVAN_TRADE));
        if (caseBlocked || lowTrustBlocked) return new ServiceDecision(false,
                caseBlocked ? "reputation.village-quest.access.case" : "reputation.village-quest.access.trust", 0, 0);
        int buys = data.guildTrust() < -19 ? 2 : data.probationRemaining() > 0 ? 4
                : data.guildTrust() >= 60 ? 6 : data.guildTrust() >= 20 ? 5 : 4;
        int bonus = data.guildTrust() >= 60 && data.probationRemaining() == 0 ? 5 : 0;
        return new ServiceDecision(true, "", buys, bonus);
    }
}
