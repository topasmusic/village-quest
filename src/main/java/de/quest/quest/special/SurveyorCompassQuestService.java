package de.quest.quest.special;

import com.mojang.datafixers.util.Pair;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModItems;
import de.quest.util.Texts;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.structure.Structure;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SurveyorCompassQuestService {
    public static final int REQUIRED_CRAFTING_REPUTATION = 200;
    private static final int REDSTONE_TARGET = 16;
    private static final int CRAFTED_TARGET = 1;
    private static final int PICKAXE_READY_TARGET = 1;
    private static final int BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_HORIZONTAL_STEP = 32;
    private static final int BIOME_VERTICAL_STEP = 64;
    private static final int STRUCTURE_SEARCH_RADIUS = 128;
    private static final int BONUS_INGOT_COUNT = 10;
    private static final long HOME_COOLDOWN_TICKS = 20L * 60L * 30L;

    private record CompassTarget(BlockPos pos, Text label) {}
    private record HomeTarget(ServerWorld world, BlockPos pos, float yaw) {}

    private enum CompassMode {
        HOME("text.village-quest.special.surveyor_compass.mode.home"),
        VILLAGE("text.village-quest.special.surveyor_compass.mode.village"),
        STRONGHOLD("text.village-quest.special.surveyor_compass.mode.stronghold"),
        TRIAL_CHAMBER("text.village-quest.special.surveyor_compass.mode.trial_chamber"),
        WOODLAND_MANSION("text.village-quest.special.surveyor_compass.mode.woodland_mansion"),
        OCEAN_MONUMENT("text.village-quest.special.surveyor_compass.mode.ocean_monument"),
        JUNGLE_TEMPLE("text.village-quest.special.surveyor_compass.mode.jungle_temple"),
        SWAMP_HUT("text.village-quest.special.surveyor_compass.mode.swamp_hut"),
        MINESHAFT("text.village-quest.special.surveyor_compass.mode.mineshaft"),
        SHIPWRECK("text.village-quest.special.surveyor_compass.mode.shipwreck"),
        RUINED_PORTAL("text.village-quest.special.surveyor_compass.mode.ruined_portal"),
        FOREST("text.village-quest.special.surveyor_compass.mode.forest"),
        PLAINS("text.village-quest.special.surveyor_compass.mode.plains"),
        TAIGA("text.village-quest.special.surveyor_compass.mode.taiga"),
        SNOWY_PLAINS("text.village-quest.special.surveyor_compass.mode.snowy_plains"),
        MOUNTAIN("text.village-quest.special.surveyor_compass.mode.mountain"),
        MEADOW("text.village-quest.special.surveyor_compass.mode.meadow"),
        CHERRY_GROVE("text.village-quest.special.surveyor_compass.mode.cherry_grove"),
        JUNGLE("text.village-quest.special.surveyor_compass.mode.jungle"),
        DESERT("text.village-quest.special.surveyor_compass.mode.desert"),
        SAVANNA("text.village-quest.special.surveyor_compass.mode.savanna"),
        SWAMP("text.village-quest.special.surveyor_compass.mode.swamp"),
        BADLANDS("text.village-quest.special.surveyor_compass.mode.badlands"),
        BEACH("text.village-quest.special.surveyor_compass.mode.beach"),
        RIVER("text.village-quest.special.surveyor_compass.mode.river"),
        OCEAN("text.village-quest.special.surveyor_compass.mode.ocean"),
        MUSHROOM_FIELDS("text.village-quest.special.surveyor_compass.mode.mushroom_fields");

        private final String translationKey;

        CompassMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text label() {
            return Text.translatable(translationKey);
        }
    }

    private SurveyorCompassQuestService() {}

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

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerQuestData data = data(world, player.getUuid());
            RelicQuestStage stage = data.getSurveyorCompassQuestStage();
            if (stage != RelicQuestStage.ACTIVE && stage != RelicQuestStage.READY) {
                continue;
            }

            int beforeRedstone = data.getSurveyorCompassRedstoneProgress();
            int beforeCrafted = data.getSurveyorCompassCraftedProgress();
            int beforeReady = data.getSurveyorCompassPickaxeReadyProgress();
            RelicQuestStage beforeStage = stage;

            int craftedProgress = Math.min(CRAFTED_TARGET, Math.max(0, DailyQuestService.getCraftedStat(player, Items.NETHERITE_PICKAXE) - data.getSurveyorCompassPickaxeBaseline()));
            int pickaxeReady = hasEligibleTurnInPickaxe(player) ? PICKAXE_READY_TARGET : 0;

            data.setSurveyorCompassCraftedProgress(craftedProgress);
            data.setSurveyorCompassPickaxeReadyProgress(pickaxeReady);
            updateReadyState(data);

            if (beforeCrafted != data.getSurveyorCompassCraftedProgress()
                    || beforeReady != data.getSurveyorCompassPickaxeReadyProgress()
                    || beforeStage != data.getSurveyorCompassQuestStage()) {
                markDirty(world);
                sendProgressFeedback(world, player, data, beforeRedstone, beforeCrafted, beforeReady, beforeStage);
            }
        }
    }

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        return switch (data.getSurveyorCompassQuestStage()) {
            case ACTIVE -> {
                showProgress(player, data);
                yield true;
            }
            case READY -> {
                claimFromQuestMaster(world, player);
                yield true;
            }
            case COMPLETED -> false;
            case NONE -> {
                if (!skipOffer && shouldOfferQuest(world, player, data)) {
                    showOffer(world, player);
                    yield true;
                }
                yield false;
            }
        };
    }

    public static boolean acceptQuest(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.SURVEYORS_COMPASS == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetSurveyorCompassQuest();
        data.setSurveyorCompassQuestStage(RelicQuestStage.ACTIVE);
        data.setSurveyorCompassPickaxeBaseline(DailyQuestService.getCraftedStat(player, Items.NETHERITE_PICKAXE));
        markDirty(world);
        player.sendMessage(Texts.acceptedTitle(title(), Formatting.GOLD), false);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getSurveyorCompassQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return data.getPendingSpecialOfferKind() == SpecialQuestKind.SURVEYOR_COMPASS
                || data.getSurveyorCompassQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        RelicQuestStage stage = data.getSurveyorCompassQuestStage();
        if (stage != RelicQuestStage.ACTIVE && stage != RelicQuestStage.READY) {
            return null;
        }
        return new SpecialQuestStatus(title(), progressLines(data));
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (world == null || player == null || state == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getSurveyorCompassQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }
        if (!state.isOf(Blocks.REDSTONE_ORE) && !state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return;
        }

        int beforeRedstone = data.getSurveyorCompassRedstoneProgress();
        int beforeCrafted = data.getSurveyorCompassCraftedProgress();
        int beforeReady = data.getSurveyorCompassPickaxeReadyProgress();
        RelicQuestStage beforeStage = data.getSurveyorCompassQuestStage();
        data.setSurveyorCompassRedstoneProgress(Math.min(REDSTONE_TARGET, beforeRedstone + 1));
        updateReadyState(data);
        markDirty(world);
        sendProgressFeedback(world, player, data, beforeRedstone, beforeCrafted, beforeReady, beforeStage);
    }

    public static boolean claimFromQuestMaster(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getSurveyorCompassQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        if (!consumeEligibleTurnInPickaxe(player)) {
            data.setSurveyorCompassPickaxeReadyProgress(0);
            data.setSurveyorCompassQuestStage(RelicQuestStage.ACTIVE);
            markDirty(world);
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.pickaxe_missing").formatted(Formatting.RED), false);
            refreshQuestUi(world, player);
            return false;
        }

        completeQuest(world, player, data);
        return true;
    }

    public static ActionResult useCompass(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (world == null || player == null || stack == null || stack.isEmpty()) {
            return ActionResult.PASS;
        }

        if (player.isSneaking()) {
            CompassMode mode = cycleMode(world, player.getUuid());
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.mode", mode.label()).formatted(Formatting.GOLD), true);
            world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 1.15f);
            return ActionResult.SUCCESS;
        }

        CompassMode mode = currentMode(world, player.getUuid());
        if (mode == CompassMode.HOME) {
            return returnHome(world, player, stack);
        }

        CompassTarget target = locateTarget(world, player.getBlockPos(), mode);
        if (target == null) {
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.not_found", mode.label()).formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }

        stack.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(
                Optional.of(GlobalPos.create(world.getRegistryKey(), target.pos())),
                true
        ));
        int distance = (int) Math.round(Math.sqrt(player.getBlockPos().getSquaredDistance(target.pos())));
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.bound", target.label(), distance).formatted(Formatting.GOLD), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.15f);
        return ActionResult.SUCCESS;
    }

    private static CompassMode currentMode(ServerWorld world, UUID playerId) {
        CompassMode[] modes = CompassMode.values();
        int index = Math.floorMod(data(world, playerId).getSurveyorCompassModeIndex(), modes.length);
        return modes[index];
    }

    private static CompassMode cycleMode(ServerWorld world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        CompassMode[] modes = CompassMode.values();
        int nextIndex = Math.floorMod(data.getSurveyorCompassModeIndex() + 1, modes.length);
        data.setSurveyorCompassModeIndex(nextIndex);
        markDirty(world);
        return modes[nextIndex];
    }

    private static CompassTarget locateTarget(ServerWorld world, BlockPos origin, CompassMode mode) {
        return switch (mode) {
            case HOME -> null;
            case VILLAGE -> structureTarget(world, origin, StructureTags.VILLAGE, mode.label());
            case STRONGHOLD -> structureTarget(world, origin, StructureTags.EYE_OF_ENDER_LOCATED, mode.label());
            case TRIAL_CHAMBER -> structureTarget(world, origin, StructureTags.ON_TRIAL_CHAMBERS_MAPS, mode.label());
            case WOODLAND_MANSION -> structureTarget(world, origin, StructureTags.ON_WOODLAND_EXPLORER_MAPS, mode.label());
            case OCEAN_MONUMENT -> structureTarget(world, origin, StructureTags.ON_OCEAN_EXPLORER_MAPS, mode.label());
            case JUNGLE_TEMPLE -> structureTarget(world, origin, StructureTags.ON_JUNGLE_EXPLORER_MAPS, mode.label());
            case SWAMP_HUT -> structureTarget(world, origin, StructureTags.ON_SWAMP_EXPLORER_MAPS, mode.label());
            case MINESHAFT -> structureTarget(world, origin, StructureTags.MINESHAFT, mode.label());
            case SHIPWRECK -> structureTarget(world, origin, StructureTags.SHIPWRECK, mode.label());
            case RUINED_PORTAL -> structureTarget(world, origin, StructureTags.RUINED_PORTAL, mode.label());
            case FOREST -> biomeTarget(world, origin, BiomeTags.IS_FOREST, mode.label());
            case PLAINS -> specificBiomeTarget(world, origin, BiomeKeys.PLAINS, mode.label());
            case TAIGA -> biomeTarget(world, origin, BiomeTags.IS_TAIGA, mode.label());
            case SNOWY_PLAINS -> specificBiomeTarget(world, origin, BiomeKeys.SNOWY_PLAINS, mode.label());
            case MOUNTAIN -> biomeTarget(world, origin, BiomeTags.IS_MOUNTAIN, mode.label());
            case MEADOW -> specificBiomeTarget(world, origin, BiomeKeys.MEADOW, mode.label());
            case CHERRY_GROVE -> specificBiomeTarget(world, origin, BiomeKeys.CHERRY_GROVE, mode.label());
            case JUNGLE -> biomeTarget(world, origin, BiomeTags.IS_JUNGLE, mode.label());
            case DESERT -> specificBiomeTarget(world, origin, BiomeKeys.DESERT, mode.label());
            case SAVANNA -> biomeTarget(world, origin, BiomeTags.IS_SAVANNA, mode.label());
            case SWAMP -> specificBiomeTarget(world, origin, BiomeKeys.SWAMP, mode.label());
            case BADLANDS -> biomeTarget(world, origin, BiomeTags.IS_BADLANDS, mode.label());
            case BEACH -> biomeTarget(world, origin, BiomeTags.IS_BEACH, mode.label());
            case RIVER -> biomeTarget(world, origin, BiomeTags.IS_RIVER, mode.label());
            case OCEAN -> biomeTarget(world, origin, BiomeTags.IS_OCEAN, mode.label());
            case MUSHROOM_FIELDS -> specificBiomeTarget(world, origin, BiomeKeys.MUSHROOM_FIELDS, mode.label());
        };
    }

    private static CompassTarget biomeTarget(ServerWorld world, BlockPos origin, TagKey<Biome> tag, Text label) {
        Pair<BlockPos, RegistryEntry<Biome>> result = world.locateBiome(entry -> entry.isIn(tag), origin, BIOME_SEARCH_RADIUS, BIOME_HORIZONTAL_STEP, BIOME_VERTICAL_STEP);
        return result == null ? null : new CompassTarget(result.getFirst(), label);
    }

    private static CompassTarget specificBiomeTarget(ServerWorld world, BlockPos origin, RegistryKey<Biome> biomeKey, Text label) {
        Pair<BlockPos, RegistryEntry<Biome>> result = world.locateBiome(
                entry -> entry.getKey().map(biomeKey::equals).orElse(false),
                origin,
                BIOME_SEARCH_RADIUS,
                BIOME_HORIZONTAL_STEP,
                BIOME_VERTICAL_STEP
        );
        return result == null ? null : new CompassTarget(result.getFirst(), label);
    }

    private static CompassTarget structureTarget(ServerWorld world, BlockPos origin, TagKey<Structure> tag, Text label) {
        BlockPos result = world.locateStructure(tag, origin, STRUCTURE_SEARCH_RADIUS, false);
        return result == null ? null : new CompassTarget(result, label);
    }

    private static ActionResult returnHome(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        PlayerQuestData data = data(world, player.getUuid());
        long now = world.getServer().getOverworld().getTime();
        long cooldownUntil = data.getSurveyorCompassHomeCooldownUntil();
        if (cooldownUntil > now) {
            player.sendMessage(Text.translatable(
                    "message.village-quest.special.surveyor_compass.home_cooldown",
                    formatCooldown(cooldownUntil - now)
            ).formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        HomeTarget home = resolveHomeTarget(world.getServer(), player);
        if (home == null) {
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.home_missing").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        stack.remove(DataComponentTypes.LODESTONE_TRACKER);
        home.world().getChunk(home.pos().getX() >> 4, home.pos().getZ() >> 4);
        player.teleport(
                home.world(),
                home.pos().getX() + 0.5,
                home.pos().getY() + 0.1,
                home.pos().getZ() + 0.5,
                Set.of(),
                home.yaw(),
                player.getPitch(),
                true
        );
        data.setSurveyorCompassHomeCooldownUntil(now + HOME_COOLDOWN_TICKS);
        markDirty(world);
        home.world().playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.85f, 1.0f);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.returned_home").formatted(Formatting.GOLD), false);
        return ActionResult.SUCCESS;
    }

    private static HomeTarget resolveHomeTarget(MinecraftServer server, ServerPlayerEntity player) {
        if (server == null || player == null) {
            return null;
        }

        ServerPlayerEntity.Respawn respawn = player.getRespawn();
        if (respawn != null && respawn.respawnData() != null) {
            WorldProperties.SpawnPoint spawnPoint = respawn.respawnData();
            ServerWorld spawnWorld = server.getWorld(spawnPoint.getDimension());
            if (spawnWorld != null) {
                return new HomeTarget(spawnWorld, spawnPoint.getPos(), spawnPoint.yaw());
            }
        }

        ServerWorld spawnWorld = server.getSpawnWorld();
        WorldProperties.SpawnPoint spawnPoint = server.getSpawnPoint();
        if (spawnWorld == null || spawnPoint == null) {
            return null;
        }
        return new HomeTarget(spawnWorld, spawnPoint.getPos(), spawnPoint.yaw());
    }

    private static Text formatCooldown(long remainingTicks) {
        long totalSeconds = Math.max(1L, (remainingTicks + 19L) / 20L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L && seconds > 0L) {
            return Text.translatable("text.village-quest.duration.minutes_seconds", minutes, seconds);
        }
        if (minutes > 0L) {
            return Text.translatable("text.village-quest.duration.minutes", minutes);
        }
        return Text.translatable("text.village-quest.duration.seconds", seconds);
    }

    private static boolean shouldOfferQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        return data.getSurveyorCompassQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.SURVEYORS_COMPASS != null
                && DailyQuestService.openQuestStatus(world, player.getUuid()) == null
                && de.quest.reputation.ReputationService.get(world, player.getUuid(), de.quest.reputation.ReputationService.ReputationTrack.CRAFTING) >= REQUIRED_CRAFTING_REPUTATION;
    }

    private static void showOffer(ServerWorld world, ServerPlayerEntity player) {
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(SpecialQuestKind.SURVEYOR_COMPASS);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text accept = Text.translatable("text.village-quest.special.surveyor_compass.offer.accept").styled(style -> style
                .withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.dailyTitle(title(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.surveyor_compass.offer.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.surveyor_compass.offer.2")).append(Text.literal("\n\n\n"))
                .append(accept).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
    }

    private static void showProgress(ServerPlayerEntity player, PlayerQuestData data) {
        player.sendMessage(Texts.dailyTitle(title(), Formatting.GOLD), false);
        for (Text line : progressLines(data)) {
            player.sendMessage(line, false);
        }
    }

    private static List<Text> progressLines(PlayerQuestData data) {
        return List.of(
                Text.translatable("quest.village-quest.special.surveyor_compass.progress.redstone", data.getSurveyorCompassRedstoneProgress(), REDSTONE_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.surveyor_compass.progress.crafted", data.getSurveyorCompassCraftedProgress(), CRAFTED_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.surveyor_compass.progress.pickaxe", data.getSurveyorCompassPickaxeReadyProgress(), PICKAXE_READY_TARGET).formatted(Formatting.GRAY)
        );
    }

    private static void updateReadyState(PlayerQuestData data) {
        if (isComplete(data)) {
            data.setSurveyorCompassQuestStage(RelicQuestStage.READY);
        } else if (data.getSurveyorCompassQuestStage() == RelicQuestStage.READY) {
            data.setSurveyorCompassQuestStage(RelicQuestStage.ACTIVE);
        }
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getSurveyorCompassRedstoneProgress() >= REDSTONE_TARGET
                && data.getSurveyorCompassCraftedProgress() >= CRAFTED_TARGET
                && data.getSurveyorCompassPickaxeReadyProgress() >= PICKAXE_READY_TARGET;
    }

    private static boolean hasEligibleTurnInPickaxe(ServerPlayerEntity player) {
        return findEligibleTurnInSlot(player) >= 0;
    }

    private static int findEligibleTurnInSlot(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isEligibleTurnInPickaxe(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean consumeEligibleTurnInPickaxe(ServerPlayerEntity player) {
        int slot = findEligibleTurnInSlot(player);
        if (slot < 0) {
            return false;
        }
        player.getInventory().getStack(slot).decrement(1);
        player.playerScreenHandler.sendContentUpdates();
        return true;
    }

    private static boolean isEligibleTurnInPickaxe(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.isOf(Items.NETHERITE_PICKAXE)
                && !stack.hasEnchantments();
    }

    private static void sendProgressFeedback(ServerWorld world,
                                             ServerPlayerEntity player,
                                             PlayerQuestData data,
                                             int beforeRedstone,
                                             int beforeCrafted,
                                             int beforeReady,
                                             RelicQuestStage beforeStage) {
        Text actionbar = null;
        boolean completedStep = false;
        if (beforeRedstone != data.getSurveyorCompassRedstoneProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.surveyor_compass.progress.redstone", data.getSurveyorCompassRedstoneProgress(), REDSTONE_TARGET).formatted(Formatting.GOLD);
            if (beforeRedstone < REDSTONE_TARGET && data.getSurveyorCompassRedstoneProgress() >= REDSTONE_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeCrafted != data.getSurveyorCompassCraftedProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.surveyor_compass.progress.crafted", data.getSurveyorCompassCraftedProgress(), CRAFTED_TARGET).formatted(Formatting.GOLD);
            if (beforeCrafted < CRAFTED_TARGET && data.getSurveyorCompassCraftedProgress() >= CRAFTED_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeReady != data.getSurveyorCompassPickaxeReadyProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.surveyor_compass.progress.pickaxe", data.getSurveyorCompassPickaxeReadyProgress(), PICKAXE_READY_TARGET).formatted(Formatting.GOLD);
            if (beforeReady < PICKAXE_READY_TARGET && data.getSurveyorCompassPickaxeReadyProgress() >= PICKAXE_READY_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }

        if (beforeStage != RelicQuestStage.READY && data.getSurveyorCompassQuestStage() == RelicQuestStage.READY) {
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.ready").formatted(Formatting.GOLD), false);
            completedStep = true;
        }

        if (actionbar != null) {
            player.sendMessage(actionbar, true);
        }
        if (actionbar != null || beforeStage != data.getSurveyorCompassQuestStage()) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
            refreshQuestUi(world, player);
        }
    }

    private static void completeQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.SURVEYORS_COMPASS));
        giveOrDrop(player, new ItemStack(Items.NETHERITE_INGOT, BONUS_INGOT_COUNT));
        data.setPendingSpecialOfferKind(null);
        data.setSurveyorCompassQuestStage(RelicQuestStage.COMPLETED);
        data.setSurveyorCompassRedstoneProgress(REDSTONE_TARGET);
        data.setSurveyorCompassCraftedProgress(CRAFTED_TARGET);
        data.setSurveyorCompassPickaxeReadyProgress(PICKAXE_READY_TARGET);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(title(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.surveyor_compass.completed.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.surveyor_compass.completed.2")).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.05f);
        refreshQuestUi(world, player);
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
        return Text.translatable("quest.village-quest.special.surveyor_compass.title");
    }
}
