package de.quest.quest.daily;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.util.QuestCalendarService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Three persisted normal offers per cycle, independent of acceptance/cancellation. */
public final class DailyChoiceService {
    public static final String PREFIX = "daily_choice_";
    private static final String SAVED = "daily.offers.v1";
    public record Offer(long day, String revision, DailyQuestService.DailyQuestType type, RepeatableTargetProfile profile) {
        public String entryId() { return PREFIX + day + "_" + revision + "_" + type.name(); }
    }
    private DailyChoiceService() {}

    public static List<Offer> offers(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return List.of();
        var state = QuestState.get(world.getServer());
        var data = state.getPlayerData(playerId);
        long day = QuestCalendarService.currentDay();
        if (data.hasActiveDailySlot() || data.getLastRewardDay() == day
                || FirstDailyChoiceService.canChoose(data) || FirstDailyChoiceService.isActive(data)) return List.of();
        var saved = decode(data.getTradeRouteString(SAVED), day);
        if (saved.size() == 3) return saved;
        var first = DailyQuestService.previewQuestChoice(world, playerId);
        String revision = UUID.randomUUID().toString();
        var result = new ArrayList<Offer>();
        result.add(new Offer(day, revision, first, data.getDailyTargetProfile()));
        var candidates = new ArrayList<>(List.of(DailyQuestService.DailyQuestType.values()));
        candidates.removeIf(type -> type == first || type.category() == DailyQuestService.DailyQuestCategory.COMBAT);
        var random = new Random(playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits() ^ day);
        Collections.shuffle(candidates, random);
        for (int i = 0; i < 2; i++) result.add(new Offer(day, revision, candidates.get(i), RepeatableTargetProfile.byId(random.nextInt(3))));
        data.setTradeRouteString(SAVED, day + ";" + revision + ";" + result.stream()
                .map(offer -> offer.type().name() + ":" + offer.profile().name()).collect(java.util.stream.Collectors.joining(";")));
        state.setDirty();
        return List.copyOf(result);
    }

    private static List<Offer> decode(String raw, long day) {
        if (raw == null || raw.isEmpty()) return List.of();
        try {
            String[] fields = raw.split(";", -1);
            if (fields.length != 5 || Long.parseLong(fields[0]) != day) return List.of();
            UUID.fromString(fields[1]);
            var result = new ArrayList<Offer>();
            for (int i = 2; i < fields.length; i++) {
                String[] pair = fields[i].split(":", -1);
                if (pair.length != 2) return List.of();
                result.add(new Offer(day, fields[1], DailyQuestService.DailyQuestType.valueOf(pair[0]), RepeatableTargetProfile.valueOf(pair[1])));
            }
            return result.stream().map(Offer::type).distinct().count() == 3 ? List.copyOf(result) : List.of();
        } catch (IllegalArgumentException malformedSave) { return List.of(); }
    }

    public static boolean accept(ServerLevel world, ServerPlayer player, String entryId) {
        if (world == null || player == null || entryId == null) return false;
        var offer = offers(world, player.getUUID()).stream().filter(row -> row.entryId().equals(entryId)).findFirst().orElse(null);
        return offer != null && DailyQuestService.acceptOfferedQuest(world, player, offer);
    }

    public static void invalidateOffers(PlayerQuestData data) { data.setTradeRouteString(SAVED, ""); }
}
