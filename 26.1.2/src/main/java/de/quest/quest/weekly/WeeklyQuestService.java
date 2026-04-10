package de.quest.quest.weekly;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.UUID;

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
    private static final int SMITH_IRON_TARGET = 50;
    private static final int SMITH_GOLD_ORE_TARGET = 20;
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

    private WeeklyQuestService() {}

    private static long currentCycle() {
        return TimeUtil.currentWeekCycle();
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerLevel world) {
        if (world != null) {
            QuestState.get(world.getServer()).setDirty();
        }
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
            data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
            data.setWeeklyRewardCycle(PlayerQuestData.UNSET_DAY);
            clearProgress(data);
            data.markWeeklyDiscovered(definition.type().name());
            currentChoice = definition.type();
            changed = true;
        }

        if (changed) {
            markDirty(world);
        }
        return currentChoice;
    }

    public static int getQuestInt(ServerLevel world, UUID playerId, String key) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        return data.getWeeklyInt(key);
    }

    public static void setQuestInt(ServerLevel world, UUID playerId, String key, int value) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        if (data.getWeeklyInt(key) == value) {
            return;
        }
        data.setWeeklyInt(key, value);
        data.setWeeklyProgressCycle(currentCycle());
        markDirty(world);
        refreshQuestUi(world, playerId);
    }

    public static void addQuestInt(ServerLevel world, UUID playerId, String key, int amount) {
        if (amount == 0) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        data.addWeeklyInt(key, amount);
        data.setWeeklyProgressCycle(currentCycle());
        markDirty(world);
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
        return data.hasWeeklyFlag(key);
    }

    public static void setQuestFlag(ServerLevel world, UUID playerId, String key, boolean enabled) {
        PlayerQuestData data = data(world, playerId);
        ensureCurrentProgressCycle(data);
        if (data.hasWeeklyFlag(key) == enabled) {
            return;
        }
        data.setWeeklyFlag(key, enabled);
        data.setWeeklyProgressCycle(currentCycle());
        markDirty(world);
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
        markDirty(world);
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
        return new WeeklyQuestStatus(definition.title(), List.copyOf(definition.progressLines(world, playerId)));
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
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        if (definition == null) {
            return false;
        }

        PlayerQuestData data = data(world, playerId);
        clearProgress(data);
        data.setWeeklyAcceptedCycle(currentCycle());
        markDirty(world);
        definition.onAccepted(world, player);
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

        deliverCompletion(world, player, definition.buildCompletion());
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
        markDirty(world);
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

        deliverCompletion(world, player, definition.buildCompletion());
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
            changed = true;
        }
        if (changed) {
            markDirty(world);
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
        data.setWeeklyAcceptedCycle(PlayerQuestData.UNSET_DAY);
        data.setWeeklyRewardCycle(PlayerQuestData.UNSET_DAY);
        clearProgress(data);
        data.markWeeklyDiscovered(rerolled.type().name());
        markDirty(world);
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
                definition.onServerTick(world, player);
            }
        }
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onBlockBreak(world, player, pos, state);
        }
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onEntityUse(world, player, entity, inHand);
        }
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onTrackedItemPickup(world, player, stack, count);
        }
    }

    public static void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onFurnaceOutput(world, player, stack);
        }
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onVillagerTrade(world, player, stack);
        }
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onAnimalLove(world, player, animal);
        }
    }

    public static void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onPilgrimPurchase(world, player, offerId);
        }
        StoryQuestService.onPilgrimPurchase(world, player, offerId);
    }

    public static void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (world == null || player == null || killedEntity == null) {
            return;
        }
        WeeklyQuestDefinition definition = activeDefinition(world, player.getUUID());
        if (definition != null) {
            definition.onMonsterKill(world, player, killedEntity);
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
        return new WeeklyQuestCompletion(
                title,
                completionLine1,
                completionLine2,
                completionLine3,
                currencyReward,
                rewardB,
                rewardC,
                levels,
                reputationTrack,
                reputationAmount
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

    public static WeeklyQuestCompletion previewCompletion(WeeklyQuestType type) {
        WeeklyQuestDefinition definition = WeeklyQuestGenerator.definition(type);
        return definition == null ? null : definition.buildCompletion();
    }

    public static int harvestWheatTarget() { return HARVEST_WHEAT_TARGET; }
    public static int harvestCarrotTarget() { return HARVEST_CARROT_TARGET; }
    public static int harvestPotatoTarget() { return HARVEST_POTATO_TARGET; }
    public static int harvestBreadTarget() { return HARVEST_BREAD_TARGET; }
    public static int bakehouseBreadTarget() { return BAKEHOUSE_BREAD_TARGET; }
    public static int bakehousePieTarget() { return BAKEHOUSE_PIE_TARGET; }
    public static int bakehousePotatoTarget() { return BAKEHOUSE_POTATO_TARGET; }
    public static int smithOreTarget() { return SMITH_ORE_TARGET; }
    public static int smithIronTarget() { return SMITH_IRON_TARGET; }
    public static int smithGoldOreTarget() { return SMITH_GOLD_ORE_TARGET; }
    public static int smithGoldTarget() { return SMITH_GOLD_TARGET; }
    public static int pastureBreedTarget() { return PASTURE_BREED_TARGET; }
    public static int pastureShearTarget() { return PASTURE_SHEAR_TARGET; }
    public static int pastureWoolTarget() { return PASTURE_WOOL_TARGET; }
    public static int marketTradeTarget() { return MARKET_TRADE_TARGET; }
    public static int marketEmeraldTarget() { return MARKET_EMERALD_TARGET; }
    public static int marketPilgrimPurchaseTarget() { return MARKET_PILGRIM_PURCHASE_TARGET; }
    public static int nightWatchZombieTarget() { return NIGHTWATCH_ZOMBIE_TARGET; }
    public static int nightWatchSkeletonTarget() { return NIGHTWATCH_SKELETON_TARGET; }
    public static int roadWardenHostileTarget() { return ROADWARDEN_HOSTILE_TARGET; }
    public static int roadWardenCreeperTarget() { return ROADWARDEN_CREEPER_TARGET; }

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
            serverPlayer.containerMenu.broadcastChanges();
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
        Component base = stack.getHoverName().copy();
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
}
