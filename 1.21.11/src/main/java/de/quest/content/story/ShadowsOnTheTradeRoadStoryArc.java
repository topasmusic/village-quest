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
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.VillagerProfession;

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
    public Text title() {
        return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.title");
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
        return ShadowsTradeRoadEncounterService.hasUnlockPrerequisites(world, playerId);
    }

    @Override
    public boolean shouldShowLockedEntry(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.WATCH_BELL);
    }

    @Override
    public Text lockedEntryBody(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "screen.village-quest.questmaster.story.shadows_on_the_trade_road.locked",
                ShadowsTradeRoadEncounterService.completedRumorCount(world, playerId),
                ShadowsTradeRoadEncounterService.RUMOR_UNLOCK_TARGET
        );
    }

    private abstract static class ShadowsChapter implements StoryChapterDefinition {
        protected void addProgress(ServerWorld world, ServerPlayerEntity player, String key, int amount, int target) {
            StoryQuestService.addQuestIntClamped(world, player.getUuid(), key, amount, target);
            StoryQuestService.completeIfEligible(world, player);
        }

        protected int progress(ServerWorld world, UUID playerId, String key) {
            return StoryQuestService.getQuestInt(world, playerId, key);
        }

        protected boolean isAdultVillager(Entity entity) {
            return entity instanceof VillagerEntity villager && !villager.isBaby();
        }

        protected boolean isToolsmith(VillagerEntity villager) {
            if (villager == null) {
                return false;
            }
            RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
            return profession.matchesKey(VillagerProfession.TOOLSMITH);
        }
    }

    private static final class WhispersBetweenBellsChapter extends ShadowsChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_1.progress.1",
                            progress(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGERS),
                            ShadowsTradeRoadEncounterService.HOME_VILLAGER_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_1.progress.2",
                            progress(world, playerId, StoryQuestKeys.SHADOWS_REMOTE_VILLAGES),
                            ShadowsTradeRoadEncounterService.REMOTE_VILLAGE_TARGET
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_1.progress.3",
                            ShadowsTradeRoadEncounterService.REMOTE_VILLAGER_TARGET
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            UUID playerId = player.getUuid();
            return progress(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGERS) >= ShadowsTradeRoadEncounterService.HOME_VILLAGER_TARGET
                    && progress(world, playerId, StoryQuestKeys.SHADOWS_REMOTE_VILLAGES) >= ShadowsTradeRoadEncounterService.REMOTE_VILLAGE_TARGET;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_1.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN,
                    10,
                    ReputationService.ReputationTrack.TRADE,
                    12,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof VillagerEntity villager) || villager.isBaby()) {
                return;
            }
            ShadowsTradeRoadEncounterService.VillageMarker village = ShadowsTradeRoadEncounterService.currentVillage(world, player.getBlockPos());
            if (village == null) {
                player.sendMessage(Text.translatable("message.village-quest.story.shadows_on_the_trade_road.need_village").formatted(Formatting.GRAY), true);
                return;
            }

            UUID playerId = player.getUuid();
            if (!ShadowsTradeRoadEncounterService.hasHomeVillage(world, playerId)) {
                ShadowsTradeRoadEncounterService.bindHomeVillage(world, playerId, village);
            }

            if (ShadowsTradeRoadEncounterService.isHomeVillage(world, playerId, village)) {
                String villagerFlag = StoryQuestKeys.SHADOWS_HOME_VILLAGER_PREFIX + villager.getUuidAsString();
                if (StoryQuestService.hasStoryFlag(world, playerId, villagerFlag)) {
                    return;
                }
                VillagerDialogueService.sendDialogue(player, villager, VillagerDialogueService.shadowsHomeRumor(villager));
                StoryQuestService.setStoryFlag(world, playerId, villagerFlag, true);
                addProgress(world, player, StoryQuestKeys.SHADOWS_HOME_VILLAGERS, 1, ShadowsTradeRoadEncounterService.HOME_VILLAGER_TARGET);
                return;
            }

            String villagerFlag = StoryQuestKeys.SHADOWS_REMOTE_VILLAGER_PREFIX + village.key() + "_" + villager.getUuidAsString();
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
        public Text title() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public boolean canAccept(ServerWorld world, ServerPlayerEntity player) {
            return ShadowsTradeRoadEncounterService.hasCompass(player);
        }

        @Override
        public Text acceptBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            return Text.translatable("message.village-quest.story.shadows_on_the_trade_road.chapter_2.compass_required").formatted(Formatting.RED);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.story.shadows_on_the_trade_road.chapter_2.progress",
                            progress(world, playerId, StoryQuestKeys.SHADOWS_TOOLSMITH_CALIBRATED),
                            1
                    ).formatted(Formatting.GRAY)
            );
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.SHADOWS_TOOLSMITH_CALIBRATED) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_2.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 6L),
                    12,
                    ReputationService.ReputationTrack.CRAFTING,
                    15,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            if (!(entity instanceof VillagerEntity villager) || !isToolsmith(villager)) {
                return;
            }
            if (!ShadowsTradeRoadEncounterService.hasCompass(player)) {
                player.sendMessage(acceptBlockedMessage(world, player), false);
                return;
            }
            VillagerDialogueService.sendDialogue(player, villager, Text.translatable("message.village-quest.story.shadows_on_the_trade_road.toolsmith"));
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_TOOLSMITH_CALIBRATED, 1);
            StoryQuestService.completeIfEligible(world, player);
        }
    }

    private static final class FirstSignalChapter extends ShadowsChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            ShadowsTradeRoadEncounterService.onFirstSignalAccepted(world, player);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.rescueProgressLines(world, playerId, StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS, 1, false);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_3.complete.3").formatted(Formatting.GRAY),
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
        public Text title() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            ShadowsTradeRoadEncounterService.onHoldingAccepted(world, player);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.rescueProgressLines(world, playerId, StoryQuestKeys.SHADOWS_HOLDING_WINS, ShadowsTradeRoadEncounterService.HOLDING_TARGET_WINS, false);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.SHADOWS_HOLDING_WINS) >= ShadowsTradeRoadEncounterService.HOLDING_TARGET_WINS;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_4.complete.3").formatted(Formatting.GRAY),
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
        public Text title() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            ShadowsTradeRoadEncounterService.onLetterAccepted(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.letterProgressLines(world, playerId);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.SHADOWS_LETTER_RECEIVED) >= 1
                    && ShadowsTradeRoadEncounterService.hasGuildWarningLetter(player);
        }

        @Override
        public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
            return isComplete(world, player) && ShadowsTradeRoadEncounterService.consumeGuildWarningLetter(player);
        }

        @Override
        public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
            return ShadowsTradeRoadEncounterService.hasGuildWarningLetter(player)
                    ? null
                    : Text.translatable("message.village-quest.story.shadows_on_the_trade_road.chapter_5.letter_missing").formatted(Formatting.RED);
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN + (CurrencyService.SILVERMARK * 6L),
                    12,
                    ReputationService.ReputationTrack.TRADE,
                    15,
                    null
            );
        }

        @Override
        public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
            ShadowsTradeRoadEncounterService.handleCourierInteraction(world, player, entity);
            StoryQuestService.completeIfEligible(world, player);
        }
    }

    private static final class BellOverTheTradeRoadChapter extends ShadowsChapter {
        @Override
        public Text title() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.title");
        }

        @Override
        public Text offerParagraph1() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.offer.1").formatted(Formatting.GRAY);
        }

        @Override
        public Text offerParagraph2() {
            return Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.offer.2").formatted(Formatting.GRAY);
        }

        @Override
        public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
            ShadowsTradeRoadEncounterService.onFinalAccepted(world, player);
        }

        @Override
        public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
            StoryQuestService.completeIfEligible(world, player);
        }

        @Override
        public List<Text> progressLines(ServerWorld world, UUID playerId) {
            return ShadowsTradeRoadEncounterService.rescueProgressLines(world, playerId, StoryQuestKeys.SHADOWS_FINAL_WON, 1, true);
        }

        @Override
        public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
            return progress(world, player.getUuid(), StoryQuestKeys.SHADOWS_FINAL_WON) >= 1;
        }

        @Override
        public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(
                    title(),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.complete.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.complete.2").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.complete.3").formatted(Formatting.GRAY),
                    CurrencyService.CROWN * 3L,
                    24,
                    ReputationService.ReputationTrack.MONSTER_HUNTING,
                    30,
                    null
            );
        }
    }
}
