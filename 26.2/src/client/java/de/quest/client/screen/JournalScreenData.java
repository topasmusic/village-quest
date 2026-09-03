package de.quest.client.screen;

import de.quest.network.Payloads;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Immutable client view-model received from the server-authoritative journal payload. */
public final class JournalScreenData {
    public final int total, discovered, completed, active;
    public final long currencyBalance;
    public final int farmingReputation, craftingReputation, animalReputation, tradeReputation, monsterReputation;
    public final boolean hasStarreachRing, hasMerchantSeal, hasShepherdFlute, hasApiaristSmoker;
    public final boolean hasSurveyorCompass, hasCaravanLedger;
    public final boolean dailyActive, weeklyActive, storyActive, pilgrimActive, specialActive;
    public final Component dailyTitle, dailyProgress, weeklyTitle, weeklyProgress;
    public final Component storyTitle, storyProgress, pilgrimTitle, pilgrimProgress, specialTitle, specialProgress;
    public final boolean hasVillageLedgerProject, hasApiaryCharterProject, hasForgeCharterProject;
    public final boolean hasMarketCharterProject, hasPastureCharterProject, hasWatchBellProject;
    public final boolean hasCaravanYardProject, hasWayshrineNetworkProject;
    public final List<Payloads.GuildPathNodeData> guildPathNodes;
    public final Component networkNextAction;
    public final Payloads.NetworkSummaryData networkSummary;
    public final List<Payloads.NetworkVillageData> networkVillages;
    public final List<Component> networkGuildLines;

    public JournalScreenData(
            int total, int discovered, int completed, int active, long currencyBalance,
            int farmingReputation, int craftingReputation, int animalReputation,
            int tradeReputation, int monsterReputation,
            boolean hasStarreachRing, boolean hasMerchantSeal, boolean hasShepherdFlute,
            boolean hasApiaristSmoker, boolean hasSurveyorCompass, boolean hasCaravanLedger,
            boolean dailyActive, Component dailyTitle, Component dailyProgress,
            boolean weeklyActive, Component weeklyTitle, Component weeklyProgress,
            boolean storyActive, Component storyTitle, Component storyProgress,
            boolean pilgrimActive, Component pilgrimTitle, Component pilgrimProgress,
            boolean specialActive, Component specialTitle, Component specialProgress,
            boolean hasVillageLedgerProject, boolean hasApiaryCharterProject,
            boolean hasForgeCharterProject, boolean hasMarketCharterProject,
            boolean hasPastureCharterProject, boolean hasWatchBellProject,
            boolean hasCaravanYardProject, boolean hasWayshrineNetworkProject,
            List<Payloads.GuildPathNodeData> guildPathNodes,
            Component networkNextAction, Payloads.NetworkSummaryData networkSummary,
            List<Payloads.NetworkVillageData> networkVillages,
            List<Component> networkGuildLines) {
        this.total = total;
        this.discovered = discovered;
        this.completed = completed;
        this.active = active;
        this.currencyBalance = currencyBalance;
        this.farmingReputation = farmingReputation;
        this.craftingReputation = craftingReputation;
        this.animalReputation = animalReputation;
        this.tradeReputation = tradeReputation;
        this.monsterReputation = monsterReputation;
        this.hasStarreachRing = hasStarreachRing;
        this.hasMerchantSeal = hasMerchantSeal;
        this.hasShepherdFlute = hasShepherdFlute;
        this.hasApiaristSmoker = hasApiaristSmoker;
        this.hasSurveyorCompass = hasSurveyorCompass;
        this.hasCaravanLedger = hasCaravanLedger;
        this.dailyActive = dailyActive;
        this.dailyTitle = safe(dailyTitle);
        this.dailyProgress = safe(dailyProgress);
        this.weeklyActive = weeklyActive;
        this.weeklyTitle = safe(weeklyTitle);
        this.weeklyProgress = safe(weeklyProgress);
        this.storyActive = storyActive;
        this.storyTitle = safe(storyTitle);
        this.storyProgress = safe(storyProgress);
        this.pilgrimActive = pilgrimActive;
        this.pilgrimTitle = safe(pilgrimTitle);
        this.pilgrimProgress = safe(pilgrimProgress);
        this.specialActive = specialActive;
        this.specialTitle = safe(specialTitle);
        this.specialProgress = safe(specialProgress);
        this.hasVillageLedgerProject = hasVillageLedgerProject;
        this.hasApiaryCharterProject = hasApiaryCharterProject;
        this.hasForgeCharterProject = hasForgeCharterProject;
        this.hasMarketCharterProject = hasMarketCharterProject;
        this.hasPastureCharterProject = hasPastureCharterProject;
        this.hasWatchBellProject = hasWatchBellProject;
        this.hasCaravanYardProject = hasCaravanYardProject;
        this.hasWayshrineNetworkProject = hasWayshrineNetworkProject;
        this.guildPathNodes = guildPathNodes == null ? List.of() : List.copyOf(guildPathNodes);
        this.networkNextAction = safe(networkNextAction);
        this.networkSummary = networkSummary == null
                ? new Payloads.NetworkSummaryData(0, 0, 0,
                Component.empty(), Component.empty(), Component.empty())
                : networkSummary;
        this.networkVillages = networkVillages == null ? List.of() : List.copyOf(networkVillages);
        this.networkGuildLines = networkGuildLines == null ? List.of() : List.copyOf(networkGuildLines);
    }

    public boolean hasAnySpecialItem() {
        return hasStarreachRing || hasMerchantSeal || hasShepherdFlute || hasApiaristSmoker
                || hasSurveyorCompass || hasCaravanLedger;
    }

    private static Component safe(Component value) {
        return value == null ? Component.empty() : value;
    }
}
