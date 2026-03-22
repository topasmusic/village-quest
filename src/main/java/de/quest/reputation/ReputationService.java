package de.quest.reputation;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ReputationService {
    public enum ReputationTrack {
        FARMING("farming", "text.village-quest.reputation.track.farming", Formatting.GREEN),
        CRAFTING("crafting", "text.village-quest.reputation.track.crafting", Formatting.GOLD),
        ANIMALS("animals", "text.village-quest.reputation.track.animals", Formatting.AQUA),
        TRADE("trade", "text.village-quest.reputation.track.trade", Formatting.BLUE),
        MONSTER_HUNTING("monster", "text.village-quest.reputation.track.monster", Formatting.RED);

        private final String id;
        private final String translationKey;
        private final Formatting color;

        ReputationTrack(String id, String translationKey, Formatting color) {
            this.id = id;
            this.translationKey = translationKey;
            this.color = color;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public Formatting color() {
            return color;
        }
    }

    public enum ReputationRank {
        NEWCOMER(0, "text.village-quest.reputation.rank.newcomer"),
        KNOWN(25, "text.village-quest.reputation.rank.known"),
        TRUSTED(60, "text.village-quest.reputation.rank.trusted"),
        ESTEEMED(120, "text.village-quest.reputation.rank.esteemed"),
        RENOWNED(200, "text.village-quest.reputation.rank.renowned");

        private final int minReputation;
        private final String translationKey;

        ReputationRank(int minReputation, String translationKey) {
            this.minReputation = minReputation;
            this.translationKey = translationKey;
        }

        public int minReputation() {
            return minReputation;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record ReputationReward(ReputationTrack track, int amount) {}
    public record OfferUnlock(String offerId, ReputationTrack track, int requiredReputation, String titleKey) {}
    public record ReputationUnlock(ReputationTrack track, int requiredReputation, String titleKey) {}

    private static final Map<String, OfferUnlock> OFFER_UNLOCKS = List.of(
            new OfferUnlock("honigfass", ReputationTrack.FARMING, 25, "text.village-quest.shop.offer.honigfass.title"),
            new OfferUnlock("bienenwabe", ReputationTrack.FARMING, 60, "text.village-quest.shop.offer.bienenwabe.title"),
            new OfferUnlock("beekeepers_kit", ReputationTrack.FARMING, 120, "text.village-quest.shop.offer.beekeepers_kit.title"),
            new OfferUnlock("gemaelde_begleiter", ReputationTrack.ANIMALS, 25, "text.village-quest.shop.offer.gemaelde_begleiter.title"),
            new OfferUnlock("gemaelde_majestaet", ReputationTrack.ANIMALS, 60, "text.village-quest.shop.offer.gemaelde_majestaet.title"),
            new OfferUnlock("gemaelde_good_doge", ReputationTrack.ANIMALS, 120, "text.village-quest.shop.offer.gemaelde_good_doge.title"),
            new OfferUnlock("gemaelde_ancient_warrior", ReputationTrack.CRAFTING, 25, "text.village-quest.shop.offer.gemaelde_ancient_warrior.title"),
            new OfferUnlock("gemaelde_the_legend", ReputationTrack.CRAFTING, 60, "text.village-quest.shop.offer.gemaelde_the_legend.title"),
            new OfferUnlock("gemaelde_pepe_the_almighty", ReputationTrack.CRAFTING, 120, "text.village-quest.shop.offer.gemaelde_pepe_the_almighty.title"),
            new OfferUnlock("gemaelde_over_there", ReputationTrack.TRADE, 25, "text.village-quest.shop.offer.gemaelde_over_there.title"),
            new OfferUnlock("gemaelde_happy_doge", ReputationTrack.TRADE, 60, "text.village-quest.shop.offer.gemaelde_happy_doge.title"),
            new OfferUnlock("gemaelde_something_is_sus", ReputationTrack.TRADE, 120, "text.village-quest.shop.offer.gemaelde_something_is_sus.title"),
            new OfferUnlock("hunters_satchel", ReputationTrack.MONSTER_HUNTING, 25, "text.village-quest.shop.offer.hunters_satchel.title"),
            new OfferUnlock("zombie_trophy", ReputationTrack.MONSTER_HUNTING, 60, "text.village-quest.shop.offer.zombie_trophy.title"),
            new OfferUnlock("skeleton_trophy", ReputationTrack.MONSTER_HUNTING, 120, "text.village-quest.shop.offer.skeleton_trophy.title"),
            new OfferUnlock("creeper_trophy", ReputationTrack.MONSTER_HUNTING, 200, "text.village-quest.shop.offer.creeper_trophy.title")
    ).stream().collect(Collectors.toUnmodifiableMap(OfferUnlock::offerId, unlock -> unlock));

    private static final List<ReputationUnlock> REPUTATION_UNLOCKS = List.of(
            new ReputationUnlock(ReputationTrack.FARMING, 25, "text.village-quest.shop.offer.honigfass.title"),
            new ReputationUnlock(ReputationTrack.FARMING, 60, "text.village-quest.shop.offer.bienenwabe.title"),
            new ReputationUnlock(ReputationTrack.FARMING, 120, "text.village-quest.shop.offer.beekeepers_kit.title"),
            new ReputationUnlock(ReputationTrack.FARMING, 200, "quest.village-quest.special.apiarist_smoker.title"),
            new ReputationUnlock(ReputationTrack.ANIMALS, 25, "text.village-quest.shop.offer.gemaelde_begleiter.title"),
            new ReputationUnlock(ReputationTrack.ANIMALS, 60, "text.village-quest.shop.offer.gemaelde_majestaet.title"),
            new ReputationUnlock(ReputationTrack.ANIMALS, 120, "text.village-quest.shop.offer.gemaelde_good_doge.title"),
            new ReputationUnlock(ReputationTrack.ANIMALS, 200, "quest.village-quest.special.flute.title"),
            new ReputationUnlock(ReputationTrack.CRAFTING, 25, "text.village-quest.shop.offer.gemaelde_ancient_warrior.title"),
            new ReputationUnlock(ReputationTrack.CRAFTING, 60, "text.village-quest.shop.offer.gemaelde_the_legend.title"),
            new ReputationUnlock(ReputationTrack.CRAFTING, 120, "text.village-quest.shop.offer.gemaelde_pepe_the_almighty.title"),
            new ReputationUnlock(ReputationTrack.CRAFTING, 200, "quest.village-quest.special.surveyor_compass.title"),
            new ReputationUnlock(ReputationTrack.TRADE, 25, "text.village-quest.shop.offer.gemaelde_over_there.title"),
            new ReputationUnlock(ReputationTrack.TRADE, 60, "text.village-quest.shop.offer.gemaelde_happy_doge.title"),
            new ReputationUnlock(ReputationTrack.TRADE, 120, "text.village-quest.shop.offer.gemaelde_something_is_sus.title"),
            new ReputationUnlock(ReputationTrack.TRADE, 200, "quest.village-quest.special.merchant.title"),
            new ReputationUnlock(ReputationTrack.MONSTER_HUNTING, 25, "text.village-quest.shop.offer.hunters_satchel.title"),
            new ReputationUnlock(ReputationTrack.MONSTER_HUNTING, 60, "text.village-quest.shop.offer.zombie_trophy.title"),
            new ReputationUnlock(ReputationTrack.MONSTER_HUNTING, 120, "text.village-quest.shop.offer.skeleton_trophy.title"),
            new ReputationUnlock(ReputationTrack.MONSTER_HUNTING, 200, "text.village-quest.shop.offer.creeper_trophy.title")
    );

    private ReputationService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static int get(ServerWorld world, UUID playerId, ReputationTrack track) {
        if (world == null || playerId == null || track == null) {
            return 0;
        }
        return data(world, playerId).getReputation(track.id());
    }

    public static int add(ServerWorld world, UUID playerId, ReputationTrack track, int amount) {
        if (world == null || playerId == null || track == null || amount == 0) {
            return get(world, playerId, track);
        }
        PlayerQuestData data = data(world, playerId);
        data.addReputation(track.id(), amount);
        QuestState.get(world.getServer()).markDirty();
        return data.getReputation(track.id());
    }

    public static int total(ServerWorld world, UUID playerId) {
        int sum = 0;
        for (ReputationTrack track : ReputationTrack.values()) {
            sum += get(world, playerId, track);
        }
        return sum;
    }

    public static ReputationTrack trackFor(DailyQuestService.DailyQuestType type) {
        if (type == null) {
            return ReputationTrack.CRAFTING;
        }
        return switch (type) {
            case HONEY, PET_COLLAR, WOOL_WEAVING, STALL_NEW_LIFE -> ReputationTrack.ANIMALS;
            case WHEAT_HARVEST, POTATO_HARVEST, RIVER_MEAL, AUTUMN_HARVEST -> ReputationTrack.FARMING;
            case WOODCUTTING, COAL_MINING, SMITH_SMELTING -> ReputationTrack.CRAFTING;
            case VILLAGE_TRADING -> ReputationTrack.TRADE;
            case ZOMBIE_CULL, SKELETON_PATROL, SPIDER_SWEEP, CREEPER_WATCH -> ReputationTrack.MONSTER_HUNTING;
        };
    }

    public static int rewardFor(DailyQuestService.DailyQuestDifficulty difficulty) {
        if (difficulty == null) {
            return 15;
        }
        return switch (difficulty) {
            case EASY -> 10;
            case STANDARD -> 15;
            case HARD -> 20;
        };
    }

    public static ReputationReward rewardFor(DailyQuestService.DailyQuestType type) {
        return new ReputationReward(trackFor(type), rewardFor(DailyQuestService.difficulty(type)));
    }

    public static ReputationTrack parseTrack(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ReputationTrack track : ReputationTrack.values()) {
            if (track.id().equalsIgnoreCase(raw)) {
                return track;
            }
        }
        return null;
    }

    public static ReputationRank rankFor(int reputation) {
        ReputationRank current = ReputationRank.NEWCOMER;
        for (ReputationRank rank : ReputationRank.values()) {
            if (reputation >= rank.minReputation()) {
                current = rank;
            }
        }
        return current;
    }

    public static Text displayRank(ReputationRank rank) {
        if (rank == null) {
            return Text.empty();
        }
        return Text.translatable(rank.translationKey()).formatted(Formatting.GRAY);
    }

    public static Text displayName(ReputationTrack track) {
        if (track == null) {
            return Text.empty();
        }
        return Text.translatable(track.translationKey()).formatted(track.color());
    }

    public static OfferUnlock unlockForOffer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        return OFFER_UNLOCKS.get(offerId);
    }

    public static boolean isOfferUnlocked(ServerWorld world, UUID playerId, String offerId) {
        OfferUnlock unlock = unlockForOffer(offerId);
        if (unlock == null) {
            return true;
        }
        return get(world, playerId, unlock.track()) >= unlock.requiredReputation();
    }

    public static List<OfferUnlock> unlocksFor(ReputationTrack track) {
        return OFFER_UNLOCKS.values().stream()
                .filter(unlock -> unlock.track() == track)
                .sorted(Comparator.comparingInt(OfferUnlock::requiredReputation))
                .toList();
    }

    public static OfferUnlock nextUnlock(ReputationTrack track, int currentValue) {
        return unlocksFor(track).stream()
                .filter(unlock -> currentValue < unlock.requiredReputation())
                .min(Comparator.comparingInt(OfferUnlock::requiredReputation))
                .orElse(null);
    }

    public static Text displayUnlockTitle(OfferUnlock unlock) {
        if (unlock == null) {
            return Text.empty();
        }
        return Text.translatable(unlock.titleKey()).formatted(unlock.track().color());
    }

    public static List<ReputationUnlock> reputationUnlocksFor(ReputationTrack track) {
        return REPUTATION_UNLOCKS.stream()
                .filter(unlock -> unlock.track() == track)
                .sorted(Comparator.comparingInt(ReputationUnlock::requiredReputation))
                .toList();
    }

    public static ReputationUnlock nextReputationUnlock(ReputationTrack track, int currentValue) {
        return reputationUnlocksFor(track).stream()
                .filter(unlock -> currentValue < unlock.requiredReputation())
                .min(Comparator.comparingInt(ReputationUnlock::requiredReputation))
                .orElse(null);
    }

    public static boolean hasReputationUnlocks(ReputationTrack track) {
        return !reputationUnlocksFor(track).isEmpty();
    }

    public static Text displayUnlockTitle(ReputationUnlock unlock) {
        if (unlock == null) {
            return Text.empty();
        }
        return Text.translatable(unlock.titleKey()).formatted(unlock.track().color());
    }

    public static Text formatRewardLine(ReputationTrack track, int amount) {
        MutableText amountText = Text.literal("+" + amount).formatted(track.color());
        return Text.translatable("text.village-quest.reputation.reward_line", displayName(track), amountText)
                .formatted(Formatting.GRAY);
    }

    public static Text formatOverviewLine(ServerWorld world, UUID playerId, ReputationTrack track) {
        int currentValue = get(world, playerId, track);
        MutableText value = Text.literal(Integer.toString(currentValue)).formatted(track.color());
        return Text.translatable("text.village-quest.reputation.overview_line", displayName(track), value, displayRank(rankFor(currentValue)))
                .formatted(Formatting.GRAY);
    }

    public static Text formatNextUnlockLine(ServerWorld world, UUID playerId, ReputationTrack track) {
        int currentValue = get(world, playerId, track);
        if (!hasReputationUnlocks(track)) {
            return Text.translatable("text.village-quest.reputation.no_unlocks_yet").formatted(Formatting.GRAY);
        }
        ReputationUnlock nextUnlock = nextReputationUnlock(track, currentValue);
        if (nextUnlock == null) {
            return Text.translatable("text.village-quest.reputation.all_unlocked").formatted(Formatting.DARK_GREEN);
        }
        return Text.translatable(
                "text.village-quest.reputation.next_unlock",
                displayUnlockTitle(nextUnlock),
                Text.literal(Integer.toString(nextUnlock.requiredReputation())).formatted(track.color())
        ).formatted(Formatting.GRAY);
    }

    public static List<Text> buildOverview(ServerWorld world, UUID playerId) {
        List<Text> lines = new ArrayList<>();
        for (ReputationTrack track : ReputationTrack.values()) {
            lines.add(formatOverviewLine(world, playerId, track));
            lines.add(formatNextUnlockLine(world, playerId, track));
        }
        lines.add(Text.translatable(
                "text.village-quest.reputation.overview_total",
                Text.literal(Integer.toString(total(world, playerId))).formatted(Formatting.GOLD)
        ).formatted(Formatting.GRAY));
        return lines;
    }
}
