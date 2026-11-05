package de.quest.reputation;

import de.quest.data.QuestState;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;
import de.quest.util.TimeUtil;
import net.minecraft.server.MinecraftServer;

/** Authoritative terminal-event reducer. Content adapters supply durable source IDs, never client points. */
public final class SocialReputationService {
    private SocialReputationService() {}
    public static boolean enabled() { return de.quest.config.VillageQuestServerConfig.get().socialReputation().enabled(); }

    public static UUID instanceId(BenefitKind kind, UUID player, String savedInstanceKey) {
        Objects.requireNonNull(kind); Objects.requireNonNull(player);
        if (savedInstanceKey == null || savedInstanceKey.isBlank()) throw new IllegalArgumentException("Missing durable source key");
        return UUID.nameUUIDFromBytes(("vq-social-v1:" + kind.name() + ":" + player + ":" + savedInstanceKey)
                .getBytes(StandardCharsets.UTF_8));
    }

    public static MutationResult recordNamedBenefit(MinecraftServer server, UUID player, BenefitKind kind,
                                                    String savedInstanceKey, List<VillageKey> villages) {
        return recordBenefit(server, new BenefitEvent(instanceId(kind, player, savedInstanceKey), kind,
                player, villages, TimeUtil.currentDay()));
    }

    public static MutationResult recordQuestCompletion(MinecraftServer server, UUID player, BenefitKind kind,
                                                       long cycle, String slot) {
        return recordBenefit(server, questCompletionEvent(player, kind, cycle, slot, TimeUtil.currentDay()));
    }

    public static MutationResult recordQuestCompletion(SocialReputationData data, UUID player, BenefitKind kind,
                                                       long cycle, String slot, long reset, long tick) {
        return recordBenefit(data, questCompletionEvent(player, kind, cycle, slot, reset), tick);
    }

    private static BenefitEvent questCompletionEvent(UUID player, BenefitKind kind, long cycle, String slot, long reset) {
        if (kind != BenefitKind.DAILY && kind != BenefitKind.WEEKLY) throw new IllegalArgumentException("Not a repeatable quest completion");
        if (!"normal".equals(slot) && !(kind == BenefitKind.DAILY && "bonus".equals(slot))) throw new IllegalArgumentException("Invalid quest slot");
        return new BenefitEvent(instanceId(kind, player, cycle + ":" + slot), kind, player, List.of(), reset);
    }

    public static MutationResult recordBenefit(MinecraftServer server, BenefitEvent event) {
        QuestState state = QuestState.get(server);
        SocialReputationData data = state.getPlayerData(event.beneficiary()).socialReputation();
        // Consuming disabled/stale terminal sources prevents rewards being banked across re-enable or reset.
        boolean repeatable = event.kind() != BenefitKind.STORY && event.kind() != BenefitKind.AFTERSTORY;
        if (!enabled() || repeatable && event.resetToken() < TimeUtil.currentDay()) {
            boolean consumed = data.markReceipt(event.kind(), event.sourceId()); if (consumed) state.setDirty();
            return new MutationResult(consumed, 0, Map.of(), data.revision());
        }
        MutationResult result = recordBenefit(data, event, state.socialServerTick());
        if (result.applied()) state.updateFromRuntime();
        return result;
    }

    /** Pure-state path also used by startup reconciliation and offline beneficiaries. */
    public static MutationResult recordBenefit(SocialReputationData data, BenefitEvent event, long serverTick) {
        Objects.requireNonNull(data); Objects.requireNonNull(event);
        validateTargets(event);
        if (!data.markReceipt(event.kind(), event.sourceId())) return new MutationResult(false, 0, Map.of(), data.revision());
        boolean onceOnly = event.kind() == BenefitKind.STORY || event.kind() == BenefitKind.AFTERSTORY;
        if (!onceOnly && event.resetToken() < data.resetToken()) return new MutationResult(true, 0, Map.of(), data.revision());
        if (!onceOnly) data.beginReset(event.resetToken());
        int before = data.guildTrust();
        int requested = guildReward(event.kind());
        Map<VillageKey, Integer> localRequested = localRewards(event);
        int sourceLimit = switch (event.kind()) {
            case DAILY, DISPATCH -> 2;
            case CONVOY, INCIDENT -> 1;
            default -> Integer.MAX_VALUE;
        };
        boolean sourceAllowed = onceOnly || data.sourceUsed(event.kind()) < sourceLimit;
        int guild = sourceAllowed ? Math.min(requested, onceOnly ? requested : Math.max(0, 8 - data.guildUsed())) : 0;
        guild = Math.min(guild, 100 - before);
        data.setGuildTrust(before + guild);
        Map<VillageKey, Integer> local = new HashMap<>();
        if (sourceAllowed) localRequested.forEach((village, amount) -> {
            int delta = Math.min(amount, onceOnly ? amount : Math.max(0, 8 - data.localUsed(village)));
            delta = Math.min(delta, 100 - data.localTrust(village));
            if (delta > 0) { data.setLocalTrust(village, data.localTrust(village) + delta); local.put(village, delta); }
        });
        if (!onceOnly) data.accountRepeatable(event.kind(), guild, local);
        if (guild > 0 || !local.isEmpty()) data.setProbationRemaining(data.probationRemaining() - 1);
        data.appendHistory(new SocialReputationData.HistoryEntry(event.sourceId(), "benefit." + event.kind().name(), before,
                data.guildTrust(), local, Math.max(0, serverTick)));
        return new MutationResult(true, guild, local, data.revision());
    }

    static int guildReward(BenefitKind kind) {
        return switch (kind) {
            case DAILY, NOTICE -> 1;
            case WEEKLY, DISPATCH, STORY -> 3;
            case CONVOY -> 6;
            case INCIDENT, AFTERSTORY -> 2;
            case RESETTLEMENT -> 5;
        };
    }
    private static Map<VillageKey, Integer> localRewards(BenefitEvent event) {
        Map<VillageKey, Integer> values = new LinkedHashMap<>();
        var villages = event.villages();
        switch (event.kind()) {
            case NOTICE, AFTERSTORY -> values.put(villages.getFirst(), 4);
            case DISPATCH -> { values.put(villages.getFirst(), 2); values.put(villages.get(1), 4); }
            case CONVOY -> villages.forEach(village -> values.put(village, 2));
            case RESETTLEMENT -> values.put(villages.getFirst(), 8);
            case STORY -> { if (!villages.isEmpty()) values.put(villages.getFirst(), 6); }
            default -> { }
        }
        return values;
    }
    private static void validateTargets(BenefitEvent event) {
        int count = event.villages().size();
        boolean valid = switch (event.kind()) {
            case DAILY, WEEKLY, INCIDENT -> count == 0;
            case NOTICE, AFTERSTORY, RESETTLEMENT -> count == 1;
            case DISPATCH -> count == 2 && !event.villages().getFirst().equals(event.villages().get(1));
            case CONVOY -> count >= 1 && count <= 3 && event.villages().stream().distinct().count() == count;
            case STORY -> count <= 1;
        };
        if (!valid) throw new IllegalArgumentException("Wrong social targets for " + event.kind());
    }
}
