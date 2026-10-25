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
import de.quest.registry.ModItems;
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
            case "friends" -> new FriendsQuestScreen(FriendsQuestPreview.view(state));
            case "building" -> { var screen=new BuildingScreen(building(state)); screen.selectTourPage(state); yield screen; }
            case "villagecheck" -> new VillageCheckScreen(new VillageCheckPayload("minecraft:overworld",List.of(
                    new VillageCheckPayload.Marker(net.minecraft.core.BlockPos.ZERO,de.quest.kingdom.BuildingScanner.Status.VALID,
                            de.quest.kingdom.BuildingScanner.Role.COUNCIL,sample("village").getString(),building("council").rows(),false)),new java.util.UUID(32,33),true));
            case "reward" -> new VanillaRewardScreen(new RewardPayloads.View(new java.util.UUID(34,35),"daily",List.of(
                    new RewardPayloads.Choice("work",List.of(sample("description")),12,List.of(new ItemStack(Items.IRON_PICKAXE))),
                    new RewardPayloads.Choice("supplies",List.of(sample("objective")),8,List.of(new ItemStack(Items.BREAD,16)))),"work"),null);
            case "adminjournal" -> new AdminJournalScreen(List.of(sample("description").getString(),sample("objective").getString()));
            case "market" -> {
                var id=new java.util.UUID(24,25);boolean limited=state.equals("limited");
                var adjustment=new de.quest.kingdom.MarketPricePolicy.Adjustment(0,0,0,0,0);
                var rows=List.of(
                        new MarketPayloads.Row("barrel",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.BARREL),8,limited?0:2,0,adjustment,limited?MarketPayloads.NONE:new java.util.UUID(1,1),limited?0:8,MarketPayloads.NONE,0),
                        new MarketPayloads.Row("oak_fence",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.OAK_FENCE),32,2,0,adjustment,new java.util.UUID(1,2),7,MarketPayloads.NONE,0),
                        new MarketPayloads.Row("hay",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.HAY_BLOCK),16,2,0,adjustment,new java.util.UUID(1,3),9,MarketPayloads.NONE,0),
                        new MarketPayloads.Row("need_wheat",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.WHEAT),32,0,4,adjustment,MarketPayloads.NONE,0,new java.util.UUID(1,4),1));
                var screen=new MarketScreen(new MarketPayloads.View(id,0,true,true,sample("merchant").getString(),sample("village").getString(),sample("market").getString(),1,limited?0:32,16,3,0,rows,MarketPayloads.Feedback.UPDATED,limited?600:7200));
                if(state.equals("details")) screen.selectTourDetails();yield screen;
            }
            case "journal" -> {
                JournalScreen screen = new JournalScreen(journal(state.equals("quests_reset")));
                screen.selectTourSection(state); screen.updateReputation(standing(false));
                yield screen;
            }
            case "questmaster" -> { var screen=new QuestMasterScreen(quest(state));
                screen.selectTourCategory(state.equals("weekly_reset") ? "weekly" : "daily"); yield screen; }
            case "trust" -> {
                ReparationScreen screen = trustPreview(null, state.equals("amends"));
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
            case "guildpath" -> new GuildPathScreen(new Payloads.GuildPathPayload(-1, 240, nodes()));
            default -> throw new IllegalArgumentException("Unknown UI tour family: " + step.family());
        };
    }
    private static BuildingPayloads.View building(String page) {
        var role=switch(page) { case "inn" -> de.quest.kingdom.BuildingScanner.Role.INN;
            case "market_anchor","places","travel" -> de.quest.kingdom.BuildingScanner.Role.MARKET;
            case "storehouse" -> de.quest.kingdom.BuildingScanner.Role.STOREHOUSE; default -> de.quest.kingdom.BuildingScanner.Role.COUNCIL; };
        boolean founding=page.equals("founding");
        var checks=switch(role) {
            case COUNCIL -> List.of(de.quest.kingdom.BuildingScanner.Check.ENTRY,de.quest.kingdom.BuildingScanner.Check.LIGHT,de.quest.kingdom.BuildingScanner.Check.ROOF,de.quest.kingdom.BuildingScanner.Check.TABLE,de.quest.kingdom.BuildingScanner.Check.SEATS,de.quest.kingdom.BuildingScanner.Check.BOOKS);
            case INN -> List.of(de.quest.kingdom.BuildingScanner.Check.ENTRY,de.quest.kingdom.BuildingScanner.Check.LIGHT,de.quest.kingdom.BuildingScanner.Check.ROOF,de.quest.kingdom.BuildingScanner.Check.TABLE,de.quest.kingdom.BuildingScanner.Check.SEATS,de.quest.kingdom.BuildingScanner.Check.BARRELS,de.quest.kingdom.BuildingScanner.Check.KITCHEN,de.quest.kingdom.BuildingScanner.Check.BED);
            case MARKET -> List.of(de.quest.kingdom.BuildingScanner.Check.ENTRY,de.quest.kingdom.BuildingScanner.Check.LIGHT,de.quest.kingdom.BuildingScanner.Check.COUNTER,de.quest.kingdom.BuildingScanner.Check.CONTAINERS);
            case STOREHOUSE -> List.of(de.quest.kingdom.BuildingScanner.Check.ENTRY,de.quest.kingdom.BuildingScanner.Check.LIGHT,de.quest.kingdom.BuildingScanner.Check.ROOF,de.quest.kingdom.BuildingScanner.Check.CONTAINERS,de.quest.kingdom.BuildingScanner.Check.WORKSTATION,de.quest.kingdom.BuildingScanner.Check.HANDOVER);
        };
        var rows=checks.stream().map(c -> new BuildingPayloads.Row(c,true,c==de.quest.kingdom.BuildingScanner.Check.SEATS?5:1,c==de.quest.kingdom.BuildingScanner.Check.SEATS?5:1,List.of(net.minecraft.core.BlockPos.ZERO))).toList();
        var stand=new BuildingPayloads.Stand(0,new net.minecraft.core.BlockPos(1,64,1),new net.minecraft.core.BlockPos(1,64,2),new net.minecraft.core.BlockPos(2,64,1),true);
        var layout=new BuildingPayloads.MarketLayout(0,List.of(stand),List.of(stand),List.of(new BuildingPayloads.Travel(new java.util.UUID(42,43),sample("merchant").getString(),sample("homestead").getString(),sample("village").getString(),"outbound",1200,false,false)));
        var deals=List.of(new BuildingPayloads.Deal(0,2,0,false),new BuildingPayloads.Deal(0,0,1,true),new BuildingPayloads.Deal(-1,-1,0,false));
        var partner=new BuildingPayloads.Partner(new java.util.UUID(44,45),sample("homestead").getString(),0,deals,"active",true,
                new BuildingPayloads.Relation("trading","improving",List.of("trade","delivery"),"none","none"));
        var crisis=new BuildingPayloads.Crisis(new java.util.UUID(46,47),0,sample("village").getString(),sample("homestead").getString(),"open","granary_pantry",2400,1,0,false);
        return new BuildingPayloads.View(new java.util.UUID(36,37),0,true,new net.minecraft.core.BlockPos(0,64,0),role,de.quest.kingdom.BuildingScanner.Range.BASIC,
                de.quest.kingdom.BuildingScanner.Status.VALID,founding?BuildingPayloads.NONE:new java.util.UUID(38,39),0,founding?BuildingPayloads.NONE:new java.util.UUID(40,41),0,
                !founding,founding,0,0,false,founding?"":sample("village").getString(),rows,founding?List.of():List.of(new BuildingPayloads.Choice(new java.util.UUID(38,39),sample("village").getString())),"",
                layout,new BuildingPayloads.Recognition(0,0,List.of("population","voices","trade","freight"),false),
                new BuildingPayloads.Diplomacy(0,1,List.of(partner),false,crisis),true,founding?BuildingPayloads.Binding.UNREGISTERED:BuildingPayloads.Binding.REGISTERED);
    }
    private static Payloads.EconomySectionData economySection(String id, String icon, String entryIcon, Component title) {
        if(id.equals("prosperity")) {
            var rows=new java.util.ArrayList<Payloads.EconomyEntryData>();
            for(var branch:ProsperityBranch.values()) {
                int rank=0;
                long price=ProsperityService.nextRankCost(null,null,branch);
                rows.add(new Payloads.EconomyEntryData("prosperity:"+branch.id(),branch.icon(),branch.title(),
                        CurrencyService.formatBalance(price),List.of(branch.benefit(rank),
                        text("screen.village-quest.prosperity.next_benefit",branch.benefit(rank+1))),price,
                        text("screen.village-quest.prosperity.invest"),price<=240,false));
            }
            return new Payloads.EconomySectionData(id,text("screen.village-quest.prosperity.section."+id),icon,rows);
        }
        if(id.equals("collection")) return new Payloads.EconomySectionData(id,
                text("screen.village-quest.prosperity.section.collection"),icon,List.of(
                collection("livery_forest","prestige_caravan_livery_forest",8),
                collection("guild_banner","prestige_guild_banner",12),
                collection("mapmaker_crest","prestige_mapmaker_crest",14),
                collection("market_pavilion","prestige_market_pavilion",18)));
        boolean actionable = !id.equals("statistics");
        long cost = switch (id) {
            case "prosperity" -> ProsperityService.nextRankCost(null, null, ProsperityBranch.MARKET);
            case "services" -> ProsperityService.VillageService.VILLAGE_FESTIVAL.cost();
            case "commission" -> ShopService.offer("decor_chiseled_bookshelf").price() + ProsperityService.commissionFee(null, null);
            case "collection" -> CurrencyService.CROWN * 8;
            default -> 0;
        };
        return new Payloads.EconomySectionData(id, text("screen.village-quest.prosperity.section." + id), icon,
                List.of(new Payloads.EconomyEntryData("tour", entryIcon, title, CurrencyService.formatBalance(cost),
                        id.equals("prosperity")?List.of(ProsperityBranch.MARKET.benefit(0),
                                text("screen.village-quest.prosperity.next_benefit",ProsperityBranch.MARKET.benefit(1))):List.of(id.equals("services")?text("screen.village-quest.prosperity.service.village_festival.description"):id.equals("collection")?text("screen.village-quest.prosperity.collection.guild_banner.description"):sample("description")), cost,
                        actionable ? id.equals("prosperity")?text("screen.village-quest.prosperity.invest"):text(id.equals("services")?"screen.village-quest.prosperity.service.buy":"screen.village-quest.prosperity.collection.buy") : Component.empty(), actionable, false)));
    }

    private static Payloads.EconomyEntryData collection(String id,String icon,int crowns) {
        long price=CurrencyService.CROWN*crowns;
        return new Payloads.EconomyEntryData("collection:"+id,icon,
                text("screen.village-quest.prosperity.collection."+id),CurrencyService.formatBalance(price),
                List.of(text("screen.village-quest.prosperity.collection."+id+".description")),price,
                text("screen.village-quest.prosperity.collection.buy"),price<=240,false);
    }

    static ReparationScreen trustPreview(net.minecraft.client.gui.screens.Screen parent, boolean amends) {
        return new ReparationScreen(new ReputationPayloads.InteractionPayload(
                ReputationPayloads.NONE, true, true, standing(amends),
                List.of(new ReputationPayloads.SupportView(ReputationPayloads.NONE,
                        new VillageKey("minecraft:overworld",350,-200),false,false,false)),""),parent);
    }
    static List<Payloads.GuildPathNodeData> nodes() {
        // Same IDs, item identities and localized ability/requirement texts as the live builder.
        // A representative mid-game snapshot; these statuses never change player progress.
        return List.of(
                node("ledger",ModItems.CARAVAN_LEDGER,"caravan_ledger",2),
                node("surveyor_compass",ModItems.SURVEYORS_COMPASS,"surveyors_compass",2),
                node("starreach_ring",ModItems.STARREACH_RING,"starreach_ring",1),
                node("merchant_seal",ModItems.MERCHANT_SEAL,"merchant_seal",1),
                node("shepherd_flute",ModItems.SHEPHERD_FLUTE,"shepherd_flute",2),
                node("apiarist_smoker",ModItems.APIARISTS_SMOKER,"apiarists_smoker",1),
                node("lens",ModItems.CARTOGRAPHERS_LENS,"cartographers_lens",1),
                node("sigil",ModItems.WAYFARERS_SIGIL,"wayfarers_sigil",0),
                node("wayshrine",ModItems.GUILD_WAYSHRINE,"guild_wayshrine",0),
                node("notice_board",ModItems.GUILD_NOTICE_POST,"guild_notice_post",1),
                node("courier_satchel",ModItems.GUILD_COURIERS_SATCHEL,"guild_couriers_satchel",0));
    }
    private static Payloads.GuildPathNodeData node(String id,net.minecraft.world.item.Item item,String itemId,int status) {
        // Headless unit compositions don't register mod items. Runtime always uses the real stack.
        ItemStack stack=item==null?ItemStack.EMPTY:new ItemStack(item);
        String prefix=id.equals("wayshrine")||id.equals("notice_board")?"block":"item";
        return new Payloads.GuildPathNodeData(id,stack,text(prefix+".village-quest."+itemId),
                text("screen.village-quest.guild_path.node."+id+".ability"),
                text("screen.village-quest.guild_path.node."+id+".requirement"),status);
    }
    private static JournalScreenData journal(boolean reset) {
        return new JournalScreenData(37, 12, 4, 2, 240, 12, 8, 5, 18, 3,
                true, false, false, false, true, true,
                !reset, text(reset?"screen.village-quest.questmaster.daily.completed_today":"quest.village-quest.daily.wheat.title"), reset?text("screen.village-quest.questmaster.daily.next_reset","6h 24m"):dailyProgress("wheat"),
                false, reset?text("screen.village-quest.questmaster.weekly.completed"):Component.empty(), reset?text("screen.village-quest.questmaster.weekly.next_reset_in","78h 24m"):Component.empty(),
                false, Component.empty(), Component.empty(), false, Component.empty(), Component.empty(), false, Component.empty(), Component.empty(),
                true, false, true, true, false, true, true, true, nodes(), sample("description"),
                new Payloads.NetworkSummaryData(3, 1, 0, sample("journey"), sample("available"), sample("description")),
                List.of(new Payloads.NetworkVillageData(10000, sample("village"), text("screen.village-quest.ui_tour.state.trusted"), sample("available"), "stable", sample("objective"), 50, 20, 12, false,
                        new java.util.UUID(10,20),List.of(text("screen.village-quest.reputation.guild")),new Payloads.JournalVillageStats(4,2,3,5))), List.of(text("command.village-quest.guild.none")));
    }
    private static QuestMasterScreen.QuestMasterData quest(String state) {
        var entries = new java.util.ArrayList<QuestMasterScreen.EntryView>();
        boolean dailyReset=state.equals("daily_reset"),weeklyReset=state.equals("weekly_reset");
        if(weeklyReset) {
            var definition=de.quest.quest.weekly.WeeklyQuestGenerator.definition(de.quest.quest.weekly.WeeklyQuestService.WeeklyQuestType.BAKEHOUSE_STOCK);
            entries.add(new QuestMasterScreen.EntryView("weekly", "weekly", definition.title(), sample("weekly"),
                    text("screen.village-quest.questmaster.status.completed"),true,Component.empty(),
                    List.of(text("screen.village-quest.questmaster.weekly.completed")),
                    List.of(text("screen.village-quest.questmaster.weekly.next_reset_in","78h 24m")),List.of(),
                    0,Component.empty(),false,0,Component.empty(),false,false));
            return new QuestMasterScreen.QuestMasterData(-1,sample("master"),List.of(
                    new QuestMasterScreen.CategoryView("daily",sample("daily"),0),
                    new QuestMasterScreen.CategoryView("weekly",sample("weekly"),1)),entries,
                    new QuestMasterScreen.PartyView(false,false,Component.empty(),List.of(),List.of()),0);
        }
        for (String id : List.of("wheat", "wood", "wool")) {
            var type = id.equals("wheat") ? DailyQuestService.DailyQuestType.WHEAT_HARVEST
                    : id.equals("wood") ? DailyQuestService.DailyQuestType.WOODCUTTING : DailyQuestService.DailyQuestType.WOOL_WEAVING;
            entries.add(new QuestMasterScreen.EntryView(id, weeklyReset?"weekly":"daily", text("quest.village-quest.daily." + id + ".title"),
                    sample("daily"), dailyReset?text("screen.village-quest.questmaster.status.completed"):sample(state), true, Component.empty(),
                    List.of(text("quest.village-quest.daily." + id + ".offer.1"), text("quest.village-quest.daily." + id + ".offer.2")),
                    List.of(dailyReset?text("screen.village-quest.questmaster.daily.next_reset","6h 24m"):weeklyReset?text("screen.village-quest.questmaster.weekly.next_reset_in","78h 24m"):state.equals("locked")?text("message.village-quest.questmaster.completed_today"):dailyProgress(id)), List.of(
                            text("screen.village-quest.questmaster.reward.currency", CurrencyService.formatBalance(DailyQuestService.rewardCurrency(type))),
                            QuestExperienceService.rewardLine(DailyQuestService.rewardLevels(type), 0, QuestExperienceService.RewardType.DAILY)),
                    1, sample("accept"), !state.equals("active") && !state.equals("locked") && !dailyReset && !weeklyReset,
                    2, sample("cancel"), state.equals("active"), state.equals("locked") || dailyReset || weeklyReset));
        }
        return new QuestMasterScreen.QuestMasterData(-1, sample("master"),
                List.of(new QuestMasterScreen.CategoryView("daily", sample("daily"), weeklyReset?0:3), new QuestMasterScreen.CategoryView("weekly", sample("weekly"), weeklyReset?3:0)),
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
