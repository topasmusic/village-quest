package de.quest.economy;

import de.quest.caravan.TradeGuildService;
import de.quest.caravan.TradeRouteService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.network.Payloads;
import de.quest.quest.story.VillageProjectService;
import de.quest.shop.ShopOffer;
import de.quest.shop.ShopService;
import de.quest.util.TimeUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Formatting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Server-owned economy expansion for 2.1.0. All values live in the existing
 * generic player state maps, so old saves can opt into the feature without a
 * one-shot migration or a new SavedData schema.
 */
public final class ProsperityService {
    private static final int MAX_RANK = 3;
    private static final long[] RANK_COSTS = {
            0L,
            CurrencyService.CROWN * 6L,
            CurrencyService.CROWN * 12L,
            CurrencyService.CROWN * 24L
    };
    private static final String RANK_PREFIX = "prosperity.rank.";
    private static final String COLLECTION_PREFIX = "prosperity.collection.";
    private static final String PENDING_COMMISSION = "prosperity.commission.offer";
    private static final String PENDING_COMMISSION_DAY = "prosperity.commission.day";
    private static final String FESTIVAL_CHARGES = "prosperity.festival_charges";
    private static final String CEREMONY_CHARGES = "prosperity.ceremony_charges";
    private static final String STAT_EARNED = "prosperity.stat.earned";
    private static final String STAT_SPENT = "prosperity.stat.spent";
    private static final String STAT_PURCHASES = "prosperity.stat.purchases";
    private static final String STAT_COMMISSIONS = "prosperity.stat.commissions";
    private static final String STAT_SERVICES = "prosperity.stat.services";
    private static final String STAT_INVESTMENTS = "prosperity.stat.investments";
    private static final String STAT_COLLECTIONS = "prosperity.stat.collections";

    private ProsperityService() {}

    public enum VillageService {
        ROAD_PATROL("road_patrol", "service_road_patrol", 8L, true),
        SURVEY_REPORT("survey_report", "service_survey_report", 5L, true),
        EMERGENCY_RECALL("emergency_recall", "service_emergency_recall", 10L, true),
        VILLAGE_FESTIVAL("village_festival", "service_village_festival", 15L, false),
        GUILD_CEREMONY("guild_ceremony", "service_guild_ceremony", 25L, false);

        private final String id;
        private final String icon;
        private final long crowns;
        private final boolean routeTargeted;

        VillageService(String id, String icon, long crowns, boolean routeTargeted) {
            this.id = id;
            this.icon = icon;
            this.crowns = crowns;
            this.routeTargeted = routeTargeted;
        }

        public String id() { return id; }
        public String icon() { return icon; }
        public long cost() { return crowns * CurrencyService.CROWN; }
        public boolean routeTargeted() { return routeTargeted; }
        public Text title() { return Text.translatable("screen.village-quest.prosperity.service." + id); }

        public static VillageService byId(String id) {
            if (id != null) {
                for (VillageService service : values()) {
                    if (service.id.equalsIgnoreCase(id)) return service;
                }
            }
            return null;
        }
    }

    private enum PrestigeReward {
        LIVERY_CRIMSON("livery_crimson", "prestige_caravan_livery_crimson", 8L, 0, Items.PAPER),
        LIVERY_FOREST("livery_forest", "prestige_caravan_livery_forest", 8L, 1, Items.PAPER),
        LIVERY_AZURE("livery_azure", "prestige_caravan_livery_azure", 8L, 2, Items.PAPER),
        LIVERY_OCHRE("livery_ochre", "prestige_caravan_livery_ochre", 8L, 3, Items.PAPER),
        LIVERY_VIOLET("livery_violet", "prestige_caravan_livery_violet", 8L, 4, Items.PAPER),
        GUILD_BANNER("guild_banner", "prestige_guild_banner", 12L, -1, Items.PAPER),
        MAPMAKER_CREST("mapmaker_crest", "prestige_mapmaker_crest", 14L, -1, Items.COMPASS),
        MARKET_PAVILION("market_pavilion", "prestige_market_pavilion", 18L, -1, Items.GOLD_INGOT),
        WATCHTOWER_PENNANT("watchtower_pennant", "prestige_watchtower_pennant", 18L, -1, Items.BELL),
        GUILD_HALL_TROPHY("guild_hall_trophy", "prestige_guild_hall_trophy", 30L, -1, Items.LODESTONE);

