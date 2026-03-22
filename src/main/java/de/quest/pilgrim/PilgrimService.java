package de.quest.pilgrim;

import de.quest.data.QuestState;
import de.quest.entity.PilgrimEntity;
import de.quest.network.Payloads;
import de.quest.registry.ModEntities;
import de.quest.shop.ShopOffer;
import de.quest.shop.ShopService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
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

public final class PilgrimService {
    private static final int NATURAL_SPAWN_CHECK_INTERVAL = 6000;
    private static final float NATURAL_SPAWN_CHANCE = 0.30f;
    private static final long NATURAL_SPAWN_COOLDOWN_TICKS = 24000L;
    private static final int MIN_SPAWN_DISTANCE = 24;
    private static final int MAX_SPAWN_DISTANCE = 48;
    private static final int MAX_SPAWN_ATTEMPTS = 16;
    private static final double MAX_INTERACT_DISTANCE_SQUARED = 64.0;

    private PilgrimService() {}

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) {
            return;
        }
        if (world.getTime() % NATURAL_SPAWN_CHECK_INTERVAL != 0L) {
            return;
        }
        if (!ShopService.hasOffers()
                || findActivePilgrim(world) != null
                || isNaturalSpawnCooldownActive(world)
                || !isPilgrimSpawnTime(world)) {
            return;
        }

        List<ServerPlayerEntity> candidates = world.getPlayers(PilgrimService::isEligibleSpawnTarget);
        if (candidates.isEmpty() || world.random.nextFloat() > NATURAL_SPAWN_CHANCE) {
            return;
        }

        ServerPlayerEntity anchor = candidates.get(world.random.nextInt(candidates.size()));
        spawnNearPlayer(world, anchor, true);
    }

    public static boolean isEligibleSpawnTarget(PlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    public static boolean isPilgrimSpawnTime(ServerWorld world) {
        long dayTime = Math.floorMod(world.getTimeOfDay(), 24000L);
        return dayTime >= 1000L && dayTime <= 12000L;
    }

    public static List<String> rollOfferIds(net.minecraft.util.math.random.Random random, int count) {
        return ShopService.rollPilgrimOfferIds(random, count);
    }

    public static PilgrimEntity spawnNearPlayer(ServerWorld world, ServerPlayerEntity anchor, boolean announce) {
        if (world == null || anchor == null || !isEligibleSpawnTarget(anchor)) {
            return null;
        }
        if (findActivePilgrim(world) != null) {
            return null;
        }

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            BlockPos spawnPos = findSpawnPos(world, anchor);
            if (spawnPos == null) {
                continue;
            }

            PilgrimEntity pilgrim = new PilgrimEntity(ModEntities.PILGRIM, world);
            float yaw = world.random.nextFloat() * 360.0f;
            pilgrim.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    yaw,
                    0.0f
            );
            pilgrim.setOfferIds(ShopService.rollPilgrimOfferIds(world.random, world, anchor.getUuid(), PilgrimEntity.DEFAULT_OFFER_COUNT));
            pilgrim.setDespawnTicks(PilgrimEntity.DEFAULT_DESPAWN_TICKS);

            if (!world.isSpaceEmpty(pilgrim)) {
                continue;
            }
            if (!world.spawnEntity(pilgrim)) {
                continue;
            }

            if (announce) {
                anchor.sendMessage(Text.translatable("message.village-quest.pilgrim.arrived").formatted(Formatting.GOLD), false);
            }
            return pilgrim;
        }
        return null;
    }

    public static int despawnAll(ServerWorld world) {
        if (world == null) {
            return 0;
        }
        List<PilgrimEntity> pilgrims = getPilgrims(world);
        for (PilgrimEntity pilgrim : pilgrims) {
            ServerPlayerEntity customer = pilgrim.getCustomer();
            if (customer != null) {
                closeTrade(customer);
                pilgrim.clearCustomer();
            }
            pilgrim.discard();
        }
        if (!pilgrims.isEmpty()) {
            beginNaturalSpawnCooldown(world);
        }
        return pilgrims.size();
    }

    public static PilgrimEntity findActivePilgrim(ServerWorld world) {
        if (world == null) {
            return null;
        }
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof PilgrimEntity pilgrim && pilgrim.isAlive()) {
                return pilgrim;
            }
        }
        return null;
    }

    public static void openTrade(ServerWorld world, ServerPlayerEntity player, PilgrimEntity pilgrim) {
        if (world == null || player == null || pilgrim == null) {
            return;
        }
        if (player.squaredDistanceTo(pilgrim) > MAX_INTERACT_DISTANCE_SQUARED) {
            player.sendMessage(Text.translatable("message.village-quest.pilgrim.too_far").formatted(Formatting.RED), false);
            return;
        }
        pilgrim.setCustomer(player);
        ServerPlayNetworking.send(player, buildPayload(Payloads.PilgrimTradePayload.ACTION_OPEN, world, player, pilgrim));
    }

    public static void closeTrade(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new Payloads.PilgrimTradePayload(
                Payloads.PilgrimTradePayload.ACTION_CLOSE,
                -1,
                Text.empty(),
                0L,
                0,
                List.of()
        ));
    }

    public static void refreshTrade(ServerWorld world, ServerPlayerEntity player, PilgrimEntity pilgrim) {
        if (world == null || player == null || pilgrim == null) {
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(Payloads.PilgrimTradePayload.ACTION_UPDATE, world, player, pilgrim));
    }

    public static void handleTradeAction(ServerPlayerEntity player, Payloads.PilgrimTradeActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Entity entity = world.getEntityById(payload.entityId());
        if (!(entity instanceof PilgrimEntity pilgrim) || !pilgrim.isAlive()) {
            closeTrade(player);
            return;
        }

        if (player.squaredDistanceTo(pilgrim) > MAX_INTERACT_DISTANCE_SQUARED) {
            player.sendMessage(Text.translatable("message.village-quest.pilgrim.too_far").formatted(Formatting.RED), false);
            pilgrim.clearCustomer();
            closeTrade(player);
            return;
        }
        if (!pilgrim.isCustomer(player)) {
            closeTrade(player);
            return;
        }

        if (!pilgrim.hasOffer(payload.offerId())) {
            player.sendMessage(Text.translatable("command.village-quest.shop.offer_missing").formatted(Formatting.RED), false);
            refreshTrade(world, player, pilgrim);
            return;
        }

        ShopService.buy(world, player, payload.offerId());
        refreshTrade(world, player, pilgrim);
    }

    public static void handleTradeSession(ServerPlayerEntity player, Payloads.PilgrimTradeSessionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        if (payload.action() != Payloads.PilgrimTradeSessionPayload.ACTION_CLOSE) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Entity entity = world.getEntityById(payload.entityId());
        if (entity instanceof PilgrimEntity pilgrim && pilgrim.isCustomer(player)) {
            pilgrim.clearCustomer();
        }
    }

    public static List<Payloads.PilgrimTradeOfferData> buildOfferData(ServerWorld world, ServerPlayerEntity player, PilgrimEntity pilgrim) {
        List<Payloads.PilgrimTradeOfferData> offers = new ArrayList<>();
        if (world == null || player == null || pilgrim == null) {
            return offers;
        }

        for (String offerId : pilgrim.getOfferIds()) {
            if (!ShopService.isOfferUnlocked(world, player.getUuid(), offerId)) {
                continue;
            }
            ShopOffer offer = ShopService.offer(offerId);
            if (offer == null) {
                continue;
            }
            offers.add(new Payloads.PilgrimTradeOfferData(
                    offer.id(),
                    offer.title(),
                    offer.description(),
                    offer.price(),
                    offer.previewStack(world)
            ));
        }
        return offers;
    }

    public static List<PilgrimEntity> getPilgrims(ServerWorld world) {
        List<PilgrimEntity> pilgrims = new ArrayList<>();
        if (world == null) {
            return pilgrims;
        }
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof PilgrimEntity pilgrim && pilgrim.isAlive()) {
                pilgrims.add(pilgrim);
            }
        }
        return pilgrims;
    }

    public static double getMaxInteractDistanceSquared() {
        return MAX_INTERACT_DISTANCE_SQUARED;
    }

    public static void beginNaturalSpawnCooldown(ServerWorld world) {
        if (world == null || world.getServer() == null) {
            return;
        }
        QuestState.get(world.getServer()).setPilgrimNaturalSpawnCooldownUntil(world.getTime() + NATURAL_SPAWN_COOLDOWN_TICKS);
    }

    private static Payloads.PilgrimTradePayload buildPayload(int action,
                                                             ServerWorld world,
                                                             ServerPlayerEntity player,
                                                             PilgrimEntity pilgrim) {
        return new Payloads.PilgrimTradePayload(
                action,
                pilgrim.getId(),
                pilgrim.getDisplayName(),
                de.quest.economy.CurrencyService.getBalance(world, player.getUuid()),
                pilgrim.getDespawnTicks(),
                buildOfferData(world, player, pilgrim)
        );
    }

    private static boolean isNaturalSpawnCooldownActive(ServerWorld world) {
        if (world == null || world.getServer() == null) {
            return false;
        }
        return world.getTime() < QuestState.get(world.getServer()).getPilgrimNaturalSpawnCooldownUntil();
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
