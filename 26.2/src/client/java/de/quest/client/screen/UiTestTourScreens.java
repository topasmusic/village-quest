package de.quest.client.screen;

import de.quest.commands.UiTestFixtures;
import de.quest.commands.UiTestTourSteps.Step;
import de.quest.network.*;
import de.quest.reputation.ReputationViewService;
import de.quest.shrine.VillageBondLevel;
import de.quest.village.VillageLifeState.VillageKey;
import de.quest.economy.CurrencyService;
import de.quest.economy.DeliveryEconomy;
import de.quest.economy.ProsperityService;
import de.quest.economy.ProsperityBranch;
import de.quest.economy.QuestExperienceService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.shop.ShopService;
import de.quest.pilgrim.PilgrimContractService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Uses the actual production screens with isolated, deterministic sample models. */
final class UiTestTourScreens {
    private UiTestTourScreens() {}
    private static Component text(String key, Object... args) { return Component.translatable(key, args); }
    private static Component sample(String key) { return text("screen.village-quest.ui_tour.sample." + key); }
    static CompatScreen create(Step step) {
        String state = step.state();
        return switch (step.family()) {
            case "journal" -> {
                JournalScreen screen = new JournalScreen(journal());
                screen.selectTourSection(state); screen.updateReputation(standing(false)); yield screen;
            }
            case "questmaster" -> new QuestMasterScreen(quest(state));
            case "trust" -> {
                ReparationScreen screen = new ReparationScreen(new ReputationPayloads.InteractionPayload(
                        ReputationPayloads.NONE, true, true, standing(state.equals("amends")),
                        List.of(new ReputationPayloads.SupportView(ReputationPayloads.NONE,
                                new VillageKey("minecraft:overworld", 350, -200), false, false, false)), ""), null);
                screen.selectTourTab(state.equals("amends") ? 1 : state.equals("resettlement") ? 2 : 0); yield screen;
            }
            case "pilgrim" -> {
                var contract = PilgrimContractService.previewHedgerowContract();
                PilgrimTradeScreen screen = new PilgrimTradeScreen(new PilgrimTradeScreen.PilgrimTradeData(-1, sample("merchant"), 240, 1200,
                        List.of(new PilgrimTradeScreen.TradeView("shelf", text("text.village-quest.shop.offer.decor_chiseled_bookshelf.title"),
                                text("text.village-quest.shop.offer.decor_chiseled_bookshelf.description"), ShopService.offer("decor_chiseled_bookshelf").price(), new ItemStack(Items.CHISELED_BOOKSHELF)),
                                new PilgrimTradeScreen.TradeView("camp", text("text.village-quest.shop.offer.road_camp_kit.title"),
                                        text("text.village-quest.shop.offer.road_camp_kit.description"), ShopService.offer("road_camp_kit").price(), new ItemStack(Items.CAMPFIRE))),
                        List.of(new PilgrimTradeScreen.PilgrimContractView(contract.contractId(), contract.title(), contract.status(),
                                contract.descriptionLines(), contract.objectiveLines(), contract.rewardLines(), contract.actionLabel(), true, contract.previewStack()))));
                screen.selectTourContract(state.equals("contract")); yield screen;
            }
            case "prosperity" -> {
                ProsperityScreen screen = new ProsperityScreen(new Payloads.EconomyPayload(0, 240, List.of(sample("village")),
                        List.of(economySection("prosperity", "prosperity_overview", "project_market", sample("market")),
                                economySection("commission", "commission", "commission", text("screen.village-quest.prosperity.section.commission")),
                                economySection("services", "service_village_festival", "service_village_festival", text("screen.village-quest.prosperity.service.village_festival")),
                                economySection("collection", "collection", "prestige_guild_banner", text("screen.village-quest.prosperity.section.collection")),
                                economySection("statistics", "economy_statistics", "economy_statistics", text("screen.village-quest.prosperity.statistics.title")))));
                screen.selectTourSection(state.equals("upgrades") ? "prosperity" : state);
                yield screen;
            }
            case "noticeboard" -> new GuildNoticeBoardScreen(UiTestFixtures.noticeBoard(VillageBondLevel.valueOf(state.toUpperCase(java.util.Locale.ROOT))));
            case "noticejourney" -> new NoticeJourneyScreen(UiTestFixtures.journey(state));
            case "caravan" -> { CaravanMasterScreen screen = new CaravanMasterScreen(UiTestFixtures.caravan(state)); screen.updateReputation(standing(false)); yield screen; }
            case "wayshrine" -> new WayshrineScreen(UiTestFixtures.wayshrine(state.equals("owner")));
            case "dispatch" -> new RegionalDispatchScreen(new VillageNetworkPayloads.RegionalDispatchPayload(
                    0, 64, 0, sample("village"), sample(state), sample("journey"),
                    state.equals("active") ? new ItemStack(Items.BREAD) : ItemStack.EMPTY, state.equals("active") ? 8 : 0,
                    false, true, List.of(new VillageNetworkPayloads.RegionalDispatchOfferData(10000, sample("village"), new ItemStack(Items.BREAD), 8, 4,
                    DeliveryEconomy.regionalReward(Items.BREAD, 8, 304)))));
            case "routes" -> new TradeRouteMapScreen(new Payloads.TradeRouteMapPayload(0, text("screen.village-quest.trade_route.title"), sample("journey"),
                    List.of(new Payloads.TradeRouteNodeData(0, sample("homestead"), 0, 0, true, true, 0),
                            new Payloads.TradeRouteNodeData(1, sample("village"), 144, -48, false, false, 0)),
                    List.of(new Payloads.TradeRouteLineData(10000, 0, sample("journey"), 0, sample("active"), sample("available"), 84, 62, false, false, false, false, Component.empty(), Component.empty(), 0L, sample("market"), Component.empty(), Component.empty(), "Rowan", "", "", "",
                            List.of(new Payloads.TradeRoutePointData(0, 0, false), new Payloads.TradeRoutePointData(144, -48, false)), 152L, 160L, 160L, 2L, 1, false)),
                    List.of(), List.of(), UiTestFixtures.wayshrine(true).destinations(), List.of()));
            case "guildpath" -> new GuildPathScreen(new Payloads.GuildPathPayload(-1, 240,
                    List.of(new Payloads.GuildPathNodeData("ledger", new ItemStack(Items.BOOK), sample("market"), sample("description"), sample("objective"), 1))));
            default -> throw new IllegalArgumentException("Unknown UI tour family: " + step.family());
        };
    }
    private static Payloads.EconomySectionData economySection(String id, String icon, String entryIcon, Component title) {
        boolean actionable = !id.equals("statistics");
        long cost = switch (id) {
            case "prosperity" -> ProsperityService.nextRankCost(null, null, ProsperityBranch.MARKET);
            case "services" -> ProsperityService.VillageService.VILLAGE_FESTIVAL.cost();
            case "commission" -> ShopService.offer("decor_chiseled_bookshelf").price() + ProsperityService.commissionFee(null, null);
            case "collection" -> CurrencyService.CROWN * 8;
            default -> 0;
        };
        return new Payloads.EconomySectionData(id, text("screen.village-quest.prosperity.section." + id), icon,
                List.of(new Payloads.EconomyEntryData("tour", entryIcon, title, sample("available"),
                        List.of(sample("description")), cost,
                        actionable ? sample("accept") : Component.empty(), actionable, false)));
    }