        private final String id;
        private final String icon;
        private final long crowns;
        private final int liveryIndex;
        private final Item rewardItem;

        PrestigeReward(String id, String icon, long crowns, int liveryIndex, Item rewardItem) {
            this.id = id;
            this.icon = icon;
            this.crowns = crowns;
            this.liveryIndex = liveryIndex;
            this.rewardItem = rewardItem;
        }

        long cost() { return crowns * CurrencyService.CROWN; }
        boolean isLivery() { return liveryIndex >= 0; }
        Text title() { return Text.translatable("screen.village-quest.prosperity.collection." + id); }

        static PrestigeReward byId(String id) {
            if (id != null) {
                for (PrestigeReward reward : values()) {
                    if (reward.id.equalsIgnoreCase(id)) return reward;
                }
            }
            return null;
        }
    }

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void dirty(ServerWorld world) {
        QuestState.get(world.getServer()).markDirty();
    }

    public static boolean hasAccess(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) return false;
        for (ProsperityBranch branch : ProsperityBranch.values()) {
            if (VillageProjectService.isUnlocked(world, playerId, branch.requiredProject())) return true;
        }
        return TradeRouteService.hasRouteAccess(world, playerId);
    }

    public static int rank(ServerWorld world, UUID playerId, ProsperityBranch branch) {
        if (world == null || playerId == null || branch == null) return 0;
        return Math.max(0, Math.min(MAX_RANK, data(world, playerId).getPilgrimInt(RANK_PREFIX + branch.id())));
    }

    public static int totalRanks(ServerWorld world, UUID playerId) {
        int total = 0;
        for (ProsperityBranch branch : ProsperityBranch.values()) total += rank(world, playerId, branch);
        return total;
    }

    public static long nextRankCost(ServerWorld world, UUID playerId, ProsperityBranch branch) {
        int next = rank(world, playerId, branch) + 1;
        return next > MAX_RANK ? 0L : RANK_COSTS[next];
    }

    public static boolean invest(ServerWorld world, ServerPlayerEntity player, ProsperityBranch branch) {
        if (world == null || player == null || branch == null) return false;
        if (!VillageProjectService.isUnlocked(world, player.getUuid(), branch.requiredProject())) {
            player.sendMessage(Text.translatable("message.village-quest.prosperity.project_locked",
                    Text.translatable("quest.village-quest.project." + branch.requiredProject().id() + ".title"))
                    .formatted(Formatting.RED), false);
            return false;
        }
        int current = rank(world, player.getUuid(), branch);
        if (current >= MAX_RANK) return false;
        long cost = RANK_COSTS[current + 1];
        if (!CurrencyService.removeBalance(world, player.getUuid(), cost)) {
            notEnough(player, world, cost);
            return false;
        }
        data(world, player.getUuid()).setPilgrimInt(RANK_PREFIX + branch.id(), current + 1);
        increment(world, player.getUuid(), STAT_INVESTMENTS, 1);
        dirty(world);
        player.sendMessage(Text.translatable("message.village-quest.prosperity.invested",
                branch.title(), rankLabel(current + 1), CurrencyService.formatBalance(cost))
                .formatted(Formatting.GREEN), false);
        return true;
    }

    public static Text rankLabel(int rank) {
        return Text.translatable("screen.village-quest.prosperity.rank." + Math.max(0, Math.min(MAX_RANK, rank)));
    }

    /** Effective Pilgrim price after the relevant permanent branch discount. */
    public static long shopPrice(ServerWorld world, UUID playerId, ShopOffer offer) {
        if (offer == null) return 0L;
        ProsperityBranch branch = branchForOffer(offer);
        int discountPercent = rank(world, playerId, branch) * 5;
        if (discountPercent <= 0) return offer.price();
        return Math.max(1L, (offer.price() * (100L - discountPercent) + 99L) / 100L);
    }

    private static ProsperityBranch branchForOffer(ShopOffer offer) {
        String id = offer.id().toLowerCase(Locale.ROOT);
        if (id.contains("bee") || id.contains("honey") || id.contains("bienen") || id.contains("apiary")) {
            return ProsperityBranch.APIARY;
        }
        if (id.contains("smith") || id.contains("forge") || id.contains("furnace") || id.contains("lava")) {
            return ProsperityBranch.FORGE;
        }
        if (id.contains("pasture") || id.contains("hay") || id.contains("tack")) {
            return ProsperityBranch.PASTURE;
        }
        if (id.contains("road") || id.contains("watch") || id.contains("hunter")) {
            return ProsperityBranch.ROAD_WATCH;
        }
        return ProsperityBranch.MARKET;
    }

    public static long routeUpgradePrice(ServerWorld world, UUID playerId, long basePrice) {
        int discountPercent = rank(world, playerId, ProsperityBranch.FORGE) * 5;
        return Math.max(0L, (basePrice * (100L - discountPercent) + 99L) / 100L);
    }

    public static int roadWatchEventReduction(ServerWorld world, UUID playerId) {
        return rank(world, playerId, ProsperityBranch.ROAD_WATCH) * 3;
    }

    public static boolean canParticipateInMarketWeek(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) return false;
        List<Long> prices = ShopService.availableOfferIds(world, playerId).stream()
                .map(ShopService::offer)
                .filter(offer -> offer != null)
                .map(offer -> shopPrice(world, playerId, offer))
                .sorted()
                .toList();
        if (prices.size() < 3) return false;
        long required = safeAdd(prices.get(0), safeAdd(prices.get(1), prices.get(2)));
        return CurrencyService.getBalance(world, playerId) >= required;
    }

    public static long commissionFee(ServerWorld world, UUID playerId) {
        return Math.max(CurrencyService.CROWN,
                CurrencyService.CROWN * (4L - rank(world, playerId, ProsperityBranch.MARKET)));
    }

    public static boolean placeCommission(ServerWorld world, ServerPlayerEntity player, String offerId) {
        if (world == null || player == null || offerId == null) return false;
        PlayerQuestData playerData = data(world, player.getUuid());
        if (rank(world, player.getUuid(), ProsperityBranch.MARKET) < 1) {
            player.sendMessage(Text.translatable("message.village-quest.prosperity.commission_locked")
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (!playerData.getTradeRouteString(PENDING_COMMISSION).isBlank()) {
            player.sendMessage(Text.translatable("message.village-quest.prosperity.commission_pending")
                    .formatted(Formatting.RED), false);
            return false;
        }
        ShopOffer offer = ShopService.offer(offerId);
        if (offer == null || !ShopService.isOfferUnlocked(world, player.getUuid(), offerId)) return false;
        long price = safeAdd(shopPrice(world, player.getUuid(), offer), commissionFee(world, player.getUuid()));
        if (!CurrencyService.removeBalance(world, player.getUuid(), price)) {
            notEnough(player, world, price);
            return false;
        }
        playerData.setTradeRouteString(PENDING_COMMISSION, offerId);
        playerData.setPilgrimInt(PENDING_COMMISSION_DAY, (int) TimeUtil.currentDay());
        increment(world, player.getUuid(), STAT_COMMISSIONS, 1);
        dirty(world);
        player.sendMessage(Text.translatable("message.village-quest.prosperity.commission_placed",
                offer.title(), CurrencyService.formatBalance(price)).formatted(Formatting.GREEN), false);
        return true;
    }

    /** Called when the next day's Pilgrim stock is actually visited. */
    public static boolean deliverPendingCommission(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) return false;
        PlayerQuestData playerData = data(world, player.getUuid());
        String offerId = playerData.getTradeRouteString(PENDING_COMMISSION);
        if (offerId.isBlank() || TimeUtil.currentDay() <= playerData.getPilgrimInt(PENDING_COMMISSION_DAY)) return false;
        if (!ShopService.fulfillCommission(world, player, offerId)) return false;
        playerData.setTradeRouteString(PENDING_COMMISSION, "");
        playerData.setPilgrimInt(PENDING_COMMISSION_DAY, 0);
        dirty(world);
        ShopOffer offer = ShopService.offer(offerId);
        player.sendMessage(Text.translatable("message.village-quest.prosperity.commission_delivered",
                offer == null ? Text.literal(offerId) : offer.title()).formatted(Formatting.GOLD), false);
        return true;
    }

    public static boolean buyService(ServerWorld world, ServerPlayerEntity player, VillageService service, int routeIndex) {
        if (world == null || player == null || service == null) return false;
        if (service.routeTargeted() && (routeIndex < 0 || routeIndex >= TradeRouteService.routeCount(world, player.getUuid()))) {
            player.sendMessage(Text.translatable("message.village-quest.prosperity.route_required")
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (service == VillageService.GUILD_CEREMONY && TradeGuildService.guildRank(world, player.getUuid()) < 3) {
            player.sendMessage(Text.translatable("message.village-quest.prosperity.ceremony_locked")
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (!CurrencyService.removeBalance(world, player.getUuid(), service.cost())) {
            notEnough(player, world, service.cost());
            return false;
        }
        boolean applied = switch (service) {
            case ROAD_PATROL -> TradeRouteService.hireRoadPatrol(world, player, routeIndex, 3);
            case SURVEY_REPORT -> TradeRouteService.buySurveyReport(world, player, routeIndex, 15);
            case EMERGENCY_RECALL -> TradeRouteService.emergencyRecall(world, player, routeIndex);
            case VILLAGE_FESTIVAL -> {
                data(world, player.getUuid()).setPilgrimInt(FESTIVAL_CHARGES, 3);
                yield true;
            }
            case GUILD_CEREMONY -> {
                data(world, player.getUuid()).setPilgrimInt(CEREMONY_CHARGES, 3);
                yield true;
            }
        };
        if (!applied) {
            CurrencyService.addBalance(world, player.getUuid(), service.cost());
            return false;
        }
        increment(world, player.getUuid(), STAT_SERVICES, 1);
        dirty(world);
        player.sendMessage(Text.translatable("message.village-quest.prosperity.service_bought",
                service.title(), CurrencyService.formatBalance(service.cost())).formatted(Formatting.GREEN), false);
        return true;
    }

    public static long applyFestivalBonus(ServerWorld world, UUID playerId, long reward) {
        if (world == null || playerId == null || reward <= 0L) return reward;
        PlayerQuestData playerData = data(world, playerId);
        int charges = playerData.getPilgrimInt(FESTIVAL_CHARGES);
        if (charges <= 0) return reward;
        playerData.setPilgrimInt(FESTIVAL_CHARGES, charges - 1);
        dirty(world);
        return safeAdd(reward, Math.max(1L, reward / 4L));
    }

    public static long applyCeremonyBonus(ServerWorld world, UUID playerId, long reward) {
        if (world == null || playerId == null || reward <= 0L) return reward;
        PlayerQuestData playerData = data(world, playerId);
        int charges = playerData.getPilgrimInt(CEREMONY_CHARGES);
        if (charges <= 0) return reward;
        playerData.setPilgrimInt(CEREMONY_CHARGES, charges - 1);
        dirty(world);
        return safeAdd(reward, Math.max(1L, reward / 4L));
    }

    public static boolean buyOrApplyCollection(ServerWorld world, ServerPlayerEntity player, String rewardId, int routeIndex) {
        if (world == null || player == null) return false;
        PrestigeReward reward = PrestigeReward.byId(rewardId);
        if (reward == null) return false;
        PlayerQuestData playerData = data(world, player.getUuid());
        String flag = COLLECTION_PREFIX + reward.id;
        boolean owned = playerData.hasPilgrimFlag(flag);
        boolean purchasedNow = false;
        if (!owned) {
            if (!collectionUnlocked(world, player.getUuid(), reward)) {
                player.sendMessage(Text.translatable("message.village-quest.prosperity.collection_locked")
                        .formatted(Formatting.RED), false);
                return false;
            }
            if (!CurrencyService.removeBalance(world, player.getUuid(), reward.cost())) {
                notEnough(player, world, reward.cost());
                return false;
            }
            playerData.setPilgrimFlag(flag, true);
            purchasedNow = true;
            increment(world, player.getUuid(), STAT_COLLECTIONS, 1);
            if (!reward.isLivery()) givePrestigeItem(player, reward);
            dirty(world);
            player.sendMessage(Text.translatable("message.village-quest.prosperity.collection_bought",
                    reward.title()).formatted(Formatting.GOLD), false);
        }
        if (reward.isLivery()) {
            boolean applied = TradeRouteService.setRouteLivery(world, player, routeIndex, reward.liveryIndex);
            return applied || purchasedNow;
        }
        return true;
    }

    private static boolean collectionUnlocked(ServerWorld world, UUID playerId, PrestigeReward reward) {
        if (reward.isLivery()) return rank(world, playerId, ProsperityBranch.MARKET) >= 1;
        return switch (reward) {
            case GUILD_BANNER -> totalRanks(world, playerId) >= 4;
            case MAPMAKER_CREST -> totalRanks(world, playerId) >= 7;
            case MARKET_PAVILION -> rank(world, playerId, ProsperityBranch.MARKET) >= 2;
            case WATCHTOWER_PENNANT -> rank(world, playerId, ProsperityBranch.ROAD_WATCH) >= 2;
            case GUILD_HALL_TROPHY -> totalRanks(world, playerId) >= 15;
            default -> false;
        };
    }

    private static void givePrestigeItem(ServerPlayerEntity player, PrestigeReward reward) {
        ItemStack stack = new ItemStack(reward.rewardItem);
        stack.set(DataComponentTypes.CUSTOM_NAME, reward.title().copy().formatted(Formatting.GOLD));
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void recordCurrencyDelta(ServerWorld world, UUID playerId, long delta) {
        if (world == null || playerId == null || delta == 0L) return;
        increment(world, playerId, delta > 0 ? STAT_EARNED : STAT_SPENT, Math.abs(delta));
    }

    public static void recordShopPurchase(ServerWorld world, UUID playerId) {
        increment(world, playerId, STAT_PURCHASES, 1);
    }

    /** Resets only the 2.1.0 economy layer for a deterministic admin QA profile. */
    public static void resetForTesting(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) return;
        PlayerQuestData playerData = data(world, playerId);
        for (ProsperityBranch branch : ProsperityBranch.values()) {
            playerData.setPilgrimInt(RANK_PREFIX + branch.id(), 0);
        }
        for (PrestigeReward reward : PrestigeReward.values()) {
            playerData.setPilgrimFlag(COLLECTION_PREFIX + reward.id, false);
        }
        playerData.setTradeRouteString(PENDING_COMMISSION, "");
        playerData.setPilgrimInt(PENDING_COMMISSION_DAY, 0);
        playerData.setPilgrimInt(FESTIVAL_CHARGES, 0);
        playerData.setPilgrimInt(CEREMONY_CHARGES, 0);
        for (String statistic : List.of(STAT_EARNED, STAT_SPENT, STAT_PURCHASES, STAT_COMMISSIONS,
                STAT_SERVICES, STAT_INVESTMENTS, STAT_COLLECTIONS)) {
            playerData.setPilgrimInt(statistic, 0);
        }
        dirty(world);
    }

    private static void increment(ServerWorld world, UUID playerId, String key, long amount) {
        if (amount <= 0L) return;
        PlayerQuestData playerData = data(world, playerId);
        long next = Math.min(Integer.MAX_VALUE, (long) playerData.getPilgrimInt(key) + amount);
        playerData.setPilgrimInt(key, (int) next);
        dirty(world);
    }

    public static void open(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) return;
        if (!hasAccess(world, player.getUuid())) {
            player.sendMessage(Text.translatable("message.village-quest.prosperity.locked")
                    .formatted(Formatting.GRAY), false);
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(world, player, Payloads.EconomyPayload.ACTION_OPEN));
    }

    public static void refresh(ServerWorld world, ServerPlayerEntity player) {
        if (world != null && player != null) {
            ServerPlayNetworking.send(player, buildPayload(world, player, Payloads.EconomyPayload.ACTION_UPDATE));
        }
    }

    public static void handleAction(ServerPlayerEntity player, Payloads.EconomyActionPayload payload) {
        if (player == null || payload == null || payload.actionId() == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        String[] parts = payload.actionId().split(":", 4);
        boolean changed = false;
        if (parts.length >= 2 && parts[0].equals("prosperity")) {
            changed = invest(world, player, ProsperityBranch.byId(parts[1]));
        } else if (parts.length >= 2 && parts[0].equals("commission")) {
            changed = placeCommission(world, player, parts[1]);
        } else if (parts.length >= 2 && parts[0].equals("service")) {
            int route = parts.length >= 3 ? parseRoute(parts[2]) : -1;
            changed = buyService(world, player, VillageService.byId(parts[1]), route);
        } else if (parts.length >= 2 && parts[0].equals("collection")) {
            int route = parts.length >= 3 ? parseRoute(parts[2]) : -1;
            changed = buyOrApplyCollection(world, player, parts[1], route);
        }
        if (changed) refresh(world, player);
    }

    private static int parseRoute(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static Payloads.EconomyPayload buildPayload(ServerWorld world, ServerPlayerEntity player, int action) {
        List<Text> routeNames = new ArrayList<>();
        for (int i = 0; i < TradeRouteService.routeCount(world, player.getUuid()); i++) {
            routeNames.add(TradeRouteService.routeDisplayName(world, player.getUuid(), i));
        }
        return new Payloads.EconomyPayload(
                action,
                CurrencyService.getBalance(world, player.getUuid()),
                List.copyOf(routeNames),
                List.of(
                        new Payloads.EconomySectionData("prosperity",
                                Text.translatable("screen.village-quest.prosperity.section.prosperity"),
                                "prosperity_overview", buildProsperityEntries(world, player)),
                        new Payloads.EconomySectionData("commission",
                                Text.translatable("screen.village-quest.prosperity.section.commission"),
                                "commission", buildCommissionEntries(world, player)),
                        new Payloads.EconomySectionData("services",
                                Text.translatable("screen.village-quest.prosperity.section.services"),
                                "service_village_festival", buildServiceEntries(world, player)),
                        new Payloads.EconomySectionData("collection",
                                Text.translatable("screen.village-quest.prosperity.section.collection"),
                                "collection", buildCollectionEntries(world, player)),
                        new Payloads.EconomySectionData("statistics",
                                Text.translatable("screen.village-quest.prosperity.section.statistics"),
                                "economy_statistics", buildStatisticsEntries(world, player))
                )
        );
    }

    private static List<Payloads.EconomyEntryData> buildProsperityEntries(ServerWorld world, ServerPlayerEntity player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        long balance = CurrencyService.getBalance(world, player.getUuid());
        for (ProsperityBranch branch : ProsperityBranch.values()) {
            int current = rank(world, player.getUuid(), branch);
            long cost = nextRankCost(world, player.getUuid(), branch);
            boolean project = VillageProjectService.isUnlocked(world, player.getUuid(), branch.requiredProject());
            boolean maxed = current >= MAX_RANK;
            List<Text> details = new ArrayList<>();
            details.add(branch.benefit(current));
            if (!maxed) details.add(Text.translatable("screen.village-quest.prosperity.next_benefit", branch.benefit(current + 1)));
            if (!project) details.add(Text.translatable("screen.village-quest.prosperity.requires_project",
                    Text.translatable("quest.village-quest.project." + branch.requiredProject().id() + ".title")));
            entries.add(new Payloads.EconomyEntryData(
                    "prosperity:" + branch.id(), branch.icon(), branch.title(), rankLabel(current),
                    List.copyOf(details), cost,
                    maxed ? Text.translatable("screen.village-quest.prosperity.completed")
                            : Text.translatable("screen.village-quest.prosperity.invest"),
                    project && !maxed && balance >= cost, maxed
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildCommissionEntries(ServerWorld world, ServerPlayerEntity player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        PlayerQuestData playerData = data(world, player.getUuid());
        String pendingId = playerData.getTradeRouteString(PENDING_COMMISSION);
        boolean unlocked = rank(world, player.getUuid(), ProsperityBranch.MARKET) >= 1;
        long fee = commissionFee(world, player.getUuid());
        if (!pendingId.isBlank()) {
            ShopOffer pending = ShopService.offer(pendingId);
            entries.add(new Payloads.EconomyEntryData(
                    "pending", "commission",
                    Text.translatable("screen.village-quest.prosperity.commission.pending"),
                    pending == null ? Text.literal(pendingId) : pending.title(),
                    List.of(Text.translatable("screen.village-quest.prosperity.commission.delivery")),
                    0L, Text.translatable("screen.village-quest.prosperity.awaiting"), false, true
            ));
        }
        List<ShopOffer> offers = ShopService.availableOfferIds(world, player.getUuid()).stream()
                .map(ShopService::offer).filter(offer -> offer != null)
                .sorted(Comparator.comparing(offer -> offer.title().getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        long balance = CurrencyService.getBalance(world, player.getUuid());
        for (ShopOffer offer : offers) {
            long itemPrice = shopPrice(world, player.getUuid(), offer);
            long total = safeAdd(itemPrice, fee);
            entries.add(new Payloads.EconomyEntryData(
                    "commission:" + offer.id(), "commission", offer.title(),
                    Text.translatable("screen.village-quest.prosperity.commission.price",
                            CurrencyService.formatBalance(itemPrice), CurrencyService.formatBalance(fee)),
                    List.of(offer.description(), Text.translatable("screen.village-quest.prosperity.commission.delivery")),
                    total, Text.translatable("screen.village-quest.prosperity.commission.place"),
                    unlocked && pendingId.isBlank() && balance >= total, false
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildServiceEntries(ServerWorld world, ServerPlayerEntity player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        long balance = CurrencyService.getBalance(world, player.getUuid());
        int routeCount = TradeRouteService.routeCount(world, player.getUuid());
        for (VillageService service : VillageService.values()) {
            boolean requirements = !service.routeTargeted() || routeCount > 0;
            if (service == VillageService.GUILD_CEREMONY) requirements &= TradeGuildService.guildRank(world, player.getUuid()) >= 3;
            entries.add(new Payloads.EconomyEntryData(
                    "service:" + service.id(), service.icon(), service.title(),
                    Text.translatable("screen.village-quest.prosperity.service.cost", CurrencyService.formatBalance(service.cost())),
                    List.of(Text.translatable("screen.village-quest.prosperity.service." + service.id() + ".description")),
                    service.cost(), Text.translatable("screen.village-quest.prosperity.service.buy"),
                    requirements && balance >= service.cost(), false
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildCollectionEntries(ServerWorld world, ServerPlayerEntity player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        long balance = CurrencyService.getBalance(world, player.getUuid());
        PlayerQuestData playerData = data(world, player.getUuid());
        for (PrestigeReward reward : PrestigeReward.values()) {
            boolean owned = playerData.hasPilgrimFlag(COLLECTION_PREFIX + reward.id);
            boolean unlocked = collectionUnlocked(world, player.getUuid(), reward);
            Text action = owned
                    ? (reward.isLivery() ? Text.translatable("screen.village-quest.prosperity.collection.apply")
                            : Text.translatable("screen.village-quest.prosperity.owned"))
                    : Text.translatable("screen.village-quest.prosperity.collection.buy");
            entries.add(new Payloads.EconomyEntryData(
                    "collection:" + reward.id, reward.icon, reward.title(),
                    owned ? Text.translatable("screen.village-quest.prosperity.owned")
                            : (unlocked ? CurrencyService.formatBalance(reward.cost())
                            : Text.translatable("screen.village-quest.prosperity.locked")),
                    List.of(Text.translatable("screen.village-quest.prosperity.collection." + reward.id + ".description")),
                    owned ? 0L : reward.cost(), action,
                    owned ? reward.isLivery() && TradeRouteService.routeCount(world, player.getUuid()) > 0
                            : unlocked && balance >= reward.cost(), owned
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildStatisticsEntries(ServerWorld world, ServerPlayerEntity player) {
        PlayerQuestData playerData = data(world, player.getUuid());
        List<Text> details = List.of(
                Text.translatable("screen.village-quest.prosperity.statistics.earned",
                        CurrencyService.formatBalance(playerData.getPilgrimInt(STAT_EARNED))),
                Text.translatable("screen.village-quest.prosperity.statistics.spent",
                        CurrencyService.formatBalance(playerData.getPilgrimInt(STAT_SPENT))),
                Text.translatable("screen.village-quest.prosperity.statistics.purchases", playerData.getPilgrimInt(STAT_PURCHASES)),
                Text.translatable("screen.village-quest.prosperity.statistics.commissions", playerData.getPilgrimInt(STAT_COMMISSIONS)),
                Text.translatable("screen.village-quest.prosperity.statistics.services", playerData.getPilgrimInt(STAT_SERVICES)),
                Text.translatable("screen.village-quest.prosperity.statistics.investments", playerData.getPilgrimInt(STAT_INVESTMENTS)),
                Text.translatable("screen.village-quest.prosperity.statistics.collection", playerData.getPilgrimInt(STAT_COLLECTIONS)),
                Text.translatable("screen.village-quest.prosperity.statistics.ranks", totalRanks(world, player.getUuid()), 15),
                Text.translatable("screen.village-quest.prosperity.statistics.festival", playerData.getPilgrimInt(FESTIVAL_CHARGES)),
                Text.translatable("screen.village-quest.prosperity.statistics.ceremony", playerData.getPilgrimInt(CEREMONY_CHARGES))
        );
        return List.of(new Payloads.EconomyEntryData(
                "statistics", "economy_statistics",
                Text.translatable("screen.village-quest.prosperity.statistics.title"),
                Text.translatable("screen.village-quest.prosperity.statistics.subtitle"),
                details, 0L, Text.empty(), false, true
        ));
    }

    private static void notEnough(ServerPlayerEntity player, ServerWorld world, long price) {
        player.sendMessage(Text.translatable("command.village-quest.shop.not_enough",
                CurrencyService.formatBalance(price), CurrencyService.formatBalance(CurrencyService.getBalance(world, player.getUuid())))
                .formatted(Formatting.RED), false);
    }

    private static long safeAdd(long first, long second) {
        if (first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }
}
