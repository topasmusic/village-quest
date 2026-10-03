package de.quest.reputation;

import de.quest.data.QuestState;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.shrine.VillageBondService;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Bounded personal snapshots. Reading never discovers villages or changes earned reputation. */
public final class ReputationViewService {
    public static final int SCHEMA = 1, VILLAGE_PAGE_SIZE = 8, HISTORY_LIMIT = 32;
    private ReputationViewService() {}
    public record VillageView(VillageKey key, int trust, boolean affected) {}
    public record HistoryView(UUID id, String event, int guildDelta, VillageKey village, int localDelta, int affectedCount, long tick) {}
    public record AidView(int material, int supplied, int required, int carried) {}
    public record CaseView(UUID id, boolean major, int affectedCount, int ticksRemaining, int selected, int offence, List<AidView> aid) {
        public CaseView { aid = List.copyOf(aid); }
    }
    public record View(int schema, long revision, boolean enabled, boolean writable, int guildTrust,
                       int probation, int buyLimit, boolean dispatchAllowed, boolean convoyAllowed, int rewardPercent,
                       int villagePage, int villageTotal, List<VillageView> villages, List<HistoryView> history, CaseView reparation) {
        public View { villages = List.copyOf(villages); history = List.copyOf(history); }
    }
    public static View build(SocialReputationData data, List<VillageKey> known, int requestedPage, boolean enabled) {
        List<VillageKey> villages = known.stream().distinct().toList();
        int page = Math.clamp(requestedPage, 0, Math.max(0, (villages.size() - 1) / VILLAGE_PAGE_SIZE));
        int start = page * VILLAGE_PAGE_SIZE;
        var active = data.activeCase();
        List<VillageView> local = villages.subList(start, Math.min(start + VILLAGE_PAGE_SIZE, villages.size())).stream()
                .map(key -> new VillageView(key, data.localTrust(key), active != null && active.affected().contains(key))).toList();
        List<HistoryView> history = new ArrayList<>();
        for (var entry : data.history().reversed()) {
            var first = entry.localDeltas().keySet().stream().sorted().findFirst().orElse(null);
            history.add(new HistoryView(entry.id(), entry.type(), entry.guildDelta(), first,
                    first == null ? 0 : entry.localDeltas().get(first), entry.localDeltas().size(), entry.tick()));
            if (history.size() == HISTORY_LIMIT) break;
        }
        CaseView reparation = null;
        if (enabled && active != null) {
            List<AidView> aid = new ArrayList<>();
            for (var material : MaterialOption.values()) if (active.required(material) > 0)
                aid.add(new AidView(material.ordinal(), active.delivered(material), active.required(material), 0));
            reparation = new CaseView(active.id(), active.major(), active.affected().size(), active.onlineTicksRemaining(), active.selected().ordinal(), active.offence().ordinal(), aid);
        }
        var trade = SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null, enabled);
        var dispatch = SocialReputationRules.decision(ServiceKind.DISPATCH_START, data, null, enabled);
        var convoy = SocialReputationRules.decision(ServiceKind.CONVOY_START, data, null, enabled);
        return new View(SCHEMA, data.revision(), enabled, data.writable(), data.guildTrust(), data.probationRemaining(),
                trade.caravanBuyLimit(), dispatch.allowed(), convoy.allowed(), dispatch.rewardPercent(), page, villages.size(), local, history, reparation);
    }
    public static List<VillageKey> knownVillages(ServerPlayer player) {
        var data = QuestState.get(player.level().getServer()).getPlayerData(player.getUUID());
        List<VillageKey> known = new ArrayList<>();
        for (int i = 0; i < VillageBondService.historicalVillageCount(data); i++) {
            String dimension = data.getTradeRouteString(VillageBondService.villageKey(i, "dimension"));
            if (dimension.isBlank()) dimension = "minecraft:overworld";
            known.add(new VillageKey(dimension, data.getTradeRouteInt(VillageBondService.villageKey(i, "x")),
                    data.getTradeRouteInt(VillageBondService.villageKey(i, "z"))));
        }
        return List.copyOf(known);
    }
    public static View view(ServerPlayer player, int page) {
        var data = QuestState.get(player.level().getServer()).getPlayerData(player.getUUID()).socialReputation();
        return build(data, knownVillages(player), page, SocialReputationService.enabled());
    }
}
