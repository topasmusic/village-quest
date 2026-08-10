package de.quest.content.story;

import de.quest.caravan.TradeRouteService;
import de.quest.economy.CurrencyService;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class TheEmptyCaravanStoryArc implements StoryArcDefinition {
    private final List<StoryChapterDefinition> chapters = List.of(
            new CartThatReturnedAloneChapter(),
            new ThreeBrokenSignsChapter(),
            new InkBeneathWaxChapter(),
            new NamesBehindSealChapter(),
            new CaravanMadeOfBaitChapter(),
            new RoadsBetweenVillagesChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.THE_EMPTY_CARAVAN;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.story.the_empty_caravan.title");
    }

    @Override
    public int chapterCount() {
        return chapters.size();
    }

    @Override
    public StoryChapterDefinition chapter(int chapterIndex) {
        return chapterIndex < 0 || chapterIndex >= chapters.size() ? null : chapters.get(chapterIndex);
    }

    @Override
    public boolean isUnlocked(ServerWorld world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
    }

    @Override
    public boolean shouldShowLockedEntry(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && !isUnlocked(world, playerId)
                && StoryQuestService.isActive(world, playerId, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
    }

    @Override
    public Text lockedEntryBody(ServerWorld world, UUID playerId) {
        return Text.translatable("screen.village-quest.questmaster.story.the_empty_caravan.locked");
    }

    private abstract static class EmptyCaravanChapter implements StoryChapterDefinition {
        protected int progress(ServerWorld world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected StoryChapterCompletion completion(int chapter,
                                                    long currency,
                                                    int levels,
                                                    ReputationService.ReputationTrack track,
                                                    int reputation,
                                                    VillageProjectType project) {
            String prefix = "quest.village-quest.story.the_empty_caravan.chapter_" + chapter;
            return new StoryChapterCompletion(
                    Text.translatable(prefix + ".title"),
                    Text.translatable(prefix + ".complete.1").formatted(Formatting.GRAY),
                    Text.translatable(prefix + ".complete.2").formatted(Formatting.GRAY),
                    Text.translatable(prefix + ".complete.3").formatted(Formatting.GRAY),
                    currency,
                    levels,
                    track,
                    reputation,
                    project
            );
        }

        protected Text title(int chapter) {
            String key = "quest.village-quest.story.the_empty_caravan.chapter_" + chapter + ".title";
            return Text.translatable(key);
        }

        protected Text offer(int chapter, int paragraph) {
            String key = "quest.village-quest.story.the_empty_caravan.chapter_" + chapter + ".offer." + paragraph;
            return Text.translatable(key).formatted(Formatting.GRAY);
        }
    }

    private static final class CartThatReturnedAloneChapter extends EmptyCaravanChapter {
        @Override public Text title() { return title(1); }
        @Override public Text offerParagraph1() { return offer(1, 1); }
        @Override public Text offerParagraph2() { return offer(1, 2); }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            EmptyCaravanStoryService.beginEmptySite(world, player);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_1.progress",
                    progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_SITE_FOUND), 1).formatted(Formatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_SITE_FOUND) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(1, CurrencyService.CROWN + 4L, 10, ReputationService.ReputationTrack.TRADE, 12, null);
        }
    }

    private static final class ThreeBrokenSignsChapter extends EmptyCaravanChapter {
        @Override public Text title() { return title(2); }
        @Override public Text offerParagraph1() { return offer(2, 1); }
        @Override public Text offerParagraph2() { return offer(2, 2); }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            EmptyCaravanStoryService.beginClueTrail(world, player);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_2.progress",
                    progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CLUES), EmptyCaravanStoryService.CLUE_TARGET)
                    .formatted(Formatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_CLUES) >= EmptyCaravanStoryService.CLUE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(2, CurrencyService.CROWN + 8L, 12, ReputationService.ReputationTrack.CRAFTING, 14, null);
        }
    }

    private static final class InkBeneathWaxChapter extends EmptyCaravanChapter {
        private static final int PAPER = 12;
        private static final int INK = 3;
        private static final int HONEYCOMB = 2;

        @Override public Text title() { return title(3); }
        @Override public Text offerParagraph1() { return offer(3, 1); }
        @Override public Text offerParagraph2() { return offer(3, 2); }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    itemProgress(world, playerId, Items.PAPER, PAPER, "paper"),
                    itemProgress(world, playerId, Items.INK_SAC, INK, "ink"),
                    itemProgress(world, playerId, Items.HONEYCOMB, HONEYCOMB, "honeycomb")
            );
        }

        private Text itemProgress(ServerWorld world, UUID playerId, Item item, int target, String suffix) {
            return Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_3.progress." + suffix,
                    Math.min(target, StoryQuestService.countCompletionItem(world, playerId, item)), target)
                    .formatted(Formatting.GRAY);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID id = player.getUuid();
            return StoryQuestService.countCompletionItem(world, id, Items.PAPER) >= PAPER
                    && StoryQuestService.countCompletionItem(world, id, Items.INK_SAC) >= INK
                    && StoryQuestService.countCompletionItem(world, id, Items.HONEYCOMB) >= HONEYCOMB;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player)
                    && StoryQuestService.consumeCompletionItems(world, player.getUuid(), Map.of(
                    Items.PAPER, PAPER,
                    Items.INK_SAC, INK,
                    Items.HONEYCOMB, HONEYCOMB
            ));
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(3, CurrencyService.CROWN * 2L, 14, ReputationService.ReputationTrack.TRADE, 18, null);
        }
    }

    private static final class NamesBehindSealChapter extends EmptyCaravanChapter {
        @Override public Text title() { return title(4); }
        @Override public Text offerParagraph1() { return offer(4, 1); }
        @Override public Text offerParagraph2() { return offer(4, 2); }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_4.progress",
                    progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_WITNESSES), EmptyCaravanStoryService.WITNESS_TARGET)
                    .formatted(Formatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_WITNESSES) >= EmptyCaravanStoryService.WITNESS_TARGET;
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof VillagerEntity villager) || villager.isBaby()) {
                return;
            }
            VillagerProfession profession = villager.getVillagerData().getProfession();
            String witness = profession == VillagerProfession.CARTOGRAPHER ? "cartographer"
                    : profession == VillagerProfession.LIBRARIAN ? "librarian"
                    : profession == VillagerProfession.CLERIC ? "cleric" : null;
            if (witness == null) {
                player.sendMessage(Text.translatable("message.village-quest.story.the_empty_caravan.witness_wrong")
                        .formatted(Formatting.GRAY), true);
                return;
            }
            String flag = StoryQuestKeys.EMPTY_CARAVAN_WITNESS_PREFIX + witness;
            if (StoryQuestService.hasStoryFlag(world, player.getUuid(), flag)) {
                return;
            }
            StoryQuestService.setStoryFlag(world, player.getUuid(), flag, true);
            StoryQuestService.addQuestIntClamped(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_WITNESSES,
                    1, EmptyCaravanStoryService.WITNESS_TARGET);
            player.sendMessage(Text.translatable("message.village-quest.story.the_empty_caravan.witness." + witness)
                    .formatted(Formatting.GOLD), false);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(4, CurrencyService.CROWN * 2L + 5L, 16, ReputationService.ReputationTrack.TRADE, 20, null);
        }
    }

    private static final class CaravanMadeOfBaitChapter extends EmptyCaravanChapter {
        @Override public Text title() { return title(5); }
        @Override public Text offerParagraph1() { return offer(5, 1); }
        @Override public Text offerParagraph2() { return offer(5, 2); }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            boolean choiceMade = StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY)
                    || StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CHOICE_JUSTICE);
            int bait = progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE);
            return List.of(
                    Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_5.progress.choice", choiceMade ? 1 : 0, 1)
                            .formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_5.progress.bait",
                            bait >= EmptyCaravanStoryService.BAIT_WON ? 1 : 0, 1).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE)
                    >= EmptyCaravanStoryService.BAIT_WON;
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            EmptyCaravanStoryService.chooseApproach(world, player, entity, inHand);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(5, CurrencyService.CROWN * 3L, 22, ReputationService.ReputationTrack.MONSTER_HUNTING, 24, null);
        }

        @Override
        public void onClaimed(ServerWorld world, ServerPlayerEntity player) {
            boolean amnesty = StoryQuestService.hasStoryFlag(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY);
            if (amnesty) {
                ReputationService.add(world, player.getUuid(), ReputationService.ReputationTrack.TRADE, 10);
            } else {
                CurrencyService.addBalance(world, player.getUuid(), CurrencyService.CROWN);
                ReputationService.add(world, player.getUuid(), ReputationService.ReputationTrack.MONSTER_HUNTING, 8);
            }
            player.sendMessage(Text.translatable(amnesty
                    ? "message.village-quest.story.the_empty_caravan.outcome.amnesty"
                    : "message.village-quest.story.the_empty_caravan.outcome.justice").formatted(Formatting.GOLD), false);
        }
    }

    private static final class RoadsBetweenVillagesChapter extends EmptyCaravanChapter {
        private static final int GRAVEL = 32;
        private static final int STONE_BRICKS = 24;
        private static final int LANTERNS = 8;
        private static final int LEADS = 4;

        @Override public Text title() { return title(6); }
        @Override public Text offerParagraph1() { return offer(6, 1); }
        @Override public Text offerParagraph2() { return offer(6, 2); }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    progressLine(world, playerId, Items.GRAVEL, GRAVEL, "gravel"),
                    progressLine(world, playerId, Items.STONE_BRICKS, STONE_BRICKS, "stone_bricks"),
                    progressLine(world, playerId, Items.LANTERN, LANTERNS, "lanterns"),
                    progressLine(world, playerId, Items.LEAD, LEADS, "leads")
            );
        }

        private Text progressLine(ServerWorld world, UUID id, Item item, int target, String suffix) {
            return Text.translatable("quest.village-quest.story.the_empty_caravan.chapter_6.progress." + suffix,
                    Math.min(target, StoryQuestService.countCompletionItem(world, id, item)), target)
                    .formatted(Formatting.GRAY);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID id = player.getUuid();
            return StoryQuestService.countCompletionItem(world, id, Items.GRAVEL) >= GRAVEL
                    && StoryQuestService.countCompletionItem(world, id, Items.STONE_BRICKS) >= STONE_BRICKS
                    && StoryQuestService.countCompletionItem(world, id, Items.LANTERN) >= LANTERNS
                    && StoryQuestService.countCompletionItem(world, id, Items.LEAD) >= LEADS;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player)
                    && StoryQuestService.consumeCompletionItems(world, player.getUuid(), Map.of(
                    Items.GRAVEL, GRAVEL,
                    Items.STONE_BRICKS, STONE_BRICKS,
                    Items.LANTERN, LANTERNS,
                    Items.LEAD, LEADS
            ));
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(6, CurrencyService.CROWN * 4L, 28, ReputationService.ReputationTrack.TRADE, 35,
                    VillageProjectType.CARAVAN_YARD);
        }

        @Override
        public void onClaimed(ServerWorld world, ServerPlayerEntity player) {
            TradeRouteService.initializeCaravanYard(world, player);
        }
    }
}
