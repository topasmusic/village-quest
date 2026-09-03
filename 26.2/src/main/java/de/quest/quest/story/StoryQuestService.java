package de.quest.quest.story;

import de.quest.caravan.TradeRouteService;
import de.quest.content.story.FailingHarvestStoryArc;
import de.quest.content.story.MarketRoadTroublesStoryArc;
import de.quest.content.story.NightBellsStoryArc;
import de.quest.content.story.RestlessPensStoryArc;
import de.quest.content.story.ShadowsOnTheTradeRoadStoryArc;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.content.story.SilentForgeStoryArc;
import de.quest.content.story.EmptyCaravanStoryService;
import de.quest.content.story.TheEmptyCaravanStoryArc;
import de.quest.content.story.ShrinesBetweenRoadsStoryArc;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.economy.QuestExperienceService;
import de.quest.party.QuestPartyService;
import de.quest.party.QuestShareProfiles;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestSoundFeedback;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StoryQuestService {
    private static final String READY_FLAG_PREFIX = "story_ready_";
    private static final double STORY_CURRENCY_MULTIPLIER = 0.70d;
    private static final long STORY_ARC_COOLDOWN_MILLIS = 1L * 60L * 60L * 1000L;

    private static final Map<StoryArcType, StoryArcDefinition> ARCS = Map.ofEntries(
            Map.entry(StoryArcType.FAILING_HARVEST, new FailingHarvestStoryArc()),
            Map.entry(StoryArcType.SILENT_FORGE, new SilentForgeStoryArc()),
            Map.entry(StoryArcType.MARKET_ROAD_TROUBLES, new MarketRoadTroublesStoryArc()),
            Map.entry(StoryArcType.RESTLESS_PENS, new RestlessPensStoryArc()),
            Map.entry(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD, new ShadowsOnTheTradeRoadStoryArc()),
            Map.entry(StoryArcType.THE_EMPTY_CARAVAN, new TheEmptyCaravanStoryArc()),
            Map.entry(StoryArcType.SHRINES_BETWEEN_ROADS, new ShrinesBetweenRoadsStoryArc()),
            Map.entry(StoryArcType.NIGHT_BELLS, new NightBellsStoryArc())
    );

    private StoryQuestService() {}

    public static boolean areCoreStoriesCompleted(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        for (StoryArcType type : StoryArcType.coreQuestmasterArcs()) {
            if (!isCompleted(world, playerId, type)) {
                return false;
            }
        }
        return true;
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static int getStoryArcCount() {
        return StoryArcType.questmasterArcs().size();
    }

    public static int getQuestInt(ServerLevel world, UUID playerId, String key) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return 0;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        int chapterIndex = activeArc == null ? 0 : chapterIndex(world, playerId, activeArc);
        if (activeArc != null && QuestPartyService.usesSharedStoryInt(world, playerId, activeArc, chapterIndex, key)) {
            return QuestPartyService.getSharedStoryInt(world, playerId, activeArc, chapterIndex, key);
        }
        return data(world, playerId).getStoryInt(key);
    }

    public static void setQuestInt(ServerLevel world, UUID playerId, String key, int value) {
        setQuestInt(world, playerId, key, value, true);
    }

    /**
     * Updates visible story progress without playing the ordinary progress cue.
     * Stage-ready feedback remains handled by {@link #completeIfEligible}.
     */
    public static void setQuestIntQuietly(ServerLevel world, UUID playerId, String key, int value) {
        setQuestInt(world, playerId, key, value, false);
    }

    private static void setQuestInt(ServerLevel world, UUID playerId, String key, int value, boolean playFeedback) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        int chapterIndex = activeArc == null ? 0 : chapterIndex(world, playerId, activeArc);
        if (activeArc != null && QuestPartyService.usesSharedStoryInt(world, playerId, activeArc, chapterIndex, key)) {
            if (QuestPartyService.getSharedStoryInt(world, playerId, activeArc, chapterIndex, key) == value) {
                return;
            }
            List<UUID> recipients = QuestPartyService.activeStoryMembers(world, playerId, activeArc, chapterIndex);
            Map<UUID, List<Component>> before = captureProgressSnapshots(world, recipients);
            QuestPartyService.setSharedStoryInt(world, playerId, activeArc, chapterIndex, key, value);
            notifyProgressChange(world, recipients, before, playFeedback);
            return;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.getStoryInt(key) == value) {
            return;
        }
        List<Component> before = currentProgressLines(world, playerId);
        data.setStoryInt(key, value);
        QuestState.get(world.getServer()).setDirty();
        notifyProgressChange(world, playerId, before, playFeedback);
    }

    public static void addQuestIntClamped(ServerLevel world, UUID playerId, String key, int amount, int target) {
        if (amount <= 0) {
            return;
        }
        int current = getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }
        setQuestInt(world, playerId, key, Math.min(target, current + amount));
    }

    public static void addQuestIntClampedQuietly(ServerLevel world, UUID playerId, String key, int amount, int target) {
        if (amount <= 0) {
            return;
        }
        int current = getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }
        setQuestIntQuietly(world, playerId, key, Math.min(target, current + amount));
    }

    public static boolean hasStoryFlag(ServerLevel world, UUID playerId, String key) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return false;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        int chapterIndex = activeArc == null ? 0 : chapterIndex(world, playerId, activeArc);
        if (activeArc != null && QuestPartyService.usesSharedStoryFlag(world, playerId, activeArc, chapterIndex, key)) {
            return QuestPartyService.getSharedStoryFlag(world, playerId, activeArc, chapterIndex, key);
        }
        return data(world, playerId).hasStoryFlag(key);
    }

    public static void setStoryFlag(ServerLevel world, UUID playerId, String key, boolean enabled) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        int chapterIndex = activeArc == null ? 0 : chapterIndex(world, playerId, activeArc);
        if (activeArc != null && QuestPartyService.usesSharedStoryFlag(world, playerId, activeArc, chapterIndex, key)) {
            if (QuestPartyService.getSharedStoryFlag(world, playerId, activeArc, chapterIndex, key) == enabled) {
                return;
            }
            List<UUID> recipients = QuestPartyService.activeStoryMembers(world, playerId, activeArc, chapterIndex);
            Map<UUID, List<Component>> before = captureProgressSnapshots(world, recipients);
            QuestPartyService.setSharedStoryFlag(world, playerId, activeArc, chapterIndex, key, enabled);
            notifyProgressChange(world, recipients, before);
            return;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.hasStoryFlag(key) == enabled) {
            return;
        }
        List<Component> before = currentProgressLines(world, playerId);
        data.setStoryFlag(key, enabled);
        QuestState.get(world.getServer()).setDirty();
        notifyProgressChange(world, playerId, before);
    }

    public static boolean acceptQuest(ServerLevel world, ServerPlayer player, StoryArcType arcType) {
        if (world == null || player == null || arcType == null || !StoryArcType.isQuestmasterArc(arcType)) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (data.getActiveStoryArc() != null
                || isCompleted(world, player.getUUID(), arcType)
                || isStoryCooldownActive(world, player.getUUID())) {
            return false;
        }

        StoryArcDefinition arc = definition(arcType);
        if (arc == null || !arc.isUnlocked(world, player.getUUID())) {
            return false;
        }

        int chapterIndex = chapterIndex(world, player.getUUID(), arcType);
        StoryChapterDefinition chapter = arc.chapter(chapterIndex);
        if (chapter == null) {
            return false;
        }
        if (!chapter.canAccept(world, player)) {
            Component blocked = chapter.acceptBlockedMessage(world, player);
            if (blocked != null) {
                player.sendSystemMessage(blocked, false);
            }
            return false;
        }

        data.clearStoryProgress();
        data.setActiveStoryArc(arcType);
        data.setStoryDiscovered(arcType.id(), true);
        QuestState.get(world.getServer()).setDirty();
        chapter.onAccepted(world, player);
        QuestPartyService.onStoryQuestAccepted(world, player, arcType, chapterIndex, chapter);
        player.sendSystemMessage(Texts.acceptedTitle(chapter.title(), ChatFormatting.GOLD), false);
        QuestSoundFeedback.playAccepted(world, player);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        refreshQuestUi(world, player.getUUID());
        return true;
    }

    public static boolean claimFromQuestMaster(ServerLevel world, ServerPlayer player, StoryArcType arcType) {
        if (world == null || player == null || arcType == null || !isActive(world, player.getUUID(), arcType)) {
            return false;
        }

        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter == null) {
            return false;
        }
        if (!chapter.isComplete(world, player)) {
            Component blocked = chapter.claimBlockedMessage(world, player);
            if (blocked != null) {
                player.sendSystemMessage(blocked, false);
            }
            return false;
        }
        if (!chapter.consumeCompletionRequirements(world, player)) {
            return false;
        }

        StoryChapterCompletion completion = chapter.buildCompletion();
        int chapterIndex = chapterIndex(world, player.getUUID(), arcType);
        if (QuestPartyService.isSharedStoryMember(world, player.getUUID(), arcType, chapterIndex)) {
            List<UUID> recipients = QuestPartyService.activeStoryMembers(world, player.getUUID(), arcType, chapterIndex);
            for (UUID recipientId : recipients) {
                ServerPlayer recipient = world.getServer().getPlayerList().getPlayer(recipientId);
                if (recipient != null) {
                    deliverCompletion(world, recipient, completion);
                    chapter.onClaimed(world, recipient);
                }
                finishChapterProgress(world, recipientId, arcType, chapterIndex);
            }
            QuestPartyService.clearStorySessionIfFinished(world, player.getUUID(), arcType, chapterIndex);
            return !recipients.isEmpty();
        }

        deliverCompletion(world, player, completion);
        chapter.onClaimed(world, player);
        finishChapterProgress(world, player.getUUID(), arcType, chapterIndex);
        return true;
    }

    public static boolean completeIfEligible(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        StoryArcType activeArc = activeArcType(world, player.getUUID());
        if (activeArc == null) {
            return false;
        }
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter == null || !chapter.isComplete(world, player)) {
            return false;
        }
        int chapterIndex = chapterIndex(world, player.getUUID(), activeArc);
        PlayerQuestData data = data(world, player.getUUID());
        String readyFlag = readyFlagKey(activeArc, chapterIndex);
        if (!data.hasStoryFlag(readyFlag)) {
            data.setStoryFlag(readyFlag, true);
            QuestState.get(world.getServer()).setDirty();
            player.sendSystemMessage(Component.translatable("message.village-quest.story.ready", chapter.title()).withStyle(ChatFormatting.GOLD), false);
            QuestSoundFeedback.playReady(world, player);
        }
        refreshQuestUi(world, player.getUUID());
        return true;
    }

    public static StoryQuestStatus openStatus(ServerLevel world, UUID playerId) {
        StoryArcType activeArc = activeArcType(world, playerId);
        StoryChapterDefinition chapter = currentChapter(world, playerId);
        if (activeArc == null || chapter == null) {
            return null;
        }

        List<Component> lines = new ArrayList<>();
        int chapterNumber = chapterIndex(world, playerId, activeArc) + 1;
        lines.add(Component.translatable("text.village-quest.story.chapter_label", chapterNumber, chapter.title()).withStyle(ChatFormatting.GOLD));
        lines.addAll(chapter.progressLines(world, playerId));
        return new StoryQuestStatus(definition(activeArc).title(), List.copyOf(lines));
    }

    public static int discoveredCount(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0;
        }
        int count = 0;
        for (StoryArcType type : StoryArcType.questmasterArcs()) {
            if (data(world, playerId).getStoryDiscovered().contains(type.id())) {
                count++;
            }
        }
        return count;
    }

    public static int completedCount(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0;
        }
        int count = 0;
        for (StoryArcType type : StoryArcType.questmasterArcs()) {
            if (data(world, playerId).getStoryCompleted().contains(type.id())) {
                count++;
            }
        }
        return count;
    }

    public static int activeCount(ServerLevel world, UUID playerId) {
        return activeArcType(world, playerId) == null ? 0 : 1;
    }

    public static boolean isActive(ServerLevel world, UUID playerId, StoryArcType arcType) {
        return arcType != null && arcType == activeArcType(world, playerId);
    }

    public static boolean isCompleted(ServerLevel world, UUID playerId, StoryArcType arcType) {
        return world != null && playerId != null && arcType != null && data(world, playerId).hasStoryCompleted(arcType.id());
    }

    public static StoryArcType activeArcType(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        StoryArcType active = data(world, playerId).getActiveStoryArc();
        return StoryArcType.isQuestmasterArc(active) ? active : null;
    }

    public static StoryArcType availableArcType(ServerLevel world, UUID playerId) {
        return availableArcTypeInternal(world, playerId, true);
    }

    public static StoryArcType availableArcTypeIgnoringCooldown(ServerLevel world, UUID playerId) {
        return availableArcTypeInternal(world, playerId, false);
    }

    public static boolean isStoryCooldownActive(ServerLevel world, UUID playerId) {
        return getStoryCooldownRemainingMillis(world, playerId) > 0L;
    }

    public static long getStoryCooldownRemainingMillis(ServerLevel world, UUID playerId) {
        return Math.max(0L, getStoryCooldownUntil(world, playerId) - System.currentTimeMillis());
    }

    public static long getStoryCooldownUntil(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0L;
        }
        long cooldownUntil = data(world, playerId).getStoryCooldownUntil();
        return cooldownUntil > System.currentTimeMillis() ? cooldownUntil : 0L;
    }

    public static int chapterIndex(ServerLevel world, UUID playerId, StoryArcType arcType) {
        if (world == null || playerId == null || arcType == null) {
            return 0;
        }
        return Math.max(0, data(world, playerId).getStoryChapterProgress(arcType.id()));
    }

    public static StoryChapterDefinition chapter(ServerLevel world, UUID playerId, StoryArcType arcType) {
        StoryArcDefinition arc = definition(arcType);
        if (arc == null) {
            return null;
        }
        return arc.chapter(chapterIndex(world, playerId, arcType));
    }

    public static StoryChapterDefinition currentChapter(ServerLevel world, UUID playerId) {
        StoryArcType activeArc = activeArcType(world, playerId);
        return activeArc == null ? null : chapter(world, playerId, activeArc);
    }

    public static StoryArcDefinition definition(StoryArcType arcType) {
        return ARCS.get(arcType);
    }

    public static List<Component> previewRewardLines(ServerLevel world,
                                                     UUID playerId,
                                                     StoryChapterCompletion completion) {
        if (completion == null) {
            return List.of();
        }
        List<Component> rewards = new ArrayList<>();
        long scaledCurrencyReward = scaledCurrencyReward(completion.currencyReward());
        if (scaledCurrencyReward > 0L) {
            rewards.add(Component.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(scaledCurrencyReward)).withStyle(ChatFormatting.GOLD));
        }
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            rewards.add(ReputationService.formatRewardLine(completion.reputationTrack(), completion.reputationAmount()));
        }
        if (completion.unlockedProject() != null) {
            rewards.add(Component.translatable(
                    "screen.village-quest.questmaster.reward.project",
                    Component.translatable("quest.village-quest.project." + completion.unlockedProject().id() + ".title")
            ).withStyle(ChatFormatting.AQUA));
            rewards.add(Component.translatable("screen.village-quest.questmaster.reward.effect." + completion.unlockedProject().id()).withStyle(ChatFormatting.GRAY));
        }
        if (completion.levels() > 0) {
            int projectExperienceBonus = VillageProjectService.bonusLevels(
                    world,
                    playerId,
                    completion.reputationTrack(),
                    completion.unlockedProject()
            );
            rewards.add(QuestExperienceService.rewardLine(
                    completion.levels(),
                    projectExperienceBonus,
                    QuestExperienceService.RewardType.STORY
            ));
        }
        return rewards;
    }

    public static List<Component> buildOverview(ServerLevel world, UUID playerId) {
        List<Component> lines = new ArrayList<>();
        for (StoryArcType type : StoryArcType.questmasterArcs()) {
            StoryArcDefinition arc = definition(type);
            if (arc == null) {
                continue;
            }
            boolean completed = isCompleted(world, playerId, type);
            boolean active = isActive(world, playerId, type);
            boolean unlocked = completed || active || arc.isUnlocked(world, playerId);
            int chapterNumber = Math.min(chapterIndex(world, playerId, type) + 1, arc.chapterCount());
            String statusKey = completed
                    ? "screen.village-quest.questmaster.status.completed"
                    : active
                    ? "screen.village-quest.questmaster.status.active"
                    : unlocked
                    ? "screen.village-quest.questmaster.status.available"
                    : "screen.village-quest.questmaster.status.locked";
            lines.add(Component.translatable(
                    "command.village-quest.questadmin.story.line",
                    arc.title(),
                    Component.translatable(statusKey),
                    chapterNumber,
                    arc.chapterCount()
            ).withStyle(completed ? ChatFormatting.AQUA : active ? ChatFormatting.GREEN : unlocked ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public static boolean adminResetStoryState(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        boolean hadState = data.getActiveStoryArc() != null
                || !data.getStoryIntState().isEmpty()
                || !data.getStoryFlags().isEmpty()
                || !data.getStoryDiscovered().isEmpty()
                || !data.getStoryCompleted().isEmpty()
                || !data.getStoryChapterProgressState().isEmpty()
                || data.getStoryCooldownUntil() > 0L;
        if (!hadState) {
            return false;
        }
        data.resetStoryState();
        QuestState.get(world.getServer()).setDirty();
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean adminForceCompleteActiveChapter(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        StoryArcType arcType = activeArcType(world, player.getUUID());
        if (chapter == null || arcType == null) {
            return false;
        }
        StoryChapterCompletion completion = chapter.buildCompletion();
        int chapterIndex = chapterIndex(world, player.getUUID(), arcType);
        if (QuestPartyService.isSharedStoryMember(world, player.getUUID(), arcType, chapterIndex)) {
            List<UUID> recipients = QuestPartyService.activeStoryMembers(world, player.getUUID(), arcType, chapterIndex);
            for (UUID recipientId : recipients) {
                ServerPlayer recipient = world.getServer().getPlayerList().getPlayer(recipientId);
                if (recipient != null) {
                    deliverCompletion(world, recipient, completion);
                    chapter.onClaimed(world, recipient);
                }
                finishChapterProgress(world, recipientId, arcType, chapterIndex);
            }
            QuestPartyService.clearStorySessionIfFinished(world, player.getUUID(), arcType, chapterIndex);
            return !recipients.isEmpty();
        }

        deliverCompletion(world, player, completion);
        chapter.onClaimed(world, player);
        finishChapterProgress(world, player.getUUID(), arcType, chapterIndex);
        return true;
    }

    public static void adminUnlockEmptyCaravanForTesting(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        data.clearStoryProgress();
        data.setActiveStoryArc(null);
        for (StoryArcType type : StoryArcType.questmasterArcs()) {
            if (type == StoryArcType.THE_EMPTY_CARAVAN) {
                continue;
            }
            StoryArcDefinition arc = definition(type);
            data.setStoryCompleted(type.id(), true);
            data.setStoryDiscovered(type.id(), true);
            if (arc != null) {
                data.setStoryChapterProgress(type.id(), arc.chapterCount());
            }
        }
        data.setStoryCompleted(StoryArcType.THE_EMPTY_CARAVAN.id(), false);
        data.setStoryDiscovered(StoryArcType.THE_EMPTY_CARAVAN.id(), false);
        data.setStoryChapterProgress(StoryArcType.THE_EMPTY_CARAVAN.id(), 0);
        data.setStoryCooldownUntil(0L);
        QuestState.get(world.getServer()).setDirty();
        refreshQuestUi(world, playerId);
    }

    /** Selects the final shrine chapter immediately before its guaranteed relay incident. */
    public static boolean adminPrepareLastRelayForTesting(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return false;
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        data.clearStoryProgress();
        data.setActiveStoryArc(StoryArcType.SHRINES_BETWEEN_ROADS);
        data.setStoryDiscovered(StoryArcType.SHRINES_BETWEEN_ROADS.id(), true);
        data.setStoryCompleted(StoryArcType.SHRINES_BETWEEN_ROADS.id(), false);
        data.setStoryChapterProgress(StoryArcType.SHRINES_BETWEEN_ROADS.id(), 5);
        data.setStoryInt(StoryQuestKeys.SHRINES_RELAY_CONTRACT_BASELINE,
                Math.max(0, TradeRouteService.completedGuildContracts(world, playerId) - 1));
        data.setStoryInt(StoryQuestKeys.SHRINES_RELAY_SUCCESS_BASELINE,
                TradeRouteService.totalRouteSuccesses(world, playerId));
        data.setStoryFlag(StoryQuestKeys.SHRINES_RELAY_READY, false);
        data.setStoryCooldownUntil(0L);
        QuestState.get(world.getServer()).setDirty();
        refreshQuestUi(world, playerId);
        return TradeRouteService.adminPrepareLastRelayTest(world, player);
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        ShadowsTradeRoadEncounterService.onServerTick(server);
        EmptyCaravanStoryService.onServerTick(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncDerivedProgression(world, player);
            StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
            if (chapter != null) {
                chapter.onServerTick(world, player);
            }
        }
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onBlockBreak(world, player, pos, state);
        }
    }

    public static void onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onUseBlock(world, player, pos, state, stack);
        }
    }

    public static void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onBeeNestInteract(world, player, state, stack);
        }
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onEntityUse(world, player, entity, inHand);
        }
    }

    public static void onSheepSheared(ServerLevel world, ServerPlayer player, net.minecraft.world.entity.animal.sheep.Sheep sheep) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onSheepSheared(world, player, sheep);
        }
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onTrackedItemPickup(world, player, stack, count);
        }
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onFurnaceOutput(world, player, stack);
        }
    }

    public static void onAnvilOutput(ServerLevel world,
                                     ServerPlayer player,
                                     ItemStack leftInput,
                                     ItemStack rightInput,
                                     ItemStack output) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onAnvilOutput(world, player, leftInput, rightInput, output);
        }
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onVillagerTrade(world, player, stack);
        }
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onAnimalLove(world, player, animal);
        }
    }

    public static void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onPilgrimPurchase(world, player, offerId);
        }
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUUID());
        if (chapter != null) {
            chapter.onMonsterKill(world, player, killedEntity);
        }
    }

    private static void deliverCompletion(ServerLevel world, ServerPlayer player, StoryChapterCompletion completion) {
        boolean unlockedProject = completion.unlockedProject() != null
                && VillageProjectService.unlock(world, player.getUUID(), completion.unlockedProject());
        if (unlockedProject) {
            SurveyorCompassQuestService.onVillageProjectUnlocked(world, player, completion.unlockedProject());
            if (completion.unlockedProject() == VillageProjectType.MARKET_CHARTER) {
                TradeRouteService.initializeProvisionalNetwork(world, player);
            }
        }
        long actualCurrencyReward = scaledCurrencyReward(completion.currencyReward())
                + VillageProjectService.bonusCurrency(world, player.getUUID(), completion.reputationTrack());
        if (actualCurrencyReward > 0L) {
            CurrencyService.addBalance(world, player.getUUID(), actualCurrencyReward);
        }
        int actualReputation = 0;
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            actualReputation = VillageProjectService.applyReputationReward(world, player.getUUID(), completion.reputationTrack(), completion.reputationAmount());
        }
        int projectExperienceBonus = VillageProjectService.bonusLevels(
                world,
                player.getUUID(),
                completion.reputationTrack(),
                completion.unlockedProject()
        );

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component rewardsTitle = Component.translatable("text.village-quest.daily.rewards").withStyle(ChatFormatting.GRAY);
        Component levelLine = Component.empty().append(Component.literal("    "))
                .append(QuestExperienceService.rewardLine(
                        completion.levels(),
                        projectExperienceBonus,
                        QuestExperienceService.RewardType.STORY
                ));

        MutableComponent rewardBody = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(completion.title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(completion.completionLine1()).append(Component.literal("\n"))
                .append(completion.completionLine2()).append(Component.literal("\n\n"))
                .append(completion.completionLine3()).append(Component.literal("\n\n"))
                .append(rewardsTitle).append(Component.literal(":\n\n"));

        appendCurrencyRewardLine(rewardBody, actualCurrencyReward);
        if (completion.reputationTrack() != null && actualReputation > 0) {
            appendTextRewardLine(rewardBody, ReputationService.formatRewardLine(completion.reputationTrack(), actualReputation));
        }
        appendTextRewardLine(rewardBody, VillageProjectService.formatBonusRewardLine(world, player.getUUID(), completion.reputationTrack()));
        appendTextRewardLine(rewardBody, VillageProjectService.formatRewardEchoLine(world, player.getUUID(), completion.reputationTrack()));
        if (unlockedProject) {
            appendTextRewardLine(rewardBody, Component.translatable(
                    "message.village-quest.story.project_unlocked",
                    Component.translatable("quest.village-quest.project." + completion.unlockedProject().id() + ".title")
            ).withStyle(ChatFormatting.AQUA));
        }
        rewardBody.append(levelLine).append(Component.literal("\n")).append(divider.copy());

        player.sendSystemMessage(rewardBody, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.0f);
        QuestExperienceService.grant(
                player,
                completion.levels(),
                projectExperienceBonus,
                QuestExperienceService.RewardType.STORY
        );
    }

    private static void appendCurrencyRewardLine(MutableComponent body, long amount) {
        if (amount <= 0L) {
            return;
        }
        body.append(Component.empty().append(Component.literal("    ")).append(CurrencyService.formatDelta(amount))).append(Component.literal("\n"));
    }

    private static void appendTextRewardLine(MutableComponent body, Component line) {
        if (line == null || line.getString().isEmpty()) {
            return;
        }
        body.append(Component.empty().append(Component.literal("    ")).append(line)).append(Component.literal("\n"));
    }

    private static long scaledCurrencyReward(long baseCurrencyReward) {
        if (baseCurrencyReward <= 0L) {
            return 0L;
        }
        return Math.max(0L, Math.round(baseCurrencyReward * STORY_CURRENCY_MULTIPLIER));
    }

    private static String readyFlagKey(StoryArcType arcType, int chapterIndex) {
        return READY_FLAG_PREFIX + arcType.id() + "_" + chapterIndex;
    }

    private static StoryArcType availableArcTypeInternal(ServerLevel world, UUID playerId, boolean respectCooldown) {
        if (world == null || playerId == null) {
            return null;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        if (activeArc != null) {
            return activeArc;
        }
        if (respectCooldown && isStoryCooldownActive(world, playerId)) {
            return null;
        }
        for (StoryArcType type : StoryArcType.questmasterArcs()) {
            StoryArcDefinition arc = definition(type);
            if (arc != null && arc.isUnlocked(world, playerId) && !isCompleted(world, playerId, type)) {
                return type;
            }
        }
        return null;
    }

    private static void armStoryCooldownIfNeeded(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        long cooldownUntil = availableArcTypeIgnoringCooldown(world, playerId) == null
                ? 0L
                : System.currentTimeMillis() + STORY_ARC_COOLDOWN_MILLIS;
        PlayerQuestData data = data(world, playerId);
        if (data.getStoryCooldownUntil() == cooldownUntil) {
            return;
        }
        data.setStoryCooldownUntil(cooldownUntil);
        QuestState.get(world.getServer()).setDirty();
    }

    private static void syncDerivedProgression(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
    }

    public static int countCompletionItem(ServerLevel world, UUID playerId, Item item) {
        if (world == null || playerId == null || item == null) {
            return 0;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        if (activeArc != null && QuestPartyService.isSharedStoryMember(world, playerId, activeArc, chapterIndex(world, playerId, activeArc))) {
            return QuestPartyService.countStoryTurnInItem(world, playerId, activeArc, chapterIndex(world, playerId, activeArc), item);
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        return player == null ? 0 : DailyQuestService.countInventoryItem(player, item);
    }

    public static boolean consumeCompletionItem(ServerLevel world, UUID playerId, Item item, int amount) {
        if (world == null || playerId == null || item == null || amount <= 0) {
            return false;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        if (activeArc != null && QuestPartyService.isSharedStoryMember(world, playerId, activeArc, chapterIndex(world, playerId, activeArc))) {
            return QuestPartyService.consumeStoryTurnInItem(world, playerId, activeArc, chapterIndex(world, playerId, activeArc), item, amount);
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        return player != null && DailyQuestService.consumeInventoryItem(player, item, amount);
    }

    /**
     * Validates a complete multi-item turn-in before removing its first stack.
     * Quest claims run on the server thread, so the second pass is atomic with
     * respect to other quest actions and cannot leave a partial delivery behind.
     */
    public static boolean consumeCompletionItems(ServerLevel world, UUID playerId, Map<Item, Integer> requirements) {
        if (world == null || playerId == null || requirements == null || requirements.isEmpty()) {
            return false;
        }
        for (Map.Entry<Item, Integer> requirement : requirements.entrySet()) {
            Item item = requirement.getKey();
            int amount = requirement.getValue() == null ? 0 : requirement.getValue();
            if (item == null || amount <= 0 || countCompletionItem(world, playerId, item) < amount) {
                return false;
            }
        }
        for (Map.Entry<Item, Integer> requirement : requirements.entrySet()) {
            if (!consumeCompletionItem(world, playerId, requirement.getKey(), requirement.getValue())) {
                return false;
            }
        }
        return true;
    }

    public static int countMatchingCompletionItems(ServerLevel world, UUID playerId, java.util.function.Predicate<ItemStack> matcher) {
        if (world == null || playerId == null || matcher == null) {
            return 0;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        if (activeArc != null && QuestPartyService.isSharedStoryMember(world, playerId, activeArc, chapterIndex(world, playerId, activeArc))) {
            return QuestPartyService.countStoryTurnInItems(world, playerId, activeArc, chapterIndex(world, playerId, activeArc), matcher);
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        return countMatchingInventory(player, matcher);
    }

    public static boolean consumeMatchingCompletionItems(ServerLevel world, UUID playerId, java.util.function.Predicate<ItemStack> matcher, int amount) {
        if (world == null || playerId == null || matcher == null || amount <= 0) {
            return false;
        }
        StoryArcType activeArc = activeArcType(world, playerId);
        if (activeArc != null && QuestPartyService.isSharedStoryMember(world, playerId, activeArc, chapterIndex(world, playerId, activeArc))) {
            return QuestPartyService.consumeStoryTurnInItems(world, playerId, activeArc, chapterIndex(world, playerId, activeArc), matcher, amount);
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        return consumeMatchingInventory(player, matcher, amount);
    }

    private static int countMatchingInventory(ServerPlayer player, java.util.function.Predicate<ItemStack> matcher) {
        if (player == null || matcher == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consumeMatchingInventory(ServerPlayer player, java.util.function.Predicate<ItemStack> matcher, int amount) {
        if (player == null || matcher == null || amount <= 0 || countMatchingInventory(player, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return remaining <= 0;
    }

    private static void finishChapterProgress(ServerLevel world, UUID playerId, StoryArcType arcType, int chapterIndex) {
        if (world == null || playerId == null || arcType == null) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        data.clearStoryProgress();
        data.setActiveStoryArc(null);
        int nextChapter = chapterIndex + 1;
        data.setStoryChapterProgress(arcType.id(), nextChapter);
        boolean completedArc = nextChapter >= definition(arcType).chapterCount();
        if (completedArc) {
            data.setStoryCompleted(arcType.id(), true);
        }
        QuestState.get(world.getServer()).setDirty();
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            syncDerivedProgression(world, player);
        } else if (completedArc) {
            armStoryCooldownIfNeeded(world, playerId);
        }
        if (completedArc) {
            armStoryCooldownIfNeeded(world, playerId);
        }
        refreshQuestUi(world, playerId);
    }

    private static void refreshRecipients(ServerLevel world, List<UUID> recipients) {
        if (world == null || recipients == null) {
            return;
        }
        for (UUID recipientId : recipients) {
            refreshQuestUi(world, recipientId);
        }
    }

    private static List<Component> currentProgressLines(ServerLevel world, UUID playerId) {
        StoryChapterDefinition chapter = currentChapter(world, playerId);
        if (chapter == null) {
            return List.of();
        }
        return List.copyOf(chapter.progressLines(world, playerId));
    }

    private static Map<UUID, List<Component>> captureProgressSnapshots(ServerLevel world, List<UUID> recipients) {
        Map<UUID, List<Component>> snapshots = new LinkedHashMap<>();
        if (recipients == null) {
            return snapshots;
        }
        for (UUID recipientId : recipients) {
            snapshots.put(recipientId, currentProgressLines(world, recipientId));
        }
        return snapshots;
    }

    private static void notifyProgressChange(ServerLevel world, UUID playerId, List<Component> before) {
        notifyProgressChange(world, playerId, before, true);
    }

    private static void notifyProgressChange(ServerLevel world, UUID playerId, List<Component> before,
                                             boolean playFeedback) {
        notifyProgressChange(world, List.of(playerId),
                Map.of(playerId, before == null ? List.of() : before), playFeedback);
    }

    private static void notifyProgressChange(ServerLevel world,
                                             List<UUID> recipients,
                                             Map<UUID, List<Component>> beforeSnapshots) {
        notifyProgressChange(world, recipients, beforeSnapshots, true);
    }

    private static void notifyProgressChange(ServerLevel world,
                                             List<UUID> recipients,
                                             Map<UUID, List<Component>> beforeSnapshots,
                                             boolean playFeedback) {
        if (world == null || recipients == null) {
            return;
        }
        for (UUID recipientId : recipients) {
            List<Component> after = currentProgressLines(world, recipientId);
            refreshQuestUi(world, recipientId);
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(recipientId);
            if (player != null && playFeedback) {
                QuestSoundFeedback.playProgressChange(
                        world,
                        player,
                        beforeSnapshots.getOrDefault(recipientId, List.of()),
                        after
                );
            }
        }
    }

    private static void refreshQuestUi(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }
}
