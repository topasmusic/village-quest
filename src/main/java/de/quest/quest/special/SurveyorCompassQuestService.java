package de.quest.quest.special;

import com.mojang.datafixers.util.Pair;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.pilgrim.PilgrimContractType;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
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
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SurveyorCompassQuestService {
    public static final int REQUIRED_CRAFTING_REPUTATION = 200;

    private static final int REDSTONE_TARGET = 64;
    private static final int CRAFTED_TARGET = 1;
    private static final int PICKAXE_READY_TARGET = 1;
    private static final int BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_HORIZONTAL_STEP = 32;
    private static final int BIOME_VERTICAL_STEP = 64;
    private static final int STRUCTURE_SEARCH_RADIUS = 128;
    private static final int BONUS_INGOT_COUNT = 3;
    private static final long HOME_COOLDOWN_TICKS = 20L * 60L * 30L;
    private static final long HOME_CONFIRM_WINDOW_TICKS = 20L * 10L;
    private static final long HINT_COOLDOWN_TICKS = 20L * 8L;
    private static final TagKey<Structure> ROADMARK_VILLAGES = structureTag("roadmark_villages");
    private static final TagKey<Structure> ROADMARK_PILLAGER_OUTPOSTS = structureTag("roadmark_pillager_outposts");
    private static final TagKey<Structure> ROADMARK_WOODLAND_MANSIONS = structureTag("roadmark_woodland_mansions");
    private static final TagKey<Structure> ROADMARK_SWAMP_HUTS = structureTag("roadmark_swamp_huts");
    private static final TagKey<Structure> COMPASS_STRONGHOLDS = structureTag("compass_strongholds");
    private static final TagKey<Structure> COMPASS_TRIAL_CHAMBERS = structureTag("compass_trial_chambers");
    private static final TagKey<Structure> COMPASS_OCEAN_MONUMENTS = structureTag("compass_ocean_monuments");
    private static final TagKey<Structure> COMPASS_JUNGLE_TEMPLES = structureTag("compass_jungle_temples");
    private static final TagKey<Structure> COMPASS_MINESHAFTS = structureTag("compass_mineshafts");
    private static final TagKey<Structure> COMPASS_SHIPWRECKS = structureTag("compass_shipwrecks");
    private static final TagKey<Structure> COMPASS_RUINED_PORTALS = structureTag("compass_ruined_portals");

    private static final String FLAG_BIOME_MODES_UNLOCKED = "surveyor_compass_biomes_unlocked";
    private static final String FLAG_BIOME_CALIBRATION_ACTIVE = "surveyor_compass_biome_calibration_active";
    private static final String FLAG_BIOME_CALIBRATION_AVAILABLE_NOTIFIED = "surveyor_compass_biome_calibration_available_notified";
    private static final String FLAG_STRUCTURE_MODES_UNLOCKED = "surveyor_compass_structures_unlocked";
    private static final String FLAG_BIOMES_UNLOCK_NOTIFIED = "surveyor_compass_biomes_unlock_notified";
    private static final String FLAG_STRUCTURES_UNLOCK_NOTIFIED = "surveyor_compass_structures_unlock_notified";
    private static final String FLAG_PREFIX_BIOME_MARK = "surveyor_compass_biome_mark_";
    private static final String FLAG_PREFIX_ROADMARK = "surveyor_compass_roadmark_";

    private static final ConcurrentMap<UUID, HintStamp> LAST_HINTS = new ConcurrentHashMap<>();

    private record CompassTarget(BlockPos pos, Text label) {}
    private record HomeTarget(ServerWorld world, BlockPos pos, float yaw) {}
    private record HintCandidate(String key, Text message) {}
    private record HintStamp(String key, long worldTime) {}

    private enum CompassModeGroup {
        HOME,
        BIOME,
        STRUCTURE
    }

    private enum CompassMode {
        HOME("text.village-quest.special.surveyor_compass.mode.home", CompassModeGroup.HOME),
        FOREST("text.village-quest.special.surveyor_compass.mode.forest", CompassModeGroup.BIOME),
        PLAINS("text.village-quest.special.surveyor_compass.mode.plains", CompassModeGroup.BIOME),
        TAIGA("text.village-quest.special.surveyor_compass.mode.taiga", CompassModeGroup.BIOME),
        SNOWY_PLAINS("text.village-quest.special.surveyor_compass.mode.snowy_plains", CompassModeGroup.BIOME),
        MOUNTAIN("text.village-quest.special.surveyor_compass.mode.mountain", CompassModeGroup.BIOME),
        MEADOW("text.village-quest.special.surveyor_compass.mode.meadow", CompassModeGroup.BIOME),
        CHERRY_GROVE("text.village-quest.special.surveyor_compass.mode.cherry_grove", CompassModeGroup.BIOME),
        DESERT("text.village-quest.special.surveyor_compass.mode.desert", CompassModeGroup.BIOME),
        JUNGLE("text.village-quest.special.surveyor_compass.mode.jungle", CompassModeGroup.BIOME),
        FROZEN_PEAKS("text.village-quest.special.surveyor_compass.mode.frozen_peaks", CompassModeGroup.BIOME),
        SAVANNA("text.village-quest.special.surveyor_compass.mode.savanna", CompassModeGroup.BIOME),
        SWAMP("text.village-quest.special.surveyor_compass.mode.swamp", CompassModeGroup.BIOME),
        BADLANDS("text.village-quest.special.surveyor_compass.mode.badlands", CompassModeGroup.BIOME),
        BEACH("text.village-quest.special.surveyor_compass.mode.beach", CompassModeGroup.BIOME),
        RIVER("text.village-quest.special.surveyor_compass.mode.river", CompassModeGroup.BIOME),
        OCEAN("text.village-quest.special.surveyor_compass.mode.ocean", CompassModeGroup.BIOME),
        MUSHROOM_FIELDS("text.village-quest.special.surveyor_compass.mode.mushroom_fields", CompassModeGroup.BIOME),
        VILLAGE("text.village-quest.special.surveyor_compass.mode.village", CompassModeGroup.STRUCTURE),
        PILLAGER_OUTPOST("text.village-quest.special.surveyor_compass.mode.pillager_outpost", CompassModeGroup.STRUCTURE),
        STRONGHOLD("text.village-quest.special.surveyor_compass.mode.stronghold", CompassModeGroup.STRUCTURE),
        TRIAL_CHAMBER("text.village-quest.special.surveyor_compass.mode.trial_chamber", CompassModeGroup.STRUCTURE),
        WOODLAND_MANSION("text.village-quest.special.surveyor_compass.mode.woodland_mansion", CompassModeGroup.STRUCTURE),
        OCEAN_MONUMENT("text.village-quest.special.surveyor_compass.mode.ocean_monument", CompassModeGroup.STRUCTURE),
        JUNGLE_TEMPLE("text.village-quest.special.surveyor_compass.mode.jungle_temple", CompassModeGroup.STRUCTURE),
        SWAMP_HUT("text.village-quest.special.surveyor_compass.mode.swamp_hut", CompassModeGroup.STRUCTURE),
        MINESHAFT("text.village-quest.special.surveyor_compass.mode.mineshaft", CompassModeGroup.STRUCTURE),
        SHIPWRECK("text.village-quest.special.surveyor_compass.mode.shipwreck", CompassModeGroup.STRUCTURE),
        RUINED_PORTAL("text.village-quest.special.surveyor_compass.mode.ruined_portal", CompassModeGroup.STRUCTURE);

        private final String translationKey;
        private final CompassModeGroup group;

        CompassMode(String translationKey, CompassModeGroup group) {
            this.translationKey = translationKey;
            this.group = group;
        }

        public Text label() {
            return Text.translatable(translationKey);
        }

        public CompassModeGroup group() {
            return group;
        }
    }

    private enum CalibrationBiome {
        DESERT("desert", CompassMode.DESERT) {
            @Override
            boolean matches(ServerWorld world, BlockPos pos) {
                return isSpecificBiomeAt(world, pos, BiomeKeys.DESERT);
            }

            @Override
            CompassTarget locate(ServerWorld world, BlockPos origin) {
                return specificBiomeTarget(world, origin, BiomeKeys.DESERT, label());
            }
        },
        JUNGLE("jungle", CompassMode.JUNGLE) {
            @Override
            boolean matches(ServerWorld world, BlockPos pos) {
                return isBiomeIn(world, pos, BiomeTags.IS_JUNGLE);
            }

            @Override
            CompassTarget locate(ServerWorld world, BlockPos origin) {
                return biomeTarget(world, origin, BiomeTags.IS_JUNGLE, label());
            }
        },
        FROZEN_PEAKS("frozen_peaks", CompassMode.FROZEN_PEAKS) {
            @Override
            boolean matches(ServerWorld world, BlockPos pos) {
                return isSpecificBiomeAt(world, pos, BiomeKeys.FROZEN_PEAKS);
            }

            @Override
            CompassTarget locate(ServerWorld world, BlockPos origin) {
                return specificBiomeTarget(world, origin, BiomeKeys.FROZEN_PEAKS, label());
            }
        },
        MUSHROOM_FIELDS("mushroom_fields", CompassMode.MUSHROOM_FIELDS) {
            @Override
            boolean matches(ServerWorld world, BlockPos pos) {
                return isSpecificBiomeAt(world, pos, BiomeKeys.MUSHROOM_FIELDS);
            }

            @Override
            CompassTarget locate(ServerWorld world, BlockPos origin) {
                return specificBiomeTarget(world, origin, BiomeKeys.MUSHROOM_FIELDS, label());
            }
        };

        private final String id;
        private final CompassMode mode;

        CalibrationBiome(String id, CompassMode mode) {
            this.id = id;
            this.mode = mode;
        }

        abstract boolean matches(ServerWorld world, BlockPos pos);

        abstract CompassTarget locate(ServerWorld world, BlockPos origin);

        public Text label() {
            return mode.label();
        }

        public String progressFlagKey() {
            return FLAG_PREFIX_BIOME_MARK + id;
        }
    }

    private enum RoadmarkStructure {
        VILLAGE("village", CompassMode.VILLAGE, ROADMARK_VILLAGES),
        PILLAGER_OUTPOST("pillager_outpost", CompassMode.PILLAGER_OUTPOST, ROADMARK_PILLAGER_OUTPOSTS),
        WOODLAND_MANSION("woodland_mansion", CompassMode.WOODLAND_MANSION, ROADMARK_WOODLAND_MANSIONS),
        SWAMP_HUT("swamp_hut", CompassMode.SWAMP_HUT, ROADMARK_SWAMP_HUTS);

        private final String id;
        private final CompassMode mode;
        private final TagKey<Structure> structureTag;

        RoadmarkStructure(String id, CompassMode mode, TagKey<Structure> structureTag) {
            this.id = id;
            this.mode = mode;
            this.structureTag = structureTag;
        }

        public Text label() {
            return mode.label();
        }

        public String progressFlagKey() {
            return FLAG_PREFIX_ROADMARK + id;
        }

        public boolean matches(ServerWorld world, BlockPos pos) {
            return isInsideStructure(world, pos, structureTag);
        }

        public CompassTarget locate(ServerWorld world, BlockPos origin) {
            return structureTarget(world, origin, structureTag, mode.label());
        }
    }

    private SurveyorCompassQuestService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        migrateLegacyState(world, playerId, data);
        return data;
    }

    private static void migrateLegacyState(ServerWorld world, UUID playerId, PlayerQuestData data) {
        if (world == null || playerId == null || data == null) {
            return;
        }
        if (!data.hasMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED)
                && data.hasMilestoneFlag(FLAG_BIOMES_UNLOCK_NOTIFIED)
                && data.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.APIARY_CHARTER)
                && !data.hasMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE)) {
            data.setMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED, true);
            QuestState.get(world.getServer()).markDirty();
        }
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
        PilgrimService.refreshIfTrading(world, player);
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerQuestData data = data(world, player.getUuid());
            RelicQuestStage stage = data.getSurveyorCompassQuestStage();
            if (stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY) {
                tickCompassQuestProgress(world, player, data, stage);
            }
            maybeSendImprintHint(world, player);
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
            case COMPLETED -> {
                if (hasBiomeCalibrationReady(world, player.getUuid())) {
                    claimBiomeCalibration(world, player);
                    yield true;
                }
                if (hasBiomeCalibrationActive(world, player.getUuid())) {
                    showBiomeCalibrationProgress(player, data);
                    yield true;
                }
                yield false;
            }
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
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean acceptBiomeCalibration(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasBiomeCalibrationAvailable(world, player.getUuid())) {
            return false;
        }
        if (!hasCompassInInventory(player)) {
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass_biomes.compass_missing").formatted(Formatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE, true);
        markDirty(world);
        player.sendMessage(Texts.acceptedTitle(biomeCalibrationTitle(), Formatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showBiomeCalibrationProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean handleQuestMasterAccept(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        if (data(world, player.getUuid()).getSurveyorCompassQuestStage() != RelicQuestStage.COMPLETED) {
            return acceptQuest(world, player);
        }
        return acceptBiomeCalibration(world, player);
    }

    public static boolean handleQuestMasterClaim(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        if (data(world, player.getUuid()).getSurveyorCompassQuestStage() == RelicQuestStage.READY) {
            return claimFromQuestMaster(world, player);
        }
        return claimBiomeCalibration(world, player);
    }

    public static boolean isActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getSurveyorCompassQuestStage();
        return stage == RelicQuestStage.ACTIVE
                || stage == RelicQuestStage.READY
                || hasBiomeCalibrationActive(world, playerId);
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean hasBiomeModesUnlocked(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).hasMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED);
    }

    public static boolean hasStructureModesUnlocked(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).hasMilestoneFlag(FLAG_STRUCTURE_MODES_UNLOCKED);
    }

    public static boolean hasCompassInInventory(ServerPlayerEntity player) {
        return player != null
                && ModItems.SURVEYORS_COMPASS != null
                && DailyQuestService.countInventoryItem(player, ModItems.SURVEYORS_COMPASS) > 0;
    }

    public static boolean unlockStructureModes(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(FLAG_STRUCTURE_MODES_UNLOCKED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_STRUCTURE_MODES_UNLOCKED, true);
        markDirty(world);
        maybeNotifyStructureUnlock(world, player);
        return true;
    }

    public static void onVillageProjectUnlocked(ServerWorld world, ServerPlayerEntity player, VillageProjectType project) {
        if (world == null || player == null || project != VillageProjectType.APIARY_CHARTER) {
            return;
        }
        if (maybeNotifyBiomeCalibrationAvailable(world, player)) {
            refreshQuestUi(world, player);
        }
    }

    public static boolean isDiscovered(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.SURVEYOR_COMPASS)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.SURVEYOR_COMPASS
                || data.getSurveyorCompassQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        RelicQuestStage stage = data.getSurveyorCompassQuestStage();
        if (stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY) {
            return new SpecialQuestStatus(title(), progressLines(data));
        }
        if (hasBiomeCalibrationActive(world, playerId)) {
            return new SpecialQuestStatus(biomeCalibrationTitle(), biomeCalibrationProgressLines(data));
        }
        return null;
    }

    public static boolean hasBiomeCalibrationAvailable(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && isCompleted(world, playerId)
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.APIARY_CHARTER)
                && !hasBiomeModesUnlocked(world, playerId);
    }

    public static boolean hasBiomeCalibrationActive(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).hasMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE)
                && !hasBiomeModesUnlocked(world, playerId);
    }

    public static boolean hasBiomeCalibrationReady(ServerWorld world, UUID playerId) {
        return hasBiomeCalibrationActive(world, playerId) && biomeImprintCount(data(world, playerId)) >= CalibrationBiome.values().length;
    }

    public static List<Text> biomeCalibrationProgressLines(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        return biomeCalibrationProgressLines(data(world, playerId));
    }

    public static List<Text> roadmarkProgressLines(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        return roadmarkProgressLines(data(world, playerId));
    }

    public static boolean hasRoadmarksReady(ServerWorld world, ServerPlayerEntity player) {
        return world != null
                && player != null
                && roadmarkCount(data(world, player.getUuid())) >= RoadmarkStructure.values().length
                && hasCompassInInventory(player);
    }

    public static boolean isRoadmarkContractActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null || hasStructureModesUnlocked(world, playerId)) {
            return false;
        }
        return PilgrimContractType.ROADMARKS_FOR_THE_COMPASS == PilgrimContractType.fromId(data(world, playerId).getActivePilgrimContractId());
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        // Progress is granted on tracked redstone dust pickups.
    }

    public static void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (world == null || player == null || stack == null || count <= 0) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getSurveyorCompassQuestStage() != RelicQuestStage.ACTIVE || !stack.isOf(Items.REDSTONE)) {
            return;
        }

        int beforeRedstone = data.getSurveyorCompassRedstoneProgress();
        int beforeCrafted = data.getSurveyorCompassCraftedProgress();
        int beforeReady = data.getSurveyorCompassPickaxeReadyProgress();
        RelicQuestStage beforeStage = data.getSurveyorCompassQuestStage();
        data.setSurveyorCompassRedstoneProgress(Math.min(REDSTONE_TARGET, beforeRedstone + count));
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

    public static boolean claimBiomeCalibration(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasBiomeCalibrationReady(world, player.getUuid())) {
            return false;
        }
        if (!hasCompassInInventory(player)) {
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass_biomes.compass_missing").formatted(Formatting.RED), false);
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
        data.setMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE, false);
        data.setMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED, true);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(biomeCalibrationTitle(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.surveyor_compass_biomes.completed.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.surveyor_compass_biomes.completed.2")).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        maybeNotifyBiomeUnlock(world, player);
        refreshQuestUi(world, player);
        return true;
    }

    public static ActionResult useCompass(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (world == null || player == null || stack == null || stack.isEmpty()) {
            return ActionResult.PASS;
        }

        if (player.isSneaking()) {
            clearHomeConfirmation(world, data(world, player.getUuid()));
            CompassMode mode = cycleMode(world, player.getUuid());
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.mode", mode.label()).formatted(Formatting.GOLD), true);
            world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 1.15f);
            return ActionResult.SUCCESS;
        }

        if (tryImprintAtCurrentLocation(world, player)) {
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
        clearHomeConfirmation(world, data(world, player.getUuid()));
        int distance = (int) Math.round(Math.sqrt(player.getBlockPos().getSquaredDistance(target.pos())));
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.bound", target.label(), distance).formatted(Formatting.GOLD), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.15f);
        return ActionResult.SUCCESS;
    }

    private static void tickCompassQuestProgress(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, RelicQuestStage stage) {
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

    private static CompassMode currentMode(ServerWorld world, UUID playerId) {
        List<CompassMode> modes = availableModes(world, playerId);
        int index = Math.floorMod(data(world, playerId).getSurveyorCompassModeIndex(), modes.size());
        return modes.get(index);
    }

    private static CompassMode cycleMode(ServerWorld world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        List<CompassMode> modes = availableModes(world, playerId);
        int nextIndex = Math.floorMod(data.getSurveyorCompassModeIndex() + 1, modes.size());
        data.setSurveyorCompassModeIndex(nextIndex);
        markDirty(world);
        return modes.get(nextIndex);
    }

    private static List<CompassMode> availableModes(ServerWorld world, UUID playerId) {
        List<CompassMode> modes = new ArrayList<>();
        for (CompassMode mode : CompassMode.values()) {
            if (isModeUnlocked(world, playerId, mode)) {
                modes.add(mode);
            }
        }
        if (modes.isEmpty()) {
            return List.of(CompassMode.HOME);
        }
        return List.copyOf(modes);
    }

    private static boolean isModeUnlocked(ServerWorld world, UUID playerId, CompassMode mode) {
        if (mode == null) {
            return false;
        }
        return switch (mode.group()) {
            case HOME -> true;
            case BIOME -> hasBiomeModesUnlocked(world, playerId);
            case STRUCTURE -> hasStructureModesUnlocked(world, playerId);
        };
    }

    private static boolean tryImprintAtCurrentLocation(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.SURVEYORS_COMPASS == null) {
            return false;
        }
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        BlockPos pos = player.getBlockPos();

        if (hasBiomeCalibrationActive(world, playerId)) {
            CalibrationBiome biome = currentBiomeCandidate(world, pos, data);
            if (biome != null) {
                imprintBiome(world, player, data, biome);
                return true;
            }
        }

        if (isRoadmarkContractActive(world, playerId)) {
            RoadmarkStructure structure = currentRoadmarkCandidate(world, pos, data);
            if (structure != null) {
                imprintRoadmark(world, player, data, structure);
                return true;
            }
        }
        return false;
    }

    private static void imprintBiome(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, CalibrationBiome biome) {
        if (data.hasMilestoneFlag(biome.progressFlagKey())) {
            return;
        }
        data.setMilestoneFlag(biome.progressFlagKey(), true);
        clearHomeConfirmation(world, data);
        markDirty(world);
        LAST_HINTS.remove(player.getUuid());

        Text actionbar = Text.translatable(
                "quest.village-quest.special.surveyor_compass_biomes.progress.summary",
                biomeImprintCount(data),
                CalibrationBiome.values().length
        ).formatted(Formatting.GOLD);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.imprinted", biome.label()).formatted(Formatting.GOLD), false);
        player.sendMessage(actionbar, true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.1f);
        if (hasBiomeCalibrationReady(world, player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass_biomes.ready").formatted(Formatting.GOLD), false);
        }
        refreshQuestUi(world, player);
    }

    private static void imprintRoadmark(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data, RoadmarkStructure structure) {
        if (data.hasMilestoneFlag(structure.progressFlagKey())) {
            return;
        }
        data.setMilestoneFlag(structure.progressFlagKey(), true);
        clearHomeConfirmation(world, data);
        markDirty(world);
        LAST_HINTS.remove(player.getUuid());

        Text actionbar = Text.translatable(
                "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.1",
                roadmarkCount(data),
                RoadmarkStructure.values().length
        ).formatted(Formatting.GOLD);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.imprinted", structure.label()).formatted(Formatting.GOLD), false);
        player.sendMessage(actionbar, true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.0f);
        refreshQuestUi(world, player);
    }

    private static void maybeSendImprintHint(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasCompassInInventory(player)) {
            if (player != null) {
                LAST_HINTS.remove(player.getUuid());
            }
            return;
        }

        HintCandidate candidate = currentHintCandidate(world, player);
        if (candidate == null) {
            LAST_HINTS.remove(player.getUuid());
            return;
        }

        long now = world.getTime();
        HintStamp last = LAST_HINTS.get(player.getUuid());
        if (last != null && last.key().equals(candidate.key()) && (now - last.worldTime()) < HINT_COOLDOWN_TICKS) {
            return;
        }

        LAST_HINTS.put(player.getUuid(), new HintStamp(candidate.key(), now));
        player.sendMessage(candidate.message().copy().formatted(Formatting.AQUA), false);
    }

    private static HintCandidate currentHintCandidate(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        PlayerQuestData data = data(world, playerId);
        BlockPos pos = player.getBlockPos();

        if (hasBiomeCalibrationActive(world, playerId)) {
            CalibrationBiome biome = currentBiomeCandidate(world, pos, data);
            if (biome != null) {
                return new HintCandidate(
                        "biome:" + biome.name(),
                        Text.translatable("message.village-quest.special.surveyor_compass.imprint_hint.biome", biome.label())
                );
            }
        }

        if (isRoadmarkContractActive(world, playerId)) {
            RoadmarkStructure structure = currentRoadmarkCandidate(world, pos, data);
            if (structure != null) {
                return new HintCandidate(
                        "structure:" + structure.name(),
                        Text.translatable("message.village-quest.special.surveyor_compass.imprint_hint.structure", structure.label())
                );
            }
        }

        return null;
    }

    private static CalibrationBiome currentBiomeCandidate(ServerWorld world, BlockPos pos, PlayerQuestData data) {
        if (world == null || pos == null || data == null || world.getRegistryKey() != World.OVERWORLD) {
            return null;
        }
        for (CalibrationBiome biome : CalibrationBiome.values()) {
            if (!data.hasMilestoneFlag(biome.progressFlagKey()) && biome.matches(world, pos)) {
                return biome;
            }
        }
        return null;
    }

    private static RoadmarkStructure currentRoadmarkCandidate(ServerWorld world, BlockPos pos, PlayerQuestData data) {
        if (world == null || pos == null || data == null || world.getRegistryKey() != World.OVERWORLD) {
            return null;
        }
        for (RoadmarkStructure structure : RoadmarkStructure.values()) {
            if (!data.hasMilestoneFlag(structure.progressFlagKey()) && structure.matches(world, pos)) {
                return structure;
            }
        }
        return null;
    }

    private static CompassTarget locateTarget(ServerWorld world, BlockPos origin, CompassMode mode) {
        return switch (mode) {
            case HOME -> null;
            case FOREST -> biomeTarget(world, origin, BiomeTags.IS_FOREST, mode.label());
            case PLAINS -> specificBiomeTarget(world, origin, BiomeKeys.PLAINS, mode.label());
            case TAIGA -> biomeTarget(world, origin, BiomeTags.IS_TAIGA, mode.label());
            case SNOWY_PLAINS -> specificBiomeTarget(world, origin, BiomeKeys.SNOWY_PLAINS, mode.label());
            case MOUNTAIN -> biomeTarget(world, origin, BiomeTags.IS_MOUNTAIN, mode.label());
            case MEADOW -> specificBiomeTarget(world, origin, BiomeKeys.MEADOW, mode.label());
            case CHERRY_GROVE -> specificBiomeTarget(world, origin, BiomeKeys.CHERRY_GROVE, mode.label());
            case DESERT -> CalibrationBiome.DESERT.locate(world, origin);
            case JUNGLE -> CalibrationBiome.JUNGLE.locate(world, origin);
            case FROZEN_PEAKS -> CalibrationBiome.FROZEN_PEAKS.locate(world, origin);
            case SAVANNA -> biomeTarget(world, origin, BiomeTags.IS_SAVANNA, mode.label());
            case SWAMP -> specificBiomeTarget(world, origin, BiomeKeys.SWAMP, mode.label());
            case BADLANDS -> biomeTarget(world, origin, BiomeTags.IS_BADLANDS, mode.label());
            case BEACH -> biomeTarget(world, origin, BiomeTags.IS_BEACH, mode.label());
            case RIVER -> biomeTarget(world, origin, BiomeTags.IS_RIVER, mode.label());
            case OCEAN -> biomeTarget(world, origin, BiomeTags.IS_OCEAN, mode.label());
            case MUSHROOM_FIELDS -> CalibrationBiome.MUSHROOM_FIELDS.locate(world, origin);
            case VILLAGE -> RoadmarkStructure.VILLAGE.locate(world, origin);
            case PILLAGER_OUTPOST -> RoadmarkStructure.PILLAGER_OUTPOST.locate(world, origin);
            case STRONGHOLD -> structureTarget(world, origin, COMPASS_STRONGHOLDS, mode.label());
            case TRIAL_CHAMBER -> structureTarget(world, origin, COMPASS_TRIAL_CHAMBERS, mode.label());
            case WOODLAND_MANSION -> RoadmarkStructure.WOODLAND_MANSION.locate(world, origin);
            case OCEAN_MONUMENT -> structureTarget(world, origin, COMPASS_OCEAN_MONUMENTS, mode.label());
            case JUNGLE_TEMPLE -> structureTarget(world, origin, COMPASS_JUNGLE_TEMPLES, mode.label());
            case SWAMP_HUT -> RoadmarkStructure.SWAMP_HUT.locate(world, origin);
            case MINESHAFT -> structureTarget(world, origin, COMPASS_MINESHAFTS, mode.label());
            case SHIPWRECK -> structureTarget(world, origin, COMPASS_SHIPWRECKS, mode.label());
            case RUINED_PORTAL -> structureTarget(world, origin, COMPASS_RUINED_PORTALS, mode.label());
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
            clearHomeConfirmation(world, data);
            player.sendMessage(Text.translatable(
                    "message.village-quest.special.surveyor_compass.home_cooldown",
                    formatCooldown(cooldownUntil - now)
            ).formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        HomeTarget home = resolveHomeTarget(world.getServer(), player);
        if (home == null) {
            clearHomeConfirmation(world, data);
            player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.home_missing").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        if (data.getSurveyorCompassHomeConfirmUntil() <= now) {
            data.setSurveyorCompassHomeConfirmUntil(now + HOME_CONFIRM_WINDOW_TICKS);
            markDirty(world);
            player.sendMessage(Text.translatable(
                    "message.village-quest.special.surveyor_compass.home_confirm",
                    formatCooldown(HOME_CONFIRM_WINDOW_TICKS)
            ).formatted(Formatting.YELLOW), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 0.9f);
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
        clearHomeConfirmation(world, data);
        data.setSurveyorCompassHomeCooldownUntil(now + HOME_COOLDOWN_TICKS);
        markDirty(world);
        home.world().playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.85f, 1.0f);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.returned_home").formatted(Formatting.GOLD), false);
        return ActionResult.SUCCESS;
    }

    private static void clearHomeConfirmation(ServerWorld world, PlayerQuestData data) {
        if (data != null && data.getSurveyorCompassHomeConfirmUntil() > 0L) {
            data.setSurveyorCompassHomeConfirmUntil(0L);
            if (world != null) {
                markDirty(world);
            }
        }
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
                && RelicQuestProgressionService.isUnlocked(world, player.getUuid(), SpecialQuestKind.SURVEYOR_COMPASS);
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

    private static void showBiomeCalibrationProgress(ServerPlayerEntity player, PlayerQuestData data) {
        player.sendMessage(Texts.dailyTitle(biomeCalibrationTitle(), Formatting.GOLD), false);
        for (Text line : biomeCalibrationProgressLines(data)) {
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

    private static List<Text> biomeCalibrationProgressLines(PlayerQuestData data) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.special.surveyor_compass_biomes.progress.summary",
                        biomeImprintCount(data),
                        CalibrationBiome.values().length
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.special.surveyor_compass_biomes.progress.1",
                        hasImprint(data, CalibrationBiome.DESERT) ? 1 : 0,
                        1,
                        hasImprint(data, CalibrationBiome.JUNGLE) ? 1 : 0,
                        1
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.special.surveyor_compass_biomes.progress.2",
                        hasImprint(data, CalibrationBiome.FROZEN_PEAKS) ? 1 : 0,
                        1,
                        hasImprint(data, CalibrationBiome.MUSHROOM_FIELDS) ? 1 : 0,
                        1
                ).formatted(Formatting.GRAY)
        );
    }

    private static List<Text> roadmarkProgressLines(PlayerQuestData data) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.1",
                        roadmarkCount(data),
                        RoadmarkStructure.values().length
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.2",
                        hasRoadmark(data, RoadmarkStructure.VILLAGE) ? 1 : 0,
                        1,
                        hasRoadmark(data, RoadmarkStructure.PILLAGER_OUTPOST) ? 1 : 0,
                        1
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.3",
                        hasRoadmark(data, RoadmarkStructure.WOODLAND_MANSION) ? 1 : 0,
                        1,
                        hasRoadmark(data, RoadmarkStructure.SWAMP_HUT) ? 1 : 0,
                        1
                ).formatted(Formatting.GRAY)
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
        maybeNotifyBiomeCalibrationAvailable(world, player);
        refreshQuestUi(world, player);
    }

    private static boolean maybeNotifyBiomeCalibrationAvailable(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasBiomeCalibrationAvailable(world, player.getUuid())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(FLAG_BIOME_CALIBRATION_AVAILABLE_NOTIFIED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_BIOME_CALIBRATION_AVAILABLE_NOTIFIED, true);
        markDirty(world);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass_biomes.available").formatted(Formatting.GOLD), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.15f);
        return true;
    }

    private static boolean maybeNotifyBiomeUnlock(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasBiomeModesUnlocked(world, player.getUuid())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(FLAG_BIOMES_UNLOCK_NOTIFIED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_BIOMES_UNLOCK_NOTIFIED, true);
        markDirty(world);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.biomes_unlocked").formatted(Formatting.GOLD), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.75f, 1.1f);
        return true;
    }

    private static boolean maybeNotifyStructureUnlock(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || !hasStructureModesUnlocked(world, player.getUuid())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(FLAG_STRUCTURES_UNLOCK_NOTIFIED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_STRUCTURES_UNLOCK_NOTIFIED, true);
        markDirty(world);
        player.sendMessage(Text.translatable("message.village-quest.special.surveyor_compass.structures_unlocked").formatted(Formatting.GOLD), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.0f);
        return true;
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

    private static int biomeImprintCount(PlayerQuestData data) {
        int count = 0;
        for (CalibrationBiome biome : CalibrationBiome.values()) {
            if (hasImprint(data, biome)) {
                count++;
            }
        }
        return count;
    }

    private static int roadmarkCount(PlayerQuestData data) {
        int count = 0;
        for (RoadmarkStructure structure : RoadmarkStructure.values()) {
            if (hasRoadmark(data, structure)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasImprint(PlayerQuestData data, CalibrationBiome biome) {
        return data != null && biome != null && data.hasMilestoneFlag(biome.progressFlagKey());
    }

    private static boolean hasRoadmark(PlayerQuestData data, RoadmarkStructure structure) {
        return data != null && structure != null && data.hasMilestoneFlag(structure.progressFlagKey());
    }

    private static TagKey<Structure> structureTag(String path) {
        return TagKey.of(RegistryKeys.STRUCTURE, Identifier.of("village-quest", path));
    }

    private static boolean isSpecificBiomeAt(ServerWorld world, BlockPos pos, RegistryKey<Biome> biomeKey) {
        return world != null
                && pos != null
                && world.getBiome(pos).getKey().map(biomeKey::equals).orElse(false);
    }

    private static boolean isBiomeIn(ServerWorld world, BlockPos pos, TagKey<Biome> tag) {
        return world != null && pos != null && tag != null && world.getBiome(pos).isIn(tag);
    }

    private static boolean isInsideStructure(ServerWorld world, BlockPos pos, TagKey<Structure> tag) {
        if (world == null || pos == null || tag == null || world.getRegistryKey() != World.OVERWORLD) {
            return false;
        }
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, tag);
        return start != null && start.hasChildren();
    }

    private static Text title() {
        return Text.translatable("quest.village-quest.special.surveyor_compass.title");
    }

    private static Text biomeCalibrationTitle() {
        return Text.translatable("quest.village-quest.special.surveyor_compass_biomes.title");
    }
}
