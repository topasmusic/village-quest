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
import de.quest.util.Texts;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class FailingHarvestStoryArc implements StoryArcDefinition {
    private static final int THIN_FIELDS_WHEAT_TARGET = 128;
    private static final int THIN_FIELDS_POTATO_TARGET = 128;
    private static final int QUIET_HIVES_HONEY_TARGET = 20;
    private static final int QUIET_HIVES_COMB_TARGET = 15;
    private static final int BREAD_BREAD_TARGET = 60;
    private static final int BREAD_POTATO_TARGET = 60;
    private static final int MARKET_TRADES_TARGET = 30;
    private static final int MARKET_EMERALDS_TARGET = 128;

    private final List<StoryChapterDefinition> chapters = List.of(
            new ThinFieldsChapter(),
            new QuietHivesChapter(),
            new BreadForTheSquareChapter(),
            new MarketReliefChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.FAILING_HARVEST;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.story.failing_harvest.title");
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

    private abstract static class FailingHarvestChapter implements StoryChapterDefinition {
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
    }

    private static final class ThinFieldsChapter extends FailingHarvestChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_1.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_1.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_1.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(Text.translatable(
                    "quest.village-quest.story.failing_harvest.chapter_1.progress",
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_WHEAT),
                    THIN_FIELDS_WHEAT_TARGET,
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_POTATO),
                    THIN_FIELDS_POTATO_TARGET
            ).formatted(Formatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_WHEAT) >= THIN_FIELDS_WHEAT_TARGET
                    && progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_POTATO) >= THIN_FIELDS_POTATO_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.SILVERMARK * 8L,
                    8,
                    ReputationService.ReputationTrack.FARMING,
                    10,
                    null
            );
        }

        @Override
        public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof CropBlock crop)) {
                return;
            }
            if (!state.contains(CropBlock.AGE) || state.get(CropBlock.AGE) < crop.getMaxAge()) {
                return;
            }
            if (state.isOf(Blocks.WHEAT)) {
                addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_WHEAT, 1, THIN_FIELDS_WHEAT_TARGET);
            } else if (state.isOf(Blocks.POTATOES)) {
                addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_POTATO, 1, THIN_FIELDS_POTATO_TARGET);
            }
        }
    }

    private static final class QuietHivesChapter extends FailingHarvestChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_2.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_2.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_2.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(Text.translatable(
                    "quest.village-quest.story.failing_harvest.chapter_2.progress",
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_HONEY),
                    QUIET_HIVES_HONEY_TARGET,
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_COMB),
                    QUIET_HIVES_COMB_TARGET
            ).formatted(Formatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_HONEY) >= QUIET_HIVES_HONEY_TARGET
                    && progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_COMB) >= QUIET_HIVES_COMB_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.FARMING,
                    12,
                    null
            );
        }

        @Override
        public void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack inHand) {
            if (!state.isOf(Blocks.BEE_NEST) && !state.isOf(Blocks.BEEHIVE)) {
                return;
            }
            if (!state.contains(BeehiveBlock.HONEY_LEVEL) || state.get(BeehiveBlock.HONEY_LEVEL) < 5) {
                return;
            }
            if (inHand.isOf(Items.GLASS_BOTTLE)) {
                addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_HONEY, 1, QUIET_HIVES_HONEY_TARGET);
            } else if (inHand.isOf(Items.SHEARS)) {
                addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_COMB, 1, QUIET_HIVES_COMB_TARGET);
            }
        }
    }

    private static final class BreadForTheSquareChapter extends FailingHarvestChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_3.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_3.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_3.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            Text line = Text.translatable(
                    "quest.village-quest.story.failing_harvest.chapter_3.progress",
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD),
                    BREAD_BREAD_TARGET,
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_BAKED_POTATO),
                    BREAD_POTATO_TARGET
            ).formatted(Formatting.GRAY);
            ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(line) : List.of(line, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD) >= BREAD_BREAD_TARGET
                    && progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_BAKED_POTATO) >= BREAD_POTATO_TARGET
                    && hasItem(player, Items.BREAD, BREAD_BREAD_TARGET)
                    && hasItem(player, Items.BAKED_POTATO, BREAD_POTATO_TARGET);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            if (!isComplete(world, player)) {
                return false;
            }
            return consumeItem(player, Items.BREAD, BREAD_BREAD_TARGET)
                    && consumeItem(player, Items.BAKED_POTATO, BREAD_POTATO_TARGET);
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD) < BREAD_BREAD_TARGET
                    || progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_BAKED_POTATO) < BREAD_POTATO_TARGET
                    || (hasItem(player, Items.BREAD, BREAD_BREAD_TARGET)
                    && hasItem(player, Items.BAKED_POTATO, BREAD_POTATO_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.BREAD.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.BREAD),
                    BREAD_BREAD_TARGET,
                    Items.BAKED_POTATO.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.BAKED_POTATO),
                    BREAD_POTATO_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_3.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 4L),
                    12,
                    ReputationService.ReputationTrack.FARMING,
                    15,
                    null
            );
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.setQuestInt(
                    world,
                    player.getUuid(),
                    StoryQuestKeys.FAILING_HARVEST_BREAD_BASELINE,
                    DailyQuestService.getCraftedStat(player, Items.BREAD) + 1
            );
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            int craftedBread = DailyQuestService.getCraftedStat(player, Items.BREAD);
            int storedBaseline = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD_BASELINE);
            if (storedBaseline == 0) {
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD_BASELINE, craftedBread + 1);
                return;
            }

            int delta = craftedBread - (storedBaseline - 1);
            if (delta > 0) {
                StoryQuestService.addQuestIntClamped(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD, delta, BREAD_BREAD_TARGET);
                StoryQuestService.completeIfEligible(world, player);
            }
            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.FAILING_HARVEST_BREAD_BASELINE, craftedBread + 1);
        }

        @Override
        public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (!stack.isOf(Items.BAKED_POTATO)) {
                return;
            }
            addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_BAKED_POTATO, stack.getCount(), BREAD_POTATO_TARGET);
        }
    }

    private static final class MarketReliefChapter extends FailingHarvestChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_4.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_4.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.failing_harvest.chapter_4.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(Text.translatable(
                    "quest.village-quest.story.failing_harvest.chapter_4.progress",
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_TRADES),
                    MARKET_TRADES_TARGET,
                    progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_EMERALDS),
                    MARKET_EMERALDS_TARGET
            ).formatted(Formatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_TRADES) >= MARKET_TRADES_TARGET
                    && progress(world, playerId, StoryQuestKeys.FAILING_HARVEST_EMERALDS) >= MARKET_EMERALDS_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.failing_harvest.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    20,
                    ReputationService.ReputationTrack.FARMING,
                    40,
                    VillageProjectType.APIARY_CHARTER
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_TRADES, 1, MARKET_TRADES_TARGET);
            if (stack.isOf(Items.EMERALD)) {
                addProgress(world, player, StoryQuestKeys.FAILING_HARVEST_EMERALDS, stack.getCount(), MARKET_EMERALDS_TARGET);
            }
        }
    }
}
