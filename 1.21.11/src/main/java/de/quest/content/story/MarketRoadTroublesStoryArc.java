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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerProfession;

import java.util.List;
import java.util.UUID;

public final class MarketRoadTroublesStoryArc implements StoryArcDefinition {
    private static final int SHUTTERED_STALLS_EMERALD_TARGET = 80;
    private static final int LEDGER_PAPER_TARGET = 64;
    private static final int LEDGER_BOOK_TARGET = 20;
    private static final int GOODS_MUST_FLOW_TRADE_TARGET = 35;
    private static final int GOODS_MUST_FLOW_PROFESSION_TARGET = 6;
    private static final int MARKET_DAY_RETURNS_VILLAGER_TARGET = 20;
    private static final int MARKET_DAY_RETURNS_BELL_TARGET = 1;
    private static final int MARKET_DAY_RETURNS_BELL_NEARBY_TARGET = 20;
    private static final double MARKET_DAY_RETURNS_BELL_RADIUS = 20.0D;

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

        protected boolean hasItem(ServerPlayerEntity player, Item item, int amount) {
            return DailyQuestService.countInventoryItem(player, item) >= amount;
        }

        protected boolean consumeItem(ServerPlayerEntity player, Item item, int amount) {
            return DailyQuestService.consumeInventoryItem(player, item, amount);
        }

        protected boolean isAdultVillager(Entity entity) {
            return entity instanceof VillagerEntity villager && !villager.isBaby();
        }

