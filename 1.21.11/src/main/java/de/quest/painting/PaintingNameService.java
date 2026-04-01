package de.quest.painting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.decoration.painting.PaintingVariant;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

public final class PaintingNameService {
    private static final int CLEANUP_INTERVAL_TICKS = 20;

    private PaintingNameService() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTicks() % CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            cleanupWorld(world);
        }
    }

    private static void cleanupWorld(ServerWorld world) {
        if (world == null) {
            return;
        }

        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof PaintingEntity painting)) {
                continue;
            }
            if (!isVillageQuestPainting(painting)) {
                continue;
            }
            if (painting.getCustomName() == null && !painting.isCustomNameVisible()) {
                continue;
            }

            painting.setCustomName(null);
            painting.setCustomNameVisible(false);
        }
    }

    private static boolean isVillageQuestPainting(PaintingEntity painting) {
        RegistryEntry<PaintingVariant> variant = painting.getVariant();
        return PaintingStackFactory.isVillageQuestPainting(variant);
    }
}
