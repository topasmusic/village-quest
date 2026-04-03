package de.quest.pilgrim;

import de.quest.data.QuestState;
import de.quest.entity.PilgrimEntity;
import de.quest.network.Payloads;
import de.quest.registry.ModEntities;
import de.quest.shop.ShopOffer;
import de.quest.shop.ShopService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
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
        ServerLevel world = server.overworld();
        if (world == null) {
            return;
        }
        if (world.getGameTime() % NATURAL_SPAWN_CHECK_INTERVAL != 0L) {
            return;
        }
        if (!ShopService.hasOffers()
                || findActivePilgrim(world) != null
                || isNaturalSpawnCooldownActive(world)
                || !isPilgrimSpawnTime(world)) {
            return;
        }

        List<ServerPlayer> candidates = world.getPlayers(PilgrimService::isEligibleSpawnTarget);
        if (candidates.isEmpty() || world.getRandom().nextFloat() > NATURAL_SPAWN_CHANCE) {
            return;
        }

        ServerPlayer anchor = candidates.get(world.getRandom().nextInt(candidates.size()));
        spawnNearPlayer(world, anchor, true);
    }

    public static boolean isEligibleSpawnTarget(Player player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    public static boolean isPilgrimSpawnTime(ServerLevel world) {
        long dayTime = Math.floorMod(world.getOverworldClockTime(), 24000L);
        return dayTime >= 1000L && dayTime <= 12000L;
    }

    public static List<String> rollOfferIds(net.minecraft.util.RandomSource random, int count) {
        return ShopService.rollPilgrimOfferIds(random, count);
    }

    public static PilgrimEntity spawnNearPlayer(ServerLevel world, ServerPlayer anchor, boolean announce) {
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
            float yaw = world.getRandom().nextFloat() * 360.0f;
            pilgrim.snapTo(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    yaw,
                    0.0f
            );
            pilgrim.setOfferIds(ShopService.rollPilgrimOfferIds(world.getRandom(), world, anchor.getUUID(), PilgrimEntity.DEFAULT_OFFER_COUNT));
            pilgrim.setDespawnTicks(PilgrimEntity.DEFAULT_DESPAWN_TICKS);
            pilgrim.beginArrivalTracking(anchor);

            if (!world.noCollision(pilgrim)) {
                continue;
            }
            if (!world.addFreshEntity(pilgrim)) {
                continue;
            }

            if (announce) {
                anchor.sendSystemMessage(Component.translatable("message.village-quest.pilgrim.arrived").withStyle(ChatFormatting.GOLD), false);
            }
            return pilgrim;
        }
        return null;
    }

    public static int despawnAll(ServerLevel world) {
        if (world == null) {
            return 0;
        }
        List<PilgrimEntity> pilgrims = getPilgrims(world);
        for (PilgrimEntity pilgrim : pilgrims) {
            ServerPlayer customer = pilgrim.getCustomer();
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

    public static PilgrimEntity findActivePilgrim(ServerLevel world) {
        if (world == null) {
            return null;
        }
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof PilgrimEntity pilgrim && pilgrim.isAlive()) {
                return pilgrim;
            }
        }
        return null;
    }

    public static void openTrade(ServerLevel world, ServerPlayer player, PilgrimEntity pilgrim) {
        if (world == null || player == null || pilgrim == null) {
            return;
        }
        if (player.distanceToSqr(pilgrim) > MAX_INTERACT_DISTANCE_SQUARED) {
            player.sendSystemMessage(Component.translatable("message.village-quest.pilgrim.too_far").withStyle(ChatFormatting.RED), false);
            return;
        }
        pilgrim.setCustomer(player);
        ServerPlayNetworking.send(player, buildPayload(Payloads.PilgrimTradePayload.ACTION_OPEN, world, player, pilgrim));
    }

    public static void closeTrade(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new Payloads.PilgrimTradePayload(
                Payloads.PilgrimTradePayload.ACTION_CLOSE,
                -1,
                Component.empty(),
                0L,
                0,
                List.of(),
                List.of()
        ));
    }

    public static void refreshTrade(ServerLevel world, ServerPlayer player, PilgrimEntity pilgrim) {
        if (world == null || player == null || pilgrim == null) {
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(Payloads.PilgrimTradePayload.ACTION_UPDATE, world, player, pilgrim));
    }

    public static void handleTradeAction(ServerPlayer player, Payloads.PilgrimTradeActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        Entity entity = world.getEntity(payload.entityId());
        if (!(entity instanceof PilgrimEntity pilgrim) || !pilgrim.isAlive()) {
            closeTrade(player);
            return;
        }

        if (player.distanceToSqr(pilgrim) > MAX_INTERACT_DISTANCE_SQUARED) {
            player.sendSystemMessage(Component.translatable("message.village-quest.pilgrim.too_far").withStyle(ChatFormatting.RED), false);
            pilgrim.clearCustomer();
            closeTrade(player);
            return;
        }
        if (!pilgrim.isCustomer(player)) {
            closeTrade(player);
            return;
        }

        if (payload.offerId() != null && payload.offerId().startsWith(PilgrimContractService.ACTION_ENTRY_ID)) {
            String contractId = payload.offerId().length() > PilgrimContractService.ACTION_ENTRY_ID.length() + 1
                    ? payload.offerId().substring(PilgrimContractService.ACTION_ENTRY_ID.length() + 1)
                    : null;
            PilgrimContractService.handleContractAction(world, player, contractId);
            refreshTrade(world, player, pilgrim);
            return;
        }

        if (!pilgrim.hasOffer(payload.offerId())) {
            player.sendSystemMessage(Component.translatable("command.village-quest.shop.offer_missing").withStyle(ChatFormatting.RED), false);
            refreshTrade(world, player, pilgrim);
            return;
        }

        ShopService.buy(world, player, payload.offerId());
        refreshTrade(world, player, pilgrim);
    }

    public static void handleTradeSession(ServerPlayer player, Payloads.PilgrimTradeSessionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        if (payload.action() != Payloads.PilgrimTradeSessionPayload.ACTION_CLOSE) {
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        Entity entity = world.getEntity(payload.entityId());
        if (entity instanceof PilgrimEntity pilgrim && pilgrim.isCustomer(player)) {
            pilgrim.clearCustomer();
        }
    }

    public static List<Payloads.PilgrimTradeOfferData> buildOfferData(ServerLevel world, ServerPlayer player, PilgrimEntity pilgrim) {
        List<Payloads.PilgrimTradeOfferData> offers = new ArrayList<>();
        if (world == null || player == null || pilgrim == null) {
            return offers;
        }

        for (String offerId : pilgrim.getOfferIds()) {
            if (!ShopService.isOfferUnlocked(world, player.getUUID(), offerId)) {
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

    public static List<PilgrimEntity> getPilgrims(ServerLevel world) {
        List<PilgrimEntity> pilgrims = new ArrayList<>();
        if (world == null) {
            return pilgrims;
        }
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof PilgrimEntity pilgrim && pilgrim.isAlive()) {
                pilgrims.add(pilgrim);
            }
        }
        return pilgrims;
    }

    public static double getMaxInteractDistanceSquared() {
        return MAX_INTERACT_DISTANCE_SQUARED;
    }

    public static void refreshIfTrading(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        PilgrimEntity pilgrim = findActivePilgrim(world);
        if (pilgrim != null && pilgrim.isCustomer(player)) {
            refreshTrade(world, player, pilgrim);
        }
    }

    public static void beginNaturalSpawnCooldown(ServerLevel world) {
        if (world == null || world.getServer() == null) {
            return;
        }
        QuestState.get(world.getServer()).setPilgrimNaturalSpawnCooldownUntil(world.getGameTime() + NATURAL_SPAWN_COOLDOWN_TICKS);
    }

    private static Payloads.PilgrimTradePayload buildPayload(int action,
                                                             ServerLevel world,
                                                             ServerPlayer player,
                                                             PilgrimEntity pilgrim) {
        return new Payloads.PilgrimTradePayload(
                action,
                pilgrim.getId(),
                pilgrim.getDisplayName(),
                de.quest.economy.CurrencyService.getBalance(world, player.getUUID()),
                pilgrim.getDespawnTicks(),
                buildOfferData(world, player, pilgrim),
                buildContractData(world, player)
        );
    }

    private static List<Payloads.PilgrimContractData> buildContractData(ServerLevel world, ServerPlayer player) {
        List<PilgrimContractService.PilgrimContractView> views = PilgrimContractService.buildViews(world, player);
        if (views.isEmpty()) {
            return List.of();
        }
        List<Payloads.PilgrimContractData> payloads = new ArrayList<>(views.size());
        for (PilgrimContractService.PilgrimContractView view : views) {
            payloads.add(new Payloads.PilgrimContractData(
                    view.contractId(),
                    view.title(),
                    view.status(),
                    view.descriptionLines(),
                    view.objectiveLines(),
                    view.rewardLines(),
                    view.actionLabel(),
                    view.actionEnabled(),
                    view.previewStack()
            ));
        }
        return payloads;
    }

    private static boolean isNaturalSpawnCooldownActive(ServerLevel world) {
        if (world == null || world.getServer() == null) {
            return false;
        }
        return world.getGameTime() < QuestState.get(world.getServer()).getPilgrimNaturalSpawnCooldownUntil();
    }

    private static BlockPos findSpawnPos(ServerLevel world, ServerPlayer anchor) {
        BlockPos origin = anchor.blockPosition();
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(world.getRandom(), MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feetPos = new BlockPos(x, y, z);
            BlockPos groundPos = feetPos.below();

            if (!world.getWorldBorder().isWithinBounds(feetPos)) {
                continue;
            }
            if (y <= world.getMinY()) {
                continue;
            }
            if (!world.getBlockState(groundPos).isFaceSturdy(world, groundPos, Direction.UP)) {
                continue;
            }
            if (!world.getBlockState(feetPos).isAir() || !world.getBlockState(feetPos.above()).isAir()) {
                continue;
            }
            return feetPos;
        }
        return null;
    }
}
