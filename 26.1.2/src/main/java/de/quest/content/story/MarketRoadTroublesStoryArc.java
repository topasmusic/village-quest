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
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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

    private static final ProfessionTarget[] GOODS_MUST_FLOW_PROFESSIONS = new ProfessionTarget[] {
            new ProfessionTarget(StoryQuestKeys.MARKET_ROAD_TOOLSMITH, VillagerProfession.TOOLSMITH),
            new ProfessionTarget(StoryQuestKeys.MARKET_ROAD_WEAPONSMITH, VillagerProfession.WEAPONSMITH),
            new ProfessionTarget(StoryQuestKeys.MARKET_ROAD_FARMER, VillagerProfession.FARMER),
            new ProfessionTarget(StoryQuestKeys.MARKET_ROAD_FISHERMAN, VillagerProfession.FISHERMAN),
            new ProfessionTarget(StoryQuestKeys.MARKET_ROAD_SHEPHERD, VillagerProfession.SHEPHERD),
            new ProfessionTarget(StoryQuestKeys.MARKET_ROAD_LIBRARIAN, VillagerProfession.LIBRARIAN)
    };

    private final List<StoryChapterDefinition> chapters = List.of(
            new ShutteredStallsChapter(),
            new LedgerAndNoticesChapter(),
            new GoodsMustFlowChapter(),
            new MarketDayReturnsChapter()
    );

    private record ProfessionTarget(String key, ResourceKey<VillagerProfession> profession) {}

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

        protected boolean hasItem(ServerPlayer player, Item item, int amount) {
            return DailyQuestService.countInventoryItem(player, item) >= amount;
        }

        protected boolean consumeItem(ServerPlayer player, Item item, int amount) {
            return DailyQuestService.consumeInventoryItem(player, item, amount);
        }

        protected boolean isAdultVillager(Entity entity) {
            return entity instanceof Villager villager && !villager.isBaby();
        }

        protected void updateCraftProgress(ServerLevel world,
                                           ServerPlayer player,
                                           String baselineKey,
                                           String progressKey,
                                           Item item,
                                           int target) {
            int baseline = StoryQuestService.getQuestInt(world, player.getUUID(), baselineKey);
            int crafted = DailyQuestService.getCraftedStat(player, item);
            if (baseline == 0) {
                StoryQuestService.setQuestInt(world, player.getUUID(), baselineKey, crafted + 1);
                return;
            }

            int delta = Math.max(0, crafted - (baseline - 1));
            int progress = Math.min(target, delta);
            if (StoryQuestService.getQuestInt(world, player.getUUID(), progressKey) != progress) {
                StoryQuestService.setQuestInt(world, player.getUUID(), progressKey, progress);
            }
        }

        protected int countProfessionProgress(ServerLevel world, UUID playerId) {
            int total = 0;
            for (ProfessionTarget target : GOODS_MUST_FLOW_PROFESSIONS) {
                if (StoryQuestService.hasStoryFlag(world, playerId, target.key())) {
                    total++;
                }
            }
            return total;
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
                    CurrencyService.SILVERMARK * 8L,
                    8,
                    ReputationService.ReputationTrack.TRADE,
                    10,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
            if (stack != null && stack.is(Items.EMERALD)) {
                addProgress(world, player, StoryQuestKeys.MARKET_ROAD_EMERALDS, stack.getCount(), SHUTTERED_STALLS_EMERALD_TARGET);
            }
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
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.MARKET_ROAD_PAPER_BASELINE, DailyQuestService.getCraftedStat(player, Items.PAPER) + 1);
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.MARKET_ROAD_BOOK_BASELINE, DailyQuestService.getCraftedStat(player, Items.BOOK) + 1);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            updateCraftProgress(world, player, StoryQuestKeys.MARKET_ROAD_PAPER_BASELINE, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED, Items.PAPER, LEDGER_PAPER_TARGET);
            updateCraftProgress(world, player, StoryQuestKeys.MARKET_ROAD_BOOK_BASELINE, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED, Items.BOOK, LEDGER_BOOK_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            Component craftedLine = Component.translatable(
                    "quest.village-quest.story.market_road_troubles.chapter_2.progress.1",
                    progress(world, playerId, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED),
                    LEDGER_PAPER_TARGET,
                    progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED),
                    LEDGER_BOOK_TARGET
            ).withStyle(ChatFormatting.GRAY);
            ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
            Component blocked = player == null ? null : claimBlockedMessage(world, player);
            return blocked == null ? List.of(craftedLine) : List.of(craftedLine, blocked);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED) >= LEDGER_PAPER_TARGET
                    && progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED) >= LEDGER_BOOK_TARGET
                    && hasItem(player, Items.PAPER, LEDGER_PAPER_TARGET)
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
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            if (player == null || world == null) {
                return null;
            }
            UUID playerId = player.getUUID();
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED) < LEDGER_PAPER_TARGET
                    || progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED) < LEDGER_BOOK_TARGET
                    || (hasItem(player, Items.PAPER, LEDGER_PAPER_TARGET) && hasItem(player, Items.BOOK, LEDGER_BOOK_TARGET))) {
                return null;
            }
            return Texts.turnInMissing(
                    Items.PAPER.getDefaultInstance().getHoverName(),
                    DailyQuestService.countInventoryItem(player, Items.PAPER),
                    LEDGER_PAPER_TARGET,
                    Items.BOOK.getDefaultInstance().getHoverName(),
                    DailyQuestService.countInventoryItem(player, Items.BOOK),
                    LEDGER_BOOK_TARGET
            );
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
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
                            "quest.village-quest.story.market_road_troubles.chapter_3.progress.1",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_TRADES),
                            GOODS_MUST_FLOW_TRADE_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_3.progress.2",
                            countProfessionProgress(world, playerId),
                            GOODS_MUST_FLOW_PROFESSION_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.MARKET_ROAD_TRADES) >= GOODS_MUST_FLOW_TRADE_TARGET
                    && countProfessionProgress(world, playerId) >= GOODS_MUST_FLOW_PROFESSION_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 4L),
                    12,
                    ReputationService.ReputationTrack.TRADE,
                    15,
                    null
            );
        }

        @Override
        public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_TRADES, 1, GOODS_MUST_FLOW_TRADE_TARGET);
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof Villager villager) || villager.isBaby()) {
                return;
            }
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            for (ProfessionTarget target : GOODS_MUST_FLOW_PROFESSIONS) {
                if (!profession.is(target.profession()) || StoryQuestService.hasStoryFlag(world, player.getUUID(), target.key())) {
                    continue;
                }
                VillagerDialogueService.sendDialogue(player, villager, VillagerDialogueService.marketRoadProfession(villager));
                StoryQuestService.setStoryFlag(world, player.getUUID(), target.key(), true);
                StoryQuestService.completeIfEligible(world, player);
                return;
            }
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
                            "quest.village-quest.story.market_road_troubles.chapter_4.progress.1",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS),
                            MARKET_DAY_RETURNS_VILLAGER_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.market_road_troubles.chapter_4.progress.2",
                            progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BELL),
                            MARKET_DAY_RETURNS_BELL_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET
                    && progress(world, playerId, StoryQuestKeys.MARKET_ROAD_BELL) >= MARKET_DAY_RETURNS_BELL_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.market_road_troubles.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    20,
                    ReputationService.ReputationTrack.TRADE,
                    40,
                    VillageProjectType.MARKET_CHARTER
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!isAdultVillager(entity)) {
                return;
            }
            String visitFlag = StoryQuestKeys.MARKET_ROAD_GATHERED_PREFIX + entity.getStringUUID();
            UUID playerId = player.getUUID();
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET
                    || StoryQuestService.hasStoryFlag(world, playerId, visitFlag)) {
                return;
            }
            VillagerDialogueService.sendDialogue(player, (Villager) entity, VillagerDialogueService.marketRoadGather((Villager) entity));
            StoryQuestService.setStoryFlag(world, playerId, visitFlag, true);
            addProgress(world, player, StoryQuestKeys.MARKET_ROAD_VILLAGERS, 1, MARKET_DAY_RETURNS_VILLAGER_TARGET);
            if (progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS) >= MARKET_DAY_RETURNS_VILLAGER_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.story.market_road_troubles.ready_for_bell").withStyle(ChatFormatting.GOLD), false);
            }
        }

        @Override
        public void onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, ItemStack inHand) {
            if (state == null || !state.is(Blocks.BELL) || progress(world, player.getUUID(), StoryQuestKeys.MARKET_ROAD_BELL) >= MARKET_DAY_RETURNS_BELL_TARGET) {
                return;
            }
            UUID playerId = player.getUUID();
            int spokenTo = progress(world, playerId, StoryQuestKeys.MARKET_ROAD_VILLAGERS);
            if (spokenTo < MARKET_DAY_RETURNS_VILLAGER_TARGET) {
                player.sendSystemMessage(Component.translatable(
                        "message.village-quest.story.market_road_troubles.villagers_missing",
                        spokenTo,
                        MARKET_DAY_RETURNS_VILLAGER_TARGET
                ).withStyle(ChatFormatting.GRAY), true);
                return;
            }

            int villagersNearby = world.getEntitiesOfClass(
                    Villager.class,
                    new AABB(pos).inflate(MARKET_DAY_RETURNS_BELL_RADIUS),
                    villager -> villager != null
                            && villager.isAlive()
                            && !villager.isRemoved()
                            && !villager.isBaby()
            ).size();
            if (villagersNearby < MARKET_DAY_RETURNS_BELL_NEARBY_TARGET) {
                player.sendSystemMessage(Component.translatable(
                        "message.village-quest.story.market_road_troubles.bell_too_thin",
                        villagersNearby,
                        MARKET_DAY_RETURNS_BELL_NEARBY_TARGET
                ).withStyle(ChatFormatting.GRAY), true);
                return;
            }

            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.MARKET_ROAD_BELL, MARKET_DAY_RETURNS_BELL_TARGET);
            StoryQuestService.completeIfEligible(world, player);
        }
    }
}
