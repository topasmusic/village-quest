package de.quest.quest;

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
import de.quest.reputation.ReputationService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
        boolean hasVillageLedgerProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.VILLAGE_LEDGER);
        boolean hasApiaryCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.APIARY_CHARTER);
        boolean hasForgeCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.FORGE_CHARTER);
        boolean hasMarketCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.MARKET_CHARTER);
        boolean hasPastureCharterProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.PASTURE_CHARTER);
        boolean hasWatchBellProject = VillageProjectService.isUnlocked(world, pid, VillageProjectType.WATCH_BELL);

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
                hasWatchBellProject
        );
    }

    public static boolean toggleJournal(ServerLevel world, ServerPlayer player) {
        UUID pid = player.getUUID();
        if (JOURNAL_ENABLED.contains(pid)) {
            JOURNAL_ENABLED.remove(pid);
            sendPayload(player, buildPayload(world, player, JournalPayload.ACTION_CLOSE));
            LAST_JOURNAL_REFRESH.remove(pid);
            return false;
        }
        JOURNAL_ENABLED.add(pid);
        sendPayload(player, buildPayload(world, player, JournalPayload.ACTION_OPEN));
        return true;
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
