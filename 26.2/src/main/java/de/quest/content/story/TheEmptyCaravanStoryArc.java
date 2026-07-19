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
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    public Component title() {
        return Component.translatable("quest.village-quest.story.the_empty_caravan.title");
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
    public boolean isUnlocked(ServerLevel world, UUID playerId) {
        return StoryQuestService.isCompleted(world, playerId, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
    }

    @Override
    public boolean shouldShowLockedEntry(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && !isUnlocked(world, playerId)
                && StoryQuestService.isActive(world, playerId, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
    }

    @Override
    public Component lockedEntryBody(ServerLevel world, UUID playerId) {
        return Component.translatable("screen.village-quest.questmaster.story.the_empty_caravan.locked");
    }

    private abstract static class EmptyCaravanChapter implements StoryChapterDefinition {
        protected int progress(ServerLevel world, UUID playerId, String key) {
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
                    Component.translatable(prefix + ".title"),
                    Component.translatable(prefix + ".complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable(prefix + ".complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable(prefix + ".complete.3").withStyle(ChatFormatting.GRAY),
                    currency,
                    levels,
                    track,
                    reputation,
                    project
            );
        }

        protected Component title(int chapter) {
            String key = "quest.village-quest.story.the_empty_caravan.chapter_" + chapter + ".title";
            return Component.translatable(key);
        }

        protected Component offer(int chapter, int paragraph) {
            String key = "quest.village-quest.story.the_empty_caravan.chapter_" + chapter + ".offer." + paragraph;
            return Component.translatable(key).withStyle(ChatFormatting.GRAY);
        }
    }

    private static final class CartThatReturnedAloneChapter extends EmptyCaravanChapter {
        @Override public Component title() { return title(1); }
        @Override public Component offerParagraph1() { return offer(1, 1); }
        @Override public Component offerParagraph2() { return offer(1, 2); }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            EmptyCaravanStoryService.beginEmptySite(world, player);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_1.progress",
                    progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_SITE_FOUND), 1).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.EMPTY_CARAVAN_SITE_FOUND) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(1, CurrencyService.CROWN + 4L, 10, ReputationService.ReputationTrack.TRADE, 12, null);
        }
    }

    private static final class ThreeBrokenSignsChapter extends EmptyCaravanChapter {
        @Override public Component title() { return title(2); }
        @Override public Component offerParagraph1() { return offer(2, 1); }
        @Override public Component offerParagraph2() { return offer(2, 2); }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            EmptyCaravanStoryService.beginClueTrail(world, player);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_2.progress",
                    progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CLUES), EmptyCaravanStoryService.CLUE_TARGET)
                    .withStyle(ChatFormatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.EMPTY_CARAVAN_CLUES) >= EmptyCaravanStoryService.CLUE_TARGET;
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

        @Override public Component title() { return title(3); }
        @Override public Component offerParagraph1() { return offer(3, 1); }
        @Override public Component offerParagraph2() { return offer(3, 2); }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    itemProgress(world, playerId, Items.PAPER, PAPER, "paper"),
                    itemProgress(world, playerId, Items.INK_SAC, INK, "ink"),
                    itemProgress(world, playerId, Items.HONEYCOMB, HONEYCOMB, "honeycomb")
            );
        }

        private Component itemProgress(ServerLevel world, UUID playerId, Item item, int target, String suffix) {
            return Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_3.progress." + suffix,
                    Math.min(target, StoryQuestService.countCompletionItem(world, playerId, item)), target)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID id = player.getUUID();
            return StoryQuestService.countCompletionItem(world, id, Items.PAPER) >= PAPER
                    && StoryQuestService.countCompletionItem(world, id, Items.INK_SAC) >= INK
                    && StoryQuestService.countCompletionItem(world, id, Items.HONEYCOMB) >= HONEYCOMB;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            return isComplete(world, player)
                    && StoryQuestService.consumeCompletionItem(world, player.getUUID(), Items.PAPER, PAPER)
                    && StoryQuestService.consumeCompletionItem(world, player.getUUID(), Items.INK_SAC, INK)
                    && StoryQuestService.consumeCompletionItem(world, player.getUUID(), Items.HONEYCOMB, HONEYCOMB);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(3, CurrencyService.CROWN * 2L, 14, ReputationService.ReputationTrack.TRADE, 18, null);
        }
    }

    private static final class NamesBehindSealChapter extends EmptyCaravanChapter {
        @Override public Component title() { return title(4); }
        @Override public Component offerParagraph1() { return offer(4, 1); }
        @Override public Component offerParagraph2() { return offer(4, 2); }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_4.progress",
                    progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_WITNESSES), EmptyCaravanStoryService.WITNESS_TARGET)
                    .withStyle(ChatFormatting.GRAY));
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.EMPTY_CARAVAN_WITNESSES) >= EmptyCaravanStoryService.WITNESS_TARGET;
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof Villager villager) || villager.isBaby()) {
                return;
            }
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            String witness = profession.is(VillagerProfession.CARTOGRAPHER) ? "cartographer"
                    : profession.is(VillagerProfession.LIBRARIAN) ? "librarian"
                    : profession.is(VillagerProfession.CLERIC) ? "cleric" : null;
            if (witness == null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.story.the_empty_caravan.witness_wrong")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            String flag = StoryQuestKeys.EMPTY_CARAVAN_WITNESS_PREFIX + witness;
            if (StoryQuestService.hasStoryFlag(world, player.getUUID(), flag)) {
                return;
            }
            StoryQuestService.setStoryFlag(world, player.getUUID(), flag, true);
            StoryQuestService.addQuestIntClamped(world, player.getUUID(), StoryQuestKeys.EMPTY_CARAVAN_WITNESSES,
                    1, EmptyCaravanStoryService.WITNESS_TARGET);
            player.sendSystemMessage(Component.translatable("message.village-quest.story.the_empty_caravan.witness." + witness)
                    .withStyle(ChatFormatting.GOLD), false);
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(4, CurrencyService.CROWN * 2L + 5L, 16, ReputationService.ReputationTrack.TRADE, 20, null);
        }
    }

    private static final class CaravanMadeOfBaitChapter extends EmptyCaravanChapter {
        @Override public Component title() { return title(5); }
        @Override public Component offerParagraph1() { return offer(5, 1); }
        @Override public Component offerParagraph2() { return offer(5, 2); }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            boolean choiceMade = StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY)
                    || StoryQuestService.hasStoryFlag(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CHOICE_JUSTICE);
            int bait = progress(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE);
            return List.of(
                    Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_5.progress.choice", choiceMade ? 1 : 0, 1)
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_5.progress.bait",
                            bait >= EmptyCaravanStoryService.BAIT_WON ? 1 : 0, 1).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE)
                    >= EmptyCaravanStoryService.BAIT_WON;
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
            EmptyCaravanStoryService.chooseApproach(world, player, entity, inHand);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(5, CurrencyService.CROWN * 3L, 22, ReputationService.ReputationTrack.MONSTER_HUNTING, 24, null);
        }

        @Override
        public void onClaimed(ServerLevel world, ServerPlayer player) {
            boolean amnesty = StoryQuestService.hasStoryFlag(world, player.getUUID(), StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY);
            if (amnesty) {
                ReputationService.add(world, player.getUUID(), ReputationService.ReputationTrack.TRADE, 10);
            } else {
                CurrencyService.addBalance(world, player.getUUID(), CurrencyService.CROWN);
                ReputationService.add(world, player.getUUID(), ReputationService.ReputationTrack.MONSTER_HUNTING, 8);
            }
            player.sendSystemMessage(Component.translatable(amnesty
                    ? "message.village-quest.story.the_empty_caravan.outcome.amnesty"
                    : "message.village-quest.story.the_empty_caravan.outcome.justice").withStyle(ChatFormatting.GOLD), false);
        }
    }

    private static final class RoadsBetweenVillagesChapter extends EmptyCaravanChapter {
        private static final int GRAVEL = 32;
        private static final int STONE_BRICKS = 24;
        private static final int LANTERNS = 8;
        private static final int LEADS = 4;

        @Override public Component title() { return title(6); }
        @Override public Component offerParagraph1() { return offer(6, 1); }
        @Override public Component offerParagraph2() { return offer(6, 2); }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    progressLine(world, playerId, Items.GRAVEL, GRAVEL, "gravel"),
                    progressLine(world, playerId, Items.STONE_BRICKS, STONE_BRICKS, "stone_bricks"),
                    progressLine(world, playerId, Items.LANTERN, LANTERNS, "lanterns"),
                    progressLine(world, playerId, Items.LEAD, LEADS, "leads")
            );
        }

        private Component progressLine(ServerLevel world, UUID id, Item item, int target, String suffix) {
            return Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_6.progress." + suffix,
                    Math.min(target, StoryQuestService.countCompletionItem(world, id, item)), target)
                    .withStyle(ChatFormatting.GRAY);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID id = player.getUUID();
            return StoryQuestService.countCompletionItem(world, id, Items.GRAVEL) >= GRAVEL
                    && StoryQuestService.countCompletionItem(world, id, Items.STONE_BRICKS) >= STONE_BRICKS
                    && StoryQuestService.countCompletionItem(world, id, Items.LANTERN) >= LANTERNS
                    && StoryQuestService.countCompletionItem(world, id, Items.LEAD) >= LEADS;
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            UUID id = player.getUUID();
            return isComplete(world, player)
                    && StoryQuestService.consumeCompletionItem(world, id, Items.GRAVEL, GRAVEL)
                    && StoryQuestService.consumeCompletionItem(world, id, Items.STONE_BRICKS, STONE_BRICKS)
                    && StoryQuestService.consumeCompletionItem(world, id, Items.LANTERN, LANTERNS)
                    && StoryQuestService.consumeCompletionItem(world, id, Items.LEAD, LEADS);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return completion(6, CurrencyService.CROWN * 4L, 28, ReputationService.ReputationTrack.TRADE, 35,
                    VillageProjectType.CARAVAN_YARD);
        }

        @Override
        public void onClaimed(ServerLevel world, ServerPlayer player) {
            TradeRouteService.initializeCaravanYard(world, player);
        }
    }
}
