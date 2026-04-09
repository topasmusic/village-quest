package de.quest.quest.daily;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.painting.PaintingStackFactory;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.reputation.ReputationService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.questmaster.QuestMasterProgressionService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;
import java.util.UUID;

public final class DailyQuestService {
    public enum DailyQuestDifficulty {
        EASY,
        STANDARD,
        HARD
    }

    public enum DailyQuestCategory {
        COOKING,
        FARM,
        ANIMALS,
        CRAFTING,
        VILLAGE,
        COMBAT
    }

    public enum DailyAdminState {
        NOT_GENERATED,
        OFFER_AVAILABLE,
        OFFER_PENDING,
        ACTIVE,
        COMPLETED,
        NEXTDAY_PREPARED
    }

    public enum DailyQuestType {
        HONEY(DailyQuestCategory.FARM),
        PET_COLLAR(DailyQuestCategory.ANIMALS),
        WHEAT_HARVEST(DailyQuestCategory.COOKING),
        POTATO_HARVEST(DailyQuestCategory.COOKING),
        WOODCUTTING(DailyQuestCategory.CRAFTING),
        COAL_MINING(DailyQuestCategory.CRAFTING),
        WOOL_WEAVING(DailyQuestCategory.ANIMALS),
        RIVER_MEAL(DailyQuestCategory.COOKING),
        AUTUMN_HARVEST(DailyQuestCategory.FARM),
        SMITH_SMELTING(DailyQuestCategory.CRAFTING),
        STALL_NEW_LIFE(DailyQuestCategory.ANIMALS),
        VILLAGE_TRADING(DailyQuestCategory.VILLAGE),
        MARKET_ROUNDS(DailyQuestCategory.VILLAGE),
        ZOMBIE_CULL(DailyQuestCategory.COMBAT),
        SKELETON_PATROL(DailyQuestCategory.COMBAT),
        SPIDER_SWEEP(DailyQuestCategory.COMBAT),
        CREEPER_WATCH(DailyQuestCategory.COMBAT);

        private final DailyQuestCategory category;

        DailyQuestType(DailyQuestCategory category) {
            this.category = category;
        }

        public DailyQuestCategory category() {
            return category;
        }
    }

    private static final int HONEY_TARGET = 5;
    private static final int COMB_TARGET = 2;
    private static final int WHEAT_TARGET = 24;
    private static final int BREAD_TARGET = 6;
    private static final int POTATO_TARGET = 30;
    private static final int CARROT_TARGET = 30;
    private static final int WOOD_TARGET = 64;
    private static final int COAL_TARGET = 32;
    private static final int IRON_TARGET = 40;
    private static final int SMITH_COAL_TARGET = 50;
    private static final int SHEEP_TARGET = 10;
    private static final int WOOL_TARGET = 0;
    private static final int RIVER_FISH_TARGET = 10;
    private static final int RIVER_COOKED_FISH_TARGET = 5;
    private static final int AUTUMN_PUMPKIN_TARGET = 12;
    private static final int AUTUMN_MELON_TARGET = 12;
    private static final int SMITH_SMELT_ORE_TARGET = 50;
    private static final int SMITH_SMELT_INGOT_TARGET = 50;
    private static final int STALL_BREED_TARGET = 10;
    private static final int VILLAGE_TRADE_TARGET = 20;
    private static final int VILLAGE_TRADE_EMERALD_TARGET = 30;
    private static final float MAGIC_SHARD_DROP_CHANCE = 0.10f;

    private enum ActiveQuestSlot {
        NORMAL,
        BONUS
    }

    private record QuestStatusSnapshot(Component title, Component progressLine) {}

    private DailyQuestService() {}

    private static long currentDay() {
        return TimeUtil.currentDay();
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerLevel world) {
        if (world == null) {
            return;
        }
        QuestState.get(world.getServer()).setDirty();
    }

    private static void ensureCurrentProgressDay(PlayerQuestData data) {
        long day = currentDay();
        if (data.getProgressDay() != day) {
            data.clearDailyProgress();
            data.setProgressDay(day);
        }
    }

    private static void clearQuestProgress(PlayerQuestData data) {
        data.clearDailyProgress();
        data.setProgressDay(PlayerQuestData.UNSET_DAY);
    }

    private static void clearPendingOffers(PlayerQuestData data) {
        data.setPendingDailyOffer(false);
        data.setPendingShardOffer(false);
        data.setPendingBonusOffer(false);
    }

