package de.quest.content.story;

import de.quest.entity.CaravanMerchantEntity;
import de.quest.entity.QuestMasterEntity;
import de.quest.entity.TraitorEntity;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BannerItem;
import net.minecraft.world.Heightmap;

public final class EmptyCaravanStoryService {
    public static final int CLUE_TARGET = 3;
    public static final int WITNESS_TARGET = 3;
    public static final int BAIT_SCHEDULED = 1;
    public static final int BAIT_ACTIVE = 2;
    public static final int BAIT_WON = 3;

    private static final int MIN_TARGET_DISTANCE = 88;
    private static final int MAX_TARGET_DISTANCE = 150;
    private static final int TARGET_TRIGGER_RADIUS = 10;
    private static final String TAG_BAIT = "vq_empty_caravan_bait";
    private static final String TAG_ATTACKER = "vq_empty_caravan_attacker";
    private static final String TAG_OWNER_PREFIX = "vq_empty_caravan_owner_";
    private static final Map<UUID, BaitRuntime> ACTIVE_BAITS = new HashMap<>();

    private EmptyCaravanStoryService() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getOverworld().getTime() % 10L != 0L) {
            return;
        }
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            if (StoryQuestService.activeArcType(world, playerId) != StoryArcType.THE_EMPTY_CARAVAN) {
                cleanupPlayer(world, playerId);
                continue;
            }
            int chapter = StoryQuestService.chapterIndex(world, playerId, StoryArcType.THE_EMPTY_CARAVAN);
            if (chapter == 0) {
                tickEmptySite(world, player);
            } else if (chapter == 1) {
                tickClueTrail(world, player);
            } else if (chapter == 4) {
                tickBait(world, player);
            } else {
                cleanupPlayer(world, playerId);
            }
        }
    }

    public static void resetRuntimeState() {
        ACTIVE_BAITS.clear();
    }

    public static void despawnAll(ServerWorld world) {
        if (world != null) {
            for (BaitRuntime runtime : ACTIVE_BAITS.values()) {
                discardEntities(world, runtime);
            }
            for (Entity entity : allEntities(world)) {
                if (entity.getCommandTags().contains(TAG_BAIT) || entity.getCommandTags().contains(TAG_ATTACKER)) {
                    entity.discard();
                }
            }
        }
        resetRuntimeState();
    }

    public static void beginEmptySite(ServerWorld world, ServerPlayerEntity player) {
        scheduleTarget(world, player, "message.village-quest.story.the_empty_caravan.target.empty_site");
    }

    public static void beginClueTrail(ServerWorld world, ServerPlayerEntity player) {
        scheduleTarget(world, player, "message.village-quest.story.the_empty_caravan.target.clue");
    }

    public static boolean chooseApproach(ServerWorld world,
                                         ServerPlayerEntity player,
                                         Entity entity,
                                         ItemStack inHand) {
        if (world == null
                || player == null
                || !(entity instanceof QuestMasterEntity)
                || inHand == null
                || StoryQuestService.activeArcType(world, player.getUuid()) != StoryArcType.THE_EMPTY_CARAVAN
                || StoryQuestService.chapterIndex(world, player.getUuid(), StoryArcType.THE_EMPTY_CARAVAN) != 4) {
            return false;
        }
        boolean amnesty = inHand.getItem() instanceof BannerItem;
        boolean justice = inHand.isOf(Items.IRON_SWORD);
        if (!amnesty && !justice) {
            return false;
        }
        UUID playerId = player.getUuid();
        StoryQuestService.setStoryFlag(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY, amnesty);
        StoryQuestService.setStoryFlag(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CHOICE_JUSTICE, justice);
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE, BAIT_SCHEDULED);
        scheduleTarget(world, player, "message.village-quest.story.the_empty_caravan.target.bait");
        player.sendMessage(Text.translatable(amnesty
                ? "message.village-quest.story.the_empty_caravan.choice.amnesty"
                : "message.village-quest.story.the_empty_caravan.choice.justice").formatted(Formatting.GOLD), false);
        return true;
    }

    public static BlockPos currentTarget(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null
                || StoryQuestService.activeArcType(world, playerId) != StoryArcType.THE_EMPTY_CARAVAN) {
            return null;
        }
        int x = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_TARGET_X);
        int z = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_TARGET_Z);
        if (x == 0 && z == 0) {
            return null;
        }
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    public static Text currentTargetLabel(ServerWorld world, UUID playerId) {
        if (currentTarget(world, playerId) == null) {
            return null;
        }
        int chapter = StoryQuestService.chapterIndex(world, playerId, StoryArcType.THE_EMPTY_CARAVAN);
        return Text.translatable(switch (chapter) {
            case 0 -> "text.village-quest.special.surveyor_compass.mode.empty_caravan";
            case 1 -> "text.village-quest.special.surveyor_compass.mode.forged_trail";
            case 4 -> "text.village-quest.special.surveyor_compass.mode.bait_caravan";
            default -> "text.village-quest.special.surveyor_compass.mode.empty_caravan";
        });
    }

    private static void tickEmptySite(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_SITE_FOUND) > 0) {
            return;
        }
        ensureTarget(world, player, "message.village-quest.story.the_empty_caravan.target.empty_site");
        BlockPos target = currentTarget(world, playerId);
        if (!isNear(player, target, TARGET_TRIGGER_RADIUS)) {
            return;
        }
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_SITE_FOUND, 1);
        world.spawnParticles(ParticleTypes.SMOKE, target.getX() + 0.5, target.getY() + 0.8, target.getZ() + 0.5,
                18, 2.5, 0.5, 2.5, 0.02);
        world.playSound(null, target, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.7f, 0.8f);
        player.sendMessage(Text.translatable("message.village-quest.story.the_empty_caravan.site_found")
                .formatted(Formatting.GOLD), false);
        StoryQuestService.completeIfEligible(world, player);
    }

    private static void tickClueTrail(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        int clues = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CLUES);
        if (clues >= CLUE_TARGET) {
            return;
        }
        ensureTarget(world, player, "message.village-quest.story.the_empty_caravan.target.clue");
        BlockPos target = currentTarget(world, playerId);
        if (!isNear(player, target, 7)) {
            return;
        }
        clues++;
        StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_CLUES, clues);
        world.spawnParticles(ParticleTypes.WAX_OFF, target.getX() + 0.5, target.getY() + 0.4, target.getZ() + 0.5,
                12, 1.2, 0.2, 1.2, 0.02);
        world.playSound(null, target, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 0.95f + clues * 0.08f);
        player.sendMessage(Text.translatable("message.village-quest.story.the_empty_caravan.clue_found",
                clues, CLUE_TARGET).formatted(Formatting.GREEN), false);
        if (clues < CLUE_TARGET) {
            scheduleTarget(world, player, "message.village-quest.story.the_empty_caravan.target.clue");
        }
        StoryQuestService.completeIfEligible(world, player);
    }

    private static void tickBait(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        int state = StoryQuestService.getQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE);
        if (state == BAIT_WON || state == 0) {
            return;
        }
        if (state == BAIT_ACTIVE && !ACTIVE_BAITS.containsKey(playerId)) {
            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE, BAIT_SCHEDULED);
            state = BAIT_SCHEDULED;
        }
        ensureTarget(world, player, "message.village-quest.story.the_empty_caravan.target.bait");
        BlockPos target = currentTarget(world, playerId);
        if (state == BAIT_SCHEDULED && isNear(player, target, 22)) {
            BaitRuntime runtime = spawnBait(world, player, target);
            if (runtime != null) {
                ACTIVE_BAITS.put(playerId, runtime);
                StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE, BAIT_ACTIVE);
                player.sendMessage(Text.translatable("message.village-quest.story.the_empty_caravan.bait_sprung")
                        .formatted(Formatting.RED), false);
                world.playSound(null, target, SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 0.8f, 1.1f);
            }
            return;
        }
        BaitRuntime runtime = ACTIVE_BAITS.get(playerId);
        if (runtime == null) {
            return;
        }
        runtime.attackerIds.removeIf(id -> {
            Entity entity = findEntity(world, id);
            return entity == null || entity.isRemoved() || !entity.isAlive();
        });
        if (runtime.attackerIds.isEmpty()) {
            discardEntities(world, runtime);
            ACTIVE_BAITS.remove(playerId);
            StoryQuestService.setQuestInt(world, playerId, StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE, BAIT_WON);
            player.sendMessage(Text.translatable("message.village-quest.story.the_empty_caravan.bait_won")
                    .formatted(Formatting.GREEN), false);
            StoryQuestService.completeIfEligible(world, player);
        }
    }

    private static BaitRuntime spawnBait(ServerWorld world, ServerPlayerEntity player, BlockPos target) {
        BaitRuntime runtime = new BaitRuntime();
        for (int i = 0; i < 3; i++) {
            BlockPos spawn = safeSurface(world, target.getX() + i * 2 - 2, target.getZ());
            if (spawn == null) {
                continue;
            }
            CaravanMerchantEntity merchant = new CaravanMerchantEntity(ModEntities.CARAVAN_MERCHANT, world);
            merchant.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            merchant.setDespawnTicks(20 * 60 * 10);
            merchant.refreshEncounterControl(i == 0);
            merchant.setCustomName(Text.translatable("entity.village-quest.bait_caravan_merchant"));
            merchant.addCommandTag(TAG_BAIT);
            merchant.addCommandTag(ownerTag(player.getUuid()));
            if (world.isSpaceEmpty(merchant) && world.spawnEntity(merchant)) {
                runtime.merchantIds.add(merchant.getUuid());
            }
        }
        boolean amnesty = StoryQuestService.hasStoryFlag(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_CHOICE_AMNESTY);
        int attackerCount = amnesty ? 3 : 5;
        for (int i = 0; i < attackerCount; i++) {
            double angle = Math.PI * 2.0 * i / attackerCount;
            BlockPos spawn = safeSurface(world,
                    target.getX() + (int) Math.round(Math.cos(angle) * 9.0),
                    target.getZ() + (int) Math.round(Math.sin(angle) * 9.0));
            if (spawn == null) {
                continue;
            }
            TraitorEntity traitor = new TraitorEntity(ModEntities.TRAITOR, world);
            traitor.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            traitor.addCommandTag(TAG_ATTACKER);
            traitor.addCommandTag(ownerTag(player.getUuid()));
            if (world.isSpaceEmpty(traitor) && world.spawnEntity(traitor)) {
                traitor.setTarget(player);
                runtime.attackerIds.add(traitor.getUuid());
            }
        }
        if (runtime.attackerIds.isEmpty()) {
            discardEntities(world, runtime);
            return null;
        }
        return runtime;
    }

    private static void ensureTarget(ServerWorld world, ServerPlayerEntity player, String messageKey) {
        if (currentTarget(world, player.getUuid()) == null) {
            scheduleTarget(world, player, messageKey);
        }
    }

    private static void scheduleTarget(ServerWorld world, ServerPlayerEntity player, String messageKey) {
        BlockPos target = findTarget(world, player.getBlockPos());
        StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_TARGET_X, target.getX());
        StoryQuestService.setQuestInt(world, player.getUuid(), StoryQuestKeys.EMPTY_CARAVAN_TARGET_Z, target.getZ());
        SurveyorCompassQuestService.selectEmptyCaravanMode(world, player.getUuid());
        player.sendMessage(Text.translatable(messageKey).formatted(Formatting.YELLOW), false);
    }

    private static BlockPos findTarget(ServerWorld world, BlockPos origin) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = MIN_TARGET_DISTANCE + world.random.nextInt(MAX_TARGET_DISTANCE - MIN_TARGET_DISTANCE + 1);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = safeSurface(world, x, z);
            if (surface != null && world.getWorldBorder().contains(surface)) {
                return surface;
            }
        }
        int x = origin.getX() + MIN_TARGET_DISTANCE;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, origin.getZ());
        return new BlockPos(x, y, origin.getZ());
    }

    private static BlockPos safeSurface(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        if (!world.getBlockState(feet).isAir()) {
            feet = feet.up();
        }
        BlockPos below = feet.down();
        if (!world.getBlockState(feet).isAir()
                || !world.getBlockState(feet.up()).isAir()
                || world.getBlockState(below).isAir()
                || !world.getBlockState(below).getFluidState().isEmpty()) {
            return null;
        }
        return feet;
    }

    private static boolean isNear(ServerPlayerEntity player, BlockPos target, int radius) {
        return player != null && target != null && player.getBlockPos().getSquaredDistance(target) <= radius * (double) radius;
    }

    private static void cleanupPlayer(ServerWorld world, UUID playerId) {
        BaitRuntime runtime = ACTIVE_BAITS.remove(playerId);
        if (runtime != null) {
            discardEntities(world, runtime);
        }
    }

    private static void discardEntities(ServerWorld world, BaitRuntime runtime) {
        for (UUID id : runtime.allIds()) {
            Entity entity = findEntity(world, id);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static Entity findEntity(ServerWorld world, UUID entityId) {
        for (Entity entity : world.iterateEntities()) {
            if (entityId.equals(entity.getUuid())) {
                return entity;
            }
        }
        return null;
    }

    private static List<Entity> allEntities(ServerWorld world) {
        List<Entity> entities = new ArrayList<>();
        if (world != null) {
            for (Entity entity : world.iterateEntities()) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private static String ownerTag(UUID playerId) {
        return TAG_OWNER_PREFIX + playerId;
    }

    private static final class BaitRuntime {
        private final List<UUID> merchantIds = new ArrayList<>();
        private final List<UUID> attackerIds = new ArrayList<>();

        private List<UUID> allIds() {
            List<UUID> ids = new ArrayList<>(merchantIds);
            ids.addAll(attackerIds);
            return ids;
        }
    }
}
