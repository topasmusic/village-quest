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
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    public Component title() {
        return Component.translatable("quest.village-quest.story.market_road_troubles.title");
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
    public boolean isUnlocked(ServerLevel world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.SILENT_FORGE);
    }

    private abstract static class MarketRoadChapter implements StoryChapterDefinition {
        protected void addProgress(ServerLevel world, ServerPlayer player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUUID(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerLevel world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
            return DailyQuestService.countInventoryItem(player, item) >= amount;
        }

        protected boolean consumeItem(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
            return DailyQuestService.consumeInventoryItem(player, item, amount);
        }

        protected boolean isEmployedVillager(Entity entity) {
            if (!(entity instanceof Villager villager) || villager.isBaby()) {
                return false;
            }
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            return !profession.is(VillagerProfession.NONE) && !profession.is(VillagerProfession.NITWIT);
        }
    }

    private static final class ShutteredStallsChapter extends MarketRoadChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_1.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_1.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_1.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_1.progress",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_EMERALDS),
                            SHUTTERED_STALLS_EMERALD_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.MARKET_ROAD_EMERALDS) >= SHUTTERED_STALLS_EMERALD_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_1.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_1.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_1.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 4L,
                    4,
                    ReputationService.ReputationTrack.TRADE,
                    10,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
            if (!stack.is(Items.EMERALD)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_EMERALDS, stack.getCount(), SHUTTERED_STALLS_EMERALD_TARGET);
        }
    }

    private static final class LedgerAndNoticesChapter extends MarketRoadChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            int paper = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.PAPER);
            int books = player == null ? 0 : DailyQuestService.countInventoryItem(player, Items.BOOK);
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_2.progress",
                            paper,
                            LEDGER_PAPER_TARGET,
                            books,
                            LEDGER_BOOK_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return hasItem(player, Items.PAPER, LEDGER_PAPER_TARGET)
                    && hasItem(player, Items.BOOK, LEDGER_BOOK_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
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
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
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
        public Component title() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_3.progress",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_TRADES),
                            GOODS_MUST_FLOW_TRADE_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.MARKET_ROAD_TRADES) >= GOODS_MUST_FLOW_TRADE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.SILVERMARK * 7L,
                    8,
                    ReputationService.ReputationTrack.TRADE,
                    15,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_TRADES, 1, GOODS_MUST_FLOW_TRADE_TARGET);
        }
    }

    private static final class MarketDayReturnsChapter extends MarketRoadChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_4.progress",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS),
                            MARKET_DAY_RETURNS_VILLAGER_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.TRADE,
                    20,
                    VillageProjectType.MARKET_CHARTER
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!isEmployedVillager(entity)) {
                return;
            }
            String visitFlag = StoryQuestKeys.MARKET_ROAD_VISITED_PREFIX + entity.getStringUUID();
            UUID playerId = player.getUUID();
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
                player.sendSystemMessage(Component.translatable("message.village-quest.story.market_road_troubles.stalls_awake").withStyle(ChatFormatting.GOLD), false);
            }
        }
    }
}
