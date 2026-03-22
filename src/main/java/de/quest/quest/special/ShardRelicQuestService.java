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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShardRelicQuestService {
    private static final int REQUIRED_SHARDS = 10;
    private static final int AMETHYST_TARGET = 4;
    private static final int POTION_TARGET = 3;
    private static final int ENCHANT_TARGET = 1;
    private static final int ENDER_PEARL_TARGET = 1;
    private static final int BLAZE_ROD_TARGET = 1;
    private static final int MIN_CACHE_DISTANCE = 900;
    private static final int MAX_CACHE_DISTANCE = 1900;
    private static final int MAX_CACHE_ATTEMPTS = 64;
    private static final int CACHE_RESOLVE_DISTANCE = 96;
    private static final int CACHE_SEARCH_RADIUS = 10;
    private static final Identifier STARREACH_REACH_ID = Identifier.of(VillageQuest.MOD_ID, "starreach_ring_reach");
    private static final EntityAttributeModifier STARREACH_REACH_MODIFIER =
            new EntityAttributeModifier(STARREACH_REACH_ID, 2.0, EntityAttributeModifier.Operation.ADD_VALUE);

    private record TrialSnapshot(
            ShardRelicQuestStage stage,
            int amethyst,
            int potions,
            int enchants,
            int enderPearls,
            int blazeRods
    ) {}

    private ShardRelicQuestService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerWorld world) {
        if (world != null) {
            QuestState.get(world.getServer()).markDirty();
        }
    }

    private static void refreshQuestUi(ServerWorld world, ServerPlayerEntity player) {
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

    private static void sendTrialProgressFeedback(ServerWorld world, ServerPlayerEntity player, TrialSnapshot before, PlayerQuestData after) {
        if (world == null || player == null || before == null || after == null) {
            return;
        }

        Text actionbar = null;
        boolean changed = false;
        boolean completedStep = false;

        if (before.amethyst() != after.getShardRelicAmethystProgress()) {
            changed = true;
            actionbar = Text.translatable(
                    "quest.village-quest.special.shards.progress.amethyst",
                    after.getShardRelicAmethystProgress(),
                    AMETHYST_TARGET
            ).formatted(Formatting.LIGHT_PURPLE);
            if (before.amethyst() < AMETHYST_TARGET && after.getShardRelicAmethystProgress() >= AMETHYST_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.potions() != after.getShardRelicPotionProgress()) {
            changed = true;
            actionbar = Text.translatable(
                    "quest.village-quest.special.shards.progress.potions",
                    after.getShardRelicPotionProgress(),
                    POTION_TARGET
            ).formatted(Formatting.LIGHT_PURPLE);
            if (before.potions() < POTION_TARGET && after.getShardRelicPotionProgress() >= POTION_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.enchants() != after.getShardRelicEnchantProgress()) {
            changed = true;
            actionbar = Text.translatable(
                    "quest.village-quest.special.shards.progress.enchant",
                    after.getShardRelicEnchantProgress(),
                    ENCHANT_TARGET
            ).formatted(Formatting.LIGHT_PURPLE);
            if (before.enchants() < ENCHANT_TARGET && after.getShardRelicEnchantProgress() >= ENCHANT_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.enderPearls() != after.getShardRelicEnderPearlProgress()) {
            changed = true;
            actionbar = Text.translatable(
                    "quest.village-quest.special.shards.progress.ender_pearl",
                    after.getShardRelicEnderPearlProgress(),
                    ENDER_PEARL_TARGET
            ).formatted(Formatting.LIGHT_PURPLE);
            if (before.enderPearls() < ENDER_PEARL_TARGET && after.getShardRelicEnderPearlProgress() >= ENDER_PEARL_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (before.blazeRods() != after.getShardRelicBlazeRodProgress()) {
            changed = true;
            actionbar = Text.translatable(
                    "quest.village-quest.special.shards.progress.blaze_rod",
                    after.getShardRelicBlazeRodProgress(),
                    BLAZE_ROD_TARGET
            ).formatted(Formatting.LIGHT_PURPLE);
            if (before.blazeRods() < BLAZE_ROD_TARGET && after.getShardRelicBlazeRodProgress() >= BLAZE_ROD_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.LIGHT_PURPLE), false);
                completedStep = true;
            }
        }

        if (!changed) {
            return;
        }

        if (actionbar != null) {
            player.sendMessage(actionbar, true);
        }
        float pitch = completedStep ? 1.7f : 1.4f;
        float volume = completedStep ? 0.28f : 0.18f;
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, volume, pitch);
        refreshQuestUi(world, player);
    }

    public static boolean hasPendingSpecialOffer(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.SHARD_RELIC;
    }

    public static boolean consumePendingSpecialOffer(ServerWorld world, UUID playerId) {
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

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player) {
        return handleQuestMasterInteraction(world, player, false);
    }

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
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

    public static boolean acceptSpecialQuest(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.MAGIC_SHARD == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferSpecialQuest(world, player, data)) {
            return false;
        }
        if (!consumeInventoryItem(player, ModItems.MAGIC_SHARD, REQUIRED_SHARDS)) {
            player.sendMessage(Text.translatable("message.village-quest.questmaster.special.shards_missing").formatted(Formatting.RED), false);
            markDirty(world);
            return false;
        }

        startTrial(world, player, data);
        markDirty(world);
        player.sendMessage(Texts.acceptedTitle(title(), Formatting.LIGHT_PURPLE), false);
        showTrialProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyRingReachBonus(player);
            PlayerQuestData data = data(world, player.getUuid());
            updatePassiveProgress(world, player, data, true);
            if (data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT) {
                prepareCacheChestIfNearby(world, player, data);
            }
            if (data.getShardRelicQuestStage() == ShardRelicQuestStage.CACHE_HUNT && hasStarreachRing(player)) {
                completeQuest(world, player, data);
            }
        }
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (world == null || player == null || state == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE || !state.isOf(Blocks.AMETHYST_CLUSTER)) {
            return;
        }
        TrialSnapshot before = captureTrialSnapshot(data);
        int next = Math.min(AMETHYST_TARGET, data.getShardRelicAmethystProgress() + 1);
        if (next == data.getShardRelicAmethystProgress()) {
            return;
        }
        data.setShardRelicAmethystProgress(next);
        updateReadyState(world, player, data, true);
        markDirty(world);
        sendTrialProgressFeedback(world, player, before, data);
    }

    public static void onPotionBrewed(ServerPlayerEntity player) {
        if (player == null || !(player.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
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

    public static void onAnvilOutput(ServerPlayerEntity player, ItemStack leftInput, ItemStack rightInput, ItemStack output) {
        if (player == null || !(player.getEntityWorld() instanceof ServerWorld world) || output == null || output.isEmpty()) {
            return;
        }

        PlayerQuestData data = data(world, player.getUuid());
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

    public static boolean allowBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return true;
        }
        Optional<UUID> owner = findCacheOwner(world, pos);
        if (owner.isEmpty()) {
            return true;
        }
        player.sendMessage(Text.translatable("message.village-quest.questmaster.special.cache_protected").formatted(Formatting.RED), false);
        return false;
    }

    public static net.minecraft.util.ActionResult onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return net.minecraft.util.ActionResult.PASS;
        }
        Optional<UUID> owner = findCacheOwner(world, pos);
        if (owner.isEmpty()) {
            return net.minecraft.util.ActionResult.PASS;
        }
        if (!owner.get().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.questmaster.special.cache_locked").formatted(Formatting.RED), false);
            return net.minecraft.util.ActionResult.FAIL;
        }
        return net.minecraft.util.ActionResult.PASS;
    }

    public static boolean hasPendingOffer(ServerWorld world, UUID playerId) {
        return hasPendingSpecialOffer(world, playerId);
    }

    public static boolean isActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        ShardRelicQuestStage stage = data(world, playerId).getShardRelicQuestStage();
        return stage == ShardRelicQuestStage.TRIAL_ACTIVE
                || stage == ShardRelicQuestStage.TRIAL_READY
                || stage == ShardRelicQuestStage.CACHE_HUNT;
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return data.getPendingSpecialOfferKind() == SpecialQuestKind.SHARD_RELIC
                || data.getShardRelicQuestStage() != ShardRelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        return switch (data.getShardRelicQuestStage()) {
            case TRIAL_ACTIVE, TRIAL_READY -> new SpecialQuestStatus(title(), trialProgressLines(data));
            case CACHE_HUNT -> new SpecialQuestStatus(title(), List.of(
                    Text.translatable("quest.village-quest.special.shards.cache_line.1").formatted(Formatting.GRAY),
                    Text.translatable("quest.village-quest.special.shards.cache_line.2").formatted(Formatting.GRAY)
            ));
            default -> null;
        };
    }

    public static boolean adminStartCacheHunt(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
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

    public static boolean adminTeleportToCache(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
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
        player.teleport(
                world,
                chestPos.getX() + 0.5,
                chestPos.getY() + 2.0,
                chestPos.getZ() + 0.5,
                Set.of(),
                player.getYaw(),
                player.getPitch(),
                true
        );
        return true;
    }

    public static boolean claimFromQuestMaster(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
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

    private static boolean shouldOfferSpecialQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        return data.getShardRelicQuestStage() == ShardRelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.MAGIC_SHARD != null
                && DailyQuestService.countInventoryItem(player, ModItems.MAGIC_SHARD) >= REQUIRED_SHARDS
                && DailyQuestService.openQuestStatus(world, player.getUuid()) == null;
    }

    private static void showSpecialOffer(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        data.setPendingSpecialOfferKind(SpecialQuestKind.SHARD_RELIC);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text accept = Text.translatable("text.village-quest.special.offer.accept").styled(style -> style
                .withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        Text body = Text.empty()
                .append(divider.copy())
                .append(Text.literal("\n"))
                .append(Texts.dailyTitle(title(), Formatting.LIGHT_PURPLE))
                .append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.shards.offer.1"))
                .append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.shards.offer.2", REQUIRED_SHARDS))
                .append(Text.literal("\n\n\n"))
                .append(accept)
                .append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
    }

    private static void startTrial(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        data.setShardRelicQuestStage(ShardRelicQuestStage.TRIAL_ACTIVE);
        data.setShardRelicAmethystProgress(0);
        data.setShardRelicPotionProgress(0);
        data.setShardRelicEnchantProgress(0);
        data.setShardRelicEnderPearlProgress(0);
        data.setShardRelicBlazeRodProgress(0);
        data.setShardRelicEnchantBaseline(DailyQuestService.getCustomStat(player, Stats.ENCHANT_ITEM));
        data.setShardRelicEnderPearlBaseline(DailyQuestService.getPickedUpStat(player, Items.ENDER_PEARL));
        data.setShardRelicBlazeRodBaseline(DailyQuestService.getPickedUpStat(player, Items.BLAZE_ROD));
        data.setShardRelicChestX(0);
        data.setShardRelicChestY(Integer.MIN_VALUE);
        data.setShardRelicChestZ(0);
        markDirty(world);
    }

    private static void updatePassiveProgress(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, boolean sendReadyMessage) {
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

        int enderPearls = Math.min(ENDER_PEARL_TARGET, Math.max(0, DailyQuestService.getPickedUpStat(player, Items.ENDER_PEARL) - data.getShardRelicEnderPearlBaseline()));
        if (enderPearls != data.getShardRelicEnderPearlProgress()) {
            data.setShardRelicEnderPearlProgress(enderPearls);
            changed = true;
        }

        int blazeRods = Math.min(BLAZE_ROD_TARGET, Math.max(0, DailyQuestService.getPickedUpStat(player, Items.BLAZE_ROD) - data.getShardRelicBlazeRodBaseline()));
        if (blazeRods != data.getShardRelicBlazeRodProgress()) {
            data.setShardRelicBlazeRodProgress(blazeRods);
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

    private static boolean updateReadyState(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, boolean sendReadyMessage) {
        if (data.getShardRelicQuestStage() != ShardRelicQuestStage.TRIAL_ACTIVE) {
            return false;
        }
        if (!isTrialComplete(data)) {
            return false;
        }
        data.setShardRelicQuestStage(ShardRelicQuestStage.TRIAL_READY);
        if (sendReadyMessage && player != null) {
            player.sendMessage(Text.translatable("message.village-quest.questmaster.special.trial_ready").formatted(Formatting.LIGHT_PURPLE), false);
            if (world != null) {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.15f);
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

        boolean outputDiffersFromLeft = !Objects.equals(left.get(DataComponentTypes.ENCHANTMENTS), output.get(DataComponentTypes.ENCHANTMENTS))
                || !Objects.equals(left.get(DataComponentTypes.STORED_ENCHANTMENTS), output.get(DataComponentTypes.STORED_ENCHANTMENTS));

        return outputDiffersFromLeft || hasEnchantments(right);
    }

    private static boolean hasEnchantments(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.get(DataComponentTypes.ENCHANTMENTS) != null || stack.get(DataComponentTypes.STORED_ENCHANTMENTS) != null);
    }

    private static void showTrialProgress(ServerPlayerEntity player, PlayerQuestData data) {
        player.sendMessage(Texts.dailyTitle(title(), Formatting.LIGHT_PURPLE), false);
        for (Text line : trialProgressLines(data)) {
            player.sendMessage(line, false);
        }
    }

    private static List<Text> trialProgressLines(PlayerQuestData data) {
        return List.of(
                Text.translatable("quest.village-quest.special.shards.progress.amethyst", data.getShardRelicAmethystProgress(), AMETHYST_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.shards.progress.potions", data.getShardRelicPotionProgress(), POTION_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.shards.progress.enchant", data.getShardRelicEnchantProgress(), ENCHANT_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.shards.progress.ender_pearl", data.getShardRelicEnderPearlProgress(), ENDER_PEARL_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.shards.progress.blaze_rod", data.getShardRelicBlazeRodProgress(), BLAZE_ROD_TARGET).formatted(Formatting.GRAY)
        );
    }

    private static boolean giveTreasureMap(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, boolean firstTime) {
        if (!hasCacheTarget(data)) {
            BlockPos targetPos = findCacheTarget(world, player.getBlockPos());
            if (targetPos == null) {
                player.sendMessage(Text.translatable("message.village-quest.questmaster.special.cache_failed").formatted(Formatting.RED), false);
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

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        MutableText body = Text.empty()
                .append(divider.copy())
                .append(Text.literal("\n"))
                .append(Texts.dailyTitle(title(), Formatting.AQUA))
                .append(Text.literal("\n\n"))
                .append(Text.translatable(firstTime
                        ? "quest.village-quest.special.shards.map_granted.1"
                        : "quest.village-quest.special.shards.map_regiven.1"))
                .append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.shards.map_granted.2"))
                .append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        refreshQuestUi(world, player);
        return true;
    }

    private static ItemStack createTreasureMap(ServerWorld world, PlayerQuestData data) {
        int mapY = data.getShardRelicChestY() == Integer.MIN_VALUE ? world.getSeaLevel() : data.getShardRelicChestY();
        ItemStack stack = FilledMapItem.createMap(world, data.getShardRelicChestX(), data.getShardRelicChestZ(), (byte) 2, true, true);
        FilledMapItem.fillExplorationMap(world, stack);
        MapState.addDecorationsNbt(
                stack,
                new BlockPos(data.getShardRelicChestX(), mapY, data.getShardRelicChestZ()),
                "+",
                MapDecorationTypes.RED_X
        );
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.village-quest.starreach_map").formatted(Formatting.AQUA));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("item.village-quest.starreach_map.lore").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    private static BlockPos findCacheTarget(ServerWorld world, BlockPos origin) {
        for (int attempt = 0; attempt < MAX_CACHE_ATTEMPTS; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int distance = MathHelper.nextInt(world.random, MIN_CACHE_DISTANCE, MAX_CACHE_DISTANCE);
            int x = origin.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + MathHelper.floor(Math.sin(angle) * distance);

            if (!world.getWorldBorder().contains(new BlockPos(x, world.getSeaLevel(), z))) {
                continue;
            }
            return new BlockPos(x, world.getSeaLevel(), z);
        }
        return null;
    }

    private static boolean isValidSurface(ServerWorld world, BlockPos pos) {
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

    private static void ensureCacheChest(ServerWorld world, PlayerQuestData data) {
        BlockPos chestPos = chestPos(data);
        if (chestPos == null) {
            return;
        }
        if (!world.getBlockState(chestPos).isOf(Blocks.CHEST)) {
            world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), 3);
        }
        BlockEntity blockEntity = world.getBlockEntity(chestPos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            return;
        }
        if (!containsRing(chest)) {
            chest.clear();
            chest.setStack(13, createRingStack());
            chest.markDirty();
        }
    }

    private static boolean containsRing(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.size(); slot++) {
            if (chest.getStack(slot).isOf(ModItems.STARREACH_RING)) {
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

    private static void prepareCacheChestIfNearby(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
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
        if ((world.getTime() + player.getId()) % 20L != 0L) {
            return;
        }

        BlockPos targetPos = new BlockPos(data.getShardRelicChestX(), world.getSeaLevel(), data.getShardRelicChestZ());
        if (player.getBlockPos().getSquaredDistance(targetPos) > (double) (CACHE_RESOLVE_DISTANCE * CACHE_RESOLVE_DISTANCE)) {
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

    private static BlockPos resolveCacheChestPos(ServerWorld world, BlockPos targetPos, boolean loadChunks) {
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
                    } else if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }

                    int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (topY <= world.getBottomY() + 8) {
                        continue;
                    }

                    BlockPos surfacePos = new BlockPos(x, topY - 1, z);
                    if (!isValidSurface(world, surfacePos)) {
                        continue;
                    }

                    BlockPos chestPos = surfacePos.down(2);
                    if (chestPos.getY() <= world.getBottomY() + 2) {
                        continue;
                    }
                    if (!world.getBlockState(chestPos.up()).isOpaqueFullCube()) {
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

    private static Optional<UUID> findCacheOwner(ServerWorld world, BlockPos pos) {
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

    private static boolean hasStarreachRing(ServerPlayerEntity player) {
        if (player == null || ModItems.STARREACH_RING == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isOf(ModItems.STARREACH_RING)) {
                return true;
            }
        }
        return player.getOffHandStack().isOf(ModItems.STARREACH_RING);
    }

    private static void completeQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        data.setPendingSpecialOfferKind(null);
        data.setShardRelicQuestStage(ShardRelicQuestStage.COMPLETED);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text body = Text.empty()
                .append(divider.copy())
                .append(Text.literal("\n"))
                .append(Texts.completedTitle(title(), Formatting.AQUA))
                .append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.shards.completed.1"))
                .append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.shards.completed.2"))
                .append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9f, 0.7f);
        refreshQuestUi(world, player);
    }

    private static void applyRingReachBonus(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        if (instance == null) {
            return;
        }
        boolean shouldHaveBonus = player.getOffHandStack().isOf(ModItems.STARREACH_RING);
        if (shouldHaveBonus) {
            if (!instance.hasModifier(STARREACH_REACH_ID)) {
                instance.addPersistentModifier(STARREACH_REACH_MODIFIER);
            }
        } else if (instance.hasModifier(STARREACH_REACH_ID)) {
            instance.removeModifier(STARREACH_REACH_ID);
        }
    }

    private static ItemStack createRingStack() {
        ItemStack stack = new ItemStack(ModItems.STARREACH_RING);
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.village-quest.starreach_ring").formatted(Formatting.AQUA));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("item.village-quest.starreach_ring.lore").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    private static boolean consumeInventoryItem(ServerPlayerEntity player, Item item, int amount) {
        if (player == null || item == null || amount <= 0) {
            return false;
        }
        if (DailyQuestService.countInventoryItem(player, item) < amount) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || !stack.isOf(item)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.decrement(removed);
            remaining -= removed;
        }
        player.playerScreenHandler.sendContentUpdates();
        return remaining <= 0;
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().insertStack(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.dropItem(remainder, false);
            }
            player.sendMessage(Text.translatable("message.village-quest.daily.inventory_full.prefix").formatted(Formatting.GRAY)
                    .append(stack.toHoverableText())
                    .append(Text.translatable("message.village-quest.daily.inventory_full.suffix").formatted(Formatting.GRAY)), false);
        } else {
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    private static Text title() {
        return Text.translatable("quest.village-quest.special.shards.title");
    }
}
