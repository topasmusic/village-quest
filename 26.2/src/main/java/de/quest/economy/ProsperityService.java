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
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    static final long FESTIVAL_MINIMUM_BONUS = CurrencyService.SILVERMARK * 4L;
    static final long CEREMONY_MINIMUM_BONUS = CurrencyService.SILVERMARK * 8L;

    private ProsperityService() {}

    public enum VillageService {
        ROAD_PATROL("road_patrol", "service_road_patrol", CurrencyService.CROWN * 8L, true),
        SURVEY_REPORT("survey_report", "service_survey_report", CurrencyService.CROWN * 5L, true),
        EMERGENCY_RECALL("emergency_recall", "service_emergency_recall", CurrencyService.CROWN * 10L, true),
        VILLAGE_FESTIVAL("village_festival", "service_village_festival", CurrencyService.CROWN, false),
        GUILD_CEREMONY("guild_ceremony", "service_guild_ceremony", CurrencyService.CROWN * 2L, false);

        private final String id;
        private final String icon;
        private final long cost;
        private final boolean routeTargeted;

        VillageService(String id, String icon, long cost, boolean routeTargeted) {
            this.id = id;
            this.icon = icon;
            this.cost = cost;
            this.routeTargeted = routeTargeted;
        }

        public String id() { return id; }
        public String icon() { return icon; }
        public long cost() { return cost; }
        public boolean routeTargeted() { return routeTargeted; }
        public Component title() { return Component.translatable("screen.village-quest.prosperity.service." + id); }

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
        Component title() { return Component.translatable("screen.village-quest.prosperity.collection." + id); }

        static PrestigeReward byId(String id) {
            if (id != null) {
                for (PrestigeReward reward : values()) {
                    if (reward.id.equalsIgnoreCase(id)) return reward;
                }
            }
            return null;
        }
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void dirty(ServerLevel world) {
        QuestState.get(world.getServer()).setDirty();
    }

    public static boolean hasAccess(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) return false;
        for (ProsperityBranch branch : ProsperityBranch.values()) {
            if (VillageProjectService.isUnlocked(world, playerId, branch.requiredProject())) return true;
        }
        return TradeRouteService.hasRouteAccess(world, playerId);
    }

    public static int rank(ServerLevel world, UUID playerId, ProsperityBranch branch) {
        if (world == null || playerId == null || branch == null) return 0;
        return Math.max(0, Math.min(MAX_RANK, data(world, playerId).getPilgrimInt(RANK_PREFIX + branch.id())));
    }

    public static int totalRanks(ServerLevel world, UUID playerId) {
        int total = 0;
        for (ProsperityBranch branch : ProsperityBranch.values()) total += rank(world, playerId, branch);
        return total;
    }

    public static long nextRankCost(ServerLevel world, UUID playerId, ProsperityBranch branch) {
        int next = rank(world, playerId, branch) + 1;
        return next > MAX_RANK ? 0L : RANK_COSTS[next];
    }

    public static boolean invest(ServerLevel world, ServerPlayer player, ProsperityBranch branch) {
        if (world == null || player == null || branch == null) return false;
        if (!VillageProjectService.isUnlocked(world, player.getUUID(), branch.requiredProject())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.project_locked",
                    Component.translatable("quest.village-quest.project." + branch.requiredProject().id() + ".title"))
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        int current = rank(world, player.getUUID(), branch);
        if (current >= MAX_RANK) return false;
        long cost = RANK_COSTS[current + 1];
        if (!CurrencyService.removeBalance(world, player.getUUID(), cost)) {
            notEnough(player, world, cost);
            return false;
        }
        data(world, player.getUUID()).setPilgrimInt(RANK_PREFIX + branch.id(), current + 1);
        increment(world, player.getUUID(), STAT_INVESTMENTS, 1);
        dirty(world);
        player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.invested",
                branch.title(), rankLabel(current + 1), CurrencyService.formatBalance(cost))
                .withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static Component rankLabel(int rank) {
        return Component.translatable("screen.village-quest.prosperity.rank." + Math.max(0, Math.min(MAX_RANK, rank)));
    }

    /** Effective Pilgrim price after the relevant permanent branch discount. */
    public static long shopPrice(ServerLevel world, UUID playerId, ShopOffer offer) {
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

    public static long routeUpgradePrice(ServerLevel world, UUID playerId, long basePrice) {
        int discountPercent = rank(world, playerId, ProsperityBranch.FORGE) * 5;
        return Math.max(0L, (basePrice * (100L - discountPercent) + 99L) / 100L);
    }

    public static int roadWatchEventReduction(ServerLevel world, UUID playerId) {
        return rank(world, playerId, ProsperityBranch.ROAD_WATCH) * 3;
    }

    public static boolean canParticipateInMarketWeek(ServerLevel world, UUID playerId) {
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

    public static long commissionFee(ServerLevel world, UUID playerId) {
        return Math.max(CurrencyService.CROWN,
                CurrencyService.CROWN * (4L - rank(world, playerId, ProsperityBranch.MARKET)));
    }

    public static boolean placeCommission(ServerLevel world, ServerPlayer player, String offerId) {
        if (world == null || player == null || offerId == null) return false;
        PlayerQuestData playerData = data(world, player.getUUID());
        if (rank(world, player.getUUID(), ProsperityBranch.MARKET) < 1) {
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.commission_locked")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!playerData.getTradeRouteString(PENDING_COMMISSION).isBlank()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.commission_pending")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ShopOffer offer = ShopService.offer(offerId);
        if (offer == null || !ShopService.isOfferUnlocked(world, player.getUUID(), offerId)) return false;
        long price = safeAdd(shopPrice(world, player.getUUID(), offer), commissionFee(world, player.getUUID()));
        if (!CurrencyService.removeBalance(world, player.getUUID(), price)) {
            notEnough(player, world, price);
            return false;
        }
        playerData.setTradeRouteString(PENDING_COMMISSION, offerId);
        playerData.setPilgrimInt(PENDING_COMMISSION_DAY, (int) TimeUtil.currentDay());
        increment(world, player.getUUID(), STAT_COMMISSIONS, 1);
        dirty(world);
        player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.commission_placed",
                offer.title(), CurrencyService.formatBalance(price)).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    /** Called when the next day's Pilgrim stock is actually visited. */
    public static boolean deliverPendingCommission(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return false;
        PlayerQuestData playerData = data(world, player.getUUID());
        String offerId = playerData.getTradeRouteString(PENDING_COMMISSION);
        if (offerId.isBlank() || TimeUtil.currentDay() <= playerData.getPilgrimInt(PENDING_COMMISSION_DAY)) return false;
        if (!ShopService.fulfillCommission(world, player, offerId)) return false;
        playerData.setTradeRouteString(PENDING_COMMISSION, "");
        playerData.setPilgrimInt(PENDING_COMMISSION_DAY, 0);
        dirty(world);
        ShopOffer offer = ShopService.offer(offerId);
        player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.commission_delivered",
                offer == null ? Component.literal(offerId) : offer.title()).withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    public static boolean buyService(ServerLevel world, ServerPlayer player, VillageService service, int routeIndex) {
        if (world == null || player == null || service == null) return false;
        if (service.routeTargeted() && (routeIndex < 0 || routeIndex >= TradeRouteService.routeCount(world, player.getUUID()))) {
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.route_required")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (service == VillageService.GUILD_CEREMONY && TradeGuildService.guildRank(world, player.getUUID()) < 3) {
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.ceremony_locked")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!CurrencyService.removeBalance(world, player.getUUID(), service.cost())) {
            notEnough(player, world, service.cost());
            return false;
        }
        boolean applied = switch (service) {
            case ROAD_PATROL -> TradeRouteService.hireRoadPatrol(world, player, routeIndex, 3);
            case SURVEY_REPORT -> TradeRouteService.buySurveyReport(world, player, routeIndex, 15);
            case EMERGENCY_RECALL -> TradeRouteService.emergencyRecall(world, player, routeIndex);
            case VILLAGE_FESTIVAL -> {
                data(world, player.getUUID()).setPilgrimInt(FESTIVAL_CHARGES, 3);
                yield true;
            }
            case GUILD_CEREMONY -> {
                data(world, player.getUUID()).setPilgrimInt(CEREMONY_CHARGES, 3);
                yield true;
            }
        };
        if (!applied) {
            CurrencyService.addBalance(world, player.getUUID(), service.cost());
            return false;
        }
        increment(world, player.getUUID(), STAT_SERVICES, 1);
        dirty(world);
        player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.service_bought",
                service.title(), CurrencyService.formatBalance(service.cost())).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static long applyFestivalBonus(ServerLevel world, UUID playerId, long reward) {
        if (world == null || playerId == null || reward <= 0L) return reward;
        PlayerQuestData playerData = data(world, playerId);
        int charges = playerData.getPilgrimInt(FESTIVAL_CHARGES);
        if (charges <= 0) return reward;
        playerData.setPilgrimInt(FESTIVAL_CHARGES, charges - 1);
        dirty(world);
        return safeAdd(reward, festivalBonusAmount(reward));
    }

    public static long applyCeremonyBonus(ServerLevel world, UUID playerId, long reward) {
        if (world == null || playerId == null || reward <= 0L) return reward;
        PlayerQuestData playerData = data(world, playerId);
        int charges = playerData.getPilgrimInt(CEREMONY_CHARGES);
        if (charges <= 0) return reward;
        playerData.setPilgrimInt(CEREMONY_CHARGES, charges - 1);
        dirty(world);
        return safeAdd(reward, ceremonyBonusAmount(reward));
    }

    static long festivalBonusAmount(long reward) {
        return reward <= 0L ? 0L : Math.max(FESTIVAL_MINIMUM_BONUS, reward / 4L);
    }

    static long ceremonyBonusAmount(long reward) {
        return reward <= 0L ? 0L : Math.max(CEREMONY_MINIMUM_BONUS, reward / 4L);
    }

    public static boolean buyOrApplyCollection(ServerLevel world, ServerPlayer player, String rewardId, int routeIndex) {
        if (world == null || player == null) return false;
        PrestigeReward reward = PrestigeReward.byId(rewardId);
        if (reward == null) return false;
        PlayerQuestData playerData = data(world, player.getUUID());
        String flag = COLLECTION_PREFIX + reward.id;
        boolean owned = playerData.hasPilgrimFlag(flag);
        boolean purchasedNow = false;
        if (!owned) {
            if (!collectionUnlocked(world, player.getUUID(), reward)) {
                player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.collection_locked")
                        .withStyle(ChatFormatting.RED), false);
                return false;
            }
            if (!CurrencyService.removeBalance(world, player.getUUID(), reward.cost())) {
                notEnough(player, world, reward.cost());
                return false;
            }
            playerData.setPilgrimFlag(flag, true);
            purchasedNow = true;
            increment(world, player.getUUID(), STAT_COLLECTIONS, 1);
            if (!reward.isLivery()) givePrestigeItem(player, reward);
            dirty(world);
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.collection_bought",
                    reward.title()).withStyle(ChatFormatting.GOLD), false);
        }
        if (reward.isLivery()) {
            boolean applied = TradeRouteService.setRouteLivery(world, player, routeIndex, reward.liveryIndex);
            return applied || purchasedNow;
        }
        return true;
    }

    private static boolean collectionUnlocked(ServerLevel world, UUID playerId, PrestigeReward reward) {
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

    private static void givePrestigeItem(ServerPlayer player, PrestigeReward reward) {
        ItemStack stack = new ItemStack(reward.rewardItem);
        stack.set(DataComponents.CUSTOM_NAME, reward.title().copy().withStyle(ChatFormatting.GOLD));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }

    public static void recordCurrencyDelta(ServerLevel world, UUID playerId, long delta) {
        if (world == null || playerId == null || delta == 0L) return;
        increment(world, playerId, delta > 0 ? STAT_EARNED : STAT_SPENT, Math.abs(delta));
    }

    /** Reverses an admin-fixture grant without recording the rollback as spending. */
    public static long adminReverseFixtureCurrency(ServerLevel world, UUID playerId, long amount) {
        if (world == null || playerId == null || amount <= 0L) return 0L;
        PlayerQuestData playerData = data(world, playerId);
        long removed = Math.min(amount, CurrencyService.getBalance(world, playerId));
        CurrencyService.setBalance(world, playerId, CurrencyService.getBalance(world, playerId) - removed);
        int earned = playerData.getPilgrimInt(STAT_EARNED);
        playerData.setPilgrimInt(STAT_EARNED, (int) Math.max(0L, (long) earned - amount));
        dirty(world);
        return removed;
    }

    public static void recordShopPurchase(ServerLevel world, UUID playerId) {
        increment(world, playerId, STAT_PURCHASES, 1);
    }

    /** Resets only the 2.1.0 economy layer for a deterministic admin QA profile. */
    public static void resetForTesting(ServerLevel world, UUID playerId) {
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

    private static void increment(ServerLevel world, UUID playerId, String key, long amount) {
        if (amount <= 0L) return;
        PlayerQuestData playerData = data(world, playerId);
        long next = Math.min(Integer.MAX_VALUE, (long) playerData.getPilgrimInt(key) + amount);
        playerData.setPilgrimInt(key, (int) next);
        dirty(world);
    }

    public static void open(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) return;
        if (!hasAccess(world, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.prosperity.locked")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(world, player, Payloads.EconomyPayload.ACTION_OPEN));
    }

    public static void refresh(ServerLevel world, ServerPlayer player) {
        if (world != null && player != null) {
            ServerPlayNetworking.send(player, buildPayload(world, player, Payloads.EconomyPayload.ACTION_UPDATE));
        }
    }

    public static void handleAction(ServerPlayer player, Payloads.EconomyActionPayload payload) {
        if (player == null || payload == null || payload.actionId() == null) return;
        ServerLevel world = (ServerLevel) player.level();
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

    private static Payloads.EconomyPayload buildPayload(ServerLevel world, ServerPlayer player, int action) {
        List<Component> routeNames = new ArrayList<>();
        for (int i = 0; i < TradeRouteService.routeCount(world, player.getUUID()); i++) {
            routeNames.add(TradeRouteService.routeDisplayName(world, player.getUUID(), i));
        }
        return new Payloads.EconomyPayload(
                action,
                CurrencyService.getBalance(world, player.getUUID()),
                List.copyOf(routeNames),
                List.of(
                        new Payloads.EconomySectionData("prosperity",
                                Component.translatable("screen.village-quest.prosperity.section.prosperity"),
                                "prosperity_overview", buildProsperityEntries(world, player)),
                        new Payloads.EconomySectionData("commission",
                                Component.translatable("screen.village-quest.prosperity.section.commission"),
                                "commission", buildCommissionEntries(world, player)),
                        new Payloads.EconomySectionData("services",
                                Component.translatable("screen.village-quest.prosperity.section.services"),
                                "service_village_festival", buildServiceEntries(world, player)),
                        new Payloads.EconomySectionData("collection",
                                Component.translatable("screen.village-quest.prosperity.section.collection"),
                                "collection", buildCollectionEntries(world, player)),
                        new Payloads.EconomySectionData("statistics",
                                Component.translatable("screen.village-quest.prosperity.section.statistics"),
                                "economy_statistics", buildStatisticsEntries(world, player))
                )
        );
    }

    private static List<Payloads.EconomyEntryData> buildProsperityEntries(ServerLevel world, ServerPlayer player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        long balance = CurrencyService.getBalance(world, player.getUUID());
        for (ProsperityBranch branch : ProsperityBranch.values()) {
            int current = rank(world, player.getUUID(), branch);
            long cost = nextRankCost(world, player.getUUID(), branch);
            boolean project = VillageProjectService.isUnlocked(world, player.getUUID(), branch.requiredProject());
            boolean maxed = current >= MAX_RANK;
            List<Component> details = new ArrayList<>();
            details.add(branch.benefit(current));
            if (!maxed) details.add(Component.translatable("screen.village-quest.prosperity.next_benefit", branch.benefit(current + 1)));
            if (!project) details.add(Component.translatable("screen.village-quest.prosperity.requires_project",
                    Component.translatable("quest.village-quest.project." + branch.requiredProject().id() + ".title")));
            entries.add(new Payloads.EconomyEntryData(
                    "prosperity:" + branch.id(), branch.icon(), branch.title(), rankLabel(current),
                    List.copyOf(details), cost,
                    maxed ? Component.translatable("screen.village-quest.prosperity.completed")
                            : Component.translatable("screen.village-quest.prosperity.invest"),
                    project && !maxed && balance >= cost, maxed
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildCommissionEntries(ServerLevel world, ServerPlayer player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        PlayerQuestData playerData = data(world, player.getUUID());
        String pendingId = playerData.getTradeRouteString(PENDING_COMMISSION);
        boolean unlocked = rank(world, player.getUUID(), ProsperityBranch.MARKET) >= 1;
        long fee = commissionFee(world, player.getUUID());
        if (!pendingId.isBlank()) {
            ShopOffer pending = ShopService.offer(pendingId);
            entries.add(new Payloads.EconomyEntryData(
                    "pending", "commission",
                    Component.translatable("screen.village-quest.prosperity.commission.pending"),
                    pending == null ? Component.literal(pendingId) : pending.title(),
                    List.of(Component.translatable("screen.village-quest.prosperity.commission.delivery")),
                    0L, Component.translatable("screen.village-quest.prosperity.awaiting"), false, true
            ));
        }
        List<ShopOffer> offers = ShopService.availableOfferIds(world, player.getUUID()).stream()
                .map(ShopService::offer).filter(offer -> offer != null)
                .sorted(Comparator.comparing(offer -> offer.title().getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        long balance = CurrencyService.getBalance(world, player.getUUID());
        for (ShopOffer offer : offers) {
            long itemPrice = shopPrice(world, player.getUUID(), offer);
            long total = safeAdd(itemPrice, fee);
            entries.add(new Payloads.EconomyEntryData(
                    "commission:" + offer.id(), "commission", offer.title(),
                    Component.translatable("screen.village-quest.prosperity.commission.price",
                            CurrencyService.formatBalance(itemPrice), CurrencyService.formatBalance(fee)),
                    List.of(offer.description(), Component.translatable("screen.village-quest.prosperity.commission.delivery")),
                    total, Component.translatable("screen.village-quest.prosperity.commission.place"),
                    unlocked && pendingId.isBlank() && balance >= total, false
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildServiceEntries(ServerLevel world, ServerPlayer player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        long balance = CurrencyService.getBalance(world, player.getUUID());
        int routeCount = TradeRouteService.routeCount(world, player.getUUID());
        for (VillageService service : VillageService.values()) {
            boolean requirements = !service.routeTargeted() || routeCount > 0;
            if (service == VillageService.GUILD_CEREMONY) requirements &= TradeGuildService.guildRank(world, player.getUUID()) >= 3;
            entries.add(new Payloads.EconomyEntryData(
                    "service:" + service.id(), service.icon(), service.title(),
                    Component.translatable("screen.village-quest.prosperity.service.cost", CurrencyService.formatBalance(service.cost())),
                    List.of(Component.translatable("screen.village-quest.prosperity.service." + service.id() + ".description")),
                    service.cost(), Component.translatable("screen.village-quest.prosperity.service.buy"),
                    requirements && balance >= service.cost(), false
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildCollectionEntries(ServerLevel world, ServerPlayer player) {
        List<Payloads.EconomyEntryData> entries = new ArrayList<>();
        long balance = CurrencyService.getBalance(world, player.getUUID());
        PlayerQuestData playerData = data(world, player.getUUID());
        for (PrestigeReward reward : PrestigeReward.values()) {
            boolean owned = playerData.hasPilgrimFlag(COLLECTION_PREFIX + reward.id);
            boolean unlocked = collectionUnlocked(world, player.getUUID(), reward);
            Component action = owned
                    ? (reward.isLivery() ? Component.translatable("screen.village-quest.prosperity.collection.apply")
                            : Component.translatable("screen.village-quest.prosperity.owned"))
                    : Component.translatable("screen.village-quest.prosperity.collection.buy");
            entries.add(new Payloads.EconomyEntryData(
                    "collection:" + reward.id, reward.icon, reward.title(),
                    owned ? Component.translatable("screen.village-quest.prosperity.owned")
                            : (unlocked ? CurrencyService.formatBalance(reward.cost())
                            : Component.translatable("screen.village-quest.prosperity.locked")),
                    List.of(Component.translatable("screen.village-quest.prosperity.collection." + reward.id + ".description")),
                    owned ? 0L : reward.cost(), action,
                    owned ? reward.isLivery() && TradeRouteService.routeCount(world, player.getUUID()) > 0
                            : unlocked && balance >= reward.cost(), owned
            ));
        }
        return List.copyOf(entries);
    }

    private static List<Payloads.EconomyEntryData> buildStatisticsEntries(ServerLevel world, ServerPlayer player) {
        PlayerQuestData playerData = data(world, player.getUUID());
        List<Component> details = List.of(
                Component.translatable("screen.village-quest.prosperity.statistics.earned",
                        CurrencyService.formatBalance(playerData.getPilgrimInt(STAT_EARNED))),
                Component.translatable("screen.village-quest.prosperity.statistics.spent",
                        CurrencyService.formatBalance(playerData.getPilgrimInt(STAT_SPENT))),
                Component.translatable("screen.village-quest.prosperity.statistics.purchases", playerData.getPilgrimInt(STAT_PURCHASES)),
                Component.translatable("screen.village-quest.prosperity.statistics.commissions", playerData.getPilgrimInt(STAT_COMMISSIONS)),
                Component.translatable("screen.village-quest.prosperity.statistics.services", playerData.getPilgrimInt(STAT_SERVICES)),
                Component.translatable("screen.village-quest.prosperity.statistics.investments", playerData.getPilgrimInt(STAT_INVESTMENTS)),
                Component.translatable("screen.village-quest.prosperity.statistics.collection", playerData.getPilgrimInt(STAT_COLLECTIONS)),
                Component.translatable("screen.village-quest.prosperity.statistics.ranks", totalRanks(world, player.getUUID()), 15),
                Component.translatable("screen.village-quest.prosperity.statistics.festival", playerData.getPilgrimInt(FESTIVAL_CHARGES)),
                Component.translatable("screen.village-quest.prosperity.statistics.ceremony", playerData.getPilgrimInt(CEREMONY_CHARGES))
        );
        return List.of(new Payloads.EconomyEntryData(
                "statistics", "economy_statistics",
                Component.translatable("screen.village-quest.prosperity.statistics.title"),
                Component.translatable("screen.village-quest.prosperity.statistics.subtitle"),
                details, 0L, Component.empty(), false, true
        ));
    }

    private static void notEnough(ServerPlayer player, ServerLevel world, long price) {
        player.sendSystemMessage(Component.translatable("command.village-quest.shop.not_enough",
                CurrencyService.formatBalance(price), CurrencyService.formatBalance(CurrencyService.getBalance(world, player.getUUID())))
                .withStyle(ChatFormatting.RED), false);
    }

    private static long safeAdd(long first, long second) {
        if (first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }
}