    public static int getQuestInt(ServerLevel world, UUID playerId, String key) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressDay(data);
        return data.getDailyInt(key);
    }

    public static void setQuestInt(ServerLevel world, UUID playerId, String key, int value) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressDay(data);
        if (data.getDailyInt(key) == value) {
            return;
        }
        QuestStatusSnapshot before = captureActiveQuestSnapshot(world, playerId);
        data.setDailyInt(key, value);
        data.setProgressDay(currentDay());
        markDirty(world);
        notifyDailyProgressChange(world, playerId, before);
    }

    public static void addQuestInt(ServerLevel world, UUID playerId, String key, int amount) {
        if (amount == 0) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressDay(data);
        QuestStatusSnapshot before = captureActiveQuestSnapshot(world, playerId);
        data.addDailyInt(key, amount);
        data.setProgressDay(currentDay());
        markDirty(world);
        notifyDailyProgressChange(world, playerId, before);
    }

    public static boolean hasQuestFlag(ServerLevel world, UUID playerId, String key) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressDay(data);
        return data.hasDailyFlag(key);
    }

    public static void setQuestFlag(ServerLevel world, UUID playerId, String key, boolean enabled) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressDay(data);
        if (data.hasDailyFlag(key) == enabled) {
            return;
        }
        QuestStatusSnapshot before = captureActiveQuestSnapshot(world, playerId);
        data.setDailyFlag(key, enabled);
        data.setProgressDay(currentDay());
        markDirty(world);
        notifyDailyProgressChange(world, playerId, before);
    }

    public static boolean hasCompletedToday(ServerLevel world, UUID playerId) {
        return data(world, playerId).getLastRewardDay() == currentDay();
    }

    public static void markCompletedToday(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        data.setLastRewardDay(currentDay());
        markDirty(world);
    }

    public static boolean hasCompletedBonusToday(ServerLevel world, UUID playerId) {
        return data(world, playerId).getBonusRewardDay() == currentDay();
    }

    public static void markBonusCompletedToday(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        data.setBonusRewardDay(currentDay());
        markDirty(world);
    }

    public static boolean isAcceptedToday(ServerLevel world, UUID playerId) {
        return data(world, playerId).getAcceptedDay() == currentDay();
    }

    public static void setAcceptedToday(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        data.setAcceptedDay(currentDay());
        markDirty(world);
    }

    public static boolean isBonusAcceptedToday(ServerLevel world, UUID playerId) {
        return data(world, playerId).getBonusAcceptedDay() == currentDay();
    }

    public static void setBonusAcceptedToday(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        data.setBonusAcceptedDay(currentDay());
        markDirty(world);
    }

    public static boolean cancelToday(ServerLevel world, UUID playerId) {
        if (isBonusAcceptedToday(world, playerId) && !hasCompletedBonusToday(world, playerId)) {
            PlayerQuestData data = data(world, playerId);
            clearQuestProgress(data);
            data.setBonusAcceptedDay(PlayerQuestData.UNSET_DAY);
            data.setPendingBonusOffer(false);
            markDirty(world);
            refreshQuestUi(world, playerId);
            return true;
        }
        if (!isAcceptedToday(world, playerId)) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        resetProgressFor(data);
        data.setAcceptedDay(PlayerQuestData.UNSET_DAY);
        clearPendingOffers(data);
        markDirty(world);
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean consumePendingDailyOffer(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (!data.isPendingDailyOffer()) {
            return false;
        }
        data.setPendingDailyOffer(false);
        markDirty(world);
        return true;
    }

    public static boolean consumePendingShardOffer(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (!data.isPendingShardOffer()) {
            return false;
        }
        data.setPendingShardOffer(false);
        markDirty(world);
        return true;
    }

    public static boolean consumePendingBonusOffer(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (!data.isPendingBonusOffer()) {
            return false;
        }
        data.setPendingBonusOffer(false);
        markDirty(world);
        return true;
    }

    private static DailyQuestDefinition definitionFor(DailyQuestType quest) {
        return DailyQuestGenerator.definition(quest);
    }

    private static DailyQuestDefinition activeDefinition(ServerLevel world, UUID playerId) {
        return definitionFor(activeQuestChoice(world, playerId));
    }

    private static ActiveQuestSlot activeQuestSlot(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        if (isBonusAcceptedToday(world, playerId) && !hasCompletedBonusToday(world, playerId)) {
            PlayerQuestData data = data(world, playerId);
            if (data.getBonusChoice() != null && data.getBonusChoiceDay() == currentDay()) {
                return ActiveQuestSlot.BONUS;
            }
        }
        if (isAcceptedToday(world, playerId) && !hasCompletedToday(world, playerId)) {
            return ActiveQuestSlot.NORMAL;
        }
        return null;
    }

    private static DailyQuestType activeQuestChoice(ServerLevel world, UUID playerId) {
        ActiveQuestSlot slot = activeQuestSlot(world, playerId);
        if (slot == null) {
            return null;
        }
        if (slot == ActiveQuestSlot.BONUS) {
            PlayerQuestData data = data(world, playerId);
            return data.getBonusChoiceDay() == currentDay() ? data.getBonusChoice() : null;
        }
        return ensureQuestChoice(world, playerId);
    }

    private static DailyQuestType ensureQuestChoice(ServerLevel world, UUID playerId) {
        return ensureQuestChoice(world, playerId, data(world, playerId));
    }

    private static DailyQuestType ensureQuestChoice(ServerLevel world, UUID playerId, PlayerQuestData data) {
        long day = currentDay();
        long chosenDay = data.getDailyChoiceDay();
        DailyQuestType choice = data.getDailyChoice();
        if (choice == null || chosenDay != day) {
            DailyQuestType excludedType = chosenDay == day - 1 ? choice : null;
            DailyQuestCategory excludedCategory = excludedType == null ? null : excludedType.category();
            DailyQuestDefinition definition = DailyQuestGenerator.pick(world, excludedType, excludedCategory);
            choice = definition == null ? DailyQuestType.HONEY : definition.type();
            data.setDailyChoice(choice);
            data.setDailyChoiceDay(day);
            resetProgressFor(data);
            markDirty(world);
        }
        return choice;
    }

    private static boolean hasBonusChoiceToday(PlayerQuestData data) {
        return data != null && data.getBonusChoice() != null && data.getBonusChoiceDay() == currentDay();
    }

    private static boolean playerHasMagicShard(ServerPlayer player) {
        return player != null && ModItems.MAGIC_SHARD != null && countInventoryItem(player, ModItems.MAGIC_SHARD) > 0;
    }

    private static boolean canOfferShardPrompt(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.MAGIC_SHARD == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        return hasCompletedToday(world, playerId)
                && !hasCompletedBonusToday(world, playerId)
                && !isBonusAcceptedToday(world, playerId)
                && !hasBonusChoiceToday(data)
                && playerHasMagicShard(player);
    }

    private static DailyQuestType rollBonusQuestChoice(ServerLevel world, DailyQuestType excludedType) {
        if (world == null) {
            return null;
        }
        DailyQuestCategory excludedCategory = excludedType == null ? null : excludedType.category();
        DailyQuestDefinition definition = DailyQuestGenerator.pick(world, excludedType, excludedCategory);
        if (definition == null) {
            definition = DailyQuestGenerator.pick(world, excludedType);
        }
        if (definition == null) {
            definition = DailyQuestGenerator.pick(world);
        }
        return definition == null ? null : definition.type();
    }

    public static void acceptQuest(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        markDailyDiscovered(world, playerId);
        PlayerQuestData data = data(world, playerId);
        clearPendingOffers(data);
        if (hasCompletedToday(world, playerId) || isAcceptedToday(world, playerId)) {
            return;
        }

        DailyQuestType choice = ensureQuestChoice(world, playerId, data);
        DailyQuestDefinition definition = definitionFor(choice);
        setAcceptedToday(world, playerId);
        if (definition != null) {
            definition.onAccepted(world, player);
        }

        Component title = definition == null ? fallbackQuestTitle(choice) : definition.title();
        player.sendSystemMessage(Texts.acceptedTitle(title, ChatFormatting.GREEN), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        sendCurrentProgressActionbar(world, player);
        refreshQuestUi(world, playerId);
    }

    public static void acceptBonusQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }

        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        clearPendingOffers(data);
        if (!hasBonusChoiceToday(data) || hasCompletedBonusToday(world, playerId) || isBonusAcceptedToday(world, playerId)) {
            return;
        }

        clearQuestProgress(data);
        setBonusAcceptedToday(world, playerId);
        DailyQuestDefinition definition = definitionFor(data.getBonusChoice());
        if (definition != null) {
            definition.onAccepted(world, player);
        }

        Component title = definition == null ? fallbackQuestTitle(data.getBonusChoice()) : definition.title();
        player.sendSystemMessage(Texts.acceptedTitle(title, ChatFormatting.LIGHT_PURPLE), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        sendCurrentProgressActionbar(world, player);
        refreshQuestUi(world, playerId);
    }

    public static boolean activateShardBonusQuestOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.MAGIC_SHARD == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        clearPendingOffers(data);
        if (!canOfferShardPrompt(world, player)) {
            return false;
        }
        if (!consumeInventoryItem(player, ModItems.MAGIC_SHARD, 1)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.shard_missing").withStyle(ChatFormatting.RED), false);
            return false;
        }

        DailyQuestType normalQuest = ensureQuestChoice(world, playerId, data);
        DailyQuestType bonusQuest = rollBonusQuestChoice(world, normalQuest);
        if (bonusQuest == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.shard_failed").withStyle(ChatFormatting.RED), false);
            return false;
        }

        clearQuestProgress(data);
        data.setBonusChoice(bonusQuest);
        data.setBonusChoiceDay(currentDay());
        markDirty(world);
        showBonusQuestOfferCompact(world, player);
        refreshQuestUi(world, playerId);
        return true;
    }

    private static Component fallbackQuestTitle(DailyQuestType quest) {
        return switch (quest) {
            case HONEY -> Component.translatable("quest.village-quest.daily.honey.title");
            case PET_COLLAR -> Component.translatable("quest.village-quest.daily.pet_collar.title");
            case WHEAT_HARVEST -> Component.translatable("quest.village-quest.daily.wheat.title");
            case POTATO_HARVEST -> Component.translatable("quest.village-quest.daily.potato.title");
            case WOODCUTTING -> Component.translatable("quest.village-quest.daily.wood.title");
            case COAL_MINING -> Component.translatable("quest.village-quest.daily.coal.title");
            case WOOL_WEAVING -> Component.translatable("quest.village-quest.daily.wool.title");
            case RIVER_MEAL -> Component.translatable("quest.village-quest.daily.river.title");
            case AUTUMN_HARVEST -> Component.translatable("quest.village-quest.daily.autumn.title");
            case SMITH_SMELTING -> Component.translatable("quest.village-quest.daily.smelt.title");
            case STALL_NEW_LIFE -> Component.translatable("quest.village-quest.daily.stall.title");
            case VILLAGE_TRADING -> Component.translatable("quest.village-quest.daily.trade.title");
            case MARKET_ROUNDS -> Component.translatable("quest.village-quest.daily.market_rounds.title");
            case ZOMBIE_CULL -> Component.translatable("quest.village-quest.daily.zombie_cull.title");
            case SKELETON_PATROL -> Component.translatable("quest.village-quest.daily.skeleton_patrol.title");
            case SPIDER_SWEEP -> Component.translatable("quest.village-quest.daily.spider_sweep.title");
            case CREEPER_WATCH -> Component.translatable("quest.village-quest.daily.creeper_watch.title");
        };
    }

    private static Component progressLine(ServerLevel world, UUID playerId, DailyQuestType quest) {
        DailyQuestDefinition definition = definitionFor(quest);
        if (definition != null) {
            return definition.progressLine(world, playerId);
        }
        return Component.empty();
    }

    private static QuestStatusSnapshot captureActiveQuestSnapshot(ServerLevel world, UUID playerId) {
        QuestStatus status = openQuestStatus(world, playerId);
        if (status == null) {
            return null;
        }
        return new QuestStatusSnapshot(status.title().copy(), status.progressLine().copy());
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

    private static void notifyDailyProgressChange(ServerLevel world, UUID playerId, QuestStatusSnapshot before) {
        QuestStatusSnapshot after = captureActiveQuestSnapshot(world, playerId);
        boolean changed = before == null
                ? after != null
                : after == null
                || !before.progressLine().getString().equals(after.progressLine().getString())
                || !before.title().getString().equals(after.title().getString());
        if (!changed) {
            return;
        }

        refreshQuestUi(world, playerId);

        if (after == null || world == null) {
            return;
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.sendSystemMessage(after.progressLine(), true);
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.18f, 1.55f);
    }

    public static Component displayKey(DailyQuestType quest) {
        return switch (quest) {
            case HONEY -> Component.translatable("command.village-quest.setquest.option.honey");
            case PET_COLLAR -> Component.translatable("command.village-quest.setquest.option.pet");
            case WHEAT_HARVEST -> Component.translatable("command.village-quest.setquest.option.wheat");
            case POTATO_HARVEST -> Component.translatable("command.village-quest.setquest.option.potato");
            case WOODCUTTING -> Component.translatable("command.village-quest.setquest.option.wood");
            case COAL_MINING -> Component.translatable("command.village-quest.setquest.option.coal");
            case WOOL_WEAVING -> Component.translatable("command.village-quest.setquest.option.wool");
            case RIVER_MEAL -> Component.translatable("command.village-quest.setquest.option.river");
            case AUTUMN_HARVEST -> Component.translatable("command.village-quest.setquest.option.autumn");
            case SMITH_SMELTING -> Component.translatable("command.village-quest.setquest.option.smelt");
            case STALL_NEW_LIFE -> Component.translatable("command.village-quest.setquest.option.stall");
            case VILLAGE_TRADING -> Component.translatable("command.village-quest.setquest.option.trade");
            case MARKET_ROUNDS -> Component.translatable("command.village-quest.setquest.option.market_rounds");
            case ZOMBIE_CULL -> Component.translatable("command.village-quest.setquest.option.zombie");
            case SKELETON_PATROL -> Component.translatable("command.village-quest.setquest.option.skeleton");
            case SPIDER_SWEEP -> Component.translatable("command.village-quest.setquest.option.spider");
            case CREEPER_WATCH -> Component.translatable("command.village-quest.setquest.option.creeper");
        };
    }

    private static void resetProgressFor(PlayerQuestData data) {
        clearQuestProgress(data);
        data.setAcceptedDay(PlayerQuestData.UNSET_DAY);
    }

    public static DailyQuestDifficulty difficulty(DailyQuestType type) {
        if (type == null) {
            return DailyQuestDifficulty.STANDARD;
        }
        return switch (type) {
            case HONEY, PET_COLLAR, WHEAT_HARVEST, POTATO_HARVEST, STALL_NEW_LIFE -> DailyQuestDifficulty.EASY;
            case WOODCUTTING, WOOL_WEAVING, RIVER_MEAL, AUTUMN_HARVEST, MARKET_ROUNDS, ZOMBIE_CULL, SPIDER_SWEEP -> DailyQuestDifficulty.STANDARD;
            case COAL_MINING, SMITH_SMELTING, VILLAGE_TRADING, SKELETON_PATROL, CREEPER_WATCH -> DailyQuestDifficulty.HARD;
        };
    }

    public static String alias(DailyQuestType quest) {
        if (quest == null) {
            return "-";
        }
        return switch (quest) {
            case HONEY -> "honey";
            case PET_COLLAR -> "pet";
            case WHEAT_HARVEST -> "bakery";
            case POTATO_HARVEST -> "kitchen";
            case WOODCUTTING -> "workshop";
            case COAL_MINING -> "smith";
            case WOOL_WEAVING -> "wool";
            case RIVER_MEAL -> "river";
            case AUTUMN_HARVEST -> "harvest";
            case SMITH_SMELTING -> "smelt";
            case STALL_NEW_LIFE -> "stall";
            case VILLAGE_TRADING -> "trade";
            case MARKET_ROUNDS -> "market_rounds";
            case ZOMBIE_CULL -> "zombie";
            case SKELETON_PATROL -> "skeleton";
            case SPIDER_SWEEP -> "spider";
            case CREEPER_WATCH -> "creeper";
        };
    }

    public static String categoryId(DailyQuestCategory category) {
        if (category == null) {
            return "none";
        }
        return switch (category) {
            case COOKING -> "cooking";
            case FARM -> "farm";
            case ANIMALS -> "animals";
            case CRAFTING -> "crafting";
            case VILLAGE -> "village";
            case COMBAT -> "combat";
        };
    }

    public static String difficultyId(DailyQuestDifficulty difficulty) {
        if (difficulty == null) {
            return "none";
        }
        return switch (difficulty) {
            case EASY -> "easy";
            case STANDARD -> "standard";
            case HARD -> "hard";
        };
    }

    public static String adminStateId(DailyAdminState state) {
        if (state == null) {
            return "not_generated";
        }
        return switch (state) {
            case NOT_GENERATED -> "not_generated";
            case OFFER_AVAILABLE -> "offer_available";
            case OFFER_PENDING -> "offer_pending";
            case ACTIVE -> "active";
            case COMPLETED -> "completed";
            case NEXTDAY_PREPARED -> "nextday_prepared";
        };
    }

    public static DailyQuestCompletion buildCompletion(DailyQuestType type,
                                                       Component title,
                                                       Component completionLine1,
                                                       Component completionLine2,
                                                       Component completionLine3,
                                                       ItemStack rewardB,
                                                       ItemStack rewardC) {
        DailyQuestRewardProfile profile = rewardProfile(type);
        return new DailyQuestCompletion(
                title,
                completionLine1,
                completionLine2,
                completionLine3,
                profile.currencyReward(),
                rewardB,
                rewardC,
                profile.levels()
        );
    }

    private static DailyQuestRewardProfile rewardProfile(DailyQuestType type) {
        return switch (difficulty(type)) {
            case EASY -> new DailyQuestRewardProfile(CurrencyService.SILVERMARK * 2L, 2);
            case STANDARD -> new DailyQuestRewardProfile(CurrencyService.SILVERMARK * 5L, 4);
            case HARD -> new DailyQuestRewardProfile(CurrencyService.CROWN, 6);
        };
    }

    public static long rewardCurrency(DailyQuestType type) {
        return rewardProfile(type).currencyReward();
    }

    public static int rewardLevels(DailyQuestType type) {
        return rewardProfile(type).levels();
    }

    public static boolean setQuestChoiceForToday(ServerLevel world, UUID playerId, DailyQuestType quest) {
        if (quest == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        markDailyDiscovered(world, playerId);
        long day = currentDay();
        data.setDailyChoice(quest);
        data.setDailyChoiceDay(day);
        resetProgressFor(data);
        data.setBonusRewardDay(PlayerQuestData.UNSET_DAY);
        data.setBonusAcceptedDay(PlayerQuestData.UNSET_DAY);
        data.setBonusChoice(null);
        data.setBonusChoiceDay(PlayerQuestData.UNSET_DAY);
        clearPendingOffers(data);
        markDirty(world);
        return true;
    }

    public static boolean adminResetDailyState(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return false;
        }

        PlayerQuestData data = data(world, playerId);
        boolean changed = false;
        long day = currentDay();

        if (!data.getDailyIntState().isEmpty() || !data.getDailyFlags().isEmpty()) {
            data.clearDailyProgress();
            changed = true;
        }
        if (data.getProgressDay() != PlayerQuestData.UNSET_DAY) {
            data.setProgressDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getAcceptedDay() == day) {
            data.setAcceptedDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getDailyChoiceDay() == day || data.getDailyChoice() != null) {
            data.setDailyChoice(null);
            data.setDailyChoiceDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getLastRewardDay() == day) {
            data.setLastRewardDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getBonusRewardDay() == day) {
            data.setBonusRewardDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getBonusAcceptedDay() == day) {
            data.setBonusAcceptedDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getBonusChoiceDay() == day || data.getBonusChoice() != null) {
            data.setBonusChoice(null);
            data.setBonusChoiceDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.isPendingDailyOffer() || data.isPendingShardOffer() || data.isPendingBonusOffer()) {
            clearPendingOffers(data);
            changed = true;
        }

        if (changed) {
            markDirty(world);
        }
        return changed;
    }

    public static DailyQuestType adminPrepareNextDaily(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return null;
        }

        PlayerQuestData data = data(world, playerId);
        DailyQuestType previousChoice = ensureQuestChoice(world, playerId, data);
        long day = currentDay();
        boolean changed = false;

        if (!data.getDailyIntState().isEmpty() || !data.getDailyFlags().isEmpty()) {
            data.clearDailyProgress();
            changed = true;
        }
        if (data.getProgressDay() != PlayerQuestData.UNSET_DAY) {
            data.setProgressDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getAcceptedDay() == day) {
            data.setAcceptedDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getLastRewardDay() == day) {
            data.setLastRewardDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getBonusRewardDay() == day) {
            data.setBonusRewardDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getBonusAcceptedDay() == day) {
            data.setBonusAcceptedDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.getBonusChoiceDay() == day || data.getBonusChoice() != null) {
            data.setBonusChoice(null);
            data.setBonusChoiceDay(PlayerQuestData.UNSET_DAY);
            changed = true;
        }
        if (data.isPendingDailyOffer() || data.isPendingShardOffer() || data.isPendingBonusOffer()) {
            clearPendingOffers(data);
            changed = true;
        }

        long simulatedPreviousDay = day - 1;
        if (data.getDailyChoiceDay() != simulatedPreviousDay) {
            data.setDailyChoiceDay(simulatedPreviousDay);
            changed = true;
        }

        if (changed) {
            markDirty(world);
        }
        return previousChoice;
    }

    public static DailyQuestType questFromString(String s) {
        if (s == null) {
            return null;
        }
        String norm = s.trim().toLowerCase();
        return switch (norm) {
            case "honey" -> DailyQuestType.HONEY;
            case "pet" -> DailyQuestType.PET_COLLAR;
            case "bakery" -> DailyQuestType.WHEAT_HARVEST;
            case "kitchen" -> DailyQuestType.POTATO_HARVEST;
            case "workshop" -> DailyQuestType.WOODCUTTING;
            case "smith" -> DailyQuestType.COAL_MINING;
            case "wool" -> DailyQuestType.WOOL_WEAVING;
            case "river" -> DailyQuestType.RIVER_MEAL;
            case "harvest" -> DailyQuestType.AUTUMN_HARVEST;
            case "smelt" -> DailyQuestType.SMITH_SMELTING;
            case "stall" -> DailyQuestType.STALL_NEW_LIFE;
            case "trade" -> DailyQuestType.VILLAGE_TRADING;
            case "market_rounds", "rounds" -> DailyQuestType.MARKET_ROUNDS;
            case "zombie" -> DailyQuestType.ZOMBIE_CULL;
            case "skeleton" -> DailyQuestType.SKELETON_PATROL;
            case "spider" -> DailyQuestType.SPIDER_SWEEP;
            case "creeper" -> DailyQuestType.CREEPER_WATCH;
            default -> null;
        };
    }

    public static QuestStatus openQuestStatus(ServerLevel world, UUID playerId) {
        DailyQuestType quest = activeQuestChoice(world, playerId);
        if (quest == null) {
            return null;
        }
        DailyQuestDefinition definition = definitionFor(quest);
        Component title = definition == null ? fallbackQuestTitle(quest) : definition.title();
        Component progress = definition == null ? Component.empty() : definition.progressLine(world, playerId);
        return new QuestStatus(title, progress);
    }

    public static int getDailyQuestCount() {
        return DailyQuestGenerator.count();
    }

    public static boolean isDailyActive(ServerLevel world, UUID playerId) {
        return activeQuestSlot(world, playerId) != null;
    }

    public static boolean isDailyCompleted(ServerLevel world, UUID playerId) {
        return hasCompletedToday(world, playerId) || hasCompletedBonusToday(world, playerId);
    }

    public static DailyQuestType activeQuestType(ServerLevel world, UUID playerId) {
        return activeQuestChoice(world, playerId);
    }

    public static DailyQuestType previewQuestChoice(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        return ensureQuestChoice(world, playerId);
    }

    public static DailyAdminInfo adminInfo(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return new DailyAdminInfo(null, DailyAdminState.NOT_GENERATED, false, false, PlayerQuestData.UNSET_DAY);
        }

        PlayerQuestData data = data(world, playerId);
        long day = currentDay();
        DailyQuestType choice = data.getDailyChoice();
        long choiceDay = data.getDailyChoiceDay();
        DailyAdminState state = resolveAdminState(data, day);
        return new DailyAdminInfo(choice, state, data.isPendingDailyOffer(), data.isDailyDiscovered(), choiceDay);
    }

    public static boolean canShowDailyOffer(ServerLevel world, UUID playerId) {
        return playerId != null
                && !hasCompletedToday(world, playerId)
                && !isAcceptedToday(world, playerId);
    }

    public static DailyQuestType adminShowDailyOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !canShowDailyOffer(world, player.getUUID())) {
            return null;
        }
        showQuestOfferCompact(world, player);
        return adminInfo(world, player.getUUID()).quest();
    }

    public static void markDailyDiscovered(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        data.setDailyDiscovered(true);
        markDirty(world);
    }

    public static boolean hasDiscoveredDaily(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return data(world, playerId).isDailyDiscovered();
    }

    public static boolean hasPendingDailyOffer(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        return data(world, playerId).isPendingDailyOffer();
    }

    public static boolean hasPendingQuestMasterOffer(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return data.isPendingDailyOffer()
                || data.isPendingShardOffer()
                || data.isPendingBonusOffer()
                || SpecialQuestService.hasPendingOffer(world, playerId);
    }

    public static void handleQuestMasterInteraction(ServerLevel world, ServerPlayer player) {
        handleQuestMasterInteraction(world, player, false);
    }

    public static void handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipSpecialOffer) {
        if (world == null || player == null) {
            return;
        }

        if (SpecialQuestService.handleQuestMasterInteraction(world, player, skipSpecialOffer)) {
            return;
        }

        UUID playerId = player.getUUID();
        if (isBonusAcceptedToday(world, playerId) && !hasCompletedBonusToday(world, playerId)) {
            boolean done = completeIfEligible(world, player);
            if (done) {
                return;
            }

            QuestStatus status = openQuestStatus(world, playerId);
            if (status != null) {
                player.sendSystemMessage(Texts.dailyTitle(status.title(), ChatFormatting.LIGHT_PURPLE), false);
                player.sendSystemMessage(status.progressLine(), false);
            }
            return;
        }

        if (hasCompletedToday(world, playerId)) {
            PlayerQuestData data = data(world, playerId);
            if (hasCompletedBonusToday(world, playerId)) {
                player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.completed_all_today"), false);
                return;
            }
            if (hasBonusChoiceToday(data)) {
                showBonusQuestOfferCompact(world, player);
                return;
            }
            if (canOfferShardPrompt(world, player)) {
                showShardPrompt(world, player);
                return;
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.completed_today"), false);
            return;
        }

        if (!isAcceptedToday(world, playerId)) {
            showQuestOfferCompact(world, player);
            return;
        }

        boolean done = completeIfEligible(world, player);
        if (done) {
            return;
        }

        QuestStatus status = openQuestStatus(world, playerId);
        if (status != null) {
            player.sendSystemMessage(Texts.dailyTitle(status.title(), ChatFormatting.GREEN), false);
            player.sendSystemMessage(status.progressLine(), false);
        }
    }

    public static void showQuestOfferCompact(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        markDailyDiscovered(world, playerId);
        if (hasCompletedToday(world, playerId) || isAcceptedToday(world, playerId)) {
            return;
        }

        PlayerQuestData data = data(world, playerId);
        clearPendingOffers(data);
        data.setPendingDailyOffer(true);
        markDirty(world);

        DailyQuestType quest = ensureQuestChoice(world, playerId, data);
        DailyQuestDefinition definition = definitionFor(quest);
        Component questTitle = definition == null ? fallbackQuestTitle(quest) : definition.title();
        Component paragraph1 = definition == null ? Component.empty() : definition.offerParagraph1();
        Component paragraph2 = definition == null ? Component.empty() : definition.offerParagraph2();
        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component accept = Component.translatable("text.village-quest.daily.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/vq daily accept")));

        Component questBody = Component.empty()
                .append(divider.copy())
                .append(Component.literal("\n"))
                .append(Texts.dailyTitle(questTitle, ChatFormatting.GREEN))
                .append(Component.literal("\n\n"))
                .append(paragraph1)
                .append(Component.literal("\n\n"))
                .append(paragraph2)
                .append(Component.literal("\n\n\n"))
                .append(accept)
                .append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(questBody, false);
    }

    private static void showShardPrompt(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        clearPendingOffers(data);
        data.setPendingShardOffer(true);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component title = Component.translatable("text.village-quest.daily.shard.title").withStyle(ChatFormatting.LIGHT_PURPLE);
        Component paragraph1 = Component.translatable("text.village-quest.daily.shard.body.1");
        Component paragraph2 = Component.translatable("text.village-quest.daily.shard.body.2");
        Component accept = Component.translatable("text.village-quest.daily.shard.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/vq daily accept")));

        Component body = Component.empty()
                .append(divider.copy())
                .append(Component.literal("\n"))
                .append(title)
                .append(Component.literal("\n\n"))
                .append(paragraph1)
                .append(Component.literal("\n\n"))
                .append(paragraph2)
                .append(Component.literal("\n\n\n"))
                .append(accept)
                .append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
    }

    private static void showBonusQuestOfferCompact(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        if (!hasBonusChoiceToday(data) || hasCompletedBonusToday(world, playerId) || isBonusAcceptedToday(world, playerId)) {
            return;
        }

        clearPendingOffers(data);
        data.setPendingBonusOffer(true);
        markDirty(world);

        DailyQuestDefinition definition = definitionFor(data.getBonusChoice());
        Component questTitle = definition == null ? fallbackQuestTitle(data.getBonusChoice()) : definition.title();
        Component paragraph1 = definition == null ? Component.empty() : definition.offerParagraph1();
        Component paragraph2 = definition == null ? Component.empty() : definition.offerParagraph2();
        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component prefix = Component.translatable("text.village-quest.daily.shard.quest_prefix").withStyle(ChatFormatting.LIGHT_PURPLE);
        Component accept = Component.translatable("text.village-quest.daily.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/vq daily accept")));

        Component questBody = Component.empty()
                .append(divider.copy())
                .append(Component.literal("\n"))
                .append(prefix)
                .append(Component.literal("\n"))
                .append(Texts.dailyTitle(questTitle, ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("\n\n"))
                .append(paragraph1)
                .append(Component.literal("\n\n"))
                .append(paragraph2)
                .append(Component.literal("\n\n\n"))
                .append(accept)
                .append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(questBody, false);
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            PlayerQuestData data = data(world, playerId);
            ensureCurrentProgressDay(data);

            DailyQuestDefinition definition = activeDefinition(world, playerId);
            if (definition != null) {
                definition.onServerTick(world, player);
            }
        }
    }

    public static void sendCurrentProgressActionbar(ServerLevel world, ServerPlayer player) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition == null) {
            return;
        }
        player.sendSystemMessage(definition.progressLine(world, player.getUUID()), true);
    }

    public static void onBeeNestInteract(ServerLevel world, ServerPlayer player, net.minecraft.world.level.block.state.BlockState state, ItemStack inHand) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onBeeNestInteract(world, player, state, inHand);
        }
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onBlockBreak(world, player, pos, state);
        }
        WeeklyQuestService.onBlockBreak(world, player, pos, state);
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, net.minecraft.world.entity.Entity entity, ItemStack inHand) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onEntityUse(world, player, entity, inHand);
        }
        WeeklyQuestService.onEntityUse(world, player, entity, inHand);
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onTrackedItemPickup(world, player, stack, count);
        }
        WeeklyQuestService.onTrackedItemPickup(world, player, stack, count);
        StoryQuestService.onTrackedItemPickup(world, player, stack, count);
        SpecialQuestService.onTrackedItemPickup(world, player, stack, count);
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onFurnaceOutput(world, player, stack);
        }
        StoryQuestService.onFurnaceOutput(world, player, stack);
        WeeklyQuestService.onFurnaceOutput(world, player, stack);
        SpecialQuestService.onFurnaceOutput(world, player, stack);
        PilgrimContractService.onFurnaceOutput(world, player, stack);
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onVillagerTrade(world, player, stack);
        }
        StoryQuestService.onVillagerTrade(world, player, stack);
        WeeklyQuestService.onVillagerTrade(world, player, stack);
        SpecialQuestService.onVillagerTrade(world, player, stack);
        PilgrimContractService.onVillagerTrade(world, player, stack);
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onAnimalLove(world, player, animal);
        }
        StoryQuestService.onAnimalLove(world, player, animal);
        WeeklyQuestService.onAnimalLove(world, player, animal);
        SpecialQuestService.onAnimalLove(world, player, animal);
        PilgrimContractService.onAnimalLove(world, player, animal);
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        DailyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onMonsterKill(world, player, killedEntity);
        }
        WeeklyQuestService.onMonsterKill(world, player, killedEntity);
    }

    public static boolean completeIfEligible(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        boolean storyWasUnlocked = QuestMasterProgressionService.isStoryCategoryUnlocked(world, playerId);
        ActiveQuestSlot slot = activeQuestSlot(world, playerId);
        if (slot == null) {
            return false;
        }

        DailyQuestDefinition definition = activeDefinition(world, playerId);
        if (definition == null) {
            return false;
        }
        if (!definition.isComplete(world, player)) {
            Component blocked = definition.claimBlockedMessage(world, player);
            if (blocked != null) {
                player.sendSystemMessage(blocked, false);
            }
            return false;
        }
        if (!definition.consumeCompletionRequirements(world, player)) {
            return false;
        }

        DailyQuestType questType = activeQuestChoice(world, playerId);
        DailyQuestCompletion completion = definition.buildCompletion(world);
        boolean allowMagicShardDrop = slot == ActiveQuestSlot.NORMAL;
        deliverCompletion(world, player, questType, completion, allowMagicShardDrop);
        if (slot == ActiveQuestSlot.BONUS) {
            markBonusCompletedToday(world, playerId);
        } else {
            markCompletedToday(world, playerId);
            QuestMasterProgressionService.onNormalDailyCompleted(world, player, storyWasUnlocked);
        }
        refreshQuestUi(world, playerId);
        return true;
    }

    public static boolean adminForceCompleteDaily(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        boolean storyWasUnlocked = QuestMasterProgressionService.isStoryCategoryUnlocked(world, playerId);
        ActiveQuestSlot slot = activeQuestSlot(world, playerId);
        if (slot == null) {
            return false;
        }

        DailyQuestDefinition definition = activeDefinition(world, playerId);
        if (definition == null) {
            return false;
        }

        DailyQuestType questType = activeQuestChoice(world, playerId);
        boolean allowMagicShardDrop = slot == ActiveQuestSlot.NORMAL;
        deliverCompletion(world, player, questType, definition.buildCompletion(world), allowMagicShardDrop);
        if (slot == ActiveQuestSlot.BONUS) {
            markBonusCompletedToday(world, playerId);
        } else {
            markCompletedToday(world, playerId);
            QuestMasterProgressionService.onNormalDailyCompleted(world, player, storyWasUnlocked);
        }
        refreshQuestUi(world, playerId);
        return true;
    }

    private static void appendRewardLine(net.minecraft.network.chat.MutableComponent body, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        body.append(Component.empty().append(Component.literal("    ")).append(formatRewardLine(stack)))
                .append(Component.literal("\n"));
    }

    private static void appendCurrencyRewardLine(net.minecraft.network.chat.MutableComponent body, long amount) {
        if (amount <= 0L) {
            return;
        }
        body.append(Component.empty()
                        .append(Component.literal("    "))
                        .append(CurrencyService.formatDelta(amount)))
                .append(Component.literal("\n"));
    }

    private static void appendTextRewardLine(net.minecraft.network.chat.MutableComponent body, Component line) {
        if (line == null || line.getString().isEmpty()) {
            return;
        }
        body.append(Component.empty()
                        .append(Component.literal("    "))
                        .append(line))
                .append(Component.literal("\n"));
    }

    private static void deliverCompletion(ServerLevel world,
                                          ServerPlayer player,
                                          DailyQuestType questType,
                                          DailyQuestCompletion completion,
                                          boolean allowMagicShardDrop) {
        int shardCountBefore = ModItems.MAGIC_SHARD == null ? 0 : countInventoryItem(player, ModItems.MAGIC_SHARD);
        ItemStack bonusReward = allowMagicShardDrop ? maybeRollMagicShardReward(world, player.getUUID()) : ItemStack.EMPTY;
        ReputationService.ReputationReward reputationReward = questType == null ? null : ReputationService.rewardFor(questType);
        ReputationService.ReputationTrack track = reputationReward == null ? null : reputationReward.track();
        long actualCurrencyReward = completion.currencyReward() + VillageProjectService.bonusCurrency(world, player.getUUID(), track);
        if (actualCurrencyReward > 0L) {
            CurrencyService.addBalance(world, player.getUUID(), actualCurrencyReward);
        }
        int actualReputationReward = 0;
        if (reputationReward != null && reputationReward.amount() > 0) {
            actualReputationReward = VillageProjectService.applyReputationReward(world, player.getUUID(), reputationReward.track(), reputationReward.amount());
        }
        giveReward(player, completion.rewardB());
        giveReward(player, completion.rewardC());
        giveReward(player, bonusReward);
        int shardCountAfter = ModItems.MAGIC_SHARD == null ? 0 : countInventoryItem(player, ModItems.MAGIC_SHARD);
        QuestMasterProgressionService.onMagicShardCountChanged(world, player, shardCountBefore, shardCountAfter);
        int actualLevelReward = completion.levels() + VillageProjectService.bonusLevels(world, player.getUUID(), track);
        if (actualLevelReward > 0) {
            player.giveExperienceLevels(actualLevelReward);
        }

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component rewardsTitle = Component.translatable("text.village-quest.daily.rewards").withStyle(ChatFormatting.GRAY);
        Component levelLine = Component.empty().append(Component.literal("    "))
                .append(Component.translatable("text.village-quest.daily.level_reward", actualLevelReward).withStyle(ChatFormatting.GREEN));

        net.minecraft.network.chat.MutableComponent rewardBody = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(completion.title(), ChatFormatting.GREEN))
                .append(Component.literal("\n\n"))
                .append(completion.completionLine1()).append(Component.literal("\n"))
                .append(completion.completionLine2()).append(Component.literal("\n\n"))
                .append(completion.completionLine3()).append(Component.literal("\n\n"))
                .append(rewardsTitle).append(Component.literal(":\n\n"));

        appendCurrencyRewardLine(rewardBody, actualCurrencyReward);
        if (reputationReward != null && actualReputationReward > 0) {
            appendTextRewardLine(rewardBody, ReputationService.formatRewardLine(reputationReward.track(), actualReputationReward));
        }
        appendTextRewardLine(rewardBody, VillageProjectService.formatBonusRewardLine(world, player.getUUID(), track));
        appendTextRewardLine(rewardBody, VillageProjectService.formatRewardEchoLine(world, player.getUUID(), track));
        appendRewardLine(rewardBody, completion.rewardB());
        appendRewardLine(rewardBody, completion.rewardC());
        appendRewardLine(rewardBody, bonusReward);
        rewardBody.append(levelLine).append(Component.literal("\n"))
                .append(divider.copy());

        player.sendSystemMessage(rewardBody, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    private static ItemStack maybeRollMagicShardReward(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null || ModItems.MAGIC_SHARD == null) {
            return ItemStack.EMPTY;
        }

        PlayerQuestData data = data(world, playerId);
        if (!data.isStarterShardGranted()) {
            data.setStarterShardGranted(true);
            markDirty(world);
            if (!hasHistoricalQuestCompletionEvidence(data)) {
                return new ItemStack(ModItems.MAGIC_SHARD);
            }
        }

        if (world.getRandom().nextFloat() >= MAGIC_SHARD_DROP_CHANCE) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ModItems.MAGIC_SHARD);
    }

    private static boolean hasHistoricalQuestCompletionEvidence(PlayerQuestData data) {
        if (data == null) {
            return false;
        }
        return data.getLastRewardDay() != PlayerQuestData.UNSET_DAY
                || data.getBonusRewardDay() != PlayerQuestData.UNSET_DAY
                || !data.getReputationState().isEmpty()
                || data.getShardRelicQuestStage() != ShardRelicQuestStage.NONE
                || data.getMerchantSealQuestStage() != RelicQuestStage.NONE
                || data.getShepherdFluteQuestStage() != RelicQuestStage.NONE
                || data.getApiaristSmokerQuestStage() != RelicQuestStage.NONE
                || data.getSurveyorCompassQuestStage() != RelicQuestStage.NONE;
    }

    private static DailyAdminState resolveAdminState(PlayerQuestData data, long day) {
        if (data.getLastRewardDay() == day) {
            return DailyAdminState.COMPLETED;
        }
        if (data.getAcceptedDay() == day) {
            return DailyAdminState.ACTIVE;
        }

        DailyQuestType choice = data.getDailyChoice();
        long choiceDay = data.getDailyChoiceDay();
        if (choice == null) {
            return DailyAdminState.NOT_GENERATED;
        }
        if (choiceDay == day) {
            return data.isPendingDailyOffer()
                    ? DailyAdminState.OFFER_PENDING
                    : DailyAdminState.OFFER_AVAILABLE;
        }
        if (choiceDay == day - 1) {
            return DailyAdminState.NEXTDAY_PREPARED;
        }
        return DailyAdminState.NOT_GENERATED;
    }

    private static void giveReward(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            giveOrDrop(player, stack);
        }
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

    public static int countInventoryItems(Player player, Item... items) {
        if (player == null || items == null || items.length == 0) {
            return 0;
        }

        int total = 0;
        for (Item item : items) {
            total += countInventoryItem(player, item);
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
            serverPlayer.containerMenu.broadcastChanges();
        }
        return remaining <= 0;
    }

    public static boolean consumeInventoryItems(Player player, int amount, Item... items) {
        if (player == null || amount <= 0 || items == null || items.length == 0) {
            return false;
        }
        if (countInventoryItems(player, items) < amount) {
            return false;
        }

        int remaining = amount;
        for (Item item : items) {
            if (remaining <= 0) {
                break;
            }
            int available = countInventoryItem(player, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!consumeInventoryItem(player, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    public static int getCraftedStat(ServerPlayer player, Item item) {
        return player.getStats().getValue(Stats.ITEM_CRAFTED, item);
    }

    public static int getPickedUpStat(ServerPlayer player, Item item) {
        return player.getStats().getValue(Stats.ITEM_PICKED_UP, item);
    }

    public static int getCustomStat(ServerPlayer player, Identifier stat) {
        return player.getStats().getValue(Stats.CUSTOM, stat);
    }

    public static int sumPickedUpStats(ServerPlayer player, Item... items) {
        int total = 0;
        for (Item item : items) {
            total += getPickedUpStat(player, item);
        }
        return total;
    }

    private static Component formatRewardLine(ItemStack stack) {
        var hover = stack.getDisplayName().getStyle().getHoverEvent();
        net.minecraft.network.chat.MutableComponent base = stack.getHoverName().copy();
        if (hover != null) {
            base = base.withStyle(style -> style.withHoverEvent(hover));
        }
        if (stack.getCount() > 1) {
            base = Component.empty().append(base)
                    .append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GRAY));
        }
        return base;
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

    public static ItemStack createCompanionPainting(ServerLevel world, boolean cat) {
        return PaintingStackFactory.create(world, cat ? "eure_majestaet" : "treuer_begleiter");
    }

    public static int honeyTarget() {
        return HONEY_TARGET;
    }

    public static int combTarget() {
        return COMB_TARGET;
    }

    public static int wheatTarget() {
        return WHEAT_TARGET;
    }

    public static int breadTarget() {
        return BREAD_TARGET;
    }

    public static int potatoTarget() {
        return POTATO_TARGET;
    }

    public static int carrotTarget() {
        return CARROT_TARGET;
    }

    public static int woodTarget() {
        return WOOD_TARGET;
    }

    public static int coalTarget() {
        return COAL_TARGET;
    }

    public static int ironTarget() {
        return IRON_TARGET;
    }

    public static int smithCoalTarget() {
        return SMITH_COAL_TARGET;
    }

    public static int sheepTarget() {
        return SHEEP_TARGET;
    }

    public static int woolTarget() {
        return WOOL_TARGET;
    }

    public static int riverFishTarget() {
        return RIVER_FISH_TARGET;
    }

    public static int riverCookedFishTarget() {
        return RIVER_COOKED_FISH_TARGET;
    }

    public static int autumnPumpkinTarget() {
        return AUTUMN_PUMPKIN_TARGET;
    }

    public static int autumnMelonTarget() {
        return AUTUMN_MELON_TARGET;
    }

    public static int smithSmeltOreTarget() {
        return SMITH_SMELT_ORE_TARGET;
    }

    public static int smithSmeltIngotTarget() {
        return SMITH_SMELT_INGOT_TARGET;
    }

    public static int stallBreedTarget() {
        return STALL_BREED_TARGET;
    }

    public static int villageTradeTarget() {
        return VILLAGE_TRADE_TARGET;
    }

    public static int villageTradeEmeraldTarget() {
        return VILLAGE_TRADE_EMERALD_TARGET;
    }

    private record DailyQuestRewardProfile(long currencyReward, int levels) {}

    public record DailyAdminInfo(DailyQuestType quest,
                                 DailyAdminState state,
                                 boolean pendingOffer,
                                 boolean discovered,
                                 long choiceDay) {}

    public record QuestStatus(Component title, Component progressLine) {}
}
