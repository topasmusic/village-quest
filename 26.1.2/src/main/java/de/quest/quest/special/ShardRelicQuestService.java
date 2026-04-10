package de.quest.quest.special;

import de.quest.VillageQuest;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShardRelicQuestService {
    private static final int REQUIRED_SHARDS = 10;
    private static final int AMETHYST_TARGET = 16;
    private static final int POTION_TARGET = 3;
    private static final int ENCHANT_TARGET = 1;
    private static final int ENDER_PEARL_TARGET = 1;
    private static final int BLAZE_ROD_TARGET = 1;
    private static final int MIN_CACHE_DISTANCE = 900;
    private static final int MAX_CACHE_DISTANCE = 1900;
    private static final int MAX_CACHE_ATTEMPTS = 64;
    private static final int CACHE_RESOLVE_DISTANCE = 96;
    private static final int CACHE_SEARCH_RADIUS = 10;
    private static final Identifier STARREACH_REACH_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "starreach_ring_reach");
    private static final AttributeModifier STARREACH_REACH_MODIFIER =
            new AttributeModifier(STARREACH_REACH_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);

    private record TrialSnapshot(
            ShardRelicQuestStage stage,
            int amethyst,
            int potions,
            int enchants,
            int enderPearls,
            int blazeRods
    ) {}

    private ShardRelicQuestService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerLevel world) {
        if (world != null) {
            QuestState.get(world.getServer()).setDirty();
        }
    }

    private static void refreshQuestUi(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    private static TrialSnapshot captureTrialSnapshot(PlayerQuestData data) {
        if (data == null) {
            return new TrialSnapshot(ShardRelicQuestStage.NONE, 0, 0, 0, 0, 0);
        }
        return new TrialSnapshot(
                data.getShardRelicQuestStage(),
                data.getShardRelicAmethystProgress(),
                data.getShardRelicPotionProgress(),
                data.getShardRelicEnchantProgress(),
                data.getShardRelicEnderPearlProgress(),
                data.getShardRelicBlazeRodProgress()
        );
    }

    private static void sendTrialProgressFeedback(ServerLevel world, ServerPlayer player, TrialSnapshot before, PlayerQuestData after) {
        if (world == null || player == null || before == null || after == null) {
            return;
        }

        Component actionbar = null;
        boolean changed = false;
        boolean completedStep = false;

        if (before.amethyst() != after.getShardRelicAmethystProgress()) {
            changed = true;
            actionbar = Component.translatable(
                    "quest.village-quest.special.shards.progress.amethyst",
                    after.getShardRelicAmethystProgress(),
                    AMETHYST_TARGET
            ).withStyle(ChatFormatting.LIGHT_PURPLE);
            if (before.amethyst() < AMETHYST_TARGET && after.getShardRelicAmethystProgress() >= AMETHYST_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.potions() != after.getShardRelicPotionProgress()) {
            changed = true;
            actionbar = Component.translatable(
                    "quest.village-quest.special.shards.progress.potions",
                    after.getShardRelicPotionProgress(),
                    POTION_TARGET
            ).withStyle(ChatFormatting.LIGHT_PURPLE);
            if (before.potions() < POTION_TARGET && after.getShardRelicPotionProgress() >= POTION_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.enchants() != after.getShardRelicEnchantProgress()) {
            changed = true;
            actionbar = Component.translatable(
                    "quest.village-quest.special.shards.progress.enchant",
                    after.getShardRelicEnchantProgress(),
                    ENCHANT_TARGET
            ).withStyle(ChatFormatting.LIGHT_PURPLE);
            if (before.enchants() < ENCHANT_TARGET && after.getShardRelicEnchantProgress() >= ENCHANT_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.enderPearls() != after.getShardRelicEnderPearlProgress()) {
            changed = true;
            actionbar = Component.translatable(
                    "quest.village-quest.special.shards.progress.ender_pearl",
                    after.getShardRelicEnderPearlProgress(),
                    ENDER_PEARL_TARGET
            ).withStyle(ChatFormatting.LIGHT_PURPLE);
            if (before.enderPearls() < ENDER_PEARL_TARGET && after.getShardRelicEnderPearlProgress() >= ENDER_PEARL_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.blazeRods() != after.getShardRelicBlazeRodProgress()) {
            changed = true;
            actionbar = Component.translatable(
                    "quest.village-quest.special.shards.progress.blaze_rod",
                    after.getShardRelicBlazeRodProgress(),
                    BLAZE_ROD_TARGET
            ).withStyle(ChatFormatting.LIGHT_PURPLE);
            if (before.blazeRods() < BLAZE_ROD_TARGET && after.getShardRelicBlazeRodProgress() >= BLAZE_ROD_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (!changed) {
            return;
        }

        if (actionbar != null) {
            player.sendSystemMessage(actionbar, true);
        }
        float pitch = completedStep ? 1.7f : 1.4f;
        float volume = completedStep ? 0.28f : 0.18f;
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, volume, pitch);
        refreshQuestUi(world, player);
    }

    public static boolean hasPendingSpecialOffer(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.SHARD_RELIC;
    }

    public static boolean consumePendingSpecialOffer(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.getPendingSpecialOfferKind() != SpecialQuestKind.SHARD_RELIC) {
            return false;
        }
        data.setPendingSpecialOfferKind(null);
        markDirty(world);
        return true;
    }

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player) {
        return handleQuestMasterInteraction(world, player, false);
    }

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        updatePassiveProgress(world, player, data, false);

        return switch (data.getShardRelicQuestStage()) {
            case TRIAL_ACTIVE -> {
                showTrialProgress(player, data);
                yield true;
            }
            case TRIAL_READY -> {
                giveTreasureMap(world, player, data, true);
                yield true;
            }
            case CACHE_HUNT -> {
                if (hasStarreachRing(player)) {
                    completeQuest(world, player, data);
                    yield true;
                }
                ensureCacheChest(world, data);
                giveTreasureMap(world, player, data, false);
                yield true;
            }
            case COMPLETED -> false;
            case NONE -> {
                if (!skipOffer && shouldOfferSpecialQuest(world, player, data)) {
                    showSpecialOffer(world, player, data);
                    yield true;
                }
                yield false;
            }
        };
    }

    public static boolean acceptSpecialQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.MAGIC_SHARD == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferSpecialQuest(world, player, data)) {
            return false;
        }
        if (!consumeInventoryItem(player, ModItems.MAGIC_SHARD, REQUIRED_SHARDS)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.special.shards_missing").withStyle(ChatFormatting.RED), false);
            markDirty(world);
            return false;
        }

        startTrial(world, player, data);
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(title(), ChatFormatting.LIGHT_PURPLE), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showTrialProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyRingReachBonus(player);
            PlayerQuestData data = data(world, player.getUUID());
            updatePassiveProgress(world, player, data, true);
            if (data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT) {
                prepareCacheChestIfNearby(world, player, data);
            }
            if (data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT && hasStarreachRing(player)) {
                completeQuest(world, player, data);
            }
        }
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (world == null || player == null || state == null) {
            return;
        }
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (world == null || player == null || stack == null || count <= 0) {
            return;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE) {
            return;
        }

        TrialSnapshot before = captureTrialSnapshot(data);
        boolean changed = false;

        if (stack.is(Items.AMETHYST_SHARD)) {
            int next = Math.min(AMETHYST_TARGET, data.getShardRelicAmethystProgress() + count);
            if (next != data.getShardRelicAmethystProgress()) {
                data.setShardRelicAmethystProgress(next);
                changed = true;
            }
        } else if (stack.is(Items.ENDER_PEARL) && data.getShardRelicEnderPearlProgress() < ENDER_PEARL_TARGET) {
            data.setShardRelicEnderPearlProgress(ENDER_PEARL_TARGET);
            changed = true;
        } else if (stack.is(Items.BLAZE_ROD) && data.getShardRelicBlazeRodProgress() < BLAZE_ROD_TARGET) {
            data.setShardRelicBlazeRodProgress(BLAZE_ROD_TARGET);
            changed = true;
        }

        if (!changed) {
            return;
        }
        updateReadyState(world, player, data, true);
        markDirty(world);
        sendTrialProgressFeedback(world, player, before, data);
    }

    public static void onPotionBrewed(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel world)) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE) {
            return;
        }
        TrialSnapshot before = captureTrialSnapshot(data);
        int next = Math.min(POTION_TARGET, data.getShardRelicPotionProgress() + 1);
        if (next == data.getShardRelicPotionProgress()) {
            return;
        }
        data.setShardRelicPotionProgress(next);
        updateReadyState(world, player, data, true);
        markDirty(world);
        sendTrialProgressFeedback(world, player, before, data);
    }

    public static void onAnvilOutput(ServerPlayer player, ItemStack leftInput, ItemStack rightInput, ItemStack output) {
        if (player == null || !(player.level() instanceof ServerLevel world) || output == null || output.isEmpty()) {
            return;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE || data.getShardRelicEnchantProgress() >= ENCHANT_TARGET) {
            return;
        }
        if (!isAnvilEnchant(leftInput, rightInput, output)) {
            return;
        }

        TrialSnapshot before = captureTrialSnapshot(data);
        data.setShardRelicEnchantProgress(ENCHANT_TARGET);
        updateReadyState(world, player, data, true);
        markDirty(world);
        sendTrialProgressFeedback(world, player, before, data);
    }

    public static boolean allowBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return true;
        }
        Optional<UUID> owner = findCacheOwner(world, pos);
        if (owner.isEmpty()) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.special.cache_protected").withStyle(ChatFormatting.RED), false);
        return false;
    }

    public static net.minecraft.world.InteractionResult onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        Optional<UUID> owner = findCacheOwner(world, pos);
        if (owner.isEmpty()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!owner.get().equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.special.cache_locked").withStyle(ChatFormatting.RED), false);
            return net.minecraft.world.InteractionResult.FAIL;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    public static boolean hasPendingOffer(ServerLevel world, UUID playerId) {
        return hasPendingSpecialOffer(world, playerId);
    }

    public static boolean isActive(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        ShardRelicQuestStage stage = data(world, playerId).getShardRelicQuestStage();
        return stage == ShardRelicQuestStage.TRIAL_ACTIVE
                || stage == ShardRelicQuestStage.TRIAL_READY
                || stage == ShardRelicQuestStage.CACHE_HUNT;
    }

    public static boolean isCompleted(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return data.getPendingSpecialOfferKind() == SpecialQuestKind.SHARD_RELIC
                || data.getShardRelicQuestStage() != ShardRelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        return switch (data.getShardRelicQuestStage()) {
            case TRIAL_ACTIVE, TRIAL_READY -> new SpecialQuestStatus(title(), trialProgressLines(data));
            case CACHE_HUNT -> new SpecialQuestStatus(title(), List.of(
                    Component.translatable("quest.village-quest.special.shards.cache_line.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("quest.village-quest.special.shards.cache_line.2").withStyle(ChatFormatting.GRAY)
            ));
            default -> null;
        };
    }

    public static boolean adminStartCacheHunt(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(null);
        data.setShardRelicQuestStage(ShardRelicQuestStage.TRIAL_READY);
        data.setShardRelicAmethystProgress(AMETHYST_TARGET);
        data.setShardRelicPotionProgress(POTION_TARGET);
        data.setShardRelicEnchantProgress(ENCHANT_TARGET);
        data.setShardRelicEnderPearlProgress(ENDER_PEARL_TARGET);
        data.setShardRelicBlazeRodProgress(BLAZE_ROD_TARGET);
        data.setShardRelicChestX(0);
        data.setShardRelicChestY(Integer.MIN_VALUE);
        data.setShardRelicChestZ(0);
        markDirty(world);
        refreshQuestUi(world, player);
        return giveTreasureMap(world, player, data, true);
    }

    public static boolean adminTeleportToCache(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (!hasCacheTarget(data) && !adminStartCacheHunt(world, player)) {
            return false;
        }

        BlockPos chestPos = chestPos(data);
        if (chestPos == null) {
            BlockPos targetPos = new BlockPos(data.getShardRelicChestX(), world.getSeaLevel(), data.getShardRelicChestZ());
            chestPos = resolveCacheChestPos(world, targetPos, true);
            if (chestPos == null) {
                return false;
            }
            data.setShardRelicChestX(chestPos.getX());
            data.setShardRelicChestY(chestPos.getY());
            data.setShardRelicChestZ(chestPos.getZ());
            markDirty(world);
        }

        world.getChunk(chestPos.getX() >> 4, chestPos.getZ() >> 4);
        ensureCacheChest(world, data);
        player.teleportTo(
                world,
                chestPos.getX() + 0.5,
                chestPos.getY() + 2.0,
                chestPos.getZ() + 0.5,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                true
        );
        return true;
    }

    public static boolean claimFromQuestMaster(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        return switch (data.getShardRelicQuestStage()) {
            case TRIAL_READY -> giveTreasureMap(world, player, data, true);
            case CACHE_HUNT -> {
                if (hasStarreachRing(player)) {
                    completeQuest(world, player, data);
                    yield true;
                }
                ensureCacheChest(world, data);
                yield giveTreasureMap(world, player, data, false);
            }
            default -> false;
        };
    }

    private static boolean shouldOfferSpecialQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        return data.getShardRelicQuestStage() == ShardRelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.MAGIC_SHARD != null
                && DailyQuestService.countInventoryItem(player, ModItems.MAGIC_SHARD) >= REQUIRED_SHARDS
                && DailyQuestService.openQuestStatus(world, player.getUUID()) == null;
    }

    private static void showSpecialOffer(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        data.setPendingSpecialOfferKind(SpecialQuestKind.SHARD_RELIC);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component accept = Component.translatable("text.village-quest.special.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/vq daily accept")));

        Component body = Component.empty()
                .append(divider.copy())
                .append(Component.literal("\n"))
                .append(Texts.dailyTitle(title(), ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.shards.offer.1"))
                .append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.shards.offer.2", REQUIRED_SHARDS))
                .append(Component.literal("\n\n\n"))
                .append(accept)
                .append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
    }

    private static void startTrial(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        data.setShardRelicQuestStage(ShardRelicQuestStage.TRIAL_ACTIVE);
        data.setShardRelicAmethystProgress(0);
        data.setShardRelicPotionProgress(0);
        data.setShardRelicEnchantProgress(0);
        data.setShardRelicEnderPearlProgress(0);
        data.setShardRelicBlazeRodProgress(0);
        data.setShardRelicEnchantBaseline(DailyQuestService.getCustomStat(player, Stats.ENCHANT_ITEM));
        data.setShardRelicEnderPearlBaseline(0);
        data.setShardRelicBlazeRodBaseline(0);
        data.setShardRelicChestX(0);
        data.setShardRelicChestY(Integer.MIN_VALUE);
        data.setShardRelicChestZ(0);
        markDirty(world);
    }

    private static void updatePassiveProgress(ServerLevel world, ServerPlayer player, PlayerQuestData data, boolean sendReadyMessage) {
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE && data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_READY) {
            return;
        }

        TrialSnapshot before = captureTrialSnapshot(data);
        boolean changed = false;
        int enchantFromStat = Math.max(0, DailyQuestService.getCustomStat(player, Stats.ENCHANT_ITEM) - data.getShardRelicEnchantBaseline());
        int enchant = Math.min(ENCHANT_TARGET, Math.max(data.getShardRelicEnchantProgress(), enchantFromStat));
        if (enchant != data.getShardRelicEnchantProgress()) {
            data.setShardRelicEnchantProgress(enchant);
            changed = true;
        }

        if (updateReadyState(world, player, data, sendReadyMessage)) {
            changed = true;
        }

        if (changed) {
            markDirty(world);
            sendTrialProgressFeedback(world, player, before, data);
        }
    }

    private static boolean updateReadyState(ServerLevel world, ServerPlayer player, PlayerQuestData data, boolean sendReadyMessage) {
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE) {
            return false;
        }
        if (!isTrialComplete(data)) {
            return false;
        }
        data.setShardRelicQuestStage(ShardRelicQuestStage.TRIAL_READY);
        if (sendReadyMessage && player != null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.special.trial_ready").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            if (world != null) {
                world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.15f);
            }
        }
        return true;
    }

    private static boolean isTrialComplete(PlayerQuestData data) {
        return data.getShardRelicAmethystProgress() >= AMETHYST_TARGET
                && data.getShardRelicPotionProgress() >= POTION_TARGET
                && data.getShardRelicEnchantProgress() >= ENCHANT_TARGET
                && data.getShardRelicEnderPearlProgress() >= ENDER_PEARL_TARGET
                && data.getShardRelicBlazeRodProgress() >= BLAZE_ROD_TARGET;
    }

    private static boolean isAnvilEnchant(ItemStack leftInput, ItemStack rightInput, ItemStack output) {
        if (!hasEnchantments(output)) {
            return false;
        }

        ItemStack left = leftInput == null ? ItemStack.EMPTY : leftInput;
        ItemStack right = rightInput == null ? ItemStack.EMPTY : rightInput;

        boolean outputDiffersFromLeft = !Objects.equals(left.get(DataComponents.ENCHANTMENTS), output.get(DataComponents.ENCHANTMENTS))
                || !Objects.equals(left.get(DataComponents.STORED_ENCHANTMENTS), output.get(DataComponents.STORED_ENCHANTMENTS));

        return outputDiffersFromLeft || hasEnchantments(right);
    }

    private static boolean hasEnchantments(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.get(DataComponents.ENCHANTMENTS) != null || stack.get(DataComponents.STORED_ENCHANTMENTS) != null);
    }

    private static void showTrialProgress(ServerPlayer player, PlayerQuestData data) {
        player.sendSystemMessage(Texts.dailyTitle(title(), ChatFormatting.LIGHT_PURPLE), false);
        for (Component line : trialProgressLines(data)) {
            player.sendSystemMessage(line, false);
        }
    }

    private static List<Component> trialProgressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable("quest.village-quest.special.shards.progress.amethyst", data.getShardRelicAmethystProgress(), AMETHYST_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.shards.progress.potions", data.getShardRelicPotionProgress(), POTION_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.shards.progress.enchant", data.getShardRelicEnchantProgress(), ENCHANT_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.shards.progress.ender_pearl", data.getShardRelicEnderPearlProgress(), ENDER_PEARL_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.shards.progress.blaze_rod", data.getShardRelicBlazeRodProgress(), BLAZE_ROD_TARGET).withStyle(ChatFormatting.GRAY)
        );
    }

    private static boolean giveTreasureMap(ServerLevel world, ServerPlayer player, PlayerQuestData data, boolean firstTime) {
        if (!hasCacheTarget(data)) {
            BlockPos targetPos = findCacheTarget(world, player.blockPosition());
            if (targetPos == null) {
                player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.special.cache_failed").withStyle(ChatFormatting.RED), false);
                return false;
            }
            data.setShardRelicChestX(targetPos.getX());
            data.setShardRelicChestY(Integer.MIN_VALUE);
            data.setShardRelicChestZ(targetPos.getZ());
        }

        data.setShardRelicQuestStage(ShardRelicQuestStage.CACHE_HUNT);
        prepareCacheChestIfNearby(world, player, data);
        markDirty(world);

        ItemStack map = createTreasureMap(world, data);
        giveOrDrop(player, map);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        MutableComponent body = Component.empty()
                .append(divider.copy())
                .append(Component.literal("\n"))
                .append(Texts.dailyTitle(title(), ChatFormatting.AQUA))
                .append(Component.literal("\n\n"))
                .append(Component.translatable(firstTime
                        ? "quest.village-quest.special.shards.map_granted.1"
                        : "quest.village-quest.special.shards.map_regiven.1"))
                .append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.shards.map_granted.2"))
                .append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        refreshQuestUi(world, player);
        return true;
    }

    private static ItemStack createTreasureMap(ServerLevel world, PlayerQuestData data) {
        int mapY = data.getShardRelicChestY() == Integer.MIN_VALUE ? world.getSeaLevel() : data.getShardRelicChestY();
        ItemStack stack = MapItem.create(world, data.getShardRelicChestX(), data.getShardRelicChestZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(world, stack);
        MapItemSavedData.addTargetDecoration(
                stack,
                new BlockPos(data.getShardRelicChestX(), mapY, data.getShardRelicChestZ()),
                "+",
                MapDecorationTypes.RED_X
        );
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.village-quest.starreach_map").withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.village-quest.starreach_map.lore").withStyle(ChatFormatting.GRAY)
        )));
        return stack;
    }

    private static BlockPos findCacheTarget(ServerLevel world, BlockPos origin) {
        for (int attempt = 0; attempt < MAX_CACHE_ATTEMPTS; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(world.getRandom(), MIN_CACHE_DISTANCE, MAX_CACHE_DISTANCE);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);

            if (!world.getWorldBorder().isWithinBounds(new BlockPos(x, world.getSeaLevel(), z))) {
                continue;
            }
            return new BlockPos(x, world.getSeaLevel(), z);
        }
        return null;
    }

    private static boolean isValidSurface(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        return block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT
                || block == Blocks.COARSE_DIRT
                || block == Blocks.PODZOL
                || block == Blocks.ROOTED_DIRT
                || block == Blocks.MOSS_BLOCK
                || block == Blocks.MUD
                || block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.GRAVEL;
    }

    private static void ensureCacheChest(ServerLevel world, PlayerQuestData data) {
        BlockPos chestPos = chestPos(data);
        if (chestPos == null) {
            return;
        }
        if (!world.getBlockState(chestPos).is(Blocks.CHEST)) {
            world.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        }
        BlockEntity blockEntity = world.getBlockEntity(chestPos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            return;
        }
        if (!containsRing(chest)) {
            chest.clearContent();
            chest.setItem(13, createRingStack());
            chest.setChanged();
        }
    }

    private static boolean containsRing(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(ModItems.STARREACH_RING)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos chestPos(PlayerQuestData data) {
        if (data.getShardRelicChestY() == Integer.MIN_VALUE) {
            return null;
        }
        return new BlockPos(data.getShardRelicChestX(), data.getShardRelicChestY(), data.getShardRelicChestZ());
    }

    private static boolean hasCacheTarget(PlayerQuestData data) {
        return data.getShardRelicChestY() != Integer.MIN_VALUE
                || data.getShardRelicChestX() != 0
                || data.getShardRelicChestZ() != 0;
    }

    private static void prepareCacheChestIfNearby(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        if (world == null || player == null || data == null) {
            return;
        }
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.CACHE_HUNT || !hasCacheTarget(data)) {
            return;
        }
        if (data.getShardRelicChestY() != Integer.MIN_VALUE) {
            ensureCacheChest(world, data);
            return;
        }
        if ((world.getGameTime() + player.getId()) % 20L != 0L) {
            return;
        }

        BlockPos targetPos = new BlockPos(data.getShardRelicChestX(), world.getSeaLevel(), data.getShardRelicChestZ());
        if (player.blockPosition().distSqr(targetPos) > (double) (CACHE_RESOLVE_DISTANCE * CACHE_RESOLVE_DISTANCE)) {
            return;
        }

        BlockPos resolved = resolveCacheChestPos(world, targetPos, false);
        if (resolved == null) {
            return;
        }

        data.setShardRelicChestX(resolved.getX());
        data.setShardRelicChestY(resolved.getY());
        data.setShardRelicChestZ(resolved.getZ());
        ensureCacheChest(world, data);
        markDirty(world);
    }

    private static BlockPos resolveCacheChestPos(ServerLevel world, BlockPos targetPos, boolean loadChunks) {
        for (int radius = 0; radius <= CACHE_SEARCH_RADIUS; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (radius > 0 && Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) {
                        continue;
                    }

                    int x = targetPos.getX() + xOffset;
                    int z = targetPos.getZ() + zOffset;
                    if (loadChunks) {
                        world.getChunk(x >> 4, z >> 4);
                    } else if (!world.hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }

                    int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (topY <= world.getMinY() + 8) {
                        continue;
                    }

                    BlockPos surfacePos = new BlockPos(x, topY - 1, z);
                    if (!isValidSurface(world, surfacePos)) {
                        continue;
                    }

                    BlockPos chestPos = surfacePos.below(2);
                    if (chestPos.getY() <= world.getMinY() + 2) {
                        continue;
                    }
                    if (!world.getBlockState(chestPos.above()).isSolidRender()) {
                        continue;
                    }
                    if (!world.getBlockState(chestPos).getFluidState().isEmpty()) {
                        continue;
                    }
                    return chestPos;
                }
            }
        }
        return null;
    }

    private static Optional<UUID> findCacheOwner(ServerLevel world, BlockPos pos) {
        for (Map.Entry<UUID, PlayerQuestData> entry : QuestState.get(world.getServer()).getPlayersView().entrySet()) {
            PlayerQuestData data = entry.getValue();
            if (data == null || data.getShardRelicQuestStage() != ShardRelicQuestStage.CACHE_HUNT) {
                continue;
            }
            BlockPos chestPos = chestPos(data);
            if (chestPos != null && chestPos.equals(pos)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static boolean hasStarreachRing(ServerPlayer player) {
        if (player == null || ModItems.STARREACH_RING == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.STARREACH_RING)) {
                return true;
            }
        }
        return player.getOffhandItem().is(ModItems.STARREACH_RING);
    }

    private static void completeQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        data.setPendingSpecialOfferKind(null);
        data.setShardRelicQuestStage(ShardRelicQuestStage.COMPLETED);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component body = Component.empty()
                .append(divider.copy())
                .append(Component.literal("\n"))
                .append(Texts.completedTitle(title(), ChatFormatting.AQUA))
                .append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.shards.completed.1"))
                .append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.shards.completed.2"))
                .append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 0.7f);
        refreshQuestUi(world, player);
    }

    private static void applyRingReachBonus(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (instance == null) {
            return;
        }
        boolean shouldHaveBonus = player.getOffhandItem().is(ModItems.STARREACH_RING);
        if (shouldHaveBonus) {
            if (!instance.hasModifier(STARREACH_REACH_ID)) {
                instance.addPermanentModifier(STARREACH_REACH_MODIFIER);
            }
        } else if (instance.hasModifier(STARREACH_REACH_ID)) {
            instance.removeModifier(STARREACH_REACH_ID);
        }
    }

    private static ItemStack createRingStack() {
        ItemStack stack = new ItemStack(ModItems.STARREACH_RING);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.village-quest.starreach_ring").withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.village-quest.starreach_ring.lore").withStyle(ChatFormatting.GRAY)
        )));
        return stack;
    }

    private static boolean consumeInventoryItem(ServerPlayer player, Item item, int amount) {
        if (player == null || item == null || amount <= 0) {
            return false;
        }
        if (DailyQuestService.countInventoryItem(player, item) < amount) {
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
        player.inventoryMenu.broadcastChanges();
        return remaining <= 0;
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

    private static Component title() {
        return Component.translatable("quest.village-quest.special.shards.title");
    }
}
