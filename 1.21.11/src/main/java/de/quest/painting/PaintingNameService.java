package de.quest.painting;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PaintingNameService {
    private static final int CLEANUP_INTERVAL_TICKS = 20;
    // Track all paintings so a later /data variant change is handled without an entity-wide scan.
    private static final Map<ServerLevel, Set<Painting>> LOADED = new HashMap<>();

    private PaintingNameService() {}

    public static void onEntityLoad(Entity entity, ServerLevel world) {
        if (entity instanceof Painting painting) {
            LOADED.computeIfAbsent(world, ignored -> new HashSet<>()).add(painting);
            cleanup(painting);
        }
    }

    public static void onEntityUnload(Entity entity, ServerLevel world) {
        if (!(entity instanceof Painting painting)) return;
        var paintings = LOADED.get(world); if (paintings == null) return;
        paintings.remove(painting); if (paintings.isEmpty()) LOADED.remove(world);
    }

    public static void clear() { LOADED.clear(); }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerLevel world : server.getAllLevels()) {
            var paintings = LOADED.get(world);
            if (paintings == null) continue;
            paintings.removeIf(Entity::isRemoved);
            paintings.forEach(PaintingNameService::cleanup);
        }
    }

    private static void cleanup(Painting painting) {
        if (!isVillageQuestPainting(painting)
                || painting.getCustomName() == null && !painting.isCustomNameVisible()) return;
        painting.setCustomName(null);
        painting.setCustomNameVisible(false);
    }

    private static boolean isVillageQuestPainting(Painting painting) {
        Holder<PaintingVariant> variant = painting.getVariant();
        return PaintingStackFactory.isVillageQuestPainting(variant);
    }
}
