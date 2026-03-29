package de.quest.content.story;

import de.quest.economy.CurrencyService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.VillagerProfession;

import java.util.List;
import java.util.UUID;

public final class MarketRoadTroublesStoryArc implements StoryArcDefinition {
    private static final int SHUTTERED_STALLS_EMERALD_TARGET = 32;
    private static final int LEDGER_PAPER_TARGET = 32;
    private static final int LEDGER_BOOK_TARGET = 3;
    private static final int GOODS_MUST_FLOW_TRADE_TARGET = 10;
    private static final int MARKET_DAY_RETURNS_VILLAGER_TARGET = 5;

    private final List<StoryChapterDefinition> chapters = List.of(
            new ShutteredStallsChapter(),
            new LedgerAndNoticesChapter(),
            new GoodsMustFlowChapter(),
            new MarketDayReturnsChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.MARKET_ROAD_TROUBLES;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.story.market_road_troubles.title");
    }

    @Override
    public int chapterCount() {
        return chapters.size();
    }

    @Override
    public StoryChapterDefinition chapter(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size()) {
            return null;
        }
        return chapters.get(chapterIndex);
    }

    @Override
    public boolean isUnlocked(ServerWorld world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.SILENT_FORGE);
    }

    private abstract static class MarketRoadChapter implements StoryChapterDefinition {
        protected void addProgress(ServerWorld world, ServerPlayerEntity player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUuid(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerWorld world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean hasItem(ServerPlayerEntity player, net.minecraft.item.Item item, int amount) {
            return DailyQuestService.countInventoryItem(player, item) >= amount;
        }

        protected boolean consumeItem(ServerPlayerEntity player, net.minecraft.item.Item item, int amount) {
            return DailyQuestService.consumeInventoryItem(player, item, amount);
        }

        protected boolean isEmployedVillager(Entity entity) {
            if (!(entity instanceof VillagerEntity villager) || villager.isBaby()) {
                return false;
            }
            RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
            return !profession.matchesKey(VillagerProfession.NONE) && !profession.matchesKey(VillagerProfession.NITWIT);
        }
    }

    private static final class ShutteredStallsChapter extends MarketRoadChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_1.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_1.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_1.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_1.progress",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_EMERALDS),
                            SHUTTERED_STALLS_EMERALD_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.MARKET_ROAD_EMERALDS) >= SHUTTERED_STALLS_EMERALD_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.TRADE,
                    10,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (!stack.isOf(Items.EMERALD)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_EMERALDS, stack.getCount(), SHUTTERED_STALLS_EMERALD_TARGET);
        }
    }

    private static final class LedgerAndNoticesChapter extends MarketRoadChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            int paper = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.PAPER);
            int books = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.BOOK);
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_2.progress",
                            paper,
                            LEDGER_PAPER_TARGET,
                            books,
                            LEDGER_BOOK_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return hasItem(player, Items.PAPER, LEDGER_PAPER_TARGET)
                    && hasItem(player, Items.BOOK, LEDGER_BOOK_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumeItem(player, Items.PAPER, LEDGER_PAPER_TARGET)
                    && consumeItem(player, Items.BOOK, LEDGER_BOOK_TARGET);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 5L,
                    6,
                    ReputationService.ReputationTrack.TRADE,
                    12,
                    null
            );
        }
    }

    private static final class GoodsMustFlowChapter extends MarketRoadChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_TRADES),
                            GOODS_MUST_FLOW_TRADE_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.MARKET_ROAD_TRADES) >= GOODS_MUST_FLOW_TRADE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 7L,
                    8,
                    ReputationService.ReputationTrack.TRADE,
                    15,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_TRADES, 1, GOODS_MUST_FLOW_TRADE_TARGET);
        }
    }

    private static final class MarketDayReturnsChapter extends MarketRoadChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_4.progress",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS),
                            MARKET_DAY_RETURNS_VILLAGER_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.TRADE,
                    20,
                    VillageProjectType.MARKET_CHARTER
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!isEmployedVillager(entity)) {
                return;
            }
            String visitFlag = StoryQuestKeys.MARKET_ROAD_VISITED_PREFIX + entity.getUuidAsString();
            UUID playerId = player.getUuid();
            if (StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET
                    || StoryQuestService.chapter(world, playerId, StoryArcType.MARKET_ROAD_TROUBLES) != this) {
                return;
            }
            if (StoryQuestService.hasStoryFlag(world, playerId, visitFlag)) {
                return;
            }
            StoryQuestService.setStoryFlag(world, playerId, visitFlag, true);
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_VILLAGERS, 1, MARKET_DAY_RETURNS_VILLAGER_TARGET);
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.story.market_road_troubles.stalls_awake").formatted(Formatting.GOLD), false);
            }
        }
    }
}
