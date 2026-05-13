package de.quest.quest.weekly;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.party.QuestPartyService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.quest.repeatable.RepeatableRewardTuning;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.quest.repeatable.RepeatableTargetTuning;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class WeeklyQuestService {
    public enum WeeklyQuestCategory {
        FARMING,
        CRAFTING,
        ANIMALS,
        TRADE,
        COMBAT
    }

    public enum WeeklyQuestType {
        HARVEST_FOR_VILLAGE(WeeklyQuestCategory.FARMING),
        BAKEHOUSE_STOCK(WeeklyQuestCategory.FARMING),
        SMITH_WEEK(WeeklyQuestCategory.CRAFTING),
        STALL_AND_PASTURE(WeeklyQuestCategory.ANIMALS),
        MARKET_WEEK(WeeklyQuestCategory.TRADE),
        NIGHT_WATCH(WeeklyQuestCategory.COMBAT),
        ROAD_WARDEN(WeeklyQuestCategory.COMBAT);

        private final WeeklyQuestCategory category;

        WeeklyQuestType(WeeklyQuestCategory category) {
            this.category = category;
        }

        public WeeklyQuestCategory category() {
            return category;
        }
    }

    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL, Items.BLACK_WOOL,
            Items.BROWN_WOOL, Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL
    };

    private static final int HARVEST_WHEAT_TARGET = 64;
    private static final int HARVEST_CARROT_TARGET = 32;
    private static final int HARVEST_POTATO_TARGET = 32;
    private static final int HARVEST_BREAD_TARGET = 16;

    private static final int BAKEHOUSE_BREAD_TARGET = 32;
    private static final int BAKEHOUSE_PIE_TARGET = 24;
    private static final int BAKEHOUSE_POTATO_TARGET = 24;

    private static final int SMITH_ORE_TARGET = 50;
    private static final int SMITH_GOLD_ORE_TARGET = 20;
    private static final int SMITH_IRON_TARGET = 50;
    private static final int SMITH_GOLD_TARGET = 20;

    private static final int PASTURE_BREED_TARGET = 32;
    private static final int PASTURE_SHEAR_TARGET = 20;
    private static final int PASTURE_WOOL_TARGET = 40;

    private static final int MARKET_TRADE_TARGET = 30;
    private static final int MARKET_EMERALD_TARGET = 64;
    private static final int MARKET_PILGRIM_PURCHASE_TARGET = 3;

    private static final int NIGHTWATCH_ZOMBIE_TARGET = 30;
    private static final int NIGHTWATCH_SKELETON_TARGET = 20;

    private static final int ROADWARDEN_HOSTILE_TARGET = 30;
    private static final int ROADWARDEN_CREEPER_TARGET = 10;
    private static final ThreadLocal<RepeatableTargetProfile> TARGET_PROFILE_CONTEXT = new ThreadLocal<>();

    private WeeklyQuestService() {}

    private static long currentCycle() {
        return TimeUtil.currentWeekCycle();
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void setDirty(ServerLevel world) {
        if (world != null) {
            QuestState.get(world.getServer()).setDirty();
        }
    }

    private static RepeatableTargetProfile rollTargetProfile(ServerLevel world) {
        return world == null ? RepeatableTargetProfile.NORMAL : RepeatableTargetProfile.random(world.getRandom());
    }

    private static <T> T withTargetProfile(PlayerQuestData data, Supplier<T> supplier) {
        RepeatableTargetProfile previous = TARGET_PROFILE_CONTEXT.get();
        TARGET_PROFILE_CONTEXT.set(data == null ? RepeatableTargetProfile.NORMAL : data.getWeeklyTargetProfile());
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                TARGET_PROFILE_CONTEXT.remove();
            } else {
                TARGET_PROFILE_CONTEXT.set(previous);
            }
        }
    }

    private static void withTargetProfile(PlayerQuestData data, Runnable action) {
        withTargetProfile(data, () -> {
            action.run();
            return null;
        });
    }

    private static RepeatableTargetProfile contextTargetProfile() {
        RepeatableTargetProfile profile = TARGET_PROFILE_CONTEXT.get();
        return profile == null ? RepeatableTargetProfile.NORMAL : profile;
    }

    private static int tunedTarget(int baseTarget, String salt) {
        return RepeatableTargetTuning.adjust(baseTarget, contextTargetProfile(), salt);
    }

    private static ItemStack tunedRewardStack(ItemStack reward) {
        return RepeatableRewardTuning.adjustRewardStack(reward, contextTargetProfile());
    }

    public static List<Component> previewProgressLines(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        PlayerQuestData data = data(world, playerId);
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(previewQuestChoice(world, playerId));
        if (definition == null) {
            return List.of();
        }
        return withTargetProfile(data, () -> List.copyOf(definition.progressLines(world, playerId)));
    }

    public static boolean isQuestReady(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition == null) {
            return false;
        }
        return withTargetProfile(data, () -> definition.isComplete(world, player));
    }

    private static void ensureCurrentProgressCycle(PlayerQuestData data) {
        long cycle = currentCycle();
        if (data.getWeeklyProgressCycle() != cycle) {
            data.clearWeeklyProgress();
            data.setWeeklyProgressCycle(PlayerQuestData.UNSET_DAY);
        }
    }

    private static void clearProgress(PlayerQuestData data) {
        data.clearWeeklyProgress();
        data.setWeeklyProgressCycle(PlayerQuestData.UNSET_DAY);
    }

    private static WeeklyQuestType ensureQuestChoice(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        return ensureQuestChoice(world, playerId, data);
    }

    private static WeeklyQuestType ensureQuestChoice(ServerLevel world, UUID playerId, PlayerQuestData data) {
        long cycle = currentCycle();
        boolean changed = false;

        if (data.getWeeklyAcceptedCycle() != PlayerQuestData.UNSET_DAY && data.getWeeklyAcceptedCycle() != cycle) {
            data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getWeeklyRewardCycle() != PlayerQuestData.UNSET_DAY && data.getWeeklyRewardCycle() != cycle) {
            data.setWeeklyRewardCycle(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getWeeklyProgressCycle() != PlayerQuestData.UNSET_DAY && data.getWeeklyProgressCycle() != cycle) {
            clearProgress(data);
            changed = true;
        }

        WeeklyQuestType currentChoice = data.getWeeklyChoice();
        if (currentChoice == null || data.getWeeklyChoiceCycle() != cycle) {
            WeeklyQuestDefinition definition = WeeklyQuestGenerator.pick(
                    world,
                    currentChoice,
                    currentChoice == null ? null : currentChoice.category()
            );
            if (definition == null) {
                return null;
            }
            data.setWeeklyChoice(definition.type());
            data.setWeeklyChoiceCycle(cycle);
            data.setWeeklyTargetProfile(rollTargetProfile(world));
            data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
            data.setWeeklyRewardCycle(PlayerQuestData.UNSET_DAY);
            clearProgress(data);
            data.markWeeklyDiscovered(definition.type().name());
            currentChoice = definition.type();
            changed = true;
        }

        if (changed) {
            setDirty(world);
        }
        return currentChoice;
    }

    public static int getQuestInt(ServerLevel world, UUID playerId, String key) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        WeeklyQuestType activeType = activeQuestType(world, playerId);
        if (QuestPartyService.usesSharedWeeklyInt(world, playerId, activeType, key)) {
            return QuestPartyService.getSharedWeeklyInt(world, playerId, activeType, key);
        }
        return data.getWeeklyInt(key);
    }

    public static void setQuestInt(ServerLevel world, UUID playerId, String key, int value) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        WeeklyQuestType activeType = activeQuestType(world, playerId);
        if (QuestPartyService.usesSharedWeeklyInt(world, playerId, activeType, key)) {
            if (QuestPartyService.getSharedWeeklyInt(world, playerId, activeType, key) == value) {
                return;
            }
            QuestPartyService.setSharedWeeklyInt(world, playerId, activeType, key, value);
            refreshRecipients(world, progressRecipients(world, playerId, activeType));
            return;
        }
        if (data.getWeeklyInt(key) == value) {
            return;
        }
        data.setWeeklyInt(key, value);
        data.setWeeklyProgressCycle(currentCycle());
        setDirty(world);
        refreshQuestUi(world, playerId);
    }

    public static void addQuestInt(ServerLevel world, UUID playerId, String key, int amount) {
        if (amount == 0) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        WeeklyQuestType activeType = activeQuestType(world, playerId);
        if (QuestPartyService.usesSharedWeeklyInt(world, playerId, activeType, key)) {
            QuestPartyService.addSharedWeeklyInt(world, playerId, activeType, key, amount);
            refreshRecipients(world, progressRecipients(world, playerId, activeType));
            return;
        }
        data.addWeeklyInt(key, amount);
        data.setWeeklyProgressCycle(currentCycle());
        setDirty(world);
        refreshQuestUi(world, playerId);
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

    public static boolean hasQuestFlag(ServerLevel world, UUID playerId, String key) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        WeeklyQuestType activeType = activeQuestType(world, playerId);
        if (QuestPartyService.usesSharedWeeklyFlag(world, playerId, activeType, key)) {
            return QuestPartyService.getSharedWeeklyFlag(world, playerId, activeType, key);
        }
        return data.hasWeeklyFlag(key);
    }

    public static void setQuestFlag(ServerLevel world, UUID playerId, String key, boolean enabled) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        WeeklyQuestType activeType = activeQuestType(world, playerId);
        if (QuestPartyService.usesSharedWeeklyFlag(world, playerId, activeType, key)) {
            if (QuestPartyService.getSharedWeeklyFlag(world, playerId, activeType, key) == enabled) {
                return;
            }
            QuestPartyService.setSharedWeeklyFlag(world, playerId, activeType, key, enabled);
            refreshRecipients(world, progressRecipients(world, playerId, activeType));
            return;
        }
        if (data.hasWeeklyFlag(key) == enabled) {
            return;
        }
        data.setWeeklyFlag(key, enabled);
        data.setWeeklyProgressCycle(currentCycle());
        setDirty(world);
        refreshQuestUi(world, playerId);
    }

    public static WeeklyQuestType previewQuestChoice(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        return ensureQuestChoice(world, playerId);
    }

    public static WeeklyQuestType activeQuestType(ServerLevel world, UUID playerId) {
        if (!isAcceptedThisWeek(world, playerId) || hasCompletedThisWeek(world, playerId)) {
            return null;
        }
        return previewQuestChoice(world, playerId);
    }

    public static boolean isAcceptedThisWeek(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getWeeklyAcceptedCycle() == currentCycle();
    }

    public static boolean hasCompletedThisWeek(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getWeeklyRewardCycle() == currentCycle();
    }

    public static void markCompletedThisWeek(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        data.setWeeklyRewardCycle(currentCycle());
        WeeklyQuestType type = data.getWeeklyChoice();
        if (type != null) {
            data.markWeeklyCompleted(type.name());
        }
        setDirty(world);
    }

    public static boolean isWeeklyActive(ServerLevel world, UUID playerId) {
        return isAcceptedThisWeek(world, playerId) && !hasCompletedThisWeek(world, playerId);
    }

    public static WeeklyQuestStatus openStatus(ServerLevel world, UUID playerId) {
        WeeklyQuestType type = activeQuestType(world, playerId);
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        if (definition == null) {
            return null;
        }
        return new WeeklyQuestStatus(definition.title(), previewProgressLines(world, playerId));
    }

    public static boolean acceptQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        if (isAcceptedThisWeek(world, playerId) || hasCompletedThisWeek(world, playerId)) {
            return false;
        }

        WeeklyQuestType type = ensureQuestChoice(world, playerId);
        WeeklyQuestType sharedType = QuestPartyService.resolveSharedWeeklyChoice(world, playerId, type);
        if (sharedType != null && sharedType != type) {
            type = sharedType;
            PlayerQuestData syncData = data(world, playerId);
            syncData.setWeeklyChoice(type);
            syncData.setWeeklyChoiceCycle(currentCycle());
            clearProgress(syncData);
            setDirty(world);
        }
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        if (definition == null) {
            return false;
        }

        PlayerQuestData data = data(world, playerId);
        clearProgress(data);
        data.setWeeklyAcceptedCycle(currentCycle());
        setDirty(world);
        withTargetProfile(data, () -> definition.onAccepted(world, player));
        QuestPartyService.onWeeklyQuestAccepted(world, player, type, definition);
        player.sendSystemMessage(Texts.acceptedTitle(definition.title(), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.0f);
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean completeIfEligible(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        if (!isAcceptedThisWeek(world, playerId) || hasCompletedThisWeek(world, playerId)) {
            return false;
        }

        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(previewQuestChoice(world, playerId));
        if (definition == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (!withTargetProfile(data, () -> definition.isComplete(world, player))) {
            Component blocked = withTargetProfile(data, () -> definition.claimBlockedMessage(world, player));
            if (blocked != null) {
                player.sendSystemMessage(blocked, false);
            }
            return false;
        }
        if (!withTargetProfile(data, () -> definition.consumeCompletionRequirements(world, player))) {
            return false;
        }

        WeeklyQuestType questType = activeQuestType(world, playerId);
        if (isSharedWeekly(world, playerId, questType)) {
            List<ServerPlayer> recipients = onlinePlayers(world, QuestPartyService.activeWeeklyMembers(world, playerId, questType, false));
            var completion = withTargetProfile(data, definition::buildCompletion);
            for (ServerPlayer recipient : recipients) {
                deliverCompletion(world, recipient, completion);
                markCompletedThisWeek(world, recipient.getUUID());
                refreshQuestUi(world, recipient.getUUID());
            }
            QuestPartyService.clearWeeklySessionIfFinished(world, playerId, questType);
            return !recipients.isEmpty();
        }

        deliverCompletion(world, player, withTargetProfile(data, definition::buildCompletion));
        markCompletedThisWeek(world, playerId);
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean cancelThisWeek(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        if (!isAcceptedThisWeek(world, playerId) || hasCompletedThisWeek(world, playerId)) {
            return false;
        }

        PlayerQuestData data = data(world, playerId);
        clearProgress(data);
        data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
        setDirty(world);
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean adminForceCompleteWeekly(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        if (!isAcceptedThisWeek(world, playerId) || hasCompletedThisWeek(world, playerId)) {
            return false;
        }

        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(previewQuestChoice(world, playerId));
        if (definition == null) {
            return false;
        }

        WeeklyQuestType questType = activeQuestType(world, playerId);
        if (isSharedWeekly(world, playerId, questType)) {
            List<ServerPlayer> recipients = onlinePlayers(world, QuestPartyService.activeWeeklyMembers(world, playerId, questType, false));
            var completion = withTargetProfile(data(world, playerId), definition::buildCompletion);
            for (ServerPlayer recipient : recipients) {
                deliverCompletion(world, recipient, completion);
                markCompletedThisWeek(world, recipient.getUUID());
                refreshQuestUi(world, recipient.getUUID());
            }
            QuestPartyService.clearWeeklySessionIfFinished(world, playerId, questType);
            return !recipients.isEmpty();
        }

        deliverCompletion(world, player, withTargetProfile(data(world, playerId), definition::buildCompletion));
        markCompletedThisWeek(world, playerId);
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean adminResetWeeklyState(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }

        PlayerQuestData data = data(world, playerId);
        boolean changed = false;
        if (!data.getWeeklyIntState().isEmpty() || !data.getWeeklyFlags().isEmpty()) {
            clearProgress(data);
            changed = true;
        }
        if (data.getWeeklyAcceptedCycle() != PlayerQuestData.UNSET_DAY) {
            data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getWeeklyRewardCycle() != PlayerQuestData.UNSET_DAY) {
            data.setWeeklyRewardCycle(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getWeeklyChoice() != null || data.getWeeklyChoiceCycle() != PlayerQuestData.UNSET_DAY) {
            data.setWeeklyChoice(null);
            data.setWeeklyChoiceCycle(PlayerQuestData.UNSET_DAY);
            data.setWeeklyTargetProfile(RepeatableTargetProfile.NORMAL);
            changed = true;
        }
        if (changed) {
            setDirty(world);
        }
        return changed;
    }

    public static WeeklyQuestType adminPrepareNextWeekly(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }

        PlayerQuestData data = data(world, playerId);
        WeeklyQuestType previous = ensureQuestChoice(world, playerId, data);
        WeeklyQuestDefinition rerolled = WeeklyQuestGenerator.pick(
                world,
                previous,
                previous == null ? null : previous.category()
        );
        if (rerolled == null) {
            return previous;
        }

        data.setWeeklyChoice(rerolled.type());
        data.setWeeklyChoiceCycle(currentCycle());
        data.setWeeklyTargetProfile(rollTargetProfile(world));
        data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
        data.setWeeklyRewardCycle(PlayerQuestData.UNSET_DAY);
        clearProgress(data);
        data.markWeeklyDiscovered(rerolled.type().name());
        setDirty(world);
        return previous;
    }

    public static int getWeeklyQuestCount() {
        return WeeklyQuestGenerator.count();
    }

    public static int discoveredCount(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0;
        }
        PlayerQuestData data = data(world, playerId);
        int count = 0;
        for (String id : data.getWeeklyDiscovered()) {
            try {
                WeeklyQuestType.valueOf(id);
                count++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return count;
    }

    public static int completedCount(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0;
        }
        PlayerQuestData data = data(world, playerId);
        int count = 0;
        for (String id : data.getWeeklyCompleted()) {
            try {
                WeeklyQuestType.valueOf(id);
                count++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return count;
    }

    public static int activeCount(ServerLevel world, UUID playerId) {
        return isWeeklyActive(world, playerId) ? 1 : 0;
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            ensureQuestChoice(world, playerId);
            WeeklyQuestDefinition definition = activeDefinition(world, playerId);
            if (definition != null) {
                withTargetProfile(data(world, playerId), () -> definition.onServerTick(world, player));
            }
        }
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onBlockBreak(world, player, pos, state));
        }
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onEntityUse(world, player, entity, inHand));
        }
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onTrackedItemPickup(world, player, stack, count));
        }
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onFurnaceOutput(world, player, stack));
        }
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onVillagerTrade(world, player, stack));
        }
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onAnimalLove(world, player, animal));
        }
    }

    public static void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onPilgrimPurchase(world, player, offerId));
        }
        StoryQuestService.onPilgrimPurchase(world, player, offerId);
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (world == null || player == null || killedEntity == null) {
            return;
        }
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            withTargetProfile(data(world, player.getUUID()), () -> definition.onMonsterKill(world, player, killedEntity));
        }
    }

    public static WeeklyQuestCompletion buildCompletion(Component title,
                                                        Component completionLine1,
                                                        Component completionLine2,
                                                        Component completionLine3,
                                                        long currencyReward,
                                                        ItemStack rewardB,
                                                        ItemStack rewardC,
                                                        int levels,
                                                        ReputationService.ReputationTrack reputationTrack,
                                                        int reputationAmount) {
        RepeatableTargetProfile profile = contextTargetProfile();
        return new WeeklyQuestCompletion(
                title,
                completionLine1,
                completionLine2,
                completionLine3,
                RepeatableRewardTuning.adjustCurrency(currencyReward, profile),
                tunedRewardStack(rewardB),
                tunedRewardStack(rewardC),
                RepeatableRewardTuning.adjustLevels(levels, profile),
                reputationTrack,
                RepeatableRewardTuning.adjustReputation(reputationAmount, profile)
        );
    }

    public static long reward(int crowns, int silvermarks) {
        long crownPart = Math.max(0, crowns) * CurrencyService.CROWN;
        long silverPart = Math.max(0, silvermarks) * CurrencyService.SILVERMARK;
        return crownPart + silverPart;
    }

    public static ItemStack magicShardReward(int count) {
        if (count <= 0 || ModItems.MAGIC_SHARD == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ModItems.MAGIC_SHARD, count);
    }

    public static Component displayName(WeeklyQuestType type) {
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        return definition == null ? Component.empty() : definition.title();
    }

    public static WeeklyQuestCompletion previewCompletion(ServerLevel world, UUID playerId, WeeklyQuestType type) {
        if (world == null || playerId == null || type == null) {
            return null;
        }
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        if (definition == null) {
            return null;
        }
        return withTargetProfile(data(world, playerId), definition::buildCompletion);
    }

    public static int harvestWheatTarget() { return tunedTarget(HARVEST_WHEAT_TARGET, "weekly.harvest.wheat"); }
    public static int harvestCarrotTarget() { return tunedTarget(HARVEST_CARROT_TARGET, "weekly.harvest.carrot"); }
    public static int harvestPotatoTarget() { return tunedTarget(HARVEST_POTATO_TARGET, "weekly.harvest.potato"); }
    public static int harvestBreadTarget() { return tunedTarget(HARVEST_BREAD_TARGET, "weekly.harvest.bread"); }
    public static int bakehouseBreadTarget() { return tunedTarget(BAKEHOUSE_BREAD_TARGET, "weekly.bakehouse.bread"); }
    public static int bakehousePieTarget() { return tunedTarget(BAKEHOUSE_PIE_TARGET, "weekly.bakehouse.pie"); }
    public static int bakehousePotatoTarget() { return tunedTarget(BAKEHOUSE_POTATO_TARGET, "weekly.bakehouse.potato"); }
    public static int smithOreTarget() { return tunedTarget(SMITH_ORE_TARGET, "weekly.smith.ore"); }
    public static int smithGoldOreTarget() { return tunedTarget(SMITH_GOLD_ORE_TARGET, "weekly.smith.gold_ore"); }
    public static int smithIronTarget() { return tunedTarget(SMITH_IRON_TARGET, "weekly.smith.iron"); }
    public static int smithGoldTarget() { return tunedTarget(SMITH_GOLD_TARGET, "weekly.smith.gold"); }
    public static int pastureBreedTarget() { return tunedTarget(PASTURE_BREED_TARGET, "weekly.pasture.breed"); }
    public static int pastureShearTarget() { return tunedTarget(PASTURE_SHEAR_TARGET, "weekly.pasture.shear"); }
    public static int pastureWoolTarget() { return tunedTarget(PASTURE_WOOL_TARGET, "weekly.pasture.wool"); }
    public static int marketTradeTarget() { return tunedTarget(MARKET_TRADE_TARGET, "weekly.market.trade"); }
    public static int marketEmeraldTarget() { return tunedTarget(MARKET_EMERALD_TARGET, "weekly.market.emerald"); }
    public static int marketPilgrimPurchaseTarget() { return tunedTarget(MARKET_PILGRIM_PURCHASE_TARGET, "weekly.market.pilgrim"); }
    public static int nightWatchZombieTarget() { return tunedTarget(NIGHTWATCH_ZOMBIE_TARGET, "weekly.nightwatch.zombie"); }
    public static int nightWatchSkeletonTarget() { return tunedTarget(NIGHTWATCH_SKELETON_TARGET, "weekly.nightwatch.skeleton"); }
    public static int roadWardenHostileTarget() { return tunedTarget(ROADWARDEN_HOSTILE_TARGET, "weekly.roadwarden.hostile"); }
    public static int roadWardenCreeperTarget() { return tunedTarget(ROADWARDEN_CREEPER_TARGET, "weekly.roadwarden.creeper"); }

    public static int getCraftedStat(ServerPlayer player, Item item) {
        return DailyQuestService.getCraftedStat(player, item);
    }

    public static int getPickedUpStat(ServerPlayer player, Item item) {
        return DailyQuestService.getPickedUpStat(player, item);
    }

    public static int countInventoryItem(Player player, Item item) {
        if (player == null || item == null) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean consumeInventoryItem(Player player, Item item, int amount) {
        if (player == null || item == null || amount <= 0) {
            return false;
        }
        if (countInventoryItem(player, item) < amount) {
            return false;
        }

        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        return remaining <= 0;
    }

    public static int totalWoolPickedUp(ServerPlayer player) {
        int total = 0;
        for (Item item : WOOL_ITEMS) {
            total += getPickedUpStat(player, item);
        }
        return total;
    }

    public static int countCompletionItem(ServerLevel world, ServerPlayer player, Item item) {
        if (player == null) {
            return 0;
        }
        WeeklyQuestType type = activeQuestType(world, player.getUUID());
        if (isSharedWeekly(world, player.getUUID(), type)) {
            return QuestPartyService.countWeeklyTurnInItem(world, player.getUUID(), type, item);
        }
        return countInventoryItem(player, item);
    }

    public static int countCompletionItems(ServerLevel world, ServerPlayer player, Item... items) {
        if (player == null || items == null || items.length == 0) {
            return 0;
        }
        int total = 0;
        for (Item item : items) {
            total += countCompletionItem(world, player, item);
        }
        return total;
    }

    public static boolean consumeCompletionItem(ServerLevel world, ServerPlayer player, Item item, int amount) {
        if (player == null) {
            return false;
        }
        WeeklyQuestType type = activeQuestType(world, player.getUUID());
        if (isSharedWeekly(world, player.getUUID(), type)) {
            return QuestPartyService.consumeWeeklyTurnInItem(world, player.getUUID(), type, item, amount);
        }
        return consumeInventoryItem(player, item, amount);
    }

    public static boolean consumeCompletionItems(ServerLevel world, ServerPlayer player, int amount, Item... items) {
        if (player == null || amount <= 0 || items == null || items.length == 0) {
            return false;
        }
        WeeklyQuestType type = activeQuestType(world, player.getUUID());
        if (!isSharedWeekly(world, player.getUUID(), type)) {
            return DailyQuestService.consumeInventoryItems(player, amount, items);
        }
        if (countCompletionItems(world, player, items) < amount) {
            return false;
        }
        int remaining = amount;
        for (Item item : items) {
            if (remaining <= 0) {
                break;
            }
            int available = countCompletionItem(world, player, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!QuestPartyService.consumeWeeklyTurnInItem(world, player.getUUID(), type, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    private static WeeklyQuestDefinition activeDefinition(ServerLevel world, UUID playerId) {
        WeeklyQuestType type = activeQuestType(world, playerId);
        return WeeklyQuestGenerator.definition(type);
    }

    private static void deliverCompletion(ServerLevel world, ServerPlayer player, WeeklyQuestCompletion completion) {
        long actualCurrencyReward = completion.currencyReward() + VillageProjectService.bonusCurrency(world, player.getUUID(), completion.reputationTrack());
        if (actualCurrencyReward > 0L) {
            CurrencyService.addBalance(world, player.getUUID(), actualCurrencyReward);
        }
        int actualReputationReward = 0;
        if (completion.reputationTrack() != null && completion.reputationAmount() > 0) {
            actualReputationReward = VillageProjectService.applyReputationReward(world, player.getUUID(), completion.reputationTrack(), completion.reputationAmount());
        }
        giveReward(player, completion.rewardB());
        giveReward(player, completion.rewardC());
        int actualLevelReward = completion.levels() + VillageProjectService.bonusLevels(world, player.getUUID(), completion.reputationTrack());
        if (actualLevelReward > 0) {
            player.giveExperienceLevels(actualLevelReward);
        }

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component rewardsTitle = Component.translatable("text.village-quest.daily.rewards").withStyle(ChatFormatting.GRAY);
        Component levelLine = Component.empty().append(Component.literal("    "))
                .append(Component.translatable("text.village-quest.daily.level_reward", actualLevelReward).withStyle(ChatFormatting.GREEN));

        MutableComponent rewardBody = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(completion.title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(completion.completionLine1()).append(Component.literal("\n"))
                .append(completion.completionLine2()).append(Component.literal("\n\n"))
                .append(completion.completionLine3()).append(Component.literal("\n\n"))
                .append(rewardsTitle).append(Component.literal(":\n\n"));

        appendCurrencyRewardLine(rewardBody, actualCurrencyReward);
        if (completion.reputationTrack() != null && actualReputationReward > 0) {
            appendTextRewardLine(rewardBody, ReputationService.formatRewardLine(completion.reputationTrack(), actualReputationReward));
        }
        appendTextRewardLine(rewardBody, VillageProjectService.formatBonusRewardLine(world, player.getUUID(), completion.reputationTrack()));
        appendTextRewardLine(rewardBody, VillageProjectService.formatRewardEchoLine(world, player.getUUID(), completion.reputationTrack()));
        appendRewardLine(rewardBody, completion.rewardB());
        appendRewardLine(rewardBody, completion.rewardC());
        rewardBody.append(levelLine).append(Component.literal("\n")).append(divider.copy());

        player.sendSystemMessage(rewardBody, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    private static void appendRewardLine(MutableComponent body, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        body.append(Component.empty().append(Component.literal("    ")).append(formatRewardLine(stack))).append(Component.literal("\n"));
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

    private static Component formatRewardLine(ItemStack stack) {
        Component base = stack.getDisplayName().copy();
        if (stack.getCount() > 1) {
            base = Component.empty().append(base).append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GRAY));
        }
        return base;
    }

    private static void giveReward(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            giveOrDrop(player, stack.copy());
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix").withStyle(ChatFormatting.GRAY)
                    .append(stack.getDisplayName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix").withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.inventoryMenu.broadcastChanges();
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

    private static boolean isSharedWeekly(ServerLevel world, UUID playerId, WeeklyQuestType type) {
        return type != null
                && QuestPartyService.isSharedWeeklyMember(world, playerId, type);
    }

    private static List<UUID> progressRecipients(ServerLevel world, UUID playerId, WeeklyQuestType type) {
        if (isSharedWeekly(world, playerId, type)) {
            return QuestPartyService.activeWeeklyMembers(world, playerId, type, false);
        }
        return List.of(playerId);
    }

    private static void refreshRecipients(ServerLevel world, List<UUID> playerIds) {
        if (playerIds == null) {
            return;
        }
        for (UUID playerId : playerIds) {
            refreshQuestUi(world, playerId);
        }
    }

    private static List<ServerPlayer> onlinePlayers(ServerLevel world, List<UUID> playerIds) {
        List<ServerPlayer> players = new ArrayList<>();
        if (world == null || playerIds == null) {
            return players;
        }
        for (UUID playerId : playerIds) {
            ServerPlayer member = world.getServer().getPlayerList().getPlayer(playerId);
            if (member != null) {
                players.add(member);
            }
        }
        return players;
    }
}
