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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.BlockState;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

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
            TagKey.of(RegistryKeys.STRUCTURE, Identifier.of("village-quest", "roadmark_villages"));

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
    public record DistressTarget(BlockPos pos, Text label) {}

    private record WaveSpec(int zombies, int skeletons, int spiders, int traitors) {}
    private enum HostileSpawnType {
        ZOMBIE,
        SKELETON,
        SPIDER,
        TRAITOR
    }
    private record EncounterSpec(int kind, int merchants, Text bossBarTitle, boolean finalConvoy, List<WaveSpec> waves) {}

    private static final class ActiveEncounter {
        private final UUID playerId;
        private final EncounterSpec spec;
        private final BlockPos anchorPos;
        private final ServerBossBar bossBar;
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
            this.bossBar = new ServerBossBar(spec.bossBarTitle(), BossBar.Color.RED, BossBar.Style.PROGRESS);
            this.bossBar.setPercent(1.0f);
        }
    }

    private static final EncounterSpec FIRST_SIGNAL_SPEC = new EncounterSpec(
            RESCUE_KIND_FIRST_SIGNAL,
            3,
            Text.translatable("bossbar.village-quest.shadows.first_signal"),
            false,
            List.of(
                    new WaveSpec(4, 1, 1, 0),
                    new WaveSpec(5, 3, 2, 0)
            )
    );
    private static final EncounterSpec HOLDING_SPEC = new EncounterSpec(
            RESCUE_KIND_HOLDING,
            3,
            Text.translatable("bossbar.village-quest.shadows.holding"),
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
            Text.translatable("bossbar.village-quest.shadows.final_convoy"),
            true,
            List.of(
                    new WaveSpec(5, 3, 2, 0),
                    new WaveSpec(6, 4, 3, 0),
                    new WaveSpec(8, 4, 3, 0),
                    new WaveSpec(0, 0, 0, 3)
            )
    );

    public static void resetRuntimeState() {
        ACTIVE.values().forEach(encounter -> encounter.bossBar.clearPlayers());
        ACTIVE.clear();
        LETTER_COURIERS.clear();
    }

    public static int despawnAll(ServerWorld world) {
        if (world == null) {
            return 0;
        }
        int removed = 0;
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (!entity.isAlive()) {
                continue;
            }
            if (entity instanceof CaravanMerchantEntity
                    || entity instanceof TraitorEntity
                    || entity.getCommandTags().contains(TAG_HOSTILE)) {
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

    public static boolean hasUnlockPrerequisites(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && VillageProjectService.isUnlocked(world, playerId, VillageProjectType.WATCH_BELL)
                && PilgrimContractService.completedCombatContractCount(world, playerId) >= RUMOR_UNLOCK_TARGET;
    }

    public static int completedRumorCount(ServerWorld world, UUID playerId) {
        return PilgrimContractService.completedCombatContractCount(world, playerId);
    }

    public static boolean hasCompass(ServerPlayerEntity player) {
        if (player == null || ModItems.SURVEYORS_COMPASS == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.SURVEYORS_COMPASS)) {
                return true;
            }
        }
        return player.getOffHandStack().isOf(ModItems.SURVEYORS_COMPASS);
    }

    public static VillageMarker currentVillage(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null || world.getRegistryKey() != World.OVERWORLD) {
            return null;
        }
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, ROADMARK_VILLAGES);
        if (start == null || !start.hasChildren()) {
            return null;
        }
        BlockBox box = start.getBoundingBox();
        int centerX = (box.getMinX() + box.getMaxX()) / 2;
        int centerZ = (box.getMinZ() + box.getMaxZ()) / 2;
        return new VillageMarker(centerX + "_" + centerZ, centerX, centerZ);
    }

    public static boolean hasHomeVillage(ServerWorld world, UUID playerId) {
        return playerId != null && (StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGERS) > 0
                || StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_X) != 0
                || StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_Z) != 0);
    }

    public static void bindHomeVillage(ServerWorld world, UUID playerId, VillageMarker village) {
        if (world == null || playerId == null || village == null) {
            return;
        }
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_X, village.centerX());
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_Z, village.centerZ());
    }

    public static boolean isHomeVillage(ServerWorld world, UUID playerId, VillageMarker village) {
        if (world == null || playerId == null || village == null || !hasHomeVillage(world, playerId)) {
            return false;
        }
        return StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_X) == village.centerX()
                && StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_HOME_VILLAGE_Z) == village.centerZ();
    }

    public static boolean isDistressModeAvailable(ServerWorld world, UUID playerId) {
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

    public static DistressTarget currentDistressTarget(ServerWorld world, UUID playerId) {
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
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new DistressTarget(new BlockPos(x, y, z), distressLabel(kind));
    }

    public static int currentDistressKind(ServerWorld world, UUID playerId) {
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
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            StoryArcType activeArc = StoryQuestService.activeArcType(world, player.getUuid());
            if (activeArc != StoryArcType.SHADOWS_ON_THE_TRADE_ROAD) {
                cleanupPlayerRuntime(world, player.getUuid(), false);
                continue;
            }

            int chapterIndex = StoryQuestService.chapterIndex(world, player.getUuid(), StoryArcType.SHADOWS_ON_THE_TRADE_ROAD);
            if (chapterIndex == 2) {
                tickRescueChapter(world, player, StoryQuestKeys.SHADOWS_FIRST_SIGNAL_WINS, 1, RESCUE_KIND_FIRST_SIGNAL, 0);
            } else if (chapterIndex == 3) {
                tickRescueChapter(world, player, StoryQuestKeys.SHADOWS_HOLDING_WINS, HOLDING_TARGET_WINS, RESCUE_KIND_HOLDING, 0);
            } else if (chapterIndex == 4) {
                ensureCourier(world, player);
            } else if (chapterIndex == 5) {
                tickRescueChapter(world, player, StoryQuestKeys.SHADOWS_FINAL_WON, 1, RESCUE_KIND_FINAL, 2);
            } else {
                cleanupPlayerRuntime(world, player.getUuid(), false);
                clearCourier(world, player.getUuid());
            }
        }
    }

    public static void onFirstSignalAccepted(ServerWorld world, ServerPlayerEntity player) {
        scheduleEncounter(world, player, RESCUE_KIND_FIRST_SIGNAL, 0);
    }

    public static void onHoldingAccepted(ServerWorld world, ServerPlayerEntity player) {
        scheduleEncounter(world, player, RESCUE_KIND_HOLDING, 0);
    }

    public static void onLetterAccepted(ServerWorld world, ServerPlayerEntity player) {
        ensureCourier(world, player);
    }

    public static void onFinalAccepted(ServerWorld world, ServerPlayerEntity player) {
        scheduleEncounter(world, player, RESCUE_KIND_FINAL, 2);
    }

    public static List<Text> rescueProgressLines(ServerWorld world, UUID playerId, String winsKey, int targetWins, boolean finalConvoy) {
        List<Text> lines = new ArrayList<>();
        if (world == null || playerId == null) {
            return lines;
        }
        lines.add(Text.translatable(
                finalConvoy
                        ? "quest.village-quest.story.shadows_on_the_trade_road.chapter_6.progress.1"
                        : "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.count",
                StoryQuestService.getQuestInt(world, playerId, winsKey),
                targetWins
        ).formatted(Formatting.GRAY));

        ActiveEncounter encounter = ACTIVE.get(playerId);
        if (encounter != null) {
            List<MobEntity> hostiles = livingHostiles(world, encounter);
            int remainingHostiles = hostiles.size() + encounter.pendingSpawns.size();
            lines.add(Text.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wave",
                    Math.max(1, encounter.waveIndex),
                    encounter.spec.waves().size()
            ).formatted(Formatting.GRAY));
            lines.add(Text.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.hostiles",
                    remainingHostiles
            ).formatted(Formatting.GRAY));
            lines.add(Text.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.survivors",
                    livingMerchants(world, encounter).size(),
                    encounter.spec.merchants()
            ).formatted(Formatting.GRAY));
            return List.copyOf(lines);
        }

        int targetDay = targetDay(world, playerId, finalConvoy);
        if (targetDay <= 0) {
            lines.add(Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.scouting").formatted(Formatting.GRAY));
            return List.copyOf(lines);
        }

        int nightsRemaining = Math.max(0, targetDay - currentWorldDay(world));
        if (nightsRemaining > 0) {
            lines.add(Text.translatable(
                    "quest.village-quest.story.shadows_on_the_trade_road.chapter_6.progress.2",
                    nightsRemaining
            ).formatted(Formatting.GRAY));
        } else if (!world.isNight()) {
            lines.add(Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wait_night").formatted(Formatting.GRAY));
        } else {
            lines.add(Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.compass").formatted(Formatting.GRAY));
        }
        return List.copyOf(lines);
    }

    public static List<Text> letterProgressLines(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return List.of();
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        if (StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_LETTER_RECEIVED) <= 0) {
            return List.of(Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.1").formatted(Formatting.GRAY));
        }
        return List.of(
                Text.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.2").formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.3",
                        player != null && hasGuildWarningLetter(player) ? 1 : 0,
                        1
                ).formatted(Formatting.GRAY)
        );
    }

    public static boolean handleCourierInteraction(ServerWorld world, ServerPlayerEntity player, Entity entity) {
        if (!(entity instanceof CaravanMerchantEntity merchant) || world == null || player == null) {
            return false;
        }
        UUID expected = LETTER_COURIERS.get(player.getUuid());
        if (expected == null || !expected.equals(merchant.getUuid())) {
            return false;
        }
        if (StoryQuestService.getQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_LETTER_RECEIVED) <= 0) {
            giveOrDrop(player, createGuildWarningLetter());
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_LETTER_RECEIVED, 1);
            player.sendMessage(Text.translatable("message.village-quest.story.shadows_on_the_trade_road.courier.1").formatted(Formatting.GOLD), false);
            player.sendMessage(Text.translatable("message.village-quest.story.shadows_on_the_trade_road.courier.2").formatted(Formatting.GRAY), false);
            merchant.setDespawnTicks(20 * 10);
            merchant.setCourier(false);
            refreshQuestUi(world, player);
        } else {
            VillagerDialogueService.sendDialogue(player, merchant, Text.translatable("message.village-quest.story.shadows_on_the_trade_road.courier.3"));
        }
        return true;
    }

    public static boolean hasGuildWarningLetter(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (isGuildWarningLetter(player.getInventory().getStack(slot))) {
                return true;
            }
        }
        return isGuildWarningLetter(player.getOffHandStack());
    }

    public static boolean consumeGuildWarningLetter(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (isGuildWarningLetter(player.getInventory().getStack(slot))) {
                player.getInventory().getStack(slot).decrement(1);
                player.playerScreenHandler.sendContentUpdates();
                return true;
            }
        }
        if (isGuildWarningLetter(player.getOffHandStack())) {
            player.getOffHandStack().decrement(1);
            player.playerScreenHandler.sendContentUpdates();
            return true;
        }
        return false;
    }

    public static boolean adminUnlockForTesting(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        boolean changed = false;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUuid());
        if (!VillageProjectService.isUnlocked(world, player.getUuid(), VillageProjectType.WATCH_BELL)) {
            VillageProjectService.unlock(world, player.getUuid(), VillageProjectType.WATCH_BELL);
            changed = true;
        }
        if (ModItems.SURVEYORS_COMPASS != null && !hasCompass(player)) {
            giveOrDrop(player, new ItemStack(ModItems.SURVEYORS_COMPASS));
            changed = true;
        }
        if (SurveyorCompassQuestService.unlockStructureModes(world, player)) {
            changed = true;
        }
        changed |= setCombatRumorTestFlags(world, player.getUuid());
        if (!data.getStoryDiscovered().contains(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id())) {
            data.setStoryDiscovered(StoryArcType.SHADOWS_ON_THE_TRADE_ROAD.id(), true);
            changed = true;
        }
        QuestState.get(world.getServer()).markDirty();
        refreshQuestUi(world, player);
        return changed;
    }

    public static boolean adminPrepareEncounterTest(ServerWorld world, ServerPlayerEntity player, boolean finalConvoy) {
        if (world == null || player == null) {
            return false;
        }
        adminUnlockForTesting(world, player);
        adminResetPlayer(world, player.getUuid());
        StoryQuestService.adminResetStoryState(world, player.getUuid());
        QuestState.get(world.getServer()).getPlayerData(player.getUuid()).setStoryChapterProgress(
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

    public static void adminResetPlayer(ServerWorld world, UUID playerId) {
        cleanupPlayerRuntime(world, playerId, false);
        clearCourier(world, playerId);
    }

    private static boolean setCombatRumorTestFlags(ServerWorld world, UUID playerId) {
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
            QuestState.get(world.getServer()).markDirty();
        }
        return changed;
    }

    private static void tickRescueChapter(ServerWorld world,
                                          ServerPlayerEntity player,
                                          String winsKey,
                                          int targetWins,
                                          int kind,
                                          int nightsUntilFirstRun) {
        UUID playerId = player.getUuid();
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
        if (!world.isNight() || currentWorldDay(world) < targetDay || player.getEntityWorld() != world) {
            return;
        }
        if (player.getBlockPos().getSquaredDistance(distressTarget.pos()) > (double) (TRIGGER_RADIUS * TRIGGER_RADIUS)) {
            return;
        }

        ActiveEncounter created = spawnEncounter(world, player, kind, distressTarget.pos());
        if (created != null) {
            ACTIVE.put(playerId, created);
            player.sendMessage(Text.translatable(
                    kind == RESCUE_KIND_FINAL
                            ? "message.village-quest.story.shadows_on_the_trade_road.final_found"
                            : "message.village-quest.story.shadows_on_the_trade_road.rescue_found"
            ).formatted(Formatting.GOLD), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 0.9f, 0.9f);
        }
    }

    private static ActiveEncounter spawnEncounter(ServerWorld world, ServerPlayerEntity player, int kind, BlockPos anchorPos) {
        EncounterSpec spec = switch (kind) {
            case RESCUE_KIND_FIRST_SIGNAL -> FIRST_SIGNAL_SPEC;
            case RESCUE_KIND_HOLDING -> HOLDING_SPEC;
            case RESCUE_KIND_FINAL -> FINAL_SPEC;
            default -> null;
        };
        if (spec == null || world == null || player == null || anchorPos == null) {
            return null;
        }

        ActiveEncounter encounter = new ActiveEncounter(player.getUuid(), spec, anchorPos);
        int guardCount = spec.merchants() / 3;
        List<BlockPos> merchantPositions = new ArrayList<>();
        for (int i = 0; i < spec.merchants(); i++) {
            BlockPos merchantPos = findMerchantSpawn(world, anchorPos, merchantPositions);
            CaravanMerchantEntity merchant = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
            merchant.refreshPositionAndAngles(merchantPos.getX() + 0.5, merchantPos.getY(), merchantPos.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
            merchant.setHealth(merchant.getMaxHealth());
            merchant.setDespawnTicks(SURVIVOR_DESPAWN_TICKS);
            boolean guard = i < guardCount;
            merchant.refreshEncounterControl(guard);
            merchant.addCommandTag(TAG_CARAVAN);
            merchant.addCommandTag(playerTag(player.getUuid()));
            if (!world.isSpaceEmpty(merchant) || !world.spawnEntity(merchant)) {
                continue;
            }
            encounter.merchantIds.add(merchant.getUuid());
            merchantPositions.add(merchantPos);
            if (guard) {
                encounter.guardMerchantIds.add(merchant.getUuid());
            }
        }
        if (encounter.merchantIds.isEmpty()) {
            return null;
        }
        startNextWave(world, encounter);
        return encounter;
    }

    private static void tickActiveEncounter(ServerWorld world,
                                            ServerPlayerEntity player,
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
        List<MobEntity> hostiles = livingHostiles(world, encounter);
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
            player.sendMessage(Text.translatable(
                    "message.village-quest.story.shadows_on_the_trade_road.wave",
                    encounter.waveIndex,
                    encounter.spec.waves().size()
            ).formatted(Formatting.GOLD), true);
            return;
        }

        succeedEncounter(world, player, encounter, winsKey, targetWins);
    }

    private static List<MobEntity> maintainHostileSafety(ServerWorld world,
                                                        ServerPlayerEntity player,
                                                        ActiveEncounter encounter,
                                                        List<MobEntity> hostiles) {
        boolean relocated = false;
        for (MobEntity hostile : hostiles) {
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

        for (MobEntity hostile : hostiles) {
            hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, LAST_HOSTILE_MARK_DURATION_TICKS, 0), player);
        }
        encounter.lastHostileMarkDelayTicks = LAST_HOSTILE_MARK_REPEAT_TICKS;
        return hostiles;
    }

    private static boolean shouldLeashHostile(ActiveEncounter encounter, MobEntity hostile) {
        if (encounter == null || hostile == null) {
            return false;
        }
        BlockPos hostilePos = hostile.getBlockPos();
        return hostilePos.getSquaredDistance(encounter.anchorPos) > (double) (HOSTILE_LEASH_RADIUS * HOSTILE_LEASH_RADIUS)
                || Math.abs(hostilePos.getY() - encounter.anchorPos.getY()) > HOSTILE_MAX_Y_DELTA;
    }

    private static void relocateHostileToEncounter(ServerWorld world, ActiveEncounter encounter, MobEntity hostile) {
        if (world == null || encounter == null || hostile == null) {
            return;
        }
        BlockPos spawnPos = findRingSpawn(world, encounter.anchorPos, HOSTILE_SPAWN_MIN_RADIUS, HOSTILE_SPAWN_MAX_RADIUS);
        hostile.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
        hostile.setVelocity(0.0, 0.0, 0.0);
        hostile.getNavigation().stop();
        encounter.aggroTicks.remove(hostile.getUuid());
    }

    private static void tickCaravanBehavior(ServerWorld world,
                                            ActiveEncounter encounter,
                                            List<CaravanMerchantEntity> merchants,
                                            List<MobEntity> hostiles) {
        if (world == null || encounter == null || merchants == null) {
            return;
        }
        for (CaravanMerchantEntity merchant : merchants) {
            boolean guard = encounter.guardMerchantIds.contains(merchant.getUuid());
            merchant.refreshEncounterControl(guard);

            if (merchant.getBlockPos().getSquaredDistance(encounter.anchorPos) > (double) (MERCHANT_MAX_RADIUS * MERCHANT_MAX_RADIUS)) {
                moveMerchantTo(merchant, encounter.anchorPos, MERCHANT_RETURN_SPEED);
                continue;
            }

            MobEntity threat = nearestHostile(merchant, hostiles);
            if (threat != null) {
                double threatDistance = merchant.squaredDistanceTo(threat);
                if (guard) {
                    merchant.tryDefendAgainst(world, threat);
                }
                if (threatDistance <= (double) (MERCHANT_RETREAT_TRIGGER_RADIUS * MERCHANT_RETREAT_TRIGGER_RADIUS)) {
                    moveMerchantTo(merchant, findMerchantRetreatPos(world, encounter, threat), MERCHANT_RETREAT_SPEED);
                    continue;
                }
            }

            if (merchant.getBlockPos().getSquaredDistance(encounter.anchorPos) > (double) (MERCHANT_RETURN_RADIUS * MERCHANT_RETURN_RADIUS)) {
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
        merchant.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    private static BlockPos findMerchantRetreatPos(ServerWorld world, ActiveEncounter encounter, MobEntity threat) {
        if (world == null || encounter == null || threat == null) {
            return encounter == null ? BlockPos.ORIGIN : encounter.anchorPos;
        }
        double dx = encounter.anchorPos.getX() + 0.5 - threat.getX();
        double dz = encounter.anchorPos.getZ() + 0.5 - threat.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.1D) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0D;
        }
        int x = encounter.anchorPos.getX() + MathHelper.floor((dx / length) * MERCHANT_RETREAT_DISTANCE);
        int z = encounter.anchorPos.getZ() + MathHelper.floor((dz / length) * MERCHANT_RETREAT_DISTANCE);
        BlockPos retreatPos = safeSurface(world, x, z);
        if (retreatPos != null && retreatPos.getSquaredDistance(encounter.anchorPos) <= (double) (MERCHANT_MAX_RADIUS * MERCHANT_MAX_RADIUS)) {
            return retreatPos;
        }
        return encounter.anchorPos;
    }

    private static void updateBossBar(ServerWorld world,
                                      ServerPlayerEntity player,
                                      ActiveEncounter encounter,
                                      List<CaravanMerchantEntity> merchants) {
        float current = 0.0f;
        float max = 0.0f;
        for (CaravanMerchantEntity merchant : merchants) {
            current += merchant.getHealth();
            max += merchant.getMaxHealth();
        }
        encounter.bossBar.setPercent(max <= 0.0f ? 0.0f : MathHelper.clamp(current / max, 0.0f, 1.0f));
        if (player != null && player.getEntityWorld() == world) {
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

    private static void retargetHostiles(ServerWorld world,
                                         ServerPlayerEntity player,
                                         ActiveEncounter encounter,
                                         List<CaravanMerchantEntity> merchants,
                                         List<MobEntity> hostiles) {
        for (MobEntity hostile : hostiles) {
            UUID hostileId = hostile.getUuid();
            if (hostile.getAttacker() instanceof ServerPlayerEntity attacker && attacker.getUuid().equals(encounter.playerId)) {
                encounter.aggroTicks.put(hostileId, AGGRO_TICKS);
            }

            int ticks = Math.max(0, encounter.aggroTicks.getOrDefault(hostileId, 0) - 1);
            if (ticks > 0) {
                encounter.aggroTicks.put(hostileId, ticks);
            } else {
                encounter.aggroTicks.remove(hostileId);
            }

            if (ticks > 0 && player != null && player.isAlive() && player.getEntityWorld() == world) {
                hostile.setTarget(player);
                continue;
            }

            CaravanMerchantEntity merchant = nearestMerchant(hostile, merchants);
            if (merchant != null) {
                hostile.setTarget(merchant);
            }
        }
    }

    private static void startNextWave(ServerWorld world, ActiveEncounter encounter) {
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

    private static void shufflePendingSpawns(ServerWorld world, List<HostileSpawnType> pendingSpawns) {
        if (world == null || pendingSpawns.size() < 2) {
            return;
        }
        for (int i = pendingSpawns.size() - 1; i > 0; i--) {
            int swapIndex = world.random.nextInt(i + 1);
            HostileSpawnType current = pendingSpawns.get(i);
            pendingSpawns.set(i, pendingSpawns.get(swapIndex));
            pendingSpawns.set(swapIndex, current);
        }
    }

    private static void spawnWavePulse(ServerWorld world, ActiveEncounter encounter) {
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

    private static void spawnHostile(ServerWorld world, ActiveEncounter encounter, HostileSpawnType type) {
        switch (type) {
            case ZOMBIE -> spawnZombies(world, encounter, 1);
            case SKELETON -> spawnSkeletons(world, encounter, 1);
            case SPIDER -> spawnSpiders(world, encounter, 1);
            case TRAITOR -> spawnTraitors(world, encounter, 1);
        }
    }

    private static void spawnZombies(ServerWorld world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            ZombieEntity zombie = new ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE, world);
            prepareHostile(world, encounter, zombie, false);
        }
    }

    private static void spawnSkeletons(ServerWorld world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            SkeletonEntity skeleton = new SkeletonEntity(net.minecraft.entity.EntityType.SKELETON, world);
            skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            prepareHostile(world, encounter, skeleton, false);
        }
    }

    private static void spawnSpiders(ServerWorld world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            SpiderEntity spider = new SpiderEntity(net.minecraft.entity.EntityType.SPIDER, world);
            prepareHostile(world, encounter, spider, false);
        }
    }

    private static void spawnTraitors(ServerWorld world, ActiveEncounter encounter, int count) {
        for (int i = 0; i < count; i++) {
            TraitorEntity traitor = new TraitorEntity(ModEntities.TRAITOR, world);
            traitor.addCommandTag(TAG_TRAITOR);
            prepareHostile(world, encounter, traitor, true);
        }
    }

    private static void prepareHostile(ServerWorld world, ActiveEncounter encounter, MobEntity hostile, boolean elite) {
        if (world == null || encounter == null || hostile == null) {
            return;
        }
        BlockPos spawnPos = findRingSpawn(world, encounter.anchorPos, HOSTILE_SPAWN_MIN_RADIUS, HOSTILE_SPAWN_MAX_RADIUS);
        hostile.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
        hostile.setPersistent();
        hostile.addCommandTag(TAG_HOSTILE);
        hostile.addCommandTag(playerTag(encounter.playerId));
        if (elite) {
            hostile.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        }
        hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, HOSTILE_SPAWN_GLOW_TICKS, 0));
        if (!world.isSpaceEmpty(hostile) || !world.spawnEntity(hostile)) {
            return;
        }
        encounter.hostileIds.add(hostile.getUuid());
    }

    private static void failEncounter(ServerWorld world,
                                      ServerPlayerEntity player,
                                      ActiveEncounter encounter,
                                      String winsKey,
                                      int targetWins) {
        cleanupEncounterEntities(world, encounter, true);
        ACTIVE.remove(encounter.playerId);
        encounter.bossBar.clearPlayers();
        scheduleEncounter(world, player, encounter.spec.kind(), encounter.spec.finalConvoy() ? 1 : 1);
        if (player != null) {
            player.sendMessage(Text.translatable(
                    encounter.spec.finalConvoy()
                            ? "message.village-quest.story.shadows_on_the_trade_road.final_failed"
                            : "message.village-quest.story.shadows_on_the_trade_road.rescue_failed"
            ).formatted(Formatting.RED), false);
            refreshQuestUi(world, player);
        }
    }

    private static void succeedEncounter(ServerWorld world,
                                         ServerPlayerEntity player,
                                         ActiveEncounter encounter,
                                         String winsKey,
                                         int targetWins) {
        cleanupEncounterEntities(world, encounter, false);
        ACTIVE.remove(encounter.playerId);
        encounter.bossBar.clearPlayers();
        UUID playerId = encounter.playerId;
        int nextWins = Math.min(targetWins, StoryQuestService.getQuestInt(world, playerId, winsKey) + 1);
        StoryQuestService.setQuestInt(world, playerId, winsKey, nextWins);
        clearScheduledEncounter(world, playerId);
        if (nextWins < targetWins) {
            scheduleEncounter(world, player, encounter.spec.kind(), 1);
        }
        if (player != null) {
            player.sendMessage(Text.translatable(
                    encounter.spec.finalConvoy()
                            ? "message.village-quest.story.shadows_on_the_trade_road.final_saved"
                            : "message.village-quest.story.shadows_on_the_trade_road.rescue_saved"
            ).formatted(Formatting.GOLD), false);
            if (!encounter.spec.finalConvoy() && encounter.spec.kind() == RESCUE_KIND_HOLDING && nextWins >= targetWins) {
                player.sendMessage(Text.translatable("message.village-quest.story.shadows_on_the_trade_road.survivor_waiting").formatted(Formatting.GRAY), false);
            }
            StoryQuestService.completeIfEligible(world, player);
            refreshQuestUi(world, player);
        }
    }

    private static void cleanupEncounterEntities(ServerWorld world, ActiveEncounter encounter, boolean discardMerchants) {
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

    private static List<CaravanMerchantEntity> livingMerchants(ServerWorld world, ActiveEncounter encounter) {
        List<CaravanMerchantEntity> living = new ArrayList<>();
        for (UUID merchantId : encounter.merchantIds) {
            Entity entity = findEntity(world, merchantId);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                living.add(merchant);
            }
        }
        return living;
    }

    private static List<MobEntity> livingHostiles(ServerWorld world, ActiveEncounter encounter) {
        List<MobEntity> living = new ArrayList<>();
        for (UUID hostileId : encounter.hostileIds) {
            Entity entity = findEntity(world, hostileId);
            if (entity instanceof MobEntity hostile && hostile.isAlive() && !hostile.isRemoved()) {
                living.add(hostile);
            }
        }
        return living;
    }

    private static CaravanMerchantEntity nearestMerchant(Entity hostile, List<CaravanMerchantEntity> merchants) {
        CaravanMerchantEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (CaravanMerchantEntity merchant : merchants) {
            double distance = hostile.squaredDistanceTo(merchant);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = merchant;
            }
        }
        return nearest;
    }

    private static MobEntity nearestHostile(Entity source, List<MobEntity> hostiles) {
        if (source == null || hostiles == null || hostiles.isEmpty()) {
            return null;
        }
        MobEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (MobEntity hostile : hostiles) {
            if (hostile == null || !hostile.isAlive() || hostile.isRemoved()) {
                continue;
            }
            double distance = source.squaredDistanceTo(hostile);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = hostile;
            }
        }
        return nearest;
    }

    private static void scheduleEncounter(ServerWorld world, ServerPlayerEntity player, int kind, int nightsUntilStart) {
        if (world == null || player == null) {
            return;
        }
        BlockPos anchor = findEncounterAnchor(world, player.getBlockPos());
        int targetDay = currentWorldDay(world) + Math.max(0, nightsUntilStart);
        StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_RESCUE_TARGET_X, anchor.getX());
        StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_RESCUE_TARGET_Z, anchor.getZ());
        StoryQuestService.setQuestInt(
                world,
                player.getUuid(),
                kind == RESCUE_KIND_FINAL ? StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY : StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY,
                targetDay
        );
        StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_RESCUE_KIND, kind);
        if (kind == RESCUE_KIND_FINAL) {
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY, 0);
        } else {
            StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY, 0);
        }
    }

    private static void clearScheduledEncounter(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_X, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_Z, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY, 0);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_KIND, 0);
    }

    private static void ensureCourier(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        UUID existing = LETTER_COURIERS.get(player.getUuid());
        if (existing != null) {
            Entity entity = findEntity(world, existing);
            if (entity instanceof CaravanMerchantEntity merchant && merchant.isAlive() && !merchant.isRemoved()) {
                return;
            }
        }

        BlockPos spawnPos = findEncounterAnchor(world, player.getBlockPos()).withY(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ()));
        CaravanMerchantEntity courier = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
        courier.refreshPositionAndAngles(player.getX() + 2.0, spawnPos.getY(), player.getZ() + 2.0, world.random.nextFloat() * 360.0f, 0.0f);
        courier.setCourier(true);
        courier.setDespawnTicks(COURIER_DESPAWN_TICKS);
        courier.addCommandTag(TAG_CARAVAN);
        courier.addCommandTag(playerTag(player.getUuid()));
        courier.setCustomName(Text.translatable("entity.village-quest.caravan_courier"));
        if (world.spawnEntity(courier)) {
            LETTER_COURIERS.put(player.getUuid(), courier.getUuid());
        }
    }

    private static void clearCourier(ServerWorld world, UUID playerId) {
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

    private static void cleanupPlayerRuntime(ServerWorld world, UUID playerId, boolean preserveScheduledTarget) {
        ActiveEncounter encounter = ACTIVE.remove(playerId);
        if (encounter != null) {
            cleanupEncounterEntities(world, encounter, true);
            encounter.bossBar.clearPlayers();
        }
        if (!preserveScheduledTarget) {
            clearScheduledEncounter(world, playerId);
        }
    }

    private static ItemStack createGuildWarningLetter() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.village-quest.guild_warning_letter").formatted(Formatting.GOLD));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("item.village-quest.guild_warning_letter.lore").formatted(Formatting.DARK_GRAY)
        )));
        return stack;
    }

    private static boolean isGuildWarningLetter(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.PAPER)) {
            return false;
        }
        String expected = Text.translatable("item.village-quest.guild_warning_letter").getString();
        return stack.getName().getString().equals(expected);
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

    private static int targetDay(ServerWorld world, UUID playerId, boolean finalConvoy) {
        return StoryQuestService.getQuestInt(
                world,
                playerId,
                finalConvoy ? StoryQuestKeys.SHADOWS_FINAL_TARGET_DAY : StoryQuestKeys.SHADOWS_RESCUE_TARGET_DAY
        );
    }

    private static int scheduledEncounterKind(ServerWorld world, UUID playerId) {
        return StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.SHADOWS_RESCUE_KIND);
    }

    private static int currentWorldDay(ServerWorld world) {
        return world == null ? 0 : (int) (world.getTimeOfDay() / 24000L);
    }

    private static Text distressLabel(int kind) {
        return Text.translatable(
                kind == RESCUE_KIND_FINAL
                        ? "text.village-quest.special.surveyor_compass.mode.guild_convoy"
                        : "text.village-quest.special.surveyor_compass.mode.caravan_distress"
        );
    }

    private static BlockPos findEncounterAnchor(ServerWorld world, BlockPos origin) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int distance = MathHelper.nextInt(world.random, MIN_RESCUE_DISTANCE, MAX_RESCUE_DISTANCE);
            int x = origin.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + MathHelper.floor(Math.sin(angle) * distance);
            BlockPos base = safeSurface(world, x, z);
            if (base != null && world.getWorldBorder().contains(base) && hasDryEncounterFootprint(world, base)) {
                return base;
            }
        }
        BlockPos fallback = safeSurface(world, origin.getX() + MIN_RESCUE_DISTANCE, origin.getZ());
        return fallback != null && hasDryEncounterFootprint(world, fallback) ? fallback : origin;
    }

    private static BlockPos findMerchantSpawn(ServerWorld world, BlockPos anchor, List<BlockPos> existingSpawns) {
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

    private static BlockPos findSpacedRingSpawn(ServerWorld world,
                                                BlockPos anchor,
                                                int minRadius,
                                                int maxRadius,
                                                List<BlockPos> existingSpawns) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS * 3; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int distance = MathHelper.nextInt(world.random, minRadius, maxRadius);
            int x = anchor.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + MathHelper.floor(Math.sin(angle) * distance);
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
            if (existing != null && candidate.getSquaredDistance(existing) < minDistanceSquared) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos findRingSpawn(ServerWorld world, BlockPos anchor, int minRadius, int maxRadius) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int distance = MathHelper.nextInt(world.random, minRadius, maxRadius);
            int x = anchor.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + MathHelper.floor(Math.sin(angle) * distance);
            BlockPos pos = safeSurface(world, x, z);
            if (pos != null) {
                return pos;
            }
        }
        return anchor;
    }

    private static BlockPos safeSurface(ServerWorld world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, topY, z);
        if (!world.getBlockState(pos).isAir()) {
            pos = pos.up();
        }
        BlockPos belowPos = pos.down();
        BlockState feetState = world.getBlockState(pos);
        BlockState headState = world.getBlockState(pos.up());
        BlockState belowState = world.getBlockState(belowPos);
        if (!feetState.isAir() || !headState.isAir()) {
            return null;
        }
        if (belowState.isAir() || !belowState.getFluidState().isEmpty() || !belowState.isSolidBlock(world, belowPos)) {
            return null;
        }
        return pos;
    }

    private static boolean hasDryEncounterFootprint(ServerWorld world, BlockPos center) {
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

    private static Entity findEntity(ServerWorld world, UUID entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        for (Entity entity : world.iterateEntities()) {
            if (entityId.equals(entity.getUuid())) {
                return entity;
            }
        }
        return null;
    }

    private static String playerTag(UUID playerId) {
        return "vq_trade_player_" + playerId.toString();
    }

    private static void refreshQuestUi(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }
}
