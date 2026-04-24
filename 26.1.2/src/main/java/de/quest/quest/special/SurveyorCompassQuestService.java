package de.quest.quest.special;

import com.mojang.datafixers.util.Pair;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelData;
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
    private static final int LAPIS_TARGET = 64;
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

    public static void resetRuntimeState() {
        LAST_HINTS.clear();
    }

    private record CompassTarget(BlockPos pos, Component label) {}
    private record HomeTarget(ServerLevel world, BlockPos pos, float yaw) {}
    private record HintCandidate(String key, Component message) {}
    private record HintStamp(String key, long worldTime) {}

    private enum CompassModeGroup {
        HOME,
        BIOME,
        STRUCTURE,
        STORY
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
        RUINED_PORTAL("text.village-quest.special.surveyor_compass.mode.ruined_portal", CompassModeGroup.STRUCTURE),
        CARAVAN_DISTRESS("text.village-quest.special.surveyor_compass.mode.caravan_distress", CompassModeGroup.STORY),
        GUILD_CONVOY("text.village-quest.special.surveyor_compass.mode.guild_convoy", CompassModeGroup.STORY);

        private final String translationKey;
        private final CompassModeGroup group;

        CompassMode(String translationKey, CompassModeGroup group) {
            this.translationKey = translationKey;
            this.group = group;
        }

        public Component label() {
            return Component.translatable(translationKey);
        }

        public CompassModeGroup group() {
            return group;
        }
    }

    private enum CalibrationBiome {
        DESERT("desert", CompassMode.DESERT) {
            @Override
            boolean matches(ServerLevel world, BlockPos pos) {
                return isSpecificBiomeAt(world, pos, Biomes.DESERT);
            }

            @Override
            CompassTarget locate(ServerLevel world, BlockPos origin) {
                return specificBiomeTarget(world, origin, Biomes.DESERT, label());
            }
        },
        JUNGLE("jungle", CompassMode.JUNGLE) {
            @Override
            boolean matches(ServerLevel world, BlockPos pos) {
                return isBiomeIn(world, pos, BiomeTags.IS_JUNGLE);
            }

            @Override
            CompassTarget locate(ServerLevel world, BlockPos origin) {
                return biomeTarget(world, origin, BiomeTags.IS_JUNGLE, label());
            }
        },
        FROZEN_PEAKS("frozen_peaks", CompassMode.FROZEN_PEAKS) {
            @Override
            boolean matches(ServerLevel world, BlockPos pos) {
                return isSpecificBiomeAt(world, pos, Biomes.FROZEN_PEAKS);
            }

            @Override
            CompassTarget locate(ServerLevel world, BlockPos origin) {
                return specificBiomeTarget(world, origin, Biomes.FROZEN_PEAKS, label());
            }
        },
        MUSHROOM_FIELDS("mushroom_fields", CompassMode.MUSHROOM_FIELDS) {
            @Override
            boolean matches(ServerLevel world, BlockPos pos) {
                return isSpecificBiomeAt(world, pos, Biomes.MUSHROOM_FIELDS);
            }

            @Override
            CompassTarget locate(ServerLevel world, BlockPos origin) {
                return specificBiomeTarget(world, origin, Biomes.MUSHROOM_FIELDS, label());
            }
        };

        private final String id;
        private final CompassMode mode;

        CalibrationBiome(String id, CompassMode mode) {
            this.id = id;
            this.mode = mode;
        }

        abstract boolean matches(ServerLevel world, BlockPos pos);

        abstract CompassTarget locate(ServerLevel world, BlockPos origin);

        public Component label() {
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

        public Component label() {
            return mode.label();
        }

        public String progressFlagKey() {
            return FLAG_PREFIX_ROADMARK + id;
        }

        public boolean matches(ServerLevel world, BlockPos pos) {
            return isInsideStructure(world, pos, structureTag);
        }

        public CompassTarget locate(ServerLevel world, BlockPos origin) {
            return structureTarget(world, origin, structureTag, mode.label());
        }
    }

    private SurveyorCompassQuestService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        migrateLegacyState(world, playerId, data);
        return data;
    }

    private static void migrateLegacyState(ServerLevel world, UUID playerId, PlayerQuestData data) {
        if (world == null || playerId == null || data == null) {
            return;
        }
        if (!data.hasMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED)
                && data.hasMilestoneFlag(FLAG_BIOMES_UNLOCK_NOTIFIED)
                && data.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.APIARY_CHARTER)
                && !data.hasMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE)) {
            data.setMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED, true);
            QuestState.get(world.getServer()).setDirty();
        }
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
        PilgrimService.refreshIfTrading(world, player);
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerQuestData data = data(world, player.getUUID());
            RelicQuestStage stage = data.getSurveyorCompassQuestStage();
            if (stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY) {
                tickCompassQuestProgress(world, player, data, stage);
            }
            maybeSendImprintHint(world, player);
        }
    }

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
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
                if (hasBiomeCalibrationReady(world, player.getUUID())) {
                    claimBiomeCalibration(world, player);
                    yield true;
                }
                if (hasBiomeCalibrationActive(world, player.getUUID())) {
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

    public static boolean acceptQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.SURVEYORS_COMPASS == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetSurveyorCompassQuest();
        data.setSurveyorCompassQuestStage(RelicQuestStage.ACTIVE);
        data.setSurveyorCompassPickaxeBaseline(DailyQuestService.getCraftedStat(player, Items.NETHERITE_PICKAXE));
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(title(), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean acceptBiomeCalibration(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasBiomeCalibrationAvailable(world, player.getUUID())) {
            return false;
        }
        if (!hasCompassInInventory(player)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass_biomes.compass_missing").withStyle(ChatFormatting.RED), false);
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE, true);
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(biomeCalibrationTitle(), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showBiomeCalibrationProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean handleQuestMasterAccept(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        if (data(world, player.getUUID()).getSurveyorCompassQuestStage() != RelicQuestStage.COMPLETED) {
            return acceptQuest(world, player);
        }
        return acceptBiomeCalibration(world, player);
    }

    public static boolean handleQuestMasterClaim(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        if (data(world, player.getUUID()).getSurveyorCompassQuestStage() == RelicQuestStage.READY) {
            return claimFromQuestMaster(world, player);
        }
        return claimBiomeCalibration(world, player);
    }

    public static boolean isActive(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getSurveyorCompassQuestStage();
        return stage == RelicQuestStage.ACTIVE
                || stage == RelicQuestStage.READY
                || hasBiomeCalibrationActive(world, playerId);
    }

    public static boolean isCompleted(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean hasBiomeModesUnlocked(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).hasMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED);
    }

    public static boolean hasStructureModesUnlocked(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).hasMilestoneFlag(FLAG_STRUCTURE_MODES_UNLOCKED);
    }

    public static boolean hasCompassInInventory(ServerPlayer player) {
        return player != null
                && ModItems.SURVEYORS_COMPASS != null
                && DailyQuestService.countInventoryItem(player, ModItems.SURVEYORS_COMPASS) > 0;
    }

    public static boolean unlockStructureModes(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(FLAG_STRUCTURE_MODES_UNLOCKED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_STRUCTURE_MODES_UNLOCKED, true);
        markDirty(world);
        maybeNotifyStructureUnlock(world, player);
        return true;
    }

    public static void onVillageProjectUnlocked(ServerLevel world, ServerPlayer player, VillageProjectType project) {
        if (world == null || player == null || project != VillageProjectType.APIARY_CHARTER) {
            return;
        }
        if (maybeNotifyBiomeCalibrationAvailable(world, player)) {
            refreshQuestUi(world, player);
        }
    }

    public static boolean isDiscovered(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.SURVEYOR_COMPASS)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.SURVEYOR_COMPASS
                || data.getSurveyorCompassQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
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

    public static boolean hasBiomeCalibrationAvailable(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && isCompleted(world, playerId)
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.APIARY_CHARTER)
                && !hasBiomeModesUnlocked(world, playerId);
    }

    public static boolean hasBiomeCalibrationActive(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).hasMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE)
                && !hasBiomeModesUnlocked(world, playerId);
    }

    public static boolean hasBiomeCalibrationReady(ServerLevel world, UUID playerId) {
        return hasBiomeCalibrationActive(world, playerId) && biomeImprintCount(data(world, playerId)) >= CalibrationBiome.values().length;
    }

    public static List<Component> biomeCalibrationProgressLines(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        return biomeCalibrationProgressLines(data(world, playerId));
    }

    public static List<Component> roadmarkProgressLines(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        return roadmarkProgressLines(data(world, playerId));
    }

    public static boolean hasRoadmarksReady(ServerLevel world, ServerPlayer player) {
        return world != null
                && player != null
                && roadmarkCount(data(world, player.getUUID())) >= RoadmarkStructure.values().length
                && hasCompassInInventory(player);
    }

    public static boolean isRoadmarkContractActive(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null || hasStructureModesUnlocked(world, playerId)) {
            return false;
        }
        return PilgrimContractType.ROADMARKS_FOR_THE_COMPASS == PilgrimContractType.fromId(data(world, playerId).getActivePilgrimContractId());
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (world == null || player == null || state == null) {
            return;
        }
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (world == null
                || player == null
                || stack == null
                || count <= 0
                || (!stack.is(Items.REDSTONE) && !stack.is(Items.LAPIS_LAZULI))) {
            return;
        }

        PlayerQuestData data = data(world, player.getUUID());
        if (data.getSurveyorCompassQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }

        int beforeRedstone = data.getSurveyorCompassRedstoneProgress();
        int beforeLapis = data.getSurveyorCompassLapisProgress();
        int beforeCrafted = data.getSurveyorCompassCraftedProgress();
        int beforeReady = data.getSurveyorCompassPickaxeReadyProgress();
        RelicQuestStage beforeStage = data.getSurveyorCompassQuestStage();
        if (stack.is(Items.REDSTONE)) {
            data.setSurveyorCompassRedstoneProgress(Math.min(REDSTONE_TARGET, beforeRedstone + count));
        } else {
            data.setSurveyorCompassLapisProgress(Math.min(LAPIS_TARGET, beforeLapis + count));
        }
        updateReadyState(data);
        markDirty(world);
        sendProgressFeedback(world, player, data, beforeRedstone, beforeLapis, beforeCrafted, beforeReady, beforeStage);
    }

    public static boolean claimFromQuestMaster(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getSurveyorCompassQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        if (!consumeEligibleTurnInPickaxe(player)) {
            data.setSurveyorCompassPickaxeReadyProgress(0);
            data.setSurveyorCompassQuestStage(RelicQuestStage.ACTIVE);
            markDirty(world);
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.pickaxe_missing").withStyle(ChatFormatting.RED), false);
            refreshQuestUi(world, player);
            return false;
        }

        completeQuest(world, player, data);
        return true;
    }

    public static boolean claimBiomeCalibration(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasBiomeCalibrationReady(world, player.getUUID())) {
            return false;
        }
        if (!hasCompassInInventory(player)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass_biomes.compass_missing").withStyle(ChatFormatting.RED), false);
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        data.setMilestoneFlag(FLAG_BIOME_CALIBRATION_ACTIVE, false);
        data.setMilestoneFlag(FLAG_BIOME_MODES_UNLOCKED, true);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(biomeCalibrationTitle(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.surveyor_compass_biomes.completed.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.surveyor_compass_biomes.completed.2")).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        maybeNotifyBiomeUnlock(world, player);
        refreshQuestUi(world, player);
        return true;
    }

    public static InteractionResult useCompass(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (world == null || player == null || stack == null || stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            clearHomeConfirmation(world, data(world, player.getUUID()));
            CompassMode mode = cycleMode(world, player.getUUID());
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.mode", mode.label()).withStyle(ChatFormatting.GOLD), true);
            world.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.15f);
            return InteractionResult.SUCCESS;
        }

        if (tryImprintAtCurrentLocation(world, player)) {
            return InteractionResult.SUCCESS;
        }

        CompassMode mode = currentMode(world, player.getUUID());
        if (mode == CompassMode.HOME) {
            return returnHome(world, player, stack);
        }

        CompassTarget target = locateTarget(world, player.getUUID(), player.blockPosition(), mode);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.not_found", mode.label()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(world.dimension(), target.pos())),
                true
        ));
        clearHomeConfirmation(world, data(world, player.getUUID()));
        int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(target.pos())));
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.bound", target.label(), distance).withStyle(ChatFormatting.GOLD), true);
        world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.15f);
        return InteractionResult.SUCCESS;
    }

    private static void tickCompassQuestProgress(ServerLevel world, ServerPlayer player, PlayerQuestData data, RelicQuestStage stage) {
        int beforeRedstone = data.getSurveyorCompassRedstoneProgress();
        int beforeLapis = data.getSurveyorCompassLapisProgress();
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
            sendProgressFeedback(world, player, data, beforeRedstone, beforeLapis, beforeCrafted, beforeReady, beforeStage);
        }
    }

    private static CompassMode currentMode(ServerLevel world, UUID playerId) {
        List<CompassMode> modes = availableModes(world, playerId);
        int index = Math.floorMod(data(world, playerId).getSurveyorCompassModeIndex(), modes.size());
        return modes.get(index);
    }

    private static CompassMode cycleMode(ServerLevel world, UUID playerId) {
        PlayerQuestData data = data(world, playerId);
        List<CompassMode> modes = availableModes(world, playerId);
        int nextIndex = Math.floorMod(data.getSurveyorCompassModeIndex() + 1, modes.size());
        data.setSurveyorCompassModeIndex(nextIndex);
        markDirty(world);
        return modes.get(nextIndex);
    }

    private static List<CompassMode> availableModes(ServerLevel world, UUID playerId) {
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

    private static boolean isModeUnlocked(ServerLevel world, UUID playerId, CompassMode mode) {
        if (mode == null) {
            return false;
        }
        return switch (mode.group()) {
            case HOME -> true;
            case BIOME -> hasBiomeModesUnlocked(world, playerId);
            case STRUCTURE -> hasStructureModesUnlocked(world, playerId);
            case STORY -> switch (mode) {
                case CARAVAN_DISTRESS -> ShadowsTradeRoadEncounterService.currentDistressKind(world, playerId) == ShadowsTradeRoadEncounterService.RESCUE_KIND_FIRST_SIGNAL
                        || ShadowsTradeRoadEncounterService.currentDistressKind(world, playerId) == ShadowsTradeRoadEncounterService.RESCUE_KIND_HOLDING;
                case GUILD_CONVOY -> ShadowsTradeRoadEncounterService.currentDistressKind(world, playerId) == ShadowsTradeRoadEncounterService.RESCUE_KIND_FINAL;
                default -> false;
            };
        };
    }

    private static boolean tryImprintAtCurrentLocation(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.SURVEYORS_COMPASS == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        BlockPos pos = player.blockPosition();

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

    private static void imprintBiome(ServerLevel world, ServerPlayer player, PlayerQuestData data, CalibrationBiome biome) {
        if (data.hasMilestoneFlag(biome.progressFlagKey())) {
            return;
        }
        data.setMilestoneFlag(biome.progressFlagKey(), true);
        clearHomeConfirmation(world, data);
        markDirty(world);
        LAST_HINTS.remove(player.getUUID());

        Component actionbar = Component.translatable(
                "quest.village-quest.special.surveyor_compass_biomes.progress.summary",
                biomeImprintCount(data),
                CalibrationBiome.values().length
        ).withStyle(ChatFormatting.GOLD);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.imprinted", biome.label()).withStyle(ChatFormatting.GOLD), false);
        player.sendSystemMessage(actionbar, true);
        world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.1f);
        if (hasBiomeCalibrationReady(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass_biomes.ready").withStyle(ChatFormatting.GOLD), false);
        }
        refreshQuestUi(world, player);
    }

    private static void imprintRoadmark(ServerLevel world, ServerPlayer player, PlayerQuestData data, RoadmarkStructure structure) {
        if (data.hasMilestoneFlag(structure.progressFlagKey())) {
            return;
        }
        data.setMilestoneFlag(structure.progressFlagKey(), true);
        clearHomeConfirmation(world, data);
        markDirty(world);
        LAST_HINTS.remove(player.getUUID());

        Component actionbar = Component.translatable(
                "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.1",
                roadmarkCount(data),
                RoadmarkStructure.values().length
        ).withStyle(ChatFormatting.GOLD);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.imprinted", structure.label()).withStyle(ChatFormatting.GOLD), false);
        player.sendSystemMessage(actionbar, true);
        world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f);
        refreshQuestUi(world, player);
    }

    private static void maybeSendImprintHint(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasCompassInInventory(player)) {
            if (player != null) {
                LAST_HINTS.remove(player.getUUID());
            }
            return;
        }

        HintCandidate candidate = currentHintCandidate(world, player);
        if (candidate == null) {
            LAST_HINTS.remove(player.getUUID());
            return;
        }

        long now = world.getGameTime();
        HintStamp last = LAST_HINTS.get(player.getUUID());
        if (last != null && last.key().equals(candidate.key()) && (now - last.worldTime()) < HINT_COOLDOWN_TICKS) {
            return;
        }

        LAST_HINTS.put(player.getUUID(), new HintStamp(candidate.key(), now));
        player.sendSystemMessage(candidate.message().copy().withStyle(ChatFormatting.AQUA), false);
    }

    private static HintCandidate currentHintCandidate(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        PlayerQuestData data = data(world, playerId);
        BlockPos pos = player.blockPosition();

        if (hasBiomeCalibrationActive(world, playerId)) {
            CalibrationBiome biome = currentBiomeCandidate(world, pos, data);
            if (biome != null) {
                return new HintCandidate(
                        "biome:" + biome.name(),
                        Component.translatable("message.village-quest.special.surveyor_compass.imprint_hint.biome", biome.label())
                );
            }
        }

        if (isRoadmarkContractActive(world, playerId)) {
            RoadmarkStructure structure = currentRoadmarkCandidate(world, pos, data);
            if (structure != null) {
                return new HintCandidate(
                        "structure:" + structure.name(),
                        Component.translatable("message.village-quest.special.surveyor_compass.imprint_hint.structure", structure.label())
                );
            }
        }

        return null;
    }

    private static CalibrationBiome currentBiomeCandidate(ServerLevel world, BlockPos pos, PlayerQuestData data) {
        if (world == null || pos == null || data == null || world.dimension() != Level.OVERWORLD) {
            return null;
        }
        for (CalibrationBiome biome : CalibrationBiome.values()) {
            if (!data.hasMilestoneFlag(biome.progressFlagKey()) && biome.matches(world, pos)) {
                return biome;
            }
        }
        return null;
    }

    private static RoadmarkStructure currentRoadmarkCandidate(ServerLevel world, BlockPos pos, PlayerQuestData data) {
        if (world == null || pos == null || data == null || world.dimension() != Level.OVERWORLD) {
            return null;
        }
        for (RoadmarkStructure structure : RoadmarkStructure.values()) {
            if (!data.hasMilestoneFlag(structure.progressFlagKey()) && structure.matches(world, pos)) {
                return structure;
            }
        }
        return null;
    }

    private static CompassTarget locateTarget(ServerLevel world, UUID playerId, BlockPos origin, CompassMode mode) {
        return switch (mode) {
            case HOME -> null;
            case FOREST -> biomeTarget(world, origin, BiomeTags.IS_FOREST, mode.label());
            case PLAINS -> specificBiomeTarget(world, origin, Biomes.PLAINS, mode.label());
            case TAIGA -> biomeTarget(world, origin, BiomeTags.IS_TAIGA, mode.label());
            case SNOWY_PLAINS -> specificBiomeTarget(world, origin, Biomes.SNOWY_PLAINS, mode.label());
            case MOUNTAIN -> biomeTarget(world, origin, BiomeTags.IS_MOUNTAIN, mode.label());
            case MEADOW -> specificBiomeTarget(world, origin, Biomes.MEADOW, mode.label());
            case CHERRY_GROVE -> specificBiomeTarget(world, origin, Biomes.CHERRY_GROVE, mode.label());
            case DESERT -> CalibrationBiome.DESERT.locate(world, origin);
            case JUNGLE -> CalibrationBiome.JUNGLE.locate(world, origin);
            case FROZEN_PEAKS -> CalibrationBiome.FROZEN_PEAKS.locate(world, origin);
            case SAVANNA -> biomeTarget(world, origin, BiomeTags.IS_SAVANNA, mode.label());
            case SWAMP -> specificBiomeTarget(world, origin, Biomes.SWAMP, mode.label());
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
            case CARAVAN_DISTRESS, GUILD_CONVOY -> storyTarget(world, playerId, mode);
        };
    }

    private static CompassTarget biomeTarget(ServerLevel world, BlockPos origin, TagKey<Biome> tag, Component label) {
        Pair<BlockPos, Holder<Biome>> result = world.findClosestBiome3d(entry -> entry.is(tag), origin, BIOME_SEARCH_RADIUS, BIOME_HORIZONTAL_STEP, BIOME_VERTICAL_STEP);
        return result == null ? null : new CompassTarget(result.getFirst(), label);
    }

    private static CompassTarget specificBiomeTarget(ServerLevel world, BlockPos origin, ResourceKey<Biome> biomeKey, Component label) {
        Pair<BlockPos, Holder<Biome>> result = world.findClosestBiome3d(
                entry -> entry.unwrapKey().map(biomeKey::equals).orElse(false),
                origin,
                BIOME_SEARCH_RADIUS,
                BIOME_HORIZONTAL_STEP,
                BIOME_VERTICAL_STEP
        );
        return result == null ? null : new CompassTarget(result.getFirst(), label);
    }

    private static CompassTarget structureTarget(ServerLevel world, BlockPos origin, TagKey<Structure> tag, Component label) {
        BlockPos result = world.findNearestMapStructure(tag, origin, STRUCTURE_SEARCH_RADIUS, false);
        return result == null ? null : new CompassTarget(result, label);
    }

    private static CompassTarget storyTarget(ServerLevel world, UUID playerId, CompassMode mode) {
        if (world == null || playerId == null) {
            return null;
        }
        int kind = ShadowsTradeRoadEncounterService.currentDistressKind(world, playerId);
        if ((mode == CompassMode.CARAVAN_DISTRESS && kind == ShadowsTradeRoadEncounterService.RESCUE_KIND_FINAL)
                || (mode == CompassMode.GUILD_CONVOY && kind != ShadowsTradeRoadEncounterService.RESCUE_KIND_FINAL)) {
            return null;
        }
        ShadowsTradeRoadEncounterService.DistressTarget target = ShadowsTradeRoadEncounterService.currentDistressTarget(world, playerId);
        return target == null ? null : new CompassTarget(target.pos(), target.label());
    }

    private static InteractionResult returnHome(ServerLevel world, ServerPlayer player, ItemStack stack) {
        PlayerQuestData data = data(world, player.getUUID());
        long now = world.getServer().overworld().getGameTime();
        long cooldownUntil = data.getSurveyorCompassHomeCooldownUntil();
        if (cooldownUntil > now) {
            clearHomeConfirmation(world, data);
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.special.surveyor_compass.home_cooldown",
                    formatCooldown(cooldownUntil - now)
            ).withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }

        HomeTarget home = resolveHomeTarget(world.getServer(), player);
        if (home == null) {
            clearHomeConfirmation(world, data);
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.home_missing").withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }

        if (data.getSurveyorCompassHomeConfirmUntil() <= now) {
            data.setSurveyorCompassHomeConfirmUntil(now + HOME_CONFIRM_WINDOW_TICKS);
            markDirty(world);
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.special.surveyor_compass.home_confirm",
                    formatCooldown(HOME_CONFIRM_WINDOW_TICKS)
            ).withStyle(ChatFormatting.YELLOW), false);
            world.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 0.9f);
            return InteractionResult.SUCCESS;
        }

        stack.remove(DataComponents.LODESTONE_TRACKER);
        home.world().getChunk(home.pos().getX() >> 4, home.pos().getZ() >> 4);
        player.teleportTo(
                home.world(),
                home.pos().getX() + 0.5,
                home.pos().getY() + 0.1,
                home.pos().getZ() + 0.5,
                Set.of(),
                home.yaw(),
                player.getXRot(),
                true
        );
        clearHomeConfirmation(world, data);
        data.setSurveyorCompassHomeCooldownUntil(now + HOME_COOLDOWN_TICKS);
        markDirty(world);
        home.world().playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.85f, 1.0f);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.returned_home").withStyle(ChatFormatting.GOLD), false);
        return InteractionResult.SUCCESS;
    }

    private static void clearHomeConfirmation(ServerLevel world, PlayerQuestData data) {
        if (data != null && data.getSurveyorCompassHomeConfirmUntil() > 0L) {
            data.setSurveyorCompassHomeConfirmUntil(0L);
            if (world != null) {
                markDirty(world);
            }
        }
    }

    private static HomeTarget resolveHomeTarget(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return null;
        }

        ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        if (respawn != null && respawn.respawnData() != null) {
            LevelData.RespawnData spawnPoint = respawn.respawnData();
            ServerLevel spawnWorld = server.getLevel(spawnPoint.dimension());
            if (spawnWorld != null) {
                return new HomeTarget(spawnWorld, spawnPoint.pos(), spawnPoint.yaw());
            }
        }

        ServerLevel spawnWorld = server.findRespawnDimension();
        LevelData.RespawnData spawnPoint = server.getRespawnData();
        if (spawnWorld == null || spawnPoint == null) {
            return null;
        }
        return new HomeTarget(spawnWorld, spawnPoint.pos(), spawnPoint.yaw());
    }

    private static Component formatCooldown(long remainingTicks) {
        long totalSeconds = Math.max(1L, (remainingTicks + 19L) / 20L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L && seconds > 0L) {
            return Component.translatable("text.village-quest.duration.minutes_seconds", minutes, seconds);
        }
        if (minutes > 0L) {
            return Component.translatable("text.village-quest.duration.minutes", minutes);
        }
        return Component.translatable("text.village-quest.duration.seconds", seconds);
    }

    private static boolean shouldOfferQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        return data.getSurveyorCompassQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.SURVEYORS_COMPASS != null
                && DailyQuestService.openQuestStatus(world, player.getUUID()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUUID(), SpecialQuestKind.SURVEYOR_COMPASS);
    }

    private static void showOffer(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(SpecialQuestKind.SURVEYOR_COMPASS);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component accept = Component.translatable("text.village-quest.special.surveyor_compass.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/vq daily accept")));

        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.dailyTitle(title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.surveyor_compass.offer.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.surveyor_compass.offer.2")).append(Component.literal("\n\n\n"))
                .append(accept).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
    }

    private static void showProgress(ServerPlayer player, PlayerQuestData data) {
        player.sendSystemMessage(Texts.dailyTitle(title(), ChatFormatting.GOLD), false);
        for (Component line : progressLines(data)) {
            player.sendSystemMessage(line, false);
        }
    }

    private static void showBiomeCalibrationProgress(ServerPlayer player, PlayerQuestData data) {
        player.sendSystemMessage(Texts.dailyTitle(biomeCalibrationTitle(), ChatFormatting.GOLD), false);
        for (Component line : biomeCalibrationProgressLines(data)) {
            player.sendSystemMessage(line, false);
        }
    }

    private static List<Component> progressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable("quest.village-quest.special.surveyor_compass.progress.redstone", data.getSurveyorCompassRedstoneProgress(), REDSTONE_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.surveyor_compass.progress.lapis", data.getSurveyorCompassLapisProgress(), LAPIS_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.surveyor_compass.progress.crafted", data.getSurveyorCompassCraftedProgress(), CRAFTED_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.surveyor_compass.progress.pickaxe", data.getSurveyorCompassPickaxeReadyProgress(), PICKAXE_READY_TARGET).withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> biomeCalibrationProgressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable(
                        "quest.village-quest.special.surveyor_compass_biomes.progress.summary",
                        biomeImprintCount(data),
                        CalibrationBiome.values().length
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.special.surveyor_compass_biomes.progress.1",
                        hasImprint(data, CalibrationBiome.DESERT) ? 1 : 0,
                        1,
                        hasImprint(data, CalibrationBiome.JUNGLE) ? 1 : 0,
                        1
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.special.surveyor_compass_biomes.progress.2",
                        hasImprint(data, CalibrationBiome.FROZEN_PEAKS) ? 1 : 0,
                        1,
                        hasImprint(data, CalibrationBiome.MUSHROOM_FIELDS) ? 1 : 0,
                        1
                ).withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> roadmarkProgressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable(
                        "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.1",
                        roadmarkCount(data),
                        RoadmarkStructure.values().length
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.2",
                        hasRoadmark(data, RoadmarkStructure.VILLAGE) ? 1 : 0,
                        1,
                        hasRoadmark(data, RoadmarkStructure.PILLAGER_OUTPOST) ? 1 : 0,
                        1
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.3",
                        hasRoadmark(data, RoadmarkStructure.WOODLAND_MANSION) ? 1 : 0,
                        1,
                        hasRoadmark(data, RoadmarkStructure.SWAMP_HUT) ? 1 : 0,
                        1
                ).withStyle(ChatFormatting.GRAY)
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
                && data.getSurveyorCompassLapisProgress() >= LAPIS_TARGET
                && data.getSurveyorCompassCraftedProgress() >= CRAFTED_TARGET
                && data.getSurveyorCompassPickaxeReadyProgress() >= PICKAXE_READY_TARGET;
    }

    private static boolean hasEligibleTurnInPickaxe(ServerPlayer player) {
        return findEligibleTurnInSlot(player) >= 0;
    }

    private static int findEligibleTurnInSlot(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isEligibleTurnInPickaxe(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean consumeEligibleTurnInPickaxe(ServerPlayer player) {
        int slot = findEligibleTurnInSlot(player);
        if (slot < 0) {
            return false;
        }
        player.getInventory().getItem(slot).shrink(1);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static boolean isEligibleTurnInPickaxe(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(Items.NETHERITE_PICKAXE)
                && !stack.isEnchanted()
                && (!stack.isDamageableItem() || !stack.isDamaged());
    }

    private static void sendProgressFeedback(ServerLevel world,
                                             ServerPlayer player,
                                             PlayerQuestData data,
                                             int beforeRedstone,
                                             int beforeLapis,
                                             int beforeCrafted,
                                             int beforeReady,
                                             RelicQuestStage beforeStage) {
        Component actionbar = null;
        boolean completedStep = false;
        if (beforeRedstone != data.getSurveyorCompassRedstoneProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.surveyor_compass.progress.redstone", data.getSurveyorCompassRedstoneProgress(), REDSTONE_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforeRedstone < REDSTONE_TARGET && data.getSurveyorCompassRedstoneProgress() >= REDSTONE_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeLapis != data.getSurveyorCompassLapisProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.surveyor_compass.progress.lapis", data.getSurveyorCompassLapisProgress(), LAPIS_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforeLapis < LAPIS_TARGET && data.getSurveyorCompassLapisProgress() >= LAPIS_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeCrafted != data.getSurveyorCompassCraftedProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.surveyor_compass.progress.crafted", data.getSurveyorCompassCraftedProgress(), CRAFTED_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforeCrafted < CRAFTED_TARGET && data.getSurveyorCompassCraftedProgress() >= CRAFTED_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeReady != data.getSurveyorCompassPickaxeReadyProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.surveyor_compass.progress.pickaxe", data.getSurveyorCompassPickaxeReadyProgress(), PICKAXE_READY_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforeReady < PICKAXE_READY_TARGET && data.getSurveyorCompassPickaxeReadyProgress() >= PICKAXE_READY_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }

        if (beforeStage != RelicQuestStage.READY && data.getSurveyorCompassQuestStage() == RelicQuestStage.READY) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.ready").withStyle(ChatFormatting.GOLD), false);
            completedStep = true;
        }

        if (actionbar != null) {
            player.sendSystemMessage(actionbar, true);
        }
        if (actionbar != null || beforeStage != data.getSurveyorCompassQuestStage()) {
            world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
            refreshQuestUi(world, player);
        }
    }

    private static void completeQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.SURVEYORS_COMPASS));
        giveOrDrop(player, new ItemStack(Items.NETHERITE_INGOT, BONUS_INGOT_COUNT));
        data.setPendingSpecialOfferKind(null);
        data.setSurveyorCompassQuestStage(RelicQuestStage.COMPLETED);
        data.setSurveyorCompassRedstoneProgress(REDSTONE_TARGET);
        data.setSurveyorCompassLapisProgress(LAPIS_TARGET);
        data.setSurveyorCompassCraftedProgress(CRAFTED_TARGET);
        data.setSurveyorCompassPickaxeReadyProgress(PICKAXE_READY_TARGET);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.surveyor_compass.completed.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.surveyor_compass.completed.2")).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.05f);
        maybeNotifyBiomeCalibrationAvailable(world, player);
        refreshQuestUi(world, player);
    }

    private static boolean maybeNotifyBiomeCalibrationAvailable(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasBiomeCalibrationAvailable(world, player.getUUID())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(FLAG_BIOME_CALIBRATION_AVAILABLE_NOTIFIED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_BIOME_CALIBRATION_AVAILABLE_NOTIFIED, true);
        markDirty(world);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass_biomes.available").withStyle(ChatFormatting.GOLD), false);
        world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.15f);
        return true;
    }

    private static boolean maybeNotifyBiomeUnlock(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasBiomeModesUnlocked(world, player.getUUID())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(FLAG_BIOMES_UNLOCK_NOTIFIED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_BIOMES_UNLOCK_NOTIFIED, true);
        markDirty(world);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.biomes_unlocked").withStyle(ChatFormatting.GOLD), false);
        world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75f, 1.1f);
        return true;
    }

    private static boolean maybeNotifyStructureUnlock(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || !hasStructureModesUnlocked(world, player.getUUID())) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(FLAG_STRUCTURES_UNLOCK_NOTIFIED)) {
            return false;
        }
        data.setMilestoneFlag(FLAG_STRUCTURES_UNLOCK_NOTIFIED, true);
        markDirty(world);
        player.sendSystemMessage(Component.translatable("message.village-quest.special.surveyor_compass.structures_unlocked").withStyle(ChatFormatting.GOLD), false);
        world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
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
        return TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("village-quest", path));
    }

    private static boolean isSpecificBiomeAt(ServerLevel world, BlockPos pos, ResourceKey<Biome> biomeKey) {
        return world != null
                && pos != null
                && world.getBiome(pos).unwrapKey().map(biomeKey::equals).orElse(false);
    }

    private static boolean isBiomeIn(ServerLevel world, BlockPos pos, TagKey<Biome> tag) {
        return world != null && pos != null && tag != null && world.getBiome(pos).is(tag);
    }

    private static boolean isInsideStructure(ServerLevel world, BlockPos pos, TagKey<Structure> tag) {
        if (world == null || pos == null || tag == null || world.dimension() != Level.OVERWORLD) {
            return false;
        }
        StructureStart start = world.structureManager().getStructureWithPieceAt(pos, tag);
        return start != null && start.isValid();
    }

    private static Component title() {
        return Component.translatable("quest.village-quest.special.surveyor_compass.title");
    }

    private static Component biomeCalibrationTitle() {
        return Component.translatable("quest.village-quest.special.surveyor_compass_biomes.title");
    }
}
