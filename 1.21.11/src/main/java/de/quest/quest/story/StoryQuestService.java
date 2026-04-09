package de.quest.quest.story;

import de.quest.content.story.FailingHarvestStoryArc;
import de.quest.content.story.MarketRoadTroublesStoryArc;
import de.quest.content.story.NightBellsStoryArc;
import de.quest.content.story.RestlessPensStoryArc;
import de.quest.content.story.SilentForgeStoryArc;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StoryQuestService {
    private static final String READY_FLAG_PREFIX = "story_ready_";
    private static final double STORY_CURRENCY_MULTIPLIER = 0.70d;
    private static final long STORY_ARC_COOLDOWN_MILLIS = 3L * 60L * 60L * 1000L;

    private static final Map<StoryArcType, StoryArcDefinition> ARCS = Map.of(
            StoryArcType.FAILING_HARVEST, new FailingHarvestStoryArc(),
            StoryArcType.SILENT_FORGE, new SilentForgeStoryArc(),
            StoryArcType.MARKET_ROAD_TROUBLES, new MarketRoadTroublesStoryArc(),
            StoryArcType.RESTLESS_PENS, new RestlessPensStoryArc(),
            StoryArcType.NIGHT_BELLS, new NightBellsStoryArc()
    );

    private StoryQuestService() {}

    public static boolean areCoreStoriesCompleted(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        for (StoryArcType type : StoryArcType.questmasterArcs()) {
            if (!isCompleted(world, playerId, type)) {
                return false;
            }
        }
        return true;
    }

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static int getStoryArcCount() {
        return StoryArcType.questmasterArcs().size();
    }

    public static int getQuestInt(ServerWorld world, UUID playerId, String key) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return 0;
        }
        return data(world, playerId).getStoryInt(key);
    }

    public static void setQuestInt(ServerWorld world, UUID playerId, String key, int value) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.getStoryInt(key) == value) {
            return;
        }
        data.setStoryInt(key, value);
        QuestState.get(world.getServer()).markDirty();
        refreshQuestUi(world, playerId);
    }

    public static void addQuestIntClamped(ServerWorld world, UUID playerId, String key, int amount, int target) {
        if (amount <= 0) {
            return;
        }
        int current = getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }
        setQuestInt(world, playerId, key, Math.min(target, current + amount));
    }

    public static boolean hasStoryFlag(ServerWorld world, UUID playerId, String key) {
        return world != null && playerId != null && key != null && !key.isEmpty() && data(world, playerId).hasStoryFlag(key);
    }

    public static void setStoryFlag(ServerWorld world, UUID playerId, String key, boolean enabled) {
        if (world == null || playerId == null || key == null || key.isEmpty()) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.hasStoryFlag(key) == enabled) {
            return;
        }
        data.setStoryFlag(key, enabled);
        QuestState.get(world.getServer()).markDirty();
        refreshQuestUi(world, playerId);
    }

    public static boolean acceptQuest(ServerWorld world, ServerPlayerEntity player, StoryArcType arcType) {
        if (world == null || player == null || arcType == null || !StoryArcType.isQuestmasterArc(arcType)) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        if (data.getActiveStoryArc() != null
                || isCompleted(world, player.getUuid(), arcType)
                || isStoryCooldownActive(world, player.getUuid())) {
            return false;
        }

        StoryArcDefinition arc = definition(arcType);
        if (arc == null || !arc.isUnlocked(world, player.getUuid())) {
            return false;
        }

        int chapterIndex = chapterIndex(world, player.getUuid(), arcType);
        StoryChapterDefinition chapter = arc.chapter(chapterIndex);
        if (chapter == null) {
            return false;
        }

        data.clearStoryProgress();
        data.setActiveStoryArc(arcType);
        data.setStoryDiscovered(arcType.id(), true);
        QuestState.get(world.getServer()).markDirty();
        chapter.onAccepted(world, player);
        player.sendMessage(Texts.acceptedTitle(chapter.title(), Formatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        refreshQuestUi(world, player.getUuid());
        return true;
    }

    public static boolean claimFromQuestMaster(ServerWorld world, ServerPlayerEntity player, StoryArcType arcType) {
        if (world == null || player == null || arcType == null || !isActive(world, player.getUuid(), arcType)) {
            return false;
        }

        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter == null) {
            return false;
        }
        if (!chapter.isComplete(world, player)) {
            Text blocked = chapter.claimBlockedMessage(world, player);
            if (blocked != null) {
                player.sendMessage(blocked, false);
            }
            return false;
        }
        if (!chapter.consumeCompletionRequirements(world, player)) {
            return false;
        }

        StoryChapterCompletion completion = chapter.buildCompletion();
        deliverCompletion(world, player, completion);

        PlayerQuestData data = data(world, player.getUuid());
        data.clearStoryProgress();
        data.setActiveStoryArc(null);
        int nextChapter = chapterIndex(world, player.getUuid(), arcType) + 1;
        data.setStoryChapterProgress(arcType.id(), nextChapter);
        boolean completedArc = nextChapter >= definition(arcType).chapterCount();
        if (completedArc) {
            data.setStoryCompleted(arcType.id(), true);
        }
        QuestState.get(world.getServer()).markDirty();
        syncDerivedProgression(world, player);
        if (completedArc) {
            armStoryCooldownIfNeeded(world, player.getUuid());
        }
        refreshQuestUi(world, player.getUuid());
        return true;
    }

    public static boolean completeIfEligible(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        StoryArcType activeArc = activeArcType(world, player.getUuid());
        if (activeArc == null) {
            return false;
        }
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter == null || !chapter.isComplete(world, player)) {
            return false;
        }
        int chapterIndex = chapterIndex(world, player.getUuid(), activeArc);
        PlayerQuestData data = data(world, player.getUuid());
        String readyFlag = readyFlagKey(activeArc, chapterIndex);
        if (!data.hasStoryFlag(readyFlag)) {
            data.setStoryFlag(readyFlag, true);
            QuestState.get(world.getServer()).markDirty();
            player.sendMessage(Text.translatable("message.village-quest.story.ready", chapter.title()).formatted(Formatting.GOLD), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.28f, 1.7f);
        }
        refreshQuestUi(world, player.getUuid());
        return true;
    }

    public static StoryQuestStatus openStatus(ServerWorld world, UUID playerId) {
        StoryArcType activeArc = activeArcType(world, playerId);
        StoryChapterDefinition chapter = currentChapter(world, playerId);
        if (activeArc == null || chapter == null) {
            return null;
        }

        List<Text> lines = new ArrayList<>();
        int chapterNumber = chapterIndex(world, playerId, activeArc) + 1;
        lines.add(Text.translatable("text.village-quest.story.chapter_label", chapterNumber, chapter.title()).formatted(Formatting.GOLD));
        lines.addAll(chapter.progressLines(world, playerId));
        return new StoryQuestStatus(definition(activeArc).title(), List.copyOf(lines));
    }

    public static int discoveredCount(ServerWorld world, UUID playerId) {
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

    public static int completedCount(ServerWorld world, UUID playerId) {
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

    public static int activeCount(ServerWorld world, UUID playerId) {
        return activeArcType(world, playerId) == null ? 0 : 1;
    }

    public static boolean isActive(ServerWorld world, UUID playerId, StoryArcType arcType) {
        return arcType != null && arcType == activeArcType(world, playerId);
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId, StoryArcType arcType) {
        return world != null && playerId != null && arcType != null && data(world, playerId).hasStoryCompleted(arcType.id());
    }

    public static StoryArcType activeArcType(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        StoryArcType active = data(world, playerId).getActiveStoryArc();
        return StoryArcType.isQuestmasterArc(active) ? active : null;
    }

    public static StoryArcType availableArcType(ServerWorld world, UUID playerId) {
        return availableArcTypeInternal(world, playerId, true);
    }

    public static StoryArcType availableArcTypeIgnoringCooldown(ServerWorld world, UUID playerId) {
        return availableArcTypeInternal(world, playerId, false);
    }

    public static boolean isStoryCooldownActive(ServerWorld world, UUID playerId) {
        return getStoryCooldownRemainingMillis(world, playerId) > 0L;
    }

    public static long getStoryCooldownRemainingMillis(ServerWorld world, UUID playerId) {
        return Math.max(0L, getStoryCooldownUntil(world, playerId) - System.currentTimeMillis());
    }

    public static long getStoryCooldownUntil(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0L;
        }
        long cooldownUntil = data(world, playerId).getStoryCooldownUntil();
        return cooldownUntil > System.currentTimeMillis() ? cooldownUntil : 0L;
    }

    public static int chapterIndex(ServerWorld world, UUID playerId, StoryArcType arcType) {
        if (world == null || playerId == null || arcType == null) {
            return 0;
        }
        return Math.max(0, data(world, playerId).getStoryChapterProgress(arcType.id()));
    }

    public static StoryChapterDefinition chapter(ServerWorld world, UUID playerId, StoryArcType arcType) {
        StoryArcDefinition arc = definition(arcType);
        if (arc == null) {
            return null;
        }
        return arc.chapter(chapterIndex(world, playerId, arcType));
    }

    public static StoryChapterDefinition currentChapter(ServerWorld world, UUID playerId) {
        StoryArcType activeArc = activeArcType(world, playerId);
        return activeArc == null ? null : chapter(world, playerId, activeArc);
    }

    public static StoryArcDefinition definition(StoryArcType arcType) {
        return ARCS.get(arcType);
    }

    public static List<Text> previewRewardLines(StoryChapterCompletion completion) {
        if (completion == null) {
            return List.of();
        }
        List<Text> rewards = new ArrayList<>();
        long scaledCurrencyReward = scaledCurrencyReward(completion.currencyReward());
        if (scaledCurrencyReward > 0L) {
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(scaledCurrencyReward)).formatted(Formatting.GOLD));
        }
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            rewards.add(ReputationService.formatRewardLine(completion.reputationTrack(), completion.reputationAmount()));
        }
        if (completion.unlockedProject() != null) {
            rewards.add(Text.translatable(
                    "screen.village-quest.questmaster.reward.project",
                    Text.translatable("quest.village-quest.project." + completion.unlockedProject().id() + ".title")
            ).formatted(Formatting.AQUA));
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.effect." + completion.unlockedProject().id()).formatted(Formatting.GRAY));
        }
        if (completion.levels() > 0) {
            rewards.add(Text.translatable("screen.village-quest.questmaster.reward.levels", completion.levels()).formatted(Formatting.GREEN));
        }
        return rewards;
    }

    public static List<Text> buildOverview(ServerWorld world, UUID playerId) {
        List<Text> lines = new ArrayList<>();
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
            lines.add(Text.translatable(
                    "command.village-quest.questadmin.story.line",
                    arc.title(),
                    Text.translatable(statusKey),
                    chapterNumber,
                    arc.chapterCount()
            ).formatted(completed ? Formatting.AQUA : active ? Formatting.GREEN : unlocked ? Formatting.YELLOW : Formatting.DARK_GRAY));
        }
        return lines;
    }

    public static boolean adminResetStoryState(ServerWorld world, UUID playerId) {
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
        QuestState.get(world.getServer()).markDirty();
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean adminForceCompleteActiveChapter(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        StoryArcType arcType = activeArcType(world, player.getUuid());
        if (chapter == null || arcType == null) {
            return false;
        }
        StoryChapterCompletion completion = chapter.buildCompletion();
        deliverCompletion(world, player, completion);

        PlayerQuestData data = data(world, player.getUuid());
        data.clearStoryProgress();
        data.setActiveStoryArc(null);
        int nextChapter = chapterIndex(world, player.getUuid(), arcType) + 1;
        data.setStoryChapterProgress(arcType.id(), nextChapter);
        boolean completedArc = nextChapter >= definition(arcType).chapterCount();
        if (completedArc) {
            data.setStoryCompleted(arcType.id(), true);
        }
        QuestState.get(world.getServer()).markDirty();
        syncDerivedProgression(world, player);
        if (completedArc) {
            armStoryCooldownIfNeeded(world, player.getUuid());
        }
        refreshQuestUi(world, player.getUuid());
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncDerivedProgression(world, player);
            StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
            if (chapter != null) {
                chapter.onServerTick(world, player);
            }
        }
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onBlockBreak(world, player, pos, state);
        }
    }

    public static void onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onUseBlock(world, player, pos, state, stack);
        }
    }

    public static void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onBeeNestInteract(world, player, state, stack);
        }
    }

    public static void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onEntityUse(world, player, entity, inHand);
        }
    }

    public static void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onTrackedItemPickup(world, player, stack, count);
        }
    }

    public static void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onFurnaceOutput(world, player, stack);
        }
    }

    public static void onAnvilOutput(ServerWorld world,
                                     ServerPlayerEntity player,
                                     ItemStack leftInput,
                                     ItemStack rightInput,
                                     ItemStack output) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onAnvilOutput(world, player, leftInput, rightInput, output);
        }
    }

    public static void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onVillagerTrade(world, player, stack);
        }
    }

    public static void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onAnimalLove(world, player, animal);
        }
    }

    public static void onPilgrimPurchase(ServerWorld world, ServerPlayerEntity player, String offerId) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onPilgrimPurchase(world, player, offerId);
        }
    }

    public static void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        StoryChapterDefinition chapter = currentChapter(world, player.getUuid());
        if (chapter != null) {
            chapter.onMonsterKill(world, player, killedEntity);
        }
    }

    private static void deliverCompletion(ServerWorld world, ServerPlayerEntity player, StoryChapterCompletion completion) {
        long actualCurrencyReward = scaledCurrencyReward(completion.currencyReward())
                + VillageProjectService.bonusCurrency(world, player.getUuid(), completion.reputationTrack());
        if (actualCurrencyReward > 0L) {
            CurrencyService.addBalance(world, player.getUuid(), actualCurrencyReward);
        }
        int actualReputation = 0;
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            actualReputation = VillageProjectService.applyReputationReward(world, player.getUuid(), completion.reputationTrack(), completion.reputationAmount());
        }
        boolean unlockedProject = completion.unlockedProject() != null
                && VillageProjectService.unlock(world, player.getUuid(), completion.unlockedProject());
        if (unlockedProject) {
            SurveyorCompassQuestService.onVillageProjectUnlocked(world, player, completion.unlockedProject());
        }
        int actualLevelReward = completion.levels() + VillageProjectService.bonusLevels(world, player.getUuid(), completion.reputationTrack());

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text rewardsTitle = Text.translatable("text.village-quest.daily.rewards").formatted(Formatting.GRAY);
        Text levelLine = Text.empty().append(Text.literal("    "))
                .append(Text.translatable("text.village-quest.daily.level_reward", actualLevelReward).formatted(Formatting.GREEN));

        MutableText rewardBody = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(completion.title(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(completion.completionLine1()).append(Text.literal("\n"))
                .append(completion.completionLine2()).append(Text.literal("\n\n"))
                .append(completion.completionLine3()).append(Text.literal("\n\n"))
                .append(rewardsTitle).append(Text.literal(":\n\n"));

        appendCurrencyRewardLine(rewardBody, actualCurrencyReward);
        if (completion.reputationTrack() != null && actualReputation > 0) {
            appendTextRewardLine(rewardBody, ReputationService.formatRewardLine(completion.reputationTrack(), actualReputation));
        }
        appendTextRewardLine(rewardBody, VillageProjectService.formatBonusRewardLine(world, player.getUuid(), completion.reputationTrack()));
        appendTextRewardLine(rewardBody, VillageProjectService.formatRewardEchoLine(world, player.getUuid(), completion.reputationTrack()));
        if (unlockedProject) {
            appendTextRewardLine(rewardBody, Text.translatable(
                    "message.village-quest.story.project_unlocked",
                    Text.translatable("quest.village-quest.project." + completion.unlockedProject().id() + ".title")
            ).formatted(Formatting.AQUA));
        }
        rewardBody.append(levelLine).append(Text.literal("\n")).append(divider.copy());

        player.sendMessage(rewardBody, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9f, 1.0f);
        if (actualLevelReward > 0) {
            player.addExperienceLevels(actualLevelReward);
        }
    }

    private static void appendCurrencyRewardLine(MutableText body, long amount) {
        if (amount <= 0L) {
            return;
        }
        body.append(Text.empty().append(Text.literal("    ")).append(CurrencyService.formatDelta(amount))).append(Text.literal("\n"));
    }

    private static void appendTextRewardLine(MutableText body, Text line) {
        if (line == null || line.getString().isEmpty()) {
            return;
        }
        body.append(Text.empty().append(Text.literal("    ")).append(line)).append(Text.literal("\n"));
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

    private static StoryArcType availableArcTypeInternal(ServerWorld world, UUID playerId, boolean respectCooldown) {
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

    private static void armStoryCooldownIfNeeded(ServerWorld world, UUID playerId) {
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
        QuestState.get(world.getServer()).markDirty();
    }

    private static void syncDerivedProgression(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        boolean changed = false;

        if (data.getActiveStoryArc() == StoryArcType.NIGHT_BELLS) {
            data.clearStoryProgress();
            data.setActiveStoryArc(null);
            data.setStoryChapterProgress(StoryArcType.NIGHT_BELLS.id(), 0);
            changed = true;
        }
        if (data.getStoryDiscovered().contains(StoryArcType.NIGHT_BELLS.id())) {
            data.setStoryDiscovered(StoryArcType.NIGHT_BELLS.id(), false);
            changed = true;
        }
        if (data.getStoryCompleted().contains(StoryArcType.NIGHT_BELLS.id())) {
            data.setStoryCompleted(StoryArcType.NIGHT_BELLS.id(), false);
            changed = true;
        }
        if (data.getStoryChapterProgress(StoryArcType.NIGHT_BELLS.id()) != 0) {
            data.setStoryChapterProgress(StoryArcType.NIGHT_BELLS.id(), 0);
            changed = true;
        }

        boolean unlockedWatchBell = false;
        if (areCoreStoriesCompleted(world, playerId) && !VillageProjectService.isUnlocked(world, playerId, VillageProjectType.WATCH_BELL)) {
            VillageProjectService.unlock(world, playerId, VillageProjectType.WATCH_BELL);
            unlockedWatchBell = true;
            changed = true;
        }

        if (!changed) {
            return;
        }

        QuestState.get(world.getServer()).markDirty();
        if (unlockedWatchBell) {
            player.sendMessage(Text.translatable(
                    "message.village-quest.story.project_unlocked",
                    Text.translatable("quest.village-quest.project.watch_bell.title")
            ).formatted(Formatting.AQUA), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.65f, 1.0f);
        }
        refreshQuestUi(world, playerId);
    }

    private static void refreshQuestUi(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }
}
