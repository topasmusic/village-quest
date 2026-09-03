package de.quest.quest;

import de.quest.network.Payloads;
import de.quest.network.Payloads.JournalPayload;
import de.quest.economy.CurrencyService;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.registry.ModItems;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.SpecialQuestStatus;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.StoryQuestStatus;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.quest.weekly.WeeklyQuestStatus;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.reputation.ReputationService;
import de.quest.shrine.VillageBondService;
import de.quest.village.LivingVillageNetworkState;
import de.quest.village.LivingVillageNetworkService;
import de.quest.config.VillageQuestServerConfig;
import de.quest.guild.VillageGuildService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestBookHelper {
    private static final Set<UUID> JOURNAL_ENABLED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_JOURNAL_REFRESH = new ConcurrentHashMap<>();

    private QuestBookHelper() {}

    private static JournalPayload buildPayload(ServerLevel world, ServerPlayer player, int action) {
        UUID pid = player.getUUID();

        DailyQuestService.QuestStatus daily = DailyQuestService.openQuestStatus(world, pid);
        boolean dailyActive = daily != null;
        Component dailyTitle = dailyActive ? daily.title() : Component.empty();
        Component dailyProgress = dailyActive ? daily.progressLine() : Component.empty();
        WeeklyQuestStatus weekly = WeeklyQuestService.openStatus(world, pid);
        boolean weeklyActive = weekly != null;
        Component weeklyTitle = weeklyActive ? weekly.title() : Component.empty();
        Component weeklyProgress = weeklyActive
                ? Component.literal(String.join("\n", weekly.lines().stream().map(Component::getString).toList()))
                : Component.empty();
        StoryQuestStatus story = StoryQuestService.openStatus(world, pid);
        boolean storyActive = story != null;
        Component storyTitle = storyActive ? story.title() : Component.empty();
        Component storyProgress = storyActive
                ? Component.literal(String.join("\n", story.lines().stream().map(Component::getString).toList()))
                : Component.empty();
        PilgrimContractService.PilgrimContractStatus pilgrim = PilgrimContractService.openStatus(world, pid);
        boolean pilgrimActive = pilgrim != null;
        Component pilgrimTitle = pilgrimActive ? pilgrim.title() : Component.empty();
        Component pilgrimProgress = pilgrimActive
                ? Component.literal(String.join("\n", pilgrim.lines().stream().map(Component::getString).toList()))
                : Component.empty();
        SpecialQuestStatus special = SpecialQuestService.openStatus(world, pid);
        boolean specialActive = special != null;
        Component specialTitle = specialActive ? special.title() : Component.empty();
        Component specialProgress = specialActive
                ? Component.literal(String.join("\n", special.lines().stream().map(Component::getString).toList()))
                : Component.empty();

        int total = DailyQuestService.getDailyQuestCount()
                + WeeklyQuestService.getWeeklyQuestCount()
                + StoryQuestService.getStoryArcCount()
                + SpecialQuestService.TOTAL_SPECIAL_QUESTS;
        int discovered = (DailyQuestService.hasDiscoveredDaily(world, pid) ? 1 : 0)
                + WeeklyQuestService.discoveredCount(world, pid)
                + StoryQuestService.discoveredCount(world, pid)
                + SpecialQuestService.discoveredCount(world, pid);
        int completed = (DailyQuestService.isDailyCompleted(world, pid) ? 1 : 0)
                + WeeklyQuestService.completedCount(world, pid)
                + StoryQuestService.completedCount(world, pid)
                + SpecialQuestService.completedCount(world, pid);
        int active = (DailyQuestService.isDailyActive(world, pid) ? 1 : 0)
                + WeeklyQuestService.activeCount(world, pid)
                + StoryQuestService.activeCount(world, pid)
                + (pilgrimActive ? 1 : 0)
                + SpecialQuestService.activeCount(world, pid);
        long currencyBalance = CurrencyService.getBalance(world, pid);
        int farmingReputation = ReputationService.get(world, pid, ReputationService.ReputationTrack.FARMING);
        int craftingReputation = ReputationService.get(world, pid, ReputationService.ReputationTrack.CRAFTING);
        int animalReputation = ReputationService.get(world, pid, ReputationService.ReputationTrack.ANIMALS);
        int tradeReputation = ReputationService.get(world, pid, ReputationService.ReputationTrack.TRADE);
        int monsterReputation = ReputationService.get(world, pid, ReputationService.ReputationTrack.MONSTER_HUNTING);
        boolean hasStarreachRing = hasInventoryItem(player, ModItems.STARREACH_RING);
        boolean hasMerchantSeal = hasInventoryItem(player, ModItems.MERCHANT_SEAL);
        boolean hasShepherdFlute = hasInventoryItem(player, ModItems.SHEPHERD_FLUTE);
        boolean hasApiaristSmoker = hasInventoryItem(player, ModItems.APIARISTS_SMOKER);
        boolean hasSurveyorCompass = hasInventoryItem(player, ModItems.SURVEYORS_COMPASS);
        boolean hasCaravanLedger = hasInventoryItem(player, ModItems.CARAVAN_LEDGER);
        boolean hasVillageLedgerProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.VILLAGE_LEDGER);
        boolean hasApiaryCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.APIARY_CHARTER);
        boolean hasForgeCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.FORGE_CHARTER);
        boolean hasMarketCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.MARKET_CHARTER);
        boolean hasPastureCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.PASTURE_CHARTER);
        boolean hasWatchBellProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.WATCH_BELL);
        boolean hasCaravanYardProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.CARAVAN_YARD);
        boolean hasWayshrineNetworkProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.WAYSHRINE_NETWORK);
        LivingVillageNetworkState.NetworkSnapshot networkProgress =
                LivingVillageNetworkState.get(world.getServer()).network(pid);
        List<VillageBondService.VillageBondView> networkVillages = VillageBondService.villages(world, pid);
        Payloads.NetworkSummaryData networkSummary = new Payloads.NetworkSummaryData(
                networkProgress.rank(),
                networkProgress.renown(),
                networkProgress.nextRankThreshold(),
                LivingVillageNetworkService.honorLabel(networkProgress.rank()),
                networkProgress.specialization().label(),
                Component.translatable("text.village-quest.adventure_profile."
                        + VillageQuestServerConfig.get().adventureProfile().name().toLowerCase(Locale.ROOT)));
        List<Payloads.NetworkVillageData> networkVillageData = networkVillages.stream()
                .map(village -> new Payloads.NetworkVillageData(
                        village.index(),
                        village.type().label(),
                        village.level().label(),
                        village.network().condition().label(),
                        village.network().condition().key(),
                        village.network().need().label(),
                        village.network().support(),
                        village.network().energyProgress()))
                .toList();
        List<Component> networkGuildLines = VillageGuildService.statusLines(world, pid);
        VillageBondService.VillageBondView priorityVillage = networkVillages.stream()
                .min(Comparator.comparingInt(village -> village.network().support())).orElse(null);
        Component networkNextAction = priorityVillage == null
                ? Component.translatable("screen.village-quest.journal.network.next.discover")
                : Component.translatable("screen.village-quest.journal.network.next.supply",
                priorityVillage.type().label(), priorityVillage.network().need().label(),
                priorityVillage.network().support(), 100);

        return new JournalPayload(
                action,
                total,
                discovered,
                completed,
                active,
                currencyBalance,
                farmingReputation,
                craftingReputation,
                animalReputation,
                tradeReputation,
                monsterReputation,
                hasStarreachRing,
                hasMerchantSeal,
                hasShepherdFlute,
                hasApiaristSmoker,
                hasSurveyorCompass,
                hasCaravanLedger,
                dailyActive,
                dailyTitle,
                dailyProgress,
                weeklyActive,
                weeklyTitle,
                weeklyProgress,
                storyActive,
                storyTitle,
                storyProgress,
                pilgrimActive,
                pilgrimTitle,
                pilgrimProgress,
                specialActive,
                specialTitle,
                specialProgress,
                hasVillageLedgerProject,
                hasApiaryCharterProject,
                hasForgeCharterProject,
                hasMarketCharterProject,
                hasPastureCharterProject,
                hasWatchBellProject,
                hasCaravanYardProject,
                hasWayshrineNetworkProject,
                QuestMasterUiService.buildGuildPathNodes(world, player),
                networkNextAction,
                networkSummary,
                networkVillageData,
                networkGuildLines
        );
    }

    public static boolean openJournal(ServerLevel world, ServerPlayer player) {
        UUID pid = player.getUUID();
        JOURNAL_ENABLED.add(pid);
        sendPayload(player, buildPayload(world, player, JournalPayload.ACTION_OPEN));
        return true;
    }

    public static boolean toggleJournal(ServerLevel world, ServerPlayer player) {
        if (JOURNAL_ENABLED.contains(player.getUUID())) {
            closeJournal(player);
            return false;
        }
        return openJournal(world, player);
    }

    public static void closeJournal(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID pid = player.getUUID();
        if (!JOURNAL_ENABLED.remove(pid)) {
            return;
        }
        LAST_JOURNAL_REFRESH.remove(pid);
        sendPayload(player, new JournalPayload(
                JournalPayload.ACTION_CLOSE,
                0,
                0,
                0,
                0,
                0L,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Component.empty(),
                Component.empty(),
                false,
                Component.empty(),
                Component.empty(),
                false,
                Component.empty(),
                Component.empty(),
                false,
                Component.empty(),
                Component.empty(),
                false,
                Component.empty(),
                Component.empty(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                java.util.List.of(),
                Component.empty(),
                new Payloads.NetworkSummaryData(0, 0, 0,
                        Component.empty(), Component.empty(), Component.empty()),
                java.util.List.of(),
                java.util.List.of()
        ));
    }

    public static void resetAllSessions() {
        JOURNAL_ENABLED.clear();
        LAST_JOURNAL_REFRESH.clear();
    }

    public static void handleDisconnect(UUID playerId) {
        if (playerId == null) {
            return;
        }
        JOURNAL_ENABLED.remove(playerId);
        LAST_JOURNAL_REFRESH.remove(playerId);
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID pid = player.getUUID();
            if (!JOURNAL_ENABLED.contains(pid)) {
                continue;
            }
            ServerLevel world = (ServerLevel) player.level();
            long now = world.getGameTime();
            long last = LAST_JOURNAL_REFRESH.getOrDefault(pid, -200L);
            if ((now - last) >= 40L) {
                refreshQuestBook(world, player);
                LAST_JOURNAL_REFRESH.put(pid, now);
            }
        }
    }

    public static void refreshQuestBook(ServerLevel world, ServerPlayer player) {
        if (!JOURNAL_ENABLED.contains(player.getUUID())) {
            return;
        }
        sendPayload(player, buildPayload(world, player, JournalPayload.ACTION_UPDATE));
    }

    private static void sendPayload(ServerPlayer player, JournalPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    private static boolean hasInventoryItem(ServerPlayer player, Item item) {
        if (player == null || item == null) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return player.getOffhandItem().is(item);
    }
}
