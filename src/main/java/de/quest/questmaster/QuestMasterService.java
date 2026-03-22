package de.quest.questmaster;

import de.quest.entity.QuestMasterEntity;
import de.quest.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

public final class QuestMasterService {
    private static final int MIN_SPAWN_DISTANCE = 4;
    private static final int MAX_SPAWN_DISTANCE = 10;
    private static final int MAX_SPAWN_ATTEMPTS = 12;
    private static final double MAX_INTERACT_DISTANCE_SQUARED = 64.0;
    private static final double NEARBY_QUESTMASTER_DISTANCE_SQUARED = 400.0;

    private QuestMasterService() {}

    public static boolean isEligibleSpawnTarget(PlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    public static QuestMasterEntity spawnNearPlayer(ServerWorld world, ServerPlayerEntity anchor) {
        if (world == null || anchor == null || !isEligibleSpawnTarget(anchor)) {
            return null;
        }
        if (findNearbyQuestMaster(world, anchor.getX(), anchor.getY(), anchor.getZ()) != null) {
            return null;
        }

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            BlockPos spawnPos = findSpawnPos(world, anchor);
            if (spawnPos == null) {
                continue;
            }

            QuestMasterEntity questMaster = new QuestMasterEntity(ModEntities.QUEST_MASTER, world);
            float yaw = world.random.nextFloat() * 360.0f;
            questMaster.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    yaw,
                    0.0f
            );

            if (!world.isSpaceEmpty(questMaster)) {
                continue;
            }
            if (!world.spawnEntity(questMaster)) {
                continue;
            }
            return questMaster;
        }
        return null;
    }

    public static void interact(ServerWorld world, ServerPlayerEntity player, QuestMasterEntity questMaster) {
        if (world == null || player == null || questMaster == null) {
            return;
        }
        if (player.squaredDistanceTo(questMaster) > MAX_INTERACT_DISTANCE_SQUARED) {
            player.sendMessage(Text.translatable("message.village-quest.questmaster.too_far").formatted(Formatting.RED), false);
            return;
        }

        questMaster.beginInteraction(player);
        questMaster.getLookControl().lookAt(player.getX(), player.getEyeY(), player.getZ());
        QuestMasterUiService.open(world, player, questMaster);
    }

    public static QuestMasterEntity findNearbyQuestMaster(ServerWorld world, double x, double y, double z) {
        if (world == null) {
            return null;
        }
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof QuestMasterEntity questMaster
                    && questMaster.isAlive()
                    && questMaster.squaredDistanceTo(x, y, z) <= NEARBY_QUESTMASTER_DISTANCE_SQUARED) {
                return questMaster;
            }
        }
        return null;
    }

    public static List<QuestMasterEntity> getQuestMasters(ServerWorld world) {
        List<QuestMasterEntity> questMasters = new ArrayList<>();
        if (world == null) {
            return questMasters;
        }
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof QuestMasterEntity questMaster && questMaster.isAlive()) {
                questMasters.add(questMaster);
            }
        }
        return questMasters;
    }

    public static double getMaxInteractDistanceSquared() {
        return MAX_INTERACT_DISTANCE_SQUARED;
    }

    private static BlockPos findSpawnPos(ServerWorld world, ServerPlayerEntity anchor) {
        BlockPos origin = anchor.getBlockPos();
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int distance = MathHelper.nextInt(world.random, MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);
            int x = origin.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + MathHelper.floor(Math.sin(angle) * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feetPos = new BlockPos(x, y, z);
            BlockPos groundPos = feetPos.down();

            if (!world.getWorldBorder().contains(feetPos)) {
                continue;
            }
            if (y <= world.getBottomY()) {
                continue;
            }
            if (!world.getBlockState(groundPos).isSideSolidFullSquare(world, groundPos, Direction.UP)) {
                continue;
            }
            if (!world.getBlockState(feetPos).isAir() || !world.getBlockState(feetPos.up()).isAir()) {
                continue;
            }
            return feetPos;
        }
        return null;
    }
}
