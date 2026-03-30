package de.quest.painting;

import de.quest.VillageQuest;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

public final class PaintingNameService {
    private static final int CLEANUP_INTERVAL_TICKS = 20;

    private PaintingNameService() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerLevel world : server.getAllLevels()) {
            cleanupWorld(world);
        }
    }

    private static void cleanupWorld(ServerLevel world) {
        if (world == null) {
            return;
        }

        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof Painting painting)) {
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

    private static boolean isVillageQuestPainting(Painting painting) {
        Holder<PaintingVariant> variant = painting.getVariant();
        if (variant == null) {
            return false;
        }

        ResourceKey<PaintingVariant> key = variant.unwrapKey().orElse(null);
        return key != null && VillageQuest.MOD_ID.equals(key.identifier().getNamespace());
    }
}