        protected void updateCraftProgress(ServerWorld world,
                                           ServerPlayerEntity player,
                                           String baselineKey,
                                           String progressKey,
                                           Item item,
                                           int target) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUuid(), baselineKey);
            int crafted = DailyQuestService.getCraftedStat(player, item);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUuid(), baselineKey, crafted + 1);
                return;
            }

            int delta = Math.max(0, crafted - (baseline - 1));
            int craftedProgress = Math.min(target, delta);
            if (StoryQuestService.getQuestInt(world, player.getUuid(), progressKey) != craftedProgress) {
                StoryQuestService.setQuestInt(world, player.getUuid(), progressKey, craftedProgress);
            }
        }

        protected int countProfessionProgress(ServerWorld world, UUID playerId) {
            int total = 0;
            total += StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_TOOLSMITH) ? 1 : 0;
            total += StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_WEAPONSMITH) ? 1 : 0;
            total += StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_FARMER) ? 1 : 0;
            total += StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_FISHERMAN) ? 1 : 0;
            total += StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_SHEPHERD) ? 1 : 0;
            total += StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_LIBRARIAN) ? 1 : 0;
            return total;
        }

        protected boolean trackProfession(ServerWorld world, UUID playerId, RegistryEntry<VillagerProfession> profession) {
            if (profession.matchesKey(VillagerProfession.TOOLSMITH)) {
                return setProfessionFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_TOOLSMITH);
            }
            if (profession.matchesKey(VillagerProfession.WEAPONSMITH)) {
                return setProfessionFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_WEAPONSMITH);
            }
            if (profession.matchesKey(VillagerProfession.FARMER)) {
                return setProfessionFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_FARMER);
            }
            if (profession.matchesKey(VillagerProfession.FISHERMAN)) {
                return setProfessionFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_FISHERMAN);
            }
            if (profession.matchesKey(VillagerProfession.SHEPHERD)) {
                return setProfessionFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_SHEPHERD);
            }
            if (profession.matchesKey(VillagerProfession.LIBRARIAN)) {
                return setProfessionFlag(world, playerId, StoryQuestKeys.MARKET_ROAD_LIBRARIAN);
            }
            return false;
        }

        private boolean setProfessionFlag(ServerWorld world, UUID playerId, String key) {
            if (StoryQuestService.hasStoryFlag(world, playerId, key)) {
                return false;
            }
            StoryQuestService.setStoryFlag(world, playerId, key, true);
            return true;
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
                    CurrencyService.SILVERMARK * 8L,
                    8,
                    ReputationService.ReputationTrack.TRADE,
                    10,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            if (stack != null && stack.isOf(Items.EMERALD)) {
                addProgress(world, player, StoryQuestKeys.MARKET_ROAD_EMERALDS, stack.getCount(), SHUTTERED_STALLS_EMERALD_TARGET);
            }
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
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.MARKET_ROAD_PAPER_BASELINE, DailyQuestService.getCraftedStat(player, Items.PAPER) + 1);
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.MARKET_ROAD_BOOK_BASELINE, DailyQuestService.getCraftedStat(player, Items.BOOK) + 1);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            updateCraftProgress(world, player, StoryQuestKeys.MARKET_ROAD_PAPER_BASELINE, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED, Items.PAPER, LEDGER_PAPER_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.MARKET_ROAD_BOOK_BASELINE, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED, Items.BOOK, LEDGER_BOOK_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            Text craftedLine = Text.translatable(
                    "quest.village-quest.story.market_road_troubles.chapter_2.progress.1",
                    progress(world, playerId, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED),
                    LEDGER_PAPER_TARGET,
                    progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED),
                    LEDGER_BOOK_TARGET
            ).formatted(Formatting.GRAY);
            ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
            Text blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(craftedLine) : List.of(craftedLine, blocked);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED) >= LEDGER_PAPER_TARGET
                    && progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED) >= LEDGER_BOOK_TARGET
                    && hasItem(player, Items.PAPER, LEDGER_PAPER_TARGET)
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
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED) < LEDGER_PAPER_TARGET
                    || progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED) < LEDGER_BOOK_TARGET
                    || (hasItem(player, Items.PAPER, LEDGER_PAPER_TARGET) && hasItem(player, Items.BOOK, LEDGER_BOOK_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.PAPER.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.PAPER),
                    LEDGER_PAPER_TARGET,
                    Items.BOOK.getDefaultStack().toHoverableText(),
                    DailyQuestService.countInventoryItem(player, Items.BOOK),
                    LEDGER_BOOK_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
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
                            "quest.village-quest.story.market_road_troubles.chapter_3.progress.1",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_TRADES),
                            GOODS_MUST_FLOW_TRADE_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_3.progress.2",
                            countProfessionProgress(world, playerId),
                            GOODS_MUST_FLOW_PROFESSION_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.MARKET_ROAD_TRADES) >= GOODS_MUST_FLOW_TRADE_TARGET
                    && countProfessionProgress(world, playerId) >= GOODS_MUST_FLOW_PROFESSION_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 4L),
                    12,
                    ReputationService.ReputationTrack.TRADE,
                    15,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_TRADES, 1, GOODS_MUST_FLOW_TRADE_TARGET);
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof VillagerEntity villager) || villager.isBaby()) {
                return;
            }
            if (trackProfession(world, player.getUuid(), villager.getVillagerData().profession())) {
                VillagerDialogueService.sendDialogue(player, villager, VillagerDialogueService.marketRoadProfession(villager));
                StoryQuestService.completeIfEligible(world, player);
            }
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
                            "quest.village-quest.story.market_road_troubles.chapter_4.progress.1",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS),
                            MARKET_DAY_RETURNS_VILLAGER_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_4.progress.2",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BELL),
                            MARKET_DAY_RETURNS_BELL_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET
                    && progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BELL) >= MARKET_DAY_RETURNS_BELL_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    20,
                    ReputationService.ReputationTrack.TRADE,
                    40,
                    VillageProjectType.MARKET_CHARTER
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!isAdultVillager(entity)) {
                return;
            }
            String visitFlag = StoryQuestKeys.MARKET_ROAD_GATHERED_PREFIX + entity.getUuidAsString();
            UUID playerId = player.getUuid();
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET
                    || StoryQuestService.hasStoryFlag(world, playerId, visitFlag)) {
                return;
            }
            VillagerDialogueService.sendDialogue(player, (VillagerEntity) entity, VillagerDialogueService.marketRoadGather((VillagerEntity) entity));
            StoryQuestService.setStoryFlag(world, playerId, visitFlag, true);
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_VILLAGERS, 1, MARKET_DAY_RETURNS_VILLAGER_TARGET);
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.story.market_road_troubles.ready_for_bell").formatted(Formatting.GOLD), false);
            }
        }

        @Override
        public void onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack inHand) {
            if (state == null || !state.isOf(Blocks.BELL) || progress(world, player.getUuid(), StoryQuestKeys.MARKET_ROAD_BELL) >= MARKET_DAY_RETURNS_BELL_TARGET) {
                return;
            }
            UUID playerId = player.getUuid();
            int spokenTo = progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS);
            if (spokenTo < MARKET_DAY_RETURNS_VILLAGER_TARGET) {
                player.sendMessage(Text.translatable(
                        "message.village-quest.story.market_road_troubles.villagers_missing",
                        spokenTo,
                        MARKET_DAY_RETURNS_VILLAGER_TARGET
                ).formatted(Formatting.GRAY), true);
                return;
            }

            int villagersNearby = world.getEntitiesByClass(
                    VillagerEntity.class,
                    new Box(pos).expand(MARKET_DAY_RETURNS_BELL_RADIUS),
                    villager -> villager != null && villager.isAlive() && !villager.isRemoved() && !villager.isBaby()
            ).size();
            if (villagersNearby < MARKET_DAY_RETURNS_BELL_NEARBY_TARGET) {
                player.sendMessage(Text.translatable(
                        "message.village-quest.story.market_road_troubles.bell_too_thin",
                        villagersNearby,
                        MARKET_DAY_RETURNS_BELL_NEARBY_TARGET
                ).formatted(Formatting.GRAY), true);
                return;
            }

            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.MARKET_ROAD_BELL, MARKET_DAY_RETURNS_BELL_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }
    }
}