    private static List<Payloads.GuildPathNodeData> nodes() {
        return List.of(new Payloads.GuildPathNodeData("village_ledger", new ItemStack(Items.BOOK), sample("market"), sample("description"), sample("objective"), 1));
    }
    private static JournalScreenData journal() {
        return new JournalScreenData(37, 12, 4, 2, 240, 12, 8, 5, 18, 3,
                true, false, false, false, true, true,
                true, text("quest.village-quest.daily.wheat.title"), sample("objective"),
                true, text("quest.village-quest.weekly.bakehouse.title"), sample("objective"),
                false, Component.empty(), Component.empty(), false, Component.empty(), Component.empty(), false, Component.empty(), Component.empty(),
                true, false, true, true, false, true, true, true, nodes(), sample("description"),
                new Payloads.NetworkSummaryData(3, 1, 0, sample("journey"), sample("available"), sample("description")),
                List.of(new Payloads.NetworkVillageData(10000, sample("village"), text("screen.village-quest.ui_tour.state.trusted"), sample("available"), "stable", sample("objective"), 50, 20, 12, false)), List.of(sample("description")));
    }
    private static QuestMasterScreen.QuestMasterData quest(String state) {
        var entries = new java.util.ArrayList<QuestMasterScreen.EntryView>();
        for (String id : List.of("wheat", "wood", "wool")) {
            var type = id.equals("wheat") ? DailyQuestService.DailyQuestType.WHEAT_HARVEST
                    : id.equals("wood") ? DailyQuestService.DailyQuestType.WOODCUTTING : DailyQuestService.DailyQuestType.WOOL_WEAVING;
            entries.add(new QuestMasterScreen.EntryView(id, "daily", text("quest.village-quest.daily." + id + ".title"),
                    sample("daily"), sample(state), true, Component.empty(),
                    List.of(text("quest.village-quest.daily." + id + ".offer.1"), text("quest.village-quest.daily." + id + ".offer.2")),
                    List.of(dailyProgress(id)), List.of(
                            text("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(DailyQuestService.rewardCurrency(type))),
                            QuestExperienceService.rewardLine(DailyQuestService.rewardLevels(type), 0, QuestExperienceService.RewardType.DAILY)),
                    1, sample("accept"), true, 2, sample("cancel"), state.equals("active"), state.equals("locked")));
        }
        return new QuestMasterScreen.QuestMasterData(-1, sample("master"),
                List.of(new QuestMasterScreen.CategoryView("daily", sample("daily"), 3), new QuestMasterScreen.CategoryView("weekly", sample("weekly"), 0)),
                entries, new QuestMasterScreen.PartyView(false, false, Component.empty(), List.of(), List.of()), 0);
    }
    private static Component dailyProgress(String id) {
        return switch (id) {
            case "wheat" -> text("quest.village-quest.daily.wheat.progress", 0, DailyQuestService.wheatTarget(), 0, DailyQuestService.breadTarget());
            case "wood" -> text("quest.village-quest.daily.wood.progress", 0, DailyQuestService.woodTarget(), 0, DailyQuestService.coalTarget());
            case "wool" -> text("quest.village-quest.daily.wool.progress", 0, DailyQuestService.sheepTarget(), 0, DailyQuestService.woolTarget());
            default -> Component.empty();
        };
    }
    private static ReputationViewService.View standing(boolean amends) {
        var village = new VillageKey("minecraft:overworld", 350, -200);
        var active = amends ? new ReputationViewService.CaseView(ReputationPayloads.NONE, false, 1, 1200, 0, 0,
                List.of(new ReputationViewService.AidView(0, 8, 32, 16))) : null;
        return new ReputationViewService.View(1, 0, true, true, amends ? -20 : 0, 0, 4, !amends, !amends, 100,
                0, 1, List.of(new ReputationViewService.VillageView(village, 12, amends)), List.of(), active);
    }
}
