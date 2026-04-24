package de.quest.content.story;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.entity.CaravanMerchantEntity;
import de.quest.entity.TraitorEntity;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModEntities;
import de.quest.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ShadowsTradeRoadEncounterService {
    public static final int RUMOR_UNLOCK_TARGET = 3;
    public static final int RESCUE_KIND_FIRST_SIGNAL = 1;
    public static final int RESCUE_KIND_HOLDING = 2;
    public static final int RESCUE_KIND_FINAL = 3;
    public static final int HOME_VILLAGER_TARGET = 6;
    public static final int REMOTE_VILLAGE_TARGET = 4;
    public static final int REMOTE_VILLAGER_TARGET = 2;
    public static final int HOLDING_TARGET_WINS = 2;

    private static final TagKey<Structure> ROADMARK_VILLAGES =
            TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("village-quest", "roadmark_villages"));

    private static final int MIN_RESCUE_DISTANCE = 500;
    private static final int MAX_RESCUE_DISTANCE = 1000;
    private static final int MAX_SPAWN_ATTEMPTS = 24;
    private static final int TRIGGER_RADIUS = 24;
    private static final int HOSTILE_SPAWN_MIN_RADIUS = 16;
    private static final int HOSTILE_SPAWN_MAX_RADIUS = 26;
    private static final int HOSTILE_LEASH_RADIUS = 45;
    private static final int HOSTILE_MAX_Y_DELTA = 14;
    private static final int LAST_HOSTILE_MARK_THRESHOLD = 2;
    private static final int LAST_HOSTILE_MARK_DELAY_TICKS = 20 * 25;
    private static final int LAST_HOSTILE_MARK_REPEAT_TICKS = 20 * 12;
    private static final int LAST_HOSTILE_MARK_DURATION_TICKS = 20 * 14;
    private static final int HOSTILE_SPAWN_GLOW_TICKS = 20 * 5;
    private static final int MERCHANT_RETREAT_TRIGGER_RADIUS = 7;
    private static final int MERCHANT_RETURN_RADIUS = 10;
    private static final int MERCHANT_MAX_RADIUS = 22;
    private static final int MERCHANT_RETREAT_DISTANCE = 9;
    private static final int MERCHANT_SPAWN_MIN_RADIUS = 3;
    private static final int MERCHANT_SPAWN_MAX_RADIUS = 9;
    private static final int MERCHANT_SPAWN_MIN_DISTANCE = 3;
    private static final double MERCHANT_RETREAT_SPEED = 1.0D;
    private static final double MERCHANT_RETURN_SPEED = 0.78D;
    private static final int AGGRO_TICKS = 20 * 7;
    private static final int NEXT_WAVE_DELAY_TICKS = 40;
    private static final int WAVE_PULSE_DELAY_TICKS = 20 * 3;
    private static final int WAVE_PULSE_SIZE = 2;
    private static final int COURIER_DESPAWN_TICKS = 20 * 60 * 5;
    private static final int SURVIVOR_DESPAWN_TICKS = 20 * 45;
    private static final String TAG_CARAVAN = "vq_trade_road_caravan";
    private static final String TAG_TRAITOR = "vq_trade_road_traitor";
    private static final String TAG_HOSTILE = "vq_trade_road_hostile";

    private static final ConcurrentMap<UUID, ActiveEncounter> ACTIVE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, UUID> LETTER_COURIERS = new ConcurrentHashMap<>();

    private ShadowsTradeRoadEncounterService() {}

    public record VillageMarker(String key, int centerX, int centerZ) {}
    public record DistressTarget(BlockPos pos, Component label) {}

    private record WaveSpec(int zombies, int skeletons, int spiders, int traitors) {}
    private enum HostileSpawnType {
        ZOMBIE,
        SKELETON,
        SPIDER,
        TRAITOR
    }
    private record EncounterSpec(int kind, int merchants, Component bossBarTitle, boolean finalConvoy, List<WaveSpec> waves) {}

    private static final class ActiveEncounter {
        private final UUID playerId;
        private final EncounterSpec spec;
        private final BlockPos anchorPos;
        private final ServerBossEvent bossBar;
        private final List<UUID> merchantIds = new ArrayList<>();
        private final List<UUID> guardMerchantIds = new ArrayList<>();
        private final List<UUID> hostileIds = new ArrayList<>();
        private final List<HostileSpawnType> pendingSpawns = new ArrayList<>();
        private final Map<UUID, Integer> aggroTicks = new HashMap<>();
        private int waveIndex;
        private int nextWaveDelayTicks;
        private int wavePulseDelayTicks;
        private int lastHostileMarkDelayTicks = LAST_HOSTILE_MARK_DELAY_TICKS;

        private ActiveEncounter(UUID playerId, EncounterSpec spec, BlockPos anchorPos) {
            this.playerId = playerId;
            this.spec = spec;
            this.anchorPos = anchorPos;
            this.bossBar = new ServerBossEvent(UUID.randomUUID(), spec.bossBarTitle(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            this.bossBar.setProgress(1.0f);
        }
    }

    private static final EncounterSpec FIRST_SIGNAL_SPEC = new EncounterSpec(
            RESCUE_KIND_FIRST_SIGNAL,
            3,
            Component.translatable("bossbar.village-quest.shadows.first_signal"),
            false,
            List.of(
                    new WaveSpec(4, 1, 1, 0),
                    new WaveSpec(5, 3, 2, 0)
            )
    );
    private static final EncounterSpec HOLDING_SPEC = new EncounterSpec(
            RESCUE_KIND_HOLDING,
            3,
            Component.translatable("bossbar.village-quest.shadows.holding"),
            false,
            List.of(
                    new WaveSpec(4, 2, 2, 0),
                    new WaveSpec(5, 3, 2, 0),
                    new WaveSpec(5, 3, 2, 0)
            )
    );
    private static final EncounterSpec FINAL_SPEC = new EncounterSpec(
            RESCUE_KIND_FINAL,
            6,
            Component.translatable("bossbar.village-quest.shadows.final_convoy"),
            true,
            List.of(
                    new WaveSpec(5, 3, 2, 0),
                    new WaveSpec(6, 4, 3, 0),
                    new WaveSpec(8, 4, 3, 0),
                    new WaveSpec(0, 0, 0, 3)
            )
    );

    public static void resetRuntimeState() {
        ACTIVE.values().forEach(encounter -> encounter.bossBar.removeAllPlayers());
        ACTIVE.clear();
        LETTER_COURIERS.clear();
    }

    public static int despawnAll(ServerLevel world) {
        if (world == null) {
            return 0;
        }
        int removed = 0;
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (!entity.isAlive()) {
                continue;
            }
            if (entity instanceof CaravanMerchantEntity
                    || entity instanceof TraitorEntity
                    || entity.entityTags().contains(TAG_HOSTILE)) {
                targets.add(entity);
            }
        }
        for (Entity entity : targets) {
            entity.discard();
            removed++;
        }
        resetRuntimeState();
        return removed;
    }

    public static boolean hasUnlockPrerequisites(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.WATCH_BELL)
                && PilgrimContractService.completedCombatContractCount(world, playerId) >= RUMOR_UNLOCK_TARGET;
    }

    public static int completedRumorCount(ServerLevel world, UUID playerId) {
        return PilgrimContractService.completedCombatContractCount(world, playerId);
    }

    public static boolean hasCompass(ServerPlayer player) {
        if (player == null || ModItems.SURVEYORS_COMPASS == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.SURVEYORS_COMPASS)) {
                return true;
            }
        }
        return player.getOffhandItem().is(ModItems.SURVEYORS_COMPASS);
    }

    public static VillageMarker currentVillage(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null || world.dimension() != Level.OVERWORLD) {
            return null;
        }
        StructureStart start = world.structureManager().getStructureWithPieceAt(pos, ROADMARK_VILLAGES);
        if (start == null || !start.isValid()) {
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        return new VillageMarker(centerX + "_" + centerZ, centerX, centerZ);
    }

    public static boolean hasHomeVillage(ServerLevel world, UUID playerId) {
        return playerId != null && (StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGERS) > 0
                || StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_X) != 0
                || StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_Z) != 0);
    }

    public static void bindHomeVillage(ServerLevel world, UUID playerId, VillageMarker village) {
        if (world == null || playerId == null || village == null) {
            return;
        }
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_X, village.centerX());
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_Z, village.centerZ());
    }

    public static boolean isHomeVillage(ServerLevel world, UUID playerId, VillageMarker village) {
        if (world == null || playerId == null || village == null || !hasHomeVillage(world, playerId)) {
            return false;
        }
        return StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_X) == village.centerX()
                && StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_Z) == village.centerZ();
    }

    public static boolean isDistressModeAvailable(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        if (ACTIVE.containsKey(playerId)) {
            return true;
        }
        StoryArcType activeArc = StoryQuestService.activeArcType(world, playerId);
        if (activeArc != StoryArcType.SHADOWS_ON_THE_TRADE_ROAD) {
            return false;
        }
        int kind = scheduledEncounterKind(world, playerId);
        return kind == RESCUE_KIND_FIRST_SIGNAL
                || kind == RESCUE_KIND_HOLDING
                || kind == RESCUE_KIND_FINAL;
    }

    public static DistressTarget currentDistressTarget(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        ActiveEncounter encounter = ACTIVE.get(playerId);
        if (encounter != null) {
            return new DistressTarget(encounter.anchorPos, distressLabel(encounter.spec.kind));
        }
        int x = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_X);
        int z = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_Z);
        int kind = scheduledEncounterKind(world, playerId);
        if (kind == 0) {
            return null;
        }
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new DistressTarget(new BlockPos(x, y, z), distressLabel(kind));
    }

    public static int currentDistressKind(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        ActiveEncounter encounter = ACTIVE.get(playerId);
        if (encounter != null) {
            return encounter.spec.kind();
        }
        return scheduledEncounterKind(world, playerId);
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StoryArcType activeArc = StoryQuestService.activeArcType(world, player.getUUID());
            if (activeArc != StoryArcType.SHADOWS_ON_THE_TRADE_ROAD) {
                cleanupPlayerRuntime(world, player.getUUID(), false);
                continue;
            }

            int chapterIndex = StoryQuestService.chapterIndex(world, player.getUUID(), StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
            if (chapterIndex == 2) {
                tickRescueChapter(world, player, StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS, 1, RESCUE_KIND_FIRST_SIGNAL, 0);
            } else if (chapterIndex == 3) {
                tickRescueChapter(world, player, StoryQuestKeys.SHADOWS_HOLDING_WINS, HOLDING_TARGET_WINS, RESCUE_KIND_HOLDING, 0);
            } else if (chapterIndex == 4) {
                ensureCourier(world, player);
            } else if (chapterIndex == 5) {
                tickRescueChapter(world, player, StoryQuestKeys.SHADOWS_FINAL_WON, 1, RESCUE_KIND_FINAL, 2);
            } else {
                cleanupPlayerRuntime(world, player.getUUID(), false);
                clearCourier(world, player.getUUID());
            }
        }
    }

    public static void onFirstSignalAccepted(ServerLevel world, ServerPlayer player) {
        scheduleEncounter(world, player, RESCUE_KIND_FIRST_SIGNAL, 0);
    }

    public static void onHoldingAccepted(ServerLevel world, ServerPlayer player) {
        scheduleEncounter(world, player, RESCUE_KIND_HOLDING, 0);
    }

    public static void onLetterAccepted(ServerLevel world, ServerPlayer player) {
        ensureCourier(world, player);
    }

    public static void onFinalAccepted(ServerLevel world, ServerPlayer player) {
        scheduleEncounter(world, player, RESCUE_KIND_FINAL, 2);
    }

    public static List<Component> rescueProgressLines(ServerLevel world, UUID playerId, String winsKey, int targetWins, boolean finalConvoy) {
        List<Component> lines = new ArrayList<>();
        if (world == null || playerId == null) {
            return lines;
        }
        lines.add(Component.translatable(
                finalConvoy
                        ? "quest.village-quest.story.shadows_on_the_trade_road.chapter_6.progress.1"
                        : "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.count",
                StoryQuestService.getQuestInt(world, playerId, winsKey),
                targetWins
        ).withStyle(ChatFormatting.GRAY));

        ActiveEncounter encounter = ACTIVE.get(playerId);
        if (encounter != null) {
            List<Mob> hostiles = livingHostiles(world, encounter);
            int remainingHostiles = hostiles.size() + encounter.pendingSpawns.size();
            lines.add(Component.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wave",
                    Math.max(1, encounter.waveIndex),
                    encounter.spec.waves().size()
            ).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.hostiles",
                    remainingHostiles
            ).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.survivors",
                    livingMerchants(world, encounter).size(),
                    encounter.spec.merchants()
            ).withStyle(ChatFormatting.GRAY));
            return List.copyOf(lines);
        }

        int targetDay = targetDay(world, playerId, finalConvoy);
        if (targetDay <= 0) {
            lines.add(Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.scouting").withStyle(ChatFormatting.GRAY));
            return List.copyOf(lines);
        }

        int nightsRemaining = Math.max(0, targetDay - currentWorldDay(world));
        if (nightsRemaining > 0) {
            lines.add(Component.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.chapter_6.progress.2",
                    nightsRemaining
            ).withStyle(ChatFormatting.GRAY));
        } else if (!isNight(world)) {
            lines.add(Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wait_night").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.compass").withStyle(ChatFormatting.GRAY));
        }
        return List.copyOf(lines);
    }

    public static List<Component> letterProgressLines(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_LETTER_RECEIVED) <= 0) {
            return List.of(Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.1").withStyle(ChatFormatting.GRAY));
        }
        return List.of(
                Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.2").withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.3",
                        player != null && hasGuildWarningLetter(player) ? 1 : 0,
                        1
                ).withStyle(ChatFormatting.GRAY)
        );
    }

    public static boolean handleCourierInteraction(ServerLevel world, ServerPlayer player, Entity entity) {
        if (!(entity instanceof CaravanMerchantEntity merchant) || world == null || player == null) {
            return false;
        }
        UUID expected = LETTER_COURIERS.get(player.getUUID());
        if (expected == null || !expected.equals(merchant.getUUID())) {
            return false;
        }
        if (StoryQuestService.getQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_LETTER_RECEIVED) <= 0) {
            giveOrDrop(player, createGuildWarningLetter());
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_LETTER_RECEIVED, 1);
            player.sendSystemMessage(Component.translatable("message.village-quest.story.shadows_on_the_trade_road.courier.1").withStyle(ChatFormatting.GOLD), false);
            player.sendSystemMessage(Component.translatable("message.village-quest.story.shadows_on_the_trade_road.courier.2").withStyle(ChatFormatting.GRAY), false);
            merchant.setDespawnTicks(20 * 10);
            merchant.setCourier(false);
            refreshQuestUi(world, player);
        } else {
            VillagerDialogueService.sendDialogue(player, merchant, Component.translatable("message.village-quest.story.shadows_on_the_trade_road.courier.3"));
        }
        return true;
    }

    public static boolean hasGuildWarningLetter(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isGuildWarningLetter(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return isGuildWarningLetter(player.getOffhandItem());
    }

    public static boolean consumeGuildWarningLetter(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isGuildWarningLetter(player.getInventory().getItem(slot))) {
                player.getInventory().getItem(slot).shrink(1);
                player.containerMenu.broadcastChanges();
                return true;
            }
        }
        if (isGuildWarningLetter(player.getOffhandItem())) {
            player.getOffhandItem().shrink(1);
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    public static boolean adminUnlockForTesting(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        boolean changed = false;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        if (!VillageProjectService.isUnlocked(world, player.getUUID(), VillageProjectType.WATCH_BELL)) {
            VillageProjectService.unlock(world, player.getUUID(), VillageProjectType.WATCH_BELL);
            changed = true;
        }
        if (ModItems.SURVEYORS_COMPASS != null && !hasCompass(player)) {
            giveOrDrop(player, new ItemStack(ModItems.SURVEYORS_COMPASS));
            changed = true;
        }
        if (SurveyorCompassQuestService.unlockStructureModes(world, player)) {
            changed = true;
        }
        changed |= setCombatRumorTestFlags(world, player.getUUID());
        if (!data.getStoryDiscovered().contains(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id())) {
            data.setStoryDiscovered(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id(), true);
            changed = true;
        }
        QuestState.get(world.getServer()).setDirty();
        refreshQuestUi(world, player);
        return changed;
    }

    public static boolean adminPrepareEncounterTest(ServerLevel world, ServerPlayer player, boolean finalConvoy) {
        if (world == null || player == null) {
            return false;
        }
        adminUnlockForTesting(world, player);
        adminResetPlayer(world, player.getUUID());
        StoryQuestService.adminResetStoryState(world, player.getUUID());
        QuestState.get(world.getServer()).getPlayerData(player.getUUID()).setStoryChapterProgress(
                StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id(),
                finalConvoy ? 5 : 2
        );
        boolean accepted = StoryQuestService.acceptQuest(world, player, StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
        if (accepted && finalConvoy) {
            scheduleEncounter(world, player, RESCUE_KIND_FINAL, 0);
            refreshQuestUi(world, player);
        }
        return accepted;
    }

    public static void adminResetPlayer(ServerLevel world, UUID playerId) {
        cleanupPlayerRuntime(world, playerId, false);
        clearCourier(world, playerId);
    }

    private static boolean setCombatRumorTestFlags(ServerLevel world, UUID playerId) {
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        boolean changed = false;
        String[] ids = new String[] {
                "quench_for_the_hall",
                "wool_before_rain",
                "tracks_in_the_dark"
        };
        for (String id : ids) {
            String flag = "pilgrim_contract_completed." + id;
            if (!data.hasMilestoneFlag(flag)) {
                data.setMilestoneFlag(flag, true);
                changed = true;
            }
        }
        if (changed) {
            QuestState.get(world.getServer()).setDirty();
        }
        return changed;
    }

    private static void tickRescueChapter(ServerLevel world,
                                          ServerPlayer player,
                                          String winsKey,
                                          int targetWins,
                                          int kind,
                                          int nightsUntilFirstRun) {
        UUID playerId = player.getUUID();
        if (StoryQuestService.getQuestInt(world, playerId, winsKey) >= targetWins) {
            cleanupPlayerRuntime(world, playerId, true);
            if (kind != RESCUE_KIND_FINAL) {
                clearScheduledEncounter(world, playerId);
            }
            return;
        }

        ActiveEncounter encounter = ACTIVE.get(playerId);
        if (encounter != null) {
            tickActiveEncounter(world, player, encounter, winsKey, targetWins);
            return;
        }

        DistressTarget distressTarget = currentDistressTarget(world, playerId);
        if (distressTarget == null) {
            scheduleEncounter(world, player, kind, nightsUntilFirstRun);
            return;
        }

        int targetDay = targetDay(world, playerId, kind == RESCUE_KIND_FINAL);
        if (!isNight(world) || currentWorldDay(world) < targetDay || player.level() != world) {
            return;
        }
        if (player.blockPosition().distSqr(distressTarget.pos()) > (double) (TRIGGER_RADIUS * TRIGGER_RADIUS)) {
            return;
        }

        ActiveEncounter created = spawnEncounter(world, player, kind, distressTarget.pos());
        if (created != null) {
            ACTIVE.put(playerId, created);
            player.sendSystemMessage(Component.translatable(
                    kind == RESCUE_KIND_FINAL
                            ? "message.village-quest.story.shadows_on_the_trade_road.final_found"
                            : "message.village-quest.story.shadows_on_the_trade_road.rescue_found"
            ).withStyle(ChatFormatting.GOLD), false);
            world.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.9f, 0.9f);
        }
    }

    private static ActiveEncounter spawnEncounter(ServerLevel world, ServerPlayer player, int kind, BlockPos anchorPos) {
        EncounterSpec spec = switch (kind) {
            case RESCUE_KIND_FIRST_SIGNAL -> FIRST_SIGNAL_SPEC;
            case RESCUE_KIND_HOLDING -> HOLDING_SPEC;
            case RESCUE_KIND_FINAL -> FINAL_SPEC;
            default -> null;
        };
        if (spec == null || world == null || player == null || anchorPos == null) {
            return null;
        }

        ActiveEncounter encounter = new ActiveEncounter(player.getUUID(), spec, anchorPos);
        int guardCount = spec.merchants() / 3;
        List<BlockPos> merchantPositions = new ArrayList<>();
        for (int i = 0; i < spec.merchants(); i++) {
            BlockPos merchantPos = findMerchantSpawn(world, anchorPos, merchantPositions);
            CaravanMerchantEntity merchant = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
            moveEntityTo(merchant, merchantPos.getX() + 0.5, merchantPos.getY(), merchantPos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0f, 0.0f);
            merchant.setHealth(merchant.getMaxHealth());
            merchant.setDespawnTicks(SURVIVOR_DESPAWN_TICKS);
            boolean guard = i < guardCount;
            merchant.refreshEncounterControl(guard);
            merchant.addTag(TAG_CARAVAN);
            merchant.addTag(playerTag(player.getUUID()));
            if (!world.noCollision(merchant) || !world.addFreshEntity(merchant)) {
                continue;
            }
            encounter.merchantIds.add(merchant.getUUID());
            merchantPositions.add(merchantPos);
            if (guard) {
                encounter.guardMerchantIds.add(merchant.getUUID());
            }
        }
        if (encounter.merchantIds.isEmpty()) {
            return null;
        }
        startNextWave(world, encounter);
        return encounter;
    }

    private static void tickActiveEncounter(ServerLevel world,
                                            ServerPlayer player,
                                            ActiveEncounter encounter,
                                            String winsKey,
                                            int targetWins) {
        List<CaravanMerchantEntity> merchants = livingMerchants(world, encounter);
        if (merchants.isEmpty()) {
            failEncounter(world, player, encounter, winsKey, targetWins);
            return;
        }

        refreshEncounterMerchants(merchants);
        updateBossBar(world, player, encounter, merchants);
        List<Mob> hostiles = livingHostiles(world, encounter);
        if (!encounter.pendingSpawns.isEmpty()) {
            if (encounter.wavePulseDelayTicks > 0) {
                encounter.wavePulseDelayTicks--;
            } else {
                spawnWavePulse(world, encounter);
                hostiles = livingHostiles(world, encounter);
            }
        }
        hostiles = maintainHostileSafety(world, player, encounter, hostiles);
        tickCaravanBehavior(world, encounter, merchants, hostiles);
        if (!hostiles.isEmpty()) {
            retargetHostiles(world, player, encounter, merchants, hostiles);
        }
        if (!hostiles.isEmpty() || !encounter.pendingSpawns.isEmpty()) {
            return;
        }

        if (encounter.waveIndex < encounter.spec.waves().size()) {
            if (encounter.nextWaveDelayTicks > 0) {
                encounter.nextWaveDelayTicks--;
                return;
            }
            startNextWave(world, encounter);
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.story.shadows_on_the_trade_road.wave",
                    encounter.waveIndex,
                    encounter.spec.waves().size()
            ).withStyle(ChatFormatting.GOLD), true);
            return;
        }

        succeedEncounter(world, player, encounter, winsKey, targetWins);
    }

    private static List<Mob> maintainHostileSafety(ServerLevel world,
                                                        ServerPlayer player,
                                                        ActiveEncounter encounter,
                                                        List<Mob> hostiles) {
        boolean relocated = false;
        for (Mob hostile : hostiles) {
            if (shouldLeashHostile(encounter, hostile)) {
                relocateHostileToEncounter(world, encounter, hostile);
                relocated = true;
            }
        }
        if (relocated) {
            hostiles = livingHostiles(world, encounter);
        }

        if (!encounter.pendingSpawns.isEmpty() || hostiles.size() > LAST_HOSTILE_MARK_THRESHOLD) {
            encounter.lastHostileMarkDelayTicks = LAST_HOSTILE_MARK_DELAY_TICKS;
            return hostiles;
        }
        if (hostiles.isEmpty()) {
            encounter.lastHostileMarkDelayTicks = LAST_HOSTILE_MARK_DELAY_TICKS;
            return hostiles;
        }
        if (encounter.lastHostileMarkDelayTicks > 0) {
            encounter.lastHostileMarkDelayTicks--;
            return hostiles;
        }

        for (Mob hostile : hostiles) {
            hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, LAST_HOSTILE_MARK_DURATION_TICKS, 0), player);
        }
        encounter.lastHostileMarkDelayTicks = LAST_HOSTILE_MARK_REPEAT_TICKS;
        return hostiles;
    }

    private static boolean shouldLeashHostile(ActiveEncounter encounter, Mob hostile) {
        if (encounter == null || hostile == null) {
            return false;
        }
        BlockPos hostilePos = hostile.blockPosition();
        return hostilePos.distSqr(encounter.anchorPos) > (double) (HOSTILE_LEASH_RADIUS * HOSTILE_LEASH_RADIUS)
                || Math.abs(hostilePos.getY() - encounter.anchorPos.getY()) > HOSTILE_MAX_Y_DELTA;
    }

    private static void relocateHostileToEncounter(ServerLevel world, ActiveEncounter encounter, Mob hostile) {
        if (world == null || encounter == null || hostile == null) {
            return;
        }
        BlockPos spawnPos = findRingSpawn(world, encounter.anchorPos, HOSTILE_SPAWN_MIN_RADIUS, HOSTILE_SPAWN_MAX_RADIUS);
        moveEntityTo(hostile, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0f, 0.0f);
        hostile.setDeltaMovement(0.0, 0.0, 0.0);
        hostile.getNavigation().stop();
        encounter.aggroTicks.remove(hostile.getUUID());
    }

    private static void tickCaravanBehavior(ServerLevel world,
                                            ActiveEncounter encounter,
                                            List<CaravanMerchantEntity> merchants,
                                            List<Mob> hostiles) {
        if (world == null || encounter == null || merchants == null) {
            return;
        }
        for (CaravanMerchantEntity merchant : merchants) {
            boolean guard = encounter.guardMerchantIds.contains(merchant.getUUID());
            merchant.refreshEncounterControl(guard);

            if (merchant.blockPosition().distSqr(encounter.anchorPos) > (double) (MERCHANT_MAX_RADIUS * MERCHANT_MAX_RADIUS)) {
                moveMerchantTo(merchant, encounter.anchorPos, MERCHANT_RETURN_SPEED);
                continue;
            }

            Mob threat = nearestHostile(merchant, hostiles);
            if (threat != null) {
                double threatDistance = merchant.distanceToSqr(threat);
                if (guard) {
                    merchant.tryDefendAgainst(world, threat);
                }
                if (threatDistance <= (double) (MERCHANT_RETREAT_TRIGGER_RADIUS * MERCHANT_RETREAT_TRIGGER_RADIUS)) {
                    moveMerchantTo(merchant, findMerchantRetreatPos(world, encounter, threat), MERCHANT_RETREAT_SPEED);
                    continue;
                }
            }

            if (merchant.blockPosition().distSqr(encounter.anchorPos) > (double) (MERCHANT_RETURN_RADIUS * MERCHANT_RETURN_RADIUS)) {
                moveMerchantTo(merchant, encounter.anchorPos, MERCHANT_RETURN_SPEED);
            } else {
                merchant.getNavigation().stop();
            }
        }
    }

    private static void moveMerchantTo(CaravanMerchantEntity merchant, BlockPos pos, double speed) {
        if (merchant == null || pos == null) {
            return;
        }
        merchant.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    private static BlockPos findMerchantRetreatPos(ServerLevel world, ActiveEncounter encounter, Mob threat) {
        if (world == null || encounter == null || threat == null) {
            return encounter == null ? BlockPos.ZERO : encounter.anchorPos;
        }
        double dx = encounter.anchorPos.getX() + 0.5 - threat.getX();
        double dz = encounter.anchorPos.getZ() + 0.5 - threat.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.1D) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0D;
        }
        int x = encounter.anchorPos.getX() + Mth.floor((dx / length) * MERCHANT_RETREAT_DISTANCE);
        int z = encounter.anchorPos.getZ() + Mth.floor((dz / length) * MERCHANT_RETREAT_DISTANCE);
        BlockPos retreatPos = safeSurface(world, x, z);
        if (retreatPos != null && retreatPos.distSqr(encounter.anchorPos) <= (double) (MERCHANT_MAX_RADIUS * MERCHANT_MAX_RADIUS)) {
            return retreatPos;
        }
        return encounter.anchorPos;
    }

    private static void updateBossBar(ServerLevel world,
                                      ServerPlayer player,
                                      ActiveEncounter encounter,
                                      List<CaravanMerchantEntity> merchants) {
        float current = 0.0f;
        float max = 0.0f;
        for (CaravanMerchantEntity merchant : merchants) {
            current += merchant.getHealth();
            max += merchant.getMaxHealth();
        }
        encounter.bossBar.setProgress(max <= 0.0f ? 0.0f : Mth.clamp(current / max, 0.0f, 1.0f));
        if (player != null && player.level() == world) {
            encounter.bossBar.addPlayer(player);
        }
    }

    private static void refreshEncounterMerchants(List<CaravanMerchantEntity> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return;
        }
        for (CaravanMerchantEntity merchant : merchants) {
            if (merchant != null && merchant.isAlive() && !merchant.isRemoved()) {
                merchant.setDespawnTicks(SURVIVOR_DESPAWN_TICKS);
            }
        }
    }

    private static void retargetHostiles(ServerLevel world,
                                         ServerPlayer player,
                                         ActiveEncounter encounter,
                                         List<CaravanMerchantEntity> merchants,
                                         List<Mob> hostiles) {
        for (Mob hostile : hostiles) {
            UUID hostileId = hostile.getUUID();
            if (hostile.getLastHurtByMob() instanceof ServerPlayer attacker && attacker.getUUID().equals(encounter.playerId)) {
                encounter.aggroTicks.put(hostileId, AGGRO_TICKS);
            }

            int ticks = Math.max(0, encounter.aggroTicks.getOrDefault(hostileId, 0) - 1);
            if (ticks > 0) {
                encounter.aggroTicks.put(hostileId, ticks);
            } else {
                encounter.aggroTicks.remove(hostileId);
            }

            if (ticks > 0 && player != null && player.isAlive() && player.level() == world) {
                hostile.setTarget(player);
                continue;
            }

            CaravanMerchantEntity merchant = nearestMerchant(hostile, merchants);
            if (merchant != null) {
                hostile.setTarget(merchant);
            }
        }
    }

    private static void startNextWave(ServerLevel world, ActiveEncounter encounter) {
        WaveSpec wave = encounter.spec.waves().get(encounter.waveIndex);
        encounter.waveIndex++;
        encounter.nextWaveDelayTicks = NEXT_WAVE_DELAY_TICKS;
        encounter.pendingSpawns.clear();
        queuePendingSpawns(encounter.pendingSpawns, HostileSpawnType.ZOMBIE, wave.zombies());
        queuePendingSpawns(encounter.pendingSpawns, HostileSpawnType.SKELETON, wave.skeletons());
        queuePendingSpawns(encounter.pendingSpawns, HostileSpawnType.SPIDER, wave.spiders());
        queuePendingSpawns(encounter.pendingSpawns, HostileSpawnType.TRAITOR, wave.traitors());
        shufflePendingSpawns(world, encounter.pendingSpawns);
        encounter.wavePulseDelayTicks = 0;
        spawnWavePulse(world, encounter);
    }

    private static void queuePendingSpawns(List<HostileSpawnType> pendingSpawns, HostileSpawnType type, int count) {
        for (int i = 0; i < count; i++) {
            pendingSpawns.add(type);
        }
    }

    private static void shufflePendingSpawns(ServerLevel world, List<HostileSpawnType> pendingSpawns) {
        if (world == null || pendingSpawns.size() < 2) {
            return;
        }
        for (int i = pendingSpawns.size() - 1; i > 0; i--) {
            int swapIndex = world.getRandom().nextInt(i + 1);
            HostileSpawnType current = pendingSpawns.get(i);
            pendingSpawns.set(i, pendingSpawns.get(swapIndex));
            pendingSpawns.set(swapIndex, current);
        }
    }

    private static void spawnWavePulse(ServerLevel world, ActiveEncounter encounter) {
        if (world == null || encounter == null || encounter.pendingSpawns.isEmpty()) {
            return;
        }
        int pulseCount = Math.min(WAVE_PULSE_SIZE, encounter.pendingSpawns.size());
        for (int i = 0; i < pulseCount; i++) {
            HostileSpawnType type = encounter.pendingSpawns.remove(encounter.pendingSpawns.size() - 1);
            spawnHostile(world, encounter, type);
        }
        encounter.wavePulseDelayTicks = encounter.pendingSpawns.isEmpty() ? 0 : WAVE_PULSE_DELAY_TICKS;
    }

    private static void spawnHostile(ServerLevel world, ActiveEncounter encounter, HostileSpawnType type) {
        switch (type) {
            case ZOMBIE -> spawnZombies(world, encounter, 1);
            case SKELETON -> spawnSkeletons(world, encounter, 1);
            case SPIDER -> spawnSpiders(world, encounter, 1);
            case TRAITOR -> spawnTraitors(world, encounter, 1);
        }
    }

    private static void spawnZombies(ServerLevel world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            Zombie zombie = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, world);
            prepareHostile(world, encounter, zombie, false);
        }
    }

    private static void spawnSkeletons(ServerLevel world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            Skeleton skeleton = new Skeleton(net.minecraft.world.entity.EntityType.SKELETON, world);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            prepareHostile(world, encounter, skeleton, false);
        }
    }

    private static void spawnSpiders(ServerLevel world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            Spider spider = new Spider(net.minecraft.world.entity.EntityType.SPIDER, world);
            prepareHostile(world, encounter, spider, false);
        }
    }

    private static void spawnTraitors(ServerLevel world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            TraitorEntity traitor = new TraitorEntity(ModEntities.TRAITOR, world);
            traitor.addTag(TAG_TRAITOR);
            prepareHostile(world, encounter, traitor, true);
        }
    }

    private static void prepareHostile(ServerLevel world, ActiveEncounter encounter, Mob hostile, boolean elite) {
        if (world == null || encounter == null || hostile == null) {
            return;
        }
        BlockPos spawnPos = findRingSpawn(world, encounter.anchorPos, HOSTILE_SPAWN_MIN_RADIUS, HOSTILE_SPAWN_MAX_RADIUS);
        moveEntityTo(hostile, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0f, 0.0f);
        hostile.setPersistenceRequired();
        hostile.addTag(TAG_HOSTILE);
        hostile.addTag(playerTag(encounter.playerId));
        if (elite) {
            hostile.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        }
        hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, HOSTILE_SPAWN_GLOW_TICKS, 0));
        if (!world.noCollision(hostile) || !world.addFreshEntity(hostile)) {
            return;
        }
        encounter.hostileIds.add(hostile.getUUID());
    }

    private static void failEncounter(ServerLevel world,
                                      ServerPlayer player,
                                      ActiveEncounter encounter,
                                      String winsKey,
                                      int targetWins) {
        cleanupEncounterEntities(world, encounter, true);
        ACTIVE.remove(encounter.playerId);
        encounter.bossBar.removeAllPlayers();
        scheduleEncounter(world, player, encounter.spec.kind(), encounter.spec.finalConvoy() ? 1 : 1);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    encounter.spec.finalConvoy()
                            ? "message.village-quest.story.shadows_on_the_trade_road.final_failed"
                            : "message.village-quest.story.shadows_on_the_trade_road.rescue_failed"
            ).withStyle(ChatFormatting.RED), false);
            refreshQuestUi(world, player);
        }
    }

    private static void succeedEncounter(ServerLevel world,
                                         ServerPlayer player,
                                         ActiveEncounter encounter,
                                         String winsKey,
                                         int targetWins) {
        cleanupEncounterEntities(world, encounter, false);
        ACTIVE.remove(encounter.playerId);
        encounter.bossBar.removeAllPlayers();
        UUID playerId = encounter.playerId;
        int nextWins = Math.min(targetWins, StoryQuestService.getQuestInt(world, playerId, winsKey) + 1);
        StoryQuestService.setQuestInt(world, playerId, winsKey, nextWins);
        clearScheduledEncounter(world, playerId);
        if (nextWins < targetWins) {
            scheduleEncounter(world, player, encounter.spec.kind(), 1);
        }
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    encounter.spec.finalConvoy()
                            ? "message.village-quest.story.shadows_on_the_trade_road.final_saved"
                            : "message.village-quest.story.shadows_on_the_trade_road.rescue_saved"
            ).withStyle(ChatFormatting.GOLD), false);
            if (!encounter.spec.finalConvoy() && encounter.spec.kind() == RESCUE_KIND_HOLDING && nextWins >= targetWins) {
                player.sendSystemMessage(Component.translatable("message.village-quest.story.shadows_on_the_trade_road.survivor_waiting").withStyle(ChatFormatting.GRAY), false);
            }
            StoryQuestService.completeIfEligible(world, player);
            refreshQuestUi(world, player);
        }
    }

    private static void cleanupEncounterEntities(ServerLevel world, ActiveEncounter encounter, boolean discardMerchants) {
        if (world == null || encounter == null) {
            return;
        }
        for (UUID hostileId : encounter.hostileIds) {
            Entity entity = findEntity(world, hostileId);
            if (entity != null) {
                entity.discard();
            }
        }
        if (discardMerchants) {
            for (UUID merchantId : encounter.merchantIds) {
                Entity entity = findEntity(world, merchantId);
                if (entity != null) {
                    entity.discard();
                }
            }
            return;
        }
        for (UUID merchantId : encounter.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (entity instanceof CaravanMerchantEntity merchant) {
                merchant.clearEncounterControl();
                merchant.setDespawnTicks(SURVIVOR_DESPAWN_TICKS);
            }
        }
    }

    private static List<CaravanMerchantEntity> livingMerchants(ServerLevel world, ActiveEncounter encounter) {
        List<CaravanMerchantEntity> living = new ArrayList<>();
        for (UUID merchantId : encounter.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                living.add(merchant);
            }
        }
        return living;
    }

    private static List<Mob> livingHostiles(ServerLevel world, ActiveEncounter encounter) {
        List<Mob> living = new ArrayList<>();
        for (UUID hostileId : encounter.hostileIds) {
            Entity entity = findEntity(world, hostileId);
            if (entity instanceof Mob hostile && hostile.isAlive() && !hostile.isRemoved()) {
                living.add(hostile);
            }
        }
        return living;
    }

    private static CaravanMerchantEntity nearestMerchant(Entity hostile, List<CaravanMerchantEntity> merchants) {
        CaravanMerchantEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (CaravanMerchantEntity merchant : merchants) {
            double distance = hostile.distanceToSqr(merchant);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = merchant;
            }
        }
        return nearest;
    }

    private static Mob nearestHostile(Entity source, List<Mob> hostiles) {
        if (source == null || hostiles == null || hostiles.isEmpty()) {
            return null;
        }
        Mob nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob hostile : hostiles) {
            if (hostile == null || !hostile.isAlive() || hostile.isRemoved()) {
                continue;
            }
            double distance = source.distanceToSqr(hostile);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = hostile;
            }
        }
        return nearest;
    }

    private static void scheduleEncounter(ServerLevel world, ServerPlayer player, int kind, int nightsUntilStart) {
        if (world == null || player == null) {
            return;
        }
        BlockPos anchor = findEncounterAnchor(world, player.blockPosition());
        int targetDay = currentWorldDay(world) + Math.max(0, nightsUntilStart);
        StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_RESCUE_TARGET_X, anchor.getX());
        StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_RESCUE_TARGET_Z, anchor.getZ());
        StoryQuestService.setQuestInt(
                world,
                player.getUUID(),
                kind == RESCUE_KIND_FINAL ? StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY : StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY,
                targetDay
        );
        StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_RESCUE_KIND, kind);
        if (kind == RESCUE_KIND_FINAL) {
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY, 0);
        } else {
            StoryQuestService.setQuestInt(world, player.getUUID(), StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY, 0);
        }
    }

    private static void clearScheduledEncounter(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_X, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_Z, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_KIND, 0);
    }

    private static void ensureCourier(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        UUID existing = LETTER_COURIERS.get(player.getUUID());
        if (existing != null) {
            Entity entity = findEntity(world, existing);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                return;
            }
        }

        BlockPos spawnPos = findEncounterAnchor(world, player.blockPosition()).atY(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ()));
        CaravanMerchantEntity courier = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
        moveEntityTo(courier, player.getX() + 2.0, spawnPos.getY(), player.getZ() + 2.0, world.getRandom().nextFloat() * 360.0f, 0.0f);
        courier.setCourier(true);
        courier.setDespawnTicks(COURIER_DESPAWN_TICKS);
        courier.addTag(TAG_CARAVAN);
        courier.addTag(playerTag(player.getUUID()));
        courier.setCustomName(Component.translatable("entity.village-quest.caravan_courier"));
        if (world.addFreshEntity(courier)) {
            LETTER_COURIERS.put(player.getUUID(), courier.getUUID());
        }
    }

    private static void clearCourier(ServerLevel world, UUID playerId) {
        if (playerId == null) {
            return;
        }
        UUID courierId = LETTER_COURIERS.remove(playerId);
        if (world == null || courierId == null) {
            return;
        }
        Entity entity = findEntity(world, courierId);
        if (entity != null) {
            entity.discard();
        }
    }

    private static void cleanupPlayerRuntime(ServerLevel world, UUID playerId, boolean preserveScheduledTarget) {
        ActiveEncounter encounter = ACTIVE.remove(playerId);
        if (encounter != null) {
            cleanupEncounterEntities(world, encounter, true);
            encounter.bossBar.removeAllPlayers();
        }
        if (!preserveScheduledTarget) {
            clearScheduledEncounter(world, playerId);
        }
    }

    private static ItemStack createGuildWarningLetter() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.village-quest.guild_warning_letter").withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.village-quest.guild_warning_letter.lore").withStyle(ChatFormatting.DARK_GRAY)
        )));
        return stack;
    }

    private static boolean isGuildWarningLetter(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.PAPER)) {
            return false;
        }
        String expected = Component.translatable("item.village-quest.guild_warning_letter").getString();
        return stack.getHoverName().getString().equals(expected);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix").withStyle(ChatFormatting.GRAY)
                    .append(stack.getHoverName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix").withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.containerMenu.broadcastChanges();
        }
    }

    private static int targetDay(ServerLevel world, UUID playerId, boolean finalConvoy) {
        return StoryQuestService.getQuestInt(
                world,
                playerId,
                finalConvoy ? StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY : StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY
        );
    }

    private static int scheduledEncounterKind(ServerLevel world, UUID playerId) {
        return StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_KIND);
    }

    private static int currentWorldDay(ServerLevel world) {
        return world == null ? 0 : (int) (world.getOverworldClockTime() / 24000L);
    }

    private static boolean isNight(ServerLevel world) {
        if (world == null) {
            return false;
        }
        long dayTime = Math.floorMod(world.getOverworldClockTime(), 24000L);
        return dayTime >= 13000L && dayTime <= 23000L;
    }

    private static void moveEntityTo(Entity entity, double x, double y, double z, float yaw, float pitch) {
        if (entity == null) {
            return;
        }
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
    }

    private static Component distressLabel(int kind) {
        return Component.translatable(
                kind == RESCUE_KIND_FINAL
                        ? "text.village-quest.special.surveyor_compass.mode.guild_convoy"
                        : "text.village-quest.special.surveyor_compass.mode.caravan_distress"
        );
    }

    private static BlockPos findEncounterAnchor(ServerLevel world, BlockPos origin) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(world.getRandom(), MIN_RESCUE_DISTANCE, MAX_RESCUE_DISTANCE);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos base = safeSurface(world, x, z);
            if (base != null && world.getWorldBorder().isWithinBounds(base) && hasDryEncounterFootprint(world, base)) {
                return base;
            }
        }
        BlockPos fallback = safeSurface(world, origin.getX() + MIN_RESCUE_DISTANCE, origin.getZ());
        return fallback != null && hasDryEncounterFootprint(world, fallback) ? fallback : origin;
    }

    private static BlockPos findMerchantSpawn(ServerLevel world, BlockPos anchor, List<BlockPos> existingSpawns) {
        BlockPos closeSpawn = findSpacedRingSpawn(
                world,
                anchor,
                MERCHANT_SPAWN_MIN_RADIUS,
                MERCHANT_SPAWN_MAX_RADIUS,
                existingSpawns
        );
        if (closeSpawn != null) {
            return closeSpawn;
        }
        BlockPos widerSpawn = findSpacedRingSpawn(
                world,
                anchor,
                MERCHANT_SPAWN_MAX_RADIUS,
                MERCHANT_MAX_RADIUS,
                existingSpawns
        );
        return widerSpawn != null ? widerSpawn : findRingSpawn(world, anchor, MERCHANT_SPAWN_MIN_RADIUS, MERCHANT_SPAWN_MAX_RADIUS);
    }

    private static BlockPos findSpacedRingSpawn(ServerLevel world,
                                                BlockPos anchor,
                                                int minRadius,
                                                int maxRadius,
                                                List<BlockPos> existingSpawns) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS * 3; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(world.getRandom(), minRadius, maxRadius);
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos pos = safeSurface(world, x, z);
            if (pos != null && isMerchantSpawnFarEnough(pos, existingSpawns)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isMerchantSpawnFarEnough(BlockPos candidate, List<BlockPos> existingSpawns) {
        if (candidate == null || existingSpawns == null || existingSpawns.isEmpty()) {
            return true;
        }
        double minDistanceSquared = MERCHANT_SPAWN_MIN_DISTANCE * MERCHANT_SPAWN_MIN_DISTANCE;
        for (BlockPos existing : existingSpawns) {
            if (existing != null && candidate.distSqr(existing) < minDistanceSquared) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos findRingSpawn(ServerLevel world, BlockPos anchor, int minRadius, int maxRadius) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(world.getRandom(), minRadius, maxRadius);
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos pos = safeSurface(world, x, z);
            if (pos != null) {
                return pos;
            }
        }
        return anchor;
    }

    private static BlockPos safeSurface(ServerLevel world, int x, int z) {
        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, topY, z);
        if (!world.getBlockState(pos).isAir()) {
            pos = pos.above();
        }
        BlockPos belowPos = pos.below();
        BlockState feetState = world.getBlockState(pos);
        BlockState headState = world.getBlockState(pos.above());
        BlockState belowState = world.getBlockState(belowPos);
        if (!feetState.isAir() || !headState.isAir()) {
            return null;
        }
        if (belowState.isAir() || !belowState.getFluidState().isEmpty() || !belowState.isFaceSturdy(world, belowPos, net.minecraft.core.Direction.UP)) {
            return null;
        }
        return pos;
    }

    private static boolean hasDryEncounterFootprint(ServerLevel world, BlockPos center) {
        if (world == null || center == null) {
            return false;
        }
        int drySpots = 0;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos sample = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                if (sample != null) {
                    drySpots++;
                }
            }
        }
        return drySpots >= 6;
    }

    private static Entity findEntity(ServerLevel world, UUID entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        for (Entity entity : world.getAllEntities()) {
            if (entityId.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    private static String playerTag(UUID playerId) {
        return "vq_trade_player_" + playerId.toString();
    }

    private static void refreshQuestUi(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }
}
