package de.quest.content.story;

import de.quest.economy.CurrencyService;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.reputation.ReputationService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.List;
import java.util.UUID;

public final class ShadowsOnTheTradeRoadStoryArc implements StoryArcDefinition {
    private final List<StoryChapterDefinition> chapters = List.of(
            new WhispersBetweenBellsChapter(),
            new NeedleForTheNightRoadChapter(),
            new FirstSignalChapter(),
            new HoldingTheVergeChapter(),
            new LetterForTheGuildChapter(),
            new BellOverTheTradeRoadChapter()
    );

    @Override
    public StoryArcType type() {
        return StoryArcType.SHADOWS_ON_THE_TRADE_ROAD;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.title");
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
        return ShadowsTradeRoadEncounterService.hasUnlockPrerequisites(world, playerId);
    }

    @Override
    public boolean shouldShowLockedEntry(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.WATCH_BELL);
    }

    @Override
    public Component lockedEntryBody(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "screen.village-quest.questmaster.story.shadows_on_the_trade_road.locked",
                ShadowsTradeRoadEncounterService.completedRumorCount(world, playerId),
                ShadowsTradeRoadEncounterService.RUMOR_UNLOCK_TARGET
        );
    }

    private abstract static class ShadowsChapter implements StoryChapterDefinition {
        protected void addProgress(ServerLevel world, ServerPlayer player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUUID(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerLevel world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean isAdultVillager(Entity entity) {
            return entity instanceof Villager villager && !villager.isBaby();
        }

        protected boolean isToolsmith(Villager villager) {
            if (villager == null) {
                return false;
            }
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            return profession.is(VillagerProfession.TOOLSMITH);
        }
    }

    private static final class WhispersBetweenBellsChapter extends ShadowsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_1.progress.1",
                            progress(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGERS),
                            ShadowsTradeRoadEncounterService.HOME_VILLAGER_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_1.progress.2",
                            progress(world, playerId, StoryQuestKeys.SHADOWS_REMOTE_VILLAGES),
                            ShadowsTradeRoadEncounterService.REMOTE_VILLAGE_TARGET
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_1.progress.3",
                            ShadowsTradeRoadEncounterService.REMOTE_VILLAGER_TARGET
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            UUID playerId = player.getUUID();
            return progress(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGERS) >= ShadowsTradeRoadEncounterService.HOME_VILLAGER_TARGET
                    && progress(world, playerId, StoryQuestKeys.SHADOWS_REMOTE_VILLAGES) >= ShadowsTradeRoadEncounterService.REMOTE_VILLAGE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.TRADE,
                    12,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inInteractionHand) {
            if (!(entity instanceof Villager villager) || villager.isBaby()) {
                return;
            }
            ShadowsTradeRoadEncounterService.VillageMarker village = ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
            if (village == null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.story.shadows_on_the_trade_road.need_village").withStyle(ChatFormatting.GRAY), true);
                return;
            }

            UUID playerId = player.getUUID();
            if (!ShadowsTradeRoadEncounterService.hasHomeVillage(world, playerId)) {
                ShadowsTradeRoadEncounterService.bindHomeVillage(world, playerId, village);
            }

            if (ShadowsTradeRoadEncounterService.isHomeVillage(world, playerId, village)) {
                String villagerFlag = StoryQuestKeys.SHADOWS_HOME_VILLAGER_PREFIX + villager.getStringUUID();
                if (StoryQuestService.hasStoryFlag(world, playerId, villagerFlag)) {
                    return;
                }
                VillagerDialogueService.sendDialogue(player, villager, VillagerDialogueService.shadowsHomeRumor(villager));
                StoryQuestService.setStoryFlag(world, playerId, villagerFlag, true);
                addProgress(world, player, StoryQuestKeys.SHADOWS_HOME_VILLAGERS, 1, ShadowsTradeRoadEncounterService.HOME_VILLAGER_TARGET);
                return;
            }

            String villagerFlag = StoryQuestKeys.SHADOWS_REMOTE_VILLAGER_PREFIX + village.key() + "_" + villager.getStringUUID();
            if (StoryQuestService.hasStoryFlag(world, playerId, villagerFlag)) {
                return;
            }
            VillagerDialogueService.sendDialogue(player, villager, VillagerDialogueService.shadowsRemoteRumor(villager));
            StoryQuestService.setStoryFlag(world, playerId, villagerFlag, true);

            String talksKey = StoryQuestKeys.SHADOWS_REMOTE_VILLAGE_TALKS_PREFIX + village.key();
            int beforeTalks = progress(world, playerId, talksKey);
            if (beforeTalks < ShadowsTradeRoadEncounterService.REMOTE_VILLAGER_TARGET) {
                StoryQuestService.setQuestInt(world, playerId, talksKey, beforeTalks + 1);
            }
            if (beforeTalks + 1 >= ShadowsTradeRoadEncounterService.REMOTE_VILLAGER_TARGET) {
                String doneFlag = StoryQuestKeys.SHADOWS_REMOTE_VILLAGE_DONE_PREFIX + village.key();
                if (!StoryQuestService.hasStoryFlag(world, playerId, doneFlag)) {
                    StoryQuestService.setStoryFlag(world, playerId, doneFlag, true);
                    addProgress(world, player, StoryQuestKeys.SHADOWS_REMOTE_VILLAGES, 1, ShadowsTradeRoadEncounterService.REMOTE_VILLAGE_TARGET);
                } else {
                    StoryQuestService.completeIfEligible(world, player);
                }
            } else {
                StoryQuestService.completeIfEligible(world, player);
            }
        }
    }

    private static final class NeedleForTheNightRoadChapter extends ShadowsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public boolean canAccept(ServerLevel world, ServerPlayer player) {
            return ShadowsTradeRoadEncounterService.hasCompass(player);
        }

        @Override
        public Component acceptBlockedMessage(ServerLevel world, ServerPlayer player) {
            return Component.translatable("message.village-quest.story.shadows_on_the_trade_road.chapter_2.compass_required").withStyle(ChatFormatting.RED);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_2.progress",
                            progress(world, playerId, StoryQuestKeys.SHADOWS_TOOLSMITH_CALIBRATED),
                            1
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.SHADOWS_TOOLSMITH_CALIBRATED) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 6L),
                    12,
                    ReputationService.ReputationTrack.CRAFTING,
                    15,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inInteractionHand) {
            if (!(entity instanceof Villager villager) || !isToolsmith(villager)) {
                return;
            }
            if (!ShadowsTradeRoadEncounterService.hasCompass(player)) {
                player.sendSystemMessage(acceptBlockedMessage(world, player), false);
                return;
            }
            VillagerDialogueService.sendDialogue(player, villager, Component.translatable("message.village-quest.story.shadows_on_the_trade_road.toolsmith"));
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_TOOLSMITH_CALIBRATED, 1);
            StoryQuestService.completeIfEligible(world, player);
        }
    }

    private static final class FirstSignalChapter extends ShadowsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            ShadowsTradeRoadEncounterService.onFirstSignalAccepted(world, player);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.rescueProgressLines(world, playerId, StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS, 1, false);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 8L),
                    14,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    16,
                    null
            );
        }
    }

    private static final class HoldingTheVergeChapter extends ShadowsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            ShadowsTradeRoadEncounterService.onHoldingAccepted(world, player);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.rescueProgressLines(world, playerId, StoryQuestKeys.SHADOWS_HOLDING_WINS, ShadowsTradeRoadEncounterService.HOLDING_TARGET_WINS, false);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.SHADOWS_HOLDING_WINS) >= ShadowsTradeRoadEncounterService.HOLDING_TARGET_WINS;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN * 2L,
                    18,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    20,
                    null
            );
        }
    }

    private static final class LetterForTheGuildChapter extends ShadowsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            ShadowsTradeRoadEncounterService.onLetterAccepted(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.letterProgressLines(world, playerId);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.SHADOWS_LETTER_RECEIVED) >= 1
                    && ShadowsTradeRoadEncounterService.hasGuildWarningLetter(player);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
            return isComplete(world, player) && ShadowsTradeRoadEncounterService.consumeGuildWarningLetter(player);
        }

        @Override
        public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
            return ShadowsTradeRoadEncounterService.hasGuildWarningLetter(player)
                    ? null
                    : Component.translatable("message.village-quest.story.shadows_on_the_trade_road.chapter_5.letter_missing").withStyle(ChatFormatting.RED);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 6L),
                    12,
                    ReputationService.ReputationTrack.TRADE,
                    15,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inInteractionHand) {
            ShadowsTradeRoadEncounterService.handleCourierInteraction(world, player, entity);
            StoryQuestService.completeIfEligible(world, player);
        }
    }

    private static final class BellOverTheTradeRoadChapter extends ShadowsChapter {
        @Override
        public Component title() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.title");
        }

        @Override
        public Component offerParagraph1() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.offer.1").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public Component offerParagraph2() {
            return Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.offer.2").withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void onAccepted(ServerLevel world, ServerPlayer player) {
            ShadowsTradeRoadEncounterService.onFinalAccepted(world, player);
        }

        @Override
        public void onServerTick(ServerLevel world, ServerPlayer player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Component> progressLines(ServerLevel world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.rescueProgressLines(world, playerId, StoryQuestKeys.SHADOWS_FINAL_WON, 1, true);
        }

        @Override
        public boolean isComplete(ServerLevel world, ServerPlayer player) {
            return progress(world, player.getUUID(), StoryQuestKeys.SHADOWS_FINAL_WON) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.complete.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.complete.2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.complete.3").withStyle(ChatFormatting.GRAY),
                    CurrencyService.CROWN * 3L,
                    24,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    30,
                    null
            );
        }
    }
}
